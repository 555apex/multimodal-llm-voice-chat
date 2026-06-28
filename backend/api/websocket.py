from flask_socketio import emit
from flask import request
import threading
import queue
import traceback
import re
import time
import logging
import os
import shutil
from services.asr_service import ASRService
from services.llm_service import LLMService
from services.tts_service import TTSService

logger = logging.getLogger(__name__)

asr_service = ASRService()
llm_service = LLMService()
tts_service = TTSService()

client_histories = {}
_socketio = None

# Edge-TTS 本地推理快（50-200ms/句），4 个 worker 可保证预取充足
tts_semaphore = threading.Semaphore(4)

# 临时音频文件存储
AUDIO_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'temp_audio')
os.makedirs(AUDIO_DIR, exist_ok=True)



def strip_markdown_for_tts(text):
    """去除 markdown 格式，表格行转换为自然语句，用于 TTS 合成"""
    # Markdown 表格分隔行（| :--- | :--- |）→ 跳过
    if re.match(r'^[\s|:\-]+$', text):
        return ''
    # emoji 纯符号行 → 跳过
    if re.match(r'^[\s🟢🟡🔴🚗🚧🚦🛣️]*$', text):
        return ''

    # 表格行 → 自然语句：| **成功大道** | 仙岳路 | 15 km/h | 缓行 | → 成功大道，仙岳路，车速15公里每小时，缓行。
    text = _convert_table_rows(text)

    text = re.sub(r'\*\*(.+?)\*\*', r'\1', text)
    text = re.sub(r'__(.+?)__', r'\1', text)
    text = re.sub(r'\*(.+?)\*', r'\1', text)
    text = re.sub(r'_(.+?)_', r'\1', text)
    text = re.sub(r'`[^`]*`', '', text)
    text = re.sub(r'```[\s\S]*?```', '', text)
    text = re.sub(r'^#{1,6}\s+', '', text, flags=re.MULTILINE)
    text = re.sub(r'^>\s+', '', text, flags=re.MULTILINE)
    text = re.sub(r'^[\s]*[-*+]\s+', '', text, flags=re.MULTILINE)
    text = re.sub(r'^[\s]*\d+\.\s+', '', text, flags=re.MULTILINE)
    text = re.sub(r'\n{2,}', '\n', text)
    return text.strip()


def _convert_table_rows(text):
    """将 Markdown 表格行转为自然口语"""
    lines = text.split('\n')
    result = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith('|') and stripped.endswith('|'):
            cells = [c.strip() for c in stripped.split('|') if c.strip()]
            if not cells:
                continue
            # 跳过对齐行 (:---, :---:)
            if all(re.match(r'^[:\-]+$', c) for c in cells):
                continue
            cleaned = []
            for c in cells:
                c = re.sub(r'\*\*(.+?)\*\*', r'\1', c)
                c = re.sub(r'__(.+?)__', r'\1', c)
                cleaned.append(c.strip())
            if cleaned:
                result.append('，'.join(cleaned) + '。')
        else:
            result.append(line)
    return '\n'.join(result)


def _sync_llm_roads_to_map(client_id, llm_text):
    """从 LLM 回复文本中提取路名，匹配 API 数据，补充地图高亮"""
    traffic_result = llm_service.last_results.get('query_traffic')
    if not traffic_result:
        return
    roads = traffic_result.get('roads', [])
    if not roads or not llm_text:
        return

    # 在 LLM 文本中出现的路名 → 加入高亮（仅拥堵/缓行，畅通路不需要高亮）
    mentioned = []
    for r in roads:
        name = r.get('name', '')
        status = r.get('status', '')
        if name and name in llm_text and status in ('拥堵', '严重拥堵', '缓行'):
            mentioned.append({
                'name': name,
                'status': status,
                'speed': r.get('speed', ''),
                'direction': r.get('direction', ''),
                'polyline': r.get('polyline', []),
            })

    if mentioned:
        logger.info(f'[LLM→Map] LLM 提到 {len(mentioned)} 条路，推送高亮更新')
        _socketio.emit('highlight_update', {
            'roads': mentioned,
        }, room=client_id)


def register_handlers(socketio):
    global _socketio
    _socketio = socketio

    @socketio.on('connect')
    def handle_connect():
        client_id = request.sid
        client_histories[client_id] = []
        logger.info(f'客户端已连接: {client_id}')
        emit('connected', {'status': 'ok'})
        emit('greeting', {'content': llm_service.greeting})

    @socketio.on('disconnect')
    def handle_disconnect():
        client_id = request.sid
        if client_id in client_histories:
            del client_histories[client_id]
        logger.info(f'客户端已断开: {client_id}')

    @socketio.on('message')
    def handle_message(data):
        client_id = request.sid
        msg_type = data.get('type')
        auto_read = data.get('auto_read', True)
        if isinstance(auto_read, str):
            auto_read = auto_read.lower() in ('true', '1', 'yes')
        else:
            auto_read = bool(auto_read)

        try:
            if msg_type == 'text':
                process_text_message(client_id, data.get('content', ''), auto_read)
            elif msg_type == 'audio':
                process_audio_message(client_id, data.get('content', ''), auto_read)
            else:
                emit('error', {'content': f'未知消息类型: {msg_type}'})
        except Exception as e:
            traceback.print_exc()
            logger.error(f'处理消息时出错: {e}')


