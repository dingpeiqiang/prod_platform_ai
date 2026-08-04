<#
.SYNOPSIS
Docker Image Management Script for AI Dynamic Form Platform

.DESCRIPTION
统一构建/推送基础镜像脚本。前后端均采用「基础镜像 → 应用镜像」两层结构。
#>

# ==============================================
# Configuration Section
# ==============================================
# WARNING: 修改版本号时，请同步更新对应 Sitech.BJ.Dockerfile.* 中的 FROM
$Config = @{
    BackendBase = @{
        ImageName     = "prod-platform-backend-base"
        ImageTag      = "2.1"  # 手动修改版本号，每次更新基础镜像时递增
        Dockerfile    = "docker/Dockerfile.base.backend"
        Registry      = "10.86.12.11:20200"
        Namespace     = "y21127-crmpos"
        Username      = "dingpq"
        Password      = "Docker.2022!"
    }
    FrontendBase = @{
        ImageName     = "prod-platform-frontend-base"
        ImageTag      = "1.2"  # 手动修改版本号，每次更新基础镜像时递增
        Dockerfile    = "docker/Dockerfile.base.frontend"
        Registry      = "10.86.12.11:20200"
        Namespace     = "y21127-crmpos"
        Username      = "dingpq"
        Password      = "Docker.2022!"
    }
}

# ==============================================
# Helper Functions
# ==============================================

function Test-DockerEngineError {
    <#
    .SYNOPSIS
    判断错误信息是否为本地 Docker 引擎/守护进程不可用
    #>
    param([string]$ErrorText)
    if ([string]::IsNullOrWhiteSpace($ErrorText)) { return $false }
    $patterns = @(
        'dockerDesktopLinuxEngine',
        'dockerDesktopWindowsEngine',
        'docker_engine',
        'failed to connect to the docker API',
        'Cannot connect to the Docker daemon',
        'error during connect',
        'Is the docker daemon running',
        'npipe:////./pipe/'
    )
    foreach ($p in $patterns) {
        if ($ErrorText.IndexOf($p, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            return $true
        }
    }
    return $false
}

function Write-DockerEngineHelp {
    <#
    .SYNOPSIS
    输出本地 Docker 引擎不可用时的明确排查指引
    #>
    param(
        [string]$Context = "操作",
        [string]$Detail = ""
    )
    Write-Host ""
    Write-Host "==============================================" -ForegroundColor Red
    Write-Host "[FAIL] $Context 失败：本地 Docker 引擎未就绪" -ForegroundColor Red
    Write-Host "==============================================" -ForegroundColor Red
    Write-Host "原因: 无法连接 Docker Desktop Linux 引擎（守护进程未运行或管道不可用）" -ForegroundColor Yellow
    if ($Detail) {
        Write-Host "原始错误:" -ForegroundColor DarkGray
        Write-Host "  $Detail" -ForegroundColor DarkGray
    }
    Write-Host ""
    Write-Host "请按以下步骤排查:" -ForegroundColor Cyan
    Write-Host "  1. 打开 Docker Desktop，等待状态变为 Running（托盘图标不再转圈）"
    Write-Host "  2. 确认使用 Linux containers（右键托盘图标 → Switch to Linux containers）"
    Write-Host "  3. 若已打开仍失败: Docker Desktop → Troubleshoot → Restart"
    Write-Host "  4. 验证引擎: 在本机 PowerShell 执行  docker info"
    Write-Host "     - 成功会打印 Server 信息；失败则引擎仍未就绪，勿继续构建"
    Write-Host "  5. 确认 WSL2 后端已启用（Settings → General → Use WSL 2 based engine）"
    Write-Host "==============================================" -ForegroundColor Red
    Write-Host ""
}

function Test-DockerAvailability {
    Write-Host "Checking Docker availability..." -ForegroundColor Cyan

    # 1) CLI 是否安装
    $DockerVersion = docker --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "==============================================" -ForegroundColor Red
        Write-Host "[FAIL] Docker CLI 不可用（未安装或不在 PATH）" -ForegroundColor Red
        Write-Host "==============================================" -ForegroundColor Red
        Write-Host "请安装并启动 Docker Desktop，然后重新打开终端。" -ForegroundColor Yellow
        Write-Host "错误详情: $DockerVersion" -ForegroundColor DarkGray
        Write-Host "==============================================" -ForegroundColor Red
        return $false
    }
    Write-Host "[OK] Docker CLI: $DockerVersion" -ForegroundColor Green

    # 2) 本地引擎/守护进程是否真正可连（--version 无法发现引擎未启动）
    Write-Host "Checking Docker engine (daemon)..." -ForegroundColor Cyan
    $DockerInfo = docker info 2>&1
    $infoExitCode = $LASTEXITCODE
    $DockerInfoText = ($DockerInfo | Out-String).Trim()
    if ($infoExitCode -ne 0) {
        Write-DockerEngineHelp -Context "Docker 引擎检测" -Detail $DockerInfoText
        return $false
    }
    Write-Host "[OK] Docker engine is running" -ForegroundColor Green
    return $true
}

