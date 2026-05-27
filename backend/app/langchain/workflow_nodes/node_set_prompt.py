"""
设置提示词节点

输入参数：
- prompt: 提示词内容（必填）
- output_var: 输出变量名（可选，默认 prompt）

输出结果：
- prompt: 提示词内容
- output_var: 输出变量名
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class SetPromptNode(WorkflowNode):
    """设置提示词节点"""
    
    name = "workflow.set_prompt"
    description = "设置提示词变量"
    inputs = {
        "prompt": {"type": "str", "required": True, "description": "提示词内容"},
        "output_var": {"type": "str", "required": False, "description": "输出变量名", "default": "prompt"}
    }
    outputs = {
        "prompt": {"type": "str", "description": "提示词内容"},
        "output_var": {"type": "str", "description": "输出变量名"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        prompt = kwargs.get("prompt", "")
        output_var = kwargs.get("output_var", "prompt")
        
        # 记录输入信息
        self._log_input(prompt=prompt[:100] + "..." if len(prompt) > 100 else prompt, output_var=output_var)
        
        # 记录处理逻辑
        processing = f"将提示词保存到变量 '{output_var}'"
        self._log_processing(processing)
        
        if prompt:
            context.outputs[output_var] = prompt
        
        result = {
            "success": True,
            "prompt": prompt,
            "output_var": output_var,
            "input": {"prompt": prompt, "output_var": output_var},
            "processing": processing,
            "output": {"prompt": prompt, "output_var": output_var}
        }
        
        # 记录输出信息
        self._log_output(success=True, prompt=prompt[:50] + "..." if len(prompt) > 50 else prompt, output_var=output_var)
        
        return result