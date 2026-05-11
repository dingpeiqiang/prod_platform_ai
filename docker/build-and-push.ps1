# ============================================================
# Build and push base images - 终极兼容 Harbor 1.10.4
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

function Build-And-Push-Image {
    param(
        [string]$Name,
        [string]$Tag,
        [string]$Dockerfile
    )

    Write-Host ""
    Write-Status "Building $Name" "Info"
    $context = Split-Path -Parent $Dockerfile
    $tmpTar = "temp-image.tar"

    try {
        # 1. 构建
        $env:DOCKER_BUILDKIT = "0"
        docker build --platform linux/amd64 -t $Tag $context
        if ($LASTEXITCODE -ne 0) { throw "Build failed" }
        Write-Status "[OK] $Name built" "Success"

        # ===================== 终极绝杀：强行转成老格式 =====================
        Write-Status "Converting to Docker V2 format..." "Info"
        docker save -o $tmpTar $Tag
        docker rmi -f $Tag
        docker load -i $tmpTar
        Remove-Item $tmpTar -Force
        # ===================================================================

        # 2. 推送（现在 100% 是老格式，Harbor 1.10.4 必过）
        Write-Status "Pushing $Tag..." "Info"
        docker push $Tag
        if ($LASTEXITCODE -ne 0) { throw "Push failed" }

        Write-Status "[OK] Pushed successfully" "Success"
        return $true
    }
    catch {
        Write-Status "[ERROR] $Name failed: $_" "Error"
        return $false
    }
}

# Main
Write-Status "========================================" "Header"
Write-Status "Build and Push Base Images" "Header"
Write-Status "========================================" "Header"

# Login
$password | docker login $registry -u $username --password-stdin
Write-Status "[OK] Login successful" "Success"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDir
Set-Location $projectRoot

# Images
$images = @(
    [PSCustomObject]@{ Name = "Backend Builder"; Tag = "$registry/$project/ai-form-backend-builder:latest"; Dockerfile = "docker/base-images/backend-builder/Dockerfile" },
    [PSCustomObject]@{ Name = "Backend Runtime"; Tag = "$registry/$project/ai-form-backend-runtime:latest"; Dockerfile = "docker/base-images/backend-runtime/Dockerfile" },
    [PSCustomObject]@{ Name = "Frontend Builder"; Tag = "$registry/$project/ai-form-frontend-builder:latest"; Dockerfile = "docker/base-images/frontend-builder/Dockerfile" }
)

$successCount = 0
foreach ($img in $images) {
    if (Build-And-Push-Image -Name $img.Name -Tag $img.Tag -Dockerfile $img.Dockerfile) {
        $successCount++
    }
}

Write-Status "Success: $successCount / $($images.Count)" "Success"