function Invoke-BuildImage {
    param(
        [string]$ImageName,
        [string]$ImageTag,
        [string]$DockerfilePath
    )
    
    $ProjectRoot = (Get-Item $PSScriptRoot).Parent.FullName
    Set-Location $ProjectRoot
    
    $FullTag = "$ImageName`:$ImageTag"
    
    Write-Host "`n" -ForegroundColor Red
    Write-Host "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" -ForegroundColor Red
    Write-Host "!!!                         WARNING                                  !!!" -ForegroundColor Red
    Write-Host "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" -ForegroundColor Red
    Write-Host "!!!  Current version: $FullTag" -ForegroundColor Yellow
    Write-Host "!!!" -ForegroundColor Red
    Write-Host "!!!  Update ImageTag in docker-manager.ps1 before building!          !!!" -ForegroundColor Red
    Write-Host "!!!  e.g. Change 1.2 to 1.3 for a new version.                      !!!" -ForegroundColor Red
    Write-Host "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!" -ForegroundColor Red
    Write-Host "`n" -ForegroundColor Red
    
    Write-Host "==============================================" -ForegroundColor Cyan
    Write-Host "Building image: $FullTag" -ForegroundColor Yellow
    Write-Host "==============================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "提示: 构建日志会实时输出；后端基础镜像需拉取基础层并执行 mvn package，" -ForegroundColor Yellow
    Write-Host "      首次或 --no-cache 时可能需要 10~30+ 分钟，请耐心等待，不要关闭窗口。" -ForegroundColor Yellow
    Write-Host ""

    # 直接流式输出到控制台（勿整体捕获，否则长时间无日志像“卡住”）
    docker buildx build -f $DockerfilePath -t $FullTag --no-cache --pull --provenance=false --sbom=false --output type=image,name=$FullTag,oci-mediatypes=false .
    $buildExitCode = $LASTEXITCODE
    
    if ($buildExitCode -eq 0) {
        Write-Host ""
        Write-Host "[OK] Image built successfully!" -ForegroundColor Green
        Write-Host "Image name: $FullTag" -ForegroundColor Green
        Write-Host ""
        Write-Host "Image info:" -ForegroundColor Cyan
        docker images $ImageName
        return $true
    }

    # 失败后探测引擎是否掉线（构建日志已在上方实时打印）
    $null = docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-DockerEngineHelp -Context "镜像构建" -Detail "docker info 失败，构建过程中引擎可能已断开"
    } else {
        Write-Host ""
        Write-Host "==============================================" -ForegroundColor Red
        Write-Host "[FAIL] 镜像构建失败: $FullTag" -ForegroundColor Red
        Write-Host "==============================================" -ForegroundColor Red
        Write-Host "请向上滚动查看 docker buildx 的完整输出定位原因。" -ForegroundColor Yellow
        Write-Host "常见原因: Dockerfile 语法错误 / 基础镜像拉取失败 / 网络问题 / 磁盘空间不足 / Maven 依赖下载失败" -ForegroundColor Yellow
        Write-Host "==============================================" -ForegroundColor Red
    }
    return $false
}

