# ============================================================
# AI Form - Build and Export Script (Optimized with Selection)
# ============================================================

$ErrorActionPreference = "Stop"

# Colors
$Colors = @{
    Success = "Green"
    Warning = "Yellow"
    Error = "Red"
    Info = "Cyan"
    Header = "DarkCyan"
    Prompt = "Magenta"
}

Write-Host ""
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host "   AI Form - Build and Export" -ForegroundColor $Colors.Header
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host ""

# Switch to script directory
$ScriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptPath

# Helper function for status
function Write-Status {
    param([string]$Message, [string]$Type = "Info")
    $color = $Colors[$Type]
    Write-Host $Message -ForegroundColor $color
}

# Interactive selection menu
Write-Status "What would you like to build?" "Prompt"
Write-Host ""
Write-Host "  [1] Backend only" -ForegroundColor Gray
Write-Host "  [2] Frontend only" -ForegroundColor Gray
Write-Host "  [3] Both (default)" -ForegroundColor Gray
Write-Host ""
$choice = Read-Host "Please enter your choice (1/2/3)"

# Default to 3 if empty
if ([string]::IsNullOrWhiteSpace($choice)) {
    $choice = "3"
}

# Determine what to build
$buildBackend = $false
$buildFrontend = $false

switch ($choice) {
    "1" {
        $buildBackend = $true
        Write-Status "Selected: Backend only" "Info"
    }
    "2" {
        $buildFrontend = $true
        Write-Status "Selected: Frontend only" "Info"
    }
    "3" {
        $buildBackend = $true
        $buildFrontend = $true
        Write-Status "Selected: Both backend and frontend" "Info"
    }
    default {
        Write-Status "Invalid choice, building both by default." "Warning"
        $buildBackend = $true
        $buildFrontend = $true
    }
}

Write-Host ""

# Check if Docker is running
Write-Status "[1/6] Checking Docker status..." "Info"
try {
    docker info 2>&1 | Out-Null
    Write-Status "[OK] Docker is running" "Success"
} catch {
    Write-Status "[ERROR] Docker is not running. Please start Docker Desktop first." "Error"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""

# Pull base images if needed
Write-Status "[2/6] Checking base images..." "Info"
$images = docker images --format "{{.Repository}}:{{.Tag}}"

if ($buildBackend) {
    if (-not ($images -match "python:3.10-slim")) {
        Write-Status "Pulling python:3.10-slim..." "Info"
        docker pull python:3.10-slim
    } else {
        Write-Status "[SKIP] python:3.10-slim already exists" "Success"
    }
}

if ($buildFrontend) {
    if (-not ($images -match "node:20-alpine")) {
        Write-Status "Pulling node:20-alpine..." "Info"
        docker pull node:20-alpine
    } else {
        Write-Status "[SKIP] node:20-alpine already exists" "Success"
    }
    if (-not ($images -match "nginx:alpine")) {
        Write-Status "Pulling nginx:alpine..." "Info"
        docker pull nginx:alpine
    } else {
        Write-Status "[SKIP] nginx:alpine already exists" "Success"
    }
}

Write-Host ""

# Build backend
if ($buildBackend) {
    Write-Status "[3/6] Building backend image..." "Info"
    docker build -f "../backend/Dockerfile" -t prod-platform-ai-backend:latest "../"
    if ($LASTEXITCODE -ne 0) {
        Write-Status "[ERROR] Failed to build backend image!" "Error"
        Read-Host "Press Enter to exit"
        exit 1
    }
    Write-Status "[OK] Backend image built" "Success"
} else {
    Write-Status "[SKIP] Backend build skipped" "Warning"
}

Write-Host ""

# Build frontend
if ($buildFrontend) {
    Write-Status "[4/6] Building frontend image..." "Info"
    docker build -f "../frontend/Dockerfile" -t prod-platform-ai-frontend:latest "../"
    if ($LASTEXITCODE -ne 0) {
        Write-Status "[ERROR] Failed to build frontend image!" "Error"
        Read-Host "Press Enter to exit"
        exit 1
    }
    Write-Status "[OK] Frontend image built" "Success"
} else {
    Write-Status "[SKIP] Frontend build skipped" "Warning"
}

Write-Host ""
Write-Status "[5/6] Exporting image files..." "Info"

# Export with progress
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$files = @()

if ($buildBackend) {
    Write-Status "Exporting backend image..." "Info"
    docker save prod-platform-ai-backend:latest -o prod-platform-ai-backend.tar
    $files += "prod-platform-ai-backend.tar"
}

if ($buildFrontend) {
    Write-Status "Exporting frontend image..." "Info"
    docker save prod-platform-ai-frontend:latest -o prod-platform-ai-frontend.tar
    $files += "prod-platform-ai-frontend.tar"
}

$sw.Stop()
Write-Status "[OK] Export completed in $($sw.Elapsed.TotalSeconds.ToString('F1'))s" "Success"

Write-Host ""
Write-Status "[6/6] Verifying files..." "Info"
$AllOk = $true

$totalSize = 0
foreach ($file in $files) {
    if (Test-Path $file) {
        $size = (Get-Item $file).Length
        $totalSize += $size
        $sizeMB = [math]::Round($size / 1MB, 2)
        Write-Status "[OK] $file ($sizeMB MB)" "Success"
    } else {
        Write-Status "[ERROR] $file not found!" "Error"
        $AllOk = $false
    }
}

$totalMB = [math]::Round($totalSize / 1MB, 2)
Write-Status "[OK] Total size: $totalMB MB" "Info"

Write-Host ""

if (-not $AllOk) {
    Write-Status "Some files failed to export. Please check error messages above." "Error"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host "   Build and Export Complete!" -ForegroundColor $Colors.Success
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host ""
Write-Status "Image files are saved in docker directory" "Info"
Write-Host ""
Write-Status "Please transfer these files to internal network server:" "Info"
foreach ($file in $files) {
    Write-Host "  - docker/$file" -ForegroundColor Gray
}
Write-Host "  - docker/docker-compose.offline.yml" -ForegroundColor Gray
Write-Host "  - docker/deploy-offline.ps1" -ForegroundColor Gray
Write-Host ""
Read-Host "Press Enter to exit"
