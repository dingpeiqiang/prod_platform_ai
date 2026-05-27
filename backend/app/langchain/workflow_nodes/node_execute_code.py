"""
执行代码节点

输入参数：
- code: Python 代码片段（必填）

输出结果：
- result: 代码执行结果（提取 result 变量）
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class ExecuteCodeNode(WorkflowNode):
    """执行代码节点"""
    
    name = "workflow.execute_code"
    description = "执行 Python 代码片段"
    inputs = {
        "code": {"type": "str", "required": True, "description": "Python 代码片段"}
    }
    outputs = {
        "result": {"type": "any", "description": "代码执行结果"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        code = kwargs.get("code", "")
        
        # 记录输入信息
        self._log_input(code=code[:100] + "..." if len(code) > 100 else code)
        
        # 记录处理逻辑
        processing = "执行用户提供的 Python 代码片段，提取 result 变量作为输出"
        self._log_processing(processing)
        
        if not code:
            error_msg = "Code is required"
            self._log_output(success=False, error=error_msg)
            
            return {
                "success": False,
                "error": error_msg,
                "input": {"code": code},
                "processing": processing,
                "output": {"error": error_msg}
            }
        
        try:
            exec_locals = {}
            exec(code, {}, exec_locals)
            
            result = exec_locals.get("result", None)
            
            output = {
                "success": True,
                "result": result,
                "input": {"code": code},
                "processing": processing,
                "output": {"result": result}
            }
            
            # 记录输出信息
            self._log_output(success=True, result=str(result)[:100] + "..." if len(str(result)) > 100 else result)
            
            return output
            
        except Exception as e:
            error_msg = str(e)
            self._log_output(success=False, error=error_msg)
            
            return {
                "success": False,
                "error": error_msg,
                "input": {"code": code},
                "processing": processing,
                "output": {"error": error_msg}
            }