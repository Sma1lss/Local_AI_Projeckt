from __future__ import annotations

import os
import re
from typing import Any

import requests
from fastapi import FastAPI
from pydantic import BaseModel


app = FastAPI(title="router", version="0.1.0")

ROUTER_LLM_URL = os.getenv("ROUTER_LLM_URL", "")
TIMEOUT = float(os.getenv("ROUTER_TIMEOUT_SEC", "6"))

CODE_RE = re.compile(r"\b(code|python|java|c\+\+|cpp|compile|stack trace|bug|test|function|class|api)\b", re.I)
MATH_RE = re.compile(r"\b(math|integral|derivative|matrix|probability|equation|solve)\b", re.I)
DESIGN_RE = re.compile(r"\b(architecture|design|scalable|microservice|latency|throughput|tradeoff)\b", re.I)


class RouteRequest(BaseModel):
    query: str


class RouteResponse(BaseModel):
    route: list[str]
    confidence: float
    source: str


def _rules(query: str) -> tuple[list[str], float] | None:
    tags: list[str] = []
    if CODE_RE.search(query):
        tags.append("code")
    if MATH_RE.search(query):
        tags.append("math")
    if DESIGN_RE.search(query):
        tags.append("design")

    if tags:
        if "code" in tags and "design" in tags:
            tags.append("reasoning")
        return list(dict.fromkeys(tags)), 0.82
    return None


def _llm_fallback(query: str) -> tuple[list[str], float] | None:
    if not ROUTER_LLM_URL:
        return None
    prompt = (
        "Classify query into tags from [code, math, design, general, reasoning]. "
        "Return JSON with keys route (array) and confidence (0..1).\n"
        f"Query: {query}"
    )
    try:
        resp = requests.post(
            ROUTER_LLM_URL.rstrip("/") + "/infer",
            json={"prompt": prompt, "max_tokens": 80, "temperature": 0.0},
            timeout=TIMEOUT,
        )
        resp.raise_for_status()
        data: Any = resp.json()
        text = (data.get("text") or "").lower()
        route = [tag for tag in ["code", "math", "design", "general", "reasoning"] if tag in text]
        if not route:
            route = ["general"]
        return route, 0.6
    except Exception:
        return None


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "router_llm_url": ROUTER_LLM_URL}


@app.post("/route", response_model=RouteResponse)
def route(req: RouteRequest) -> RouteResponse:
    result = _rules(req.query)
    if result is not None:
        route_tags, conf = result
        return RouteResponse(route=route_tags, confidence=conf, source="rules")

    result = _llm_fallback(req.query)
    if result is not None:
        route_tags, conf = result
        return RouteResponse(route=route_tags, confidence=conf, source="llm")

    return RouteResponse(route=["general"], confidence=0.4, source="default")
