"""
LLM 调用节点

支持动态配置输入输出变量：
- 输入变量：从 kwargs 动态获取，必须包含 prompt
- 输出变量：根据 self.outputs 定义动态生成

输出处理逻辑：
- 类型为 'str' 时，直接返回原始响应
- 类型为 'object' 或 'array' 时，解析 JSON 并返回
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node
import json


@register_node
class CallLLMNode(WorkflowNode):
    """LLM 调用节点
    
    支持动态配置输入输出变量：
    - 输入：从 kwargs 动态获取参数
    - 输出：根据 self.outputs 定义动态生成
    
    输出类型处理：
    - str: 返回原始 LLM 响应字符串
    - object/array/dict/list: 解析 JSON 后返回
    """
    
    name = "workflow.call_llm"
    description = "调用大语言模型生成响应"
    inputs = {}  # 动态配置，由工作流定义指定
    outputs = {}  # 动态配置，由工作流定义指定
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        # 动态获取参数，支持自定义参数名
        prompt = kwargs.get("prompt", kwargs.get("input", ""))
        model = kwargs.get("model", kwargs.get("llm_model", "qwen-plus"))
        temperature = kwargs.get("temperature", kwargs.get("temp", 0.7))
        
        # 记录输入信息（记录所有传入的参数）
        self._log_input(**kwargs)
        
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
            
            # 清理响应（移除 markdown 代码块标记）
            cleaned_response = self._clean_response(response)
            
            # 根据动态配置的输出定义处理响应
            output = await self._process_output_by_type(response, cleaned_response)
            output["model"] = model
            
            result = {
                "success": True,
                "response": response,
                "model": model,
                "input": kwargs,  # 记录所有输入参数
                "processing": processing,
                "output": output
            }
            
            # 记录输出信息
            self._log_output(success=True, model=model, response=response[:100] + "..." if len(response) > 100 else response)
            
            return result
            
        except Exception as e:
            error_msg = str(e)
            self._log_output(success=False, error=error_msg)
            
            return {
                "success": False,
                "error": error_msg,
                "input": kwargs,  # 记录所有输入参数
                "processing": processing,
                "output": {"error": error_msg}
            }
    
    def _clean_response(self, response: str) -> str:
        """清理响应，移除 markdown 代码块标记"""
        cleaned = response.strip()
        
        # 移除 markdown 代码块标记
        if cleaned.startswith("```json"):
            cleaned = cleaned[7:]
        if cleaned.startswith("```"):
            cleaned = cleaned[3:]
        if cleaned.endswith("```"):
            cleaned = cleaned[:-3]
        
        return cleaned.strip()
    
    async def _process_output_by_type(self, raw_response: str, cleaned_response: str) -> Dict[str, Any]:
        """根据动态配置的输出类型处理响应
        
        - 如果输出类型是 'str'，直接返回原始响应
        - 如果输出类型是 'object' 或 'array'，解析 JSON 并返回
        
        输出字段由 self.outputs 动态定义
        """
        output = {"response": raw_response}
        
        # 尝试解析 JSON（用于 object/array 类型的输出）
        parsed_response = None
        try:
            parsed_response = json.loads(cleaned_response)
        except:
            parsed_response = None
        
        # 如果没有配置输出定义，使用默认输出
        if not self.outputs:
            output["response"] = raw_response
            if parsed_response is not None:
                output["parsed"] = parsed_response
            return output
        
        # 根据动态配置的 outputs 定义处理输出
        for output_name, output_def in self.outputs.items():
            output_type = output_def.get("type", "str")
            output_source = output_def.get("source", "response")  # 支持指定数据源
            
            if output_type == "str":
                # 字符串类型返回原始响应
                output[output_name] = raw_response
            elif output_type in ("object", "array", "dict", "list"):
                # 对象或数组类型返回解析后的 JSON
                if parsed_response is not None:
                    output[output_name] = parsed_response
                else:
                    # 如果无法解析，返回空对象/数组
                    output[output_name] = {} if output_type in ("object", "dict") else []
        
        return output