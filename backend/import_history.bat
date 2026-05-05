@echo off
chcp 65001 >nul 2>&1
cd /d "%~dp0"

echo ============================================================
echo   历史数据导入器 v3（JSONL原生，支持嵌套/层级结构）
echo   数据源: config/import_data/
echo   格式:  每行一个JSON对象 (.data.jsonl)
echo ============================================================
echo.

python -m app.scripts.import_history --list

if "%1"=="" (
    echo.
    set /p choice="输入表单编码 (或 all 全部导入, 或 --dry-run 预览): "
    python -m app.scripts.import_history %choice%
) else (
    python -m app.scripts.import_history %*
)

echo.
pause
