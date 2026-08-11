FROM python:3.11-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    SPEECH_ASR_DOWNLOAD_ROOT=/models

WORKDIR /app

COPY requirements.txt ./
RUN pip install --upgrade pip && pip install -r requirements.txt

RUN useradd --create-home --uid 10001 speech \
    && mkdir -p /models \
    && chown -R speech:speech /app /models

FROM base AS runtime

COPY --chown=speech:speech app ./app

USER speech

EXPOSE 8091

HEALTHCHECK --interval=15s --timeout=5s --start-period=180s --retries=5 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8091/health/ready', timeout=4)" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8091", "--workers", "1"]

FROM base AS test

COPY requirements-test.txt ./
RUN pip install -r requirements-test.txt
COPY --chown=speech:speech app ./app
COPY --chown=speech:speech tests ./tests
USER speech

CMD ["python", "-m", "pytest", "-q"]
