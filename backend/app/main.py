from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
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
]

for _name in _LOG_MODULES:
    logging.getLogger(_name).setLevel(logging.DEBUG)

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

settings = get_settings()
Base.metadata.create_all(bind=engine)

from app.core.config_loader import config_loader
config_loader.set_db_session_factory(SessionLocal)

# ── MCP 工具初始化 ──────────────────────────────────────────────────────────
# 在应用启动时注册所有 MCP 工具
from app.mcp_tools import register_all_tools
_mcp_tools = register_all_tools()
_logger.info(f"[MCP] 已注册 {_mcp_tools.get_tool_count()} 个工具")


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
