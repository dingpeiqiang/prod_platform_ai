"""
执行代码节点
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class ExecuteCodeNode(WorkflowNode):
    """执行代码节点"""

    name = "workflow.execute_code"
    display_name = "执行代码"
    description = "执行 Python 代码片段"
    config_fields = {
        "code": ParamSchema(type="str", required=True, description="Python 代码片段"),
    }
    output_fields = {
        "result": ParamSchema(type="any", description="代码执行结果"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        code = execution.get("code", "")

        self._log_input(code=code[:100] + "..." if len(code) > 100 else code)
        processing = "执行用户提供的 Python 代码片段，提取 result 变量作为输出"
        self._log_processing(processing)

        if not code:
            execution.set("result", None)
            execution.set("error", "Code is required")
            self._log_output(success=False, error="Code is required")
            return

        try:
            exec_locals = {}
            exec(code, {}, exec_locals)
            result = exec_locals.get("result", None)
            execution.set("result", result)
            self._log_output(success=True, result=str(result)[:100] + "..." if len(str(result)) > 100 else result)

        except Exception as e:
            error_msg = str(e)
            execution.set("result", None)
            execution.set("error", error_msg)
            self._log_output(success=False, error=error_msg)