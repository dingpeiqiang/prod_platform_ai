# =============================================================
# 后端 Python 依赖离线打包脚本 (Windows PowerShell)
# 在有网络的机器上执行，将所有 pip 依赖下载到 backend/vendor/
# 执行后将 backend/vendor/ 目录提交到 git
# =============================================================
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$VendorDir = Join-Path $ProjectRoot "backend\vendor"
$Requirements = Join-Path $ProjectRoot "backend\requirements.txt"

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " 后端 Python 依赖离线打包" -ForegroundColor Cyan
Write-Host " 目标目录: $VendorDir" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# 清理旧包
if (Test-Path $VendorDir) {
    Write-Host "[INFO] 清理旧的 vendor 目录..."
    Remove-Item -Recurse -Force $VendorDir
}
New-Item -ItemType Directory -Force -Path $VendorDir | Out-Null

# 检查 requirements.txt
if (-not (Test-Path $Requirements)) {
    Write-Host "[ERROR] 找不到 requirements.txt: $Requirements" -ForegroundColor Red
    exit 1
}

Write-Host "[INFO] 开始下载依赖包（目标平台: linux/amd64, Python 3.10）..." -ForegroundColor Yellow
Write-Host "[INFO] 使用 requirements.txt: $Requirements"
Write-Host ""

# 第一步：尝试下载二进制 wheel（与容器平台一致）
Write-Host "[STEP 1/3] 下载 manylinux wheel 包..." -ForegroundColor Green
pip download `
    --dest $VendorDir `
    --platform manylinux2014_x86_64 `
    --python-version 3.10 `
    --implementation cp `
    --abi cp310 `
    --only-binary=:all: `
    -r $Requirements 2>$null
# 忽略错误，下一步补充 sdist

# 第二步：专门下载 uvicorn[standard] 的 Linux 依赖（如 uvloop, httptools）
Write-Host "[STEP 2/3] 补充下载 uvicorn[standard] 的 Linux 依赖..." -ForegroundColor Green
pip download `
    --dest $VendorDir `
    --platform manylinux2014_x86_64 `
    --python-version 3.10 `
    --implementation cp `
    --abi cp310 `
    --only-binary=:all: `
    "uvicorn[standard]" 2>$null

# 第三步：补充下载（源码包 / 当前平台包），用于构建时编译
Write-Host "[STEP 3/3] 补充下载 sdist 和未覆盖的包..." -ForegroundColor Green
pip download `
    --dest $VendorDir `
    -r $Requirements

# 统计结果
$PackCount = (Get-ChildItem $VendorDir).Count
$SizeBytes = (Get-ChildItem -Recurse $VendorDir | Measure-Object -Property Length -Sum).Sum
$SizeMB = [math]::Round($SizeBytes / 1MB, 2)

Write-Host ""
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " ✅ 打包完成！" -ForegroundColor Green
Write-Host " 共 $PackCount 个包，总大小: ${SizeMB} MB"
Write-Host " 目录: $VendorDir"
Write-Host ""
Write-Host " 后续步骤："
Write-Host "   git add backend/vendor/"
Write-Host '   git commit -m "chore: add offline python dependencies"'
Write-Host "==================================================" -ForegroundColor Cyan
