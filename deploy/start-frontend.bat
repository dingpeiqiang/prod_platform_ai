@echo off
chcp 65001 >nul
title AI驱动动态表单 - 前端启动脚本 (Vite)
color 0B

echo.
echo ╔═══════════════════════════════════════════════════════════╗
echo ║       AI驱动动态表单底层框架 - 前端启动脚本                ║
echo ╚═══════════════════════════════════════════════════════════╝
echo.

cd /d "%~dp0..\frontend"

echo [1/3] 检查Node.js环境...
node --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 未找到Node.js，请先安装Node.js 16+
    pause
    exit /b 1
)
echo [OK] Node.js:
node --version
echo.

echo [2/3] 检查npm依赖...
if not exist "node_modules" (
    echo [INFO] 首次运行，正在安装依赖...
    call npm install
) else (
    echo [OK] 依赖已存在
)
echo.

echo [3/3] 检查后端服务...
curl -s http://localhost:6173/api/v1/health >nul 2>&1
if %errorLevel% == 0 (
    echo [OK] 后端服务正常运行 (http://localhost:6173)
) else (
    curl -s http://localhost:6174/api/v1/health >nul 2>&1
    if %errorLevel% == 0 (
        echo [OK] 后端服务正常运行 (http://localhost:6174)
    ) else (
        echo [WARNING] 未检测到后端服务
        echo [INFO] 请确保后端已启动
    )
)
echo.

echo.
echo [READY] 启动 Vite 开发服务器...
echo.
echo    前端地址:  http://localhost:5173
echo    后端API:   http://localhost:6173
echo.
echo ========================================
echo.

call npm run dev

REM 脚本结束，不暂停（由 start-all.bat 一键启动时不需要）
