"""Adapter for vLLM-Omni's OpenAI-compatible streaming speech endpoint."""
from __future__ import annotations

import asyncio
import base64
from pathlib import Path
import subprocess
import threading
from urllib.request import urlopen

import httpx


class VllmOmniTtsEngine:
    supports_cancellation = True
    engine_name = "vllm-omni"

    def __init__(self, settings):
        self.settings = settings
        self.sample_rate = settings.tts_sample_rate
        self.model_name = settings.tts_service_model
        self._reference_audio = ""
        self._healthy = False

    def load(self) -> None:
        if not self.settings.tts_service_url:
            raise ValueError("SPEECH_TTS_SERVICE_URL is required for vllm-omni")
        if not self.model_name:
            raise ValueError("SPEECH_TTS_SERVICE_MODEL is required for vllm-omni")
        if self.settings.tts_reference_audio:
            path = Path(self.settings.tts_reference_audio)
            if not path.is_file():
                raise FileNotFoundError(f"TTS reference audio not found: {path}")
            suffix = path.suffix.lower().lstrip(".") or "wav"
            mime = {"wav": "audio/wav", "mp3": "audio/mpeg", "flac": "audio/flac"}.get(
                suffix, "audio/wav"
            )
            self._reference_audio = f"data:{mime};base64,{base64.b64encode(path.read_bytes()).decode()}"
        with urlopen(f"{self.settings.tts_service_url}/health", timeout=10) as response:
            if response.status != 200:
                raise RuntimeError(f"vLLM-Omni health returned {response.status}")
        self._healthy = True

    def healthy(self) -> bool:
        return self._healthy

    def _payload(self, text: str, streaming: bool) -> dict[str, object]:
        payload: dict[str, object] = {
            "model": self.model_name,
            "input": text,
            "voice": self.settings.tts_voice.lower(),
            "language": self.settings.tts_language,
            "response_format": "pcm" if streaming else "mp3",
            "stream": streaming,
        }
        if streaming:
            payload["stream_format"] = "audio"
        if self._reference_audio:
            payload["voice"] = "default"
            payload["ref_audio"] = self._reference_audio
            payload["ref_text"] = self.settings.tts_reference_text
        return payload

    async def stream(self, text: str, cancelled: threading.Event | None = None):
        headers = {"Authorization": f"Bearer {self.settings.tts_api_key}"}
        timeout = httpx.Timeout(180.0, connect=10.0, read=180.0)
        try:
            async with httpx.AsyncClient(timeout=timeout) as client:
                async with client.stream(
                    "POST",
                    f"{self.settings.tts_service_url}/v1/audio/speech",
                    json=self._payload(text, True),
                    headers=headers,
                ) as response:
                    response.raise_for_status()
                    self._healthy = True
                    async for chunk in response.aiter_bytes(65536):
                        if cancelled is not None and cancelled.is_set():
                            return
                        if chunk:
                            if len(chunk) % 2:
                                raise RuntimeError("vLLM-Omni returned an incomplete PCM sample")
                            yield chunk
        except Exception:
            self._healthy = False
            raise

    async def synthesize(self, text: str, cancelled: threading.Event | None = None) -> bytes:
        pcm = b"".join([chunk async for chunk in self.stream(text, cancelled)])
        if not pcm:
            raise RuntimeError("vLLM-Omni returned no audio")

        def encode() -> bytes:
            process = subprocess.run(
                [
                    "ffmpeg", "-hide_banner", "-loglevel", "error",
                    "-f", "s16le", "-ar", str(self.sample_rate), "-ac", "1", "-i", "pipe:0",
                    "-codec:a", "libmp3lame", "-b:a", "96k", "-f", "mp3", "pipe:1",
                ],
                input=pcm,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                check=False,
            )
            if process.returncode or not process.stdout:
                raise RuntimeError("FFmpeg did not produce MP3 audio")
            return process.stdout

        return await asyncio.to_thread(encode)
