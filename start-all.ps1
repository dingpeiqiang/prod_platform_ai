#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$script:BackendPort = 6174
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

function Get-ListeningPids {
    param([int]$Port)
    $pids = @()
    try {
        $pids = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique |
            Where-Object { $_ -and $_ -gt 0 })
    }
    catch { }

    if (-not $pids -or $pids.Count -eq 0) {
        $netstat = netstat -ano 2>$null | Select-String ":$Port\s+.*LISTENING\s+(\d+)$"
        foreach ($m in $netstat) {
            $pidText = $m.Matches[0].Groups[1].Value
            if ($pidText -match '^\d+$' -and [int]$pidText -gt 0) {
                $pids += [int]$pidText
            }
        }
        $pids = @($pids | Select-Object -Unique)
    }
    return $pids
}

function Clear-Port {
    param(
        [int]$Port,
        [int]$MaxRetries = 5
    )
    Write-Status "[Checking] Port $Port availability..."

    for ($attempt = 1; $attempt -le $MaxRetries; $attempt++) {
        $processes = @(Get-ListeningPids -Port $Port)
        if (-not $processes -or $processes.Count -eq 0) {
            Write-Success "Port $Port available"
            return
        }

        Write-Warning "Port $Port is in use (attempt $attempt/$MaxRetries), cleaning up..."
        foreach ($processId in $processes) {
            if ($processId -eq $PID) { continue }
            Write-Status "  Killing process PID: $processId"
            $killed = $false
            try {
                Stop-Process -Id $processId -Force -ErrorAction Stop
                $killed = $true
            }
            catch {
                $null = cmd /c "taskkill /F /T /PID $processId >nul 2>&1"
                if ($LASTEXITCODE -eq 0) { $killed = $true }
            }

            if ($killed) {
                Write-Success "  Process $processId terminated"
            }
            else {
                Write-Error "  Failed to terminate process $processId"
            }
        }

        Start-Sleep -Seconds 1
    }

    $stillBusy = @(Get-ListeningPids -Port $Port)
    if ($stillBusy -and $stillBusy.Count -gt 0) {
        Write-Error "Port $Port still occupied by PID(s): $($stillBusy -join ', ')"
        Write-Host ''
        Write-Status 'Please close the process occupying this port manually:'
        Write-Status "  netstat -ano | findstr :$Port"
        Write-Status "  taskkill /F /T /PID <ProcessID>"
        Write-Host ''
        Read-Host 'Press Enter to exit'
        exit 1
    }

    Write-Success "Port $Port available"
}

function Main {
    Write-Host ''
    Write-Status '=============================================================' -Color Cyan
    Write-Status '' -Color Cyan
    Write-Status '  AI Platform - One-click Startup (Spring Boot + Frontend)' -Color Cyan
    Write-Status '' -Color Cyan
    Write-Status '=============================================================' -Color Cyan
    Write-Host ''
    
    Clear-Port -Port $BackendPort
    Write-Host ''
    Clear-Port -Port $FrontendPort
    Write-Host ''
    
    $backendScript = Join-Path $ProjectRoot 'start-backend-app.ps1'
    $frontendScript = Join-Path $ProjectRoot 'start-frontend.ps1'
    
    Write-Status "[Starting] Spring Boot backend (port $BackendPort)..."
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
    Write-Status "    Backend API:  http://localhost:$BackendPort  (Spring Boot)"
    Write-Status "    Frontend:     http://localhost:$FrontendPort"
    Write-Status "    Health:       http://localhost:$BackendPort/api/v1/health"
    Write-Status "    Ontology:     http://localhost:$BackendPort/api/v1/ontology-mvp/graph"
    Write-Status "    Chat v2:      http://localhost:$BackendPort/api/v2/chat/sessions"
    Write-Host ''
    Write-Status '    Waiting 5 seconds for services to fully start...'
    Write-Status '============================================' -Color Yellow
    Write-Host ''
    
    Start-Sleep -Seconds 5
    
    Write-Success 'Ready to use!'
    Read-Host 'Press Enter to exit'
}

Main
