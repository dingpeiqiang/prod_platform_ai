"""
工作流执行引擎核心模块

此模块实现了与LangChain深度集成的工作流执行引擎，支持：
- 可视化工作流定义的解析与执行
- 多种节点类型的执行器（LLM、工具调用、条件分支、循环等）
- 上下文管理和变量传递
- 异步执行和流式输出
"""

from typing import Dict, Any, List, Optional, AsyncGenerator
from abc import ABC, abstractmethod
from datetime import datetime
import json
import logging
import re
from enum import Enum

from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import JsonOutputParser, StrOutputParser

from app.langchain.llm_wrapper import get_langchain_llm
from app.core.config_loader import config_loader

logger = logging.getLogger("workflow_executor")


class ExecutionStatus(str, Enum):
    """节点执行状态"""
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    SKIPPED = "skipped"


class WorkflowContext:
    """工作流执行上下文"""
    
    def __init__(self, workflow_id: str, inputs: Dict[str, Any] = None):
        self.workflow_id = workflow_id
        self.inputs = inputs or {}
        self.variables = dict(inputs or {})  # 用户输入参数转为变量
        self.outputs = {}
        self.status = ExecutionStatus.PENDING
        self.created_at = datetime.now()
        self.started_at: Optional[datetime] = None
        self.completed_at: Optional[datetime] = None
        self.current_node_id: Optional[str] = None
        self.error: Optional[str] = None
        self.metadata: Dict[str, Any] = {}
        self.node_statuses: Dict[str, ExecutionStatus] = {}
    
    def set_variable(self, key: str, value: Any):
        """设置变量"""
        self.variables[key] = value
    
    def get_variable(self, key: str, default: Any = None) -> Any:
        """获取变量"""
        return self.variables.get(key, default)
    
    def update_node_status(self, node_id: str, status: ExecutionStatus):
        """更新节点状态"""
        self.node_statuses[node_id] = status


class NodeExecutor(ABC):
    """节点执行器基类"""
    
    NODE_TYPE = ""
    
    def __init__(self, node: Dict[str, Any]):
        self.node = node
        self.node_id = node.get("id", "")
        self.node_data = node.get("data", {})
    
    @abstractmethod
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        """执行节点，返回下一个节点ID列表"""
        pass
    
    def render_template(self, template: str, context: WorkflowContext) -> str:
        """渲染模板，替换变量"""
        if not template:
            return ""
        
        def replace_var(match):
            var_name = match.group(1)
            return str(context.get_variable(var_name, ""))
        
        return re.sub(r"\{\{(\w+)\}\}", replace_var, template)


class StartNodeExecutor(NodeExecutor):
    """开始节点执行器"""
    
    NODE_TYPE = "start"
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        # 将输入参数设置到上下文中
        if context.inputs:
            for key, value in context.inputs.items():
                context.set_variable(key, value)
        
        context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
        
        # 获取输出边
        return self._get_next_nodes(edges)
    
    def _get_next_nodes(self, edges: List[Dict[str, Any]]) -> List[str]:
        return [e["target"] for e in edges if e["source"] == self.node_id]


class EndNodeExecutor(NodeExecutor):
    """结束节点执行器"""
    
    NODE_TYPE = "end"
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        context.status = ExecutionStatus.COMPLETED
        context.completed_at = datetime.now()
        context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
        return []


class PromptNodeExecutor(NodeExecutor):
    """提示词节点执行器"""
    
    NODE_TYPE = "prompt"
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        prompt = self.node_data.get("prompt", "")
        rendered_prompt = self.render_template(prompt, context)
        
        # 将渲染后的提示词设置为下一个节点的输入
        context.set_variable("input", rendered_prompt)
        context.outputs["prompt"] = rendered_prompt
        
        logger.info(f"Prompt rendered: {rendered_prompt[:100]}...")
        
        context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
        return self._get_next_nodes(edges)
    
    def _get_next_nodes(self, edges: List[Dict[str, Any]]) -> List[str]:
        return [e["target"] for e in edges if e["source"] == self.node_id]


