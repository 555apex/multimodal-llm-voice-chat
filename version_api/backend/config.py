import os
import secrets
from dotenv import load_dotenv

load_dotenv()

class Config:
    """应用配置"""

    # Flask 配置
    # 安全改进：如果没有设置 SECRET_KEY，生成随机密钥
    SECRET_KEY = os.getenv('SECRET_KEY') or secrets.token_hex(32)
    DEBUG = os.getenv('FLASK_DEBUG', 'False').lower() == 'true'  # 安全改进：默认关闭调试模式

    # mimo API 配置
    MIMO_API_KEY = os.getenv('MIMO_API_KEY', '')
    MIMO_API_BASE_URL = 'https://api.xiaomimimo.com/v1'

    # 模型配置
    MIMO_LLM_MODEL = 'mimo-v2.5-pro'
    MIMO_TTS_MODEL = 'mimo-v2.5-tts'
    MIMO_ASR_MODEL = 'mimo-v2.5-asr'

    # TTS 配置
    TTS_VOICE = 'mimo_default'  # 可选：冰糖、茉莉、苏打、白桦、Mia、Chloe、Milo、Dean
    TTS_FORMAT = 'wav'

    # 音频配置
    AUDIO_SAMPLE_RATE = 16000
    AUDIO_CHANNELS = 1

    # LLM 配置
    MAX_COMPLETION_TOKENS = int(os.getenv('MAX_COMPLETION_TOKENS', 2048))  # 性能优化：减少默认值

    # 服务端口
    PORT = int(os.getenv('PORT', 5000))

    # 验证必要的配置
    @classmethod
    def validate(cls):
        """验证配置是否完整"""
        if not cls.MIMO_API_KEY:
            raise ValueError("MIMO_API_KEY 未配置。请在 .env 文件中设置 MIMO_API_KEY")
        return True
