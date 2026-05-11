# ============================================================
# 离线部署脚本 - 在无网络环境中执行 (Windows)
# ============================================================

$ErrorActionPreference = "Stop"

# Colors
$Colors = @{
    Success = "Green"
    Warning = "Yellow"
    Error = "Red"
    Info = "Cyan"
    Header = "DarkCyan"
}

function Write-Status {
    param([string]$Message, [string]$Type = "Info")
    $color = $Colors[$Type]
    Write-Host $Message -ForegroundColor $color
}

function Get-ScriptDirectory {
    Split-Path -Parent $MyInvocation.MyCommand.Path
}

# Main
Write-Host ""
Write-Status "========================================" "Header"
Write-Status "离线部署工具 (Windows)" "Header"
Write-Status "========================================" "Header"
Write-Host ""

# Get script directory
$scriptDir = Get-ScriptDirectory
Write-Status "工作目录: $scriptDir" "Info"
Write-Host ""

# Check if running from correct location (package root)
$imagesDir = Join-Path $scriptDir "docker-images"
$sourceDir = Join-Path $scriptDir "source"
if (!(Test-Path $imagesDir) -or !(Test-Path $sourceDir)) {
    Write-Status "[ERROR] 请在离线包根目录下运行此脚本" "Error"
    Write-Status "目录结构应为:" "Info"
    Write-Status "  package-dir/" "Info"
    Write-Status "  ├── docker-images/" "Info"
    Write-Status "  ├── source/" "Info"
    Write-Status "  └── deploy-offline.ps1 (本文件)" "Info"
    Read-Host "按回车键退出"
    exit 1
}

# ============================================================
# Step 1: 导入 Docker 镜像
# ============================================================
Write-Status "步骤 1/4: 导入 Docker 镜像..." "Info"

$imageFiles = Get-ChildItem -Path $imagesDir -Filter "*.tar"
if ($imageFiles.Count -eq 0) {
    Write-Status "[WARNING] 未找到镜像文件" "Warning"
} else {
    foreach ($imgFile in $imageFiles) {
        Write-Status "  导入 $($imgFile.Name) ..." "Info"
        try {
            docker load -i $imgFile.FullName
            if ($LASTEXITCODE -ne 0) {
                throw "导入失败"
            }
            Write-Status "  [OK] $($imgFile.Name) 导入成功" "Success"
        }
        catch {
            Write-Status "  [ERROR] $($imgFile.Name) 导入失败: $_" "Error"
        }
    }
}
Write-Host ""

# ============================================================
# Step 2: 准备项目文件
# ============================================================
Write-Status "步骤 2/4: 准备项目文件..." "Info"

# 复制项目文件到当前目录上级（如果需要）
$targetDir = Split-Path -Parent $scriptDir
Write-Status "  目标目录: $targetDir" "Info"

# 检查是否需要复制
$copySource = $true
if ((Test-Path (Join-Path $targetDir "backend")) -and 
    (Test-Path (Join-Path $targetDir "frontend"))) {
    $response = Read-Host "  检测到目标目录已存在项目文件，是否覆盖？(y/N，默认 N)"
    if ($response -ne "y" -and $response -ne "Y") {
        $copySource = $false
        Write-Status "  跳过文件复制" "Info"
    }
}

if ($copySource) {
    Write-Status "  复制项目文件..." "Info"
    Copy-Item -Path (Join-Path $sourceDir "*") -Destination $targetDir -Recurse -Force
    Write-Status "[OK] 项目文件准备完成" "Success"
}
Write-Host ""

# ============================================================
# Step 3: 配置环境变量
# ============================================================
Write-Status "步骤 3/4: 配置环境变量..." "Info"

$envFile = Join-Path $targetDir ".env"
$envExample = Join-Path $targetDir ".env.example"

if (!(Test-Path $envFile) -and (Test-Path $envExample)) {
    Write-Status "  创建 .env 文件..." "Info"
    Copy-Item -Path $envExample -Destination $envFile -Force
    Write-Status "[OK] .env 文件已创建，请根据需要编辑配置" "Success"
} elseif (Test-Path $envFile) {
    Write-Status "[OK] .env 文件已存在" "Success"
} else {
    Write-Status "[WARNING] 未找到 .env.example 文件" "Warning"
}
Write-Host ""

# ============================================================
# Step 4: 启动服务
# ============================================================
Write-Status "步骤 4/4: 启动服务..." "Info"

Set-Location $targetDir

Write-Status "  使用 Docker Compose 启动服务..." "Info"
try {
    docker-compose up -d --build
    if ($LASTEXITCODE -ne 0) {
        throw "启动失败"
    }
    Write-Status "[OK] 服务启动成功" "Success"
}
catch {
    Write-Status "[ERROR] 服务启动失败: $_" "Error"
    Write-Status "请检查 Docker 是否正常运行" "Warning"
}
Write-Host ""

# Summary
Write-Status "========================================" "Header"
Write-Status "部署完成!" "Header"
Write-Status "========================================" "Header"
Write-Host ""
Write-Status "服务状态:" "Info"
Write-Status "  运行: docker-compose ps" "Info"
Write-Status "  日志: docker-compose logs -f" "Info"
Write-Status "  停止: docker-compose down" "Info"
Write-Host ""
Write-Status "访问地址:" "Success"
Write-Status "  前端: http://localhost (或配置的端口)" "Info"
Write-Status "  后端: http://localhost:6173" "Info"
Write-Host ""
Read-Host "按回车键退出"
