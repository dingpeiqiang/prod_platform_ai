"""
测试工作流引擎的参数解析功能
"""
import asyncio
from app.langchain.workflow_engine import WorkflowEngine, ExecutionContext, WorkflowDefinition, StepDefinition, StepType

class TestResolveParams:
    """测试参数解析功能"""

    def test_resolve_params_with_node_output(self):
        """测试解析前一个节点输出"""
        engine = WorkflowEngine()
        
        # 创建一个模拟的执行上下文
        context = ExecutionContext(
            workflow_id="test_workflow",
            definition=WorkflowDefinition(id="test", name="test"),
            inputs={"user_input": "test input"},
            outputs={"existing_var": "existing_value"},
            step_results={
                "previous_step": {
                    "success": True,
                    "response": "test response",
                    "tariff_code": "ABC123"
                }
            }
        )
        
        # 测试解析前一个节点输出
        params = {"variable_value": "{{__node_output__}}"}
        resolved = engine._resolve_params(params, context)
        
        assert resolved["variable_value"] == {"success": True, "response": "test response", "tariff_code": "ABC123"}

    def test_resolve_params_with_output_field(self):
        """测试解析前一个节点输出的字段"""
        engine = WorkflowEngine()
        
        context = ExecutionContext(
            workflow_id="test_workflow",
            definition=WorkflowDefinition(id="test", name="test"),
            inputs={},
            outputs={},
            step_results={
                "previous_step": {
                    "success": True,
                    "tariff_code": "ABC123",
                    "message": "test message"
                }
            }
        )
        
        # 测试解析前一个节点输出的字段
        params = {"variable_value": "{{__output__.tariff_code}}"}
        resolved = engine._resolve_params(params, context)
        
        assert resolved["variable_value"] == "ABC123"

    def test_resolve_params_with_output_field_not_found(self):
        """测试解析不存在的字段"""
        engine = WorkflowEngine()
        
        context = ExecutionContext(
            workflow_id="test_workflow",
            definition=WorkflowDefinition(id="test", name="test"),
            inputs={},
            outputs={},
            step_results={
                "previous_step": {"success": True, "tariff_code": "ABC123"}
            }
        )
        
        # 测试解析不存在的字段
        params = {"variable_value": "{{__output__.nonexistent_field}}"}
        resolved = engine._resolve_params(params, context)
        
        # 当字段不存在时，应该返回原始表达式
        assert resolved["variable_value"] == "{{__output__.nonexistent_field}}"

    def test_resolve_params_with_existing_variable(self):
        """测试解析已存在的变量"""
        engine = WorkflowEngine()
        
        context = ExecutionContext(
            workflow_id="test_workflow",
            definition=WorkflowDefinition(id="test", name="test"),
            inputs={"input_var": "input_value"},
            outputs={"output_var": "output_value"},
            step_results={}
        )
        
        # 测试从 outputs 获取变量
        params = {"value1": "{{output_var}}"}
        resolved = engine._resolve_params(params, context)
        assert resolved["value1"] == "output_value"
        
        # 测试从 inputs 获取变量
        params = {"value2": "{{input_var}}"}
        resolved = engine._resolve_params(params, context)
        assert resolved["value2"] == "input_value"
        
        # 测试不存在的变量返回原始表达式
        params = {"value3": "{{nonexistent}}"}
        resolved = engine._resolve_params(params, context)
        assert resolved["value3"] == "{{nonexistent}}"

    def test_resolve_params_without_step_results(self):
        """测试没有前一个节点结果时的解析"""
        engine = WorkflowEngine()
        
        context = ExecutionContext(
            workflow_id="test_workflow",
            definition=WorkflowDefinition(id="test", name="test"),
            inputs={},
            outputs={},
            step_results={}
        )
        
        # 测试没有前一个节点时解析 __node_output__
        params = {"variable_value": "{{__node_output__}}"}
        resolved = engine._resolve_params(params, context)
        
        assert resolved["variable_value"] == {}

    def test_resolve_params_with_literal_value(self):
        """测试解析字面量值"""
        engine = WorkflowEngine()
        
        context = ExecutionContext(
            workflow_id="test_workflow",
            definition=WorkflowDefinition(id="test", name="test"),
            inputs={},
            outputs={},
            step_results={}
        )
        
        # 测试字面量值不被解析
        params = {
            "string_value": "hello",
            "int_value": 123,
            "dict_value": {"key": "value"},
            "list_value": [1, 2, 3]
        }
        resolved = engine._resolve_params(params, context)
        
        assert resolved["string_value"] == "hello"
        assert resolved["int_value"] == 123
        assert resolved["dict_value"] == {"key": "value"}
        assert resolved["list_value"] == [1, 2, 3]

    def test_resolve_params_with_field_access(self):
        """测试解析变量的字段访问（如 {{parsed_result.tariff_code}}）"""
        engine = WorkflowEngine()
        
        context = ExecutionContext(
            workflow_id="test_workflow",
            definition=WorkflowDefinition(id="test", name="test"),
            inputs={},
            outputs={
                "parsed_result": {
                    "tariff_code": "ABC123",
                    "name": "测试套餐",
                    "details": {
                        "price": 99.9,
                        "period": "monthly"
                    }
                }
            },
            step_results={}
        )
        
        # 测试字段访问
        params = {"variable_value": "{{parsed_result.tariff_code}}"}
        resolved = engine._resolve_params(params, context)
        assert resolved["variable_value"] == "ABC123"

    def test_resolve_params_with_nested_field_access(self):
        """测试解析嵌套字段访问"""
        engine = WorkflowEngine()
        
        context = ExecutionContext(
            workflow_id="test_workflow",
            definition=WorkflowDefinition(id="test", name="test"),
            inputs={},
            outputs={
                "parsed_result": {
                    "tariff_code": "ABC123",
                    "details": {
                        "price": 99.9,
                        "period": "monthly"
                    }
                }
            },
            step_results={}
        )
        
        # 测试嵌套字段访问
        params = {"variable_value": "{{parsed_result.details.price}}"}
        resolved = engine._resolve_params(params, context)
        assert resolved["variable_value"] == 99.9

    def test_resolve_params_with_nonexistent_field(self):
        """测试解析不存在的字段"""
        engine = WorkflowEngine()
        
        context = ExecutionContext(
            workflow_id="test_workflow",
            definition=WorkflowDefinition(id="test", name="test"),
            inputs={},
            outputs={
                "parsed_result": {"tariff_code": "ABC123"}
            },
            step_results={}
        )
        
        # 测试不存在的字段
        params = {"variable_value": "{{parsed_result.nonexistent_field}}"}
        resolved = engine._resolve_params(params, context)
        assert resolved["variable_value"] == "{{parsed_result.nonexistent_field}}"

    def test_resolve_params_with_nonexistent_variable(self):
        """测试解析不存在的变量"""
        engine = WorkflowEngine()
        
        context = ExecutionContext(
            workflow_id="test_workflow",
            definition=WorkflowDefinition(id="test", name="test"),
            inputs={},
            outputs={},
            step_results={}
        )
        
        # 测试不存在的变量
        params = {"variable_value": "{{nonexistent_var.field}}"}
        resolved = engine._resolve_params(params, context)
        assert resolved["variable_value"] == "{{nonexistent_var.field}}"

    def test_resolve_field_path(self):
        """测试字段路径解析函数"""
        engine = WorkflowEngine()
        
        # 测试字典访问
        obj = {"user": {"name": "张三", "age": 25}}
        assert engine._resolve_field_path(obj, "user.name") == "张三"
        assert engine._resolve_field_path(obj, "user.age") == 25
        assert engine._resolve_field_path(obj, "nonexistent") is None
        
        # 测试嵌套字典
        obj = {"a": {"b": {"c": 123}}}
        assert engine._resolve_field_path(obj, "a.b.c") == 123
        
        # 测试列表索引（虽然字典不支持索引，但代码应该能处理）
        obj = {"list": [1, 2, 3]}
        # 字典的 key 是字符串，所以 "list.0" 会查找 key="0"，不存在
        assert engine._resolve_field_path(obj, "list.0") is None

