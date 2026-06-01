"""
LLM 调用节点
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema
import json
import re


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

        # 渲染模板变量（如 {{user_input}}）
        rendered_prompt = self._render_template(prompt, execution)

        self._log_input(prompt=rendered_prompt[:100] + "..." if len(rendered_prompt) > 100 else rendered_prompt,
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
            response = await chain.ainvoke({"prompt": rendered_prompt})

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

    def _render_template(self, template: str, execution: DelegateExecution) -> str:
        """渲染模板，替换变量引用

        支持的变量引用方式：
        - {{variable_name}} - 标准模板语法

        从 execution context 中查找变量值进行替换。
        """
        if not template:
            return ""

        logger = execution.context._logger if hasattr(execution.context, '_logger') else None

        def replace_var(match):
            var_name = match.group(1).strip()
            # 从 execution context 中获取变量值
            value = execution.get(var_name, "")
            if logger:
                logger.debug(f"[CallLLMNode._render_template] 查找变量 '{var_name}'，找到值: {repr(value)[:50]}")
            if value is None:
                return ""
            return str(value)

        # 支持 {{variable}} 语法（双花括号）
        result = re.sub(r"\{\{([^{}]+)\}\}", replace_var, template)

        return result

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