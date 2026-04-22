param(
    [string]$BaseDir = "B:\Local_AI_Projeckt\Python_AI_Model"
)

$ErrorActionPreference = "Stop"

function Import-DotEnv([string]$EnvPath) {
    if (-not (Test-Path $EnvPath)) {
        return
    }
    foreach ($line in Get-Content -LiteralPath $EnvPath) {
        $l = $line.Trim()
        if (-not $l) { continue }
        if ($l.StartsWith("#")) { continue }
        $parts = $l.Split("=", 2)
        if ($parts.Count -ne 2) { continue }
        $key = $parts[0].Trim()
        $val = $parts[1].Trim().Trim('"')
        if ($key) {
            Set-Item -Path ("Env:" + $key) -Value $val
        }
    }
}

function Push-EnvVar([string]$Name, [string]$Value) {
    $exists = Test-Path ("Env:" + $Name)
    $prev = if ($exists) { (Get-Item ("Env:" + $Name)).Value } else { $null }
    Set-Item -Path ("Env:" + $Name) -Value $Value
    return @{ Name = $Name; Exists = $exists; Value = $prev }
}

function Pop-EnvVar($State) {
    $name = $State.Name
    if ($State.Exists) {
        Set-Item -Path ("Env:" + $name) -Value $State.Value
    } else {
        Remove-Item -Path ("Env:" + $name) -ErrorAction SilentlyContinue
    }
}

function Start-Uvicorn([string]$App, [int]$Port) {
    Start-Process -FilePath $python -ArgumentList "-m uvicorn $App --host 0.0.0.0 --port $Port" -WorkingDirectory $BaseDir | Out-Null
}

function Start-LlamaServer([string]$Name, [string]$ModelPath, [int]$Port) {
    $s1 = Push-EnvVar "MODEL_NAME" $Name
    $s2 = Push-EnvVar "MODEL_PATH" $ModelPath
    $s3 = Push-EnvVar "LLAMACPP_BIN" $env:LLAMACPP_BIN
    Start-Process -FilePath $python -ArgumentList "-m uvicorn services.model_server_llamacpp:app --host 0.0.0.0 --port $Port" -WorkingDirectory $BaseDir | Out-Null
    Pop-EnvVar $s3
    Pop-EnvVar $s2
    Pop-EnvVar $s1
}

function Start-HFServer([string]$Name, [string]$ModelId, [int]$Port) {
    $device = if (Test-Path Env:MODEL_DEVICE) { $env:MODEL_DEVICE } else { "cpu" }
    $bnb = if (Test-Path Env:USE_BITSANDBYTES) { $env:USE_BITSANDBYTES } else { "0" }

    $s1 = Push-EnvVar "MODEL_NAME" $Name
    $s2 = Push-EnvVar "MODEL_ID" $ModelId
    $s3 = Push-EnvVar "MODEL_DEVICE" $device
    $s4 = Push-EnvVar "USE_BITSANDBYTES" $bnb
    Start-Process -FilePath $python -ArgumentList "-m uvicorn services.model_server_hf:app --host 0.0.0.0 --port $Port" -WorkingDirectory $BaseDir | Out-Null
    Pop-EnvVar $s4
    Pop-EnvVar $s3
    Pop-EnvVar $s2
    Pop-EnvVar $s1
}

$python = Join-Path $BaseDir ".venv\bin\python.exe"
if (-not (Test-Path $python)) {
    throw "Python venv not found: $python"
}

$patchDir = Join-Path $BaseDir "scripts\py_patches"
if (Test-Path $patchDir) {
    $env:PYTHONPATH = "$patchDir;$env:PYTHONPATH"
}

Import-DotEnv (Join-Path $BaseDir ".env")

if (-not (Test-Path Env:BASE_DIR)) { $env:BASE_DIR = $BaseDir }
if (-not (Test-Path Env:MODELS_DIR)) { $env:MODELS_DIR = (Join-Path $BaseDir "models") }
if (-not (Test-Path Env:LLAMACPP_BIN)) { $env:LLAMACPP_BIN = (Join-Path $BaseDir "llama.cpp\build\bin\llama-cli.exe") }
if (-not (Test-Path Env:EXPERT_ENDPOINTS)) {
    $env:EXPERT_ENDPOINTS = '{"codellama":"http://127.0.0.1:8001","starcoder2":"http://127.0.0.1:8011","llama3":"http://127.0.0.1:8010","mpt3":"http://127.0.0.1:8012"}'
}

$env:TEMP = Join-Path $BaseDir "tmp"
$env:TMP = Join-Path $BaseDir "tmp"

# Core services
Start-Uvicorn "services.retriever:app" 8002
Start-Uvicorn "services.router:app" 8003
Start-Uvicorn "services.executor:app" 8004
Start-Uvicorn "services.controller:app" 8000

# Model servers
$modelsDir = Join-Path $BaseDir "models"
Start-LlamaServer "codellama-7b" (Join-Path $modelsDir "codellama-7b\codellama-7b-q4.gguf") 8001
Start-HFServer "starcoder2-3b" "bigcode/starcoder2-3b" 8011
Start-LlamaServer "llama3-3b" (Join-Path $modelsDir "llama3-3b\llama3-3b-instruct-q4.gguf") 8010
Start-LlamaServer "mpt3" (Join-Path $modelsDir "mpt-3b\mpt-3b-instruct-q4.gguf") 8012

Write-Host "Started core services on ports 8000,8002,8003,8004"
Write-Host "Started model services on ports 8001,8010,8011,8012"
Write-Host "Main endpoint: POST http://127.0.0.1:8000/ask"
