"""
API 测试脚本
用于诊断 DashScope LLM 服务是否正常工作
"""

import requests
import json
from config import Config


def test_dashscope_api():
    """测试 DashScope API 连接"""
    print("=" * 50)
    print("DashScope API 连接测试")
    print("=" * 50)

    # 检查 API Key
    print("\n1. 检查 API Key 配置...")
    if Config.DASHSCOPE_API_KEY:
        print(f"   API Key: {Config.DASHSCOPE_API_KEY[:20]}...")
    else:
        print("   [ERROR] API Key 未配置")
        return

    print(f"   LLM Base URL: {Config.DASHSCOPE_LLM_BASE_URL}")
    print(f"   LLM Model: {Config.DASHSCOPE_LLM_MODEL}")

    # 测试 LLM
    print("\n2. 测试 DashScope LLM（OpenAI 兼容模式）...")
    try:
        headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {Config.DASHSCOPE_API_KEY}'
        }

        payload = {
            'model': Config.DASHSCOPE_LLM_MODEL,
            'messages': [
                {'role': 'user', 'content': '你好，请用一句话介绍你自己'}
            ],
            'stream': False,
            'max_tokens': 50
        }

        response = requests.post(
            f'{Config.DASHSCOPE_LLM_BASE_URL}/chat/completions',
            headers=headers,
            json=payload,
            timeout=30
        )

        print(f"   响应状态码: {response.status_code}")

        if response.status_code == 200:
            result = response.json()
            content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
            print(f"   [SUCCESS] LLM 连接成功!")
            print(f"   响应内容: {content[:100]}")
            return True
        else:
            print(f"   [FAILED] 请求失败")
            print(f"   错误: {response.text[:200]}")

    except Exception as e:
        print(f"   [ERROR] 请求出错: {e}")

    print("\n" + "=" * 50)
    print("结论：DashScope API 连接失败")
    print("请检查：")
    print("1. API Key 是否正确（以 sk- 开头）")
    print("2. 百炼平台是否已开通模型服务")
    print("3. 账户是否有可用额度")
    print("=" * 50)

    return False


if __name__ == '__main__':
    test_dashscope_api()
