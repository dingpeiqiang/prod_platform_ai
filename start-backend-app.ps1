#Requires -Version 5.1
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$script:BackendAppDir = Join-Path $ProjectRoot 'backend-app'
$script:Port = 6174
$script:MavenSettings = Join-Path $BackendAppDir '.mvn\local-settings.xml'
# Prefer project JDK; override with env JAVA_HOME if already set to a valid path
$script:DefaultJdkHome = 'D:\APP\WORK\jdk-17'

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

function Write-Err {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[WARNING] $Message" -ForegroundColor Yellow
}

function Initialize-JavaHome {
    $jdkHome = $env:JAVA_HOME
    if (-not $jdkHome -or -not (Test-Path (Join-Path $jdkHome 'bin\java.exe'))) {
        $jdkHome = $DefaultJdkHome
    }
    if (-not (Test-Path (Join-Path $jdkHome 'bin\java.exe'))) {
        Write-Err "JDK not found: $jdkHome"
        Write-Status 'Set JAVA_HOME or install JDK 17 at D:\APP\WORK\jdk-17'
        Read-Host 'Press Enter to exit'
        exit 1
    }
    $env:JAVA_HOME = $jdkHome
    $env:Path = "$(Join-Path $jdkHome 'bin');$env:Path"
    Write-Success "JAVA_HOME=$jdkHome"
}

function Get-CommandOutput {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [string[]]$ArgumentList = @()
    )
    # Avoid PowerShell treating native stderr as terminating errors (java -version writes to stderr)
    $argString = ($ArgumentList | ForEach-Object {
        if ($_ -match '\s') { '"{0}"' -f $_ } else { $_ }
    }) -join ' '
    $quotedExe = '"{0}"' -f $FilePath
    $raw = cmd /c "$quotedExe $argString 2>&1"
    if ($null -eq $raw) {
        return ''
    }
    if ($raw -is [System.Array]) {
        return ($raw -join [Environment]::NewLine)
    }
    return [string]$raw
}

function Test-JavaAvailability {
    Write-Status '[1/3] Checking Java / Maven...'
    Initialize-JavaHome

    $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
    $javaOut = Get-CommandOutput -FilePath $javaExe -ArgumentList @('-version')
    if (-not ($javaOut -match 'version\s+"?\d+')) {
        Write-Err "Java not runnable: $javaExe"
        Write-Status $javaOut
        Read-Host 'Press Enter to exit'
        exit 1
    }
    Write-Success ("Java: " + (($javaOut -split "`r?`n")[0].Trim()))

    $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
    if (-not $mvnCmd) {
        Write-Err 'Maven not found. Please install Apache Maven 3.6+'
        Read-Host 'Press Enter to exit'
        exit 1
    }
    $mvnOut = Get-CommandOutput -FilePath $mvnCmd.Source -ArgumentList @('-version')
    $mvnFirst = (($mvnOut -split "`r?`n") | Where-Object { $_ -match 'Apache Maven' } | Select-Object -First 1)
    if (-not $mvnFirst) {
        Write-Err 'Maven not runnable'
        Write-Status $mvnOut
        Read-Host 'Press Enter to exit'
        exit 1
    }
    Write-Success "Maven: $mvnFirst"
}

function Clear-Port {
    param([int]$Port)
    Write-Status "[2/3] Checking port $Port availability..."

    $processes = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
                 Where-Object { $_.State -eq 'Listen' } |
                 Select-Object -ExpandProperty OwningProcess -Unique

    if ($processes) {
        Write-Warn "Port $Port is in use, cleaning up..."
        foreach ($processId in $processes) {
            Write-Status "  Killing process PID: $processId"
            try {
                Stop-Process -Id $processId -Force -ErrorAction Stop
                Write-Success "  Process $processId terminated"
            }
            catch {
                Write-Err "  Failed to terminate process $processId, please handle manually"
                Read-Host 'Press Enter to exit'
                exit 1
            }
        }
        Start-Sleep -Seconds 1
    }

    Write-Success "Using port: $Port"
}

function Get-MavenArgs {
    $mvnArgs = @()
    if (Test-Path $MavenSettings) {
        $mvnArgs += @('-s', $MavenSettings)
        Write-Status "[INFO] Using Maven settings: .mvn\local-settings.xml"
    }
    $mvnArgs += @('spring-boot:run', "-Dspring-boot.run.arguments=--server.port=$Port")
    return $mvnArgs
}

function Main {
    Write-Host ''
    Write-Status '============================================================' -Color Cyan
    Write-Status '     AI Platform - Spring Boot Backend (backend-app)' -Color Cyan
    Write-Status '============================================================' -Color Cyan
    Write-Host ''

    if (-not (Test-Path $BackendAppDir)) {
        Write-Err "Directory not found: $BackendAppDir"
        Read-Host 'Press Enter to exit'
        exit 1
    }

    Set-Location $BackendAppDir

    Test-JavaAvailability
    Write-Host ''

    Clear-Port -Port $Port
    Write-Host ''

    Write-Status '[3/3] Starting Spring Boot...'
    Write-Host ''

    $llmEnabled = $env:LLM_ENABLED
    if (-not $llmEnabled) {
        $llmEnabled = 'false'
    }

    Write-Status '============================================================' -Color Yellow
    Write-Status "    API:       http://localhost:$Port"
    Write-Status "    Health:    http://localhost:$Port/api/v1/health"
    Write-Status "    Ontology:  http://localhost:$Port/api/v1/ontology-mvp/graph"
    Write-Status "    LLM:       enabled=$llmEnabled (set LLM_ENABLED=true to call model)"
    Write-Status '============================================================' -Color Yellow
    Write-Host ''
    Write-Status '    Optional env: LLM_ENABLED / LLM_API_KEY / LLM_BASE_URL / LLM_MODEL'
    Write-Host ''

    $mvnArgs = Get-MavenArgs
    & mvn @mvnArgs
}

Main @args
