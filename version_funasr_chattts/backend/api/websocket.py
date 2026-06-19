from flask_socketio import emit
from flask import request
from services.asr_service import ASRService
from services.llm_service import LLMService
from services.tts_service import TTSService
from skills.traffic_assistant import get_greeting, detect_function_call, FUNCTION_PORTS
import logging
import re
import threading
import queue
from collections import deque

logger = logging.getLogger(__name__)

# 初始化服务
asr_service = ASRService()
llm_service = LLMService()
tts_service = TTSService()

# 存储每个客户端的对话历史（限制长度）
MAX_HISTORY_LENGTH = 100
client_histories = {}

# SocketIO 实例（用于后台线程发送消息）
_socketio = None


def strip_markdown_for_tts(text):
    """去除 markdown 格式和 emoji 表情，用于 TTS 合成"""
    # 移除代码块
    text = re.sub(r'```[\s\S]*?```', '', text)
    # 移除行内代码
    text = re.sub(r'`[^`]*`', '', text)
    # 移除图片
    text = re.sub(r'!\[.*?\]\(.*?\)', '', text)
    # 移除链接，保留文字
    text = re.sub(r'\[([^\]]*)\]\([^\)]*\)', r'\1', text)
    # 移除标题标记
    text = re.sub(r'^#{1,6}\s+', '', text, flags=re.MULTILINE)
    # 移除加粗
    text = re.sub(r'\*\*(.+?)\*\*', r'\1', text)
    text = re.sub(r'__(.+?)__', r'\1', text)
    # 移除斜体
    text = re.sub(r'(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)', r'\1', text)
    text = re.sub(r'(?<!_)_(?!_)(.+?)(?<!_)_(?!_)', r'\1', text)
    # 移除删除线
    text = re.sub(r'~~(.+?)~~', r'\1', text)
    # 移除引用
    text = re.sub(r'^>\s+', '', text, flags=re.MULTILINE)
    # 移除水平线
    text = re.sub(r'^[-*_]{3,}\s*$', '', text, flags=re.MULTILINE)
    # 移除列表标记
    text = re.sub(r'^[\s]*[-*+]\s+', '', text, flags=re.MULTILINE)
    text = re.sub(r'^[\s]*\d+\.\s+', '', text, flags=re.MULTILINE)
    # 移除 emoji 表情（只匹配真正的 emoji，不误伤中文字符）
    emoji_pattern = re.compile(
        "["
        "\U0001F600-\U0001F64F"  # 表情符号
        "\U0001F300-\U0001F5FF"  # 符号和象形文字
        "\U0001F680-\U0001F6FF"  # 交通和地图符号
        "\U0001F900-\U0001F9FF"  # 补充符号和象形文字
        "\U0001FA00-\U0001FA6F"  # 棋子符号
        "\U0001FA70-\U0001FAFF"  # 符号和象形文字扩展-A
        "\U0001F1E0-\U0001F1FF"  # 区域指示符号（旗帜）
        "\U00002702-\U000027B0"  # 装饰符号
        "\U0000FE00-\U0000FE0F"  # 变体选择符
        "\U0000200D"             # 零宽连接符
        "\U00002600-\U000026FF"  # 杂项符号
        "\U00002B50-\U00002B55"  # 星星和圆圈
        "\U0000231A-\U0000231B"  # 手表和沙漏
        "\U000023E9-\U000023F3"  # 媒体控制符号
        "\U000023F8-\U000023FA"  # 媒体控制符号
        "\U000025AA-\U000025AB"  # 小方块
        "\U000025B6"             # 播放按钮
        "\U000025C0"             # 倒放按钮
        "\U000025FB-\U000025FE"  # 方块
        "\U00002614-\U00002615"  # 雨伞和咖啡
        "\U00002648-\U00002653"  # 星座
        "\U0000267F"             # 轮椅
        "\U00002693"             # 锚
        "\U000026A1"             # 闪电
        "\U000026AA-\U000026AB"  # 圆圈
        "\U000026BD-\U000026BE"  # 足球和棒球
        "\U000026C4-\U000026C5"  # 雪人和太阳
        "\U000026CE"             # 蛇夫座
        "\U000026D4"             # 禁止通行
        "\U000026EA"             # 教堂
        "\U000026F2-\U000026F3"  # 喷泉和高尔夫
        "\U000026F5"             # 帆船
        "\U000026FA"             # 帐篷
        "\U000026FD"             # 加油站
        "\U00002702"             # 剪刀
        "\U00002705"             # 勾选
        "\U00002708-\U0000270D"  # 飞机到写字
        "\U0000270F"             # 铅笔
        "\U00002712"             # 黑色铅笔
        "\U00002714"             # 粗体勾选
        "\U00002716"             # 粗体叉号
        "\U0000271D"             # 十字架
        "\U00002721"             # 大卫星
        "\U00002728"             # 闪光
        "\U00002733-\U00002734"  # 八星和六星
        "\U00002744"             # 雪花
        "\U00002747"             # 闪光装饰
        "\U0000274C"             # 叉号
        "\U0000274E"             # 方框叉号
        "\U00002753-\U00002755"  # 问号
        "\U00002757"             # 感叹号
        "\U00002763-\U00002764"  # 心形
        "\U00002795-\U00002797"  # 加减除
        "\U000027A1"             # 右箭头
        "\U000027B0"             # 卷曲环
        "]+",
        flags=re.UNICODE
    )
    text = emoji_pattern.sub('', text)
    # 移除 markdown 特殊字符（保留括号、^、小数点、*、_ 等常用符号）
    text = re.sub(r'[#\[\]>`~|]', '', text)
    # 将 LaTeX 数学公式转为可读文本
    # 行内公式 $...$ → 去掉 $ 符号
    text = re.sub(r'\$([^$]+)\$', r'\1', text)
    # 行间公式 $$...$$ → 去掉 $$ 符号
    text = re.sub(r'\$\$([^$]+)\$\$', r'\1', text)
    # 清理多余空白
    text = re.sub(r'\n{2,}', '\n', text)
    text = text.strip()
    return text


