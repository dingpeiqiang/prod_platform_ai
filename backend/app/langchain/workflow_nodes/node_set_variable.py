"""
设置变量节点

输入参数：
- variable_name: 变量名称（必填）
- variable_value: 变量值（必填）

输出结果：
- variable_name: 变量名称
- variable_value: 变量值
- previous_value: 变量之前的值
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class SetVariableNode(WorkflowNode):
    """设置变量节点"""
    
    name = "workflow.set_variable"
    description = "设置工作流上下文变量"
    inputs = {
        "variable_name": {"type": "str", "required": True, "description": "变量名称"},
        "variable_value": {"type": "any", "required": True, "description": "变量值"}
    }
    outputs = {
        "variable_name": {"type": "str", "description": "变量名称"},
        "variable_value": {"type": "any", "description": "变量值"},
        "previous_value": {"type": "any", "description": "变量之前的值"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        variable_name = kwargs.get("variable_name", "")
        variable_value = kwargs.get("variable_value", "")
        
        # 记录输入信息
        self._log_input(variable_name=variable_name, variable_value=variable_value)
        
        # 获取之前的值
        previous_value = context.outputs.get(variable_name, "（未设置）")
        
        # 构建处理逻辑描述
        processing = f"设置变量 '{variable_name}'，原值: {previous_value}，新值: {variable_value}"
        self._log_processing(processing)
        
        if not variable_name:
            error_msg = "variable_name is required"
            self._log_output(success=False, error=error_msg)
            
            return {
                "success": False,
                "error": error_msg,
                "input": {"variable_name": variable_name, "variable_value": variable_value},
                "processing": processing,
                "output": {"error": error_msg}
            }
        
        # 设置变量
        context.outputs[variable_name] = variable_value
        
        result = {
            "success": True,
            "variable_name": variable_name,
            "variable_value": variable_value,
            "previous_value": previous_value,
            "input": {"variable_name": variable_name, "variable_value": variable_value},
            "processing": processing,
            "output": {"variable_name": variable_name, "variable_value": variable_value, "previous_value": previous_value}
        }
        
        # 记录输出信息
        self._log_output(success=True, variable_name=variable_name, variable_value=variable_value, previous_value=previous_value)
        
        return result