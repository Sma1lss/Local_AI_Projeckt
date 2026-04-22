from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import time
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field


app = FastAPI(title="executor", version="0.1.0")

EXEC_TIMEOUT_SEC = int(os.getenv("EXEC_TIMEOUT_SEC", "10"))
USE_DOCKER = os.getenv("USE_DOCKER_EXECUTOR", "0") == "1"
TMP_ROOT = Path(os.getenv("EXEC_TMP_ROOT", "data/exec_tmp")).resolve()
TMP_ROOT.mkdir(parents=True, exist_ok=True)


class ExecuteRequest(BaseModel):
    language: str = Field(pattern="^(python|cpp|c)$")
    code: str
    tests: str = ""
    stdin: str = ""


class ExecuteResponse(BaseModel):
    exit_code: int
    stdout: str
    stderr: str
    runtime: float
    passed_tests_count: int


def _docker_available() -> bool:
    try:
        p = subprocess.run(["docker", "--version"], capture_output=True, text=True, timeout=3)
        return p.returncode == 0
    except Exception:
        return False


def _run(cmd: list[str], cwd: Path, timeout: int, stdin: str = "") -> subprocess.CompletedProcess:
    return subprocess.run(
        cmd,
        cwd=str(cwd),
        input=stdin,
        capture_output=True,
        text=True,
        timeout=timeout,
        check=False,
    )


def _run_python_local(workdir: Path, req: ExecuteRequest) -> ExecuteResponse:
    main_py = workdir / "main.py"
    test_py = workdir / "test_runner.py"
    main_py.write_text(req.code, encoding="utf-8")

    if req.tests.strip():
        test_py.write_text(req.tests, encoding="utf-8")
        cmd = ["python", str(test_py)]
    else:
        cmd = ["python", str(main_py)]

    started = time.perf_counter()
    proc = _run(cmd, cwd=workdir, timeout=EXEC_TIMEOUT_SEC, stdin=req.stdin)
    runtime = time.perf_counter() - started

    passed = proc.stdout.count("PASSED") if proc.returncode == 0 else 0
    return ExecuteResponse(
        exit_code=proc.returncode,
        stdout=proc.stdout,
        stderr=proc.stderr,
        runtime=runtime,
        passed_tests_count=passed,
    )


def _run_cpp_local(workdir: Path, req: ExecuteRequest) -> ExecuteResponse:
    src = workdir / "main.cpp"
    exe = workdir / "main.exe"
    src.write_text(req.code, encoding="utf-8")

    compile_started = time.perf_counter()
    cproc = _run(["g++", "-O2", "-std=c++17", str(src), "-o", str(exe)], cwd=workdir, timeout=EXEC_TIMEOUT_SEC)
    if cproc.returncode != 0:
        return ExecuteResponse(
            exit_code=cproc.returncode,
            stdout=cproc.stdout,
            stderr=cproc.stderr,
            runtime=time.perf_counter() - compile_started,
            passed_tests_count=0,
        )

    started = time.perf_counter()
    rproc = _run([str(exe)], cwd=workdir, timeout=EXEC_TIMEOUT_SEC, stdin=req.stdin)
    runtime = time.perf_counter() - started
    return ExecuteResponse(
        exit_code=rproc.returncode,
        stdout=rproc.stdout,
        stderr=rproc.stderr,
        runtime=runtime,
        passed_tests_count=1 if rproc.returncode == 0 else 0,
    )


def _run_python_docker(workdir: Path, req: ExecuteRequest) -> ExecuteResponse:
    main_py = workdir / "main.py"
    test_py = workdir / "test_runner.py"
    main_py.write_text(req.code, encoding="utf-8")

    if req.tests.strip():
        test_py.write_text(req.tests, encoding="utf-8")
        command = "python /code/test_runner.py"
    else:
        command = "python /code/main.py"

    cmd = [
        "docker",
        "run",
        "--rm",
        "--memory=512m",
        "--cpus=0.5",
        "-v",
        f"{workdir}:/code",
        "python:3.11",
        "bash",
        "-lc",
        command,
    ]

    started = time.perf_counter()
    proc = _run(cmd, cwd=workdir, timeout=EXEC_TIMEOUT_SEC)
    runtime = time.perf_counter() - started

    passed = proc.stdout.count("PASSED") if proc.returncode == 0 else 0
    return ExecuteResponse(
        exit_code=proc.returncode,
        stdout=proc.stdout,
        stderr=proc.stderr,
        runtime=runtime,
        passed_tests_count=passed,
    )


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "use_docker": USE_DOCKER,
        "docker_available": _docker_available(),
        "exec_timeout_sec": EXEC_TIMEOUT_SEC,
        "tmp_root": str(TMP_ROOT),
    }


@app.post("/execute", response_model=ExecuteResponse)
def execute(req: ExecuteRequest) -> ExecuteResponse:
    if req.language not in {"python", "cpp", "c"}:
        raise HTTPException(status_code=400, detail="Unsupported language")

    workdir = Path(tempfile.mkdtemp(prefix="exec-", dir=str(TMP_ROOT)))
    try:
        if req.language == "python":
            if USE_DOCKER and _docker_available():
                return _run_python_docker(workdir, req)
            return _run_python_local(workdir, req)

        # c and cpp handled by g++ for simplicity.
        return _run_cpp_local(workdir, req)
    except subprocess.TimeoutExpired as exc:
        raise HTTPException(status_code=504, detail=f"Execution timeout after {EXEC_TIMEOUT_SEC}s") from exc
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"Execution failed: {exc}") from exc
    finally:
        try:
            shutil.rmtree(workdir)
        except Exception:
            pass


@app.post("/execute_json")
def execute_json(req: ExecuteRequest) -> dict:
    # Debug endpoint that returns plain dict.
    resp = execute(req)
    return json.loads(resp.model_dump_json()) if hasattr(resp, "model_dump_json") else resp.dict()
