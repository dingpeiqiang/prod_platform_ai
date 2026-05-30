<#
.SYNOPSIS
Docker Image Management Script for AI Dynamic Form Platform

.DESCRIPTION
A unified script to build and push Docker images with menu-based selection.
Supports both backend and frontend base images with separate configurations.
#>

# ==============================================
# Configuration Section - Separated for easy management
# ==============================================
# WARNING: 修改版本号时，请同步更新 Sitech.BJ.Dockerfile.backend 和 Sitech.BJ.Dockerfile.frontend 中的 FROM 指令
$Config = @{
    BackendBase = @{
        ImageName     = "prod-platform-backend-base"
        ImageTag      = "1.2"  # 手动修改版本号，每次更新基础镜像时递增
        Dockerfile    = "docker/Dockerfile.base.backend"
        Registry      = "10.86.12.11:20200"
        Namespace     = "y21127-crmpos"
        Username      = "dingpq"
        Password      = "Docker.2022!"
    }
    FrontendBase = @{
        ImageName     = "prod-platform-frontend-base"
        ImageTag      = "1.2"  # 手动修改版本号，每次更新基础镜像时递增
        Dockerfile    = "docker/Dockerfile.base.frontend"
        Registry      = "10.86.12.11:20200"
        Namespace     = "y21127-crmpos"
        Username      = "dingpq"
        Password      = "Docker.2022!"
    }
}

# ==============================================
# Helper Functions
# ==============================================
function Test-DockerAvailability {
    Write-Host "Checking Docker availability..." -ForegroundColor Cyan
    try {
        $DockerVersion = docker --version 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw $DockerVersion
        }
        Write-Host "[OK] Docker version: $DockerVersion" -ForegroundColor Green
        return $true
    } catch {
        Write-Error "[FAIL] Docker is not available!"
        Write-Error "Please check:"
        Write-Error "  1. Docker Desktop is installed"
        Write-Error "  2. Docker Desktop is running"
        Write-Error "  3. Linux container mode is enabled"
        Write-Error "Error details: $_"
        return $false
    }
}

function Invoke-BuildImage {
    param(
        [string]$ImageName,
        [string]$ImageTag,
        [string]$DockerfilePath
    )
    
    $ProjectRoot = (Get-Item $PSScriptRoot).Parent.FullName
    Set-Location $ProjectRoot
    
    $FullTag = "$ImageName`:$ImageTag"
    
    # ==============================================
    # Warning message - update ImageTag before build
    # ==============================================
    Write-Host "`n" -ForegroundColor Red
    Write-Host "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" -ForegroundColor Red
    Write-Host "!!!                         WARNING                                  !!!" -ForegroundColor Red
    Write-Host "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" -ForegroundColor Red
    Write-Host "!!!  Current version: $FullTag" -ForegroundColor Yellow
    Write-Host "!!!" -ForegroundColor Red
    Write-Host "!!!  Update ImageTag in docker-manager.ps1 before building!          !!!" -ForegroundColor Red
    Write-Host "!!!  e.g. Change 1.2 to 1.3 for a new version.                      !!!" -ForegroundColor Red
    Write-Host "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" -ForegroundColor Red
    Write-Host "`n" -ForegroundColor Red
    
    Write-Host "==============================================" -ForegroundColor Cyan
    Write-Host "Building image: $FullTag" -ForegroundColor Yellow
    Write-Host "==============================================" -ForegroundColor Cyan
    
    docker buildx build -f $DockerfilePath -t $FullTag --no-cache --pull --provenance=false --sbom=false --output type=image,name=$FullTag,oci-mediatypes=false .
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n[OK] Image built successfully!" -ForegroundColor Green
        Write-Host "Image name: $FullTag" -ForegroundColor Green
        Write-Host "`nImage info:" -ForegroundColor Cyan
        docker images $ImageName
        return $true
    } else {
        Write-Error "[FAIL] Image build failed!"
        return $false
    }
}

function Invoke-PushImage {
    param(
        [string]$ImageName,
        [string]$ImageTag,
        [string]$Registry,
        [string]$Namespace,
        [string]$Username,
        [string]$Password
    )
    
    $ProjectRoot = (Get-Item $PSScriptRoot).Parent.FullName
    Set-Location $ProjectRoot
    
    $LocalTag = "$ImageName`:$ImageTag"
    
    Write-Host "Checking local image: $LocalTag" -ForegroundColor Yellow
    $ImageExists = docker images -q $LocalTag
    if (-not $ImageExists) {
        Write-Error "Local image not found: $LocalTag"
        Write-Error "Please build the image first"
        return $false
    }
    Write-Host "Local image exists" -ForegroundColor Green
    
    $RemoteTag = "$Registry`/$Namespace`/$ImageName`:$ImageTag"
    Write-Host "`nRemote image tag: $RemoteTag" -ForegroundColor Yellow
    
    Write-Host "Tagging image..." -ForegroundColor Cyan
    docker tag $LocalTag $RemoteTag
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to tag image!"
        return $false
    }
    
    Write-Host "`nLogging in to $Registry ..." -ForegroundColor Cyan
    Write-Host "Username: $Username" -ForegroundColor Yellow
    $Password | docker login $Registry --username $Username --password-stdin
    $Password = $null
    
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Login failed, check the following:"
        Write-Warning "  1. Registry URL: $Registry"
        Write-Warning "  2. For HTTP registry, add --insecure-registry $Registry to Docker daemon config"
        Write-Warning "  3. Network connectivity"
        return $false
    }
    Write-Host "[OK] Login successful!" -ForegroundColor Green
    
    Write-Host "`nPushing image..." -ForegroundColor Cyan
    docker push $RemoteTag
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n[OK] Image pushed successfully!" -ForegroundColor Green
        Write-Host "Remote image: $RemoteTag" -ForegroundColor Green
        return $true
    } else {
        Write-Error "[FAIL] Failed to push image!"
        Write-Warning "If the error mentions unsupported content type or OCI manifest, rebuild the image"
        return $false
    }
}

