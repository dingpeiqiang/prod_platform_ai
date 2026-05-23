from contextlib import asynccontextmanager
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import ValidationError
import logging
import logging.handlers
import os

# ── 日志配置 ─────────────────────────────────────────────────────────────────
import logging
import logging.handlers
import os

_BASE_DIR = os.path.dirname(os.path.abspath(__file__))
# logs 目录放在 backend/logs/（与 app/ 同级），避免 app/app/logs 的嵌套问题
_LOG_DIR = os.path.join(_BASE_DIR, "..", "logs")
os.makedirs(_LOG_DIR, exist_ok=True)

_FMT = "%(asctime)s.%(msecs)03d [%(levelname)-8s] %(name)-25s: %(message)s"
_DATE_FMT = "%H:%M:%S"

# 清除所有现有handlers
_root_logger = logging.getLogger()
_root_logger.handlers.clear()

# 终端输出
_console_handler = logging.StreamHandler()
_console_handler.setLevel(logging.DEBUG)
_console_handler.setFormatter(logging.Formatter(_FMT, _DATE_FMT))
_root_logger.addHandler(_console_handler)

# 文件输出（简化版，避免文件占用问题）
try:
    _file_handler = logging.FileHandler(
        filename=os.path.join(_LOG_DIR, "app.log"),
        mode="a",
        encoding="utf-8",
        delay=True,
    )
    _file_handler.setLevel(logging.DEBUG)
    _file_handler.setFormatter(logging.Formatter(_FMT, _DATE_FMT))
    _root_logger.addHandler(_file_handler)
except Exception as e:
    print(f"Failed to create file handler: {e}")

# 设置根日志级别
_root_logger.setLevel(logging.DEBUG)

# 详细配置各模块的日志级别
_LOG_MODULES = [
    "main", "llm_service", "chat_api", "config_loader", "agent_executor",
    "form_service", "form_api", "chat_with_tools_api", "config_api",
    "history_service", "ontology_service",
    "validation_service", "harness.engine", "harness.observability",
    "llm_call",
    "intent.form_handler", "recommendation_engine",
    "app.api.chat",  # 确保 chat.py 中的 logger 也能输出 DEBUG
    "app.api.chat",  # 重复配置确保所有 chat.py 中的 logger 都能输出 DEBUG
]

for _name in _LOG_MODULES:
    logger_instance = logging.getLogger(_name)
    logger_instance.setLevel(logging.DEBUG)

# 降低第三方库的日志级别
logging.getLogger("uvicorn.access").setLevel(logging.INFO)
logging.getLogger("uvicorn.autodiscover").setLevel(logging.WARNING)
logging.getLogger("watchfiles.main").setLevel(logging.WARNING)
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("urllib3").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)

_logger = logging.getLogger("main")
_logger.info("=" * 60)
_logger.info("日志系统初始化完成")
_logger.info("日志文件: %s", os.path.join(_LOG_DIR, "app.log"))
_logger.info("日志级别: DEBUG")
_logger.info("=" * 60)

# ── 其他导入（在日志配置之后）─────────────────────────────────────────────────
from app.core.config import get_settings
from app.core.database import engine, Base, SessionLocal
from app.api.form import router as form_router
from app.api.config import router as config_router
from app.api.validation import router as validation_router
from app.api.chat import router as chat_router
from app.api.admin import router as admin_router
from app.api.chat_v2 import router as chat_crud_router
from app.api.chat_with_tools import router as chat_with_tools_router
from app.api.harness import router as harness_router
from app.api.mcp import router as mcp_router
from app.api.health import router as health_router
from app.api.langchain_api import router as langchain_router
from app.api.visualization import router as visualization_router
from app.api.workflows import router as workflow_router
from app.api.execution import router as execution_router
from app.api.scheduler import router as scheduler_router
from app.api.mcp_management import router as mcp_management_router
from app.api.kb import router as kb_router
from app.api.llm_config import router as llm_config_router
from app.mock import router as mock_router

settings = get_settings()
Base.metadata.create_all(bind=engine)

from app.core.config_loader import config_loader
config_loader.set_db_session_factory(SessionLocal)

# ── MCP 工具初始化 ──────────────────────────────────────────────────────────
# 在应用启动时注册所有 MCP 工具
from app.mcp_tools import register_all_tools, register_external_tariff_tools
_mcp_tools = register_all_tools()