def process_text_message(client_id, text, auto_read):
    if client_id not in client_histories:
        client_histories[client_id] = []
    client_histories[client_id].append({'role': 'user', 'content': text})
    logger.info(f'auto_read={auto_read}')

    tts_queue = queue.Queue()

    def tts_worker():
        while True:
            item = tts_queue.get()
            if item is None:
                break
            chunk_text, idx, char_pos = item
            clean = strip_markdown_for_tts(chunk_text)
            if not clean:
                continue
            logger.info(f'[TTS-{idx}] 合成 {len(clean)} 字: {clean[:25]}...')
            try:
                with tts_semaphore:
                    audio_path = tts_service.synthesize(clean)
                if audio_path:
                    fname = os.path.basename(audio_path)
                    dest = os.path.join(AUDIO_DIR, fname)
                    shutil.move(audio_path, dest)
                    url = f'/audio/temp_audio/{fname}'
                    _socketio.emit('audio', {
                        'url': url, 'index': idx, 'char_pos': char_pos
                    }, room=client_id)
                    logger.info(f'[TTS-{idx}] 完成')
                else:
                    logger.warning(f'[TTS-{idx}] 合成返回空，跳过')
            except Exception as e:
                logger.warning(f'[TTS-{idx}] 合成失败，跳过: {e}')

    workers = [threading.Thread(target=tts_worker, daemon=True) for _ in range(4)]
    for w in workers:
        w.start()

    full_response = ''
    pending = ''        # 等待完整的句子
    sent_texts = set()  # 去重
    tts_index = 0

    def send_sentence(text, has_more=False):
        """发送句子到 TTS 队列。has_more 表示 pending 中还有后续文本。"""
        nonlocal tts_index
        if not auto_read:
            return
        text = text.strip()
        if not text or text in sent_texts:
            return
        # 动态阈值：后面有内容 → 低门槛（能跟上）；后面空的 → 高门槛（防间隔）
        min_len = 6 if has_more else 20
        if len(text) < min_len:
            return
        # 预检：过滤后为空的文本不占 index（防止前端播放链卡死）
        if not strip_markdown_for_tts(text):
            return
        sent_texts.add(text)
        char_pos = sum(len(s) for s in sent_texts) - len(text)
        tts_queue.put((text, tts_index, char_pos))
        logger.info(f'[TTS-{tts_index}] 入队 {len(text)} 字: {text[:30]}...')
        tts_index += 1

    def emit_frontend(results):
        """工具结果就绪 → 不等文本流，立即推前端地图"""
        for skill_name, full_result in results.items():
            skill = llm_service._skill_map.get(skill_name)
            if not skill:
                continue
            frontend = skill.frontend_data(full_result)
            if frontend and frontend.get('roads'):
                highlights = skill.build_highlights(frontend)
                _socketio.emit('traffic_data', {
                    'city': frontend.get('area', frontend.get('city', '')),
                    'summary': frontend.get('summary', ''),
                    'roads': frontend['roads'],
                    'center': frontend.get('center'),
                    'query_radius': frontend.get('query_radius'),
                    'bounds': frontend.get('bounds'),
                    'highlights': highlights,
                }, room=client_id)
                n_roads = len(frontend['roads'])
                logger.info(f'[Skill:{skill_name}] 推送 {n_roads} 条道路, {len(highlights)} 条高亮')

    try:
        json_started = False
        for chunk in llm_service.chat_stream_with_tools(
            client_histories[client_id],
            on_tool_results=emit_frontend,
        ):
            if chunk:
                full_response += chunk

                if not json_started and '```json' in full_response:
                    json_started = True

                if not json_started:
                    emit('text_chunk', {'content': chunk, 'is_final': False})

                pending += chunk
                time.sleep(0.02)

                while True:
                    m = re.match(r'(.+?[。！？\n])\s*(.*)', pending)
                    if m:
                        sentence, rest = m.group(1), m.group(2)
                        has_more = bool(rest.strip())
                        if len(sentence) >= (6 if has_more else 20):
                            send_sentence(sentence, has_more=has_more)
                            pending = rest
                            continue
                        # 短句向前多看一句合并
                        m2 = re.match(r'(.+?[。！？\n，；])\s*(.*)', rest)
                        if m2:
                            combined = sentence + m2.group(1)
                            new_rest = m2.group(2)
                            has_more = bool(new_rest.strip())
                            if len(combined) >= (6 if has_more else 20):
                                send_sentence(combined, has_more=has_more)
                                pending = new_rest
                                continue
                        break

                    # 逗号分号切分
                    m = re.match(r'(.+?[，；])\s*(.*)', pending)
                    if m:
                        sentence, rest = m.group(1), m.group(2)
                        has_more = bool(rest.strip())
                        if len(sentence) >= (6 if has_more else 20):
                            send_sentence(sentence, has_more=has_more)
                            pending = rest
                            continue
                        break

                    # 兜底：缓冲区积压过多 → 强制按逗号/换行切分
                    if len(pending) > 80:
                        m = re.match(r'(.+?[，；\n])\s*(.*)', pending)
                        if m:
                            send_sentence(m.group(1), has_more=True)
                            pending = m.group(2)
                            continue
                        # 仍不行 → 每 ~40 字硬切（防止无边界文本积压）
                        send_sentence(pending[:40], has_more=True)
                        pending = pending[40:]
                        continue
                    break

        if pending.strip():
            send_sentence(pending, has_more=False)

    finally:
        for _ in workers:
            tts_queue.put(None)
        for w in workers:
            w.join(timeout=30)

    display_text = re.sub(r'```json[\s\S]*?```', '', full_response).strip()
    emit('text_complete', {'content': display_text})
    client_histories[client_id].append({'role': 'assistant', 'content': full_response})

    # ── LLM 路名匹配 → 补充地图高亮 ──
    _sync_llm_roads_to_map(client_id, full_response)


def process_audio_message(client_id, audio_data, auto_read):
    recognized_text = asr_service.recognize(audio_data)
    if not recognized_text:
        emit('error', {'content': '语音识别失败，请重试'})
        return
    emit('asr_result', {'content': recognized_text})
    process_text_message(client_id, recognized_text, auto_read)
