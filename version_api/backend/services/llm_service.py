import requests
import json
import logging
from config import Config

logger = logging.getLogger(__name__)


class LLMService:
    """大语言模型服务 (mimo-v2.5-pro)"""

    def __init__(self):
        self.api_key = Config.MIMO_API_KEY
        self.api_url = f'{Config.MIMO_API_BASE_URL}/chat/completions'
        self.model = Config.MIMO_LLM_MODEL
        self.max_tokens = Config.MAX_COMPLETION_TOKENS

        # 系统提示词
        self.system_prompt = """你是一个友好、专业的AI助手。请用简洁、自然的语言回答用户的问题。
如果用户用中文提问，请用中文回答；如果用英文提问，请用英文回答。"""

    def chat_stream(self, messages):
        """
        流式对话

        Args:
            messages: 对话历史列表，格式: [{'role': 'user'/'assistant', 'content': '...'}]

        Yields:
            生成的文本片段
        """
        if not self.api_key:
            logger.error('未配置 MIMO_API_KEY')
            return

        try:
            headers = {
                'Content-Type': 'application/json',
                'Authorization': f'Bearer {self.api_key}'
            }

            # 构建消息列表（包含系统提示）
            api_messages = [
                {'role': 'system', 'content': self.system_prompt}
            ] + messages

            payload = {
                'model': self.model,
                'messages': api_messages,
                'stream': True,
                'temperature': 1.0,
                'top_p': 0.95,
                'max_completion_tokens': self.max_tokens  # 使用配置的值
            }

            # 发起流式请求
            response = requests.post(
                self.api_url,
                headers=headers,
                json=payload,
                stream=True,
                timeout=60
            )

            if response.status_code != 200:
                print(f'LLM 请求失败: {response.status_code} - {response.text}')
                return

            # 解析 SSE 流
            for line in response.iter_lines():
                if line:
                    line = line.decode('utf-8')

                    # 跳过空行和结束标记
                    if line.startswith('data: '):
                        data_str = line[6:]  # 去掉 'data: ' 前缀

                        if data_str.strip() == '[DONE]':
                            break

                        try:
                            data = json.loads(data_str)
                            if 'choices' in data and len(data['choices']) > 0:
                                delta = data['choices'][0].get('delta', {})
                                content = delta.get('content', '')
                                if content:
                                    yield content
                        except json.JSONDecodeError:
                            continue

        except requests.exceptions.Timeout:
            logger.error('LLM 请求超时')
        except Exception as e:
            logger.error(f'LLM 对话出错: {e}')
            import traceback
            logger.debug(traceback.format_exc())

    def chat(self, messages):
        """
        非流式对话

        Args:
            messages: 对话历史列表

        Returns:
            完整的回复文本
        """
        if not self.api_key:
            logger.error('未配置 MIMO_API_KEY')
            return None

        try:
            headers = {
                'Content-Type': 'application/json',
                'Authorization': f'Bearer {self.api_key}'
            }

            api_messages = [
                {'role': 'system', 'content': self.system_prompt}
            ] + messages

            payload = {
                'model': self.model,
                'messages': api_messages,
                'stream': False,
                'temperature': 1.0,
                'top_p': 0.95,
                'max_completion_tokens': self.max_tokens  # 使用配置的值
            }

            response = requests.post(
                self.api_url,
                headers=headers,
                json=payload,
                timeout=60
            )

            if response.status_code == 200:
                result = response.json()
                if 'choices' in result and len(result['choices']) > 0:
                    return result['choices'][0]['message']['content']
            else:
                logger.error(f'LLM 请求失败: {response.status_code} - {response.text}')

        except Exception as e:
            logger.error(f'LLM 对话出错: {e}')
            import traceback
            logger.debug(traceback.format_exc())

        return None
