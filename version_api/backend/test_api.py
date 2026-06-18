"""
API 测试脚本
用于诊断 LLM 服务是否正常工作
"""

import requests
import json
from config import Config

def test_mimo_api():
    """测试 mimo API 连接"""
    print("=" * 50)
    print("mimo API 连接测试")
    print("=" * 50)

    # 检查 API Key
    print("\n1. 检查 API Key 配置...")
    if Config.MIMO_API_KEY:
        # 安全改进：不打印实际的 API Key
        print(f"   API Key: {'*' * 20}...")
    else:
        print("   [ERROR] API Key 未配置")
        return

    print(f"   API URL: {Config.MIMO_API_BASE_URL}")

    # 测试认证方式一：api-key
    print("\n2. 测试认证方式一：api-key header...")
    try:
        headers = {
            'Content-Type': 'application/json',
            'api-key': Config.MIMO_API_KEY
        }

        payload = {
            'model': Config.MIMO_LLM_MODEL,
            'messages': [
                {'role': 'user', 'content': '你好'}
            ],
            'stream': False,
            'max_completion_tokens': 50
        }

        response = requests.post(
            f'{Config.MIMO_API_BASE_URL}/chat/completions',
            headers=headers,
            json=payload,
            timeout=30
        )

        print(f"   响应状态码: {response.status_code}")

        if response.status_code == 200:
            result = response.json()
            content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
            print(f"   [SUCCESS] 认证方式一成功!")
            print(f"   响应内容: {content[:100]}")
            return True
        else:
            print(f"   [FAILED] 认证方式一失败")
            print(f"   错误: {response.text[:200]}")

    except Exception as e:
        print(f"   [ERROR] 请求出错: {e}")

    # 测试认证方式二：Bearer
    print("\n3. 测试认证方式二：Bearer token...")
    try:
        headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {Config.MIMO_API_KEY}'
        }

        response = requests.post(
            f'{Config.MIMO_API_BASE_URL}/chat/completions',
            headers=headers,
            json=payload,
            timeout=30
        )

        print(f"   响应状态码: {response.status_code}")

        if response.status_code == 200:
            result = response.json()
            content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
            print(f"   [SUCCESS] 认证方式二成功!")
            print(f"   响应内容: {content[:100]}")
            return True
        else:
            print(f"   [FAILED] 认证方式二失败")
            print(f"   错误: {response.text[:200]}")

    except Exception as e:
        print(f"   [ERROR] 请求出错: {e}")

    print("\n" + "=" * 50)
    print("结论：API Key 无效或已过期")
    print("请检查：")
    print("1. API Key 是否正确复制")
    print("2. API Key 是否在有效期内")
    print("3. API Key 是否有访问权限")
    print("=" * 50)

    return False

if __name__ == '__main__':
    test_mimo_api()
