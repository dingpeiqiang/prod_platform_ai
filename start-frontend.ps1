#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$script:FrontendDir = Join-Path $ProjectRoot 'frontend'

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

function Test-NodeAvailability {
    Write-Status '[1/3] Checking Node.js environment...'
    try {
        $nodeVersion = node --version 2>&1
        if ($LASTEXITCODE -ne 0) {
            throw 'Node.js not found'
        }
        Write-Success "Node.js: $nodeVersion"
        return $true
    }
    catch {
        Write-Error 'Node.js not found. Please install Node.js 16+'
        Read-Host 'Press Enter to exit'
        exit 1
    }
}

function Initialize-NpmDependencies {
    Write-Status '[2/3] Checking npm dependencies...'
    $nodeModulesPath = Join-Path $FrontendDir 'node_modules'
    
    if (-not (Test-Path $nodeModulesPath)) {
        Write-Status '[INFO] First run, installing dependencies...'
        npm install
    }
    else {
        Write-Success 'Dependencies already exist'
    }
}

function Test-BackendHealth {
    Write-Status '[3/3] Checking backend service...'
    
    $healthUrls = @(
        'http://localhost:6173/api/v1/health',
        'http://localhost:6174/api/v1/health'
    )
    
    foreach ($url in $healthUrls) {
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Success "Backend service running ($url)"
                return
            }
        }
        catch {
            continue
        }
    }
    
    Write-Warning 'Backend service not detected'
    Write-Status '[INFO] Please ensure backend is started'
}

function Main {
    Write-Host ''
    Write-Status '╔═══════════════════════════════════════════════════════════╗' -Color Cyan
    Write-Status '║       AI驱动动态表单底层框架 - 前端启动脚本                 ║' -Color Cyan
    Write-Status '╚═══════════════════════════════════════════════════════════╝' -Color Cyan
    Write-Host ''
    
    Set-Location $FrontendDir
    
    Test-NodeAvailability
    Write-Host ''
    
    Initialize-NpmDependencies
    Write-Host ''
    
    Test-BackendHealth
    Write-Host ''
    
    Write-Host ''
    Write-Status '[READY] Starting Vite development server...'
    Write-Host ''
    Write-Status '    Frontend:  http://localhost:5173'
    Write-Status '    Backend API:   http://localhost:6173'
    Write-Host ''
    Write-Status '========================================' -Color Yellow
    Write-Host ''
    
    npm run dev
}

Main