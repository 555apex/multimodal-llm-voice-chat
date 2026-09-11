from __future__ import annotations

from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
import asyncio
import subprocess
import tempfile
import threading
from typing import Protocol

from .config import Settings
from .model_cache import resolve_model_reference


@dataclass(frozen=True)
class Transcription:
    text: str
    language: str
    duration_seconds: float

class InvalidAudioError(ValueError):
    pass

class AudioTooLongError(ValueError):
    pass


class AsrEngine(Protocol):
    def load(self) -> None:
        ...

    def transcribe(self, audio: bytes, suffix: str) -> Transcription:
        ...


class TtsEngine(Protocol):
    def load(self) -> None:
        ...

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
            from faster_whisper.audio import decode_audio
            try:
                samples = decode_audio(temporary_path, sampling_rate=16000)
            except Exception as error:
                raise InvalidAudioError('Unable to decode recording') from error
            if len(samples) / 16000 > 60.25:
                raise AudioTooLongError('Recording exceeds 60 seconds')
            segments, info = self._model.transcribe(
                samples,
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


class Qwen3TtsEngine:
    supports_cancellation = True
    def __init__(self, settings: Settings):
        self._settings = settings
        self._model = None
        self._request = threading.local()

    def load(self) -> None:
        import torch
        from qwen_tts import Qwen3TTSModel

        if not self._settings.tts_model_path:
            raise ValueError("SPEECH_TTS_MODEL_PATH must not be empty")
        model_path = Path(self._settings.tts_model_path)
        if not model_path.is_dir():
            raise FileNotFoundError(f"TTS model directory not found: {model_path}")
        dtype = getattr(torch, self._settings.tts_dtype, None)
        if dtype is None:
            raise ValueError(f"Unsupported TTS dtype: {self._settings.tts_dtype}")
        self._model = Qwen3TTSModel.from_pretrained(
            str(model_path),
            device_map=self._settings.tts_device,
            dtype=dtype,
            attn_implementation="sdpa",
        )
        # qwen-tts 0.1.1 drops arbitrary stopping_criteria before talker.generate.
        # Check at each talker forward boundary, after the previous CUDA step returns.
        def check_cancelled(_module, _args):
            event = getattr(self._request, 'cancelled', None)
            if event is not None and event.is_set():
                raise RuntimeError('Synthesis cancelled')
        self._model.model.talker.register_forward_pre_hook(check_cancelled)

    async def synthesize(self, text: str, cancelled: threading.Event | None = None) -> bytes:
        def owned_call():
            self._request.cancelled = cancelled
            try:return self._synthesize_blocking(text, cancelled)
            finally:self._request.cancelled = None
        return await asyncio.to_thread(owned_call)

    def _synthesize_blocking(self, text: str, cancelled: threading.Event | None = None) -> bytes:
        if self._model is None:
            raise RuntimeError("TTS model is not ready")
        if cancelled is not None and cancelled.is_set():raise RuntimeError('Synthesis cancelled')
        waveforms, sample_rate = self._model.generate_custom_voice(
            text=text,
            language=self._settings.tts_language,
            speaker=self._settings.tts_voice,
        )
        if cancelled is not None and cancelled.is_set():raise RuntimeError('Synthesis cancelled')
        if not waveforms:
            raise RuntimeError("TTS model returned no waveform")
        waveform = waveforms[0]
        if hasattr(waveform, "detach"):
            waveform = waveform.detach().cpu().numpy()

        import soundfile as soundfile

        wav_buffer = BytesIO()
        soundfile.write(wav_buffer, waveform, sample_rate, format="WAV", subtype="PCM_16")
        process = subprocess.run(
            [
                "ffmpeg",
                "-hide_banner",
                "-loglevel",
                "error",
                "-f",
                "wav",
                "-i",
                "pipe:0",
                "-codec:a",
                "libmp3lame",
                "-b:a",
                "96k",
                "-f",
                "mp3",
                "pipe:1",
            ],
            input=wav_buffer.getvalue(),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
        if process.returncode != 0:
            details = process.stderr.decode("utf-8", errors="replace").strip()
            raise RuntimeError(f"FFmpeg MP3 conversion failed: {details}")
        if not process.stdout:
            raise RuntimeError("FFmpeg returned no MP3 audio")
        return process.stdout


async def load_asr(engine: AsrEngine) -> None:
    await asyncio.to_thread(engine.load)


async def load_tts(engine: TtsEngine) -> None:
    await asyncio.to_thread(engine.load)