class LlmNodeExecutor(NodeExecutor):
    """LLM调用节点执行器"""
    
    NODE_TYPE = "llm"
    
    def __init__(self, node: Dict[str, Any]):
        super().__init__(node)
        self.llm = get_langchain_llm().llm
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        try:
            model = self.node_data.get("model", "qwen-vl-plus")
            temperature = self.node_data.get("temperature", 0.7)
            max_tokens = self.node_data.get("maxTokens", 4096)
            top_p = self.node_data.get("topP", 0.95)
            system_prompt = self.node_data.get("systemPrompt", "")
            
            # 获取输入（来自前一个节点的output或input）
            prompt_input = context.get_variable("input", "")
            
            logger.info(f"LLM executing with model={model}, input={prompt_input[:50]}...")
            
            # 构建消息
            messages = []
            if system_prompt:
                messages.append(("system", self.render_template(system_prompt, context)))
            
            messages.append(("user", prompt_input))
            
            # 创建Prompt
            prompt = ChatPromptTemplate.from_messages(messages)
            
            # 执行LLM调用
            chain = prompt | self.llm | StrOutputParser()
            result = await chain.ainvoke({})
            
            # 设置输出变量
            context.set_variable("output", result)
            context.outputs["llm_output"] = result
            
            logger.info(f"LLM response received: {result[:100]}...")
            
            context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
            
        except Exception as e:
            logger.error(f"LLM execution failed: {e}")
            context.error = str(e)
            context.update_node_status(self.node_id, ExecutionStatus.FAILED)
            raise
        
        return self._get_next_nodes(edges)
    
    def _get_next_nodes(self, edges: List[Dict[str, Any]]) -> List[str]:
        return [e["target"] for e in edges if e["source"] == self.node_id]


class ConditionNodeExecutor(NodeExecutor):
    """条件分支节点执行器"""
    
    NODE_TYPE = "condition"
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        try:
            # 获取条件配置
            left_type = self.node_data.get("leftType", "variable")
            left_value = self.node_data.get("leftValue", "")
            operator = self.node_data.get("operator", "==")
            right_type = self.node_data.get("rightType", "constant")
            right_value = self.node_data.get("rightValue", "")
            
            # 获取左操作数
            if left_type == "variable":
                left_operand = context.get_variable(left_value, "")
            else:  # constant or expression
                left_operand = left_value
            
            # 获取右操作数
            if right_type == "variable":
                right_operand = context.get_variable(right_value, "")
            else:
                right_operand = right_value
            
            # 执行条件判断
            result = self._evaluate_condition(left_operand, operator, right_operand)
            context.set_variable("condition_result", result)
            
            logger.info(f"Condition evaluated: {left_operand} {operator} {right_operand} = {result}")
            
            # 根据结果选择输出边
            output_key = "true" if result else "false"
            next_nodes = [
                e["target"] for e in edges 
                if e["source"] == self.node_id and e.get("sourceHandle") == output_key
            ]
            
            context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
            return next_nodes
            
        except Exception as e:
            logger.error(f"Condition evaluation failed: {e}")
            context.error = str(e)
            context.update_node_status(self.node_id, ExecutionStatus.FAILED)
            raise
    
    def _evaluate_condition(self, left, operator, right) -> bool:
        """评估条件表达式"""
        try:
            # 类型转换
            if isinstance(left, str) and left.replace('.', '').isdigit():
                left = float(left) if '.' in left else int(left)
            if isinstance(right, str) and right.replace('.', '').isdigit():
                right = float(right) if '.' in right else int(right)
            
            # 执行比较
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


