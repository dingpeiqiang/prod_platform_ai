# ============================================================
# Build and push base images to private registry
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
        [string]$Tag,
        [string]$Dockerfile
    )
    
    Write-Host ""
    Write-Status "Building $Name" "Info"
    
    $context = Split-Path -Parent $Dockerfile
    try {
        docker build -t $Tag -f $Dockerfile $context
        if ($LASTEXITCODE -ne 0) {
            throw "Build failed"
        }
        Write-Status "[OK] $Name built successfully" "Success"
        return $true
    }
    catch {
        Write-Status "[ERROR] Failed to build $Name : $_" "Error"
        return $false
    }
}

function Push-Image {
    param([string]$Tag)
    
    try {
        Write-Status "Pushing $Tag..." "Info"
        docker push $Tag
        if ($LASTEXITCODE -ne 0) {
            throw "Push failed"
        }
        Write-Status "[OK] Push successful" "Success"
        return $true
    }
    catch {
        Write-Status "[ERROR] Failed to push: $_" "Error"
        return $false
    }
}

# Main
Write-Host ""
Write-Status "========================================" "Header"
Write-Status "Build and Push Base Images" "Header"
Write-Status "========================================" "Header"
Write-Host ""
Write-Status "Registry: http://$registry" "Info"
Write-Status "Project: $project" "Info"
Write-Host ""
Write-Status "IMPORTANT: Configure Docker to allow insecure registry first!" "Warning"
Write-Host "  Add `"insecure-registries`": [`"10.86.12.11:20200`"] in Docker Engine config" -ForegroundColor Gray
Write-Host ""

# Login to registry
Write-Status "Logging in to private registry..." "Info"
$password | docker login $registry -u $username --password-stdin
if ($LASTEXITCODE -ne 0) {
    Write-Status "[ERROR] Login failed" "Error"
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Status "[OK] Login successful" "Success"

# Get project root (go up one level from script directory)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
Set-Location $projectRoot
Write-Status "Working from: $projectRoot" "Info"

# Image list - build all by default
$images = @(
    [PSCustomObject]@{
        Name = "Backend Builder"
        LocalTag = "ai-form-backend-builder:latest"
        RemoteTag = "$registry/$project/ai-form-backend-builder:latest"
        Dockerfile = "docker/base-images/backend-builder/Dockerfile"
    },
    [PSCustomObject]@{
        Name = "Backend Runtime"
        LocalTag = "ai-form-backend-runtime:latest"
        RemoteTag = "$registry/$project/ai-form-backend-runtime:latest"
        Dockerfile = "docker/base-images/backend-runtime/Dockerfile"
    },
    [PSCustomObject]@{
        Name = "Frontend Builder"
        LocalTag = "ai-form-frontend-builder:latest"
        RemoteTag = "$registry/$project/ai-form-frontend-builder:latest"
        Dockerfile = "docker/base-images/frontend-builder/Dockerfile"
    }
)

Write-Host ""
Write-Status "Building $($images.Count) images..." "Info"
Write-Host ""

# Start building
$totalStartTime = Get-Date
$successCount = 0

foreach ($img in $images) {
    if (Build-Image -Name $img.Name -Tag $img.LocalTag -Dockerfile $img.Dockerfile) {
        # Tag
        docker tag $img.LocalTag $img.RemoteTag
        
        if (Push-Image -Tag $img.RemoteTag) {
            $successCount++
        }
    }
}

# Summary
$totalEndTime = Get-Date
$totalDuration = ($totalEndTime - $totalStartTime).TotalSeconds

Write-Host ""
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host "Build Complete" -ForegroundColor $Colors.Success
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host ""
Write-Status "Success: $successCount / $($images.Count)" "Success"
Write-Status "Duration: $($totalDuration.ToString('F1')) seconds" "Info"
Write-Host ""

if ($successCount -eq $images.Count) {
    Write-Status "All images successfully pushed to: http://$registry/$project" "Success"
}
