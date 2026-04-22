from __future__ import annotations

import os
import shlex
import subprocess
import time
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from services.common import deterministic_embedding, env_int


class InferRequest(BaseModel):
    prompt: str
    max_tokens: int = Field(default=256, ge=1, le=4096)
    temperature: float = Field(default=0.2, ge=0.0, le=2.0)


class InferResponse(BaseModel):
    text: str
    logprob: Optional[float] = None
    latency_ms: int
    model: str


class EmbedRequest(BaseModel):
    text: str


app = FastAPI(title="llama.cpp model server", version="0.1.0")

MODEL_NAME = os.getenv("MODEL_NAME", "codellama-7b")
MODEL_PATH = Path(os.getenv("MODEL_PATH", "models/codellama-7b/codellama-7b-q4.gguf")).resolve()
LLAMACPP_BIN = Path(os.getenv("LLAMACPP_BIN", "llama.cpp/build/bin/llama-cli")).resolve()
if os.name == "nt" and not LLAMACPP_BIN.exists():
    alt = LLAMACPP_BIN.with_suffix(".exe")
    if alt.exists():
        LLAMACPP_BIN = alt
CONTEXT_SIZE = env_int("MODEL_CONTEXT", 2048)
THREADS = env_int("MODEL_THREADS", 8)
TIMEOUT_SEC = env_int("MODEL_TIMEOUT_SEC", 180)


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "model_path": str(MODEL_PATH),
        "binary": str(LLAMACPP_BIN),
        "model_exists": MODEL_PATH.exists(),
        "binary_exists": LLAMACPP_BIN.exists(),
    }


def _build_cmd(req: InferRequest) -> list[str]:
    exe = str(LLAMACPP_BIN)
    cmd = [
        exe,
        "-m",
        str(MODEL_PATH),
        "-p",
        req.prompt,
        "-n",
        str(req.max_tokens),
        "-c",
        str(CONTEXT_SIZE),
        "--threads",
        str(THREADS),
        "--temp",
        str(req.temperature),
        "--no-display-prompt",
    ]
    return cmd


def _extract_text(stdout: str, prompt: str) -> str:
    text = stdout.strip()
    if text.startswith(prompt):
        text = text[len(prompt) :].strip()
    return text


@app.post("/infer", response_model=InferResponse)
def infer(req: InferRequest) -> InferResponse:
    if not MODEL_PATH.exists():
        raise HTTPException(status_code=500, detail=f"Model file not found: {MODEL_PATH}")
    if not LLAMACPP_BIN.exists():
        raise HTTPException(status_code=500, detail=f"llama.cpp binary not found: {LLAMACPP_BIN}")

    cmd = _build_cmd(req)
    started = time.perf_counter()

    try:
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=TIMEOUT_SEC,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        raise HTTPException(status_code=504, detail=f"Generation timeout after {TIMEOUT_SEC}s") from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Failed to run llama.cpp: {exc}") from exc

    latency_ms = int((time.perf_counter() - started) * 1000)

    if proc.returncode != 0:
        stderr = (proc.stderr or "").strip()
        raise HTTPException(status_code=500, detail=f"llama.cpp failed rc={proc.returncode}: {stderr[:1000]}")

    text = _extract_text(proc.stdout or "", req.prompt)
    return InferResponse(text=text, logprob=None, latency_ms=latency_ms, model=MODEL_NAME)


@app.post("/embed")
def embed(req: EmbedRequest) -> list[float]:
    return deterministic_embedding(req.text)


@app.get("/debug/cmd")
def debug_cmd() -> dict:
    sample = InferRequest(prompt="def fib(n):", max_tokens=64, temperature=0.1)
    return {
        "cmd": shlex.join(_build_cmd(sample)),
        "binary_exists": LLAMACPP_BIN.exists(),
        "model_exists": MODEL_PATH.exists(),
    }