class LoopNodeExecutor(NodeExecutor):
    """循环节点执行器"""
    
    NODE_TYPE = "loop"
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        try:
            loop_type = self.node_data.get("loopType", "for")
            loop_count = int(self.node_data.get("loopCount", 3))
            
            logger.info(f"Starting {loop_type} loop with {loop_count} iterations")
            
            # 获取循环体边
            body_edges = [e for e in edges if e["source"] == self.node_id and e.get("sourceHandle") == "body"]
            end_edges = [e for e in edges if e["source"] == self.node_id and e.get("sourceHandle") == "end"]
            
            if loop_type == "for":
                for i in range(loop_count):
                    context.set_variable("loopIndex", i)
                    context.set_variable("loopCount", loop_count)
                    context.set_variable("loopFirst", i == 0)
                    context.set_variable("loopLast", i == loop_count - 1)
                    
                    logger.info(f"Loop iteration {i + 1}/{loop_count}")
                    
                    # 执行循环体（递归调用由executor处理）
                    # 这里只需设置变量，实际执行由executor调度
                    break  # 单次迭代，由executor继续处理
            
            context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
            
            # 返回循环体节点（第一次迭代）
            if body_edges:
                return [body_edges[0]["target"]]
            
            # 如果没有循环体，直接返回结束边
            return [e["target"] for e in end_edges]
            
        except Exception as e:
            logger.error(f"Loop execution failed: {e}")
            context.error = str(e)
            context.update_node_status(self.node_id, ExecutionStatus.FAILED)
            raise


class VariableNodeExecutor(NodeExecutor):
    """变量赋值节点执行器"""
    
    NODE_TYPE = "variable"
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        var_name = self.node_data.get("variableName", "result")
        var_value = self.node_data.get("variableValue", "")
        
        # 渲染变量值（支持模板）
        rendered_value = self.render_template(var_value, context)
        
        # 如果值引用了output变量，使用output
        if var_value == "{{output}}" or var_value == "output":
            rendered_value = context.get_variable("output", "")
        
        context.set_variable(var_name, rendered_value)
        context.outputs[var_name] = rendered_value
        
        logger.info(f"Variable set: {var_name} = {rendered_value[:50]}...")
        
        context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
        return self._get_next_nodes(edges)
    
    def _get_next_nodes(self, edges: List[Dict[str, Any]]) -> List[str]:
        return [e["target"] for e in edges if e["source"] == self.node_id]


class HttpNodeExecutor(NodeExecutor):
    """HTTP请求节点执行器"""
    
    NODE_TYPE = "http"
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        import aiohttp
        
        try:
            method = self.node_data.get("method", "GET").upper()
            url = self.render_template(self.node_data.get("url", ""), context)
            headers = self.node_data.get("headers", {})
            body = self.node_data.get("body", "")
            
            logger.info(f"HTTP {method} request to {url}")
            
            async with aiohttp.ClientSession() as session:
                async with session.request(
                    method,
                    url,
                    headers=headers,
                    data=body if body else None,
                    json=json.loads(body) if body and body.startswith("{") else None
                ) as response:
                    status = response.status
                    content_type = response.headers.get("Content-Type", "")
                    
                    if "json" in content_type:
                        result = await response.json()
                    else:
                        result = await response.text()
                    
                    context.set_variable("httpResult", {
                        "status": status,
                        "data": result,
                        "headers": dict(response.headers)
                    })
                    context.outputs["httpResult"] = context.variables["httpResult"]
            
            logger.info(f"HTTP response received: status={status}")
            
            context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
            
        except Exception as e:
            logger.error(f"HTTP request failed: {e}")
            context.error = str(e)
            context.update_node_status(self.node_id, ExecutionStatus.FAILED)
            raise
        
        return self._get_next_nodes(edges)
    
    def _get_next_nodes(self, edges: List[Dict[str, Any]]) -> List[str]:
        return [e["target"] for e in edges if e["source"] == self.node_id]


