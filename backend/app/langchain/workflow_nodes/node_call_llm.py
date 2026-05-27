"""
LLM 调用节点

输入参数：
- prompt: 提示词（必填）
- model: 模型名称（可选，默认 qwen-plus）
- temperature: 温度参数（可选，默认 0.7）

输出结果：
- response: LLM 响应内容
- model: 使用的模型名称
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node
import json


@register_node
class CallLLMNode(WorkflowNode):
    """LLM 调用节点"""
    
    name = "workflow.call_llm"
    description = "调用大语言模型生成响应"
    inputs = {
        "prompt": {"type": "str", "required": True, "description": "提示词"},
        "model": {"type": "str", "required": False, "description": "模型名称", "default": "qwen-plus"},
        "temperature": {"type": "float", "required": False, "description": "温度参数", "default": 0.7}
    }
    outputs = {
        "response": {"type": "str", "description": "LLM 响应内容"},
        "model": {"type": "str", "description": "使用的模型名称"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        prompt = kwargs.get("prompt", "")
        model = kwargs.get("model", "qwen-plus")
        temperature = kwargs.get("temperature", 0.7)
        
        # 记录输入信息
        self._log_input(prompt=prompt, model=model, temperature=temperature)
        
        # 构建处理逻辑描述
        processing = f"使用模型 '{model}'，温度参数 {temperature}，执行 LLM 调用生成响应"
        self._log_processing(processing)
        
        try:
            from app.langchain.llm_wrapper import get_langchain_llm
            llm = get_langchain_llm().llm
            
            from langchain_core.prompts import ChatPromptTemplate
            from langchain_core.output_parsers import StrOutputParser
            
            prompt_template = ChatPromptTemplate.from_messages([
                ("user", "{prompt}")
            ])
            
            chain = prompt_template | llm | StrOutputParser()
            response = await chain.ainvoke({"prompt": prompt})
            
            result = {
                "success": True,
                "response": response,
                "model": model,
                "input": {"prompt": prompt, "model": model, "temperature": temperature},
                "processing": processing,
                "output": {"response": response, "model": model}
            }
            
            # 记录输出信息
            self._log_output(success=True, model=model, response=response)
            
            return result
            
        except Exception as e:
            error_msg = str(e)
            self._log_output(success=False, error=error_msg)
            
            return {
                "success": False,
                "error": error_msg,
                "input": {"prompt": prompt, "model": model, "temperature": temperature},
                "processing": processing,
                "output": {"error": error_msg}
            }