from flask_socketio import emit
from flask import request
from services.asr_service import ASRService
from services.llm_service import LLMService
from services.tts_service import TTSService
import logging
from collections import deque

logger = logging.getLogger(__name__)

# 初始化服务
asr_service = ASRService()
llm_service = LLMService()
tts_service = TTSService()

# 存储每个客户端的对话历史（限制长度）
MAX_HISTORY_LENGTH = 100
client_histories = {}


def register_handlers(socketio):
    """注册 WebSocket 事件处理器"""

    @socketio.on('connect')
    def handle_connect():
        """客户端连接"""
        client_id = request.sid
        client_histories[client_id] = deque(maxlen=MAX_HISTORY_LENGTH)
        logger.info(f'客户端已连接: {client_id}')
        emit('connected', {'status': 'ok'})

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
        auto_read = data.get('auto_read', True)
        volume = data.get('volume', 0.8)

        try:
            if msg_type == 'text':
                # 文本消息
                user_text = data.get('content', '')
                process_text_message(client_id, user_text, auto_read, volume)

            elif msg_type == 'audio':
                # 语音消息
                audio_data = data.get('content', '')
                audio_format = data.get('format', 'wav')
                process_audio_message(client_id, audio_data, audio_format, auto_read, volume)

            else:
                emit('error', {'content': f'未知消息类型: {msg_type}'})

        except Exception as e:
            logger.error(f'处理消息时出错: {e}')
            import traceback
            logger.debug(traceback.format_exc())
            emit('error', {'content': f'处理消息时出错: {str(e)}'})


def process_text_message(client_id, text, auto_read, volume):
    """处理文本消息"""
    # 添加到对话历史
    if client_id not in client_histories:
        client_histories[client_id] = deque(maxlen=MAX_HISTORY_LENGTH)

    client_histories[client_id].append({
        'role': 'user',
        'content': text
    })

    # 调用 LLM 获取流式回复
    full_response = ''

    for chunk in llm_service.chat_stream(list(client_histories[client_id])):
        if chunk:
            full_response += chunk
            emit('text_chunk', {
                'content': chunk,
                'is_final': False
            })

    # 发送完成信号
    emit('text_complete', {'content': full_response})

    # 添加助手回复到历史
    client_histories[client_id].append({
        'role': 'assistant',
        'content': full_response
    })

    # 如果开启了自动朗读，合成语音
    if auto_read and full_response:
        logger.info(f'开始 TTS 合成: {full_response[:50]}...')
        tts_audio = tts_service.synthesize(full_response)
        if tts_audio:
            emit('audio', {
                'content': tts_audio,
                'format': 'wav'
            })
            logger.info('TTS 合成完成，已发送音频')


def process_audio_message(client_id, audio_data, audio_format, auto_read, volume):
    """处理语音消息"""
    # ASR 识别
    logger.info('开始 ASR 识别...')
    recognized_text = asr_service.recognize(audio_data, audio_format)

    if not recognized_text:
        emit('error', {'content': '语音识别失败，请重试'})
        return

    logger.info(f'ASR 识别结果: {recognized_text}')

    # 发送识别结果给客户端
    emit('asr_result', {'content': recognized_text})

    # 继续处理文本消息（同 process_text_message）
    process_text_message(client_id, recognized_text, auto_read, volume)
