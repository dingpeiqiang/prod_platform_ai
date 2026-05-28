"""
设置提示词节点
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class SetPromptNode(WorkflowNode):
    """设置提示词节点"""

    name = "workflow.set_prompt"
    display_name = "设置提示词"
    description = "设置提示词变量"
    config_fields = {
        "prompt": ParamSchema(type="str", required=True, description="提示词内容"),
        "output_var": ParamSchema(type="str", required=False, description="输出变量名", default="prompt"),
    }
    output_fields = {
        "prompt": ParamSchema(type="str", description="提示词内容"),
        "output_var": ParamSchema(type="str", description="输出变量名"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        prompt = execution.get("prompt", "")
        output_var = execution.get("output_var", "prompt")

        self._log_input(prompt=prompt[:100] + "..." if len(prompt) > 100 else prompt, output_var=output_var)
        processing = f"将提示词保存到变量 '{output_var}'"
        self._log_processing(processing)

        execution.set(output_var, prompt)
        execution.set("prompt", prompt)
        execution.set("output_var", output_var)

        self._log_output(success=True, prompt=prompt[:50] + "..." if len(prompt) > 50 else prompt,
                        output_var=output_var)