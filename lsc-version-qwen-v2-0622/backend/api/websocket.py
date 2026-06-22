from flask_socketio import emit
from flask import request
import threading
import queue
import traceback
import re
import time
import logging
from services.asr_service import ASRService
from services.llm_service import LLMService
from services.tts_service import TTSService

logger = logging.getLogger(__name__)

asr_service = ASRService()
llm_service = LLMService()
tts_service = TTSService()

client_histories = {}
_socketio = None

tts_semaphore = threading.Semaphore(2)

GREETING = """您好！我是**闽路通**，福建省公路交通智能助手。

我可以帮您：
**实时路况** — 查询全省九地市任意区域的道路通行状态
**态势研判** — 分析拥堵分布，评估路网运行态势
**精准定位** — 支持城市、区县、具体地点或道路名查询

您可以直接用语音或文字问我，例如：
"厦门市路况怎么样？"
"福州站附近堵不堵？"
"成功大道现在什么情况？"

请问有什么可以帮您的？"""


def strip_markdown_for_tts(text):
    """去除 markdown 格式，用于 TTS 合成"""
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


def register_handlers(socketio):
    global _socketio
    _socketio = socketio

    @socketio.on('connect')
    def handle_connect():
        client_id = request.sid
        client_histories[client_id] = []
        logger.info(f'客户端已连接: {client_id}')
        emit('connected', {'status': 'ok'})
        emit('greeting', {'content': GREETING})

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
            with tts_semaphore:
                audio_url = tts_service.synthesize(clean)
            if audio_url:
                _socketio.emit('audio', {
                    'url': audio_url, 'index': idx, 'char_pos': char_pos
                }, room=client_id)
                logger.info(f'[TTS-{idx}] 完成')
            else:
                logger.warning(f'[TTS-{idx}] 失败')

    workers = [threading.Thread(target=tts_worker, daemon=True) for _ in range(2)]
    for w in workers:
        w.start()

    full_response = ''
    pending = ''        # 等待完整的句子
    sent_texts = set()  # 去重
    tts_index = 0
    first_sentence = True

    def send_sentence(text):
        nonlocal tts_index, first_sentence
        if not auto_read:
            return
        text = text.strip()
        if not text or text in sent_texts:
            return
        # 首句不足 20 字 → 攒到下一句合并发（防止 TTS0 过短）
        if first_sentence and len(text) < 20:
            pending = text
            first_sentence = False
            return
        first_sentence = False
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
                _socketio.emit('traffic_data', {
                    'city': frontend.get('area', frontend.get('city', '')),
                    'summary': frontend.get('summary', ''),
                    'roads': frontend['roads'],
                    'center': frontend.get('center'),
                    'query_radius': frontend.get('query_radius'),
                }, room=client_id)
                n_roads = len(frontend['roads'])
                logger.info(f'[Skill:{skill_name}] 提前推送 {n_roads} 条道路数据')

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

                # 如果首句被攒了，把攒的文本拼到新的 pending 中
                if not first_sentence and pending and pending not in sent_texts:
                    chunk = pending + chunk
                    pending = ''

                pending += chunk
                time.sleep(0.06)

                while True:
                    m = re.match(r'(.+?[。！？\n])\s*(.*)', pending)
                    if not m:
                        if len(pending) > 80:
                            fm = re.match(r'(.+?[，；])\s*(.*)', pending)
                            if fm:
                                send_sentence(fm.group(1))
                                pending = fm.group(2)
                                continue
                        break
                    send_sentence(m.group(1))
                    pending = m.group(2)

        if pending.strip():
            send_sentence(pending)

    finally:
        for _ in workers:
            tts_queue.put(None)
        for w in workers:
            w.join(timeout=30)

    display_text = re.sub(r'```json[\s\S]*?```', '', full_response).strip()
    emit('text_complete', {'content': display_text})
    client_histories[client_id].append({'role': 'assistant', 'content': full_response})


def process_audio_message(client_id, audio_data, auto_read):
    recognized_text = asr_service.recognize(audio_data)
    if not recognized_text:
        emit('error', {'content': '语音识别失败，请重试'})
        return
    emit('asr_result', {'content': recognized_text})
    process_text_message(client_id, recognized_text, auto_read)
