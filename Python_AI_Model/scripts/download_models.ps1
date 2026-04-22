param(
    [string]$BaseDir = "B:\Local_AI_Projeckt\Python_AI_Model"
)

$ErrorActionPreference = "Stop"

$python = Join-Path $BaseDir ".venv\bin\python.exe"
if (-not (Test-Path $python)) {
    throw "venv python not found: $python"
}

$patchDir = Join-Path $BaseDir "scripts\py_patches"
if (Test-Path $patchDir) {
    $env:PYTHONPATH = "$patchDir;$env:PYTHONPATH"
}

$env:BASE_DIR = $BaseDir
$env:TEMP = Join-Path $BaseDir "tmp"
$env:TMP = Join-Path $BaseDir "tmp"

& $python (Join-Path $BaseDir "scripts\download_assets.py") --manifest (Join-Path $BaseDir "config\models.json") --base-dir $BaseDir --extract-llamacpp

# Fetch prebuilt Windows binaries to avoid local CMake build
& $python (Join-Path $BaseDir "scripts\fetch_llamacpp_windows_release.py")