def extract_complete_sentences(text):
    """从文本中提取完整的句子，返回 (句子列表, 剩余文本)"""
    # 匹配中英文句子结尾
    # 中文：句号、叹号、问号、换行符
    # 英文：.!? 后面跟空格或换行或结尾（避免在小数点处断句）
    # 使用非贪婪匹配，确保不会跳过中间的句号
    pattern = r'([^。！？\n]+[。！？\n]|.+?[.!?](?:\s|\n|$))'
    matches = re.findall(pattern, text)

    if matches:
        last_match = matches[-1]
        last_idx = text.rfind(last_match) + len(last_match)
        remaining = text[last_idx:]
        return matches, remaining
    else:
        # 没有完整句子，检查缓冲区长度
        if len(text) > 100:
            # 在逗号、分号等处断开
            for sep in ['，', '；', ',', ';', '、']:
                idx = text.rfind(sep)
                if idx > 0:
                    return [text[:idx + 1]], text[idx + 1:]
            # 没有好的断点，强制在100字符处断开
            return [text[:100]], text[100:]
        return [], text


def register_handlers(socketio):
    """注册 WebSocket 事件处理器"""
    global _socketio
    _socketio = socketio

    @socketio.on('connect')
    def handle_connect():
        """客户端连接"""
        client_id = request.sid
        client_histories[client_id] = deque(maxlen=MAX_HISTORY_LENGTH)
        logger.info(f'客户端已连接: {client_id}')
        emit('connected', {'status': 'ok'})

        # 发送开场白
        greeting = get_greeting()
        emit('greeting', {'content': greeting})

    @socketio.on('disconnect')
    def handle_disconnect():
        """客户端断开"""
        client_id = request.sid
        if client_id in client_histories:
            del client_histories[client_id]
        logger.info(f'客户端已断开: {client_id}')

    @socketio.on('message')
    def handle_message(data):
        """处理客户端消息"""
        client_id = request.sid
        msg_type = data.get('type')

        try:
            if msg_type == 'text':
                user_text = data.get('content', '')
                process_text_message(client_id, user_text)

            elif msg_type == 'audio':
                audio_data = data.get('content', '')
                audio_format = data.get('format', 'wav')
                process_audio_message(client_id, audio_data, audio_format)

            else:
                emit('error', {'content': f'未知消息类型: {msg_type}'})

        except Exception as e:
            logger.error(f'处理消息时出错: {e}')
            import traceback
            logger.debug(traceback.format_exc())
            emit('error', {'content': f'处理消息时出错: {str(e)}'})


def process_text_message(client_id, text):
    """处理文本消息 - 流式 TTS 版本"""
    if client_id not in client_histories:
        client_histories[client_id] = deque(maxlen=MAX_HISTORY_LENGTH)

    client_histories[client_id].append({'role': 'user', 'content': text})

    full_response = ''
    sentence_buffer = ''

    # TTS 后台工作线程
    tts_queue = queue.Queue()

    def tts_worker():
        while True:
            item = tts_queue.get()
            if item is None:
                break
            try:
                clean_text = strip_markdown_for_tts(item)
                if clean_text.strip():
                    logger.info(f'TTS 合成: {clean_text[:30]}...')
                    tts_audio = tts_service.synthesize(clean_text)
                    if tts_audio:
                        _socketio.emit('audio_chunk', {
                            'content': tts_audio,
                            'format': 'wav'
                        }, room=client_id)
            except Exception as e:
                logger.error(f'TTS 合成出错: {e}')
            finally:
                tts_queue.task_done()

    tts_thread = threading.Thread(target=tts_worker, daemon=True)
    tts_thread.start()

    # 流式获取 LLM 回复
    for chunk in llm_service.chat_stream(list(client_histories[client_id])):
        if chunk:
            full_response += chunk
            sentence_buffer += chunk
            emit('text_chunk', {'content': chunk, 'is_final': False})

            # 提取完整句子并发送给 TTS
            sentences, sentence_buffer = extract_complete_sentences(sentence_buffer)
            for sentence in sentences:
                tts_queue.put(sentence)

    # 处理剩余文本
    if sentence_buffer.strip():
        tts_queue.put(sentence_buffer)

    # 通知 TTS 线程结束并等待
    tts_queue.put(None)
    tts_thread.join(timeout=60)

    # 添加助手回复到历史
    client_histories[client_id].append({'role': 'assistant', 'content': full_response})

    # 检测功能调用标记
    function_calls = detect_function_call(full_response)
    if function_calls:
        logger.info(f'检测到功能调用: {function_calls}')
        for func_name in function_calls:
            if func_name in FUNCTION_PORTS:
                emit('function_call', {
                    'function': func_name,
                    'config': FUNCTION_PORTS[func_name]
                })

    emit('text_complete', {'content': full_response})
    emit('audio_done', {'success': True})


def process_audio_message(client_id, audio_data, audio_format):
    """处理语音消息"""
    logger.info('开始 ASR 识别...')
    recognized_text = asr_service.recognize(audio_data, audio_format)

    if not recognized_text:
        emit('error', {'content': '语音识别失败，请重试'})
        return

    logger.info(f'ASR 识别结果: {recognized_text}')
    emit('asr_result', {'content': recognized_text})
    process_text_message(client_id, recognized_text)
