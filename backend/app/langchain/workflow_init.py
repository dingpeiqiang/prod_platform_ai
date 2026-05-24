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
from .workflow_actions import (
    action_workflow_start,
    action_workflow_end,
    action_call_llm,
    action_ask_user,
    action_call_tool,
    action_set_variable,
    action_http_request,
    action_execute_code,
    action_parse_output,
    action_query_knowledge,
    action_generate_form as workflow_generate_form,
    action_validate_form as workflow_validate_form,
    action_merge_results as workflow_merge_results,
    action_handle_missing_fields,
    action_set_prompt
)

logger = logging.getLogger("workflow_init")


def init_workflow_engine():
    """初始化工作流引擎"""
    logger.info("[WorkflowInit] 开始初始化工作流引擎...")
    
    # 注册资费备案动作
    register_tariff_actions()
    
    # 注册工作流编辑器动作
    register_workflow_actions()
    
    # 加载工作流定义
    load_workflows()
    
    logger.info("[WorkflowInit] 工作流引擎初始化完成")


def register_workflow_actions():
    """注册工作流编辑器动作"""
    actions = [
        ("workflow.start", action_workflow_start),
        ("workflow.end", action_workflow_end),
        ("workflow.set_prompt", action_set_prompt),
        ("workflow.call_llm", action_call_llm),
        ("workflow.ask_user", action_ask_user),
        ("workflow.call_tool", action_call_tool),
        ("workflow.set_variable", action_set_variable),
        ("workflow.http_request", action_http_request),
        ("workflow.execute_code", action_execute_code),
        ("workflow.parse_output", action_parse_output),
        ("workflow.query_knowledge", action_query_knowledge),
        ("workflow.generate_form", workflow_generate_form),
        ("workflow.validate_form", workflow_validate_form),
        ("workflow.merge_results", workflow_merge_results),
        ("workflow.handle_missing_fields", action_handle_missing_fields),
    ]
    
    for action_name, handler in actions:
        workflow_engine.register_action(action_name, handler)
    
    logger.info(f"[WorkflowInit] 已注册 {len(actions)} 个工作流编辑器动作")


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
