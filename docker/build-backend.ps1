# ============================================================
# AI驱动动态表单 - 后端构建脚本 (Windows)
# 使用方式: .\build-backend.ps1 [egg|wheel|all]
# ============================================================

$scriptPath = $PSScriptRoot
$projectRoot = Split-Path $scriptPath -Parent
$backendDir = Join-Path $projectRoot "backend"
$distDir = Join-Path $backendDir "dist"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "AI驱动动态表单 - 后端构建脚本" -ForegroundColor Cyan
Write-Host "项目根目录: $projectRoot" -ForegroundColor Cyan
Write-Host "后端目录: $backendDir" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 创建 dist 目录
if (-not (Test-Path $distDir)) {
    New-Item -ItemType Directory -Path $distDir | Out-Null
}

# 切换到后端目录
Set-Location $backendDir

# 根据参数选择打包方式
$param = if ($args.Count -gt 0) { $args[0] } else { "all" }

switch ($param) {
    "egg" {
        Write-Host "开始构建 egg 包..." -ForegroundColor Yellow
        python setup.py bdist_egg
    }
    "wheel" {
        Write-Host "开始构建 wheel 包..." -ForegroundColor Yellow
        python setup.py bdist_wheel
    }
    "all" {
        Write-Host "开始构建 egg 包..." -ForegroundColor Yellow
        python setup.py bdist_egg
        
        Write-Host "开始构建 wheel 包..." -ForegroundColor Yellow
        python setup.py bdist_wheel
    }
    default {
        Write-Host "用法: .\build-backend.ps1 [egg|wheel|all]" -ForegroundColor Red
        exit 1
    }
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "构建完成！" -ForegroundColor Green
    Write-Host "输出目录: $distDir" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    
    # 列出生成的包
    Get-ChildItem $distDir | Format-Table Name, LastWriteTime, Length
} else {
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "构建失败！" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    exit 1
}
