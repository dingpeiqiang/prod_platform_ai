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
        "inputParams": ParamSchema(type="list", required=False, default=[],
                                   description="输入参数列表，格式: [{\"name\": \"参数名\", \"value\": \"变量引用或值\"}]"),
        "outputParams": ParamSchema(type="list", required=False, default=[],
                                    description="输出参数列表，格式: [{\"name\": \"字段名\", \"source\": \"来源路径\", \"type\": \"输出类型\", \"description\": \"字段描述\"}]")
    }
    output_fields = {}  # 完全通过配置定义输出字段，不写死
    has_dynamic_output = True

    async def execute(self, execution: DelegateExecution) -> None:
        prompt = execution.get("prompt")
        model = execution.get("model", "qwen-plus")
        temperature = execution.get("temperature", 0.7)

        # 解析输入参数
        input_params = execution.get("inputParams", [])
        if isinstance(input_params, list):
            for param in input_params:
                if isinstance(param, dict) and "name" in param:
                    execution.set(param["name"], param.get("value"))

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

            response_json = self._try_parse_json(response)

            # 获取配置的输出字段
            output_params = execution.get("outputParams", [])

            if isinstance(output_params, list) and output_params:
                # 有配置化输出字段，完全按照配置输出
                self._set_dynamic_outputs(output_params, response_json, execution, response, model)
            else:
                # 无配置化输出字段，默认输出基本字段
                execution.set("response", response)
                execution.set("response_json", response_json)
                execution.set("model", model)

            self._log_output(success=True, model=model,
                           response=response[:100] + "..." if len(response) > 100 else response)

        except Exception as e:
            error_msg = str(e)
            self._log_output(success=False, error=error_msg)

            # 获取配置的输出字段
            output_params = execution.get("outputParams", [])

            if isinstance(output_params, list) and output_params:
                for field_config in output_params:
                    if isinstance(field_config, dict) and "name" in field_config:
                        execution.set(field_config["name"], None)
            else:
                execution.set("response", "")
                execution.set("response_json", {})
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

    def _set_dynamic_outputs(self, output_params: list, response_json: dict, execution: DelegateExecution,
                            response: str = None, model: str = None):
        """根据配置设置动态输出字段

        Args:
            output_params: 输出参数列表，格式: [{"name": "字段名", "source": "来源路径", "type": "输出类型", "description": "字段描述"}]
            response_json: LLM 返回的解析结果（JSON 对象）
            execution: 执行上下文
            response: 原始响应字符串
            model: 模型名称

        输出类型说明：
            - "string": 输出字符串格式（对象转为 JSON 字符串）
            - "object": 输出对象格式（JSON 字符串解析为对象）
            - 其他/空: 保持原始类型
        """
        for field_config in output_params:
            if not isinstance(field_config, dict):
                continue

            field_name = field_config.get("name")
            field_source = field_config.get("source", "")
            field_type = field_config.get("type", "")

            if not field_name:
                continue

            # 根据来源路径获取值
            value = self._get_value_by_source(field_source, response_json, response, model)

            # 根据 type 字段转换输出格式
            value = self._convert_output_type(value, field_type)

            execution.set(field_name, value)
            self._log_processing(f"设置动态输出字段 '{field_name}' (source={field_source}, type={field_type}) = {repr(value)[:50]}")

    def _convert_output_type(self, value: Any, output_type: str) -> Any:
        """根据输出类型转换值的格式

        Args:
            value: 原始值
            output_type: 输出类型 ("string" 或 "object")

        Returns:
            转换后的值
        """
        output_type = output_type.lower().strip() if output_type else ""

        if output_type == "string":
            # 输出字符串格式：对象转为 JSON 字符串
            if isinstance(value, (dict, list)):
                return json.dumps(value, ensure_ascii=False)
            return str(value) if value is not None else ""

        elif output_type == "object":
            # 输出对象格式：字符串如果是 JSON 则解析为对象
            if isinstance(value, str):
                try:
                    return json.loads(value)
                except (json.JSONDecodeError, TypeError):
                    return value
            return value

        # 默认保持原始类型
        return value

    def _get_value_by_source(self, source: str, response_json: dict, response: str, model: str) -> Any:
        """根据来源路径获取值

        支持的来源类型：
        - "response" - 原始响应字符串
        - "model" - 模型名称
        - "response_json" 或空 - 完整的解析结果对象
        - "response_json.field_name" 或 "field_name" - 解析结果中的字段值

        Args:
            source: 来源路径
            response_json: LLM 返回的解析结果
            response: 原始响应字符串
            model: 模型名称

        Returns:
            对应来源的值
        """
        if not source:
            return response_json

        # 支持直接引用特殊字段
        if source == "response":
            return response
        elif source == "model":
            return model
        elif source == "response_json":
            return response_json

        # 支持 response_json.field_name 格式
        if source.startswith("response_json."):
            field_path = source[14:]  # 移除 "response_json." 前缀
            return self._get_value_by_path(response_json, field_path)

        # 默认从 response_json 中获取字段
        return self._get_value_by_path(response_json, source)

    def _get_value_by_path(self, data: dict, path: str) -> Any:
        """根据路径获取嵌套字典中的值

        Args:
            data: 字典数据
            path: 路径，支持点号分隔（如 "data.tariff_code"）

        Returns:
            对应路径的值，不存在返回 None
        """
        if not path or not isinstance(data, dict):
            return None

        keys = path.split('.')
        value = data

        for key in keys:
            if isinstance(value, dict) and key in value:
                value = value[key]
            else:
                return None

        return value

    def get_dynamic_outputs(self, config_data: Dict[str, Any]) -> Dict[str, ParamSchema]:
        """返回动态输出 schema

        根据配置的 outputParams 返回动态输出字段的 schema。

        Args:
            config_data: 配置数据

        Returns:
            动态输出的字段名 → ParamSchema 映射
        """
        outputs = {}
        output_params = config_data.get("outputParams", [])

        if isinstance(output_params, list):
            for field_config in output_params:
                if isinstance(field_config, dict):
                    field_name = field_config.get("name")
                    field_description = field_config.get("description", f"动态输出字段: {field_name}")
                    if field_name:
                        outputs[field_name] = ParamSchema(type="any", description=field_description)

        return outputs