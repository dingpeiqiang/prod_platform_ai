# ============================================================
# Prod Platform AI - tar 包打包脚本（构建机 Windows PowerShell）
# 生成:
#   deploy/out/prod-ai-backend.tar.gz
#   deploy/out/prod-ai-frontend.tar.gz
# 前置: 后端已 mvn package，前端已 npm run build
# 用法:  powershell -ExecutionPolicy Bypass -File deploy\build-tar.ps1
# ============================================================
$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$OutDir      = Join-Path $PSScriptRoot 'out'
$TarCmd      = Get-Command tar -ErrorAction SilentlyContinue

if (-not $TarCmd) {
    Write-Error '未找到 tar 命令（Windows 10+ 自带 bsdtar）。请确认 PATH。'
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# ---------- 1) 后端 tar ----------
$BackendStage = Join-Path $PSScriptRoot '_stage_backend'
if (Test-Path $BackendStage) { Remove-Item -Recurse -Force $BackendStage }
New-Item -ItemType Directory -Force -Path "$BackendStage\bin","$BackendStage\conf","$BackendStage\config","$BackendStage\logs","$BackendStage\data","$BackendStage\uploads" | Out-Null

# 后端可执行 jar
$Jar = Get-ChildItem -Path (Join-Path $ProjectRoot 'backend-app\target') -Filter 'prod-platform-ai-*.jar' |
       Where-Object { $_.Name -notmatch 'original|sources' } | Select-Object -First 1
if (-not $Jar) {
    Write-Warning '后端 jar 未找到，请先执行: mvn -s .mvn/local-settings.xml -DskipTests clean package（backend-app 目录）'
}
if ($Jar) {
    Copy-Item $Jar.FullName (Join-Path $BackendStage 'app.jar')
}

# tar 包内启停脚本 + systemd/env 模板
Copy-Item (Join-Path $PSScriptRoot 'backend\bin\start.sh')           (Join-Path $BackendStage 'bin\start.sh')     -Force
Copy-Item (Join-Path $PSScriptRoot 'backend\conf\prod-ai-backend.service') (Join-Path $BackendStage 'conf\') -Force
Copy-Item (Join-Path $PSScriptRoot 'backend\conf\backend.env.example')     (Join-Path $BackendStage 'conf\') -Force

# 默认外部配置（可选随包，供启动脚本 --spring.config.additional-location 使用）
if (Test-Path (Join-Path $ProjectRoot 'docker\config\application.yml')) {
    Copy-Item (Join-Path $ProjectRoot 'docker\config\application.yml') (Join-Path $BackendStage 'config\') -Force
}

$BackendTar = Join-Path $OutDir 'prod-ai-backend.tar.gz'
Push-Location $PSScriptRoot
& $TarCmd.Source -czf $BackendTar -C '_stage_backend' . 2>$null
if ($LASTEXITCODE -ne 0) {
    # bsdtar 可能因中文路径告警，重试指定目录
    & $TarCmd.Source -czf $BackendTar -C '_stage_backend' bin conf config logs data uploads
}
Pop-Location
Write-Host "[OK] $BackendTar"

# ---------- 2) 前端 tar ----------
$FrontendStage = Join-Path $PSScriptRoot '_stage_frontend'
if (Test-Path $FrontendStage) { Remove-Item -Recurse -Force $FrontendStage }
New-Item -ItemType Directory -Force -Path "$FrontendStage\html","$FrontendStage\conf","$FrontendStage\bin" | Out-Null

$Dist = Join-Path $ProjectRoot 'frontend\dist'
if (-not (Test-Path (Join-Path $Dist 'index.html'))) {
    Write-Warning '前端 dist 未找到，请先执行: npm ci && npm run build（frontend 目录）'
}
if (Test-Path $Dist) {
    Copy-Item -Recurse -Force (Join-Path $Dist '*') (Join-Path $FrontendStage 'html\')
}

Copy-Item (Join-Path $PSScriptRoot 'frontend\conf\prod-ai.conf')           (Join-Path $FrontendStage 'conf\') -Force
Copy-Item (Join-Path $PSScriptRoot 'frontend\bin\install.sh')              (Join-Path $FrontendStage 'bin\')   -Force

$FrontendTar = Join-Path $OutDir 'prod-ai-frontend.tar.gz'
Push-Location $PSScriptRoot
& $TarCmd.Source -czf $FrontendTar -C '_stage_frontend' html conf bin
Pop-Location
Write-Host "[OK] $FrontendTar"

# ---------- 3) MySQL 初始化包（纯 MySQL 8.0 DDL + 初始化 + 一键脚本） ----------
if (Test-Path (Join-Path $PSScriptRoot 'mysql')) {
    $MySqlTar   = Join-Path $OutDir 'prod-ai-mysql.tar.gz'
    $MySqlStage = Join-Path $PSScriptRoot '_stage_mysql'
    if (Test-Path $MySqlStage) { Remove-Item -Recurse -Force $MySqlStage }
    New-Item -ItemType Directory -Force -Path $MySqlStage | Out-Null
    Copy-Item (Join-Path $PSScriptRoot 'mysql\*') $MySqlStage -Recurse -Force
    Push-Location $PSScriptRoot
    & $TarCmd.Source -czf $MySqlTar -C '_stage_mysql' .
    Pop-Location
    Write-Host "[OK] $MySqlTar"
} else {
    Write-Warning '未找到 deploy/mysql 目录，跳过 MySQL 初始化包'
}

# ---------- 清理舞台目录 ----------
Remove-Item -Recurse -Force $BackendStage, $FrontendStage -ErrorAction SilentlyContinue
if ($MySqlStage) { Remove-Item -Recurse -Force $MySqlStage -ErrorAction SilentlyContinue }

Write-Host ''
Write-Host '打包完成，交付物:'
Get-ChildItem $OutDir -Filter '*.tar.gz' | ForEach-Object {
    Write-Host "  $($_.FullName)  ($([Math]::Round($_.Length/1MB, 2)) MB)"
}