function Invoke-PushImage {
    param(
        [string]$ImageName,
        [string]$ImageTag,
        [string]$Registry,
        [string]$Namespace,
        [string]$Username,
        [string]$Password
    )
    
    $ProjectRoot = (Get-Item $PSScriptRoot).Parent.FullName
    Set-Location $ProjectRoot
    
    $LocalTag = "$ImageName`:$ImageTag"
    
    Write-Host "Checking local image: $LocalTag" -ForegroundColor Yellow
    $ImageExistsOutput = docker images -q $LocalTag 2>&1
    if ($LASTEXITCODE -ne 0 -and (Test-DockerEngineError -ErrorText ($ImageExistsOutput | Out-String))) {
        Write-DockerEngineHelp -Context "推送前检查本地镜像" -Detail ($ImageExistsOutput | Out-String).Trim()
        return $false
    }
    if (-not $ImageExistsOutput) {
        Write-Host ""
        Write-Host "[FAIL] 本地镜像不存在: $LocalTag" -ForegroundColor Red
        Write-Host "请先构建镜像（菜单选项 1 或 4）" -ForegroundColor Yellow
        return $false
    }
    Write-Host "Local image exists" -ForegroundColor Green
    
    $RemoteTag = "$Registry`/$Namespace`/$ImageName`:$ImageTag"
    Write-Host "`nRemote image tag: $RemoteTag" -ForegroundColor Yellow
    
    Write-Host "Tagging image..." -ForegroundColor Cyan
    $tagOutput = docker tag $LocalTag $RemoteTag 2>&1
    if ($LASTEXITCODE -ne 0) {
        $tagText = ($tagOutput | Out-String).Trim()
        if (Test-DockerEngineError -ErrorText $tagText) {
            Write-DockerEngineHelp -Context "镜像打标签" -Detail $tagText
        } else {
            Write-Host "[FAIL] 镜像打标签失败: $LocalTag -> $RemoteTag" -ForegroundColor Red
            if ($tagText) { Write-Host $tagText -ForegroundColor DarkGray }
        }
        return $false
    }
    
    Write-Host "`nLogging in to $Registry ..." -ForegroundColor Cyan
    Write-Host "Username: $Username" -ForegroundColor Yellow
    $loginOutput = $Password | docker login $Registry --username $Username --password-stdin 2>&1
    $Password = $null
    $loginText = ($loginOutput | Out-String).Trim()
    
    if ($LASTEXITCODE -ne 0) {
        if (Test-DockerEngineError -ErrorText $loginText) {
            Write-DockerEngineHelp -Context "仓库登录" -Detail $loginText
        } else {
            Write-Host "[FAIL] 仓库登录失败" -ForegroundColor Red
            Write-Host "请检查:" -ForegroundColor Yellow
            Write-Host "  1. Registry 地址: $Registry"
            Write-Host "  2. HTTP 仓库需在 Docker daemon 配置 insecure-registry: $Registry"
            Write-Host "  3. 网络连通性与账号密码"
            if ($loginText) { Write-Host "详情: $loginText" -ForegroundColor DarkGray }
        }
        return $false
    }
    Write-Host "[OK] Login successful!" -ForegroundColor Green
    
    Write-Host ""
    Write-Host "Pushing image..." -ForegroundColor Cyan
    docker push $RemoteTag
    $pushExitCode = $LASTEXITCODE
    
    if ($pushExitCode -eq 0) {
        Write-Host ""
        Write-Host "[OK] Image pushed successfully!" -ForegroundColor Green
        Write-Host "Remote image: $RemoteTag" -ForegroundColor Green
        return $true
    }

    $null = docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-DockerEngineHelp -Context "镜像推送" -Detail "docker info 失败，推送过程中引擎可能已断开"
    } else {
        Write-Host ""
        Write-Host "[FAIL] 镜像推送失败: $RemoteTag" -ForegroundColor Red
        Write-Host "若提示 unsupported content type / OCI manifest，请重新构建镜像后再推送" -ForegroundColor Yellow
    }
    return $false
}

function Invoke-BuildAndPush {
    param(
        [hashtable]$Config,
        [string]$ImageType
    )
    
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "$ImageType - Build and Push" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    
    if (-not (Test-DockerAvailability)) {
        return
    }
    
    $buildSuccess = Invoke-BuildImage -ImageName $Config.ImageName -ImageTag $Config.ImageTag -DockerfilePath $Config.Dockerfile
    if ($buildSuccess) {
        Invoke-PushImage -ImageName $Config.ImageName -ImageTag $Config.ImageTag -Registry $Config.Registry -Namespace $Config.Namespace -Username $Config.Username -Password $Config.Password
    }
}

