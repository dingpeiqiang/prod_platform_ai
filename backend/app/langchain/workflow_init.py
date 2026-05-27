"""
工作流引擎初始化

在应用启动时调用此模块，初始化节点模块和工作流定义
"""
import os
from app.core.logger import get_logger

logger = get_logger(__name__)

from app.langchain.workflow_nodes import initialize_nodes
from app.langchain.workflow_engine import WorkflowEngine

# 创建工作流引擎实例
workflow_engine = WorkflowEngine()


def init_workflow_engine():
    """初始化工作流引擎"""
    logger.info("[WorkflowInit] 开始初始化工作流引擎...")
    
    # 初始化节点模块（自动加载所有节点文件）
    initialize_nodes()
    
    # 加载工作流定义
    load_workflows()
    
    logger.info("[WorkflowInit] 工作流引擎初始化完成")


def load_workflows():
    """加载工作流定义
    
    工作流配置已迁移至数据库管理，此函数保留为空以保持兼容性。
    工作流将在运行时从数据库动态加载。
    """
    logger.info("[WorkflowInit] 工作流配置已迁移至数据库，跳过文件加载")