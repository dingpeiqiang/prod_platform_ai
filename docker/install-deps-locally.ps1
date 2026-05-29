# ============================================================
# 在本地安装依赖到指定目录
# 使用方式: .\install-deps-locally.ps1
# ============================================================

$scriptPath = $PSScriptRoot
$projectRoot = Split-Path $scriptPath -Parent
$depsDir = Join-Path $projectRoot "backend-deps"
$reqFile = Join-Path $projectRoot "backend\requirements.txt"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "开始在本地安装依赖..." -ForegroundColor Cyan
Write-Host "依赖目录: $depsDir" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 创建依赖目录
if (-not (Test-Path $depsDir)) {
    New-Item -ItemType Directory -Path $depsDir | Out-Null
}

# 使用 pip 安装依赖到指定目录
pip install `
    --target $depsDir `
    --no-cache-dir `
    -r $reqFile

if ($LASTEXITCODE -eq 0) {
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "依赖安装完成！" -ForegroundColor Green
    Write-Host "依赖目录: $depsDir" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "依赖安装失败！" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    exit 1
}
