from fastapi.testclient import TestClient

from app.config import Settings
from app.engines import Transcription
from app.main import create_app


class FakeAsr:
    def load(self) -> None:
        return None

    def transcribe(self, audio: bytes, suffix: str) -> Transcription:
        assert audio == b"fake-audio"
        assert suffix == ".webm"
        return Transcription("福州五四路现在拥堵吗", "zh", 1.25)


class FakeTts:
    def load(self) -> None:
        return None

    async def synthesize(self, text: str) -> bytes:
        assert text == "当前道路通行平稳。"
        return b"ID3-fake-mp3"


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


def client() -> TestClient:
    application = create_app(settings(), FakeAsr(), FakeTts(), load_model=False)
    return TestClient(application)


def test_health_and_transcription_without_loading_real_model() -> None:
    with client() as test_client:
        assert test_client.get("/health/ready").json()["asrAvailable"] is True
        response = test_client.post(
            "/v1/asr/transcriptions",
            files={"audio": ("question.webm", b"fake-audio", "audio/webm")},
        )
        assert response.status_code == 200
        assert response.json() == {
            "text": "福州五四路现在拥堵吗",
            "language": "zh",
            "durationMs": 1250,
        }


def test_rejects_unsupported_and_oversized_audio() -> None:
    with client() as test_client:
        unsupported = test_client.post(
            "/v1/asr/transcriptions",
            files={"audio": ("question.txt", b"text", "text/plain")},
        )
        oversized = test_client.post(
            "/v1/asr/transcriptions",
            files={"audio": ("question.webm", b"x" * 1025, "audio/webm")},
        )
        assert unsupported.status_code == 415
        assert oversized.status_code == 413


def test_returns_mp3_from_local_tts_engine() -> None:
    with client() as test_client:
        response = test_client.post(
            "/v1/tts/speech", json={"text": "当前道路通行平稳。"}
        )
        assert response.status_code == 200
        assert response.headers["content-type"] == "audio/mpeg"
        assert response.content == b"ID3-fake-mp3"
