from flask_socketio import emit
from flask import request
import threading
import queue
import traceback
import re
import time
from services.asr_service import ASRService
from services.llm_service import LLMService
from services.tts_service import TTSService

asr_service = ASRService()
llm_service = LLMService()
tts_service = TTSService()

client_histories = {}
_socketio = None

tts_semaphore = threading.Semaphore(2)


def register_handlers(socketio):
    global _socketio
    _socketio = socketio

    @socketio.on('connect')
    def handle_connect():
        client_id = request.sid
        client_histories[client_id] = []
        print(f'客户端已连接: {client_id}')
        emit('connected', {'status': 'ok'})

    @socketio.on('disconnect')
    def handle_disconnect():
        client_id = request.sid
        if client_id in client_histories:
            del client_histories[client_id]
        print(f'客户端已断开: {client_id}')

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
            print(f'处理消息时出错: {e}')


def process_text_message(client_id, text, auto_read):
    if client_id not in client_histories:
        client_histories[client_id] = []
    client_histories[client_id].append({'role': 'user', 'content': text})
    print(f'[消息] auto_read={auto_read}')

    tts_queue = queue.Queue()

    def tts_worker():
        while True:
            item = tts_queue.get()
            if item is None:
                break
            chunk_text, idx, char_pos = item
            print(f'[TTS-{idx}] 合成 {len(chunk_text)} 字: {chunk_text[:25]}...')
            with tts_semaphore:
                audio_url = tts_service.synthesize(chunk_text)
            if audio_url:
                _socketio.emit('audio', {
                    'url': audio_url, 'index': idx, 'char_pos': char_pos
                }, room=client_id)
                print(f'[TTS-{idx}] 完成')
            else:
                print(f'[TTS-{idx}] 失败')

    workers = [threading.Thread(target=tts_worker, daemon=True) for _ in range(2)]
    for w in workers:
        w.start()

    full_response = ''
    char_buffer = ''
    tts_buffer = ''
    tts_char_start = 0
    chunk_index = 0

    def flush_tts():
        nonlocal tts_buffer, tts_char_start, chunk_index
        if not auto_read:
            tts_buffer = ''
            return
        if tts_buffer.strip():
            tts_queue.put((tts_buffer.strip(), chunk_index, tts_char_start))
            chunk_index += 1
            tts_buffer = ''
            tts_char_start = len(full_response) - len(char_buffer)

    try:
        json_started = False
        for chunk in llm_service.chat_stream(client_histories[client_id]):
            if chunk:
                full_response += chunk

                # 检测到 JSON 块开始 → 停止发送 text_chunk（前端不显示 JSON）
                if not json_started and '```json' in full_response:
                    json_started = True

                if not json_started:
                    emit('text_chunk', {'content': chunk, 'is_final': False})

                char_buffer += chunk
                time.sleep(0.06)

                while True:
                    m = re.search(r'[^。！？\n，；]+[。！？\n，；]', char_buffer)
                    if not m:
                        if len(char_buffer) > 80:
                            if not tts_buffer:
                                tts_char_start = len(full_response) - len(char_buffer)
                            tts_buffer += char_buffer.strip()
                            char_buffer = ''
                        break
                    clause = m.group().strip()
                    if clause:
                        if not tts_buffer:
                            tts_char_start = len(full_response) - len(char_buffer)
                        tts_buffer += clause
                    char_buffer = char_buffer[m.end():]

                cc = len(re.findall(r'[。！？，；]', tts_buffer))
                if chunk_index == 0:
                    if cc >= 2 and len(tts_buffer) >= 20:
                        flush_tts()
                elif chunk_index == 1:
                    if cc >= 3:
                        flush_tts()
                else:
                    if cc >= 4 or len(tts_buffer) >= 200:
                        flush_tts()

        if char_buffer.strip():
            if not tts_buffer:
                tts_char_start = len(full_response) - len(char_buffer)
            tts_buffer += char_buffer.strip()
        flush_tts()

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
