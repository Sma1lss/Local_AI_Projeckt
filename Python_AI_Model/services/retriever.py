from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from services.common import cosine, deterministic_embedding

try:
    import numpy as np
except Exception:
    np = None

try:
    import faiss
except Exception:
    faiss = None

try:
    from sentence_transformers import SentenceTransformer
except Exception:
    SentenceTransformer = None


BASE_DIR = Path(os.getenv("BASE_DIR", ".")).resolve()
DOCS_DIR = Path(os.getenv("DOCS_DIR", BASE_DIR / "data" / "docs")).resolve()
INDEX_DIR = Path(os.getenv("INDEX_DIR", BASE_DIR / "data" / "index")).resolve()
INDEX_DIR.mkdir(parents=True, exist_ok=True)

DOCS_JSON = INDEX_DIR / "docs.json"
EMBS_JSON = INDEX_DIR / "embeddings.json"
FAISS_INDEX = INDEX_DIR / "faiss.index"


app = FastAPI(title="retriever", version="0.1.0")

_docs: list[dict[str, Any]] = []
_embs: list[list[float]] = []
_st_model = None
_faiss_index = None


def _load_st_model():
    global _st_model
    if _st_model is not None:
        return _st_model
    if SentenceTransformer is None:
        return None
    _st_model = SentenceTransformer("all-MiniLM-L6-v2")
    return _st_model


def embed_texts(texts: list[str]) -> list[list[float]]:
    model = _load_st_model()
    if model is not None:
        arr = model.encode(texts, show_progress_bar=False)
        return [list(map(float, row)) for row in arr]
    return [deterministic_embedding(t) for t in texts]


def load_documents(docs_dir: Path) -> list[dict[str, str]]:
    docs: list[dict[str, str]] = []
    for path in docs_dir.rglob("*"):
        if not path.is_file():
            continue
        if path.suffix.lower() not in {".txt", ".md", ".rst", ".py", ".java", ".cpp", ".c", ".h", ".json"}:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except Exception:
            try:
                text = path.read_text(encoding="latin-1")
            except Exception:
                continue

        # Split into medium chunks for retrieval.
        chunk_size = 1200
        for i in range(0, len(text), chunk_size):
            chunk = text[i : i + chunk_size].strip()
            if not chunk:
                continue
            docs.append(
                {
                    "doc_id": f"{path.relative_to(docs_dir).as_posix()}::{i // chunk_size}",
                    "path": str(path),
                    "text": chunk,
                }
            )
    return docs


def save_index(docs: list[dict[str, Any]], embs: list[list[float]]) -> None:
    DOCS_JSON.write_text(json.dumps(docs, ensure_ascii=False, indent=2), encoding="utf-8")
    EMBS_JSON.write_text(json.dumps(embs), encoding="utf-8")


def load_index() -> None:
    global _docs, _embs, _faiss_index
    if DOCS_JSON.exists() and EMBS_JSON.exists():
        _docs = json.loads(DOCS_JSON.read_text(encoding="utf-8"))
        _embs = json.loads(EMBS_JSON.read_text(encoding="utf-8"))

    if faiss is not None and np is not None and FAISS_INDEX.exists():
        _faiss_index = faiss.read_index(str(FAISS_INDEX))


class IndexRequest(BaseModel):
    docs_dir: str = str(DOCS_DIR)


class SearchRequest(BaseModel):
    query: str
    top_k: int = Field(default=5, ge=1, le=50)


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "docs": len(_docs),
        "embeddings": len(_embs),
        "faiss": faiss is not None,
        "faiss_loaded": _faiss_index is not None,
        "sentence_transformers": SentenceTransformer is not None,
        "index_dir": str(INDEX_DIR),
    }


@app.post("/index")
def index(req: IndexRequest) -> dict:
    global _docs, _embs, _faiss_index

    docs_dir = Path(req.docs_dir).resolve()
    if not docs_dir.exists():
        raise HTTPException(status_code=404, detail=f"Docs dir not found: {docs_dir}")

    docs = load_documents(docs_dir)
    if not docs:
        raise HTTPException(status_code=400, detail="No documents found for indexing")

    texts = [d["text"] for d in docs]
    embs = embed_texts(texts)

    _docs = docs
    _embs = embs
    save_index(_docs, _embs)

    _faiss_index = None
    if faiss is not None and np is not None:
        arr = np.array(_embs, dtype="float32")
        idx = faiss.IndexFlatL2(arr.shape[1])
        idx.add(arr)
        faiss.write_index(idx, str(FAISS_INDEX))
        _faiss_index = idx

    return {"indexed_docs": len(_docs), "dim": len(_embs[0]) if _embs else 0}


@app.post("/search")
def search(req: SearchRequest) -> dict:
    if not _docs or not _embs:
        raise HTTPException(status_code=400, detail="Index is empty. Call /index first.")

    qvec = embed_texts([req.query])[0]

    results: list[tuple[int, float]] = []
    if _faiss_index is not None and np is not None:
        q = np.array([qvec], dtype="float32")
        dists, idxs = _faiss_index.search(q, req.top_k)
        for i, d in zip(idxs[0], dists[0]):
            if i < 0 or i >= len(_docs):
                continue
            score = 1.0 / (1.0 + float(d))
            results.append((int(i), score))
    else:
        sims = [(i, cosine(qvec, emb)) for i, emb in enumerate(_embs)]
        sims.sort(key=lambda x: x[1], reverse=True)
        results = sims[: req.top_k]

    passages = []
    for i, score in results:
        d = _docs[i]
        passages.append(
            {
                "doc_id": d["doc_id"],
                "score": float(score),
                "snippet": d["text"][:700],
                "path": d["path"],
            }
        )

    return {"query": req.query, "top_k": req.top_k, "passages": passages}


load_index()
