"""
测试 CallLLMNode 节点的输出字段
"""
import pytest
from unittest.mock import MagicMock
from app.langchain.workflow_nodes.node_call_llm import CallLLMNode
from app.langchain.workflow_nodes import ParamSchema


class TestCallLLMNode:
    """测试 CallLLMNode 节点"""

    def test_output_fields_definition(self):
        """测试输出字段定义 - 现在完全通过配置定义"""
        node = CallLLMNode()

        # 输出字段现在完全通过配置定义，不写死
        assert node.output_fields == {}
        assert node.has_dynamic_output is True

    def test_config_fields_definition(self):
        """测试配置字段定义"""
        node = CallLLMNode()

        assert "prompt" in node.config_fields
        assert "model" in node.config_fields
        assert "temperature" in node.config_fields

        assert node.config_fields["prompt"].required is True
        assert node.config_fields["model"].default == "qwen-plus"
        assert node.config_fields["temperature"].default == 0.7

    def test_palette_schema(self):
        """测试 palette schema"""
        schema = CallLLMNode.get_palette_schema()

        assert schema["name"] == "workflow.call_llm"
        assert "prompt" in schema["config_fields"]
        assert "outputParams" in schema["config_fields"]
        assert schema["output_fields"] == {}  # 输出字段完全配置化
        assert schema["has_dynamic_output"] is True

    def test_try_parse_json_with_code_block(self):
        """测试 JSON 解析 - 移除代码块标记"""
        node = CallLLMNode()

        response = "```json\n{\"tariff_code\": \"ABC123\"}\n```"
        parsed = node._try_parse_json(response)
        assert parsed == {"tariff_code": "ABC123"}

        response = "{\"tariff_code\": \"ABC123\"}"
        parsed = node._try_parse_json(response)
        assert parsed == {"tariff_code": "ABC123"}

    def test_try_parse_json_with_invalid_json(self):
        """测试 JSON 解析 - 无效 JSON"""
        node = CallLLMNode()

        response = "这不是有效的 JSON"
        parsed = node._try_parse_json(response)
        assert parsed == {}

    def test_try_parse_json_with_empty(self):
        """测试 JSON 解析 - 空对象"""
        node = CallLLMNode()

        response = "```json\n{}\n```"
        parsed = node._try_parse_json(response)
        assert parsed == {}

    def test_render_template_with_double_braces(self):
        """测试模板渲染 - 双花括号语法 {{variable}}"""
        node = CallLLMNode()

        # 创建模拟的 execution 对象
        mock_execution = MagicMock()
        mock_execution.get.side_effect = lambda name, default=None: {
            "user_input": "P12345",
            "name": "测试用户"
        }.get(name, default)

        template = "从用户输入中提取套餐编码：{{user_input}}"
        rendered = node._render_template(template, mock_execution)
        assert rendered == "从用户输入中提取套餐编码：P12345"

    def test_render_template_with_single_brace_unchanged(self):
        """测试模板渲染 - 单花括号不被替换（避免与JSON冲突）"""
        node = CallLLMNode()

        mock_execution = MagicMock()
        mock_execution.get.side_effect = lambda name, default=None: {
            "user_input": "P67890"
        }.get(name, default)

        # 单花括号语法不再支持，保持原样
        template = "用户输入：{user_input}"
        rendered = node._render_template(template, mock_execution)
        assert rendered == "用户输入：{user_input}"

    def test_render_template_with_multiple_variables(self):
        """测试模板渲染 - 多个变量"""
        node = CallLLMNode()

        mock_execution = MagicMock()
        mock_execution.get.side_effect = lambda name, default=None: {
            "tariff_code": "P123",
            "action": "查询"
        }.get(name, default)

        template = "套餐编码：{{tariff_code}}，操作类型：{{action}}"
        rendered = node._render_template(template, mock_execution)
        assert rendered == "套餐编码：P123，操作类型：查询"

    def test_render_template_with_nonexistent_variable(self):
        """测试模板渲染 - 不存在的变量"""
        node = CallLLMNode()

        mock_execution = MagicMock()
        mock_execution.get.return_value = ""

        template = "用户输入：{{nonexistent_var}}"
        rendered = node._render_template(template, mock_execution)
        assert rendered == "用户输入："

    def test_render_template_empty(self):
        """测试模板渲染 - 空模板"""
        node = CallLLMNode()

        mock_execution = MagicMock()
        rendered = node._render_template("", mock_execution)
        assert rendered == ""

    def test_render_template_with_json_format(self):
        """测试模板渲染 - 包含 JSON 格式的提示词"""
        node = CallLLMNode()

        mock_execution = MagicMock()
        mock_execution.get.side_effect = lambda name, default=None: {
            "user_input": "用户查询套餐 P12345"
        }.get(name, default)

        template = """从用户输入中提取套餐编码和操作类型：
 
 用户输入：{{user_input}} 
 提取套餐编码结果：请输出JSON格式： 
 { 
   "tariff_code": "提取的套餐编码，如果没有则为空字符串" 
 }"""
        
        rendered = node._render_template(template, mock_execution)
        
        # 验证变量被正确替换
        assert "{{user_input}}" not in rendered
        assert "用户查询套餐 P12345" in rendered
        
        # 验证 JSON 格式没有被破坏（关键：单花括号的 JSON 不应该被替换）
        assert '{' in rendered
        assert '}' in rendered
        assert '"tariff_code"' in rendered
        assert '提取的套餐编码，如果没有则为空字符串' in rendered

    def test_get_dynamic_outputs_with_config(self):
        """测试动态输出配置"""
        node = CallLLMNode()

        config_data = {
            "outputParams": [
                {"name": "input_json", "source": "response_json", "description": "解析后的JSON结果"},
                {"name": "tariff_code", "source": "tariff_code", "description": "套餐编码"}
            ]
        }

        outputs = node.get_dynamic_outputs(config_data)

        assert "input_json" in outputs
        assert "tariff_code" in outputs
        assert outputs["input_json"].description == "解析后的JSON结果"
        assert outputs["tariff_code"].description == "套餐编码"

    def test_get_value_by_source(self):
        """测试根据来源获取值"""
        node = CallLLMNode()

        response_json = {"tariff_code": "P12345", "name": "测试套餐"}
        response = "原始响应"
        model = "qwen-plus"

        # 测试获取完整解析结果
        assert node._get_value_by_source("response_json", response_json, response, model) == response_json

        # 测试获取响应字符串
        assert node._get_value_by_source("response", response_json, response, model) == response

        # 测试获取模型名称
        assert node._get_value_by_source("model", response_json, response, model) == model

        # 测试获取解析结果中的字段
        assert node._get_value_by_source("tariff_code", response_json, response, model) == "P12345"

        # 测试 response_json.field_name 格式
        assert node._get_value_by_source("response_json.name", response_json, response, model) == "测试套餐"

        # 测试空来源
        assert node._get_value_by_source("", response_json, response, model) == response_json

        # 测试不存在的字段
        assert node._get_value_by_source("nonexistent", response_json, response, model) is None

    def test_convert_output_type_string(self):
        """测试输出类型转换 - string"""
        node = CallLLMNode()

        # 对象转字符串
        assert node._convert_output_type({"tariff_code": "P123"}, "string") == '{"tariff_code": "P123"}'
        
        # 列表转字符串
        assert node._convert_output_type(["a", "b", "c"], "string") == '["a", "b", "c"]'
        
        # 字符串保持原样
        assert node._convert_output_type("hello", "string") == "hello"
        
        # None 转为空字符串
        assert node._convert_output_type(None, "string") == ""

    def test_convert_output_type_object(self):
        """测试输出类型转换 - object"""
        node = CallLLMNode()

        # JSON 字符串转对象
        assert node._convert_output_type('{"tariff_code": "P123"}', "object") == {"tariff_code": "P123"}
        
        # 非 JSON 字符串保持原样
        assert node._convert_output_type("hello", "object") == "hello"
        
        # 对象保持原样
        assert node._convert_output_type({"key": "value"}, "object") == {"key": "value"}

    def test_convert_output_type_default(self):
        """测试输出类型转换 - 默认（保持原始类型）"""
        node = CallLLMNode()

        assert node._convert_output_type({"key": "value"}, "") == {"key": "value"}
        assert node._convert_output_type("hello", None) == "hello"
        assert node._convert_output_type("hello", "unknown") == "hello"

    def test_set_dynamic_outputs_with_type_config(self):
        """测试动态输出配置（包含 type 字段）"""
        node = CallLLMNode()
        
        # 模拟执行上下文
        class MockExecution:
            def __init__(self):
                self._variables = {}
            
            def set(self, name, value):
                self._variables[name] = value
            
            def get(self, name, default=None):
                return self._variables.get(name, default)
        
        execution = MockExecution()
        
        output_params = [
            {"name": "input_json", "source": "response_json", "type": "string", "description": "JSON字符串"},
            {"name": "tariff_code", "source": "tariff_code", "type": "string", "description": "套餐编码"},
            {"name": "raw_response", "source": "response", "type": "object", "description": "原始响应"},
            {"name": "model_name", "source": "model", "type": "string", "description": "模型名称"}
        ]
        
        response_json = {"tariff_code": "P12345", "name": "测试套餐"}
        response = '{"tariff_code": "P12345"}'
        model = "qwen-plus"
        
        node._set_dynamic_outputs(output_params, response_json, execution, response, model)
        
        # 验证输出
        assert "input_json" in execution._variables
        assert "tariff_code" in execution._variables
        assert "raw_response" in execution._variables
        assert "model_name" in execution._variables
        
        # 验证类型转换
        assert isinstance(execution._variables["input_json"], str)  # object -> string
        assert isinstance(execution._variables["tariff_code"], str)  # str -> str
        assert isinstance(execution._variables["raw_response"], dict)  # JSON string -> object
        assert isinstance(execution._variables["model_name"], str)  # str -> str
        
        assert execution._variables["input_json"] == '{"tariff_code": "P12345", "name": "\u6d4b\u8bd5\u5957\u9910"}'