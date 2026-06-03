@echo off
setlocal enabledelayedexpansion

echo ============================================
echo    Mock API Server Starter
echo ============================================
echo.
echo Usage:
echo   start-mock-server.bat [port]
echo   Default port: 6174
echo.

set "MOCK_SERVER_PORT=6174"

if not "%1"=="" (
    set "MOCK_SERVER_PORT=%1"
)

set "PYTHON_EXE=python"

%PYTHON_EXE% --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python not found. Please install Python and add to PATH.
    pause
    exit /b 1
)

:: Check if port is in use, kill the process if so
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :!MOCK_SERVER_PORT! ^| findstr LISTENING') do (
    set "PID=%%a"
    goto :kill_process
)

:check_done
echo INFO: Starting Mock API Server...
echo INFO: Port: !MOCK_SERVER_PORT!
echo INFO: URL: http://localhost:!MOCK_SERVER_PORT!/mock
echo INFO: Press Ctrl+C to stop
echo.

cd backend
set "MOCK_SERVER_PORT=!MOCK_SERVER_PORT!"
%PYTHON_EXE% mock_server.py

endlocal
pause
exit /b

:kill_process
echo WARNING: Port !MOCK_SERVER_PORT! is already in use (PID: !PID!)
echo INFO: Terminating the process...
taskkill /F /PID !PID! >nul 2>&1
timeout /t 1 /nobreak >nul
set "PID="
goto :check_done
