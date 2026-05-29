<#
.SYNOPSIS
构建 AI动态表单后端基础镜像

.DESCRIPTION
使用官方 Python 3.10 镜像构建包含所有依赖的基础镜像
#>

param(
    [string]$ImageName = "prod-platform-backend-base",
    [string]$ImageTag = "1.0",
    [string]$DockerfilePath = "docker/Dockerfile.base"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "构建 AI动态表单后端基础镜像" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 检查 Docker 是否可用
Write-Host "检查 Docker 是否可用..." -ForegroundColor Cyan
try {
    $DockerVersion = docker --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw $DockerVersion
    }
    Write-Host "✅ Docker 版本: $DockerVersion" -ForegroundColor Green
} catch {
    Write-Error "❌ Docker 不可用！"
    Write-Error "请检查："
    Write-Error "  1. Docker Desktop 是否已安装"
    Write-Error "  2. Docker Desktop 是否已启动"
    Write-Error "  3. 是否启用了 Linux 容器模式"
    Write-Error "错误详情: $_"
    exit 1
}

# 获取项目根目录（脚本所在目录即为项目根目录）
$ProjectRoot = $PSScriptRoot
Write-Host "项目根目录: $ProjectRoot" -ForegroundColor Green

# 切换到项目根目录
Set-Location $ProjectRoot

# 构建镜像
$FullTag = "$ImageName`:$ImageTag"
Write-Host "`n开始构建镜像: $FullTag" -ForegroundColor Yellow

docker build -f $DockerfilePath -t $FullTag .

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✅ 基础镜像构建成功！" -ForegroundColor Green
    Write-Host "镜像名称: $FullTag" -ForegroundColor Green
    
    # 显示镜像信息
    Write-Host "`n镜像信息:" -ForegroundColor Cyan
    docker images $ImageName
} else {
    Write-Error "❌ 基础镜像构建失败！"
    exit 1
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "构建完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
