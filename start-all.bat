@echo off
chcp 65001 >nul
title AI驱动动态表单 - 一键启动
color 0A

echo.
echo ╔═══════════════════════════════════════════════════════════╗
echo ║                                                           ║
echo ║       AI驱动动态表单底层框架 - 一键启动脚本                ║
echo ║                                                           ║
echo ╚═══════════════════════════════════════════════════════════╝
echo.

:: 检查端口6173占用
set BACKEND_PORT=6173
echo [检查] 端口%BACKEND_PORT%占用情况...
netstat -ano | findstr :%BACKEND_PORT% | findstr LISTENING >nul 2>&1
if %errorLevel% == 0 (
    echo [WARNING] 端口%BACKEND_PORT%已被占用！
    echo.
    echo 请先关闭占用端口的进程：
    echo   netstat -ano ^| findstr :%BACKEND_PORT%
    echo   taskkill /F /PID <进程ID>
    echo.
    pause
    exit /b 1
)
echo [OK] 端口%BACKEND_PORT%可用
echo.

:: 启动后端
echo [启动] 后端服务 (端口%BACKEND_PORT%)...
start "AI表单-后端[%BACKEND_PORT%]" cmd /k "cd /d "%~dp0" && start-backend.bat"

timeout /t 2 /nobreak >nul

:: 启动前端
echo [启动] 前端服务 (端口5173)...
start "AI表单-前端[5173]" cmd /k "cd /d "%~dp0" && start-frontend.bat"

echo.
echo [成功] 服务启动中...
echo.
echo ========================================
echo.
echo    后端API:  http://localhost:%BACKEND_PORT%
echo    前端界面: http://localhost:5173
echo    API文档: http://localhost:%BACKEND_PORT%/docs
echo.
echo    等待5秒让服务完全启动...
echo ========================================
echo.

timeout /t 5 /nobreak >nul

echo [完成] 可以开始使用了！
pause
