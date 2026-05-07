@echo off
chcp 65001 >nul
title AI驱动动态表单 - Docker 部署脚本
color 0A

echo.
echo ╔═══════════════════════════════════════════════════════════╗
echo ║       AI驱动动态表单 - 容器化部署脚本                       ║
echo ╚═══════════════════════════════════════════════════════════╝
echo.

cd /d "%~dp0"

echo [1/5] 检查 Docker 环境...
docker --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 未安装 Docker，请先安装 Docker Desktop
    pause
    exit /b 1
)
echo [OK] Docker 已就绪
echo.

echo [2/5] 检查 docker-compose...
docker compose version >nul 2>&1
if errorlevel 1 (
    docker-compose --version >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] 未安装 docker-compose
        pause
        exit /b 1
    )
    set COMPOSE_CMD=docker-compose
) else (
    set COMPOSE_CMD=docker compose
)
echo [OK] docker-compose 就绪
echo.

echo [3/5] 检查 .env 配置文件...
if not exist ".env" (
    echo [INFO] 未找到 .env 文件，将使用 .env.example 模板
    if exist ".env.example" (
        copy .env.example .env >nul
        echo [INFO] 已创建 .env 文件，请编辑填入实际配置
    )
) else (
    echo [OK] .env 配置文件已存在
)
echo.

echo [4/5] 构建并启动容器...
echo    生产模式：所有服务（前端+后端）
echo    访问地址：http://localhost
echo    API文档：  http://localhost:6173/docs
echo.
%COMPOSE_CMD% up -d --build
if errorlevel 1 (
    echo [ERROR] 容器启动失败
    pause
    exit /b 1
)
echo.

echo [5/5] 检查服务健康状态...
timeout /t 10 /nobreak >nul
%COMPOSE_CMD% ps
echo.
echo ========================================
echo    前端地址:  http://localhost
echo    API文档:   http://localhost:6173/docs
echo    停止服务:  docker-compose down
echo ========================================
echo.
echo [OK] 部署完成！
pause