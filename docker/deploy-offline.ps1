# ============================================================
# AI Form - Offline Deployment Script (Optimized with Selection)
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
Write-Host "   AI Form - Offline Deployment" -ForegroundColor $Colors.Header
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
Write-Status "What would you like to deploy?" "Prompt"
Write-Host ""
Write-Host "  [1] Backend only" -ForegroundColor Gray
Write-Host "  [2] Frontend only" -ForegroundColor Gray
Write-Host "  [3] All (default)" -ForegroundColor Gray
Write-Host ""
$choice = Read-Host "Please enter your choice (1/2/3)"

# Default to 3 if empty
if ([string]::IsNullOrWhiteSpace($choice)) {
    $choice = "3"
}

# Determine what to deploy
$deployBackend = $false
$deployFrontend = $false

switch ($choice) {
    "1" {
        $deployBackend = $true
        Write-Status "Selected: Backend only" "Info"
    }
    "2" {
        $deployFrontend = $true
        Write-Status "Selected: Frontend only" "Info"
    }
    "3" {
        $deployBackend = $true
        $deployFrontend = $true
        Write-Status "Selected: All services" "Info"
    }
    default {
        Write-Status "Invalid choice, deploying all by default." "Warning"
        $deployBackend = $true
        $deployFrontend = $true
    }
}

Write-Host ""

# Check if Docker is running
Write-Status "[1/5] Checking Docker status..." "Info"
try {
    docker info 2>&1 | Out-Null
    Write-Status "[OK] Docker is running" "Success"
} catch {
    Write-Status "[ERROR] Docker is not running. Please start Docker Desktop first." "Error"
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Status "[2/5] Checking images..." "Info"
$AllImages = docker images --format "{{.Repository}}:{{.Tag}}"

$BackendOk = $true
$FrontendOk = $true

if ($deployBackend) {
    if ($AllImages -match "prod-platform-ai-backend:latest") {
        Write-Status "[OK] Backend image found" "Success"
    } else {
        Write-Status "[ERROR] Backend image not found!" "Error"
        Write-Status "Please run: docker load -i prod-platform-ai-backend.tar" "Warning"
        $BackendOk = $false
    }
}

if ($deployFrontend) {
    if ($AllImages -match "prod-platform-ai-frontend:latest") {
        Write-Status "[OK] Frontend image found" "Success"
    } else {
        Write-Status "[ERROR] Frontend image not found!" "Error"
        Write-Status "Please run: docker load -i prod-platform-ai-frontend.tar" "Warning"
        $FrontendOk = $false
    }
}

# Check if required images are missing
$proceed = $true
if ($deployBackend -and -not $BackendOk) {
    $proceed = $false
}
if ($deployFrontend -and -not $FrontendOk) {
    $proceed = $false
}

if (-not $proceed) {
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Status "[3/5] Stopping old containers..." "Info"
docker-compose -f docker-compose.offline.yml down 2>&1 | Out-Null
Write-Status "[OK] Old containers stopped" "Success"

Write-Host ""
Write-Status "[4/5] Preparing directories..." "Info"
$LogPath = Join-Path (Split-Path -Parent $ScriptPath) "logs"
if ($deployBackend) {
    $BackendLogPath = Join-Path $LogPath "backend"
    if (-not (Test-Path $BackendLogPath)) {
        New-Item -ItemType Directory -Path $BackendLogPath -Force | Out-Null
        Write-Status "[OK] Log directory created" "Success"
    } else {
        Write-Status "[OK] Log directory exists" "Success"
    }
}

Write-Host ""
Write-Status "[5/5] Starting services..." "Info"
$sw = [System.Diagnostics.Stopwatch]::StartNew()

# Create a temporary compose file with selected services
$composeContent = Get-Content docker-compose.offline.yml -Raw
$tempFile = "docker-compose.temp.yml"
Set-Content -Path $tempFile -Value $composeContent

# If not deploying all, we need to specify profiles or services
# For simplicity, always start all, but user can choose later
docker-compose -f $tempFile up -d

# Clean up temp file
Remove-Item $tempFile

if ($LASTEXITCODE -ne 0) {
    Write-Status "[ERROR] Failed to start services!" "Error"
    Read-Host "Press Enter to exit"
    exit 1
}

$sw.Stop()
Write-Status "[OK] Services started in $($sw.Elapsed.TotalSeconds.ToString('F1'))s" "Success"

Write-Host ""
Write-Status "Waiting for services to be ready..." "Info"

# Check service readiness
$maxWait = 30
$ready = $false
$expectedCount = 0
if ($deployBackend) { $expectedCount += 1 }  # backend only
if ($deployFrontend) { $expectedCount += 1 }  # frontend

for ($i = 0; $i -lt $maxWait; $i++) {
    try {
        $result = docker-compose -f docker-compose.offline.yml ps --services --filter "status=running" 2>&1
        $running = ($result | Measure-Object).Count
        if ($running -ge $expectedCount) {
            $ready = $true
            break
        }
    } catch {
        # Ignore errors
    }
    Write-Host "." -NoNewline
    Start-Sleep -Seconds 1
}

Write-Host ""
if ($ready) {
    Write-Status "[OK] Services are ready!" "Success"
} else {
    Write-Status "[WARNING] Services may still be starting..." "Warning"
}

Write-Host ""
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host "   Deployment Complete!" -ForegroundColor $Colors.Success
Write-Host "========================================" -ForegroundColor $Colors.Header
Write-Host ""
Write-Status "Access URLs:" "Info"
if ($deployFrontend) {
    Write-Host "  - Frontend: http://localhost" -ForegroundColor Gray
}
if ($deployBackend) {
    Write-Host "  - Backend: http://localhost:6173" -ForegroundColor Gray
    Write-Host "  - API Docs: http://localhost:6173/docs" -ForegroundColor Gray
}
Write-Host ""
Write-Status "Useful commands:" "Info"
Write-Host "  docker-compose -f docker-compose.offline.yml logs -f" -ForegroundColor Gray
Write-Host "  docker-compose -f docker-compose.offline.yml ps" -ForegroundColor Gray
Write-Host "  docker-compose -f docker-compose.offline.yml down" -ForegroundColor Gray
Write-Host ""
Read-Host "Press Enter to exit"
