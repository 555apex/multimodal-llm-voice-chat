import requests
import base64
import logging
from config import Config

logger = logging.getLogger(__name__)


class TTSService:
    """语音合成服务 (mimo-v2.5-tts)"""

    def __init__(self):
        self.api_key = Config.MIMO_API_KEY
        self.api_url = f'{Config.MIMO_API_BASE_URL}/chat/completions'
        self.model = Config.MIMO_TTS_MODEL
        self.voice = Config.TTS_VOICE
        self.audio_format = Config.TTS_FORMAT

    def synthesize(self, text):
        """
        将文本合成为语音

        Args:
            text: 要合成的文本

        Returns:
            base64 编码的音频数据，失败返回 None
        """
        if not self.api_key:
            logger.error('未配置 MIMO_API_KEY')
            return None

        if not text or not text.strip():
            return None

        try:
            headers = {
                'Content-Type': 'application/json',
                'Authorization': f'Bearer {self.api_key}'
            }

            # mimo-v2.5-tts 接口格式
            # 需要在 messages 中添加 assistant 消息指定合成文本
            payload = {
                'model': self.model,
                'messages': [
                    {
                        'role': 'user',
                        'content': '请朗读以下内容'
                    },
                    {
                        'role': 'assistant',
                        'content': text
                    }
                ],
                'audio': {
                    'format': self.audio_format,
                    'voice': self.voice
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
                # 提取音频数据
                if 'choices' in result and len(result['choices']) > 0:
                    message = result['choices'][0].get('message', {})
                    audio_obj = message.get('audio', {})
                    audio_data = audio_obj.get('data', '')
                    if audio_data:
                        return audio_data
            else:
                logger.error(f'TTS 请求失败: {response.status_code} - {response.text}')

        except Exception as e:
            logger.error(f'TTS 合成出错: {e}')
            import traceback
            logger.debug(traceback.format_exc())

        return None

    def get_available_voices(self):
        """获取可用音色列表"""
        return {
            '中文': ['冰糖', '茉莉', '苏打', '白桦'],
            '英文': ['Mia', 'Chloe', 'Milo', 'Dean'],
            '默认': ['mimo_default']
        }
