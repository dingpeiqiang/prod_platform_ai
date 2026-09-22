# ============================================================
# Prod Platform AI - tar 包打包脚本（构建机 Windows PowerShell）
# 生成:
#   deploy/out/prod-ai-backend.tar.gz
#   deploy/out/prod-ai-frontend.tar.gz
#   deploy/out/prod-ai-mysql.tar.gz
# 后端由本脚本自动执行 mvn clean package -DskipTests（默认 Maven =
#       D:\APP\WORK\apache-maven-3.6.3，可用 -MavenHome 覆盖；settings 自动探测
#       backend-app/.mvn/local-settings.xml）；如需用已构建产物，传 -SkipBuild 跳过编译。
# 前端由本脚本自动执行 npm ci + npm run build，无需手动预构建
# 用法:
#   powershell -ExecutionPolicy Bypass -File deploy\build-tar.ps1            # 交互式选择
#   powershell -ExecutionPolicy Bypass -File deploy\build-tar.ps1 -Target all
#   powershell -ExecutionPolicy Bypass -File deploy\build-tar.ps1 -Target backend,frontend
#   powershell -ExecutionPolicy Bypass -File deploy\build-tar.ps1 -Target backend -SkipBuild
#   powershell -ExecutionPolicy Bypass -File deploy\build-tar.ps1 -Target backend -MavenHome D:\APP\WORK\apache-maven-3.6.3
#   -Target 取值: all | backend | frontend | mysql （多个用逗号分隔）
#   -SkipBuild 跳过后端 mvn 编译，直接使用已有 jar
#   -MavenHome 指定 Maven 安装目录（默认 D:\APP\WORK\apache-maven-3.6.3）
# ============================================================
param(
    [ValidateSet('all', 'backend', 'frontend', 'mysql')]
    [string[]]$Target,

    [switch]$SkipBuild,

    # 指定 Maven 安装目录（含 bin\mvn.cmd）。默认使用 D:\APP\WORK\apache-maven-3.6.3
    [string]$MavenHome = 'D:\APP\WORK\apache-maven-3.6.3'
)

$ErrorActionPreference = 'Stop'

# 前端部署前缀（与 docker/Sitech.BJ.Dockerfile.frontend 及
# deploy/frontend/conf/prod-ai.conf 的 /prod-ai 挂载保持一致）。
# 如需覆盖，可导出环境变量 VITE_BASE_PATH。
$FrontendBasePath = $(if ($env:VITE_BASE_PATH) { $env:VITE_BASE_PATH } else { '/prod-ai/' })

$ProjectRoot = Split-Path -Parent $PSScriptRoot
$OutDir      = Join-Path $PSScriptRoot 'out'
$TarCmd      = Get-Command tar -ErrorAction SilentlyContinue

if (-not $TarCmd) {
    Write-Error '未找到 tar 命令（Windows 10+ 自带 bsdtar）。请确认 PATH。'
}

# ---------- 选择打包目标（命令行参数优先，否则交互式） ----------
function Get-SelectedTargets {
    if ($Target) {
        if ($Target -contains 'all') { return @('backend', 'frontend', 'mysql') }
        return $Target
    }

    Write-Host ''
    Write-Host '请选择要打包的内容：'
    Write-Host '  1) 后端 (prod-ai-backend.tar.gz)'
    Write-Host '  2) 前端 (prod-ai-frontend.tar.gz)'
    Write-Host '  3) MySQL 初始化包 (prod-ai-mysql.tar.gz)'
    Write-Host '  4) 全部'
    Write-Host '  (可输入多个，如 1,2 或 12；回车默认全部)'
    $answer = Read-Host '输入序号'
    if ([string]::IsNullOrWhiteSpace($answer)) { return @('backend', 'frontend', 'mysql') }

    $selected = @()
    if ($answer -match '4') {
        return @('backend', 'frontend', 'mysql')
    }
    if ($answer -match '1') { $selected += 'backend' }
    if ($answer -match '2') { $selected += 'frontend' }
    if ($answer -match '3') { $selected += 'mysql' }
    if ($selected.Count -eq 0) {
        Write-Error "无效选择 '$answer'，请重新运行"
    }
    return $selected
}

$Targets        = Get-SelectedTargets
$DoBackend      = $Targets -contains 'backend'
$DoFrontend     = $Targets -contains 'frontend'
$DoMysql        = $Targets -contains 'mysql'

Write-Host ''
Write-Host ("打包目标: " + ($Targets -join ', '))

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$MySqlStage = $null