# 注册外部资费工具（query_tariff_by_code / query_tariff_info 实际调用外部 HTTP API）
tariff_count = register_external_tariff_tools()

# 从数据库加载外部 API 工具
from app.core.database import SessionLocal
from app.mcp_tools.tool_registry_manager import init_external_tools

db_session = SessionLocal()
try:
    external_count = init_external_tools(db_session)
    external_count += tariff_count  # 加上代码注册的外部资费工具
    _logger.info(f"[MCP] 已注册 {external_count} 个外部 API 工具（代码注册 + 数据库配置）")
finally:
    db_session.close()

_logger.info(f"[MCP] 总计 {_mcp_tools.get_tool_count() + external_count} 个工具可用")


# ── 数据初始化 ─────────────────────────────────────────────────────────────
from app.services.data_init import init_all_data
init_all_data()


@asynccontextmanager
async def lifespan(app: FastAPI):
    _logger.info("应用启动完成，所有路由已注册")
    yield
    _logger.info("应用正在关闭")


app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description="AI驱动动态表单底层框架 v2.0 - 配置化+LLM智能",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """全局验证异常处理器 - 显示详细的验证错误信息"""
    logger = logging.getLogger("main")
    logger.error(f"[Validation Error] 请求验证失败")
    logger.error(f"[Validation Error] URL: {request.url}")
    logger.error(f"[Validation Error] 错误详情: {exc.errors()}")
    logger.error(f"[Validation Error] 原始消息: {exc.json()}")
    
    return JSONResponse(
        status_code=422,
        content={
            "success": False,
            "message": "请求参数验证失败",
            "detail": exc.errors(),
            "body": exc.body if hasattr(exc, 'body') else None
        },
    )


app.include_router(health_router)  # 健康检查接口
app.include_router(form_router)
app.include_router(config_router)
app.include_router(validation_router)
app.include_router(chat_router)
app.include_router(admin_router)  # 管理相关 API
app.include_router(chat_crud_router)  # 通用聊天 v2 API
app.include_router(chat_with_tools_router)
app.include_router(harness_router)
app.include_router(mcp_router)  # MCP 工具接口
app.include_router(mcp_management_router)  # MCP 管理接口
app.include_router(langchain_router)  # LangChain API
app.include_router(visualization_router)  # 可视化 API
app.include_router(workflow_router)  # 工作流管理 API
app.include_router(execution_router)  # 工作流执行 API
app.include_router(scheduler_router)  # 工作流调度器 API
app.include_router(kb_router)  # 知识库 API
app.include_router(llm_config_router)  # LLM 配置管理 API

# ── Mock API（条件注册）──────────────────────────────────────────────────────
if settings.MOCK_API_ENABLED:
    app.include_router(mock_router)
    _logger.info("[Mock] Mock API 已启用，访问 /mock 查看可用端点")
else:
    _logger.info("[Mock] Mock API 未启用（设置 MOCK_API_ENABLED=true 开启）")


@app.get("/")
async def root():
    _logger.debug("[root] 收到根路径请求")
    return {"name": settings.APP_NAME, "version": settings.APP_VERSION, "status": "running"}


@app.get("/health")
async def health_check():
    _logger.debug("[health] 收到健康检查请求")
    return {"status": "healthy"}


@app.get("/debug/logger-test")
async def logger_test():
    """调试端点：触发各级别日志，验证日志系统是否正常"""
    _logger.debug("  [DEBUG] DEBUG 级别日志")
    _logger.info("   [INFO] INFO 级别日志")
    _logger.warning("[WARNING] WARNING 级别日志")
    _logger.error("  [ERROR] ERROR 级别日志")
    for h in logging.getLogger().handlers:
        h.flush()
    return {
        "status": "logged",
        "file": os.path.join(_LOG_DIR, "app.log"),
        "check": ["终端应看到 DEBUG~ERROR 四行", "app.log 文件应有多行记录"]
    }


@app.get("/debug/logger-status")
async def logger_status():
    root = logging.getLogger()
    return {
        "root_level": logging.getLevelName(root.level),
        "handler_count": len(root.handlers),
        "log_files": os.listdir(_LOG_DIR) if os.path.exists(_LOG_DIR) else [],
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.WEBSOCKET_HOST,
        port=settings.WEBSOCKET_PORT,
        reload=settings.DEBUG
    )
