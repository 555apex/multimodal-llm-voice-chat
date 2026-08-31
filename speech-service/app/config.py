from dataclasses import dataclass
import os


def _positive_int(name: str, default: int) -> int:
    value = int(os.getenv(name, str(default)))
    if value <= 0:
        raise ValueError(f"{name} must be positive")
    return value


@dataclass(frozen=True)
class Settings:
    asr_model: str
    asr_device: str
    asr_compute_type: str
    asr_download_root: str
    asr_model_base_url: str
    asr_language: str
    asr_initial_prompt: str
    asr_max_concurrency: int
    max_audio_bytes: int
    tts_voice: str
    tts_rate: str
    tts_volume: str
    tts_pitch: str
    max_tts_characters: int

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(
            asr_model=os.getenv("SPEECH_ASR_MODEL", "small"),
            asr_device=os.getenv("SPEECH_ASR_DEVICE", "cpu"),
            asr_compute_type=os.getenv("SPEECH_ASR_COMPUTE_TYPE", "int8"),
            asr_download_root=os.getenv("SPEECH_ASR_DOWNLOAD_ROOT", "/models"),
            asr_model_base_url=os.getenv("SPEECH_ASR_MODEL_BASE_URL", "").strip(),
            asr_language=os.getenv("SPEECH_ASR_LANGUAGE", "zh"),
            asr_initial_prompt=os.getenv(
                "SPEECH_ASR_INITIAL_PROMPT",
                "福建，福州，厦门，泉州，漳州，莆田，宁德，龙岩，三明，南平，"
                "福建省，国道，省道，G104，北京平潭，宁德市，福州市，通行态势，拥堵异常，"
                "发展趋势，未来一到两小时，持续拥堵，逐渐缓解，"
                "实际通行能力，设计通行能力，通行能力利用率，瓶颈路线，严重瓶颈，应急调度，"
                "交通枢纽，卡口，交调站，区域交通联系，区域交通压力，日均流量，活跃卡口，"
                "车型结构，小型客车，中型客车，大型货车，出行规律，早高峰，晚高峰，工作日，周末",
            ),
            asr_max_concurrency=_positive_int("SPEECH_ASR_MAX_CONCURRENCY", 1),
            max_audio_bytes=_positive_int("SPEECH_MAX_AUDIO_BYTES", 10 * 1024 * 1024),
            tts_voice=os.getenv("SPEECH_TTS_VOICE", "zh-CN-XiaoxiaoNeural"),
            tts_rate=os.getenv("SPEECH_TTS_RATE", "+0%"),
            tts_volume=os.getenv("SPEECH_TTS_VOLUME", "+0%"),
            tts_pitch=os.getenv("SPEECH_TTS_PITCH", "+0Hz"),
            max_tts_characters=_positive_int("SPEECH_MAX_TTS_CHARACTERS", 500),
        )
