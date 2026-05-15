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
    
    def __init__(self):
        self.llm = get_langchain_llm().llm
        self.node_map = {}
        self.edge_map = {}
    
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
        return {"context": inputs.get("inputs", {}), "output": ""}
    
    def _handle_end(self, inputs: Dict[str, Any]) -> Dict[str, Any]:
        """处理结束节点"""
        return {"result": inputs.get("output", ""), "context": inputs.get("context", {})}
    
    def _create_prompt_runnable(self, node_data: Dict[str, Any]) -> Runnable:
        """创建提示词节点的Runnable"""
        prompt_text = node_data.get("prompt", "")
        
        def render_prompt(inputs: Dict[str, Any]) -> Dict[str, Any]:
            context = inputs.get("context", {})
            rendered = prompt_text
            for key, value in context.items():
                rendered = rendered.replace(f"{{{{{key}}}}}", str(value))
            return {"input": rendered, "context": context}
        
        return RunnableLambda(render_prompt)
    
    def _create_llm_runnable(self, node_data: Dict[str, Any]) -> Runnable:
        """创建LLM节点的Runnable"""
        system_prompt = node_data.get("systemPrompt", "")
        
        def build_and_run(inputs: Dict[str, Any]) -> Dict[str, Any]:
            messages = []
            if system_prompt:
                messages.append(("system", system_prompt))
            messages.append(("user", inputs.get("input", "")))
            
            prompt = ChatPromptTemplate.from_messages(messages)
            chain = prompt | self.llm | StrOutputParser()
            
            result = chain.invoke({})
            return {"output": result, "context": inputs.get("context", {})}
        
        return RunnableLambda(build_and_run)
    
    def _handle_variable(self, node_data: Dict[str, Any]):
        """处理变量赋值节点"""
        var_name = node_data.get("variableName", "result")
        var_value = node_data.get("variableValue", "")
        
        def assign_variable(inputs: Dict[str, Any]) -> Dict[str, Any]:
            context = inputs.get("context", {}).copy()
            if var_value.startswith("{{") and var_value.endswith("}}"):
                # 引用其他变量
                ref_var = var_value[2:-2]
                context[var_name] = context.get(ref_var, "")
            elif var_value == "output":
                # 使用上一个输出
                context[var_name] = inputs.get("output", "")
            else:
                # 直接赋值
                context[var_name] = var_value
            
            return {"context": context, "output": inputs.get("output", "")}
        
        return assign_variable
    
    def _handle_condition(self, node_data: Dict[str, Any]):
        """处理条件节点"""
        left_type = node_data.get("leftType", "variable")
        left_value = node_data.get("leftValue", "")
        operator = node_data.get("operator", "==")
        right_type = node_data.get("rightType", "constant")
        right_value = node_data.get("rightValue", "")
        
        def evaluate_condition(inputs: Dict[str, Any]) -> Dict[str, Any]:
            context = inputs.get("context", {})
            
            # 获取左操作数
            if left_type == "variable":
                left = context.get(left_value, "")
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
            return {"context": context, "output": inputs.get("output", ""), "condition_result": result}
        
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
            return {"context": context, "output": inputs.get("output", "")}
        
        return execute_loop
    
    def _handle_http(self, node_data: Dict[str, Any]):
        """处理HTTP请求节点"""
        method = node_data.get("method", "GET").upper()
        url = node_data.get("url", "")
        headers = node_data.get("headers", {})
        body = node_data.get("body", "")
        
        async def make_request(inputs: Dict[str, Any]) -> Dict[str, Any]:
            import aiohttp
            
            context = inputs.get("context", {})
            
            # 渲染URL中的变量
            rendered_url = url
            for key, value in context.items():
                rendered_url = rendered_url.replace(f"{{{{{key}}}}}", str(value))
            
            async with aiohttp.ClientSession() as session:
                async with session.request(
                    method,
                    rendered_url,
                    headers=headers,
                    data=body if body else None
                ) as response:
                    content_type = response.headers.get("Content-Type", "")
                    if "json" in content_type:
                        data = await response.json()
                    else:
                        data = await response.text()
                    
                    context["httpResult"] = {
                        "status": response.status,
                        "data": data,
                        "headers": dict(response.headers)
                    }
            
            return {"context": context, "output": inputs.get("output", "")}
        
        return make_request
    
    def _handle_code(self, node_data: Dict[str, Any]):
        """处理代码执行节点"""
        code = node_data.get("code", "")
        language = node_data.get("language", "python").lower()
        
        def execute_code(inputs: Dict[str, Any]) -> Dict[str, Any]:
            context = inputs.get("context", {}).copy()
            
            if language == "python":
                exec_locals = {
                    "context": context,
                    "output": inputs.get("output", ""),
                    "input": inputs.get("input", "")
                }
                
                try:
                    exec(code, {}, exec_locals)
                    
                    if "result" in exec_locals:
                        context["codeResult"] = exec_locals["result"]
                    
                    for key, value in exec_locals.items():
                        if key not in ["context"]:
                            context[key] = value
                except Exception as e:
                    context["codeError"] = str(e)
            
            return {"context": context, "output": inputs.get("output", "")}
        
        return execute_code
    
    def _handle_parser(self, inputs: Dict[str, Any]) -> Dict[str, Any]:
        """处理输出解析节点"""
        import json
        
        context = inputs.get("context", {}).copy()
        output = inputs.get("output", "")
        
        try:
            parsed = json.loads(output)
        except (json.JSONDecodeError, TypeError):
            parsed = {"text": output}
        
        context["parsed"] = parsed
        return {"context": context, "output": parsed}
    
    def _handle_tool(self, node_data: Dict[str, Any]):
        """处理工具调用节点"""
        tool_type = node_data.get("toolType", "")
        tool_params = node_data.get("params", {})
        
        def call_tool(inputs: Dict[str, Any]) -> Dict[str, Any]:
            context = inputs.get("context", {}).copy()
            
            # 渲染参数
            rendered_params = {}
            for key, value in tool_params.items():
                if isinstance(value, str) and value.startswith("{{") and value.endswith("}}"):
                    ref_var = value[2:-2]
                    rendered_params[key] = context.get(ref_var, value)
                else:
                    rendered_params[key] = value
            
            context["toolResult"] = {
                "toolType": tool_type,
                "params": rendered_params,
                "timestamp": __import__('datetime').datetime.now().isoformat()
            }
            
            return {"context": context, "output": inputs.get("output", "")}
        
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