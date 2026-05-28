"""
资费备案 - 处理缺失编码节点

输入参数：无（从上下文获取数据）

输出结果：
- action: 动作类型（ask_user）
- missing_fields: 缺失字段列表
- message: 消息
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class TariffHandleMissingCodeNode(WorkflowNode):
    """资费备案处理缺失编码节点"""
    
    name = "tariff.handle_missing_code"
    description = "处理缺失套餐编码的情况"
    _legacy = True
    inputs = {}
    outputs = {
        "action": {"type": "str", "description": "动作类型"},
        "missing_fields": {"type": "list", "description": "缺失字段列表"},
        "message": {"type": "str", "description": "消息"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        # 记录处理逻辑
        processing = "处理缺失套餐编码"
        self._log_processing(processing)
        
        # 从上下文获取解析结果（支持新的输出格式 {'output': value}）
        parse_result = context.step_results.get("parse_input", {})
        # 从 output 字段获取实际数据
        parse_result = parse_result.get("output", parse_result)
        
        missing_fields = parse_result.get("missing_fields", [])
        
        result = {
            "success": True,
            "action": "ask_user",
            "missing_fields": missing_fields,
            "message": parse_result.get("message", "请提供套餐编码"),
            "input": {},
            "processing": processing,
            "output": {
                "action": "ask_user",
                "missing_field_count": len(missing_fields)
            }
        }
        
        self._log_output(
            success=True,
            action="ask_user",
            missing_field_count=len(missing_fields)
        )
        
        return result