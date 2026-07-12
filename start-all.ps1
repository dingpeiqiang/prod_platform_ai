#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$script:BackendPort = 6173
$script:FrontendPort = 5173

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

function Test-PortAvailability {
    param([int]$Port)
    Write-Status "[Checking] Port $Port availability..."
    
    $processes = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
                 Where-Object { $_.State -eq 'Listen' } |
                 Select-Object -ExpandProperty OwningProcess -Unique
    
    if ($processes) {
        Write-Error "Port $Port is already in use!"
        Write-Host ''
        Write-Status 'Please close the process occupying this port first:'
        Write-Status "  netstat -ano | findstr :$Port"
        Write-Status "  taskkill /F /PID <ProcessID>"
        Write-Host ''
        Read-Host 'Press Enter to exit'
        exit 1
    }
    
    Write-Success "Port $Port available"
}

function Main {
    Write-Host ''
    Write-Status '╔═══════════════════════════════════════════════════════════╗' -Color Cyan
    Write-Status '║                                                           ║' -Color Cyan
    Write-Status '║       AI驱动动态表单底层框架 - 一键启动脚本                 ║' -Color Cyan
    Write-Status '║                                                           ║' -Color Cyan
    Write-Status '╚═══════════════════════════════════════════════════════════╝' -Color Cyan
    Write-Host ''
    
    Test-PortAvailability -Port $BackendPort
    Write-Host ''
    
    $backendScript = Join-Path $ProjectRoot 'start-backend.ps1'
    $frontendScript = Join-Path $ProjectRoot 'start-frontend.ps1'
    
    Write-Status "[Starting] Backend service (port $BackendPort)..."
    $backendArgs = @(
        '-ExecutionPolicy', 'Bypass',
        '-File', "`"$backendScript`""
    )
    Start-Process -FilePath 'powershell.exe' -ArgumentList $backendArgs -WindowStyle Normal -WorkingDirectory $ProjectRoot
    
    Start-Sleep -Seconds 2
    
    Write-Status "[Starting] Frontend service (port $FrontendPort)..."
    $frontendArgs = @(
        '-ExecutionPolicy', 'Bypass',
        '-File', "`"$frontendScript`""
    )
    Start-Process -FilePath 'powershell.exe' -ArgumentList $frontendArgs -WindowStyle Normal -WorkingDirectory $ProjectRoot
    
    Write-Host ''
    Write-Success 'Services starting...'
    Write-Host ''
    Write-Status '============================================' -Color Yellow
    Write-Host ''
    Write-Status "    Backend API:  http://localhost:$BackendPort"
    Write-Status "    Frontend: http://localhost:$FrontendPort"
    Write-Status "    API Docs: http://localhost:$BackendPort/docs"
    Write-Host ''
    Write-Status '    Waiting 5 seconds for services to fully start...'
    Write-Status '============================================' -Color Yellow
    Write-Host ''
    
    Start-Sleep -Seconds 5
    
    Write-Success 'Ready to use!'
    Read-Host 'Press Enter to exit'
}

Main