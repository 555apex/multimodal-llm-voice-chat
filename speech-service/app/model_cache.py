from __future__ import annotations

import logging
import os
from pathlib import Path
import re
import time
from urllib.parse import quote
from urllib.request import Request, urlopen

from .config import Settings

LOGGER = logging.getLogger("uvicorn.error")
MODEL_FILES = ("config.json", "model.bin", "tokenizer.json", "vocabulary.txt")
CHUNK_SIZE = 1024 * 1024
PROGRESS_STEP = 32 * 1024 * 1024


def resolve_model_reference(settings: Settings) -> str:
    """Use the Hub normally, or a resumable direct mirror for restricted networks."""
    if settings.asr_model_path:
        model_path = Path(settings.asr_model_path)
        if not model_path.is_dir():
            raise FileNotFoundError(f"ASR model directory not found: {model_path}")
        return str(model_path)
    if not settings.asr_model_base_url:
        return settings.asr_model
    safe_model_name = re.sub(r"[^A-Za-z0-9_.-]", "-", settings.asr_model)
    model_directory = Path(settings.asr_download_root) / f"direct-{safe_model_name}"
    model_directory.mkdir(parents=True, exist_ok=True)
    base_url = settings.asr_model_base_url.rstrip("/")
    for file_name in MODEL_FILES:
        destination = model_directory / file_name
        if destination.is_file() and destination.stat().st_size > 0:
            continue
        _download_with_resume(
            f"{base_url}/{quote(file_name)}",
            destination,
        )
    return str(model_directory)


def _download_with_resume(url: str, destination: Path, retries: int = 10) -> None:
    partial = destination.with_name(destination.name + ".part")
    for attempt in range(1, retries + 1):
        current_size = partial.stat().st_size if partial.exists() else 0
        headers = {"User-Agent": "road-agent-speech/1.0"}
        if current_size:
            headers["Range"] = f"bytes={current_size}-"
        try:
            with urlopen(Request(url, headers=headers), timeout=60) as response:
                status = getattr(response, "status", 200)
                append = current_size > 0 and status == 206
                if not append:
                    current_size = 0
                content_length = int(response.headers.get("Content-Length", "0") or 0)
                expected_size = current_size + content_length if content_length else 0
                mode = "ab" if append else "wb"
                written = current_size
                next_progress = ((written // PROGRESS_STEP) + 1) * PROGRESS_STEP
                LOGGER.info("Downloading %s from byte %d", destination.name, written)
                with partial.open(mode) as output:
                    while True:
                        chunk = response.read(CHUNK_SIZE)
                        if not chunk:
                            break
                        output.write(chunk)
                        written += len(chunk)
                        if written >= next_progress:
                            LOGGER.info(
                                "Downloaded %s: %.1f MiB",
                                destination.name,
                                written / 1024 / 1024,
                            )
                            next_progress += PROGRESS_STEP
                if expected_size and written < expected_size:
                    raise OSError(
                        f"incomplete download: expected {expected_size}, got {written}"
                    )
            os.replace(partial, destination)
            LOGGER.info(
                "Downloaded %s: %.1f MiB complete",
                destination.name,
                destination.stat().st_size / 1024 / 1024,
            )
            return
        except Exception as exception:
            if attempt == retries:
                raise
            LOGGER.warning(
                "Download %s failed on attempt %d/%d: %s",
                destination.name,
                attempt,
                retries,
                exception.__class__.__name__,
            )
            time.sleep(min(attempt * 2, 10))
