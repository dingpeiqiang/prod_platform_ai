@echo off
chcp 65001 >nul
title AI驱动动态表单 - 后端启动脚本 (FastAPI)
color 0A

echo.
echo ╔═══════════════════════════════════════════════════════════╗
echo ║       AI驱动动态表单底层框架 - 后端启动脚本                ║
echo ╚═══════════════════════════════════════════════════════════╝
echo.

cd /d "%~dp0backend"

echo [1/4] 检查Python环境...
python --version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] 未找到Python，请先安装Python 3.8+
    pause
    exit /b 1
)
echo [OK] Python:
python --version
echo.

echo [2/4] 检查/创建虚拟环境...
if not exist "venv" (
    echo [INFO] 首次运行，正在创建虚拟环境...
    python -m venv venv
)
call venv\Scripts\activate.bat
pip install -r requirements.txt -q
echo [OK] 依赖就绪
echo.

echo [3/4] 检查端口6173占用情况...
netstat -ano | findstr :6173 | findstr LISTENING >nul 2>&1
if %errorLevel% == 0 (
    echo [WARNING] 端口6173已被占用，尝试使用6174端口
    set PORT=6174
) else (
    set PORT=6173
)
echo [OK] 将使用端口: %PORT%
echo.

echo [4/4] 选择启动模式：
echo.
echo    [1] 生产模式（uvicorn，无 reload，日志完整）
echo    [2] 开发模式（uvicorn --reload，改代码自动重启）
echo.
set /p MODE=请输入选项 [1/2，默认1]:

if "%MODE%"=="2" goto DEV

:PROD
echo.
echo [PROD] 启动生产模式...
echo.
echo    - API地址:  http://localhost:%PORT%
echo    - API文档:  http://localhost:%PORT%/docs
echo    - 模式:     单进程，无自动重载
echo    - 日志:     终端 + backend\app\logs\app.log
echo.
echo ========================================
python -m uvicorn app.main:app --host 0.0.0.0 --port %PORT% --log-level debug
goto END

:DEV
echo.
echo [DEV] 启动开发模式...
echo.
echo    - API地址:  http://localhost:%PORT%
echo    - API文档:  http://localhost:%PORT%/docs
echo    - 模式:     自动重载（改代码自动重启）
echo    - 日志:     查看 backend\app\logs\app.log
echo.
echo ========================================
set PYTHONUNBUFFERED=1
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port %PORT% --log-level debug
goto END

:END
pause
