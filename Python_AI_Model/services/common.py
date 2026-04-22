from __future__ import annotations

import hashlib
import math
import os
import re
from dataclasses import dataclass
from typing import Iterable

try:
    from dotenv import load_dotenv

    load_dotenv()
except Exception:
    pass


TOKEN_RE = re.compile(r"[A-Za-z0-9_]+")


@dataclass
class DocumentHit:
    doc_id: str
    score: float
    snippet: str


@dataclass
class CandidateAnswer:
    candidate_id: str
    model: str
    text: str
    latency_ms: int
    evidence_score: float
    sem_sim: float
    exec_pass: bool
    logprob_norm: float

    @property
    def total_score(self) -> float:
        return 10.0 * (1.0 if self.exec_pass else 0.0) + 3.0 * self.evidence_score + 1.5 * self.sem_sim + 0.1 * self.logprob_norm


def env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except Exception:
        return default


def env_float(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)))
    except Exception:
        return default


def tokenize(text: str) -> list[str]:
    return [t.lower() for t in TOKEN_RE.findall(text or "")]


def token_set(text: str) -> set[str]:
    return set(tokenize(text))


def evidence_overlap(answer: str, snippets: Iterable[str]) -> float:
    ans = token_set(answer)
    if not ans:
        return 0.0
    ctx = set()
    for s in snippets:
        ctx.update(tokenize(s))
    if not ctx:
        return 0.0
    return len(ans & ctx) / max(1, len(ans))


def deterministic_embedding(text: str, dim: int = 256) -> list[float]:
    vec = [0.0] * dim
    for token in tokenize(text):
        digest = hashlib.sha256(token.encode("utf-8")).digest()
        idx = int.from_bytes(digest[:4], "little") % dim
        sign = -1.0 if digest[4] % 2 else 1.0
        vec[idx] += sign
    norm = math.sqrt(sum(v * v for v in vec))
    if norm > 0:
        vec = [v / norm for v in vec]
    return vec


def cosine(a: list[float], b: list[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na <= 0 or nb <= 0:
        return 0.0
    return dot / (na * nb)


def clamp01(x: float) -> float:
    if x < 0:
        return 0.0
    if x > 1:
        return 1.0
    return x
