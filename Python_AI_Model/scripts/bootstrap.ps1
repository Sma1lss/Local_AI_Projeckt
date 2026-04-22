param(
    [string]$BaseDir = "B:\Local_AI_Projeckt\Python_AI_Model"
)

$ErrorActionPreference = "Stop"

Write-Host "[bootstrap] BaseDir: $BaseDir"

$dirs = @(
    "models",
    "services",
    "data",
    "data\docs",
    "data\index",
    "logs",
    "tests",
    "scripts",
    "tmp",
    "config",
    "deploy\systemd"
) | ForEach-Object { Join-Path $BaseDir $_ }

foreach ($dir in $dirs) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

$python = Join-Path $BaseDir ".venv\bin\python.exe"
if (-not (Test-Path $python)) {
    python -m venv (Join-Path $BaseDir ".venv")
}

if (-not (Test-Path $python)) {
    throw "Cannot find venv python: $python"
}

# Inject pip when ensurepip is broken in this MSYS build.
$pipDir = Join-Path $BaseDir ".venv\lib\python3.12\site-packages\pip"
if (-not (Test-Path $pipDir)) {
    $seed = "B:\AI_Helper\python\.venv\lib\python3.12\site-packages"
    if (Test-Path (Join-Path $seed "pip")) {
        Copy-Item -Recurse -Force (Join-Path $seed "pip") (Join-Path $BaseDir ".venv\lib\python3.12\site-packages\")
        Copy-Item -Recurse -Force (Join-Path $seed "pip-25.0.1.dist-info") (Join-Path $BaseDir ".venv\lib\python3.12\site-packages\") -ErrorAction SilentlyContinue
    }
}

$patchDir = Join-Path $BaseDir "scripts\py_patches"
New-Item -ItemType Directory -Force -Path $patchDir | Out-Null

@"
"""Runtime patch for broken tempfile permissions in this environment."""
from __future__ import annotations

import os
import tempfile
import uuid


def _safe_mkdtemp(suffix=None, prefix=None, dir=None):
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
"@ | Set-Content -Path (Join-Path $patchDir "sitecustomize.py") -Encoding UTF8

$env:PYTHONPATH = "$patchDir;$env:PYTHONPATH"
$env:TEMP = Join-Path $BaseDir "tmp"
$env:TMP = Join-Path $BaseDir "tmp"

& $python -m pip --version
& $python -m pip install -r (Join-Path $BaseDir "requirements.windows-mingw.txt")

Write-Host "[bootstrap] completed"
Write-Host "[next] $python scripts\download_assets.py --manifest config\models.json"
