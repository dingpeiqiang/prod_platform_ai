# ============================================================
# AI Form - 构建并导出基础镜像脚本（支持私有仓库）
# ============================================================

$ErrorActionPreference = "Stop"

# 私有仓库配置
$registry = "10.86.12.11:20200"
$username = "dingpq"
$password = "Docker.2022!"
$project = "crm-pgcent"

# Colors
$Colors = @{
    Success = "Green"
    Warning = "Yellow"
    Error = "Error"
    Info = "Cyan"
    Header = "DarkCyan"
}

Write-Host ""
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host "   AI Form - 构建并导出基础镜像" -ForegroundColor $Colors.Header
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host ""

# Switch to script directory
$ScriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptPath

# Helper function for status
function Write-Status {
    param([string]$Message, [string]$Type = "Info")
    $color = $Colors[$Type]
    Write-Host $Message -ForegroundColor $color
}

# 基础镜像列表
$baseImages = @(
    @{ Name = "python:3.10-slim"; File = "python-3.10-slim.tar"; Desc = "后端基础镜像" },
    @{ Name = "node:20-alpine"; File = "node-20-alpine.tar"; Desc = "前端构建基础镜像" },
    @{ Name = "nginx:alpine"; File = "nginx-alpine.tar"; Desc = "前端运行基础镜像" }
)

Write-Status "配置信息:" -ForegroundColor $Colors.Info
Write-Host "  - 私有仓库: $registry" -ForegroundColor Gray
Write-Host "  - 用户名: $username" -ForegroundColor Gray
Write-Host "  - 项目: $project" -ForegroundColor Gray
Write-Host ""
Write-Status "以下基础镜像将被处理:" -ForegroundColor $Colors.Info
foreach ($img in $baseImages) {
    Write-Host "  - $($img.Name) - $($img.Desc)" -ForegroundColor Gray
}
Write-Host ""

Write-Status "[1/6] 登录私有仓库..." -ForegroundColor $Colors.Info
docker login $registry -u $username -p $password
if ($LASTEXITCODE -ne 0) {
    Write-Status "[警告] 登录失败，将尝试导出本地镜像" -ForegroundColor $Colors.Warning
} else {
    Write-Status "[OK] 登录成功" -ForegroundColor $Colors.Success
}
Write-Host ""

Write-Status "[2/6] 检查私有仓库镜像..." -ForegroundColor $Colors.Info
Write-Host ""

$useRegistry = $true
$imagesFromRegistry = @()
$imagesToPull = @()

foreach ($img in $baseImages) {
    $registryImage = "$registry/$project/$($img.Name -replace ':','-')"
    Write-Status "检查 $registryImage..." -ForegroundColor $Colors.Info
    
    # 尝试拉取私有仓库镜像
    docker pull $registryImage 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Status "[OK] 从私有仓库获取 $($img.Name)" -ForegroundColor $Colors.Success
        # 重新打标签
        docker tag $registryImage $img.Name
        $imagesFromRegistry += $img
    } else {
        Write-Status "[跳过] 私有仓库无此镜像" -ForegroundColor $Colors.Warning
        $imagesToPull += $img
    }
    Write-Host ""
}

if ($imagesToPull.Count -gt 0) {
    Write-Status "[3/6] 从Docker Hub拉取剩余镜像..." -ForegroundColor $Colors.Info
    Write-Host ""
    
    foreach ($img in $imagesToPull) {
        Write-Status "拉取 $($img.Name)..." -ForegroundColor $Colors.Info
        docker pull $img.Name
        if ($LASTEXITCODE -ne 0) {
            Write-Status "拉取 $($img.Name) 失败!" -ForegroundColor $Colors.Error
            Read-Host "按回车键退出"
            exit 1
        }
        Write-Status "[OK] $($img.Name) 已拉取" -ForegroundColor $Colors.Success
        
        # 推送到私有仓库
        $registryImage = "$registry/$project/$($img.Name -replace ':','-')"
        Write-Status "推送到私有仓库: $registryImage" -ForegroundColor $Colors.Info
        docker tag $img.Name $registryImage
        docker push $registryImage
        if ($LASTEXITCODE -ne 0) {
            Write-Status "[警告] 推送失败" -ForegroundColor $Colors.Warning
        } else {
            Write-Status "[OK] 推送成功" -ForegroundColor $Colors.Success
        }
        Write-Host ""
    }
} else {
    Write-Status "[3/6] 全部镜像从私有仓库获取完成" -ForegroundColor $Colors.Success
}

Write-Status "[4/6] 导出基础镜像..." -ForegroundColor $Colors.Info
Write-Host ""

$totalSize = 0
$exportedFiles = @()

foreach ($img in $baseImages) {
    Write-Status "导出 $($img.Name) -> $($img.File)..." -ForegroundColor $Colors.Info
    docker save $img.Name -o $img.File
    if ($LASTEXITCODE -ne 0) {
        Write-Status "导出 $($img.Name) 失败!" -ForegroundColor $Colors.Error
        Read-Host "按回车键退出"
        exit 1
    }
    
    $size = (Get-Item $img.File).Length
    $sizeMB = [math]::Round($size / 1MB, 2)
    $totalSize += $size
    $exportedFiles += $img.File
    
    Write-Status "[OK] $($img.File) ($sizeMB MB)" -ForegroundColor $Colors.Success
    Write-Host ""
}

$sw.Stop()
$totalSizeMB = [math]::Round($totalSize / 1MB, 2)
Write-Status "导出完成! 耗时: $($sw.Elapsed.TotalSeconds.ToString('F1'))s, 总大小: $totalSizeMB MB" -ForegroundColor $Colors.Success
Write-Host ""

Write-Status "[5/6] 验证导出文件..." -ForegroundColor $Colors.Info
Write-Host ""

$allOk = $true
foreach ($file in $exportedFiles) {
    if (Test-Path $file) {
        $size = (Get-Item $file).Length
        $sizeMB = [math]::Round($size / 1MB, 2)
        Write-Status "[OK] $file ($sizeMB MB)" -ForegroundColor $Colors.Success
    } else {
        Write-Status "[错误] $file 不存在!" -ForegroundColor $Colors.Error
        $allOk = $false
    }
}

Write-Host ""

if (-not $allOk) {
    Write-Status "部分文件验证失败!" -ForegroundColor $Colors.Error
    Read-Host "按回车键退出"
    exit 1
}

Write-Status "[6/6] 完成!" -ForegroundColor $Colors.Info
Write-Host ""
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host "   基础镜像构建导出完成!" -ForegroundColor $Colors.Success
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host ""
Write-Status "请将以下文件传输到内网服务器:" -ForegroundColor $Colors.Info
foreach ($file in $exportedFiles) {
    Write-Host "  - docker/$file" -ForegroundColor Gray
}
Write-Host ""
Write-Status "在内网使用 import-base-images.ps1 脚本导入这些镜像" -ForegroundColor $Colors.Info
Write-Host ""
Read-Host "按回车键退出"
