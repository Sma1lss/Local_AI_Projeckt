from __future__ import annotations

import json
import os
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Any

import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from services.common import CandidateAnswer, clamp01, cosine, deterministic_embedding, evidence_overlap


app = FastAPI(title="controller", version="0.2.0")

RETRIEVER_URL = os.getenv("RETRIEVER_URL", "http://127.0.0.1:8002")
ROUTER_URL = os.getenv("ROUTER_URL", "http://127.0.0.1:8003")
EXECUTOR_URL = os.getenv("EXECUTOR_URL", "http://127.0.0.1:8004")
SYNTHESIZER_URL = os.getenv("SYNTHESIZER_URL", "")
HTTP_TIMEOUT = float(os.getenv("HTTP_TIMEOUT_SEC", "25"))
THRESH_HIGH = float(os.getenv("THRESH_HIGH", "1.8"))
THRESH_LOW = float(os.getenv("THRESH_LOW", "0.9"))
MAX_WORKERS = int(os.getenv("CONTROLLER_WORKERS", "4"))

DEFAULT_EXPERTS = {
    "codellama": "http://127.0.0.1:8001",
    "starcoder2": "http://127.0.0.1:8011",
    "llama3": "http://127.0.0.1:8010",
    "mpt3": "http://127.0.0.1:8012",
}
EXPERT_ENDPOINTS = json.loads(os.getenv("EXPERT_ENDPOINTS", json.dumps(DEFAULT_EXPERTS)))
JUDGE_URL = os.getenv("JUDGE_URL", EXPERT_ENDPOINTS.get("llama3", ""))


class AskRequest(BaseModel):
    query: str
    mode: str = "normal"
    top_k: int = Field(default=5, ge=1, le=20)


class AskResponse(BaseModel):
    answer: str
    confidence: float
    provenance: list[dict[str, Any]]
    tests: list[dict[str, Any]]
    used_models: list[str]
    candidates: list[dict[str, Any]]


PY_BLOCK_RE = re.compile(r"```python\s*(.*?)```", re.S | re.I)


def _post(url: str, payload: dict[str, Any]) -> dict[str, Any]:
    r = requests.post(url, json=payload, timeout=HTTP_TIMEOUT)
    r.raise_for_status()
    return r.json()


def route_query(query: str) -> list[str]:
    try:
        return _post(ROUTER_URL.rstrip("/") + "/route", {"query": query}).get("route", ["general"])
    except Exception:
        return ["general"]


def retrieve(query: str, top_k: int) -> list[dict[str, Any]]:
    try:
        return _post(RETRIEVER_URL.rstrip("/") + "/search", {"query": query, "top_k": top_k}).get("passages", [])
    except Exception:
        return []


def select_experts(route: list[str]) -> list[tuple[str, str]]:
    selected: list[str] = []
    if "code" in route:
        selected += ["starcoder2", "codellama"]
    if "design" in route or "reasoning" in route or "math" in route:
        selected += ["mpt3", "llama3"]
    if not selected:
        selected = ["llama3", "starcoder2"]
    seen = set()
    out: list[tuple[str, str]] = []
    for name in selected:
        if name in seen or name not in EXPERT_ENDPOINTS:
            continue
        seen.add(name)
        out.append((name, EXPERT_ENDPOINTS[name]))
    if not out and EXPERT_ENDPOINTS:
        for name, url in EXPERT_ENDPOINTS.items():
            if name in seen:
                continue
            out.append((name, url))
    return out


def build_prompt(role: str, query: str, passages: list[dict[str, Any]]) -> str:
    ctx = "\n\n".join([f"[{p.get('doc_id')}] {p.get('snippet','')}" for p in passages])
    return (
        f"SYSTEM: You are specialist {role}. Use evidence. Return answer, short tests, confidence.\n"
        f"CONTEXT:\n{ctx}\n"
        f"QUESTION:\n{query}\n"
    )


