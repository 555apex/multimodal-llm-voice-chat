import requests
import base64
import logging
from config import Config

logger = logging.getLogger(__name__)


class ASRService:
    """语音识别服务 (mimo-v2.5-asr)"""

    def __init__(self):
        self.api_key = Config.MIMO_API_KEY
        self.api_url = f'{Config.MIMO_API_BASE_URL}/chat/completions'
        self.model = Config.MIMO_ASR_MODEL

    def recognize(self, audio_base64, audio_format='wav', language='auto'):
        """
        识别语音为文本

        Args:
            audio_base64: base64 编码的音频数据（不含 data URL 前缀）
            audio_format: 音频格式 (wav, mp3)
            language: 语种 (auto/zh/en)

        Returns:
            识别出的文本，失败返回 None
        """
        if not self.api_key:
            logger.error('未配置 MIMO_API_KEY')
            return None

        try:
            headers = {
                'Content-Type': 'application/json',
                'Authorization': f'Bearer {self.api_key}'
            }

            # 构建 data URL 格式
            mime_type = 'audio/wav' if audio_format == 'wav' else 'audio/mpeg'
            data_url = f'data:{mime_type};base64,{audio_base64}'

            # mimo-v2.5-asr 请求格式
            payload = {
                'model': self.model,
                'messages': [
                    {
                        'role': 'user',
                        'content': [
                            {
                                'type': 'input_audio',
                                'input_audio': {
                                    'data': data_url
                                }
                            }
                        ]
                    }
                ],
                'asr_options': {
                    'language': language  # auto / zh / en
                }
            }

            response = requests.post(
                self.api_url,
                headers=headers,
                json=payload,
                timeout=30
            )

            if response.status_code == 200:
                result = response.json()
                # 提取识别结果
                if 'choices' in result and len(result['choices']) > 0:
                    content = result['choices'][0]['message']['content']
                    return content.strip() if content else None
            else:
                logger.error(f'ASR 请求失败: {response.status_code} - {response.text}')

        except Exception as e:
            logger.error(f'ASR 识别出错: {e}')
            import traceback
            logger.debug(traceback.format_exc())

        return None
