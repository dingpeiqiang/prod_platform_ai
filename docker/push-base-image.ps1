<#
.SYNOPSIS
推送基础镜像到镜像仓库

.DESCRIPTION
将构建好的基础镜像推送到指定的镜像仓库
#>

param(
    [string]$ImageName = "prod-platform-base",
    [string]$ImageTag = "1.0",
    [string]$Registry = "docker.io",
    [string]$Namespace = "your-namespace"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "推送基础镜像到仓库" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 检查 Docker 是否可用
try {
    docker --version | Out-Null
} catch {
    Write-Error "Docker 不可用，请确保 Docker 已安装并运行"
    exit 1
}

# 检查镜像是否存在
$LocalTag = "$ImageName`:$ImageTag"
Write-Host "检查本地镜像: $LocalTag" -ForegroundColor Yellow

$ImageExists = docker images -q $LocalTag
if (-not $ImageExists) {
    Write-Error "❌ 本地镜像不存在: $LocalTag"
    Write-Error "请先运行 build-base-image.ps1 构建镜像"
    exit 1
}

Write-Host "✅ 本地镜像存在" -ForegroundColor Green

# 构建完整的仓库标签
$RemoteTag = "$Registry`/$Namespace`/$ImageName`:$ImageTag"
Write-Host "`n远程镜像标签: $RemoteTag" -ForegroundColor Yellow

# 打标签
Write-Host "打标签..." -ForegroundColor Cyan
docker tag $LocalTag $RemoteTag

if ($LASTEXITCODE -ne 0) {
    Write-Error "❌ 打标签失败！"
    exit 1
}

# 登录仓库（可选）
Write-Host "`n是否需要登录仓库? (Y/N)" -ForegroundColor Cyan
$Answer = Read-Host
if ($Answer -eq "Y" -or $Answer -eq "y") {
    Write-Host "登录 $Registry..." -ForegroundColor Cyan
    docker login $Registry
}

# 推送镜像
Write-Host "`n推送镜像..." -ForegroundColor Cyan
docker push $RemoteTag

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✅ 镜像推送成功！" -ForegroundColor Green
    Write-Host "远程镜像: $RemoteTag" -ForegroundColor Green
} else {
    Write-Error "❌ 镜像推送失败！"
    exit 1
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "推送完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