def infer_expert(name: str, url: str, prompt: str) -> dict[str, Any]:
    started = time.perf_counter()
    try:
        data = _post(url.rstrip("/") + "/infer", {"prompt": prompt, "max_tokens": 512, "temperature": 0.2})
        return {
            "model": name,
            "text": data.get("text", ""),
            "logprob": float(data.get("logprob", 0.0) or 0.0),
            "latency_ms": int(data.get("latency_ms", (time.perf_counter() - started) * 1000)),
        }
    except Exception as exc:
        return {
            "model": name,
            "text": f"[error] {exc}",
            "logprob": 0.0,
            "latency_ms": int((time.perf_counter() - started) * 1000),
        }


def run_exec(answer: str) -> dict[str, Any]:
    m = PY_BLOCK_RE.search(answer)
    if not m:
        return {"exec_pass": False, "exit_code": -1, "stdout": "", "stderr": ""}
    code = m.group(1).strip()
    if not code:
        return {"exec_pass": False, "exit_code": -1, "stdout": "", "stderr": ""}
    try:
        data = _post(EXECUTOR_URL.rstrip("/") + "/execute", {"language": "python", "code": code, "tests": "", "stdin": ""})
        return {
            "exec_pass": int(data.get("exit_code", 1)) == 0,
            "exit_code": data.get("exit_code", 1),
            "stdout": data.get("stdout", ""),
            "stderr": data.get("stderr", ""),
        }
    except Exception as exc:
        return {"exec_pass": False, "exit_code": -1, "stdout": "", "stderr": str(exc)}


def score(query: str, passages: list[dict[str, Any]], raws: list[dict[str, Any]]) -> tuple[list[CandidateAnswer], list[dict[str, Any]]]:
    qvec = deterministic_embedding(query)
    snippets = [p.get("snippet", "") for p in passages]
    scored: list[CandidateAnswer] = []
    tests: list[dict[str, Any]] = []

    for i, raw in enumerate(raws):
        text = raw.get("text", "")
        e = clamp01(evidence_overlap(text, snippets))
        s = clamp01(cosine(qvec, deterministic_embedding(text)))
        ex = run_exec(text)
        tests.append(
            {
                "candidate_id": f"cand_{i}",
                "model": raw.get("model", "unknown"),
                "exec_pass": bool(ex.get("exec_pass", False)),
                "exit_code": ex.get("exit_code"),
                "stdout": ex.get("stdout", "")[:1000],
                "stderr": ex.get("stderr", "")[:1000],
            }
        )
        scored.append(
            CandidateAnswer(
                candidate_id=f"cand_{i}",
                model=raw.get("model", "unknown"),
                text=text,
                latency_ms=int(raw.get("latency_ms", 0)),
                evidence_score=e,
                sem_sim=s,
                exec_pass=bool(ex.get("exec_pass", False)),
                logprob_norm=clamp01(float(raw.get("logprob", 0.0))),
            )
        )

    scored.sort(key=lambda x: x.total_score, reverse=True)
    return scored, tests


def polish(text: str, passages: list[dict[str, Any]]) -> str:
    if not SYNTHESIZER_URL:
        return text
    ctx = "\n".join([f"[{p.get('doc_id')}] {p.get('snippet','')}" for p in passages[:4]])
    prompt = f"Polish and cite snippet ids.\nEVIDENCE:\n{ctx}\nDRAFT:\n{text}"
    try:
        return _post(SYNTHESIZER_URL.rstrip("/") + "/infer", {"prompt": prompt, "max_tokens": 256, "temperature": 0.1}).get("text", text)
    except Exception:
        return text


def _extract_json(text: str) -> dict[str, Any] | None:
    if not text:
        return None
    # Try direct JSON first
    try:
        return json.loads(text)
    except Exception:
        pass
    # Try to find JSON inside the text
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        blob = text[start : end + 1]
        try:
            return json.loads(blob)
        except Exception:
            return None
    return None


