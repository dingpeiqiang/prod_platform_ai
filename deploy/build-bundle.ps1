# ============================================================
# Prod Platform AI - 整体交付包打包脚本（构建机 Windows PowerShell）
# 生成:
#   deploy/out/crm-pgcent-mng.tar.gz
# 包内结构（解压到虚拟机任意目录即可）:
#   crm-pgcent-mng/
#   ├── installer/            # 发布包（供 deployup.sh 后续更新使用）
#   │   ├── prod-ai-backend.tar.gz
#   │   └── prod-ai-frontend.tar.gz
#   ├── prod-ai-backend/      # 后端（已解压到位，含 app.jar/bin/conf/config）
#   ├── prod-ai-frontend/     # 前端（已解压到位，含 html/conf/bin）
#   └── mysql/                # 数据库 DDL/初始化脚本
# 前置: 先执行 deploy/build-tar.ps1（或已有 deploy/out/prod-ai-*.tar.gz）
# 用法: powershell -ExecutionPolicy Bypass -File deploy\build-bundle.ps1
# ============================================================
$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$OutDir      = Join-Path $PSScriptRoot 'out'
$TarCmd      = Get-Command tar -ErrorAction SilentlyContinue

if (-not $TarCmd) {
    Write-Error '未找到 tar 命令（Windows 10+ 自带 bsdtar）。请确认 PATH。'
}

$BackendTar  = Join-Path $OutDir 'prod-ai-backend.tar.gz'
$FrontendTar = Join-Path $OutDir 'prod-ai-frontend.tar.gz'
$MySqlTar    = Join-Path $OutDir 'prod-ai-mysql.tar.gz'

foreach ($t in @($BackendTar, $FrontendTar)) {
    if (-not (Test-Path $t)) {
        Write-Error "缺少 $t，请先执行: powershell -ExecutionPolicy Bypass -File deploy\build-tar.ps1"
    }
}

$Stage = Join-Path $PSScriptRoot '_stage_bundle'
$Root  = Join-Path $Stage 'crm-pgcent-mng'
if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
New-Item -ItemType Directory -Force -Path $Root | Out-Null

# ---------- 1) installer：放入原始发布包 ----------
$InstallerDir = Join-Path $Root 'installer'
New-Item -ItemType Directory -Force -Path $InstallerDir | Out-Null
Copy-Item $BackendTar  $InstallerDir -Force
Copy-Item $FrontendTar $InstallerDir -Force
Write-Host '[OK] installer/ 已放入 prod-ai-backend.tar.gz / prod-ai-frontend.tar.gz'

# ---------- 2) 解压前后端到各自目录 ----------
$BackendDir  = Join-Path $Root 'prod-ai-backend'
$FrontendDir = Join-Path $Root 'prod-ai-frontend'
New-Item -ItemType Directory -Force -Path $BackendDir, $FrontendDir | Out-Null

& $TarCmd.Source -xzf $BackendTar  -C $BackendDir
if ($LASTEXITCODE -ne 0) { Write-Error '解压 prod-ai-backend.tar.gz 失败' }
& $TarCmd.Source -xzf $FrontendTar -C $FrontendDir
if ($LASTEXITCODE -ne 0) { Write-Error '解压 prod-ai-frontend.tar.gz 失败' }
Write-Host '[OK] 前后端已解压到位（prod-ai-backend/ prod-ai-frontend/）'

# ---------- 3) 首次放置前端静态资源到 dist/ ----------
$HtmlDir = Join-Path $FrontendDir 'html'
$DistDir = Join-Path $FrontendDir 'dist'
if (Test-Path (Join-Path $HtmlDir 'index.html')) {
    New-Item -ItemType Directory -Force -Path $DistDir | Out-Null
    Copy-Item -Recurse -Force (Join-Path $HtmlDir '*') $DistDir
    Write-Host '[OK] 前端静态产物已放置到 prod-ai-frontend/dist/'
} else {
    Write-Warning '未找到 prod-ai-frontend/html/index.html，跳过 dist/ 预置'
}

# ---------- 4) mysql 目录 ----------
$MySqlDir = Join-Path $Root 'mysql'
New-Item -ItemType Directory -Force -Path $MySqlDir | Out-Null
$MySqlSrc = Join-Path $PSScriptRoot 'mysql'
if (Test-Path $MySqlSrc) {
    Copy-Item -Recurse -Force (Join-Path $MySqlSrc '*') $MySqlDir
    Write-Host '[OK] mysql/ 已放入 DDL/初始化脚本'
} else {
    Write-Warning '未找到 deploy/mysql 目录，mysql/ 为空'
}

# ---------- 5) 打包 ----------
$BundleTar = Join-Path $OutDir 'crm-pgcent-mng.tar.gz'
Push-Location $Stage
& $TarCmd.Source -czf $BundleTar -C $Stage 'crm-pgcent-mng' 2>$null
if ($LASTEXITCODE -ne 0) {
    & $TarCmd.Source -czf $BundleTar -C $Stage 'crm-pgcent-mng'
}
Pop-Location
Write-Host "[OK] $BundleTar"

# ---------- 6) 清理 ----------
Remove-Item -Recurse -Force $Stage -ErrorAction SilentlyContinue

Write-Host ''
Write-Host '打包完成:'
Write-Host "  $BundleTar  ($([Math]::Round((Get-Item $BundleTar).Length/1MB, 2)) MB)"
Write-Host ''
Write-Host '上传到虚拟机任意目录，解压后按 INSTALL.md 调整 config/application.yml 即可启动。'
