import requests
import json
import logging
from config import Config

logger = logging.getLogger(__name__)


class LLMService:
    """大语言模型服务 (支持 DeepSeek / mimo 等 OpenAI 兼容 API)"""

    def __init__(self):
        # 优先使用 LLM_API_KEY，兼容 MIMO_API_KEY
        self.api_key = Config.LLM_API_KEY or Config.MIMO_API_KEY
        self.api_url = f'{Config.LLM_API_BASE_URL}/chat/completions'
        self.model = Config.LLM_MODEL
        self.provider = Config.LLM_PROVIDER

        # 系统提示词
        self.system_prompt = """你是一个友好、专业的AI助手。请用简洁、自然的语言回答用户的问题。
如果用户用中文提问，请用中文回答；如果用英文提问，请用英文回答。"""

        logger.info(f'LLM 服务初始化: provider={self.provider}, model={self.model}, url={self.api_url}')

    def chat_stream(self, messages):
        """
        流式对话

        Args:
            messages: 对话历史列表，格式: [{'role': 'user'/'assistant', 'content': '...'}]

        Yields:
            生成的文本片段
        """
        if not self.api_key:
            logger.error('未配置 LLM API Key，请在 .env 文件中设置 LLM_API_KEY')
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
                'max_tokens': Config.MAX_COMPLETION_TOKENS
            }

            logger.info(f'发送 LLM 请求: {self.model}, messages={len(api_messages)}条')

            # 发起流式请求
            response = requests.post(
                self.api_url,
                headers=headers,
                json=payload,
                stream=True,
                timeout=60
            )

            if response.status_code != 200:
                logger.error(f'LLM 请求失败: {response.status_code} - {response.text}')
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
