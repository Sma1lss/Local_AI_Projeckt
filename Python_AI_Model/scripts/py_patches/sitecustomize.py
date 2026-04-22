"""Runtime patch for broken tempfile permissions in this environment."""
from __future__ import annotations

import os
import tempfile
import uuid


def _safe_mkdtemp(suffix: str | None = None, prefix: str | None = None, dir: str | None = None):
    suffix = "" if suffix is None else suffix
    prefix = "tmp" if prefix is None else prefix
    root = tempfile.gettempdir() if dir is None else dir
    os.makedirs(root, exist_ok=True)
    for _ in range(1024):
        candidate = os.path.join(root, f"{prefix}{uuid.uuid4().hex}{suffix}")
        try:
            os.makedirs(candidate, exist_ok=False)
            return candidate
        except FileExistsError:
            continue
    raise FileExistsError("Could not allocate temporary directory")


tempfile.mkdtemp = _safe_mkdtemp
