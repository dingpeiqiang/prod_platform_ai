# ============================================================
# 离线打包脚本 - 在有网络环境中执行
# ============================================================

$ErrorActionPreference = "Stop"

# Configuration
$registry = "10.86.12.11:20200"
$project = "crm-pgcent"
$outputDir = "offline-package"
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$packageName = "prod-platform-ai-offline-$timestamp"

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
Write-Status "离线打包工具" "Header"
Write-Status "========================================" "Header"
Write-Host ""

# Get script directory and project root
$scriptDir = Get-ScriptDirectory
$projectRoot = Split-Path -Parent $scriptDir
Set-Location $projectRoot
Write-Status "工作目录: $projectRoot" "Info"
Write-Host ""

# Create output directory
$packageDir = Join-Path $scriptDir $outputDir $packageName
if (Test-Path $packageDir) {
    Remove-Item -Path $packageDir -Recurse -Force
}
New-Item -ItemType Directory -Path $packageDir -Force | Out-Null
Write-Status "创建打包目录: $packageDir" "Info"
Write-Host ""

# ============================================================
# Step 1: 导出 Docker 基础镜像
# ============================================================
Write-Status "步骤 1/4: 导出 Docker 基础镜像..." "Info"

$images = @(
    @{ Name = "ai-form-backend-builder"; Tag = "$registry/$project/ai-form-backend-builder:latest" },
    @{ Name = "ai-form-backend-runtime"; Tag = "$registry/$project/ai-form-backend-runtime:latest" },
    @{ Name = "ai-form-frontend-builder"; Tag = "$registry/$project/ai-form-frontend-builder:latest" },
    @{ Name = "mysql"; Tag = "mysql:8.0" }
)

$imagesDir = Join-Path $packageDir "docker-images"
New-Item -ItemType Directory -Path $imagesDir -Force | Out-Null

foreach ($img in $images) {
    $tarFile = Join-Path $imagesDir "$($img.Name).tar"
    Write-Status "  导出 $($img.Tag) -> $tarFile" "Info"
    try {
        docker save -o $tarFile $img.Tag
        if ($LASTEXITCODE -ne 0) {
            throw "导出失败"
        }
        Write-Status "  [OK] $($img.Name) 导出成功" "Success"
    }
    catch {
        Write-Status "  [ERROR] $($img.Name) 导出失败: $_" "Error"
    }
}
Write-Host ""

# ============================================================
# Step 2: 复制项目源代码和配置
# ============================================================
Write-Status "步骤 2/4: 复制项目文件..." "Info"

$sourceDir = Join-Path $packageDir "source"
New-Item -ItemType Directory -Path $sourceDir -Force | Out-Null

# 复制核心目录
$dirsToCopy = @("backend", "frontend", "config", "docker")
foreach ($dir in $dirsToCopy) {
    $src = Join-Path $projectRoot $dir
    $dst = Join-Path $sourceDir $dir
    if (Test-Path $src) {
        Write-Status "  复制 $dir/ ..." "Info"
        Copy-Item -Path $src -Destination $sourceDir -Recurse -Force
    }
}

# 复制根目录文件
$filesToCopy = @("docker-compose.yml", ".env.example", "AGENTS.md", "README.md")
foreach ($file in $filesToCopy) {
    $src = Join-Path $projectRoot $file
    $dst = Join-Path $sourceDir $file
    if (Test-Path $src) {
        Copy-Item -Path $src -Destination $sourceDir -Force
    }
}
Write-Status "[OK] 项目文件复制完成" "Success"
Write-Host ""

# ============================================================
# Step 3: 复制部署脚本
# ============================================================
Write-Status "步骤 3/4: 复制部署脚本..." "Info"

# 复制部署脚本
Copy-Item -Path (Join-Path $scriptDir "deploy-offline.ps1") -Destination $packageDir -Force
Copy-Item -Path (Join-Path $scriptDir "deploy-offline.sh") -Destination $packageDir -Force

# 创建部署说明
$readmePath = Join-Path $packageDir "DEPLOY_README.md"
@"
# 离线部署说明

## 目录结构
```
$packageName/
├── docker-images/          # Docker镜像文件
├── source/                # 项目源代码
├── deploy-offline.ps1     # Windows部署脚本
├── deploy-offline.sh      # Linux部署脚本
└── DEPLOY_README.md       # 本文件
```

## Windows 环境部署

1. 确保已安装 Docker Desktop
2. 右键以管理员身份运行 `deploy-offline.ps1`
3. 按照提示操作

## Linux 环境部署

1. 确保已安装 Docker 和 Docker Compose
2. 运行: `chmod +x deploy-offline.sh && ./deploy-offline.sh`

## 更多信息

请参考项目根目录的 README.md 获取详细使用说明。
"@ | Out-File -FilePath $readmePath -Encoding UTF8

Write-Status "[OK] 部署脚本复制完成" "Success"
Write-Host ""

# ============================================================
# Step 4: 创建压缩包
# ============================================================
Write-Status "步骤 4/4: 创建压缩包..." "Info"

$zipPath = Join-Path (Join-Path $scriptDir $outputDir) "$packageName.zip"

# Compress the package
Write-Status "  正在压缩: $zipPath" "Info"
Compress-Archive -Path $packageDir -DestinationPath $zipPath -Force

# Get file size
$fileSize = (Get-Item $zipPath).Length / 1MB
Write-Status "[OK] 压缩完成: $([math]::Round($fileSize, 2)) MB" "Success"
Write-Host ""

# Cleanup temporary directory
Remove-Item -Path $packageDir -Recurse -Force

# Summary
Write-Status "========================================" "Header"
Write-Status "打包完成!" "Header"
Write-Status "========================================" "Header"
Write-Host ""
Write-Status "包文件: $zipPath" "Success"
Write-Status "大小: $([math]::Round($fileSize, 2)) MB" "Info"
Write-Host ""
Write-Status "使用方法:" "Info"
Write-Status "  1. 将 $packageName.zip 传输到目标服务器" "Info"
Write-Status "  2. 解压后运行 deploy-offline.ps1 (Windows) 或 deploy-offline.sh (Linux)" "Info"
Write-Host ""
