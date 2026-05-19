@echo off
chcp 65001 >nul
title AI Platform - Backend (FastAPI)
color 0A

echo.
echo ============================================================
echo          AI Platform - Backend Startup Script
echo ============================================================
echo.

cd /d "%~dp0backend"

echo [1/4] Checking Python environment...
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Python not found. Please install Python 3.8+
    pause
    exit /b 1
)
echo [OK] Python:
python --version
echo.

echo [2/4] Checking/Creating virtual environment...
if not exist "venv" (
    echo [INFO] First run, creating virtual environment...
    python -m venv venv
)
call venv\Scripts\activate.bat
pip install -r requirements.txt -q
echo [OK] Dependencies ready
echo.

set PORT=6173
echo [3/4] Checking port %PORT% availability...
netstat -ano | findstr :%PORT% | findstr LISTENING >nul 2>&1
if %errorLevel% == 0 (
    echo [WARNING] Port %PORT% is in use, cleaning up...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%PORT% ^| findstr LISTENING') do (
        echo   Killing process PID: %%a
        taskkill /F /PID %%a >nul 2>&1
        if %errorLevel% == 0 (
            echo   [OK] Process %%a terminated
        ) else (
            echo   [ERROR] Failed to terminate process %%a, please handle manually
            pause
            exit /b 1
        )
    )
    timeout /t 1 /nobreak >nul
)
echo [OK] Using port: %PORT%
echo.

echo [4/4] Checking startup parameters...
if "%1"=="dev" goto DEV
if "%1"=="--dev" goto DEV
if "%1"=="-d" goto DEV

echo.
echo [PROD] Starting production mode (default)...
echo    - For development mode: start-backend.bat dev
echo.

:PROD
echo ============================================================
echo    API:       http://localhost:%PORT%
echo    Docs:      http://localhost:%PORT%/docs
echo    Mode:      Single process, no auto-reload
echo    Log:       Terminal + backend\app\logs\app.log
echo ============================================================
echo.
python -m uvicorn app.main:app --host 0.0.0.0 --port %PORT% --log-level debug
goto END

:DEV
echo ============================================================
echo    API:       http://localhost:%PORT%
echo    Docs:      http://localhost:%PORT%/docs
echo    Mode:      Auto-reload (restarts on code change)
echo    Log:       Check backend\app\logs\app.log
echo ============================================================
echo.
set PYTHONUNBUFFERED=1
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port %PORT% --log-level debug
goto END

:END
REM Script ends without pause (not needed when called from start-all.bat)
