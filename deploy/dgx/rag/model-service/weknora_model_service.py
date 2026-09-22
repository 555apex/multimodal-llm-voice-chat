from __future__ import annotations

import argparse
import os
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import torch
import torch.nn.functional as F
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from transformers import AutoModel, AutoModelForSequenceClassification, AutoTokenizer


class EmbeddingRequest(BaseModel):
    input: str | list[str]
    model: str = "bge-m3-ft-final"
    encoding_format: str | None = None
    dimensions: int | None = None
    truncate_prompt_tokens: int | None = None


class RerankDocument(BaseModel):
    text: str | None = None


class RerankRequest(BaseModel):
    query: str
    documents: list[str | RerankDocument | dict[str, Any]]
    model: str = "bge-reranker-v2-m3-ft-final"
    top_n: int | None = Field(default=None, ge=1)
    return_documents: bool = True
    truncate_prompt_tokens: int | None = None
    additional_data: dict[str, Any] | None = None


@dataclass
class State:
    embedding_path: Path
    rerank_path: Path
    device: torch.device
    api_key: str
    batch_size: int
    embedding_tokenizer: Any = None
    embedding_model: Any = None
    rerank_tokenizer: Any = None
    rerank_model: Any = None


def authenticate(state: State, authorization: str | None) -> None:
    if authorization != f"Bearer {state.api_key}":
        raise HTTPException(status_code=401, detail="invalid api key")


def load(state: State) -> None:
    dtype = torch.float16
    state.embedding_tokenizer = AutoTokenizer.from_pretrained(state.embedding_path)
    state.embedding_model = AutoModel.from_pretrained(state.embedding_path, dtype=dtype).to(state.device).eval()
    # BGE v2-m3 uses a Metaspace pre-tokenizer. Transformers' automatic
    # Mistral regex heuristic is not applicable and crashes when patched.
    state.rerank_tokenizer = AutoTokenizer.from_pretrained(state.rerank_path, fix_mistral_regex=False)
    state.rerank_model = AutoModelForSequenceClassification.from_pretrained(
        state.rerank_path, dtype=dtype
    ).to(state.device).eval()


def embeddings(state: State, texts: list[str]) -> list[list[float]]:
    vectors: list[list[float]] = []
    for start in range(0, len(texts), state.batch_size):
        batch = texts[start : start + state.batch_size]
        encoded = state.embedding_tokenizer(batch, padding=True, truncation=True, max_length=512,
                                            return_tensors="pt").to(state.device)
        with torch.inference_mode():
            pooled = state.embedding_model(**encoded).last_hidden_state[:, 0]
            normalized = F.normalize(pooled, p=2, dim=1)
        vectors.extend(normalized.detach().cpu().float().tolist())
    return vectors


def document_text(value: str | RerankDocument | dict[str, Any]) -> str:
    if isinstance(value, str):
        return value
    if isinstance(value, RerankDocument):
        return value.text or ""
    return str(value.get("text") or value.get("content") or value.get("document") or "")


def estimate_usage(texts: list[str]) -> dict[str, int]:
    tokens = sum(max(1, len(text) // 4) for text in texts)
    return {"prompt_tokens": tokens, "total_tokens": tokens}


def rerank(state: State, request: RerankRequest) -> dict[str, Any]:
    docs = [document_text(item) for item in request.documents]
    if not request.query or not docs:
        raise HTTPException(status_code=400, detail="query and documents must not be empty")
    pairs = [(request.query, doc) for doc in docs]
    scores: list[float] = []
    for start in range(0, len(pairs), state.batch_size):
        batch = pairs[start : start + state.batch_size]
        encoded = state.rerank_tokenizer([item[0] for item in batch], [item[1] for item in batch],
                                         padding=True, truncation=True, max_length=512,
                                         return_tensors="pt").to(state.device)
        with torch.inference_mode():
            logits = state.rerank_model(**encoded).logits
        batch_scores = logits.squeeze(-1) if logits.shape[-1] == 1 else logits[:, -1]
        scores.extend(batch_scores.detach().cpu().float().tolist())
    ranked = sorted(enumerate(scores), key=lambda item: item[1], reverse=True)
    if request.top_n:
        ranked = ranked[: request.top_n]
    return {
        "id": f"rerank-{int(time.time() * 1000)}",
        "model": request.model,
        "results": [
            {"index": index, "relevance_score": float(score), "score": float(score),
             **({"document": {"text": docs[index]}} if request.return_documents else {})}
            for index, score in ranked
        ],
        "usage": estimate_usage([request.query, *docs]),
    }


def create_app(state: State) -> FastAPI:
    app = FastAPI(title="RoadAgent BGE service", version="1.0.0")

    @app.on_event("startup")
    def startup() -> None:
        load(state)

    @app.get("/health")
    def health() -> dict[str, Any]:
        return {"status": "ok", "device": str(state.device), "cuda": torch.cuda.is_available(),
                "embedding_dimension": 1024, "embedding_model": str(state.embedding_path),
                "rerank_model": str(state.rerank_path)}

    @app.get("/")
    def root() -> dict[str, str]:
        return {"status": "ok", "service": "roadagent-bge-weknora"}

    @app.get("/v1/models")
    def models() -> dict[str, Any]:
        return {"object": "list", "data": [
            {"id": "bge-m3-ft-final", "object": "model", "owned_by": "local"},
            {"id": "bge-reranker-v2-m3-ft-final", "object": "model", "owned_by": "local"},
        ]}

    @app.post("/v1/embeddings")
    def embed(request: EmbeddingRequest, authorization: str | None = Header(default=None)) -> dict[str, Any]:
        authenticate(state, authorization)
        texts = [request.input] if isinstance(request.input, str) else request.input
        if not texts:
            raise HTTPException(status_code=400, detail="input must not be empty")
        vectors = embeddings(state, texts)
        return {"object": "list", "model": request.model,
                "data": [{"object": "embedding", "index": i, "embedding": vector}
                         for i, vector in enumerate(vectors)],
                "usage": estimate_usage(texts)}

    @app.post("/v1/rerank")
    @app.post("/rerank")
    @app.post("/api/v1/rerank")
    def rank(request: RerankRequest, authorization: str | None = Header(default=None)) -> dict[str, Any]:
        authenticate(state, authorization)
        return rerank(state, request)

    return app


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8001)
    parser.add_argument("--embedding-model", type=Path, required=True)
    parser.add_argument("--rerank-model", type=Path, required=True)
    parser.add_argument("--device", choices=["cuda"], default="cuda")
    parser.add_argument("--batch-size", type=int, default=8)
    args = parser.parse_args()
    api_key = os.environ.get("BGE_API_KEY", "")
    if not api_key:
        raise SystemExit("BGE_API_KEY is required")
    if not torch.cuda.is_available():
        raise SystemExit("CUDA is required but torch.cuda.is_available() is false")
    state = State(args.embedding_model.resolve(), args.rerank_model.resolve(), torch.device("cuda"),
                  api_key, args.batch_size)
    if not state.embedding_path.exists() or not state.rerank_path.exists():
        raise SystemExit("model path does not exist")
    import uvicorn
    uvicorn.run(create_app(state), host=args.host, port=args.port)


if __name__ == "__main__":
    main()
