<#
.SYNOPSIS
Build AI dynamic form backend base image

.DESCRIPTION
Build base image with all dependencies using Python 3.12 slim image (Tsinghua mirror)
#>

param(
    [string]$ImageName = "prod-platform-backend-base",
    [string]$ImageTag = "1.0",
    [string]$DockerfilePath = "docker/Dockerfile.base"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Build AI Dynamic Form Backend Base Image" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Check Docker availability
Write-Host "Checking Docker availability..." -ForegroundColor Cyan
try {
    $DockerVersion = docker --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw $DockerVersion
    }
    Write-Host "[OK] Docker version: $DockerVersion" -ForegroundColor Green
} catch {
    Write-Error "[FAIL] Docker is not available!"
    Write-Error "Please check:"
    Write-Error "  1. Docker Desktop is installed"
    Write-Error "  2. Docker Desktop is running"
    Write-Error "  3. Linux container mode is enabled"
    Write-Error "Error details: $_"
    exit 1
}

# Get project root directory
$ProjectRoot = (Get-Item $PSScriptRoot).Parent.FullName
Write-Host "Project root: $ProjectRoot" -ForegroundColor Green

# Switch to project root
Set-Location $ProjectRoot

# Build image
$FullTag = "$ImageName`:$ImageTag"
Write-Host "`nBuilding image: $FullTag" -ForegroundColor Yellow

docker build -f $DockerfilePath -t $FullTag .

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n[OK] Base image built successfully!" -ForegroundColor Green
    Write-Host "Image name: $FullTag" -ForegroundColor Green
    
    # Show image info
    Write-Host "`nImage info:" -ForegroundColor Cyan
    docker images $ImageName
} else {
    Write-Error "[FAIL] Base image build failed!"
    exit 1
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Build Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan