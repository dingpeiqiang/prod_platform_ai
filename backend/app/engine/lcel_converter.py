"""
工作流到LangChain LCEL的转换器

将可视化工作流定义转换为LangChain的Runnable链，
充分利用LangChain的优化和执行能力。
"""

from typing import Dict, Any, List
from langchain_core.runnables import (
    RunnableSequence,
    RunnableBranch,
    RunnableLambda,
    RunnableParallel,
    RunnablePassthrough
)
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import JsonOutputParser, StrOutputParser
from langchain_core.runnables.base import Runnable

from app.langchain.llm_wrapper import get_langchain_llm

logger = __import__('logging').getLogger(__name__)


class LcelConverter:
    """工作流到LCEL转换器"""
    
    # 标准输出变量名，与原生模式保持一致
    OUTPUT_VAR_NAME = "__node_output__"
    
    def __init__(self):
        self.llm = get_langchain_llm().llm
        self.node_map = {}
        self.edge_map = {}
        self.current_node_data = {}  # 当前节点数据，用于获取输入输出映射
    
    def _resolve_expression(self, expr: str, context: Dict[str, Any], node_output: Any = None) -> Any:
        """解析表达式，支持多种引用方式"""
        if not expr or not isinstance(expr, str):
            return expr
        
        expr = expr.strip()
        
        if expr.startswith("{{") and expr.endswith("}}"):
            var_path = expr[2:-2].strip()
            
            # 处理 __output__ 引用（当前节点输出）
            if var_path == "__output__":
                return node_output
            elif var_path.startswith("__output__."):
                field_name = var_path[10:]
                if isinstance(node_output, dict) and field_name in node_output:
                    return node_output[field_name]
                return ""
            
            # 处理 __node_output__ 引用（前一个节点输出）
            if var_path == self.OUTPUT_VAR_NAME:
                return context.get(self.OUTPUT_VAR_NAME, "")
            elif var_path.startswith(self.OUTPUT_VAR_NAME + "."):
                field_name = var_path[len(self.OUTPUT_VAR_NAME) + 1:]
                prev_output = context.get(self.OUTPUT_VAR_NAME, {})
                if isinstance(prev_output, dict) and field_name in prev_output:
                    return prev_output[field_name]
                return ""
            
            # 尝试从上下文获取
            if var_path in context:
                return context[var_path]
            
            # 尝试从前一个节点输出获取
            prev_output = context.get(self.OUTPUT_VAR_NAME, {})
            if isinstance(prev_output, dict) and var_path in prev_output:
                return prev_output[var_path]
            
            return ""
        
        return expr
    
    def _resolve_inputs(self, inputs_config: Dict[str, str], context: Dict[str, Any], node_output: Any = None) -> Dict[str, Any]:
        """解析显性输入配置"""
        resolved = {}
        if not inputs_config:
            return resolved
        
        for input_key, source_expr in inputs_config.items():
            resolved[input_key] = self._resolve_expression(source_expr, context, node_output)
        
        return resolved
    
    def convert(self, workflow_def: Dict[str, Any]) -> Runnable:
        """将工作流定义转换为LCEL Runnable"""
        nodes = workflow_def.get("nodes", [])
        edges = workflow_def.get("edges", [])
        
        # 构建节点和边的映射
        self.node_map = {node["id"]: node for node in nodes}
        self.edge_map = self._build_edge_map(edges)
        
        # 找到开始节点
        start_node = next((n for n in nodes if n.get("type") == "start"), None)
        if not start_node:
            raise ValueError("工作流缺少开始节点")
        
        # 递归构建LCEL链
        return self._build_chain(start_node["id"])
    
    def _build_edge_map(self, edges: List[Dict[str, Any]]) -> Dict[str, List[Dict[str, Any]]]:
        """构建边的映射：source -> [targets]"""
        edge_map = {}
        for edge in edges:
            source = edge.get("source")
            if source not in edge_map:
                edge_map[source] = []
            edge_map[source].append(edge)
        return edge_map
    
    def _build_chain(self, node_id: str) -> Runnable:
        """递归构建节点对应的LCEL链"""
        node = self.node_map.get(node_id)
        if not node:
            return RunnablePassthrough()
        
        node_type = node.get("type", "")
        node_data = node.get("data", {})
        
        # 构建当前节点的Runnable
        current_runnable = self._node_to_runnable(node)
        
        # 获取下一个节点
        next_edges = self.edge_map.get(node_id, [])
        
        if not next_edges:
            # 没有后续节点，返回当前runnable
            return current_runnable
        
        # 处理条件分支
        if node_type == "condition":
            return self._build_condition_chain(node, next_edges)
        
        # 处理循环
        if node_type == "loop":
            return self._build_loop_chain(node, next_edges)
        
        # 普通顺序执行
        next_runnables = []
        for edge in next_edges:
            if not edge.get("sourceHandle"):  # 非条件分支的边
                next_runnable = self._build_chain(edge["target"])
                next_runnables.append(next_runnable)
        
        if len(next_runnables) == 1:
            return current_runnable | next_runnables[0]
        elif len(next_runnables) > 1:
            # 并行执行
            return current_runnable | RunnableParallel(
                **{str(i): r for i, r in enumerate(next_runnables)}
            )
        
        return current_runnable
    
    def _node_to_runnable(self, node: Dict[str, Any]) -> Runnable:
        """将单个节点转换为Runnable"""
        node_type = node.get("type", "")
        node_data = node.get("data", {})
        
        match node_type:
            case "start":
                return RunnableLambda(self._handle_start)
            
            case "end":
                return RunnableLambda(self._handle_end)
            
            case "prompt":
                return self._create_prompt_runnable(node_data)
            
            case "llm":
                return self._create_llm_runnable(node_data)
            
            case "variable":
                return RunnableLambda(self._handle_variable(node_data))
            
            case "condition":
                return RunnableLambda(self._handle_condition(node_data))
            
            case "loop":
                return RunnableLambda(self._handle_loop(node_data))
            
            case "http":
                return RunnableLambda(self._handle_http(node_data))
            
            case "code":
                return RunnableLambda(self._handle_code(node_data))
            
            case "parser":
                return RunnableLambda(self._handle_parser)
            
            case "tool":
                return RunnableLambda(self._handle_tool(node_data))
            
            case _:
                logger.warning(f"Unknown node type: {node_type}")
                return RunnablePassthrough()
    
    def _handle_start(self, inputs: Dict[str, Any]) -> Dict[str, Any]:
        """处理开始节点"""
        input_data = inputs.get("inputs", {})
        return {
            "context": input_data, 
            "output": "",
            self.OUTPUT_VAR_NAME: input_data  # 设置标准输出变量
        }
    
    def _handle_end(self, inputs: Dict[str, Any]) -> Dict[str, Any]:
        """处理结束节点"""
        final_output = inputs.get(self.OUTPUT_VAR_NAME, inputs.get("output", ""))
        return {
            "result": final_output, 
            "context": inputs.get("context", {}),
            self.OUTPUT_VAR_NAME: final_output
        }
    
    def _create_prompt_runnable(self, node_data: Dict[str, Any]) -> Runnable:
        """创建提示词节点的Runnable"""
        prompt_text = node_data.get("prompt", "")
        input_mappings = node_data.get("inputs", {})
        output_mappings = node_data.get("outputs", {})
        
        def render_prompt(inputs: Dict[str, Any]) -> Dict[str, Any]:
            context = inputs.get("context", {}).copy()
            prev_output = inputs.get(self.OUTPUT_VAR_NAME, "")
            
            # 处理显性输入映射
            if input_mappings:
                resolved_inputs = self._resolve_inputs(input_mappings, context, prev_output)
                context.update(resolved_inputs)
            
            rendered = prompt_text
            for key, value in context.items():
                rendered = rendered.replace(f"{{{{{key}}}}}", str(value))
            
            # 如果模板中引用了前一个节点的输出，尝试从标准输出变量获取
            if "{{__node_output__}}" in rendered:
                rendered = rendered.replace("{{__node_output__}}", str(prev_output))
            
            # 处理显性输出映射
            output_value = rendered
            if output_mappings:
                output_dict = {}
                for target_var, source_expr in output_mappings.items():
                    resolved_value = self._resolve_expression(source_expr, context, rendered)
                    context[target_var] = resolved_value
                    output_dict[target_var] = resolved_value
                output_value = output_dict
            
            return {
                "input": rendered, 
                "context": context,
                "output": rendered,
                self.OUTPUT_VAR_NAME: output_value  # 设置标准输出变量
            }
        
        return RunnableLambda(render_prompt)
    
    def _create_llm_runnable(self, node_data: Dict[str, Any]) -> Runnable:
        """创建LLM节点的Runnable"""
        system_prompt = node_data.get("systemPrompt", "")
        input_mappings = node_data.get("inputs", {})
        output_mappings = node_data.get("outputs", {})
        
        def build_and_run(inputs: Dict[str, Any]) -> Dict[str, Any]:
            context = inputs.get("context", {}).copy()
            prev_output = inputs.get(self.OUTPUT_VAR_NAME, "")
            
            # 处理显性输入映射
            if input_mappings:
                resolved_inputs = self._resolve_inputs(input_mappings, context, prev_output)
                context.update(resolved_inputs)
            
            # 获取输入（优先级：显性配置输入 > input变量 > 标准输出变量 > output变量）
            prompt_input = ""
            
            # 检查显性配置的输入
            if input_mappings and "input" in input_mappings:
                prompt_input = self._resolve_expression(input_mappings["input"], context, "")
            
            # 如果没有显性配置，使用默认行为
            if not prompt_input:
                prompt_input = inputs.get("input", "")
            if not prompt_input:
                prompt_input = inputs.get(self.OUTPUT_VAR_NAME, "")
            if not prompt_input:
                prompt_input = inputs.get("output", "")
            
            messages = []
            if system_prompt:
                # 渲染系统提示词中的变量
                rendered_system = system_prompt
                for key, value in context.items():
                    rendered_system = rendered_system.replace(f"{{{{{key}}}}}", str(value))
                messages.append(("system", rendered_system))
            
            messages.append(("user", prompt_input))
            
            prompt = ChatPromptTemplate.from_messages(messages)
            chain = prompt | self.llm | StrOutputParser()
            
            result = chain.invoke({})
            
            # 处理显性输出映射
            output_value = result
            if output_mappings:
                output_dict = {}
                for target_var, source_expr in output_mappings.items():
                    resolved_value = self._resolve_expression(source_expr, context, result)
                    context[target_var] = resolved_value
                    output_dict[target_var] = resolved_value
                output_value = output_dict
            
            return {
                "output": result, 
                "context": context,
                self.OUTPUT_VAR_NAME: output_value  # 设置标准输出变量
            }
        
        return RunnableLambda(build_and_run)
    
    def _handle_variable(self, node_data: Dict[str, Any]):
        """处理变量赋值节点"""
        var_name = node_data.get("variableName", "result")
        var_value = node_data.get("variableValue", "")
        input_mappings = node_data.get("inputs", {})
        output_mappings = node_data.get("outputs", {})
        
        def assign_variable(inputs: Dict[str, Any]) -> Dict[str, Any]:
            context = inputs.get("context", {}).copy()
            prev_output = inputs.get(self.OUTPUT_VAR_NAME, "")
            
            # 处理显性输入映射
            if input_mappings:
                resolved_inputs = self._resolve_inputs(input_mappings, context, prev_output)
                context.update(resolved_inputs)
            
            # 如果配置了显性输出映射，优先使用映射配置
            if output_mappings:
                output_dict = {}
                for target_var, source_expr in output_mappings.items():
                    resolved_value = self._resolve_expression(source_expr, context, "")
                    context[target_var] = resolved_value
                    output_dict[target_var] = resolved_value
                return {
                    "context": context, 
                    "output": inputs.get("output", ""),
                    self.OUTPUT_VAR_NAME: output_dict  # 设置标准输出变量
                }
            
            # 传统变量赋值逻辑
            if var_value.startswith("{{") and var_value.endswith("}}"):
                # 引用其他变量
                ref_var = var_value[2:-2]
                if ref_var == self.OUTPUT_VAR_NAME:
                    context[var_name] = prev_output
                else:
                    context[var_name] = context.get(ref_var, "")
            elif var_value == "output":
                # 使用上一个输出（保持向后兼容）
                context[var_name] = inputs.get("output", "")
            elif var_value == "__node_output__":
                # 使用标准输出变量
                context[var_name] = prev_output
            else:
                # 直接赋值（支持模板渲染）
                rendered_value = var_value
                for key, value in context.items():
                    rendered_value = rendered_value.replace(f"{{{{{key}}}}}", str(value))
                context[var_name] = rendered_value
            
            output_value = {var_name: context[var_name]}
            
            return {
                "context": context, 
                "output": inputs.get("output", ""),
                self.OUTPUT_VAR_NAME: output_value  # 设置标准输出变量
            }
        
        return assign_variable
    
    def _handle_condition(self, node_data: Dict[str, Any]):
        """处理条件节点"""
        left_type = node_data.get("leftType", "variable")
        left_value = node_data.get("leftValue", "")
        operator = node_data.get("operator", "==")
        right_type = node_data.get("rightType", "constant")
        right_value = node_data.get("rightValue", "")
        input_mappings = node_data.get("inputs", {})
        output_mappings = node_data.get("outputs", {})
        
        def evaluate_condition(inputs: Dict[str, Any]) -> Dict[str, Any]:
            context = inputs.get("context", {}).copy()
            prev_output = inputs.get(self.OUTPUT_VAR_NAME, "")
            
            # 处理显性输入映射
            if input_mappings:
                resolved_inputs = self._resolve_inputs(input_mappings, context, prev_output)
                context.update(resolved_inputs)
            
            # 获取左操作数
            if left_type == "variable":
                left = context.get(left_value, "")
                # 如果变量不存在，尝试从前一个节点的输出中获取
                if not left and left_value != "":
                    if isinstance(prev_output, dict) and left_value in prev_output:
                        left = prev_output[left_value]
            else:
                left = left_value
            
            # 获取右操作数
            if right_type == "variable":
                right = context.get(right_value, "")
            else:
                right = right_value
            
            # 类型转换
            if isinstance(left, str) and left.replace('.', '').isdigit():
                left = float(left) if '.' in left else int(left)
            if isinstance(right, str) and right.replace('.', '').isdigit():
                right = float(right) if '.' in right else int(right)
            
            # 执行比较
            result = False
            match operator:
                case "==": result = left == right
                case "!=": result = left != right
                case ">": result = left > right
                case "<": result = left < right
                case ">=": result = left >= right
                case "<=": result = left <= right
                case "contains": result = str(right) in str(left)
                case "not_contains": result = str(right) not in str(left)
                case _: result = False
            
            context["condition_result"] = result
            
            # 处理显性输出映射
            output_value = {"condition_result": result, "input": left}
            if output_mappings:
                output_dict = {}
                for target_var, source_expr in output_mappings.items():
                    resolved_value = self._resolve_expression(source_expr, context, output_value)
                    context[target_var] = resolved_value
                    output_dict[target_var] = resolved_value
                output_value = output_dict
            
            return {
                "context": context, 
                "output": inputs.get("output", ""), 
                "condition_result": result,
                self.OUTPUT_VAR_NAME: output_value  # 设置标准输出变量
            }
        
        return evaluate_condition
    
    def _handle_loop(self, node_data: Dict[str, Any]):
        """处理循环节点"""
        loop_type = node_data.get("loopType", "for")
        loop_count = int(node_data.get("loopCount", 3))
        
        def execute_loop(inputs: Dict[str, Any]) -> Dict[str, Any]:
            context = inputs.get("context", {}).copy()
            results = []
            
            for i in range(loop_count):
                context["loopIndex"] = i
                context["loopCount"] = loop_count
                context["loopFirst"] = (i == 0)
                context["loopLast"] = (i == loop_count - 1)
                
                # 这里只是设置循环变量，实际循环体执行由下游节点处理
                results.append({"iteration": i, "context": context.copy()})
            
            context["loopResults"] = results
            
            loop_info = {
                "loopIndex": context.get("loopIndex", 0),
                "loopCount": loop_count,
                "loopFirst": context.get("loopFirst", True),
                "loopLast": context.get("loopLast", False),
                "loopResults": results
            }
            
            return {
                "context": context, 
                "output": inputs.get("output", ""),
                self.OUTPUT_VAR_NAME: loop_info  # 设置标准输出变量
            }
        
        return execute_loop
    
    def _handle_http(self, node_data: Dict[str, Any]):
        """处理HTTP请求节点"""
        method = node_data.get("method", "GET").upper()
        url = node_data.get("url", "")
        headers = node_data.get("headers", {})
        body = node_data.get("body", "")
        input_mappings = node_data.get("inputs", {})
        output_mappings = node_data.get("outputs", {})
        
        async def make_request(inputs: Dict[str, Any]) -> Dict[str, Any]:
            import aiohttp
            
            context = inputs.get("context", {}).copy()
            prev_output = inputs.get(self.OUTPUT_VAR_NAME, "")
            
            # 处理显性输入映射
            if input_mappings:
                resolved_inputs = self._resolve_inputs(input_mappings, context, prev_output)
                context.update(resolved_inputs)
            
            # 渲染URL中的变量
            rendered_url = url
            for key, value in context.items():
                rendered_url = rendered_url.replace(f"{{{{{key}}}}}", str(value))
            
            # 渲染body中的变量
            rendered_body = body
            if isinstance(rendered_body, str):
                for key, value in context.items():
                    rendered_body = rendered_body.replace(f"{{{{{key}}}}}", str(value))
            
            async with aiohttp.ClientSession() as session:
                async with session.request(
                    method,
                    rendered_url,
                    headers=headers,
                    data=rendered_body if rendered_body else None,
                    json=json.loads(rendered_body) if rendered_body and rendered_body.startswith("{") else None
                ) as response:
                    content_type = response.headers.get("Content-Type", "")
                    if "json" in content_type:
                        data = await response.json()
                    else:
                        data = await response.text()
                    
                    http_result = {
                        "status": response.status,
                        "data": data,
                        "headers": dict(response.headers)
                    }
                    context["httpResult"] = http_result
            
            # 处理显性输出映射
            output_value = http_result
            if output_mappings:
                output_dict = {}
                for target_var, source_expr in output_mappings.items():
                    resolved_value = self._resolve_expression(source_expr, context, http_result)
                    context[target_var] = resolved_value
                    output_dict[target_var] = resolved_value
                output_value = output_dict
            
            return {
                "context": context, 
                "output": inputs.get("output", ""),
                self.OUTPUT_VAR_NAME: output_value  # 设置标准输出变量
            }
        
        return make_request
    
    def _handle_code(self, node_data: Dict[str, Any]):
        """处理代码执行节点"""
        code = node_data.get("code", "")
        language = node_data.get("language", "python").lower()
        input_mappings = node_data.get("inputs", {})
        output_mappings = node_data.get("outputs", {})
        
        def execute_code(inputs: Dict[str, Any]) -> Dict[str, Any]:
            context = inputs.get("context", {}).copy()
            prev_output = inputs.get(self.OUTPUT_VAR_NAME, "")
            
            # 处理显性输入映射
            if input_mappings:
                resolved_inputs = self._resolve_inputs(input_mappings, context, prev_output)
                context.update(resolved_inputs)
            
            if language == "python":
                exec_locals = {
                    "context": context,
                    "output": inputs.get("output", ""),
                    "input": inputs.get("input", ""),
                    self.OUTPUT_VAR_NAME: prev_output  # 添加前一个节点的输出
                }
                
                try:
                    exec(code, {}, exec_locals)
                    
                    if "result" in exec_locals:
                        context["codeResult"] = exec_locals["result"]
                        code_result = exec_locals["result"]
                    else:
                        code_result = prev_output  # 如果没有显式设置result，使用前一个节点的输出
                    
                    for key, value in exec_locals.items():
                        if key not in ["context", self.OUTPUT_VAR_NAME]:
                            context[key] = value
                except Exception as e:
                    context["codeError"] = str(e)
                    code_result = {"error": str(e)}
            
            # 处理显性输出映射
            output_value = code_result
            if output_mappings:
                output_dict = {}
                for target_var, source_expr in output_mappings.items():
                    resolved_value = self._resolve_expression(source_expr, context, code_result)
                    context[target_var] = resolved_value
                    output_dict[target_var] = resolved_value
                output_value = output_dict
            
            return {
                "context": context, 
                "output": inputs.get("output", ""),
                self.OUTPUT_VAR_NAME: output_value  # 设置标准输出变量
            }
        
        return execute_code
    
    def _handle_parser(self, inputs: Dict[str, Any]) -> Dict[str, Any]:
        """处理输出解析节点"""
        import json
        
        context = inputs.get("context", {}).copy()
        prev_output = inputs.get(self.OUTPUT_VAR_NAME, "")
        
        # 获取输入（优先级：output变量 > 标准输出变量）
        input_data = inputs.get("output", "")
        if not input_data:
            input_data = inputs.get(self.OUTPUT_VAR_NAME, "")
        
        try:
            # 如果input_data已经是字典，直接使用
            if isinstance(input_data, dict):
                parsed = input_data
            else:
                parsed = json.loads(input_data)
        except (json.JSONDecodeError, TypeError):
            parsed = {"text": input_data}
        
        context["parsed"] = parsed
        
        return {
            "context": context, 
            "output": parsed,
            self.OUTPUT_VAR_NAME: parsed  # 设置标准输出变量
        }
    
    def _handle_tool(self, node_data: Dict[str, Any]):
        """处理工具调用节点"""
        tool_type = node_data.get("toolType", "")
        input_mappings = node_data.get("inputs", {})
        output_mappings = node_data.get("outputs", {})
        tool_params = node_data.get("params", {})
        
        def call_tool(inputs: Dict[str, Any]) -> Dict[str, Any]:
            context = inputs.get("context", {}).copy()
            prev_output = inputs.get(self.OUTPUT_VAR_NAME, "")
            
            # 处理显性输入映射
            if input_mappings:
                resolved_inputs = self._resolve_inputs(input_mappings, context, prev_output)
                context.update(resolved_inputs)
            
            # 渲染参数
            rendered_params = {}
            for key, value in tool_params.items():
                if isinstance(value, str) and value.startswith("{{") and value.endswith("}}"):
                    ref_var = value[2:-2]
                    if ref_var == self.OUTPUT_VAR_NAME:
                        rendered_params[key] = prev_output
                    else:
                        rendered_params[key] = context.get(ref_var, value)
                else:
                    rendered_params[key] = value
            
            tool_result = {
                "toolType": tool_type,
                "params": rendered_params,
                "timestamp": __import__('datetime').datetime.now().isoformat()
            }
            context["toolResult"] = tool_result
            
            # 处理显性输出映射
            output_value = tool_result
            if output_mappings:
                output_dict = {}
                for target_var, source_expr in output_mappings.items():
                    resolved_value = self._resolve_expression(source_expr, context, tool_result)
                    context[target_var] = resolved_value
                    output_dict[target_var] = resolved_value
                output_value = output_dict
            
            return {
                "context": context, 
                "output": inputs.get("output", ""),
                self.OUTPUT_VAR_NAME: output_value  # 设置标准输出变量
            }
        
        return call_tool
    
    def _build_condition_chain(self, node: Dict[str, Any], edges: List[Dict[str, Any]]) -> Runnable:
        """构建条件分支链"""
        # 先执行条件判断
        condition_runnable = self._node_to_runnable(node)
        
        # 获取true和false分支的目标节点
        true_edges = [e for e in edges if e.get("sourceHandle") == "true"]
        false_edges = [e for e in edges if e.get("sourceHandle") == "false"]
        
        # 构建分支链
        true_runnable = None
        if true_edges:
            true_runnable = self._build_chain(true_edges[0]["target"])
        
        false_runnable = None
        if false_edges:
            false_runnable = self._build_chain(false_edges[0]["target"])
        
        # 使用RunnableBranch
        branch = RunnableBranch(
            (lambda x: x.get("condition_result", False), true_runnable or RunnablePassthrough()),
            false_runnable or RunnablePassthrough()
        )
        
        return condition_runnable | branch
    
    def _build_loop_chain(self, node: Dict[str, Any], edges: List[Dict[str, Any]]) -> Runnable:
        """构建循环链"""
        # 先执行循环初始化
        loop_runnable = self._node_to_runnable(node)
        
        # 获取循环体和结束边
        body_edges = [e for e in edges if e.get("sourceHandle") == "body"]
        end_edges = [e for e in edges if e.get("sourceHandle") == "end"]
        
        # 构建循环体链
        body_runnable = None
        if body_edges:
            body_runnable = self._build_chain(body_edges[0]["target"])
        
        # 构建结束链
        end_runnable = None
        if end_edges:
            end_runnable = self._build_chain(end_edges[0]["target"])
        
        if body_runnable:
            if end_runnable:
                return loop_runnable | body_runnable | end_runnable
            return loop_runnable | body_runnable
        
        return loop_runnable | (end_runnable or RunnablePassthrough())


# 使用示例
if __name__ == "__main__":
    workflow_def = {
        "nodes": [
            {"id": "start-1", "type": "start", "data": {"label": "开始"}},
            {"id": "prompt-1", "type": "prompt", "data": {"label": "提示词", "prompt": "请介绍{{topic}}"}},
            {"id": "llm-1", "type": "llm", "data": {"label": "LLM", "model": "qwen-vl-plus"}},
            {"id": "end-1", "type": "end", "data": {"label": "结束"}}
        ],
        "edges": [
            {"source": "start-1", "target": "prompt-1"},
            {"source": "prompt-1", "target": "llm-1"},
            {"source": "llm-1", "target": "end-1"}
        ]
    }
    
    converter = LcelConverter()
    chain = converter.convert(workflow_def)
    
    result = chain.invoke({"inputs": {"topic": "人工智能"}})
    print("执行结果:", result)