class TestWorkflowConverter:
    """测试工作流转换器的变量赋值节点兼容性"""

    def test_convert_variable_node_with_varName(self):
        """测试前端使用 varName/varValue 的变量节点转换"""
        from app.langchain.workflow_converter import WorkflowConverter
        
        frontend_data = {
            "nodes": [
                {
                    "id": "var-1",
                    "type": "variable",
                    "data": {
                        "label": "设置变量",
                        "varName": "tariff_code",
                        "varValue": "{{__output__.tariff_code}}"
                    }
                }
            ],
            "edges": []
        }
        
        backend_config = WorkflowConverter.convert(frontend_data, "test_workflow", "测试工作流")
        
        assert "var-1" in backend_config["steps"]
        step = backend_config["steps"]["var-1"]
        assert step["action_params"]["variable_name"] == "tariff_code"
        assert step["action_params"]["variable_value"] == "{{__output__.tariff_code}}"

    def test_convert_variable_node_with_variableName(self):
        """测试前端使用 variableName/variableValue 的变量节点转换"""
        from app.langchain.workflow_converter import WorkflowConverter
        
        frontend_data = {
            "nodes": [
                {
                    "id": "var-1",
                    "type": "variable",
                    "data": {
                        "label": "设置变量",
                        "variableName": "tariff_code",
                        "variableValue": "{{__output__.tariff_code}}"
                    }
                }
            ],
            "edges": []
        }
        
        backend_config = WorkflowConverter.convert(frontend_data, "test_workflow", "测试工作流")
        
        assert "var-1" in backend_config["steps"]
        step = backend_config["steps"]["var-1"]
        assert step["action_params"]["variable_name"] == "tariff_code"
        assert step["action_params"]["variable_value"] == "{{__output__.tariff_code}}"


if __name__ == "__main__":
    import pytest
    pytest.main([__file__, "-v"])