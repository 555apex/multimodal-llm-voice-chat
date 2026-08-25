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
    asr_model_path: str
    asr_device: str
    asr_compute_type: str
    asr_download_root: str
    asr_model_base_url: str
    asr_language: str
    asr_initial_prompt: str
    asr_max_concurrency: int
    max_audio_bytes: int
    tts_model_path: str
    tts_voice: str
    tts_language: str
    tts_device: str
    tts_dtype: str
    tts_max_concurrency: int
    tts_rate: str
    tts_volume: str
    tts_pitch: str
    max_tts_characters: int

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(
            asr_model=os.getenv("SPEECH_ASR_MODEL", "small"),
            asr_model_path=os.getenv("SPEECH_ASR_MODEL_PATH", "").strip(),
            asr_device=os.getenv("SPEECH_ASR_DEVICE", "cpu"),
            asr_compute_type=os.getenv("SPEECH_ASR_COMPUTE_TYPE", "int8"),
            asr_download_root=os.getenv("SPEECH_ASR_DOWNLOAD_ROOT", "/models"),
            asr_model_base_url=os.getenv("SPEECH_ASR_MODEL_BASE_URL", "").strip(),
            asr_language=os.getenv("SPEECH_ASR_LANGUAGE", "zh"),
            asr_initial_prompt=os.getenv(
                "SPEECH_ASR_INITIAL_PROMPT",
                "福建，福州，厦门，泉州，漳州，莆田，宁德，龙岩，三明，南平，"
                "五四路，思明区，道路，路况，拥堵，缓行，交通事故，应急调度",
            ),
            asr_max_concurrency=_positive_int("SPEECH_ASR_MAX_CONCURRENCY", 1),
            max_audio_bytes=_positive_int("SPEECH_MAX_AUDIO_BYTES", 10 * 1024 * 1024),
            tts_model_path=os.getenv(
                "SPEECH_TTS_MODEL_PATH",
                "/models/Qwen--Qwen3-TTS-12Hz-0.6B-CustomVoice",
            ).strip(),
            tts_voice=os.getenv("SPEECH_TTS_VOICE", "Serena"),
            tts_language=os.getenv("SPEECH_TTS_LANGUAGE", "Chinese"),
            tts_device=os.getenv("SPEECH_TTS_DEVICE", "cuda:0"),
            tts_dtype=os.getenv("SPEECH_TTS_DTYPE", "bfloat16"),
            tts_max_concurrency=_positive_int("SPEECH_TTS_MAX_CONCURRENCY", 1),
            tts_rate=os.getenv("SPEECH_TTS_RATE", "+0%"),
            tts_volume=os.getenv("SPEECH_TTS_VOLUME", "+0%"),
            tts_pitch=os.getenv("SPEECH_TTS_PITCH", "+0Hz"),
            max_tts_characters=_positive_int("SPEECH_MAX_TTS_CHARACTERS", 500),
        )
