import os
import secrets
import logging
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

class Config:
    """应用配置"""

    # Flask 配置
    # 安全改进：如果没有设置 SECRET_KEY，生成随机密钥
    SECRET_KEY = os.getenv('SECRET_KEY') or secrets.token_hex(32)
    DEBUG = os.getenv('FLASK_DEBUG', 'False').lower() == 'true'  # 安全改进：默认关闭调试模式

    # mimo API 配置 (用于 LLM)
    MIMO_API_KEY = os.getenv('MIMO_API_KEY', '')
    MIMO_API_BASE_URL = 'https://api.xiaomimimo.com/v1'
    MIMO_LLM_MODEL = 'mimo-v2.5-pro'

    # 模型缓存路径
    MODEL_CACHE_DIR = os.getenv('MODEL_CACHE_DIR', r'D:\whisper_cosyvoice')

    # 安全改进：检查并创建模型缓存目录
    @staticmethod
    def ensure_model_dir():
        if not os.path.exists(Config.MODEL_CACHE_DIR):
            try:
                os.makedirs(Config.MODEL_CACHE_DIR, exist_ok=True)
                logger.info(f'创建模型缓存目录: {Config.MODEL_CACHE_DIR}')
            except OSError as e:
                logger.warning(f'无法创建模型缓存目录: {e}')

    # ASR 配置 (Whisper)
    ASR_MODEL = os.getenv('ASR_MODEL', 'large-v3')
    ASR_DEVICE = os.getenv('ASR_DEVICE', 'cuda')
    ASR_LANGUAGE = os.getenv('ASR_LANGUAGE', 'zh')
    ASR_MODEL_DIR = os.path.join(MODEL_CACHE_DIR, 'whisper')

    # TTS 配置 (CosyVoice)
    TTS_MODEL = os.getenv('TTS_MODEL', 'CosyVoice-300M')
    TTS_DEVICE = os.getenv('TTS_DEVICE', 'cuda')
    TTS_MODEL_DIR = os.path.join(MODEL_CACHE_DIR, 'cosyvoice')

    # 音频配置
    AUDIO_SAMPLE_RATE = 16000

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
        cls.ensure_model_dir()
        return True