function Invoke-BuildAndPush {
    param(
        [hashtable]$Config,
        [string]$ImageType
    )
    
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "$ImageType - Build and Push" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    
    if (-not (Test-DockerAvailability)) {
        return
    }
    
    $buildSuccess = Invoke-BuildImage -ImageName $Config.ImageName -ImageTag $Config.ImageTag -DockerfilePath $Config.Dockerfile
    if ($buildSuccess) {
        Invoke-PushImage -ImageName $Config.ImageName -ImageTag $Config.ImageTag -Registry $Config.Registry -Namespace $Config.Namespace -Username $Config.Username -Password $Config.Password
    }
}

# ==============================================
# Main Menu
# ==============================================
function Show-Menu {
    Clear-Host
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "    Docker Image Management Tool" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  Backend Base Image:" -ForegroundColor Yellow
    Write-Host "    1. Build Backend Base Image"
    Write-Host "    2. Push Backend Base Image"
    Write-Host "    3. Build & Push Backend Base Image"
    Write-Host ""
    Write-Host "  Frontend Base Image:" -ForegroundColor Yellow
    Write-Host "    4. Build Frontend Base Image"
    Write-Host "    5. Push Frontend Base Image"
    Write-Host "    6. Build & Push Frontend Base Image"
    Write-Host ""
    Write-Host "  Other:" -ForegroundColor Yellow
    Write-Host "    7. Show Configurations"
    Write-Host "    0. Exit"
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
}

# ==============================================
# Main Execution
# ==============================================
do {
    Show-Menu
    $choice = Read-Host "Enter your choice (0-7)"
    
    switch ($choice) {
        "1" {
            Write-Host "`n========================================" -ForegroundColor Cyan
            Write-Host "Build Backend Base Image" -ForegroundColor Cyan
            Write-Host "========================================" -ForegroundColor Cyan
            
            if (Test-DockerAvailability) {
                Invoke-BuildImage -ImageName $Config.BackendBase.ImageName -ImageTag $Config.BackendBase.ImageTag -DockerfilePath $Config.BackendBase.Dockerfile
            }
        }
        
        "2" {
            Write-Host "`n========================================" -ForegroundColor Cyan
            Write-Host "Push Backend Base Image" -ForegroundColor Cyan
            Write-Host "========================================" -ForegroundColor Cyan
            
            if (Test-DockerAvailability) {
                Invoke-PushImage -ImageName $Config.BackendBase.ImageName -ImageTag $Config.BackendBase.ImageTag -Registry $Config.BackendBase.Registry -Namespace $Config.BackendBase.Namespace -Username $Config.BackendBase.Username -Password $Config.BackendBase.Password
            }
        }
        
        "3" {
            Invoke-BuildAndPush -Config $Config.BackendBase -ImageType "Backend Base Image"
        }
        
        "4" {
            Write-Host "`n========================================" -ForegroundColor Cyan
            Write-Host "Build Frontend Base Image" -ForegroundColor Cyan
            Write-Host "========================================" -ForegroundColor Cyan
            
            if (Test-DockerAvailability) {
                Invoke-BuildImage -ImageName $Config.FrontendBase.ImageName -ImageTag $Config.FrontendBase.ImageTag -DockerfilePath $Config.FrontendBase.Dockerfile
            }
        }
        
        "5" {
            Write-Host "`n========================================" -ForegroundColor Cyan
            Write-Host "Push Frontend Base Image" -ForegroundColor Cyan
            Write-Host "========================================" -ForegroundColor Cyan
            
            if (Test-DockerAvailability) {
                Invoke-PushImage -ImageName $Config.FrontendBase.ImageName -ImageTag $Config.FrontendBase.ImageTag -Registry $Config.FrontendBase.Registry -Namespace $Config.FrontendBase.Namespace -Username $Config.FrontendBase.Username -Password $Config.FrontendBase.Password
            }
        }
        
        "6" {
            Invoke-BuildAndPush -Config $Config.FrontendBase -ImageType "Frontend Base Image"
        }
        
        "7" {
            Write-Host "`n========================================" -ForegroundColor Cyan
            Write-Host "Current Configurations" -ForegroundColor Cyan
            Write-Host "========================================" -ForegroundColor Cyan
            
            Write-Host "`n[Backend Base Image]" -ForegroundColor Yellow
            $Config.BackendBase.GetEnumerator() | ForEach-Object {
                if ($_.Key -eq "Password") {
                    Write-Host "  $($_.Key): ********"
                } else {
                    Write-Host "  $($_.Key): $($_.Value)"
                }
            }
            
            Write-Host "`n[Frontend Base Image]" -ForegroundColor Yellow
            $Config.FrontendBase.GetEnumerator() | ForEach-Object {
                if ($_.Key -eq "Password") {
                    Write-Host "  $($_.Key): ********"
                } else {
                    Write-Host "  $($_.Key): $($_.Value)"
                }
            }
        }
        
        "0" {
            Write-Host "`nExiting..." -ForegroundColor Cyan
            break
        }
        
        default {
            Write-Error "`nInvalid choice! Please enter a number between 0 and 7."
        }
    }
    
    if ($choice -ne "0") {
        Read-Host "`nPress Enter to continue..."
    }
} while ($choice -ne "0")

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Done" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
