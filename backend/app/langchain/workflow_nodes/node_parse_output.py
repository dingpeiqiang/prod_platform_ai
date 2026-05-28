"""
解析输出节点
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema
import re


@register_node
class ParseOutputNode(WorkflowNode):
    """解析输出节点"""

    name = "workflow.parse_output"
    display_name = "解析输出"
    description = "使用正则表达式解析内容"
    config_fields = {
        "content": ParamSchema(type="str", required=True, description="要解析的内容"),
        "pattern": ParamSchema(type="str", required=False, description="正则表达式"),
    }
    output_fields = {
        "parsed": ParamSchema(type="str", description="解析结果"),
        "groups": ParamSchema(type="tuple", description="捕获组"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        content = execution.get("content", "")
        pattern = execution.get("pattern", "")

        self._log_input(content=content[:100] + "..." if len(content) > 100 else content, pattern=pattern)
        processing = f"使用正则表达式 '{pattern}' 解析内容"
        self._log_processing(processing)

        if pattern and content:
            try:
                match = re.search(pattern, content)
                if match:
                    execution.set("parsed", match.group(0))
                    execution.set("groups", match.groups())
                    self._log_output(success=True, parsed=match.group(0), groups=match.groups())
                    return
            except Exception as e:
                execution.set("parsed", "")
                execution.set("groups", ())
                execution.set("error", str(e))
                self._log_output(success=False, error=str(e))
                return

        execution.set("parsed", content)
        execution.set("groups", ())
        self._log_output(success=True, parsed=content[:100] + "..." if len(content) > 100 else content)