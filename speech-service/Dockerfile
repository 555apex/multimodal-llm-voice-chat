ARG SPEECH_BASE_IMAGE=nvcr.io/nvidia/pytorch@sha256:d724ba5b68075cd3b96eefbc510a45d36e60dd16fd70b217708625f1f4b37bc1
ARG TEST_BASE_IMAGE=dockerproxy.net/library/python:3.12-slim
# The default PyPI endpoint is intermittently unreachable from this DGX and has
# served incomplete version lists (starlette>=0.46.0 reported as "none"), which
# breaks dependency resolution. Default to a reachable mirror; override to switch.
ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple

FROM ${SPEECH_BASE_IMAGE} AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_INDEX_URL=${PIP_INDEX_URL} \
    SPEECH_ASR_DOWNLOAD_ROOT=/models

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg libsndfile1 curl patch \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
COPY patches/qwen_tts_lazy_torchaudio.patch /tmp/qwen_tts_lazy_torchaudio.patch
RUN python -m pip install --upgrade pip \
    && python -m pip install -r requirements.txt \
    && python -m pip uninstall -y torchaudio \
    && patch -p1 -d /usr/local/lib/python3.12/dist-packages/qwen_tts \
        < /tmp/qwen_tts_lazy_torchaudio.patch \
    && rm -f /tmp/qwen_tts_lazy_torchaudio.patch

RUN groupadd --gid 10001 speech \
    && useradd --create-home --uid 10001 --gid 10001 speech \
    && mkdir -p /models \
    && chown -R speech:speech /app /models

FROM base AS runtime

COPY --chown=speech:speech app ./app

USER speech

EXPOSE 8091

HEALTHCHECK --interval=30s --timeout=5s --start-period=15m --retries=5 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8091/health/ready', timeout=4)" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8091", "--workers", "1"]

FROM ${TEST_BASE_IMAGE} AS test

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_INDEX_URL=${PIP_INDEX_URL}

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements-test.txt ./
RUN python -m pip install --upgrade pip \
    && python -m pip install \
        fastapi==0.139.2 \
        uvicorn==0.51.0 \
        python-multipart==0.0.20 \
        soundfile==0.13.1 \
        -r requirements-test.txt

COPY app ./app
COPY tests ./tests

CMD ["python", "-m", "pytest", "-q"]
