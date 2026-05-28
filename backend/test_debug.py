import sys
sys.path.insert(0, '.')

from app.langchain.workflow_engine import WorkflowEngine

class MockContext:
    def __init__(self, inputs=None, outputs=None, step_results=None):
        self.inputs = inputs or {}
        self.outputs = outputs or {}
        self.step_results = step_results or {}

engine = WorkflowEngine()

# 模拟用户日志中的场景
context = MockContext(
    step_results={
        'code-211a6b31': {'json_string': '{"tariff_code": ""}', 'tariff_code': ''}
    }
)

# 用户的条件表达式
expression = "(code-211a6b31.output.tariff_code != '')"

print(f"节点输出: {context.step_results}")
print(f"条件表达式: {expression}")

# 测试变量解析
result = engine._eval_expression(expression, context)
print(f"表达式计算结果: {result}")

# 测试直接字段访问
expression2 = "(code-211a6b31.tariff_code != '')"
result2 = engine._eval_expression(expression2, context)
print(f"\n直接字段访问表达式: {expression2}")
print(f"表达式计算结果: {result2}")
