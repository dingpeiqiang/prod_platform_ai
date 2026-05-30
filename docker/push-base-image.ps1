<#
.SYNOPSIS
Push base image to private registry

.DESCRIPTION
Push the built base image to a private HTTP registry
Default: 10.86.12.11:20200 / crm-pgcent / dingpq

.NOTES
For HTTP (non-HTTPS) registry, configure Docker daemon:
  Add "insecure-registries": ["10.86.12.11:20200"] to daemon.json
#>

param(
    [string]$ImageName = "prod-platform-backend-base",
    [string]$ImageTag = "1.0",
    [string]$Registry = "10.86.12.11:20200",
    [string]$Namespace = "crm-pgcent",
    [string]$Username = "dingpq"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Push Base Image to Registry" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Check Docker availability
try {
    docker --version | Out-Null
} catch {
    Write-Error "Docker is not available, please ensure Docker is installed and running"
    exit 1
}

# Check local image
$LocalTag = "$ImageName`:$ImageTag"
Write-Host "Checking local image: $LocalTag" -ForegroundColor Yellow

$ImageExists = docker images -q $LocalTag
if (-not $ImageExists) {
    Write-Error "Local image not found: $LocalTag"
    Write-Error "Please run build-base-image.ps1 first"
    exit 1
}

Write-Host "Local image exists" -ForegroundColor Green

# Build remote tag
$RemoteTag = "$Registry`/$Namespace`/$ImageName`:$ImageTag"
Write-Host "`nRemote image tag: $RemoteTag" -ForegroundColor Yellow

# Tag image
Write-Host "Tagging image..." -ForegroundColor Cyan
docker tag $LocalTag $RemoteTag

if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to tag image!"
    exit 1
}

# Login to registry
Write-Host "`nLogging in to $Registry ..." -ForegroundColor Cyan
Write-Host "Username: $Username" -ForegroundColor Yellow
$Password = "Docker.2022!"
$Password | docker login $Registry --username $Username --password-stdin
$Password = $null

if ($LASTEXITCODE -ne 0) {
    Write-Warning "Login failed, check the following:"
    Write-Warning "  1. Registry URL: $Registry"
    Write-Warning "  2. For HTTP registry, add --insecure-registry $Registry to Docker daemon config"
    Write-Warning "  3. Network connectivity"
    exit 1
}

# Push image
Write-Host "`nPushing image..." -ForegroundColor Cyan
docker push $RemoteTag

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nImage pushed successfully!" -ForegroundColor Green
    Write-Host "Remote image: $RemoteTag" -ForegroundColor Green
} else {
    Write-Error "Failed to push image!"
    exit 1
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Done" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan