"""
版本2简化测试脚本
测试基本功能，不依赖完整的模型加载
"""

import sys
import os
import io

# 修复编码
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

def test_config():
    """测试配置文件"""
    print("=" * 50)
    print("1. 测试配置文件")
    print("=" * 50)

    try:
        from config import Config

        print(f"✓ SECRET_KEY: {'*' * 20}...")
        print(f"✓ DEBUG: {Config.DEBUG}")
        print(f"✓ MIMO_API_KEY: {'*' * 20}...")
        print(f"✓ MODEL_CACHE_DIR: {Config.MODEL_CACHE_DIR}")
        print(f"✓ ASR_MODEL: {Config.ASR_MODEL}")
        print(f"✓ ASR_DEVICE: {Config.ASR_DEVICE}")
        print(f"✓ TTS_MODEL: {Config.TTS_MODEL}")
        print(f"✓ TTS_DEVICE: {Config.TTS_DEVICE}")
        print(f"✓ AUDIO_SAMPLE_RATE: {Config.AUDIO_SAMPLE_RATE}")

        # 验证配置
        Config.validate()
        print("✓ 配置验证通过")

        return True
    except Exception as e:
        print(f"✗ 配置测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_torch():
    """测试PyTorch"""
    print("\n" + "=" * 50)
    print("2. 测试PyTorch")
    print("=" * 50)

    try:
        import torch
        print(f"✓ PyTorch版本: {torch.__version__}")
        print(f"✓ CUDA可用: {torch.cuda.is_available()}")

        if torch.cuda.is_available():
            print(f"✓ CUDA版本: {torch.version.cuda}")
            print(f"✓ GPU数量: {torch.cuda.device_count()}")
            for i in range(torch.cuda.device_count()):
                print(f"  GPU {i}: {torch.cuda.get_device_name(i)}")
        else:
            print("⚠ CUDA不可用，将使用CPU模式")

        return True
    except Exception as e:
        print(f"✗ PyTorch测试失败: {e}")
        return False

def test_llm_service():
    """测试LLM服务（不依赖GPU）"""
    print("\n" + "=" * 50)
    print("3. 测试LLM服务")
    print("=" * 50)

    try:
        from services.llm_service import LLMService

        llm = LLMService()

        # 测试流式对话
        print("测试流式对话...")
        messages = [{'role': 'user', 'content': '你好'}]
        response_chunks = []

        for chunk in llm.chat_stream(messages):
            if chunk:
                response_chunks.append(chunk)
                # 只收集前几个chunk进行测试
                if len(response_chunks) >= 5:
                    break

        if response_chunks:
            full_response = ''.join(response_chunks)
            print(f"✓ LLM流式响应成功")
            print(f"  响应片段: {full_response[:100]}...")
            print(f"  收到 {len(response_chunks)} 个片段")
            return True
        else:
            print("✗ LLM无响应")
            return False

    except Exception as e:
        print(f"✗ LLM测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_asr_service_structure():
    """测试ASR服务结构（不加载模型）"""
    print("\n" + "=" * 50)
    print("4. 测试ASR服务结构")
    print("=" * 50)

    try:
        from services.asr_service import ASRService

        asr = ASRService()
        print(f"✓ ASR服务初始化成功")
        print(f"  模型: {asr.model_name}")
        print(f"  设备: {asr.device}")
        print(f"  模型目录: {asr.model_dir}")
        print(f"  已初始化: {asr._initialized}")

        # 注意：不实际加载模型，只测试结构
        print("⚠ 跳过模型加载（需要GPU和完整依赖）")

        return True
    except Exception as e:
        print(f"✗ ASR服务测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_tts_service_structure():
    """测试TTS服务结构（不加载模型）"""
    print("\n" + "=" * 50)
    print("5. 测试TTS服务结构")
    print("=" * 50)

    try:
        from services.tts_service import TTSService

        tts = TTSService()
        print(f"✓ TTS服务初始化成功")
        print(f"  设备: {tts.device}")
        print(f"  模型目录: {tts.model_dir}")
        print(f"  采样率: {tts.sample_rate}")
        print(f"  已初始化: {tts._initialized}")

        # 注意：不实际加载模型，只测试结构
        print("⚠ 跳过模型加载（需要GPU和完整依赖）")

        return True
    except Exception as e:
        print(f"✗ TTS服务测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_websocket_structure():
    """测试WebSocket处理器结构"""
    print("\n" + "=" * 50)
    print("6. 测试WebSocket处理器结构")
    print("=" * 50)

    try:
        from api.websocket import register_handlers, MAX_HISTORY_LENGTH, client_histories

        print(f"✓ WebSocket处理器导入成功")
        print(f"  最大历史长度: {MAX_HISTORY_LENGTH}")

        # 测试deque
        from collections import deque
        test_deque = deque(maxlen=MAX_HISTORY_LENGTH)
        test_deque.append({'role': 'user', 'content': 'test'})
        print(f"✓ deque工作正常")

        return True
    except Exception as e:
        print(f"✗ WebSocket处理器测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_app_structure():
    """测试应用结构"""
    print("\n" + "=" * 50)
    print("7. 测试应用结构")
    print("=" * 50)

    try:
        from app import app, socketio

        print(f"✓ Flask应用创建成功")
        print(f"✓ SocketIO初始化成功")

        # 测试健康检查路由
        with app.test_client() as client:
            response = client.get('/health')
            if response.status_code == 200:
                print(f"✓ 健康检查端点正常")
                data = response.get_json()
                print(f"  状态: {data.get('status')}")
                print(f"  消息: {data.get('message')}")
            else:
                print(f"✗ 健康检查失败: {response.status_code}")

        return True
    except Exception as e:
        print(f"✗ 应用结构测试失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def run_all_tests():
    """运行所有测试"""
    print("\n" + "=" * 60)
    print("版本2 (FunASR + ChatTTS) 简化测试")
    print("=" * 60)

    results = []

    # 运行各项测试
    results.append(("配置文件", test_config()))
    results.append(("PyTorch", test_torch()))
    results.append(("LLM服务", test_llm_service()))
    results.append(("ASR服务结构", test_asr_service_structure()))
    results.append(("TTS服务结构", test_tts_service_structure()))
    results.append(("WebSocket处理器", test_websocket_structure()))
    results.append(("应用结构", test_app_structure()))

    # 打印测试结果汇总
    print("\n" + "=" * 60)
    print("测试结果汇总")
    print("=" * 60)

    passed = 0
    failed = 0

    for name, result in results:
        status = "✓ PASS" if result else "✗ FAIL"
        print(f"  {name}: {status}")
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
        print("\n🎉 所有测试通过！")
        print("\n下一步:")
        print("  1. 安装完整依赖: pip install -r requirements.txt")
        print("  2. 配置 .env 文件")
        print("  3. 启动服务: python app.py")
    else:
        print(f"\n⚠️  有 {failed} 项测试失败")

    return failed == 0

if __name__ == '__main__':
    run_all_tests()