# ==============================================
# Main Menu
# ==============================================
function Show-Menu {
    Clear-Host
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "    Docker Image Management Tool" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  Backend Base Image:" -ForegroundColor Yellow
    Write-Host "    1. Build Backend Base Image"
    Write-Host "    2. Push Backend Base Image"
    Write-Host "    3. Build & Push Backend Base Image"
    Write-Host ""
    Write-Host "  Frontend Base Image:" -ForegroundColor Yellow
    Write-Host "    4. Build Frontend Base Image"
    Write-Host "    5. Push Frontend Base Image"
    Write-Host "    6. Build & Push Frontend Base Image"
    Write-Host ""
    Write-Host "  Other:" -ForegroundColor Yellow
    Write-Host "    7. Show Configurations"
    Write-Host "    0. Exit"
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
}

# ==============================================
# Main Execution
# ==============================================
do {
    Show-Menu
    $choice = Read-Host "Enter your choice (0-7)"
    
    switch ($choice) {
        "1" {
            Write-Host "`n========================================" -ForegroundColor Cyan
            Write-Host "Build Backend Base Image" -ForegroundColor Cyan
            Write-Host "========================================" -ForegroundColor Cyan
            
            if (Test-DockerAvailability) {
                Invoke-BuildImage -ImageName $Config.BackendBase.ImageName -ImageTag $Config.BackendBase.ImageTag -DockerfilePath $Config.BackendBase.Dockerfile
            }
        }
        
        "2" {
            Write-Host "`n========================================" -ForegroundColor Cyan
            Write-Host "Push Backend Base Image" -ForegroundColor Cyan
            Write-Host "========================================" -ForegroundColor Cyan
            
            if (Test-DockerAvailability) {
                Invoke-PushImage -ImageName $Config.BackendBase.ImageName -ImageTag $Config.BackendBase.ImageTag -Registry $Config.BackendBase.Registry -Namespace $Config.BackendBase.Namespace -Username $Config.BackendBase.Username -Password $Config.BackendBase.Password
            }
        }
        
        "3" {
            Invoke-BuildAndPush -Config $Config.BackendBase -ImageType "Backend Base Image"
        }
        
        "4" {
            Write-Host "`n========================================" -ForegroundColor Cyan
            Write-Host "Build Frontend Base Image" -ForegroundColor Cyan
            Write-Host "========================================" -ForegroundColor Cyan
            
            if (Test-DockerAvailability) {
                Invoke-BuildImage -ImageName $Config.FrontendBase.ImageName -ImageTag $Config.FrontendBase.ImageTag -DockerfilePath $Config.FrontendBase.Dockerfile
            }
        }
        
        "5" {
            Write-Host "`n========================================" -ForegroundColor Cyan
            Write-Host "Push Frontend Base Image" -ForegroundColor Cyan
            Write-Host "========================================" -ForegroundColor Cyan
            
            if (Test-DockerAvailability) {
                Invoke-PushImage -ImageName $Config.FrontendBase.ImageName -ImageTag $Config.FrontendBase.ImageTag -Registry $Config.FrontendBase.Registry -Namespace $Config.FrontendBase.Namespace -Username $Config.FrontendBase.Username -Password $Config.FrontendBase.Password
            }
        }
        
        "6" {
            Invoke-BuildAndPush -Config $Config.FrontendBase -ImageType "Frontend Base Image"
        }
        
        "7" {
            Write-Host "`n========================================" -ForegroundColor Cyan
            Write-Host "Current Configurations" -ForegroundColor Cyan
            Write-Host "========================================" -ForegroundColor Cyan
            
            Write-Host "`n[Backend Base Image]" -ForegroundColor Yellow
            $Config.BackendBase.GetEnumerator() | ForEach-Object {
                if ($_.Key -eq "Password") {
                    Write-Host "  $($_.Key): ********"
                } else {
                    Write-Host "  $($_.Key): $($_.Value)"
                }
            }
            
            Write-Host "`n[Frontend Base Image]" -ForegroundColor Yellow
            $Config.FrontendBase.GetEnumerator() | ForEach-Object {
                if ($_.Key -eq "Password") {
                    Write-Host "  $($_.Key): ********"
                } else {
                    Write-Host "  $($_.Key): $($_.Value)"
                }
            }
        }
        
        "0" {
            Write-Host "`nExiting..." -ForegroundColor Cyan
            break
        }
        
        default {
            Write-Error "`nInvalid choice! Please enter a number between 0 and 7."
        }
    }
    
    if ($choice -ne "0") {
        Read-Host "`nPress Enter to continue..."
    }
} while ($choice -ne "0")

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Done" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
