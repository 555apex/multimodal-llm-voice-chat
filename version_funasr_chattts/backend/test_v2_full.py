"""
版本2完整功能测试脚本
测试 FunASR + ChatTTS 版本的所有功能
"""

import requests
import json
import base64
import time
import sys
import io
from config import Config

# 修复 Windows 终端编码问题
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

def test_health_endpoint():
    """测试健康检查端点"""
    print("=" * 50)
    print("1. 测试健康检查端点")
    print("=" * 50)

    try:
        response = requests.get('http://localhost:5000/health', timeout=5)
        if response.status_code == 200:
            result = response.json()
            print(f"   [SUCCESS] 健康检查通过")
            print(f"   状态: {result.get('status')}")
            print(f"   消息: {result.get('message')}")
            return True
        else:
            print(f"   [FAILED] 健康检查失败: {response.status_code}")
            return False
    except Exception as e:
        print(f"   [ERROR] 无法连接到后端服务: {e}")
        return False

def test_llm_service():
    """测试 LLM 服务"""
    print("\n" + "=" * 50)
    print("2. 测试 LLM 服务")
    print("=" * 50)

    try:
        from services.llm_service import LLMService

        llm = LLMService()

        # 测试流式对话
        print("   测试流式对话...")
        messages = [{'role': 'user', 'content': '你好，请简单介绍一下自己'}]
        response_chunks = []

        for chunk in llm.chat_stream(messages):
            if chunk:
                response_chunks.append(chunk)
                if len(response_chunks) >= 10:
                    break

        if response_chunks:
            full_response = ''.join(response_chunks)
            print(f"   [SUCCESS] LLM 响应成功")
            print(f"   响应内容: {full_response[:100]}...")
            print(f"   收到 {len(response_chunks)} 个片段")
            return True
        else:
            print("   [FAILED] LLM 无响应")
            return False

    except Exception as e:
        print(f"   [ERROR] LLM 测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_asr_service():
    """测试 ASR 服务"""
    print("\n" + "=" * 50)
    print("3. 测试 ASR 服务 (FunASR)")
    print("=" * 50)

    try:
        from services.asr_service import ASRService

        asr = ASRService()

        # 创建一个简单的测试音频（静音）
        print("   创建测试音频...")
        import wave
        import io

        # 生成1秒的静音音频
        sample_rate = 16000
        duration = 1
        num_samples = sample_rate * duration

        # 创建 WAV 文件
        buffer = io.BytesIO()
        with wave.open(buffer, 'wb') as wav_file:
            wav_file.setnchannels(1)
            wav_file.setsampwidth(2)
            wav_file.setframerate(sample_rate)
            # 写入静音数据
            wav_file.writeframes(b'\x00\x00' * num_samples)

        audio_base64 = base64.b64encode(buffer.getvalue()).decode('utf-8')

        # 测试识别
        print("   测试语音识别...")
        result = asr.recognize(audio_base64, 'wav')

        print(f"   [SUCCESS] ASR 服务正常")
        print(f"   识别结果: {result if result else '(静音音频，无识别结果)'}")
        return True

    except Exception as e:
        print(f"   [ERROR] ASR 测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_tts_service():
    """测试 TTS 服务"""
    print("\n" + "=" * 50)
    print("4. 测试 TTS 服务 (ChatTTS)")
    print("=" * 50)

    try:
        from services.tts_service import TTSService

        tts = TTSService()

        # 测试语音合成
        test_text = "你好，这是一个测试"
        print(f"   测试文本: {test_text}")
        print("   测试语音合成...")

        result = tts.synthesize(test_text)

        if result:
            print(f"   [SUCCESS] TTS 服务正常")
            print(f"   音频数据长度: {len(result)} 字符 (base64)")
            return True
        else:
            print("   [FAILED] TTS 无响应")
            return False

    except Exception as e:
        print(f"   [ERROR] TTS 测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_websocket_connection():
    """测试 WebSocket 连接"""
    print("\n" + "=" * 50)
    print("5. 测试 WebSocket 连接")
    print("=" * 50)

    try:
        import socketio

        sio = socketio.Client()
        connected = False
        response_received = False

        @sio.on('connect')
        def on_connect():
            nonlocal connected
            connected = True
            print("   WebSocket 已连接")

        @sio.on('connected')
        def on_connected(data):
            nonlocal response_received
            response_received = True
            print(f"   收到服务器确认: {data}")

        @sio.on('disconnect')
        def on_disconnect():
            print("   WebSocket 已断开")

        # 连接到服务器
        print("   尝试连接 WebSocket...")
        sio.connect('http://localhost:5000')

        # 等待连接建立
        time.sleep(1)

        if connected and response_received:
            print("   [SUCCESS] WebSocket 连接正常")
            sio.disconnect()
            return True
        else:
            print("   [FAILED] WebSocket 连接失败")
            sio.disconnect()
            return False

    except Exception as e:
        print(f"   [ERROR] WebSocket 测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_text_message_flow():
    """测试完整文本消息流程"""
    print("\n" + "=" * 50)
    print("6. 测试完整文本消息流程")
    print("=" * 50)

    try:
        import socketio

        sio = socketio.Client()
        responses = []
        text_chunks = []

        @sio.on('connect')
        def on_connect():
            print("   WebSocket 已连接")

        @sio.on('text_chunk')
        def on_text_chunk(data):
            text_chunks.append(data.get('content', ''))

        @sio.on('text_complete')
        def on_text_complete(data):
            responses.append(data.get('content', ''))

        @sio.on('error')
        def on_error(data):
            print(f"   错误: {data.get('content')}")

        # 连接
        sio.connect('http://localhost:5000')
        time.sleep(0.5)

        # 发送测试消息
        test_message = "你好"
        print(f"   发送测试消息: {test_message}")
        sio.emit('message', {
            'type': 'text',
            'content': test_message,
            'auto_read': False
        })

        # 等待响应
        print("   等待 LLM 响应...")
        timeout = 30
        start_time = time.time()
        while len(responses) == 0 and time.time() - start_time < timeout:
            time.sleep(0.1)

        sio.disconnect()

        if responses:
            full_response = responses[0]
            print(f"   [SUCCESS] 收到 LLM 响应")
            print(f"   响应内容: {full_response[:100]}...")
            print(f"   流式块数: {len(text_chunks)}")
            return True
        else:
            print("   [FAILED] 未收到响应")
            return False

    except Exception as e:
        print(f"   [ERROR] 文本消息测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def run_all_tests():
    """运行所有测试"""
    print("\n" + "=" * 60)
    print("版本2 (FunASR + ChatTTS) 完整功能测试")
    print("=" * 60)

    results = []

    # 运行各项测试
    results.append(("健康检查", test_health_endpoint()))
    results.append(("LLM 服务", test_llm_service()))
    results.append(("ASR 服务", test_asr_service()))
    results.append(("TTS 服务", test_tts_service()))
    results.append(("WebSocket 连接", test_websocket_connection()))
    results.append(("文本消息流程", test_text_message_flow()))

    # 打印测试结果汇总
    print("\n" + "=" * 60)
    print("测试结果汇总")
    print("=" * 60)

    passed = 0
    failed = 0

    for name, result in results:
        status = "✓ PASS" if result else "✗ FAIL"
        print(f"   {name}: {status}")
        if result:
            passed += 1
        else:
            failed += 1

    print("\n" + "-" * 60)
    print(f"总计: {passed + failed} 项测试")
    print(f"通过: {passed} 项")
    print(f"失败: {failed} 项")
    print("-" * 60)

    if failed == 0:
        print("\n🎉 所有测试通过！版本2运行正常。")
        print("\n访问前端: http://localhost:3000")
    else:
        print(f"\n⚠️  有 {failed} 项测试失败，请检查相关服务。")

    return failed == 0

if __name__ == '__main__':
    run_all_tests()