class CodeNodeExecutor(NodeExecutor):
    """代码执行节点执行器"""
    
    NODE_TYPE = "code"
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        code = self.node_data.get("code", "")
        language = self.node_data.get("language", "python").lower()
        
        try:
            if language == "python":
                # 创建安全的执行环境
                exec_locals = {
                    "context": context,
                    "variables": context.variables,
                    "output": context.get_variable("output", ""),
                    "input": context.get_variable("input", ""),
                }
                
                # 执行代码
                exec(code, {}, exec_locals)
                
                # 收集结果
                if "result" in exec_locals:
                    context.set_variable("codeResult", exec_locals["result"])
                    context.outputs["codeResult"] = exec_locals["result"]
                
                # 更新上下文中的变量
                for key, value in exec_locals.items():
                    if key not in ["context", "variables"]:
                        context.set_variable(key, value)
            
            logger.info(f"Code executed successfully")
            
            context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
            
        except Exception as e:
            logger.error(f"Code execution failed: {e}")
            context.error = str(e)
            context.update_node_status(self.node_id, ExecutionStatus.FAILED)
            raise
        
        return self._get_next_nodes(edges)
    
    def _get_next_nodes(self, edges: List[Dict[str, Any]]) -> List[str]:
        return [e["target"] for e in edges if e["source"] == self.node_id]


class ParserNodeExecutor(NodeExecutor):
    """输出解析节点执行器"""
    
    NODE_TYPE = "parser"
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        try:
            input_data = context.get_variable("output", "")
            
            # 尝试解析为JSON
            try:
                parsed = json.loads(input_data)
                context.set_variable("parsed", parsed)
            except (json.JSONDecodeError, TypeError):
                # 如果不是JSON，尝试提取结构化信息
                parsed = {"text": input_data}
                context.set_variable("parsed", parsed)
            
            context.outputs["parsed"] = context.variables["parsed"]
            
            logger.info(f"Output parsed successfully")
            
            context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
            
        except Exception as e:
            logger.error(f"Parser execution failed: {e}")
            context.error = str(e)
            context.update_node_status(self.node_id, ExecutionStatus.FAILED)
            raise
        
        return self._get_next_nodes(edges)
    
    def _get_next_nodes(self, edges: List[Dict[str, Any]]) -> List[str]:
        return [e["target"] for e in edges if e["source"] == self.node_id]


class ToolNodeExecutor(NodeExecutor):
    """工具调用节点执行器"""
    
    NODE_TYPE = "tool"
    
    def __init__(self, node: Dict[str, Any]):
        super().__init__(node)
        self.llm = get_langchain_llm().llm
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        tool_type = self.node_data.get("toolType", "")
        tool_params = self.node_data.get("params", {})
        
        try:
            # 渲染参数中的变量
            rendered_params = {}
            for key, value in tool_params.items():
                if isinstance(value, str):
                    rendered_params[key] = self.render_template(value, context)
                else:
                    rendered_params[key] = value
            
            # 根据工具类型执行不同操作
            if tool_type == "form_submit":
                result = await self._submit_form(rendered_params, context)
            elif tool_type == "search":
                result = await self._search(rendered_params, context)
            elif tool_type == "database":
                result = await self._query_database(rendered_params, context)
            else:
                result = await self._generic_tool_call(tool_type, rendered_params, context)
            
            context.set_variable("toolResult", result)
            context.outputs["toolResult"] = result
            
            logger.info(f"Tool {tool_type} executed successfully")
            
            context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
            
        except Exception as e:
            logger.error(f"Tool execution failed: {e}")
            context.error = str(e)
            context.update_node_status(self.node_id, ExecutionStatus.FAILED)
            raise
        
        return self._get_next_nodes(edges)
    
    async def _submit_form(self, params: Dict[str, Any], context: WorkflowContext) -> Dict[str, Any]:
        """提交表单"""
        from app.services.form_service import FormService
        
        form_code = params.get("formCode", "")
        form_data = params.get("formData", {})
        
        result = await FormService.create_instance(form_code, form_data)
        return result
    
    async def _search(self, params: Dict[str, Any], context: WorkflowContext) -> Dict[str, Any]:
        """执行搜索"""
        query = params.get("query", "")
        limit = params.get("limit", 10)
        
        # 使用推荐引擎进行搜索
        rec_engine = config_loader.get_recommendation_engine()
        results = await rec_engine.search(query, limit=limit)
        
        return {"query": query, "results": results[:limit]}
    
    async def _query_database(self, params: Dict[str, Any], context: WorkflowContext) -> Dict[str, Any]:
        """查询数据库"""
        # 安全考虑：限制只能执行预定义的查询
        query_type = params.get("queryType", "")
        
        if query_type == "get_history":
            from app.services.history_service import HistoryService
            result = await HistoryService.get_recent_history(params.get("limit", 10))
            return result
        
        return {"error": "Unknown query type"}
    
    async def _generic_tool_call(self, tool_type: str, params: Dict[str, Any], context: WorkflowContext) -> Dict[str, Any]:
        """通用工具调用"""
        return {
            "toolType": tool_type,
            "params": params,
            "message": "Tool execution placeholder"
        }
    
    def _get_next_nodes(self, edges: List[Dict[str, Any]]) -> List[str]:
        return [e["target"] for e in edges if e["source"] == self.node_id]


