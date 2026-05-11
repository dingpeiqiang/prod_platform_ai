# =============================================================
# 前端 Node 依赖离线打包脚本 (Windows PowerShell)
# 在有网络的机器上执行，将所有 npm 依赖打包到 frontend/vendor/
# 执行后将 frontend/vendor/ 目录提交到 git
# =============================================================
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$FrontendDir = Join-Path $ProjectRoot "frontend"
$VendorDir = Join-Path $FrontendDir "vendor"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " 前端 Node 依赖离线打包" -ForegroundColor Cyan
Write-Host " 目标目录: $VendorDir" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 清理旧包
if (Test-Path $VendorDir) {
    Write-Host "[INFO] 清理旧的 vendor 目录..."
    Remove-Item -Recurse -Force $VendorDir
}
New-Item -ItemType Directory -Force -Path $VendorDir | Out-Null

# 进入前端目录
Set-Location $FrontendDir

# 确保 node_modules 存在
$NodeModules = Join-Path $FrontendDir "node_modules"
if (-not (Test-Path $NodeModules)) {
    Write-Host "[INFO] 安装依赖（解析依赖树需要）..."
    npm ci
}

Write-Host "[INFO] 从 node_modules 打包所有模块..." -ForegroundColor Yellow
Write-Host ""

$PackedCount = 0
$FailedCount = 0

# 遍历 node_modules 顶层目录
$TopDirs = Get-ChildItem -Directory $NodeModules | Where-Object { $_.Name -notmatch '^\.' }

foreach ($Dir in $TopDirs) {
    $DirName = $Dir.Name

    if ($DirName.StartsWith("@")) {
        # Scoped package：进入下一层
        $ScopedDirs = Get-ChildItem -Directory $Dir.FullName
        foreach ($Scoped in $ScopedDirs) {
            $Result = npm pack $Scoped.FullName --pack-destination $VendorDir 2>$null
            if ($LASTEXITCODE -eq 0) {
                Write-Host "[OK] $DirName/$($Scoped.Name)" -ForegroundColor Green
                $PackedCount++
            } else {
                Write-Host "[SKIP] $DirName/$($Scoped.Name)" -ForegroundColor DarkGray
                $FailedCount++
            }
        }
    } else {
        $Result = npm pack $Dir.FullName --pack-destination $VendorDir 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "[OK] $DirName" -ForegroundColor Green
            $PackedCount++
        } else {
            Write-Host "[SKIP] $DirName" -ForegroundColor DarkGray
            $FailedCount++
        }
    }
}

# 统计
$TgzFiles = Get-ChildItem $VendorDir -Filter "*.tgz"
$SizeBytes = ($TgzFiles | Measure-Object -Property Length -Sum).Sum
$SizeMB = [math]::Round($SizeBytes / 1MB, 2)

Write-Host ""
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " ✅ 前端依赖打包完成！" -ForegroundColor Green
Write-Host " 成功: $PackedCount 个包，跳过: $FailedCount 个"
Write-Host " 总大小: ${SizeMB} MB"
Write-Host " 目录: $VendorDir"
Write-Host ""
Write-Host " 后续步骤："
Write-Host "   git add frontend/vendor/"
Write-Host '   git commit -m "chore: add offline npm dependencies"'
Write-Host "==================================================" -ForegroundColor Cyan
