# ============================================================
# 构建并推送基础镜像到私有仓库
# ============================================================

$ErrorActionPreference = "Stop"

# 配置
$registry = "10.86.12.11:20200"
$username = "dingpq"
$password = "Docker.2022!"
$project = "crm-pgcent"

# 颜色
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

function Build-Image {
    param(
        [string]$Name,
        [string]$Tag,
        [string]$Dockerfile
    )
    
    Write-Host ""
    Write-Status "========================================" "Info"
    Write-Status "构建 $Name" "Info"
    Write-Status "========================================" "Info"
    
    $context = Split-Path -Parent $Dockerfile
    try {
        docker build -t $Tag -f $Dockerfile $context
        if ($LASTEXITCODE -ne 0) {
            throw "构建失败"
        }
        Write-Status "✅ $Name 构建成功" "Success"
        return $true
    }
    catch {
        Write-Status "❌ $Name 构建失败: $_" "Error"
        return $false
    }
}

function Push-Image {
    param([string]$Tag)
    
    try {
        Write-Status "推送 $Tag..." "Info"
        docker push $Tag
        if ($LASTEXITCODE -ne 0) {
            throw "推送失败"
        }
        Write-Status "✅ 推送成功" "Success"
        return $true
    }
    catch {
        Write-Status "❌ 推送失败: $_" "Error"
        return $false
    }
}

# 主程序
Write-Host ""
Write-Status "========================================" "Header"
Write-Status "构建并推送基础镜像" "Header"
Write-Status "========================================" "Header"
Write-Host ""
Write-Status "仓库: $registry" "Info"
Write-Status "项目: $project" "Info"
Write-Host ""

# 登录仓库
Write-Status "登录私有仓库..." "Info"
docker login $registry -u $username -p $password
if ($LASTEXITCODE -ne 0) {
    Write-Status "❌ 登录失败，请检查用户名和密码" "Error"
    Read-Host "按回车键退出"
    exit 1
}
Write-Status "✅ 登录成功" "Success"

# 镜像列表
$images = @(
    @{
        Name = "后端构建基础镜像"
        LocalTag = "ai-form-backend-builder:latest"
        RemoteTag = "$registry/$project/ai-form-backend-builder:latest"
        Dockerfile = "docker/base-images/backend-builder/Dockerfile"
    },
    @{
        Name = "后端运行时基础镜像"
        LocalTag = "ai-form-backend-runtime:latest"
        RemoteTag = "$registry/$project/ai-form-backend-runtime:latest"
        Dockerfile = "docker/base-images/backend-runtime/Dockerfile"
    },
    @{
        Name = "前端构建基础镜像"
        LocalTag = "ai-form-frontend-builder:latest"
        RemoteTag = "$registry/$project/ai-form-frontend-builder:latest"
        Dockerfile = "docker/base-images/frontend-builder/Dockerfile"
    }
)

# 选择要构建的镜像
Write-Host ""
Write-Status "请选择要构建和推送的镜像:" "Header"
Write-Host "  1. 全部" -ForegroundColor Gray
Write-Host "  2. 仅后端基础镜像" -ForegroundColor Gray
Write-Host "  3. 仅前端基础镜像" -ForegroundColor Gray
Write-Host ""
$choice = Read-Host "请输入选项 (1-3，默认1)"

if ([string]::IsNullOrWhiteSpace($choice)) {
    $choice = "1"
}

# 根据选择过滤
$selectedImages = @()
switch ($choice) {
    "1" { $selectedImages = $images }
    "2" { $selectedImages = $images | Where-Object { $_.Name -like "*后端*" } }
    "3" { $selectedImages = $images | Where-Object { $_.Name -like "*前端*" } }
    default { $selectedImages = $images }
}

Write-Host ""
Write-Status "准备构建 $($selectedImages.Count) 个镜像" "Info"
$continue = Read-Host "继续？(Y/N，默认Y)"

if ($continue -and $continue.ToLower() -ne "y" -and $continue -ne "") {
    Write-Status "已取消" "Warning"
    exit 0
}

# 开始构建
$totalStartTime = Get-Date
$successCount = 0

foreach ($img in $selectedImages) {
    if (Build-Image -Name $img.Name -Tag $img.LocalTag -Dockerfile $img.Dockerfile) {
        # 打标签
        docker tag $img.LocalTag $img.RemoteTag
        Write-Status "打标签: $img.RemoteTag" "Info"
        
        if (Push-Image -Tag $img.RemoteTag) {
            $successCount++
        }
    }
}

# 总结
$totalEndTime = Get-Date
$totalDuration = ($totalEndTime - $totalStartTime).TotalSeconds

Write-Host ""
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host "构建完成" -ForegroundColor $Colors.Success
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host ""
Write-Status "成功: $successCount / $($selectedImages.Count)" "Success"
Write-Status "耗时: $($totalDuration.ToString('F1'))秒" "Info"
Write-Host ""

if ($successCount -eq $selectedImages.Count) {
    Write-Status "所有镜像已成功推送到: $registry/$project" "Success"
}

Read-Host "按回车键退出"
