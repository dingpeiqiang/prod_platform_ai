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
    Write-Status "========================================" "Info"
    Write-Status "Building $Name" "Info"
    Write-Status "========================================" "Info"
    
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
Write-Status "Registry: $registry" "Info"
Write-Status "Project: $project" "Info"
Write-Host ""

# Login to registry
Write-Status "Logging in to private registry..." "Info"
docker login $registry -u $username -p $password
if ($LASTEXITCODE -ne 0) {
    Write-Status "[ERROR] Login failed, please check username and password" "Error"
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Status "[OK] Login successful" "Success"

# Image list
$images = @(
    @{
        Name = "Backend Builder"
        LocalTag = "ai-form-backend-builder:latest"
        RemoteTag = "$registry/$project/ai-form-backend-builder:latest"
        Dockerfile = "docker/base-images/backend-builder/Dockerfile"
    },
    @{
        Name = "Backend Runtime"
        LocalTag = "ai-form-backend-runtime:latest"
        RemoteTag = "$registry/$project/ai-form-backend-runtime:latest"
        Dockerfile = "docker/base-images/backend-runtime/Dockerfile"
    },
    @{
        Name = "Frontend Builder"
        LocalTag = "ai-form-frontend-builder:latest"
        RemoteTag = "$registry/$project/ai-form-frontend-builder:latest"
        Dockerfile = "docker/base-images/frontend-builder/Dockerfile"
    }
)

# Select images to build
Write-Host ""
Write-Status "Select images to build and push:" "Header"
Write-Host "  1. All" -ForegroundColor Gray
Write-Host "  2. Backend only" -ForegroundColor Gray
Write-Host "  3. Frontend only" -ForegroundColor Gray
Write-Host ""
$choice = Read-Host "Enter choice (1-3, default 1)"

if ([string]::IsNullOrWhiteSpace($choice)) {
    $choice = "1"
}

# Filter by choice
$selectedImages = @()
switch ($choice) {
    "1" { $selectedImages = $images }
    "2" { $selectedImages = $images | Where-Object { $_.Name -like "*Backend*" } }
    "3" { $selectedImages = $images | Where-Object { $_.Name -like "*Frontend*" } }
    default { $selectedImages = $images }
}

Write-Host ""
Write-Status "Ready to build $($selectedImages.Count) image(s)" "Info"
$continue = Read-Host "Continue? (Y/N, default Y)"

if ($continue -and $continue.ToLower() -ne "y" -and $continue -ne "") {
    Write-Status "Cancelled" "Warning"
    exit 0
}

# Start building
$totalStartTime = Get-Date
$successCount = 0

foreach ($img in $selectedImages) {
    if (Build-Image -Name $img.Name -Tag $img.LocalTag -Dockerfile $img.Dockerfile) {
        # Tag
        docker tag $img.LocalTag $img.RemoteTag
        Write-Status "Tagged: $img.RemoteTag" "Info"
        
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
Write-Status "Success: $successCount / $($selectedImages.Count)" "Success"
Write-Status "Duration: $($totalDuration.ToString('F1')) seconds" "Info"
Write-Host ""

if ($successCount -eq $selectedImages.Count) {
    Write-Status "All images successfully pushed to: $registry/$project" "Success"
}

Read-Host "Press Enter to exit"
