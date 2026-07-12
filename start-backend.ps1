#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$script:BackendDir = Join-Path $ProjectRoot 'backend'
$script:Port = 6173

function Write-Status {
    param(
        [string]$Message,
        [string]$Color = 'Cyan'
    )
    Write-Host $Message -ForegroundColor $Color
}

function Write-Success {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

function Write-Warning {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Test-PythonAvailability {
    Write-Status '[1/4] Checking Python environment...'
    try {
        $pythonVersion = python --version 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'Python not found'
        }
        Write-Success "Python: $pythonVersion"
        return $true
    }
    catch {
        Write-Error 'Python not found. Please install Python 3.8+'
        Read-Host 'Press Enter to exit'
        exit 1
    }
}

function Initialize-VirtualEnvironment {
    Write-Status '[2/4] Checking/Creating virtual environment...'
    $venvPath = Join-Path $BackendDir 'venv'
    
    if (-not (Test-Path $venvPath)) {
        Write-Status '[INFO] First run, creating virtual environment...'
        python -m venv $venvPath
    }
    
    $activateScript = Join-Path $venvPath 'Scripts\Activate.ps1'
    . $activateScript
    
    pip install -r requirements.txt -q
    Write-Success 'Dependencies ready'
}

function Clear-Port {
    param([int]$Port)
    Write-Status "[3/4] Checking port $Port availability..."
    
    $processes = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
                 Where-Object { $_.State -eq 'Listen' } |
                 Select-Object -ExpandProperty OwningProcess -Unique
    
    if ($processes) {
        Write-Warning "Port $Port is in use, cleaning up..."
        foreach ($pid in $processes) {
            Write-Status "  Killing process PID: $pid"
            try {
                Stop-Process -Id $pid -Force -ErrorAction Stop
                Write-Success "  Process $pid terminated"
            }
            catch {
                Write-Error "  Failed to terminate process $pid, please handle manually"
                Read-Host 'Press Enter to exit'
                exit 1
            }
        }
        Start-Sleep -Seconds 1
    }
    
    Write-Success "Using port: $Port"
}

function Start-ProductionMode {
    Write-Status "`n[PROD] Starting production mode (default)..."
    Write-Status "    - For development mode: .\start-backend.ps1 dev"
    Write-Status ''
    
    Write-Status '============================================================' -Color Yellow
    Write-Status "    API:       http://localhost:$Port"
    Write-Status "    Docs:      http://localhost:$Port/docs"
    Write-Status '    Mode:      Single process, no auto-reload'
    Write-Status '    Log:       Terminal + backend\app\logs\app.log'
    Write-Status '============================================================' -Color Yellow
    Write-Status ''
    
    python -m uvicorn app.main:app --host 0.0.0.0 --port $Port --log-level debug
}

function Start-DevelopmentMode {
    Write-Status ''
    
    Write-Status '============================================================' -Color Yellow
    Write-Status "    API:       http://localhost:$Port"
    Write-Status "    Docs:      http://localhost:$Port/docs"
    Write-Status '    Mode:      Auto-reload (restarts on code change)'
    Write-Status '    Log:       Check backend\app\logs\app.log'
    Write-Status '============================================================' -Color Yellow
    Write-Status ''
    
    $env:PYTHONUNBUFFERED = '1'
    python -m uvicorn app.main:app --reload --host 0.0.0.0 --port $Port --log-level debug
}

function Main {
    Write-Host ''
    Write-Status '============================================================' -Color Cyan
    Write-Status '          AI Platform - Backend Startup Script' -Color Cyan
    Write-Status '============================================================' -Color Cyan
    Write-Host ''
    
    Set-Location $BackendDir
    
    Test-PythonAvailability
    Write-Host ''
    
    Initialize-VirtualEnvironment
    Write-Host ''
    
    Clear-Port -Port $Port
    Write-Host ''
    
    Write-Status '[4/4] Checking startup parameters...'
    
    if ($args.Count -gt 0 -and $args[0] -in 'dev', '--dev', '-d') {
        Start-DevelopmentMode
    }
    else {
        Start-ProductionMode
    }
}

Main @args