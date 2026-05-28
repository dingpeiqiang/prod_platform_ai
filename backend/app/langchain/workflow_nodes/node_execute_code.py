"""
执行代码节点
"""
import re
import json
from typing import Dict, Any, List
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class ExecuteCodeNode(WorkflowNode):
    """执行代码节点"""

    name = "workflow.execute_code"
    display_name = "执行代码"
    description = "执行代码片段（支持 Python 和 JavaScript）"
    config_fields = {
        "code": ParamSchema(type="str", required=True, description="代码片段"),
        "language": ParamSchema(type="str", required=False, default="python", description="编程语言: python 或 javascript"),
        "inputParams": ParamSchema(type="list", required=False, default=[], description="输入参数列表"),
        "outputParams": ParamSchema(type="list", required=False, default=[], description="输出参数列表"),
    }
    output_fields = {
        "result": ParamSchema(type="any", description="代码执行结果"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        """执行代码节点主逻辑"""
        try:
            # ========== 1. 获取输入参数 ==========
            input_params = self._get_input_params(execution)
            
            # ========== 2. 验证并解析依赖 ==========
            resolved_inputs = self._resolve_inputs(input_params, execution)
            
            # ========== 3. 执行代码 ==========
            result = self._execute(resolved_inputs, execution)
            
            # ========== 4. 生成输出 ==========
            self._set_outputs(result, execution)
        except Exception as e:
            # 记录错误并设置失败状态
            error_msg = str(e)
            execution.set("result", None)
            execution.set("error", error_msg)
            self._log_output(success=False, error=error_msg)
            raise

    def _get_input_params(self, execution: DelegateExecution) -> List[Dict]:
        """获取输入参数列表"""
        return execution.get("inputParams", [])

    def _resolve_inputs(self, input_params: List[Dict], execution: DelegateExecution) -> Dict[str, Any]:
        """解析输入参数值"""
        resolved = {}
        missing = []
        
        for param in input_params:
            if not isinstance(param, dict):
                continue
                
            name = param.get("name")
            source = param.get("value")
            
            if not name:
                continue
                
            if source:
                value = self._get_value_from_source(source, execution.context)
                if value is not None:
                    resolved[name] = value
                    execution.set(name, value)
                    # 显示参数值（复杂对象使用 JSON 序列化）
                    import json
                    if isinstance(value, (dict, list)):
                        value_str = json.dumps(value, ensure_ascii=False)[:200]
                    else:
                        value_str = str(value)[:100]
                    self._log_processing(f"输入参数 '{name}' = {type(value).__name__} = {value_str}")
                else:
                    missing.append(name)
                    self._log_processing(f"输入参数 '{name}' 解析失败: {source}")
            else:
                # 如果没有指定来源，从上下文中直接获取
                value = execution.get(name)
                if value is not None:
                    resolved[name] = value
        
        # 验证必需参数
        if missing:
            error_msg = f"缺少必需输入参数: {', '.join(missing)}"
            execution.set("result", None)
            execution.set("error", error_msg)
            self._log_output(success=False, error=error_msg)
            raise ValueError(error_msg)
        
        return resolved

    def _get_value_from_source(self, source: str, context) -> Any:
        """从来源获取值
        
        变量引用格式: ${nodeId}.output.${variableName}
        
        参数:
            source: 变量引用路径，如 "node-abc123.output.parsed_result"
            context: 工作流执行上下文
        
        返回:
            引用的值，如果找不到返回 None
        """
        parts = source.split('.')
        if len(parts) < 3:
            return None
        
        node_id = parts[0]
        if parts[1] != 'output':
            return None
        
        var_name = parts[2]
        
        # 从 step_results 获取步骤执行结果
        step_result = context.step_results.get(node_id)
        if step_result is None:
            return None
        
        # 支持新的输出格式 {'output': value}
        # 先尝试从 output 字段获取，兼容新格式
        actual_result = step_result.get("output", step_result)
        
        # 获取指定的变量值
        return actual_result.get(var_name)

    def _execute(self, inputs: Dict[str, Any], execution: DelegateExecution) -> Any:
        """执行代码逻辑"""
        code = execution.get("code", "")
        
        # 记录执行信息
        code_snippet = code.strip().replace('\n', '\\n')[:150]
        inputs_str = json.dumps(inputs, ensure_ascii=False)[:100]
        self._log_input(code=code_snippet, language="python", inputs=inputs_str)
        self._log_processing("执行 Python 代码")
        
        # 替换模板变量
        code = self._replace_templates(code, inputs)
        
        # 执行 Python 代码
        return self._run_python(code)

    def _replace_templates(self, code: str, inputs: Dict[str, Any]) -> str:
        """替换代码中的 {{variable}} 模板"""
        def replace_var(match):
            var_expr = match.group(1).strip()
            parts = var_expr.split('.')
            value = inputs.get(parts[0])
            
            if value is None:
                return match.group(0)
            
            # 处理字段访问
            for part in parts[1:]:
                if isinstance(value, dict) and part in value:
                    value = value[part]
                elif isinstance(value, list) and part.isdigit():
                    idx = int(part)
                    value = value[idx] if idx < len(value) else None
                else:
                    value = None
                    break
            
            if value is None:
                return match.group(0)
            
            # 根据类型格式化
            if isinstance(value, str):
                return f"'{value}'"
            elif isinstance(value, (dict, list)):
                return json.dumps(value)
            return str(value)
        
        return re.sub(r'\{\{(\s*[\w\.\-]+\s*)\}\}', replace_var, code)

    def _set_outputs(self, result: Any, execution: DelegateExecution) -> None:
        """设置输出参数"""
        output_params = execution.get("outputParams", [])
        self._log_processing(f"原始 outputParams 配置: {json.dumps(output_params, ensure_ascii=False)}")
        
        # 提取指定的输出参数名
        specified_output_names = []
        for param in output_params:
            if isinstance(param, dict):
                name = param.get("name")
                if name:
                    specified_output_names.append(name)
        
        # 如果指定了输出参数且结果是字典，设置每个指定的参数
        output_names = []
        if specified_output_names and isinstance(result, dict):
            for name in specified_output_names:
                if name in result:
                    execution.set(name, result[name])
                    output_names.append(name)
        
        # 如果没有指定输出参数，使用默认行为（返回完整结果）
        if not output_names:
            execution.set("result", result)
            output_names = ["result"]
        
        self._log_processing(f"输出参数: {output_names}")
        
        # 格式化输出结果（只显示实际输出的参数）
        output_result = {}
        for name in output_names:
            value = execution.get(name)
            if value is not None:
                output_result[name] = value
        
        if not output_result:
            result_str = "None"
        elif isinstance(output_result, dict):
            result_str = json.dumps(output_result, ensure_ascii=False, indent=2)[:200]
        elif isinstance(output_result, list):
            result_str = json.dumps(output_result, ensure_ascii=False)[:200]
        else:
            result_str = str(output_result)[:100]
        
        self._log_output(success=True, result=result_str)

    def get_dynamic_outputs(self, config_data: Dict[str, Any]) -> Dict[str, ParamSchema]:
        """返回动态输出 schema"""
        outputs = {}
        output_params = config_data.get("outputParams", [])
        for param in output_params:
            if isinstance(param, dict):
                name = param.get("name")
                if name:
                    outputs[name] = ParamSchema(type="any", description=f"动态输出参数: {name}")
        return outputs

    def _run_python(self, code: str) -> Any:
        """执行 Python 代码"""
        import types
        exec_locals = {}
        try:
            exec(code, {}, exec_locals)
            # 返回所有执行后的本地变量（除了内置变量和模块对象）
            result = {}
            for key, value in exec_locals.items():
                if not key.startswith('_') and not isinstance(value, types.ModuleType):
                    result[key] = value
            return result
        except Exception as e:
            raise RuntimeError(f"Python 代码执行失败: {str(e)}")