from __future__ import annotations

import argparse
import logging
import os
import sys
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
class ModelState:
    embed_model_path: Path
    rerank_model_path: Path
    device: torch.device
    api_key: str | None
    max_embedding_length: int
    max_rerank_length: int
    batch_size: int
    embedding_model_id: str
    rerank_model_id: str
    safetensors_backend: str
    embedding_tokenizer: Any | None = None
    embedding_model: Any | None = None
    rerank_tokenizer: Any | None = None
    rerank_model: Any | None = None


def create_app(state: ModelState) -> FastAPI:
    app = FastAPI(title="Local BGE service for WeKnora", version="1.0.0")
    app.state.model_state = state

    @app.on_event("startup")
    def startup() -> None:
        load_models(app.state.model_state)

    @app.get("/health")
    def health() -> dict[str, Any]:
        return {
            "status": "ok",
            "device": str(app.state.model_state.device),
            "embedding_model": str(app.state.model_state.embed_model_path),
            "rerank_model": str(app.state.model_state.rerank_model_path),
        }

    @app.get("/")
    def root() -> dict[str, str]:
        return {"status": "ok", "service": "local-bge-weknora"}

    @app.get("/v1/models")
    def models() -> dict[str, Any]:
        return {
            "object": "list",
            "data": [
                {"id": app.state.model_state.embedding_model_id, "object": "model", "owned_by": "local"},
                {"id": app.state.model_state.rerank_model_id, "object": "model", "owned_by": "local"},
            ],
        }

    @app.post("/v1/embeddings")
    def embeddings(req: EmbeddingRequest, authorization: str | None = Header(default=None)) -> dict[str, Any]:
        check_auth(app.state.model_state, authorization)
        texts = [req.input] if isinstance(req.input, str) else req.input
        if not texts:
            raise HTTPException(status_code=400, detail="input must not be empty")

        vectors = embed_texts(app.state.model_state, texts)
        return {
            "object": "list",
            "data": [
                {"object": "embedding", "index": index, "embedding": vector}
                for index, vector in enumerate(vectors)
            ],
            "model": req.model or app.state.model_state.embedding_model_id,
            "usage": estimate_usage(texts),
        }

    @app.post("/v1/rerank")
    def rerank_v1(req: RerankRequest, authorization: str | None = Header(default=None)) -> dict[str, Any]:
        check_auth(app.state.model_state, authorization)
        return rerank_response(app.state.model_state, req)

    @app.post("/rerank")
    def rerank_plain(req: RerankRequest, authorization: str | None = Header(default=None)) -> dict[str, Any]:
        check_auth(app.state.model_state, authorization)
        return rerank_response(app.state.model_state, req)

    @app.post("/api/v1/rerank")
    def rerank_weknora_cloud_path(req: RerankRequest, authorization: str | None = Header(default=None)) -> dict[str, Any]:
        check_auth(app.state.model_state, authorization)
        return rerank_response(app.state.model_state, req)

    return app


def check_auth(state: ModelState, authorization: str | None) -> None:
    if not state.api_key:
        return
    expected = f"Bearer {state.api_key}"
    if authorization != expected:
        raise HTTPException(status_code=401, detail="invalid api key")


def load_models(state: ModelState) -> None:
    patch_safetensors_backend(state.safetensors_backend)
    dtype = torch.float16 if state.device.type == "cuda" else torch.float32
    logging.info("loading embedding model from %s on %s", state.embed_model_path, state.device)

    state.embedding_tokenizer = AutoTokenizer.from_pretrained(state.embed_model_path)
    state.embedding_model = AutoModel.from_pretrained(
        state.embed_model_path,
        torch_dtype=dtype,
    ).to(state.device)
    state.embedding_model.eval()

    logging.info("loading rerank model from %s on %s", state.rerank_model_path, state.device)
    state.rerank_tokenizer = AutoTokenizer.from_pretrained(state.rerank_model_path)
    state.rerank_model = AutoModelForSequenceClassification.from_pretrained(
        state.rerank_model_path,
        torch_dtype=dtype,
    ).to(state.device)
    state.rerank_model.eval()


def patch_safetensors_backend(backend: str) -> None:
    if backend == "auto":
        backend = "pread" if sys.platform.startswith("win") else "mmap"
    if backend == "mmap":
        return

    import transformers.modeling_utils as modeling_utils
    from safetensors import safe_open as original_safe_open

    def safe_open_with_backend(filename: str | os.PathLike[str], framework: str, device: Any = "cpu", **kwargs: Any) -> Any:
        kwargs.setdefault("backend", backend)
        return original_safe_open(filename, framework=framework, device=device, **kwargs)

    modeling_utils.safe_open = safe_open_with_backend


