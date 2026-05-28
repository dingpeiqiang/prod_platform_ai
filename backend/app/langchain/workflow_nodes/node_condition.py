"""
条件分支节点

输入参数：
- branches: 分支配置列表
  - type: 分支类型 (if/else)
  - handle: 分支标识
  - conditions: 条件列表
    - variable: 变量名（支持节点ID+路径格式，如 code-211a6b31.output.tariff_code）
    - operator: 操作符 (==, !=, >, <, >=, <=, contains, not_contains)
    - valueType: 值类型 (constant/reference)
    - value: 值

输出结果：
- condition_result: 条件判断结果 (True/False)
- matched_handle: 匹配的分支标识
"""
from typing import Dict, Any, List, Optional
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class ConditionNode(WorkflowNode):
    """条件分支节点"""

    name = "workflow.condition"
    display_name = "条件分支"
    description = "根据条件表达式执行不同的分支"
    config_fields = {
        "branches": ParamSchema(type="list", required=True, description="分支配置列表"),
    }
    output_fields = {
        "condition_result": ParamSchema(type="bool", description="条件判断结果"),
        "matched_handle": ParamSchema(type="str", description="匹配的分支标识"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        """执行条件判断"""
        context = execution.context
        
        branches = execution.get("branches", [])
        
        self._log_input(branches=branches)
        
        result, matched_handle = await self._evaluate_branches(branches, context)
        
        execution.set("condition_result", result)
        execution.set("matched_handle", matched_handle)
        
        self._log_output(condition_result=result, matched_handle=matched_handle)

    async def _evaluate_branches(self, branches: List[Dict[str, Any]], context) -> tuple:
        """评估 branches 格式的条件"""
        for branch_index, branch in enumerate(branches):
            branch_type = branch.get("type", "")
            conditions = branch.get("conditions", [])
            
            branch_handle = branch.get("handle")
            if not branch_handle:
                if branch_type == "else":
                    branch_handle = "branch_else"
                else:
                    branch_handle = f"branch_{branch_index}"
            
            if branch_type == "else":
                return True, branch_handle
            
            all_conditions_met = True
            if conditions:
                for condition in conditions:
                    var_name = condition.get("variable", "")
                    operator = condition.get("operator", "==")
                    value_type = condition.get("valueType", "input")
                    value = condition.get("value", "")
                    
                    if not var_name or not operator:
                        all_conditions_met = False
                        continue
                    
                    # 获取左操作数（支持节点ID+路径格式）
                    left_operand = self._get_variable_value(var_name, context)
                    
                    # 获取右操作数
                    if value_type == "reference":
                        right_operand = self._get_variable_value(value, context)
                    else:
                        right_operand = value
                    
                    # 执行条件判断
                    if not self._evaluate_condition(left_operand, operator, right_operand):
                        all_conditions_met = False
                        break
            
            if all_conditions_met:
                return True, branch_handle
        
        return False, "branch_0"

    def _get_variable_value(self, var_path: str, context) -> Any:
        """获取变量值，支持多种引用格式：
        
        1. 普通变量名：直接从上下文获取
        2. 节点ID + 输出路径：如 "code-211a6b31.output.tariff_code"
           从指定节点的输出中获取值
        """
        if not var_path:
            return ""
        
        # 尝试从上下文直接获取
        value = context.get_variable(var_path, None)
        if value is not None:
            return value
        
        # 尝试从前一个节点输出获取
        prev_output = self._get_previous_output(context)
        if isinstance(prev_output, dict) and var_path in prev_output:
            return prev_output[var_path]
        
        # 检查是否是节点ID + 输出路径格式（如 "code-211a6b31.output.tariff_code"）
        # 节点ID通常包含短横线，这是区分普通变量和节点引用的特征
        if '-' in var_path:
            parts = var_path.split('.', 1)
            if len(parts) >= 2:
                node_id = parts[0]
                rest_path = parts[1]
                
                # 从节点输出中查找
                node_output = context.get_node_output(node_id) if hasattr(context, 'get_node_output') else None
                if not node_output:
                    # 尝试从 step_results 中获取
                    node_output = context.step_results.get(node_id)
                
                if node_output:
                    # 支持新的输出格式 {'output': value}
                    if isinstance(node_output, dict) and "output" in node_output:
                        node_output = node_output["output"]
                    
                    path_parts = rest_path.split('.')
                    current_value = node_output
                    for part in path_parts:
                        if isinstance(current_value, dict) and part in current_value:
                            current_value = current_value[part]
                        elif hasattr(current_value, part):
                            current_value = getattr(current_value, part)
                        else:
                            current_value = None
                            break
                    
                    if current_value is not None:
                        return current_value
        
        return ""

    def _get_previous_output(self, context) -> Dict[str, Any]:
        """获取前一个节点的输出"""
        step_results = getattr(context, 'step_results', {})
        if step_results:
            steps = list(step_results.keys())
            if steps:
                last_step_id = steps[-1]
                return step_results.get(last_step_id, {})
        return {}

    def _evaluate_condition(self, left, operator, right) -> bool:
        """评估条件表达式"""
        try:
            if isinstance(left, str) and left.replace('.', '').isdigit():
                left = float(left) if '.' in left else int(left)
            if isinstance(right, str) and right.replace('.', '').isdigit():
                right = float(right) if '.' in right else int(right)
            
            if operator == "==":
                return left == right
            elif operator == "!=":
                return left != right
            elif operator == ">":
                return left > right
            elif operator == "<":
                return left < right
            elif operator == ">=":
                return left >= right
            elif operator == "<=":
                return left <= right
            elif operator == "contains":
                return str(right) in str(left)
            elif operator == "not_contains":
                return str(right) not in str(left)
            elif operator == "starts_with":
                return str(left).startswith(str(right))
            elif operator == "ends_with":
                return str(left).endswith(str(right))
            elif operator == "is_empty":
                return str(left) == ""
            elif operator == "not_empty":
                return str(left) != ""
            elif operator == "is_true":
                return bool(left)
            elif operator == "is_false":
                return not bool(left)
            else:
                return True
        except Exception:
            return False