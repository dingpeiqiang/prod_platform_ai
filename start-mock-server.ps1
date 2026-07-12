#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$script:BackendDir = Join-Path $ProjectRoot 'backend'
$script:MockServerPort = 6174

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
    Write-Status 'Checking Python availability...'
    try {
        $pythonVersion = python --version 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'Python not found'
        }
        Write-Success "Python: $pythonVersion"
        return $true
    }
    catch {
        Write-Error 'Python not found. Please install Python and add to PATH.'
        Read-Host 'Press Enter to exit'
        exit 1
    }
}

function Clear-Port {
    param([int]$Port)
    $processes = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
                 Where-Object { $_.State -eq 'Listen' } |
                 Select-Object -ExpandProperty OwningProcess -Unique
    
    if ($processes) {
        foreach ($pid in $processes) {
            Write-Warning "Port $Port is already in use (PID: $pid)"
            Write-Status "Terminating process $pid..."
            try {
                Stop-Process -Id $pid -Force -ErrorAction Stop
                Start-Sleep -Seconds 1
            }
            catch {
                Write-Error "Failed to terminate process $pid"
                Read-Host 'Press Enter to exit'
                exit 1
            }
        }
    }
}

function Main {
    Write-Status '============================================' -Color Cyan
    Write-Status '    Mock API Server Starter' -Color Cyan
    Write-Status '============================================' -Color Cyan
    Write-Host ''
    Write-Status 'Usage:'
    Write-Status '  .\start-mock-server.ps1 [port]'
    Write-Status "  Default port: $MockServerPort"
    Write-Host ''
    
    if ($args.Count -gt 0 -and $args[0] -match '^\d+$') {
        $script:MockServerPort = [int]$args[0]
    }
    
    Test-PythonAvailability
    Write-Host ''
    
    Clear-Port -Port $MockServerPort
    
    Write-Success "Starting Mock API Server..."
    Write-Status "Port: $MockServerPort"
    Write-Status "URL: http://localhost:$MockServerPort/mock"
    Write-Status 'Press Ctrl+C to stop'
    Write-Host ''
    
    Set-Location $BackendDir
    $env:MOCK_SERVER_PORT = $MockServerPort.ToString()
    python mock_server.py
}

Main @args