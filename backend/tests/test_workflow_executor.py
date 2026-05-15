"""
工作流执行引擎测试用例
"""

import pytest
import asyncio
from app.engine.workflow_executor import WorkflowExecutor, ExecutionStatus


class TestWorkflowExecutor:
    """工作流执行器测试"""

    def test_simple_workflow(self):
        """测试简单工作流执行"""
        workflow_def = {
            "nodes": [
                {"id": "start-1", "type": "start", "data": {"label": "开始"}},
                {"id": "prompt-1", "type": "prompt", "data": {"label": "提示词", "prompt": "请回答问题：{{question}}"}},
                {"id": "llm-1", "type": "llm", "data": {"label": "LLM", "model": "qwen-vl-plus", "temperature": 0.7}},
                {"id": "end-1", "type": "end", "data": {"label": "结束"}}
            ],
            "edges": [
                {"source": "start-1", "target": "prompt-1"},
                {"source": "prompt-1", "target": "llm-1"},
                {"source": "llm-1", "target": "end-1"}
            ]
        }
        
        executor = WorkflowExecutor(workflow_def)
        context = asyncio.run(executor.execute({"question": "什么是人工智能？"}))
        
        assert context.status == ExecutionStatus.COMPLETED
        assert "prompt" in context.outputs
        assert "llm_output" in context.outputs

    def test_condition_workflow(self):
        """测试条件分支工作流"""
        workflow_def = {
            "nodes": [
                {"id": "start-1", "type": "start", "data": {"label": "开始"}},
                {"id": "condition-1", "type": "condition", "data": {
                    "label": "条件判断",
                    "leftType": "variable",
                    "leftValue": "score",
                    "operator": ">",
                    "rightType": "constant",
                    "rightValue": "60"
                }},
                {"id": "llm-pass", "type": "llm", "data": {"label": "及格处理", "model": "qwen-vl-plus"}},
                {"id": "llm-fail", "type": "llm", "data": {"label": "不及格处理", "model": "qwen-vl-plus"}},
                {"id": "end-1", "type": "end", "data": {"label": "结束"}}
            ],
            "edges": [
                {"source": "start-1", "target": "condition-1"},
                {"source": "condition-1", "target": "llm-pass", "sourceHandle": "true"},
                {"source": "condition-1", "target": "llm-fail", "sourceHandle": "false"},
                {"source": "llm-pass", "target": "end-1"},
                {"source": "llm-fail", "target": "end-1"}
            ]
        }
        
        # 测试及格路径
        executor_pass = WorkflowExecutor(workflow_def)
        context_pass = asyncio.run(executor_pass.execute({"score": 85}))
        assert context_pass.status == ExecutionStatus.COMPLETED
        
        # 测试不及格路径
        executor_fail = WorkflowExecutor(workflow_def)
        context_fail = asyncio.run(executor_fail.execute({"score": 45}))
        assert context_fail.status == ExecutionStatus.COMPLETED

    def test_variable_assignment(self):
        """测试变量赋值节点"""
        workflow_def = {
            "nodes": [
                {"id": "start-1", "type": "start", "data": {"label": "开始"}},
                {"id": "var-1", "type": "variable", "data": {"label": "设置变量", "variableName": "greeting", "variableValue": "Hello World"}},
                {"id": "prompt-1", "type": "prompt", "data": {"label": "使用变量", "prompt": "{{greeting}} - 这是测试"}},
                {"id": "end-1", "type": "end", "data": {"label": "结束"}}
            ],
            "edges": [
                {"source": "start-1", "target": "var-1"},
                {"source": "var-1", "target": "prompt-1"},
                {"source": "prompt-1", "target": "end-1"}
            ]
        }
        
        executor = WorkflowExecutor(workflow_def)
        context = asyncio.run(executor.execute())
        
        assert context.status == ExecutionStatus.COMPLETED
        assert context.get_variable("greeting") == "Hello World"
        assert "Hello World" in context.outputs.get("prompt", "")

    def test_workflow_without_start_node(self):
        """测试缺少开始节点的工作流"""
        workflow_def = {
            "nodes": [
                {"id": "llm-1", "type": "llm", "data": {"label": "LLM"}}
            ],
            "edges": []
        }
        
        executor = WorkflowExecutor(workflow_def)
        context = asyncio.run(executor.execute())
        
        assert context.status == ExecutionStatus.FAILED
        assert "缺少开始节点" in context.error

    def test_empty_workflow(self):
        """测试空工作流"""
        workflow_def = {
            "nodes": [],
            "edges": []
        }
        
        executor = WorkflowExecutor(workflow_def)
        context = asyncio.run(executor.execute())
        
        assert context.status == ExecutionStatus.FAILED

    def test_prompt_template_rendering(self):
        """测试提示词模板渲染"""
        workflow_def = {
            "nodes": [
                {"id": "start-1", "type": "start", "data": {"label": "开始"}},
                {"id": "prompt-1", "type": "prompt", "data": {"label": "模板测试", "prompt": "姓名：{{name}}，年龄：{{age}}，城市：{{city}}"}},
                {"id": "end-1", "type": "end", "data": {"label": "结束"}}
            ],
            "edges": [
                {"source": "start-1", "target": "prompt-1"},
                {"source": "prompt-1", "target": "end-1"}
            ]
        }
        
        executor = WorkflowExecutor(workflow_def)
        context = asyncio.run(executor.execute({"name": "张三", "age": 28, "city": "北京"}))
        
        assert context.status == ExecutionStatus.COMPLETED
        assert "姓名：张三，年龄：28，城市：北京" in context.outputs.get("prompt", "")

    def test_condition_operators(self):
        """测试条件操作符"""
        operators = ["==", "!=", ">", "<", ">=", "<=", "contains", "not_contains"]
        
        for operator in operators:
            workflow_def = {
                "nodes": [
                    {"id": "start-1", "type": "start", "data": {"label": "开始"}},
                    {"id": "condition-1", "type": "condition", "data": {
                        "label": "条件",
                        "leftType": "variable",
                        "leftValue": "testValue",
                        "operator": operator,
                        "rightType": "constant",
                        "rightValue": "test"
                    }},
                    {"id": "end-1", "type": "end", "data": {"label": "结束"}}
                ],
                "edges": [
                    {"source": "start-1", "target": "condition-1"},
                    {"source": "condition-1", "target": "end-1", "sourceHandle": "true"},
                    {"source": "condition-1", "target": "end-1", "sourceHandle": "false"}
                ]
            }
            
            executor = WorkflowExecutor(workflow_def)
            context = asyncio.run(executor.execute({"testValue": "this is a test"}))
            
            assert context.status == ExecutionStatus.COMPLETED


if __name__ == "__main__":
    pytest.main([__file__, "-v"])