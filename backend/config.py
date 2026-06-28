import os
from dotenv import load_dotenv

load_dotenv()


class Config:
    """应用配置"""

    # Flask 配置
    SECRET_KEY = os.getenv('SECRET_KEY', 'dev-secret-key-change-in-production')
    DEBUG = os.getenv('FLASK_DEBUG', 'True').lower() == 'true'

    # ── DashScope API 配置 ──
    DASHSCOPE_API_KEY = os.getenv('DASHSCOPE_API_KEY', '')

    # LLM（OpenAI 兼容模式）
    DASHSCOPE_LLM_BASE_URL = 'https://dashscope.aliyuncs.com/compatible-mode/v1'
    DASHSCOPE_LLM_MODEL = os.getenv('DASHSCOPE_LLM_MODEL', 'qwen-plus')

    # TTS（Edge-TTS，微软免费引擎）
    EDGE_TTS_VOICE = os.getenv('EDGE_TTS_VOICE', 'zh-CN-XiaoxiaoNeural')
    EDGE_TTS_RATE = os.getenv('EDGE_TTS_RATE', '+0%')  # 语速: -50% ~ +100%
    EDGE_TTS_PITCH = os.getenv('EDGE_TTS_PITCH', '+0Hz')  # 音调

    # ── 本地 ASR 配置（faster-whisper）──
    WHISPER_MODEL_SIZE = os.getenv('WHISPER_MODEL_SIZE', 'small')

    # ── 音频配置 ──
    AUDIO_SAMPLE_RATE = 16000
    AUDIO_CHANNELS = 1

    # ── 高德地图 API 配置 ──
    GAODE_API_KEY = os.getenv('GAODE_API_KEY', '')

    # ── 服务端口 ──
    PORT = int(os.getenv('PORT', 5001))