def embed_texts(state: ModelState, texts: list[str]) -> list[list[float]]:
    assert state.embedding_tokenizer is not None
    assert state.embedding_model is not None

    outputs: list[list[float]] = []
    for start in range(0, len(texts), state.batch_size):
        batch = texts[start : start + state.batch_size]
        encoded = state.embedding_tokenizer(
            batch,
            padding=True,
            truncation=True,
            max_length=state.max_embedding_length,
            return_tensors="pt",
        ).to(state.device)
        with torch.inference_mode():
            hidden = state.embedding_model(**encoded).last_hidden_state
            pooled = hidden[:, 0]
            normalized = F.normalize(pooled, p=2, dim=1)
        outputs.extend(normalized.detach().cpu().float().tolist())
    return outputs


def doc_text(document: str | RerankDocument | dict[str, Any]) -> str:
    if isinstance(document, str):
        return document
    if isinstance(document, RerankDocument):
        return document.text or ""
    value = document.get("text") or document.get("content") or document.get("document") or ""
    return str(value)


def rerank_response(state: ModelState, req: RerankRequest) -> dict[str, Any]:
    docs = [doc_text(document) for document in req.documents]
    if not req.query:
        raise HTTPException(status_code=400, detail="query must not be empty")
    if not docs:
        raise HTTPException(status_code=400, detail="documents must not be empty")

    scores = score_pairs(state, req.query, docs)
    ranked = sorted(enumerate(scores), key=lambda item: item[1], reverse=True)
    if req.top_n:
        ranked = ranked[: req.top_n]

    results: list[dict[str, Any]] = []
    for index, score in ranked:
        item: dict[str, Any] = {"index": index, "relevance_score": float(score), "score": float(score)}
        if req.return_documents:
            item["document"] = {"text": docs[index]}
        results.append(item)

    return {
        "id": f"rerank-{int(time.time() * 1000)}",
        "model": req.model or state.rerank_model_id,
        "results": results,
        "usage": estimate_usage([req.query, *docs]),
    }


def score_pairs(state: ModelState, query: str, docs: list[str]) -> list[float]:
    assert state.rerank_tokenizer is not None
    assert state.rerank_model is not None

    scores: list[float] = []
    pairs = [(query, doc) for doc in docs]
    for start in range(0, len(pairs), state.batch_size):
        batch = pairs[start : start + state.batch_size]
        encoded = state.rerank_tokenizer(
            [pair[0] for pair in batch],
            [pair[1] for pair in batch],
            padding=True,
            truncation=True,
            max_length=state.max_rerank_length,
            return_tensors="pt",
        ).to(state.device)
        with torch.inference_mode():
            logits = state.rerank_model(**encoded).logits
        if logits.shape[-1] == 1:
            batch_scores = logits.squeeze(-1)
        else:
            batch_scores = logits[:, -1]
        scores.extend(batch_scores.detach().cpu().float().tolist())
    return scores


def estimate_usage(texts: list[str]) -> dict[str, int]:
    tokens = sum(max(1, len(text) // 4) for text in texts)
    return {"prompt_tokens": tokens, "total_tokens": tokens}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="OpenAI-compatible local BGE service for WeKnora.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8001)
    parser.add_argument("--embedding-model", type=Path, default=Path("models/bge-m3-ft-final"))
    parser.add_argument("--rerank-model", type=Path, default=Path("models/bge-reranker-v2-m3-ft-final"))
    parser.add_argument("--embedding-model-id", default="bge-m3-ft-final")
    parser.add_argument("--rerank-model-id", default="bge-reranker-v2-m3-ft-final")
    parser.add_argument("--api-key", default=os.getenv("BGE_API_KEY", ""))
    parser.add_argument("--device", default="auto", choices=["auto", "cuda", "cpu"])
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--max-embedding-length", type=int, default=512)
    parser.add_argument("--max-rerank-length", type=int, default=512)
    parser.add_argument("--safetensors-backend", default="auto", choices=["auto", "mmap", "pread"])
    return parser.parse_args()


def resolve_device(name: str) -> torch.device:
    if name == "auto":
        return torch.device("cuda" if torch.cuda.is_available() else "cpu")
    if name == "cuda" and not torch.cuda.is_available():
        raise SystemExit("CUDA requested but torch.cuda.is_available() is false.")
    return torch.device(name)


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    args = parse_args()
    if not args.api_key: raise SystemExit("BGE_API_KEY is required")
    state = ModelState(
        embed_model_path=args.embedding_model.resolve(),
        rerank_model_path=args.rerank_model.resolve(),
        device=resolve_device(args.device),
        api_key=args.api_key,
        max_embedding_length=args.max_embedding_length,
        max_rerank_length=args.max_rerank_length,
        batch_size=args.batch_size,
        embedding_model_id=args.embedding_model_id,
        rerank_model_id=args.rerank_model_id,
        safetensors_backend=args.safetensors_backend,
    )
    for model_path in [state.embed_model_path, state.rerank_model_path]:
        if not model_path.exists():
            raise SystemExit(f"Model path does not exist: {model_path}")

    import uvicorn

    uvicorn.run(create_app(state), host=args.host, port=args.port)


if __name__ == "__main__":
    main()
