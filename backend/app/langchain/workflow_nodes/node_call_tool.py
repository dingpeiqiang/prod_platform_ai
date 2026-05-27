"""
工具调用节点

输入参数：
- tool_name: 工具名称（必填）
- tool_type: 工具类型（可选）
- params: 工具参数（可选）

输出结果：
- result: 工具执行结果
- tool_name: 使用的工具名称
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node
import json


@register_node
class CallToolNode(WorkflowNode):
    """工具调用节点"""
    
    name = "workflow.call_tool"
    description = "调用工具执行特定操作"
    inputs = {
        "tool_name": {"type": "str", "required": True, "description": "工具名称"},
        "tool_type": {"type": "str", "required": False, "description": "工具类型"},
        "params": {"type": "dict", "required": False, "description": "工具参数", "default": {}}
    }
    outputs = {
        "result": {"type": "any", "description": "工具执行结果"},
        "tool_name": {"type": "str", "description": "使用的工具名称"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        tool_name = kwargs.get("tool_name", "")
        tool_type = kwargs.get("tool_type", "")
        params = kwargs.get("params", {})
        
        # 记录输入信息
        self._log_input(tool_name=tool_name, tool_type=tool_type, params=json.dumps(params, ensure_ascii=False))
        
        # 构建处理逻辑描述
        processing = f"通过 ToolHub 调用工具 '{tool_name}'，传入参数 {list(params.keys())}"
        self._log_processing(processing)
        
        try:
            from app.mcp_tools.tool_hub import get_toolhub
            tool_hub = get_toolhub()
            
            result = tool_hub.execute_sync(tool_name, params)
            
            output = {
                "success": True,
                "result": result,
                "tool_name": tool_name,
                "input": {"tool_name": tool_name, "tool_type": tool_type, "params": params},
                "processing": processing,
                "output": {"result": result, "tool_name": tool_name}
            }
            
            # 记录输出信息
            self._log_output(success=True, tool_name=tool_name, result=str(result))
            
            return output
            
        except Exception as e:
            error_msg = str(e)
            self._log_output(success=False, error=error_msg)
            
            return {
                "success": False,
                "error": error_msg,
                "input": {"tool_name": tool_name, "tool_type": tool_type, "params": params},
                "processing": processing,
                "output": {"error": error_msg}
            }