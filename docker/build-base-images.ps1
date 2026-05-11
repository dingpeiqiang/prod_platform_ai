# ============================================================
# AI Form - 构建本地基础镜像脚本
# ============================================================

$ErrorActionPreference = "Stop"

# 颜色配置
$Colors = @{
    Success = "Green"
    Warning = "Yellow"
    Error = "Red"
    Info = "Cyan"
    Header = "DarkCyan"
}

Write-Host ""
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host "   AI Form - 构建本地基础镜像" -ForegroundColor $Colors.Header
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host ""

# 切换到项目根目录
$ScriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptPath
Set-Location $ProjectRoot

Write-Status -Message "项目根目录: $ProjectRoot" -Type "Info"
Write-Host ""

# 辅助函数
function Write-Status {
    param([string]$Message, [string]$Type = "Info")
    $color = $Colors[$Type]
    Write-Host $Message -ForegroundColor $color
}

function Build-Image {
    param(
        [string]$Name,
        [string]$Tag,
        [string]$DockerfilePath,
        [string]$ContextPath
    )
    
    Write-Status "----------------------------------------" -Type "Info"
    Write-Status "构建镜像: $Name" -Type "Info"
    Write-Status "Dockerfile: $DockerfilePath" -Type "Info"
    Write-Status "上下文: $ContextPath" -Type "Info"
    Write-Host ""
    
    $startTime = Get-Date
    try {
        docker build -t $Tag -f $DockerfilePath $ContextPath
        if ($LASTEXITCODE -ne 0) {
            throw "构建镜像 $Name 失败"
        }
        
        $endTime = Get-Date
        $duration = ($endTime - $startTime).TotalSeconds
        Write-Status "[成功] $Name 构建完成 ($($duration.ToString('F1'))s)" -Type "Success"
        Write-Host ""
        return $true
    }
    catch {
        Write-Status "[错误] $_" -Type "Error"
        return $false
    }
}

# 基础镜像列表
$baseImages = @(
    @{
        Name = "后端构建基础镜像"
        Tag = "ai-form-backend-builder:latest"
        Dockerfile = "$ProjectRoot/docker/base-images/backend-builder/Dockerfile"
        Context = "$ProjectRoot/docker/base-images/backend-builder"
    },
    @{
        Name = "后端运行时基础镜像"
        Tag = "ai-form-backend-runtime:latest"
        Dockerfile = "$ProjectRoot/docker/base-images/backend-runtime/Dockerfile"
        Context = "$ProjectRoot/docker/base-images/backend-runtime"
    },
    @{
        Name = "前端构建基础镜像"
        Tag = "ai-form-frontend-builder:latest"
        Dockerfile = "$ProjectRoot/docker/base-images/frontend-builder/Dockerfile"
        Context = "$ProjectRoot/docker/base-images/frontend-builder"
    }
)

# 询问用户是否要构建所有镜像
Write-Status "请选择要构建的镜像:" -Type "Header"
Write-Host "  1. 所有基础镜像（推荐）" -ForegroundColor Gray
Write-Host "  2. 仅后端基础镜像" -ForegroundColor Gray
Write-Host "  3. 仅前端基础镜像" -ForegroundColor Gray
Write-Host ""
$choice = Read-Host "请输入选项 (1-3，默认为1)"

if ([string]::IsNullOrWhiteSpace($choice)) {
    $choice = "1"
}

# 根据选择过滤镜像
$imagesToBuild = @()
switch ($choice) {
    "1" {
        $imagesToBuild = $baseImages
    }
    "2" {
        $imagesToBuild = $baseImages | Where-Object { $_.Name -like "*后端*" }
    }
    "3" {
        $imagesToBuild = $baseImages | Where-Object { $_.Name -like "*前端*" }
    }
    default {
        Write-Status "无效选项，将构建所有镜像" -Type "Warning"
        $imagesToBuild = $baseImages
    }
}

Write-Host ""
Write-Status "开始构建... ($($imagesToBuild.Count) 个镜像)" -Type "Info"
Write-Host ""

$successCount = 0
$totalStartTime = Get-Date

foreach ($img in $imagesToBuild) {
    if (Build-Image -Name $img.Name -Tag $img.Tag -DockerfilePath $img.Dockerfile -ContextPath $img.Context) {
        $successCount++
    }
}

$totalEndTime = Get-Date
$totalDuration = ($totalEndTime - $totalStartTime).TotalSeconds

Write-Host ""
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host "   构建完成" -ForegroundColor $Colors.Success
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host ""
Write-Status "成功: $successCount / $($imagesToBuild.Count) 个镜像" -Type "Success"
Write-Status "总耗时: $($totalDuration.ToString('F1'))s" -Type "Info"
Write-Host ""
Write-Status "构建的镜像:" -Type "Info"
foreach ($img in $imagesToBuild) {
    Write-Host "  - $($img.Tag)" -ForegroundColor Gray
}
Write-Host ""
Write-Status "现在可以使用这些基础镜像构建应用了!" -Type "Success"
Write-Host ""
Read-Host "按回车键退出"
