# ============================================================
# AI Form - 导入基础镜像脚本（支持私有仓库）
# ============================================================

$ErrorActionPreference = "Stop"

# 私有仓库配置（与build-base-images.ps1保持一致）
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
    Prompt = "Magenta"
}

Write-Host ""
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host "   AI Form - 导入基础镜像" -ForegroundColor $Colors.Header
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

# 基础镜像列表（与build-base-images.ps1保持一致）
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
Write-Status "请选择镜像获取方式:" -ForegroundColor $Colors.Prompt
Write-Host "  [1] 从私有仓库拉取（推荐，速度快）" -ForegroundColor Gray
Write-Host "  [2] 从本地tar文件导入" -ForegroundColor Gray
Write-Host ""
$choice = Read-Host "请输入选项 (1/2，默认为1)"

if ([string]::IsNullOrWhiteSpace($choice)) {
    $choice = "1"
}

Write-Host ""

Write-Status "检查Docker运行状态..." -ForegroundColor $Colors.Info
try {
    docker info 2>&1 | Out-Null
    Write-Status "[OK] Docker运行正常" -ForegroundColor $Colors.Success
} catch {
    Write-Status "[错误] Docker未运行，请先启动Docker!" -ForegroundColor $Colors.Error
    Read-Host "按回车键退出"
    exit 1
}
Write-Host ""

if ($choice -eq "1") {
    # 方式1：从私有仓库拉取
    Write-Status "[方式1] 从私有仓库拉取镜像" -ForegroundColor $Colors.Info
    Write-Host ""
    
    Write-Status "[1/3] 登录私有仓库..." -ForegroundColor $Colors.Info
    docker login $registry -u $username -p $password
    if ($LASTEXITCODE -ne 0) {
        Write-Status "[错误] 登录失败!" -ForegroundColor $Colors.Error
        Read-Host "按回车键退出"
        exit 1
    }
    Write-Status "[OK] 登录成功" -ForegroundColor $Colors.Success
    Write-Host ""
    
    Write-Status "[2/3] 拉取镜像..." -ForegroundColor $Colors.Info
    Write-Host ""
    
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $successCount = 0
    
    foreach ($img in $baseImages) {
        $registryImage = "$registry/$project/$($img.Name -replace ':','-')"
        Write-Status "拉取 $registryImage..." -ForegroundColor $Colors.Info
        docker pull $registryImage
        if ($LASTEXITCODE -ne 0) {
            Write-Status "[错误] 拉取 $($img.Name) 失败!" -ForegroundColor $Colors.Error
        } else {
            Write-Status "[OK] 拉取成功" -ForegroundColor $Colors.Success
            # 重新打标签
            docker tag $registryImage $img.Name
            $successCount++
            Write-Status "[OK] $($img.Name) 已就绪" -ForegroundColor $Colors.Success
        }
        Write-Host ""
    }
    
    $sw.Stop()
    
    Write-Status "[3/3] 验证镜像..." -ForegroundColor $Colors.Info
    Write-Host ""
    
    $verifiedCount = 0
    $localImages = docker images --format "{{.Repository}}:{{.Tag}}"
    
    foreach ($img in $baseImages) {
        if ($localImages -match $img.Name) {
            Write-Status "[OK] $($img.Name) 已就绪" -ForegroundColor $Colors.Success
            $verifiedCount++
        } else {
            Write-Status "[警告] $($img.Name) 未找到" -ForegroundColor $Colors.Warning
        }
    }
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor $Colors.Header
    Write-Host "   从私有仓库拉取完成!" -ForegroundColor $Colors.Success
    Write-Host "========================================" -ForegroundColor $Colors.Header
    Write-Host ""
    Write-Status "统计信息:" -ForegroundColor $Colors.Info
    Write-Host "  - 成功拉取: $successCount / $($baseImages.Count) 个镜像" -ForegroundColor Gray
    Write-Host "  - 验证通过: $verifiedCount / $($baseImages.Count) 个镜像" -ForegroundColor Gray
    Write-Host "  - 拉取耗时: $($sw.Elapsed.TotalSeconds.ToString('F1'))s" -ForegroundColor Gray
    
} else {
    # 方式2：从本地文件导入
    Write-Status "[方式2] 从本地tar文件导入" -ForegroundColor $Colors.Info
    Write-Host ""
    
    Write-Status "[1/3] 检查镜像文件是否存在..." -ForegroundColor $Colors.Info
    Write-Host ""
    
    $filesToImport = @()
    $allFilesExist = $true
    
    foreach ($img in $baseImages) {
        if (Test-Path $img.File) {
            $size = (Get-Item $img.File).Length
            $sizeMB = [math]::Round($size / 1MB, 2)
            Write-Status "[OK] $($img.File) ($sizeMB MB) - $($img.Desc)" -ForegroundColor $Colors.Success
            $filesToImport += $img
        } else {
            Write-Status "[跳过] $($img.File) 不存在" -ForegroundColor $Colors.Warning
            $allFilesExist = $false
        }
    }
    
    Write-Host ""
    
    if ($filesToImport.Count -eq 0) {
        Write-Status "没有找到任何基础镜像文件!" -ForegroundColor $Colors.Error
        Write-Status "请确保从外网传输了以下文件到docker目录:" -ForegroundColor $Colors.Warning
        foreach ($img in $baseImages) {
            Write-Host "  - $($img.File)" -ForegroundColor Gray
        }
        Read-Host "按回车键退出"
        exit 1
    }
    
    Write-Status "[2/3] 导入基础镜像..." -ForegroundColor $Colors.Info
    Write-Host ""
    
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $importCount = 0
    
    foreach ($img in $filesToImport) {
        Write-Status "导入 $($img.File) -> $($img.Name)..." -ForegroundColor $Colors.Info
        docker load -i $img.File
        if ($LASTEXITCODE -ne 0) {
            Write-Status "[错误] 导入 $($img.File) 失败!" -ForegroundColor $Colors.Error
        } else {
            Write-Status "[OK] $($img.Name) 导入成功!" -ForegroundColor $Colors.Success
            $importCount++
        }
        Write-Host ""
    }
    
    $sw.Stop()
    
    Write-Status "[3/3] 验证镜像..." -ForegroundColor $Colors.Info
    Write-Host ""
    
    $verifiedCount = 0
    $localImages = docker images --format "{{.Repository}}:{{.Tag}}"
    
    foreach ($img in $baseImages) {
        if ($localImages -match $img.Name) {
            Write-Status "[OK] $($img.Name) 已就绪" -ForegroundColor $Colors.Success
            $verifiedCount++
        } else {
            Write-Status "[警告] $($img.Name) 未找到" -ForegroundColor $Colors.Warning
        }
    }
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor $Colors.Header
    Write-Host "   从本地文件导入完成!" -ForegroundColor $Colors.Success
    Write-Host "========================================" -ForegroundColor $Colors.Header
    Write-Host ""
    Write-Status "统计信息:" -ForegroundColor $Colors.Info
    Write-Host "  - 成功导入: $importCount / $($baseImages.Count) 个镜像" -ForegroundColor Gray
    Write-Host "  - 验证通过: $verifiedCount / $($baseImages.Count) 个镜像" -ForegroundColor Gray
    Write-Host "  - 导入耗时: $($sw.Elapsed.TotalSeconds.ToString('F1'))s" -ForegroundColor Gray
}

Write-Host ""
Write-Status "现在可以运行 build-export.ps1 构建应用镜像了!" -ForegroundColor $Colors.Info
Write-Host ""
Read-Host "按回车键退出"