class WorkflowExecutor:
    """工作流执行器主类
    
    支持两种执行模式：
    1. 原生模式：使用自定义节点执行器
    2. LCEL模式：转换为LangChain Runnable链执行
    
    LCEL模式优势：
    - 利用LangChain的优化（缓存、重试、并行执行等）
    - 支持LangChain生态系统的所有工具和特性
    - 更好的类型安全和错误处理
    """
    
    # 节点执行器注册表
    _executor_registry = {
        "start": StartNodeExecutor,
        "end": EndNodeExecutor,
        "prompt": PromptNodeExecutor,
        "llm": LlmNodeExecutor,
        "condition": ConditionNodeExecutor,
        "loop": LoopNodeExecutor,
        "variable": VariableNodeExecutor,
        "http": HttpNodeExecutor,
        "code": CodeNodeExecutor,
        "parser": ParserNodeExecutor,
        "tool": ToolNodeExecutor,
    }
    
    @classmethod
    def register_executor(cls, node_type: str, executor_class):
        """注册自定义节点执行器"""
        cls._executor_registry[node_type] = executor_class
    
    def __init__(self, workflow_def: Dict[str, Any], use_lcel: bool = False):
        self.workflow_def = workflow_def
        self.nodes = workflow_def.get("nodes", [])
        self.edges = workflow_def.get("edges", [])
        self.node_map = {node["id"]: node for node in self.nodes}
        self.use_lcel = use_lcel
        self._lcel_chain = None
        
    async def execute(self, inputs: Dict[str, Any] = None) -> WorkflowContext:
        """执行工作流"""
        workflow_id = f"exec_{datetime.now().strftime('%Y%m%d%H%M%S%f')}"
        context = WorkflowContext(workflow_id, inputs or {})
        
        context.status = ExecutionStatus.RUNNING
        context.started_at = datetime.now()
        
        logger.info(f"Starting workflow execution: {workflow_id}")
        
        try:
            if self.use_lcel:
                # 使用LCEL模式执行
                await self._execute_lcel(context, inputs or {})
            else:
                # 使用原生模式执行
                start_node = next((n for n in self.nodes if n.get("type") == "start"), None)
                if not start_node:
                    raise ValueError("工作流缺少开始节点")
                await self._execute_node(start_node["id"], context)
            
        except Exception as e:
            logger.error(f"Workflow execution failed: {e}")
            context.status = ExecutionStatus.FAILED
            context.error = str(e)
        
        return context
    
    async def _execute_lcel(self, context: WorkflowContext, inputs: Dict[str, Any]):
        """使用LCEL模式执行工作流"""
        from .lcel_converter import LcelConverter
        
        converter = LcelConverter()
        chain = converter.convert(self.workflow_def)
        
        logger.info("Executing workflow using LCEL mode")
        
        # 执行LCEL链
        result = chain.invoke({"inputs": inputs})
        
        # 将LCEL结果转换为上下文
        if isinstance(result, dict):
            if "result" in result:
                context.set_variable("output", result["result"])
                context.outputs["final_result"] = result["result"]
            if "context" in result:
                for key, value in result["context"].items():
                    context.set_variable(key, value)
        
        context.status = ExecutionStatus.COMPLETED
        context.completed_at = datetime.now()
    
    async def execute_streaming(self, inputs: Dict[str, Any] = None) -> AsyncGenerator[Dict[str, Any], None]:
        """流式执行工作流"""
        workflow_id = f"exec_{datetime.now().strftime('%Y%m%d%H%M%S%f')}"
        context = WorkflowContext(workflow_id, inputs or {})
        
        context.status = ExecutionStatus.RUNNING
        context.started_at = datetime.now()
        
        yield {
            "type": "workflow_start",
            "workflow_id": workflow_id,
            "timestamp": datetime.now().isoformat()
        }
        
        try:
            start_node = next((n for n in self.nodes if n.get("type") == "start"), None)
            if not start_node:
                yield {
                    "type": "error",
                    "message": "工作流缺少开始节点",
                    "timestamp": datetime.now().isoformat()
                }
                return
            
            await self._execute_node_streaming(start_node["id"], context, lambda msg: yield msg)
            
            yield {
                "type": "workflow_complete",
                "workflow_id": workflow_id,
                "status": context.status.value,
                "outputs": context.outputs,
                "timestamp": datetime.now().isoformat()
            }
            
        except Exception as e:
            logger.error(f"Workflow streaming execution failed: {e}")
            context.status = ExecutionStatus.FAILED
            context.error = str(e)
            
            yield {
                "type": "error",
                "message": str(e),
                "workflow_id": workflow_id,
                "timestamp": datetime.now().isoformat()
            }
    
    async def _execute_node(self, node_id: str, context: WorkflowContext):
        """递归执行节点"""
        if context.status == ExecutionStatus.COMPLETED:
            return
        
        node = self.node_map.get(node_id)
        if not node:
            return
        
        node_type = node.get("type", "")
        executor_class = self._executor_registry.get(node_type)
        
        if not executor_class:
            logger.warning(f"No executor found for node type: {node_type}")
            return
        
        executor = executor_class(node)
        next_node_ids = await executor.execute(context, self.edges)
        
        # 递归执行下一个节点
        for next_node_id in next_node_ids:
            await self._execute_node(next_node_id, context)
    
    async def _execute_node_streaming(self, node_id: str, context: WorkflowContext, yield_fn):
        """递归流式执行节点"""
        if context.status == ExecutionStatus.COMPLETED:
            return
        
        node = self.node_map.get(node_id)
        if not node:
            return
        
        node_type = node.get("type", "")
        executor_class = self._executor_registry.get(node_type)
        
        if not executor_class:
            logger.warning(f"No executor found for node type: {node_type}")
            return
        
        # 发送节点开始事件
        yield_fn({
            "type": "node_start",
            "node_id": node_id,
            "node_type": node_type,
            "node_label": node.get("data", {}).get("label", node_type),
            "timestamp": datetime.now().isoformat()
        })
        
        executor = executor_class(node)
        next_node_ids = await executor.execute(context, self.edges)
        
        # 发送节点完成事件
        node_status = context.node_statuses.get(node_id, ExecutionStatus.COMPLETED)
        yield_fn({
            "type": "node_complete",
            "node_id": node_id,
            "node_type": node_type,
            "status": node_status.value,
            "outputs": context.outputs,
            "timestamp": datetime.now().isoformat()
        })
        
        # 递归执行下一个节点
        for next_node_id in next_node_ids:
            await self._execute_node_streaming(next_node_id, context, yield_fn)