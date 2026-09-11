from __future__ import annotations

from contextlib import asynccontextmanager
import asyncio
import logging
import os
import base64
import json
from uuid import UUID
from typing import AsyncIterator

from fastapi import FastAPI, File, HTTPException, UploadFile, Request
from .inference_queue import InferenceQueue
from .engines import InvalidAudioError, AudioTooLongError
from fastapi.responses import Response, StreamingResponse
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
    if tts_engine is None and os.getenv('SPEECH_TTS_ENGINE') == 'cuda-graph':
        from .streaming_tts import StreamingTtsEngine
        tts_engine = StreamingTtsEngine(resolved_settings)
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
            try:
                await load_asr(resolved_asr)
                application.state.asr_ready = True
            except Exception:
                LOGGER.exception('ASR loading failed')
            LOGGER.info(
                "Loading Qwen3-TTS model=%s device=%s dtype=%s voice=%s",
                resolved_settings.tts_model_path,
                resolved_settings.tts_device,
                resolved_settings.tts_dtype,
                resolved_settings.tts_voice,
            )
            try:
                await load_tts(resolved_tts)
                application.state.tts_ready = True
            except Exception:
                LOGGER.exception('TTS loading failed; ASR remains independently available')
        if not load_model:
            application.state.asr_ready = True
            application.state.tts_ready = True
        application.state.ready = application.state.asr_ready or application.state.tts_ready
        yield
        application.state.ready = False
        if hasattr(resolved_tts, 'close'): resolved_tts.close()

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
    application.state.asr_ready = False
    application.state.tts_ready = False
    application.state.asr_queue = InferenceQueue(resolved_settings.asr_max_concurrency)
    application.state.tts_queue = InferenceQueue(resolved_settings.tts_max_concurrency)

    @application.get("/health/live")
    async def live() -> dict[str, str]:
        return {"status": "UP"}

    @application.get("/health/ready")
    async def ready() -> dict[str, object]:
        tts_ready = application.state.tts_ready and (not hasattr(resolved_tts, 'healthy') or resolved_tts.healthy())
        if not application.state.ready:
            raise HTTPException(status_code=503, detail="ASR model is loading")
        return {
            "status": "UP",
            "asrAvailable": application.state.asr_ready,
            "ttsAvailable": tts_ready,
            "ttsStreamingAvailable": tts_ready and hasattr(resolved_tts, 'stream'),
            "asrModel": resolved_settings.asr_model,
            "ttsVoice": resolved_settings.tts_voice,
        }

    @application.post("/v1/asr/transcriptions")
    async def transcribe(request: Request, audio: UploadFile = File(...)) -> dict[str, object]:
        if not application.state.asr_ready: raise HTTPException(503, "ASR is unavailable")
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
            result = await application.state.asr_queue.run(
                lambda cancelled: asyncio.to_thread(resolved_asr.transcribe, payload, suffix), request)
        except HTTPException:
            raise
        except InvalidAudioError as exception:
            raise HTTPException(415, 'Unable to decode recording') from exception
        except AudioTooLongError as exception:
            raise HTTPException(413, 'Recording exceeds 60 seconds') from exception
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
    async def synthesize(body: TtsRequest, request: Request) -> Response:
        if not application.state.tts_ready: raise HTTPException(503, "TTS is unavailable")
        text = body.text.strip()
        if not text:
            raise HTTPException(status_code=400, detail="Text is empty")
        if len(text) > resolved_settings.max_tts_characters:
            raise HTTPException(status_code=413, detail="Text is too long")
        try:
            async def generate(cancelled):
                if getattr(resolved_tts, 'supports_cancellation', False):
                    return await resolved_tts.synthesize(text, cancelled=cancelled)
                return await resolved_tts.synthesize(text)
            request_id=request.headers.get('X-Speech-Request-Id')
            if request_id:
                try:request_id=str(UUID(request_id))
                except ValueError:raise HTTPException(400,'Invalid speech request id')
            audio = await application.state.tts_queue.run(generate, request, request_id)
        except HTTPException:
            raise
        except Exception as exception:
            LOGGER.exception("TTS synthesis failed")
            raise HTTPException(status_code=503, detail="TTS synthesis failed") from exception
        return Response(
            content=audio,
            media_type="audio/mpeg",
            headers={"Cache-Control": "no-store"},
        )

    @application.delete('/v1/tts/requests/{request_id}', status_code=204)
    async def cancel_speech(request_id: UUID):
        application.state.tts_queue.cancel(str(request_id))

    @application.post('/v1/tts/speech/stream')
    async def stream_speech(request: TtsRequest):
        text=request.text.strip()
        if not text: raise HTTPException(400, 'Text is empty')
        if len(text)>resolved_settings.max_tts_characters: raise HTTPException(413, 'Text is too long')
        if not hasattr(resolved_tts, 'stream'): raise HTTPException(501, 'Streaming TTS is unavailable')
        def event(name, data):
            return 'event: '+name+'\ndata: '+json.dumps(data,separators=(',',':'))+'\n\n'
        async def audio():
            yield event('audio.start', {'sampleRate':24000,'channels':1,'format':'s16le'})
            sequence=0
            try:
                async for pcm in resolved_tts.stream(text):
                    yield event('audio.chunk', {'sequence':sequence,'pcm':base64.b64encode(pcm).decode()})
                    sequence+=1
                yield event('audio.completed', {'chunks':sequence})
            except Exception:
                LOGGER.exception('Streaming TTS failed')
                yield event('audio.failed', {'message':'语音合成中断，请重试'})
        return StreamingResponse(audio(),media_type='text/event-stream',headers={'Cache-Control':'no-store','X-Accel-Buffering':'no'})

    return application


app = create_app()
