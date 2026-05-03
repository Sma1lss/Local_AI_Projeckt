$ErrorActionPreference = 'Stop'
$limitBytes = 100MB
$foundLarge = $false

$stagedFiles = git diff --cached --name-only --diff-filter=ACMR
if ($LASTEXITCODE -ne 0) {
    exit 0
}

foreach ($path in ($stagedFiles -split "`r?`n")) {
    if ([string]::IsNullOrWhiteSpace($path)) {
        continue
    }

    $sizeText = git cat-file -s (":" + $path) 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($sizeText)) {
        continue
    }

    $size = [int64]$sizeText.Trim()
    if ($size -le $limitBytes) {
        continue
    }

    git restore --staged -- $path 2>$null
    if ($LASTEXITCODE -ne 0) {
        git reset -q HEAD -- $path 2>$null
    }

    $sizeMb = [math]::Ceiling($size / 1MB)
    Write-Output ("Excluded from commit (>100 MB): {0} ({1} MB)" -f $path, $sizeMb)
    $foundLarge = $true
}

if ($foundLarge) {
    Write-Output 'Large files were removed from staging and remain in the working tree.'
}

exit 0