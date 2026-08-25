import asyncio
from types import SimpleNamespace

from app.config import Settings
from app.engines import Qwen3TtsEngine


def settings() -> Settings:
    return Settings(
        asr_model="small",
        asr_model_path="",
        asr_device="cpu",
        asr_compute_type="int8",
        asr_download_root="/tmp/models",
        asr_model_base_url="",
        asr_language="zh",
        asr_initial_prompt="福建道路",
        asr_max_concurrency=1,
        max_audio_bytes=1024,
        tts_model_path="/tmp/qwen3-tts",
        tts_voice="Serena",
        tts_language="Chinese",
        tts_device="cuda:0",
        tts_dtype="bfloat16",
        tts_max_concurrency=1,
        tts_rate="+0%",
        tts_volume="+0%",
        tts_pitch="+0Hz",
        max_tts_characters=100,
    )


class FakeQwenTtsModel:
    def __init__(self) -> None:
        self.arguments = None

    def generate_custom_voice(self, **kwargs):
        self.arguments = kwargs
        return [[0.0, 0.25, -0.25, 0.0]], 24000


def test_qwen_tts_generates_chinese_voice_and_converts_to_mp3(monkeypatch) -> None:
    engine = Qwen3TtsEngine(settings())
    fake_model = FakeQwenTtsModel()
    engine._model = fake_model

    def fake_run(command, *, input, stdout, stderr, check):
        assert command[0] == "ffmpeg"
        assert input.startswith(b"RIFF")
        assert stdout is not None
        assert stderr is not None
        assert check is False
        return SimpleNamespace(returncode=0, stdout=b"ID3-local-mp3", stderr=b"")

    monkeypatch.setattr("app.engines.subprocess.run", fake_run)

    audio = asyncio.run(engine.synthesize("当前道路通行平稳。"))

    assert audio == b"ID3-local-mp3"
    assert fake_model.arguments == {
        "text": "当前道路通行平稳。",
        "language": "Chinese",
        "speaker": "Serena",
    }


def test_qwen_tts_rejects_empty_ffmpeg_output(monkeypatch) -> None:
    engine = Qwen3TtsEngine(settings())
    engine._model = FakeQwenTtsModel()
    monkeypatch.setattr(
        "app.engines.subprocess.run",
        lambda *_args, **_kwargs: SimpleNamespace(returncode=0, stdout=b"", stderr=b""),
    )

    try:
        asyncio.run(engine.synthesize("测试"))
    except RuntimeError as exception:
        assert "no MP3 audio" in str(exception)
    else:
        raise AssertionError("empty FFmpeg output must fail")
