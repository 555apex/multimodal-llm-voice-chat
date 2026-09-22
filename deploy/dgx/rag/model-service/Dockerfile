ARG RAG_MODEL_BASE_IMAGE=nvcr.io/nvidia/pytorch@sha256:d724ba5b68075cd3b96eefbc510a45d36e60dd16fd70b217708625f1f4b37bc1
FROM ${RAG_MODEL_BASE_IMAGE}

ARG PIP_INDEX_URL=https://pypi.org/simple

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HF_HUB_OFFLINE=1 \
    TRANSFORMERS_OFFLINE=1
WORKDIR /app
COPY requirements.txt /app/requirements.txt
RUN python -m pip install --no-cache-dir --index-url "${PIP_INDEX_URL}" -r /app/requirements.txt
COPY weknora_model_service.py /app/weknora_model_service.py
USER 1000:1000
ENTRYPOINT ["python", "/app/weknora_model_service.py"]
CMD ["--host", "0.0.0.0", "--port", "8001", "--device", "cuda", "--embedding-model", "/models/bge-m3-ft-final", "--rerank-model", "/models/bge-reranker-v2-m3-ft-final"]
