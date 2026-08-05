# Prod Platform AI - 数据库一键部署（Windows PowerShell）
# 用法（在项目根目录）：
#   .\sql\deploy.ps1
#   .\sql\deploy.ps1 -DbHost 127.0.0.1 -Port 3306 -RootUser root -RootPassword 'xxx'
# 仅执行初始化数据：
#   .\sql\deploy.ps1 -SkipSchema -SkipCreateDb

param(
    [string]$DbHost = "127.0.0.1",
    [int]$Port = 3306,
    [string]$RootUser = "root",
    [string]$RootPassword = "",
    [string]$AppUser = "prodplatformai",
    [string]$AppPassword = "prodplatformai@134",
    [string]$Database = "prodplatformai",
    [switch]$SkipCreateDb,
    [switch]$SkipSchema,
    [switch]$SkipInit
)

$ErrorActionPreference = "Stop"
$SqlDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function Invoke-MysqlFile {
    param(
        [string]$User,
        [string]$Password,
        [string]$File,
        [string]$Db = ""
    )
    $mysqlArgs = @(
        "-h$DbHost",
        "-P$Port",
        "-u$User",
        "--default-character-set=utf8mb4"
    )
    if ($Password) { $mysqlArgs += "-p$Password" }
    if ($Db) { $mysqlArgs += $Db }
    Write-Host ">> mysql $($File | Split-Path -Leaf)" -ForegroundColor Cyan
    Get-Content -Raw -Encoding UTF8 $File | & mysql @mysqlArgs
    if ($LASTEXITCODE -ne 0) {
        throw "执行失败: $File (exit=$LASTEXITCODE)"
    }
}

if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
    throw "未找到 mysql 客户端，请先安装并加入 PATH"
}

Push-Location (Join-Path $SqlDir "..")
try {
    if (-not $SkipCreateDb) {
        Invoke-MysqlFile -User $RootUser -Password $RootPassword -File (Join-Path $SqlDir "00_create_database.sql")
    }
    if (-not $SkipSchema) {
        Invoke-MysqlFile -User $AppUser -Password $AppPassword -File (Join-Path $SqlDir "01_full_schema_ddl.sql") -Db $Database
    }
    if (-not $SkipInit) {
        Invoke-MysqlFile -User $AppUser -Password $AppPassword -File (Join-Path $SqlDir "02_init_data.sql") -Db $Database
    }
    Write-Host "数据库部署完成: $Database@$DbHost`:$Port" -ForegroundColor Green
}
finally {
    Pop-Location
}
