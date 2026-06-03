@echo off
chcp 65001 >nul

echo ============================================
echo    Mock API Server 启动脚本
echo ============================================
echo.
echo 使用说明:
echo   start-mock-server-custom.bat [端口号]
echo   默认端口: 6174
echo.

rem 设置默认端口
set "MOCK_SERVER_PORT=6174"

rem 如果提供了参数，则使用参数作为端口
if "%1" neq "" (
    set "MOCK_SERVER_PORT=%1"
)

rem 设置 Python 路径
set "PYTHON_EXE=python"

rem 检查 Python 是否可用
%PYTHON_EXE% --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Python 未安装或未添加到 PATH
    pause
    exit /b 1
)

echo [INFO] 启动 Mock API Server...
echo [INFO] 端口: %MOCK_SERVER_PORT%
echo [INFO] 访问地址: http://localhost:%MOCK_SERVER_PORT%
echo [INFO] 健康检查: http://localhost:%MOCK_SERVER_PORT%/list
echo [INFO] 按 Ctrl+C 停止服务
echo.

rem 启动服务
cd backend
set "MOCK_SERVER_PORT=%MOCK_SERVER_PORT%"
%PYTHON_EXE% mock_server.py

pause
