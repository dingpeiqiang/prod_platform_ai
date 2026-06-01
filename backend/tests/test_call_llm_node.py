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
        """测试输出字段定义"""
        node = CallLLMNode()

        assert "response" in node.output_fields
        assert "parsed_result" in node.output_fields
        assert "model" in node.output_fields

        assert node.output_fields["response"].type == "str"
        assert node.output_fields["parsed_result"].type == "object"
        assert node.output_fields["model"].type == "str"

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
        assert "response" in schema["output_fields"]
        assert schema["has_dynamic_output"] is False

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