@echo off
chcp 65001 >nul
echo.
echo ========================================
echo   🚀 历史数据导入功能 - 快速演示
echo ========================================
echo.
echo 本脚本将引导你完成历史数据导入功能的演示
echo.
echo ----------------------------------------
echo.

:menu
echo 请选择操作：
echo.
echo 1. 查看可导入的表单列表（命令行）
echo 2. 预览请假数据导入效果
echo 3. 执行请假数据导入（限制5条）
echo 4. 启动后端服务（用于Web界面测试）
echo 5. 运行API测试脚本
echo 6. 查看使用说明文档
echo 0. 退出
echo.
set /p choice=请输入选项 (0-6): 

if "%choice%"=="1" goto list_forms
if "%choice%"=="2" goto preview_import
if "%choice%"=="3" goto execute_import
if "%choice%"=="4" goto start_backend
if "%choice%"=="5" goto test_api
if "%choice%"=="6" goto view_docs
if "%choice%"=="0" goto end
goto menu

:list_forms
echo.
echo ----------------------------------------
echo 正在列出可导入的表单...
echo ----------------------------------------
cd /d "%~dp0..\backend"
python -m app.scripts.import_history --list
cd /d "%~dp0.."
echo.
pause
goto menu

:preview_import
echo.
echo ----------------------------------------
echo 正在预览请假数据导入效果...
echo ----------------------------------------
cd /d "%~dp0..\backend"
python -m app.scripts.import_history leave --dry-run --limit 5
cd /d "%~dp0.."
echo.
pause
goto menu

:execute_import
echo.
echo ----------------------------------------
echo 警告：此操作将写入数据库！
echo ----------------------------------------
set /p confirm=确认执行？(y/n): 
if /i "%confirm%"=="y" (
    cd /d "%~dp0..\backend"
    python -m app.scripts.import_history leave --limit 5
    cd /d "%~dp0.."
    echo.
    echo ✅ 导入完成！
) else (
    echo.
    echo ❌ 已取消操作
)
echo.
pause
goto menu

:start_backend
echo.
echo ----------------------------------------
echo 正在启动后端服务...
echo ----------------------------------------
echo 提示：按 Ctrl+C 停止服务
echo.
cd /d "%~dp0..\backend"
python -m uvicorn app.main:app --reload --port 8000
cd /d "%~dp0.."
goto menu

:test_api
echo.
echo ----------------------------------------
echo 正在运行API测试...
echo ----------------------------------------
echo 注意：请确保后端服务已启动
echo.
cd /d "%~dp0.."
python test_import_api.py
echo.
pause
goto menu

:view_docs
echo.
echo ----------------------------------------
echo 打开使用说明文档...
echo ----------------------------------------
start "" "QUICK_START_IMPORT.md"
echo.
echo 💡 提示：也可以使用以下命令查看详细文档
echo    - QUICK_START_IMPORT.md          (快速开始)
echo    - docs\历史数据导入功能说明.md     (详细说明)
echo    - docs\历史数据导入界面说明.md     (界面说明)
echo.
pause
goto menu

:end
echo.
echo ========================================
echo   感谢使用！再见！ 👋
echo ========================================
echo.
exit /b 0
