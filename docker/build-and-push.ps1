# ============================================================
# Build base images and export to TAR files
# ============================================================

$ErrorActionPreference = "Stop"

# Configuration
$registry = "10.86.12.11:20200"
$username = "dingpq"
$password = "Docker.2022!"
$project = "crm-pgcent"

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

function Build-Image {
    param(
        [string]$Name,
        [string]$LocalTag,
        [string]$RemoteTag,
        [string]$Dockerfile
    )
    
    Write-Host ""
    Write-Status "Building $Name" "Info"
    
    $context = Split-Path -Parent $Dockerfile
    try {
        docker build -t $LocalTag -f $Dockerfile $context
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed"
        }
        Write-Status "[OK] $Name built successfully" "Success"
        
        # Tag with remote registry
        docker tag $LocalTag $RemoteTag
        Write-Status "[OK] Tagged as $RemoteTag" "Success"
        
        return $true
    }
    catch {
        Write-Status "[ERROR] Failed to build $Name : $_" "Error"
        return $false
    }
}

function Save-Image {
    param(
        [string]$Tag,
        [string]$OutputFile
    )
    
    try {
        Write-Status "Saving to $OutputFile..." "Info"
        docker save $Tag -o $OutputFile
        if ($LASTEXITCODE -ne 0) {
            throw "Save failed"
        }
        $size = (Get-Item $OutputFile).Length / 1MB
        Write-Status "[OK] Saved ($([math]::Round($size, 2)) MB)" "Success"
        return $true
    }
    catch {
        Write-Status "[ERROR] Failed to save image: $_" "Error"
        return $false
    }
}

# Main
Write-Host ""
Write-Status "========================================" "Header"
Write-Status "Build and Export Base Images" "Header"
Write-Status "========================================" "Header"
Write-Host ""
Write-Status "Registry: http://$registry" "Info"
Write-Status "Project: $project" "Info"
Write-Host ""
Write-Status "This script builds and exports images to TAR files." "Warning"
Write-Status "Copy these TAR files to internal network and import them there." "Warning"
Write-Host ""

# Get project root (go up one level from script directory)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
Set-Location $projectRoot
Write-Status "Working from: $projectRoot" "Info"

# Image list
$images = @(
    [PSCustomObject]@{
        Name = "Backend Builder"
        LocalTag = "ai-form-backend-builder:latest"
        RemoteTag = "$registry/$project/ai-form-backend-builder:latest"
        Dockerfile = "docker/base-images/backend-builder/Dockerfile"
        OutputFile = "docker/ai-form-backend-builder.tar"
    },
    [PSCustomObject]@{
        Name = "Backend Runtime"
        LocalTag = "ai-form-backend-runtime:latest"
        RemoteTag = "$registry/$project/ai-form-backend-runtime:latest"
        Dockerfile = "docker/base-images/backend-runtime/Dockerfile"
        OutputFile = "docker/ai-form-backend-runtime.tar"
    },
    [PSCustomObject]@{
        Name = "Frontend Builder"
        LocalTag = "ai-form-frontend-builder:latest"
        RemoteTag = "$registry/$project/ai-form-frontend-builder:latest"
        Dockerfile = "docker/base-images/frontend-builder/Dockerfile"
        OutputFile = "docker/ai-form-frontend-builder.tar"
    }
)

Write-Host ""
Write-Status "Building $($images.Count) images..." "Info"
Write-Host ""

# Start building
$totalStartTime = Get-Date
$successCount = 0
$exportedFiles = @()

foreach ($img in $images) {
    if (Build-Image -Name $img.Name -LocalTag $img.LocalTag -RemoteTag $img.RemoteTag -Dockerfile $img.Dockerfile) {
        if (Save-Image -Tag $img.RemoteTag -OutputFile $img.OutputFile) {
            $successCount++
            $exportedFiles += $img.OutputFile
        }
    }
}

# Summary
$totalEndTime = Get-Date
$totalDuration = ($totalEndTime - $totalStartTime).TotalSeconds

Write-Host ""
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host "Build and Export Complete" -ForegroundColor $Colors.Success
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host ""
Write-Status "Success: $successCount / $($images.Count)" "Success"
Write-Status "Duration: $($totalDuration.ToString('F1')) seconds" "Info"
Write-Host ""

if ($successCount -eq $images.Count) {
    Write-Status "All images exported successfully!" "Success"
    Write-Host ""
    Write-Status "Copy these files to internal network:" "Info"
    foreach ($file in $exportedFiles) {
        Write-Host "  - $file" "Gray"
    }
    Write-Host ""
    Write-Status "On internal machine:" "Info"
    Write-Host "  1. docker load -i <filename.tar>" "Gray"
    Write-Host "  2. docker login $registry -u $username" "Gray"
    Write-Host "  3. docker push <image-tag>" "Gray"
}
