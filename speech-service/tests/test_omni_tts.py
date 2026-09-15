from app.omni_tts import VllmOmniTtsEngine
from test_main import settings


def test_qwen_omni_payload_requests_chinese_streaming_pcm():
    base = settings()
    configured = type(base)(**{
        **base.__dict__,
        "tts_engine": "vllm-omni",
        "tts_service_url": "http://tts-candidate:8091",
        "tts_service_model": "/models/Qwen3-TTS-1.7B",
    })
    engine = VllmOmniTtsEngine(configured)
    assert engine._payload("道路通行正常。", True) == {
        "model": "/models/Qwen3-TTS-1.7B",
        "input": "道路通行正常。",
        "voice": "serena",
        "language": "Chinese",
        "response_format": "pcm",
        "stream": True,
        "stream_format": "audio",
    }
