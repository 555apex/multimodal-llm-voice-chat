from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import asyncio
import tempfile
from typing import Protocol

from .config import Settings
from .model_cache import resolve_model_reference


@dataclass(frozen=True)
class Transcription:
    text: str
    language: str
    duration_seconds: float


class AsrEngine(Protocol):
    def load(self) -> None:
        ...

    def transcribe(self, audio: bytes, suffix: str) -> Transcription:
        ...


class TtsEngine(Protocol):
    async def synthesize(self, text: str) -> bytes:
        ...


class FasterWhisperAsrEngine:
    def __init__(self, settings: Settings):
        self._settings = settings
        self._model = None

    def load(self) -> None:
        from faster_whisper import WhisperModel

        model_reference = resolve_model_reference(self._settings)
        self._model = WhisperModel(
            model_reference,
            device=self._settings.asr_device,
            compute_type=self._settings.asr_compute_type,
            download_root=self._settings.asr_download_root,
        )

    def transcribe(self, audio: bytes, suffix: str) -> Transcription:
        if self._model is None:
            raise RuntimeError("ASR model is not ready")
        temporary_path = ""
        try:
            with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as temporary:
                temporary.write(audio)
                temporary_path = temporary.name
            segments, info = self._model.transcribe(
                temporary_path,
                language=self._settings.asr_language,
                beam_size=5,
                vad_filter=True,
                vad_parameters={"min_silence_duration_ms": 500},
                initial_prompt=self._settings.asr_initial_prompt,
                condition_on_previous_text=False,
            )
            text = "".join(segment.text for segment in segments).strip()
            language = getattr(info, "language", self._settings.asr_language)
            duration = float(getattr(info, "duration", 0.0) or 0.0)
            return Transcription(text=text, language=language, duration_seconds=duration)
        finally:
            if temporary_path:
                Path(temporary_path).unlink(missing_ok=True)


class EdgeTtsEngine:
    def __init__(self, settings: Settings):
        self._settings = settings

    async def synthesize(self, text: str) -> bytes:
        import edge_tts

        communicator = edge_tts.Communicate(
            text,
            self._settings.tts_voice,
            rate=self._settings.tts_rate,
            volume=self._settings.tts_volume,
            pitch=self._settings.tts_pitch,
        )
        chunks = bytearray()
        async for chunk in communicator.stream():
            if chunk.get("type") == "audio":
                chunks.extend(chunk["data"])
        if not chunks:
            raise RuntimeError("TTS service returned no audio")
        return bytes(chunks)


async def load_asr(engine: AsrEngine) -> None:
    await asyncio.to_thread(engine.load)
