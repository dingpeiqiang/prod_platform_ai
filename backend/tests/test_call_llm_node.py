"""
测试 CallLLMNode 节点的输出字段
"""
import pytest
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