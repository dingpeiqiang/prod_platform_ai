# =============================================================
# 一键离线依赖打包脚本 (Windows PowerShell)
# 在有网络的机器上执行，打包后端 Python 包 + 前端 Node 包
# 打包完成后提交到 git，内网构建不再需要外网访问
# =============================================================
param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$SkipGitAdd
)

$ErrorActionPreference = "Continue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

Write-Host ""
Write-Host "██████████████████████████████████████████████" -ForegroundColor Cyan
Write-Host "  内网离线依赖打包工具" -ForegroundColor Cyan
Write-Host "  项目: prod_platform_ai" -ForegroundColor Cyan
Write-Host "██████████████████████████████████████████████" -ForegroundColor Cyan
Write-Host ""

$StartTime = Get-Date

# ── 后端 Python 依赖 ──────────────────────────────────────
if (-not $FrontendOnly) {
    Write-Host "▶ [1/2] 打包后端 Python 依赖..." -ForegroundColor Yellow
    & "$ScriptDir\pack-deps-backend.ps1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 后端依赖打包失败！" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
}

# ── 前端 Node 依赖 ────────────────────────────────────────
if (-not $BackendOnly) {
    Write-Host "▶ [2/2] 打包前端 Node 依赖..." -ForegroundColor Yellow
    & "$ScriptDir\pack-deps-frontend.ps1"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] 前端依赖打包失败！" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
}

# ── Git 提交提示 ──────────────────────────────────────────
$Elapsed = [math]::Round(((Get-Date) - $StartTime).TotalSeconds, 1)

Write-Host "██████████████████████████████████████████████" -ForegroundColor Green
Write-Host "  ✅ 所有依赖打包完成！耗时: ${Elapsed}s" -ForegroundColor Green
Write-Host "██████████████████████████████████████████████" -ForegroundColor Green
Write-Host ""

if (-not $SkipGitAdd) {
    Write-Host "是否立即执行 git add + commit？(y/N)" -ForegroundColor Yellow -NoNewline
    $Answer = Read-Host " "
    if ($Answer -match "^[yY]") {
        Set-Location $ProjectRoot
        git add backend/vendor/ frontend/vendor/ 2>$null
        git status --short
        Write-Host ""
        $CommitMsg = "chore: update offline vendor dependencies $(Get-Date -Format 'yyyy-MM-dd')"
        git commit -m $CommitMsg
        Write-Host "[OK] 已提交到 git: $CommitMsg" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "请手动执行以下命令提交到 git：" -ForegroundColor Yellow
        Write-Host "  git add backend/vendor/ frontend/vendor/"
        Write-Host '  git commit -m "chore: update offline vendor dependencies"'
    }
}

Write-Host ""
Write-Host "内网构建命令：" -ForegroundColor Cyan
Write-Host "  docker-compose -f deploy/docker-compose.yml up -d --build"
Write-Host ""
