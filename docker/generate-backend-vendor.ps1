<#
.SYNOPSIS
Backend Docker offline package generator

.DESCRIPTION
Download Python dependencies to backend/vendor for offline Docker build
#>

param()

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Backend Docker Offline Package Generator" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host

$scriptDir = $PSScriptRoot
$vendorDir = Join-Path (Split-Path $scriptDir -Parent) "backend/vendor"
$requirementsPath = Join-Path (Split-Path $scriptDir -Parent) "backend/requirements.txt"

if (-not (Test-Path $vendorDir)) {
    New-Item -ItemType Directory -Path $vendorDir | Out-Null
}

if (-not (Test-Path $requirementsPath)) {
    Write-Host "Error: requirements.txt not found" -ForegroundColor Red
    Write-Host "Expected: $requirementsPath" -ForegroundColor Red
    Read-Host "Press Enter to exit..."
    exit 1
}

try {
    $pythonVersion = python --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Python not found"
    }
    Write-Host "Python OK: $pythonVersion" -ForegroundColor Green
}
catch {
    Write-Host "Error: Python not installed" -ForegroundColor Red
    Read-Host "Press Enter to exit..."
    exit 1
}

Write-Host

try {
    $dependencies = @()
    Get-Content $requirementsPath -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#')) {
            $dependencies += $line
        }
    }
    Write-Host "Found $($dependencies.Count) packages" -ForegroundColor Green
}
catch {
    Write-Host "Error reading requirements.txt" -ForegroundColor Red
    Read-Host "Press Enter to exit..."
    exit 1
}

Write-Host

Write-Host "Cleaning old packages..." -ForegroundColor Yellow
Get-ChildItem -Path $vendorDir -Filter "*.whl" | Remove-Item -Force
Get-ChildItem -Path $vendorDir -Filter "*.tar.gz" | Remove-Item -Force
Get-ChildItem -Path $vendorDir -Filter "*.zip" | Remove-Item -Force

Write-Host "Downloading packages..." -ForegroundColor Yellow
Write-Host "This may take time, please wait..." -ForegroundColor Yellow
Write-Host

$successCount = 0
$failCount = 0

foreach ($dep in $dependencies) {
    Write-Host "Downloading: $dep" -NoNewline
    try {
        python -m pip download "$dep" -d "$vendorDir" --platform manylinux_x86_64 --python-version 310 --implementation cp --abi cp310 --only-binary=:all: 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Host " [OK]" -ForegroundColor Green
            $successCount++
        }
        else {
            python -m pip download "$dep" -d "$vendorDir" 2>&1 | Out-Null
            if ($LASTEXITCODE -eq 0) {
                Write-Host " [OK (fallback)]" -ForegroundColor Yellow
                $successCount++
            }
            else {
                Write-Host " [FAIL]" -ForegroundColor Red
                $failCount++
            }
        }
    }
    catch {
        Write-Host " [FAIL: $_]" -ForegroundColor Red
        $failCount++
    }
}

Write-Host

$listContent = "# Backend offline packages list`n"
$listContent += "# Generated: $(Get-Date -Format "yyyy-MM-ddTHH:mm:ss.fffZ")`n"
$listContent += "# Count: $($dependencies.Count)`n`n"
$listContent += $dependencies -join "`n"

$listPath = Join-Path $vendorDir "package-list.txt"
Set-Content -Path $listPath -Value $listContent -Encoding UTF8

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Completed: $successCount success, $failCount failed" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$pkgFiles = Get-ChildItem -Path $vendorDir -Include "*.whl", "*.tar.gz", "*.zip" | Measure-Object
Write-Host "`nVendor dir has $($pkgFiles.Count) package files" -ForegroundColor Green

if ($failCount -gt 0) {
    Write-Host "`nWarning: Some packages failed to download" -ForegroundColor Yellow
    Read-Host "Press Enter to exit..."
    exit 1
}
else {
    Write-Host "`nAll packages downloaded successfully!" -ForegroundColor Green
    Read-Host "Press Enter to exit..."
    exit 0
}