def judge_synthesize(
    query: str,
    passages: list[dict[str, Any]],
    scored: list[CandidateAnswer],
    tests: list[dict[str, Any]],
) -> dict[str, Any] | None:
    if not JUDGE_URL:
        return None

    evidence = [{"id": p.get("doc_id"), "snippet": (p.get("snippet") or "")[:400]} for p in passages]
    candidates = []
    for c in scored[:6]:
        candidates.append(
            {
                "id": c.candidate_id,
                "model": c.model,
                "score": c.total_score,
                "evidence_score": c.evidence_score,
                "sem_sim": c.sem_sim,
                "exec_pass": c.exec_pass,
                "text": c.text[:1200],
            }
        )

    payload = {
        "question": query,
        "evidence": evidence,
        "candidates": candidates,
        "tests": tests,
        "instructions": (
            "You are the judge and synthesizer.\n"
            "1) Analyze each candidate with pros/cons.\n"
            "2) Build the best final answer by merging the strongest parts and fixing mistakes.\n"
            "3) Use evidence snippets where possible.\n"
            "Return ONLY JSON with keys: final_answer, confidence (0..1), pros_cons (short)."
        ),
    }

    prompt = "JUDGE_INPUT:\n" + json.dumps(payload, ensure_ascii=True, indent=2)

    try:
        data = _post(JUDGE_URL.rstrip("/") + "/infer", {"prompt": prompt, "max_tokens": 512, "temperature": 0.1})
        text = (data.get("text") or "").strip()
        parsed = _extract_json(text)
        if parsed and parsed.get("final_answer"):
            return parsed
        if text:
            return {"final_answer": text}
        return None
    except Exception:
        return None


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "retriever": RETRIEVER_URL,
        "router": ROUTER_URL,
        "executor": EXECUTOR_URL,
        "synthesizer": SYNTHESIZER_URL,
        "judge": JUDGE_URL,
        "experts": EXPERT_ENDPOINTS,
    }


@app.post("/ask", response_model=AskResponse)
def ask(req: AskRequest) -> AskResponse:
    query = req.query.strip()
    if not query:
        raise HTTPException(status_code=400, detail="Empty query")

    route = route_query(query)
    passages = retrieve(query, req.top_k)
    experts = select_experts(route)
    if not experts:
        raise HTTPException(status_code=500, detail="No experts configured")

    raws: list[dict[str, Any]] = []
    with ThreadPoolExecutor(max_workers=min(MAX_WORKERS, len(experts))) as pool:
        futures = []
        for name, url in experts:
            role = "code" if name in {"codellama", "starcoder2"} else "reasoning"
            futures.append(pool.submit(infer_expert, name, url, build_prompt(role, query, passages)))
        for f in as_completed(futures):
            raws.append(f.result())

    scored, tests = score(query, passages, raws)
    if not scored:
        raise HTTPException(status_code=500, detail="No candidates")

    best = scored[0]
    confidence = clamp01(best.total_score / max(THRESH_HIGH, 1e-6))

    judge = judge_synthesize(query, passages, scored, tests)
    if judge and judge.get("final_answer"):
        final = judge["final_answer"]
        if "confidence" in judge:
            try:
                confidence = clamp01(float(judge["confidence"]))
            except Exception:
                pass
    else:
        final = best.text
        if best.total_score < THRESH_LOW:
            final = "Low confidence local answer. Refine query or verify model/index setup.\n\n" + final
        elif THRESH_LOW <= best.total_score < THRESH_HIGH and len(scored) >= 2:
            final = "Consensus draft:\n" + "\n\n".join([f"[{c.model}]\n{c.text}" for c in scored[:2]])
        final = polish(final, passages)

    provenance = [
        {"doc_id": p.get("doc_id"), "snippet": p.get("snippet", "")[:500], "score": p.get("score", 0.0)}
        for p in passages
    ]

    return AskResponse(
        answer=final,
        confidence=confidence,
        provenance=provenance,
        tests=tests,
        used_models=[c.model for c in scored],
        candidates=[
            {
                "candidate_id": c.candidate_id,
                "model": c.model,
                "score": c.total_score,
                "evidence_score": c.evidence_score,
                "sem_sim": c.sem_sim,
                "exec_pass": c.exec_pass,
                "latency_ms": c.latency_ms,
            }
            for c in scored
        ],
    )