# ---------- 1) 后端 tar ----------
if ($DoBackend) {
    $BackendRoot = Join-Path $ProjectRoot 'backend-app'

    # 1.1 后端编译：mvn clean package -DskipTests（settings 自动探测 backend-app/.mvn/local-settings.xml）
    if ($SkipBuild) {
        Write-Host '[BUILD] -SkipBuild 已指定，跳过后端编译，直接使用已有 jar'
    } else {
        if (-not (Test-Path (Join-Path $BackendRoot 'pom.xml'))) {
            Write-Error "未找到后端工程 ${BackendRoot}，无法编译"
        }
        # Maven 解析优先级：-MavenHome 指定 > 环境变量 MAVEN_HOME 下的 mvn.cmd > PATH 上的 mvn
        $MvnExe = $null
        if ($MavenHome) {
            $candidate = Join-Path $MavenHome 'bin\mvn.cmd'
            if (Test-Path $candidate) { $MvnExe = $candidate }
            else { Write-Error "MavenHome 未找到 mvn.cmd：$candidate" }
        }
        if (-not $MvnExe) {
            $MvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
            if ($MvnCmd) { $MvnExe = $MvnCmd.Source }
        }
        if (-not $MvnExe) {
            Write-Error '未找到 mvn 命令，请用 -MavenHome 指定 Maven 目录，或加入 PATH，或使用 -SkipBuild 跳过编译'
        }
        $LocalSettings = Join-Path $BackendRoot '.mvn\local-settings.xml'
        $MvnArgs = @()
        if (Test-Path $LocalSettings) {
            $MvnArgs += '-s', $LocalSettings
            Write-Host "[BUILD] mvn clean package -DskipTests（backend-app 目录，mvn=$MvnExe，settings=$LocalSettings）..."
        } else {
            Write-Host "[BUILD] mvn clean package -DskipTests（backend-app 目录，mvn=$MvnExe，使用全局 settings）..."
        }
        Push-Location $BackendRoot
        try {
            & $MvnExe @MvnArgs clean package -DskipTests
            if ($LASTEXITCODE -ne 0) { throw "后端编译失败（exit=$LASTEXITCODE）" }
        } finally {
            Pop-Location
        }
    }

    $BackendStage = Join-Path $PSScriptRoot '_stage_backend'
    if (Test-Path $BackendStage) { Remove-Item -Recurse -Force $BackendStage }
    New-Item -ItemType Directory -Force -Path "$BackendStage\bin", "$BackendStage\conf", "$BackendStage\config", "$BackendStage\logs", "$BackendStage\data", "$BackendStage\uploads" | Out-Null

    # 后端可执行 jar
    $Jar = Get-ChildItem -Path (Join-Path $ProjectRoot 'backend-app\target') -Filter 'prod-platform-ai-*.jar' |
    Where-Object { $_.Name -notmatch 'original|sources' } | Select-Object -First 1
    if (-not $Jar) {
        Write-Error '后端 jar 未找到，请检查编译输出（backend-app/target），或先手动执行 mvn -DskipTests clean package'
    }
    Copy-Item $Jar.FullName (Join-Path $BackendStage 'app.jar')

    # tar 包内启停/更新脚本 + systemd 模板
    Copy-Item (Join-Path $PSScriptRoot 'backend\bin\start.sh') (Join-Path $BackendStage 'bin\start.sh') -Force
    Copy-Item (Join-Path $PSScriptRoot 'backend\bin\deployup.sh') (Join-Path $BackendStage 'bin\deployup.sh') -Force
    Copy-Item (Join-Path $PSScriptRoot 'backend\conf\prod-ai-backend.service') (Join-Path $BackendStage 'conf\') -Force

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

    Remove-Item -Recurse -Force $BackendStage -ErrorAction SilentlyContinue
}

# ---------- 2) 前端 tar（包根为 dist/ + conf/ + bin/，解压即见 dist） ----------
if ($DoFrontend) {
    # 2.1 前端构建：自动 npm ci + 设置 VITE_BASE_PATH 后 npm run build，
    #     产物带 /prod-ai 前缀，与 nginx 前缀规则 / 路由 base 匹配。
    $FrontendRoot = Join-Path $ProjectRoot 'frontend'
    if (-not (Test-Path (Join-Path $FrontendRoot 'package.json'))) {
        Write-Error "未找到前端工程 ${FrontendRoot}，无法自动构建"
    }
    if (-not (Test-Path (Join-Path $FrontendRoot 'node_modules\vite'))) {
        Write-Host '[BUILD] 首次构建，执行 npm ci 安装依赖（frontend 目录）...'
        Push-Location $FrontendRoot
        try {
            & npm ci
            if ($LASTEXITCODE -ne 0) { throw "npm ci 失败（exit=$LASTEXITCODE）" }
        } finally {
            Pop-Location
        }
    }
    $env:VITE_BASE_PATH = $FrontendBasePath
    Write-Host "[BUILD] npm run build（frontend 目录，VITE_BASE_PATH=$env:VITE_BASE_PATH）..."
    Push-Location $FrontendRoot
    try {
        & npm run build
        if ($LASTEXITCODE -ne 0) { throw "npm run build 失败（exit=$LASTEXITCODE）" }
    } finally {
        Pop-Location
    }

    # 2.2 组装前端 stage 目录
    $FrontendStage = Join-Path $PSScriptRoot '_stage_frontend'
    if (Test-Path $FrontendStage) { Remove-Item -Recurse -Force $FrontendStage }
    New-Item -ItemType Directory -Force -Path "$FrontendStage\dist", "$FrontendStage\conf", "$FrontendStage\bin" | Out-Null

    $Dist = Join-Path $FrontendRoot 'dist'
    if (-not (Test-Path (Join-Path $Dist 'index.html'))) {
        Write-Warning '前端构建未生成 dist/index.html（构建异常）'
    } else {
        Copy-Item -Recurse -Force (Join-Path $Dist '*') (Join-Path $FrontendStage 'dist\')
    }

    # 免登录静态页（IFRAME 直嵌）：必须随包，缺失会被 SPA 回退到 index.html 触发登录跳转
    $OpsWorkbench = Join-Path $FrontendStage 'dist\ops-web\config-workbench.html'
    if (-not (Test-Path $OpsWorkbench)) {
        Write-Error '缺少 ops-web/config-workbench.html，请执行 npm run build 后重试（public/ops-web 会整体拷贝到 dist/）'
    }

    Copy-Item (Join-Path $PSScriptRoot 'frontend\conf\prod-ai.conf') (Join-Path $FrontendStage 'conf\') -Force
    Copy-Item (Join-Path $PSScriptRoot 'frontend\conf\mime.types') (Join-Path $FrontendStage 'conf\') -Force
    Copy-Item (Join-Path $PSScriptRoot 'frontend\bin\start.sh') (Join-Path $FrontendStage 'bin\') -Force
    Copy-Item (Join-Path $PSScriptRoot 'frontend\bin\stop.sh') (Join-Path $FrontendStage 'bin\') -Force
    Copy-Item (Join-Path $PSScriptRoot 'frontend\bin\deployup.sh') (Join-Path $FrontendStage 'bin\') -Force

    $FrontendTar = Join-Path $OutDir 'prod-ai-frontend.tar.gz'
    Push-Location $PSScriptRoot
    & $TarCmd.Source -czf $FrontendTar -C '_stage_frontend' dist conf bin
    Pop-Location
    Write-Host "[OK] $FrontendTar"

    Remove-Item -Recurse -Force $FrontendStage -ErrorAction SilentlyContinue
}

# ---------- 3) MySQL 初始化包（纯 MySQL 8.0 DDL + 初始化 + 一键脚本） ----------
if ($DoMysql) {
    if (Test-Path (Join-Path $PSScriptRoot 'mysql')) {
        $MySqlTar = Join-Path $OutDir 'prod-ai-mysql.tar.gz'
        $MySqlStage = Join-Path $PSScriptRoot '_stage_mysql'
        if (Test-Path $MySqlStage) { Remove-Item -Recurse -Force $MySqlStage }
        New-Item -ItemType Directory -Force -Path $MySqlStage | Out-Null
        Copy-Item (Join-Path $PSScriptRoot 'mysql\*') $MySqlStage -Recurse -Force
        Push-Location $PSScriptRoot
        & $TarCmd.Source -czf $MySqlTar -C '_stage_mysql' .
        Pop-Location
        Write-Host "[OK] $MySqlTar"
        Remove-Item -Recurse -Force $MySqlStage -ErrorAction SilentlyContinue
    } else {
        Write-Warning '未找到 deploy/mysql 目录，跳过 MySQL 初始化包'
    }
}

Write-Host ''
Write-Host '打包完成，交付物:'
Get-ChildItem $OutDir -Filter '*.tar.gz' | ForEach-Object {
    Write-Host "  $($_.FullName)  ($([Math]::Round($_.Length / 1MB, 2)) MB)"
}
