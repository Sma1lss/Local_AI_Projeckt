from __future__ import annotations

import os
import time
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from services.common import deterministic_embedding

try:
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer, pipeline

    HF_AVAILABLE = True
except Exception:
    torch = None
    AutoModelForCausalLM = None
    AutoTokenizer = None
    pipeline = None
    HF_AVAILABLE = False


class InferRequest(BaseModel):
    prompt: str
    max_tokens: int = Field(default=256, ge=1, le=2048)
    temperature: float = Field(default=0.2, ge=0.0, le=2.0)


class InferResponse(BaseModel):
    text: str
    logprob: Optional[float] = None
    latency_ms: int
    model: str


class EmbedRequest(BaseModel):
    text: str


app = FastAPI(title="HF model server", version="0.1.0")

MODEL_NAME = os.getenv("MODEL_NAME", "starcoder2-3b")
MODEL_ID = os.getenv("MODEL_ID", "bigcode/starcoder2-3b")
DEVICE = os.getenv("MODEL_DEVICE", "cuda")
USE_BNB = os.getenv("USE_BITSANDBYTES", "1") == "1"

_TEXT_PIPE = None


def _load_pipeline():
    global _TEXT_PIPE
    if _TEXT_PIPE is not None:
        return _TEXT_PIPE

    if not HF_AVAILABLE:
        raise RuntimeError("transformers/torch not installed")

    kwargs = {}
    if USE_BNB:
        kwargs["load_in_4bit"] = True

    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_ID,
        device_map="auto" if DEVICE == "cuda" else None,
        torch_dtype=torch.float16 if DEVICE == "cuda" else torch.float32,
        **kwargs,
    )
    _TEXT_PIPE = pipeline("text-generation", model=model, tokenizer=tokenizer)
    return _TEXT_PIPE


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "model_id": MODEL_ID,
        "hf_available": HF_AVAILABLE,
    }


@app.post("/infer", response_model=InferResponse)
def infer(req: InferRequest) -> InferResponse:
    started = time.perf_counter()

    if not HF_AVAILABLE:
        # deterministic fallback for environments without compiled ML stack
        fallback = req.prompt + "\n# Fallback response: transformers stack is unavailable in this runtime."
        return InferResponse(text=fallback, logprob=None, latency_ms=int((time.perf_counter() - started) * 1000), model=MODEL_NAME)

    try:
        pipe = _load_pipeline()
        outputs = pipe(
            req.prompt,
            max_new_tokens=req.max_tokens,
            temperature=req.temperature,
            do_sample=req.temperature > 0,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"HF inference failed: {exc}") from exc

    latency_ms = int((time.perf_counter() - started) * 1000)
    text = outputs[0].get("generated_text", "") if outputs else ""
    return InferResponse(text=text, logprob=None, latency_ms=latency_ms, model=MODEL_NAME)


@app.post("/embed")
def embed(req: EmbedRequest) -> list[float]:
    return deterministic_embedding(req.text)
