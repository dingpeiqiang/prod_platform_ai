"""
LLM 调用节点
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema
import json


@register_node
class CallLLMNode(WorkflowNode):
    """LLM 调用节点

    调用大语言模型生成响应，支持多种输出格式。
    """

    name = "workflow.call_llm"
    display_name = "LLM 调用"
    description = "调用大语言模型生成响应"
    config_fields = {
        "prompt": ParamSchema(type="str", required=True, description="提示词"),
        "model": ParamSchema(type="str", required=False, description="模型名称", default="qwen-plus"),
        "temperature": ParamSchema(type="float", required=False, description="温度参数", default=0.7),
    }
    output_fields = {
        "response": ParamSchema(type="str", description="原始响应字符串"),
        "parsed_result": ParamSchema(type="object", description="解析后的 JSON 对象"),
        "model": ParamSchema(type="str", description="使用的模型名称"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        prompt = execution.get("prompt")
        model = execution.get("model", "qwen-plus")
        temperature = execution.get("temperature", 0.7)

        self._log_input(prompt=prompt[:100] + "..." if len(prompt) > 100 else prompt,
                       model=model, temperature=temperature)

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

            parsed_result = self._try_parse_json(response)

            execution.set("response", response)
            execution.set("parsed_result", parsed_result)
            execution.set("model", model)

            self._log_output(success=True, model=model,
                           response=response[:100] + "..." if len(response) > 100 else response)

        except Exception as e:
            error_msg = str(e)
            self._log_output(success=False, error=error_msg)

            execution.set("response", "")
            execution.set("parsed_result", {})
            execution.set("model", model)
            execution.set("error", error_msg)
            raise

    def _try_parse_json(self, text: str) -> dict:
        cleaned = text.strip()
        if cleaned.startswith("```json"):
            cleaned = cleaned[7:]
        if cleaned.startswith("```"):
            cleaned = cleaned[3:]
        if cleaned.endswith("```"):
            cleaned = cleaned[:-3]
        cleaned = cleaned.strip()
        try:
            return json.loads(cleaned)
        except Exception:
            return {}