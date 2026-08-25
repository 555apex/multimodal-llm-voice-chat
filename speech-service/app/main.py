from __future__ import annotations

from contextlib import asynccontextmanager
import asyncio
import logging
from typing import AsyncIterator

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import Response
from pydantic import BaseModel, Field

from .config import Settings
from .engines import (
    AsrEngine,
    FasterWhisperAsrEngine,
    Qwen3TtsEngine,
    TtsEngine,
    load_asr,
    load_tts,
)

LOGGER = logging.getLogger("uvicorn.error")
ALLOWED_MEDIA_TYPES = {
    "audio/webm": ".webm",
    "audio/ogg": ".ogg",
    "audio/mp4": ".m4a",
    "audio/mpeg": ".mp3",
    "audio/wav": ".wav",
    "audio/x-wav": ".wav",
}


class TtsRequest(BaseModel):
    text: str = Field(min_length=1)


def create_app(
    settings: Settings | None = None,
    asr_engine: AsrEngine | None = None,
    tts_engine: TtsEngine | None = None,
    load_model: bool = True,
) -> FastAPI:
    resolved_settings = settings or Settings.from_environment()
    resolved_asr = asr_engine or FasterWhisperAsrEngine(resolved_settings)
    resolved_tts = tts_engine or Qwen3TtsEngine(resolved_settings)

    @asynccontextmanager
    async def lifespan(application: FastAPI) -> AsyncIterator[None]:
        application.state.ready = False
        if load_model:
            LOGGER.info(
                "Loading faster-whisper model=%s device=%s compute_type=%s",
                resolved_settings.asr_model,
                resolved_settings.asr_device,
                resolved_settings.asr_compute_type,
            )
            await load_asr(resolved_asr)
            LOGGER.info(
                "Loading Qwen3-TTS model=%s device=%s dtype=%s voice=%s",
                resolved_settings.tts_model_path,
                resolved_settings.tts_device,
                resolved_settings.tts_dtype,
                resolved_settings.tts_voice,
            )
            await load_tts(resolved_tts)
        application.state.ready = True
        yield
        application.state.ready = False

    application = FastAPI(
        title="Road Agent Speech Service",
        version="1.0.0",
        lifespan=lifespan,
    )
    application.state.settings = resolved_settings
    application.state.asr = resolved_asr
    application.state.tts = resolved_tts
    application.state.asr_semaphore = asyncio.Semaphore(
        resolved_settings.asr_max_concurrency
    )
    application.state.tts_semaphore = asyncio.Semaphore(
        resolved_settings.tts_max_concurrency
    )
    application.state.ready = False

    @application.get("/health/live")
    async def live() -> dict[str, str]:
        return {"status": "UP"}

    @application.get("/health/ready")
    async def ready() -> dict[str, object]:
        if not application.state.ready:
            raise HTTPException(status_code=503, detail="ASR model is loading")
        return {
            "status": "UP",
            "asrAvailable": True,
            "ttsAvailable": True,
            "asrModel": resolved_settings.asr_model,
            "ttsVoice": resolved_settings.tts_voice,
        }

    @application.post("/v1/asr/transcriptions")
    async def transcribe(audio: UploadFile = File(...)) -> dict[str, object]:
        media_type = (audio.content_type or "").split(";", 1)[0].lower()
        suffix = ALLOWED_MEDIA_TYPES.get(media_type)
        if suffix is None:
            await audio.close()
            raise HTTPException(status_code=415, detail="Unsupported audio media type")
        payload = await audio.read(resolved_settings.max_audio_bytes + 1)
        await audio.close()
        if not payload:
            raise HTTPException(status_code=400, detail="Audio is empty")
        if len(payload) > resolved_settings.max_audio_bytes:
            raise HTTPException(status_code=413, detail="Audio is too large")
        try:
            async with application.state.asr_semaphore:
                result = await asyncio.to_thread(
                    resolved_asr.transcribe, payload, suffix
                )
        except Exception as exception:
            LOGGER.exception("ASR transcription failed")
            raise HTTPException(status_code=503, detail="ASR transcription failed") from exception
        if not result.text:
            raise HTTPException(status_code=422, detail="No speech was recognized")
        return {
            "text": result.text,
            "language": result.language,
            "durationMs": round(result.duration_seconds * 1000),
        }

    @application.post("/v1/tts/speech")
    async def synthesize(request: TtsRequest) -> Response:
        text = request.text.strip()
        if not text:
            raise HTTPException(status_code=400, detail="Text is empty")
        if len(text) > resolved_settings.max_tts_characters:
            raise HTTPException(status_code=413, detail="Text is too long")
        try:
            async with application.state.tts_semaphore:
                audio = await resolved_tts.synthesize(text)
        except Exception as exception:
            LOGGER.exception("TTS synthesis failed")
            raise HTTPException(status_code=503, detail="TTS synthesis failed") from exception
        return Response(
            content=audio,
            media_type="audio/mpeg",
            headers={"Cache-Control": "no-store"},
        )

    return application


app = create_app()
