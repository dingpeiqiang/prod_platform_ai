"""
工作流引擎初始化

在应用启动时调用此模块，注册动作处理器和工作流定义
"""
import os
import logging

from .workflow_engine import workflow_engine
from .tariff_actions import (
    action_parse_input,
    action_query_tariff,
    action_generate_form,
    action_validate_form,
    action_merge_results
)

logger = logging.getLogger("workflow_init")


def init_workflow_engine():
    """初始化工作流引擎"""
    logger.info("[WorkflowInit] 开始初始化工作流引擎...")
    
    # 注册资费备案动作
    register_tariff_actions()
    
    # 加载工作流定义
    load_workflows()
    
    logger.info("[WorkflowInit] 工作流引擎初始化完成")


def register_tariff_actions():
    """注册资费备案动作"""
    actions = [
        ("tariff.parse_input", action_parse_input),
        ("tariff.query_tariff", action_query_tariff),
        ("tariff.generate_form", action_generate_form),
        ("tariff.validate_form", action_validate_form),
        ("tariff.merge_results", action_merge_results),
        ("tariff.handle_missing_code", handle_missing_code_action)
    ]
    
    for action_name, handler in actions:
        workflow_engine.register_action(action_name, handler)


async def handle_missing_code_action(context, **kwargs):
    """处理缺失套餐编码的动作"""
    parse_result = context.step_results.get("parse_input", {})
    return {
        "action": "ask_user",
        "missing_fields": parse_result.get("missing_fields", []),
        "message": parse_result.get("message", "请提供套餐编码")
    }


def load_workflows():
    """加载工作流定义"""
    # 获取工作流配置目录
    config_dir = os.path.join(os.path.dirname(__file__), "../config/workflows")
    
    if not os.path.exists(config_dir):
        logger.warning(f"[WorkflowInit] 工作流配置目录不存在: {config_dir}")
        return
    
    # 加载所有 JSON 文件
    for filename in os.listdir(config_dir):
        if filename.endswith(".json"):
            filepath = os.path.join(config_dir, filename)
            try:
                workflow = workflow_engine.load_workflow_from_file(filepath)
                workflow_engine.register_workflow(workflow)
                logger.info(f"[WorkflowInit] 加载工作流: {workflow.id} ({workflow.name})")
            except Exception as e:
                logger.error(f"[WorkflowInit] 加载工作流失败 {filename}: {e}")
