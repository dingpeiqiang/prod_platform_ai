<#
.SYNOPSIS
前端Docker离线安装包生成工具

.DESCRIPTION
自动读取frontend/package.json中的依赖配置，批量下载所有依赖包到frontend/vendor目录，
支持内网离线环境构建Docker镜像。

.EXAMPLE
.\generate-frontend-vendor.ps1

.NOTES
需要Node.js环境（推荐Node.js 18+）
#>

param()

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "前端Docker离线安装包生成工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host

$scriptDir = $PSScriptRoot
$vendorDir = Join-Path (Split-Path $scriptDir -Parent) "frontend/vendor"
$packageJsonPath = Join-Path (Split-Path $scriptDir -Parent) "frontend/package.json"

if (-not (Test-Path $vendorDir)) {
    New-Item -ItemType Directory -Path $vendorDir | Out-Null
}

if (-not (Test-Path $packageJsonPath)) {
    Write-Host "错误: 未找到package.json文件" -ForegroundColor Red
    Write-Host "期望路径: $packageJsonPath" -ForegroundColor Red
    Read-Host "按回车键退出..."
    exit 1
}

try {
    $nodeVersion = node --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Node.js not found"
    }
    Write-Host "Node.js环境正常: $nodeVersion" -ForegroundColor Green
}
catch {
    Write-Host "错误: 未安装Node.js或未配置环境变量" -ForegroundColor Red
    Read-Host "按回车键退出..."
    exit 1
}

Write-Host

try {
    $packageJson = Get-Content $packageJsonPath -Raw | ConvertFrom-Json
    $dependencies = @()

    if ($packageJson.dependencies) {
        foreach ($dep in $packageJson.dependencies.PSObject.Properties) {
            $dependencies += "$($dep.Name)@$($dep.Value)"
        }
    }

    if ($packageJson.devDependencies) {
        foreach ($dep in $packageJson.devDependencies.PSObject.Properties) {
            $dependencies += "$($dep.Name)@$($dep.Value)"
        }
    }

    Write-Host "读取到 $($dependencies.Count) 个依赖包" -ForegroundColor Green
}
catch {
    Write-Host "错误: 无法解析package.json文件" -ForegroundColor Red
    Write-Host "错误信息: $_" -ForegroundColor Red
    Read-Host "按回车键退出..."
    exit 1
}

Write-Host

Write-Host "清理旧的依赖包..." -ForegroundColor Yellow
Get-ChildItem -Path $vendorDir -Filter "*.tgz" | Remove-Item -Force

Write-Host "开始下载依赖包..." -ForegroundColor Yellow
Write-Host "这可能需要一些时间，请耐心等待..." -ForegroundColor Yellow
Write-Host

$successCount = 0
$failCount = 0

foreach ($dep in $dependencies) {
    Write-Host "正在下载: $dep" -NoNewline

    try {
        npm pack $dep --pack-destination $vendorDir 2>&1 | Out-Null

        if ($LASTEXITCODE -eq 0) {
            Write-Host " [成功]" -ForegroundColor Green
            $successCount++
        }
        else {
            Write-Host " [失败]" -ForegroundColor Red
            $failCount++
        }
    }
    catch {
        Write-Host " [失败: $_]" -ForegroundColor Red
        $failCount++
    }
}

Write-Host

$listContent = @"
# 前端离线依赖包列表
# 生成时间: $(Get-Date -Format "yyyy-MM-ddTHH:mm:ss.fffZ")
# 依赖数量: $($dependencies.Count)

$($dependencies -join "`n")
"@

$listPath = Join-Path $vendorDir "package-list.txt"
Set-Content -Path $listPath -Value $listContent -Encoding UTF8

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "下载完成: 成功 $successCount 个, 失败 $failCount 个" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan