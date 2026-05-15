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


class VariableMetadata:
    """变量元数据"""
    
    def __init__(self, name: str, value: Any, source: str = "input", 
                 type_name: str = None, description: str = ""):
        self.name = name
        self.value = value
        self.source = source  # input, node_output, system, custom
        self.type_name = type_name or self._infer_type(value)
        self.description = description
    
    def _infer_type(self, value: Any) -> str:
        """推断变量类型"""
        if isinstance(value, str):
            return "string"
        elif isinstance(value, int):
            return "number"
        elif isinstance(value, float):
            return "number"
        elif isinstance(value, bool):
            return "boolean"
        elif isinstance(value, dict):
            return "object"
        elif isinstance(value, list):
            return "array"
        elif value is None:
            return "null"
        else:
            return "unknown"
    
    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return {
            "name": self.name,
            "type": self.type_name,
            "source": self.source,
            "description": self.description,
            "preview": self._get_preview()
        }
    
    def _get_preview(self) -> str:
        """获取变量预览值"""
        if isinstance(self.value, str):
            return self.value[:50] + "..." if len(self.value) > 50 else self.value
        elif isinstance(self.value, dict):
            return f"Object with {len(self.value)} keys"
        elif isinstance(self.value, list):
            return f"Array with {len(self.value)} items"
        else:
            return str(self.value)[:50]


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
        # 变量元数据存储
        self.variable_metadata: Dict[str, VariableMetadata] = {}
        
        # 初始化输入变量的元数据
        for key, value in self.inputs.items():
            self.variable_metadata[key] = VariableMetadata(
                name=key,
                value=value,
                source="input",
                description="工作流输入参数"
            )
    
    def set_variable(self, key: str, value: Any, source: str = "node_output", description: str = ""):
        """设置变量"""
        self.variables[key] = value
        
        # 更新或创建变量元数据
        if key in self.variable_metadata:
            self.variable_metadata[key].value = value
            self.variable_metadata[key].type_name = self.variable_metadata[key]._infer_type(value)
        else:
            self.variable_metadata[key] = VariableMetadata(
                name=key,
                value=value,
                source=source,
                description=description
            )
    
    def get_variable(self, key: str, default: Any = None) -> Any:
        """获取变量"""
        return self.variables.get(key, default)
    
    def get_variable_metadata(self, key: str) -> Optional[VariableMetadata]:
        """获取变量元数据"""
        return self.variable_metadata.get(key)
    
    def get_all_variables(self) -> Dict[str, Any]:
        """获取所有变量"""
        return self.variables.copy()
    
    def get_all_variable_metadata(self) -> List[Dict[str, Any]]:
        """获取所有变量的元数据列表"""
        return [meta.to_dict() for meta in self.variable_metadata.values()]
    
    def search_variables(self, query: str = "", type_filter: str = None, 
                        source_filter: str = None) -> List[Dict[str, Any]]:
        """搜索变量
        
        Args:
            query: 搜索关键词，匹配变量名或描述
            type_filter: 类型过滤（string, number, boolean, object, array）
            source_filter: 来源过滤（input, node_output, system, custom）
        
        Returns:
            匹配的变量元数据列表
        """
        results = []
        
        for meta in self.variable_metadata.values():
            # 关键词过滤
            if query:
                query_lower = query.lower()
                if query_lower not in meta.name.lower() and query_lower not in meta.description.lower():
                    continue
            
            # 类型过滤
            if type_filter and meta.type_name != type_filter:
                continue
            
            # 来源过滤
            if source_filter and meta.source != source_filter:
                continue
            
            results.append(meta.to_dict())
        
        # 按名称排序
        results.sort(key=lambda x: x["name"])
        
        return results
    
    def suggest_variables(self, prefix: str = "", limit: int = 10) -> List[Dict[str, Any]]:
        """根据前缀建议变量
        
        Args:
            prefix: 变量名前缀
            limit: 返回数量限制
        
        Returns:
            匹配的变量元数据列表
        """
        results = []
        
        for meta in self.variable_metadata.values():
            if meta.name.startswith(prefix):
                results.append(meta.to_dict())
        
        # 按名称排序并限制数量
        results.sort(key=lambda x: x["name"])
        return results[:limit]
    
    def update_node_status(self, node_id: str, status: ExecutionStatus):
        """更新节点状态"""
        self.node_statuses[node_id] = status


class NodeExecutor(ABC):
    """节点执行器基类"""
    
    NODE_TYPE = ""
    
    # 标准输出变量名，用于节点间数据传递
    OUTPUT_VAR_NAME = "__node_output__"
    
    def __init__(self, node: Dict[str, Any]):
        self.node = node
        self.node_id = node.get("id", "")
        self.node_data = node.get("data", {})
        # 显性配置的输入输出映射
        self.input_mappings = self.node_data.get("inputs", {})
        self.output_mappings = self.node_data.get("outputs", {})
    
    @abstractmethod
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        """执行节点，返回下一个节点ID列表"""
        pass
    
    def get_available_variables(self, context: WorkflowContext, 
                               type_filter: str = None, 
                               include_internal: bool = False) -> List[Dict[str, Any]]:
        """获取当前可用的变量列表
        
        Args:
            context: 工作流上下文
            type_filter: 类型过滤（string, number, boolean, object, array）
            include_internal: 是否包含内部变量（如 __node_output__）
        
        Returns:
            变量元数据列表，包含name, type, source, description, preview
        """
        variables = context.get_all_variable_metadata()
        
        # 过滤内部变量
        if not include_internal:
            variables = [v for v in variables if not v["name"].startswith("_")]
        
        # 类型过滤
        if type_filter:
            variables = [v for v in variables if v["type"] == type_filter]
        
        return variables
    
    def search_available_variables(self, context: WorkflowContext, 
                                  query: str = "", 
                                  type_filter: str = None) -> List[Dict[str, Any]]:
        """搜索可用变量
        
        Args:
            context: 工作流上下文
            query: 搜索关键词
            type_filter: 类型过滤
        
        Returns:
            匹配的变量元数据列表
        """
        return context.search_variables(query, type_filter)
    
    def suggest_variables(self, context: WorkflowContext, 
                         prefix: str = "", 
                         limit: int = 10) -> List[Dict[str, Any]]:
        """根据前缀建议变量
        
        Args:
            context: 工作流上下文
            prefix: 变量名前缀
            limit: 返回数量限制
        
        Returns:
            匹配的变量元数据列表
        """
        return context.suggest_variables(prefix, limit)
    
    def validate_variable(self, context: WorkflowContext, variable_name: str) -> Dict[str, Any]:
        """验证变量是否存在
        
        Args:
            context: 工作流上下文
            variable_name: 变量名
        
        Returns:
            验证结果字典，包含exists, type, preview
        """
        value = context.get_variable(variable_name)
        meta = context.get_variable_metadata(variable_name)
        
        if meta:
            return {
                "exists": True,
                "type": meta.type_name,
                "preview": meta._get_preview(),
                "source": meta.source,
                "description": meta.description
            }
        elif value is not None:
            # 变量存在但没有元数据
            return {
                "exists": True,
                "type": type(value).__name__,
                "preview": str(value)[:50],
                "source": "unknown",
                "description": ""
            }
        else:
            return {
                "exists": False,
                "type": None,
                "preview": None,
                "source": None,
                "description": None
            }
    
    def get_previous_output(self, context: WorkflowContext) -> Any:
        """获取前一个节点的输出
        
        这是节点间数据传递的标准方式，每个节点执行完毕后应该将结果
        存储到 OUTPUT_VAR_NAME 变量中，下一个节点通过此方法获取。
        """
        return context.get_variable(self.OUTPUT_VAR_NAME, "")
    
    def set_output(self, context: WorkflowContext, value: Any):
        """设置当前节点的输出
        
        将节点执行结果存储到标准输出变量中，供下一个节点使用。
        同时也将结果存储到 context.outputs 中供外部访问。
        
        如果配置了output_mappings，则按照映射关系设置变量。
        """
        context.set_variable(self.OUTPUT_VAR_NAME, value)
        # 同时更新节点专属输出（保持向后兼容）
        context.outputs[self.NODE_TYPE] = value
        
        # 处理显性输出映射
        if self.output_mappings:
            for target_var, source_expr in self.output_mappings.items():
                resolved_value = self._resolve_expression(source_expr, context, value)
                context.set_variable(target_var, resolved_value)
                context.outputs[target_var] = resolved_value
    
    def _resolve_expression(self, expr: str, context: WorkflowContext, node_output: Any = None) -> Any:
        """解析表达式，支持多种引用方式
        
        支持的表达式类型：
        1. {{variable_name}} - 引用上下文变量
        2. {{__node_output__}} - 引用前一个节点的输出
        3. {{__output__}} - 引用当前节点的输出
        4. {{__output__.field}} - 引用当前节点输出的字段
        5. 直接字符串常量
        """
        if not expr or not isinstance(expr, str):
            return expr
        
        # 去除首尾空格
        expr = expr.strip()
        
        # 检查是否是变量引用
        if expr.startswith("{{") and expr.endswith("}}"):
            var_path = expr[2:-2].strip()
            
            # 处理 __output__ 引用（当前节点输出）
            if var_path == "__output__":
                return node_output
            elif var_path.startswith("__output__."):
                # 支持 {{__output__.field_name}} 语法
                field_name = var_path[10:]
                if isinstance(node_output, dict) and field_name in node_output:
                    return node_output[field_name]
                return ""
            
            # 处理 __node_output__ 引用（前一个节点输出）
            if var_path == self.OUTPUT_VAR_NAME:
                return self.get_previous_output(context)
            elif var_path.startswith(self.OUTPUT_VAR_NAME + "."):
                field_name = var_path[len(self.OUTPUT_VAR_NAME) + 1:]
                prev_output = self.get_previous_output(context)
                if isinstance(prev_output, dict) and field_name in prev_output:
                    return prev_output[field_name]
                return ""
            
            # 尝试从上下文变量获取
            value = context.get_variable(var_path, None)
            if value is not None:
                return value
            
            # 尝试从前一个节点输出的字段获取
            prev_output = self.get_previous_output(context)
            if isinstance(prev_output, dict) and var_path in prev_output:
                return prev_output[var_path]
            
            return ""
        
        # 直接返回字符串常量
        return expr
    
    def resolve_inputs(self, context: WorkflowContext) -> Dict[str, Any]:
        """根据显性配置解析输入变量
        
        返回一个字典，包含所有配置的输入变量及其解析后的值。
        如果没有配置inputs，则返回空字典（使用默认行为）。
        """
        resolved = {}
        
        if not self.input_mappings:
            return resolved
        
        for input_key, source_expr in self.input_mappings.items():
            # 获取当前节点输出（可能还不存在，用于自引用）
            current_output = context.get_variable(self.OUTPUT_VAR_NAME, "")
            resolved[input_key] = self._resolve_expression(source_expr, context, current_output)
        
        return resolved
    
    def render_template(self, template: str, context: WorkflowContext) -> str:
        """渲染模板，替换变量
        
        支持的变量引用方式：
        1. {{variable_name}} - 标准模板语法
        2. {{{variable_name}}} - 三重花括号，用于需要保留花括号的场景
        3. {{variable_name.field}} - 访问对象字段
        4. {{variable_name[index]}} - 访问数组元素
        
        优先从上下文变量中获取值，如果找不到则尝试从前一个节点的输出中获取。
        """
        if not template:
            return ""
        
        def replace_var(match):
            var_expr = match.group(1).strip()
            
            # 解析变量表达式（支持 field 和 index 访问）
            value = self._resolve_variable_expression(var_expr, context)
            
            if value is not None:
                return str(value)
            
            # 尝试从前一个节点的输出中获取
            previous_output = self.get_previous_output(context)
            if isinstance(previous_output, dict) and var_expr in previous_output:
                return str(previous_output[var_expr])
            
            return ""
        
        # 支持 {{variable}} 语法（支持复杂表达式）
        result = re.sub(r"\{\{([^{}]+)\}\}", replace_var, template)
        
        # 支持 {{{variable}}} 语法（用于需要保留花括号的场景）
        def replace_triple_braces(match):
            var_expr = match.group(1).strip()
            value = self._resolve_variable_expression(var_expr, context)
            return "{{" + str(value if value is not None else "") + "}}"
        
        result = re.sub(r"\{\{\{([^{}]+)\}\}\}", replace_triple_braces, result)
        
        return result
    
    def _resolve_variable_expression(self, expr: str, context: WorkflowContext) -> Any:
        """解析变量表达式，支持字段访问和数组索引
        
        支持的表达式格式：
        - variable_name - 直接变量名
        - variable_name.field - 访问对象字段
        - variable_name[index] - 访问数组元素
        - variable_name.field[index] - 组合访问
        
        Args:
            expr: 变量表达式
            context: 工作流上下文
        
        Returns:
            解析后的值，如果解析失败返回None
        """
        if not expr:
            return None
        
        # 首先尝试直接获取变量
        value = context.get_variable(expr, None)
        if value is not None:
            return value
        
        # 解析嵌套表达式
        try:
            # 处理字段访问和数组索引
            parts = expr.split('.')
            current_value = context.get_variable(parts[0], None)
            
            if current_value is None:
                return None
            
            # 遍历剩余部分
            for part in parts[1:]:
                # 处理数组索引：field[index]
                if '[' in part and ']' in part:
                    field_name = part[:part.index('[')]
                    index_str = part[part.index('[')+1:part.index(']')]
                    
                    # 获取字段值
                    if isinstance(current_value, dict) and field_name in current_value:
                        current_value = current_value[field_name]
                    elif hasattr(current_value, field_name):
                        current_value = getattr(current_value, field_name)
                    
                    # 获取数组索引
                    try:
                        index = int(index_str)
                        if isinstance(current_value, list) and 0 <= index < len(current_value):
                            current_value = current_value[index]
                        else:
                            return None
                    except ValueError:
                        return None
                else:
                    # 普通字段访问
                    if isinstance(current_value, dict) and part in current_value:
                        current_value = current_value[part]
                    elif hasattr(current_value, part):
                        current_value = getattr(current_value, part)
                    else:
                        return None
            
            return current_value
        
        except Exception:
            return None


class StartNodeExecutor(NodeExecutor):
    """开始节点执行器"""
    
    NODE_TYPE = "start"
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        # 将输入参数设置到上下文中
        if context.inputs:
            for key, value in context.inputs.items():
                context.set_variable(key, value)
        
        # 设置初始输出（包含所有输入参数）
        self.set_output(context, context.inputs.copy())
        
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
        
        # 如果配置了显性输入映射，先解析输入变量
        if self.input_mappings:
            resolved_inputs = self.resolve_inputs(context)
            # 将解析的输入变量设置到上下文中
            for key, value in resolved_inputs.items():
                context.set_variable(key, value)
        
        prompt = self.node_data.get("prompt", "")
        rendered_prompt = self.render_template(prompt, context)
        
        # 将渲染后的提示词设置为下一个节点的输入（标准方式）
        context.set_variable("input", rendered_prompt)
        context.outputs["prompt"] = rendered_prompt
        
        # 使用标准输出变量传递数据（同时处理显性输出映射）
        self.set_output(context, rendered_prompt)
        
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
            
            # 如果配置了显性输入映射，先解析输入变量
            if self.input_mappings:
                resolved_inputs = self.resolve_inputs(context)
                # 将解析的输入变量设置到上下文中
                for key, value in resolved_inputs.items():
                    context.set_variable(key, value)
            
            # 获取输入（优先级：显性配置输入 > input变量 > 前一个节点输出 > output变量 > 节点自带prompt）
            prompt_input = ""
            
            # 检查显性配置的输入
            if self.input_mappings and "input" in self.input_mappings:
                prompt_input = self._resolve_expression(self.input_mappings["input"], context, "")
            
            # 如果没有显性配置，使用默认行为
            if not prompt_input:
                prompt_input = context.get_variable("input", "")
            
            # 如果没有输入，尝试从标准输出变量获取（前一个节点的输出）
            if not prompt_input:
                prompt_input = self.get_previous_output(context)
            
            # 如果仍然没有输入，尝试从output变量获取
            if not prompt_input:
                prompt_input = context.get_variable("output", "")
            
            # 如果仍然没有输入，使用节点自带的prompt
            if not prompt_input:
                node_prompt = self.node_data.get("prompt", "")
                if node_prompt:
                    prompt_input = self.render_template(node_prompt, context)
            
            # 验证输入
            if not prompt_input or not prompt_input.strip():
                raise ValueError("LLM节点需要输入内容，请确保前一个节点已正确设置input变量或在节点配置中设置prompt")
            
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
            
            # 设置输出变量（保持向后兼容）
            context.set_variable("output", result)
            context.outputs["llm_output"] = result
            
            # 使用标准输出变量传递数据（同时处理显性输出映射）
            self.set_output(context, result)
            
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
            # 如果配置了显性输入映射，先解析输入变量
            if self.input_mappings:
                resolved_inputs = self.resolve_inputs(context)
                for key, value in resolved_inputs.items():
                    context.set_variable(key, value)
            
            # 获取条件配置
            left_type = self.node_data.get("leftType", "variable")
            left_value = self.node_data.get("leftValue", "")
            operator = self.node_data.get("operator", "==")
            right_type = self.node_data.get("rightType", "constant")
            right_value = self.node_data.get("rightValue", "")
            
            # 获取左操作数
            if left_type == "variable":
                left_operand = context.get_variable(left_value, "")
                # 如果变量不存在，尝试从前一个节点的输出中获取
                if not left_operand and left_value != "":
                    prev_output = self.get_previous_output(context)
                    if isinstance(prev_output, dict) and left_value in prev_output:
                        left_operand = prev_output[left_value]
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
            
            # 使用标准输出变量传递条件结果（同时处理显性输出映射）
            self.set_output(context, {"condition_result": result, "input": left_operand})
            
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
        
        # 如果配置了显性输入映射，先解析输入变量
        if self.input_mappings:
            resolved_inputs = self.resolve_inputs(context)
            for key, value in resolved_inputs.items():
                context.set_variable(key, value)
        
        var_name = self.node_data.get("variableName", "result")
        var_value = self.node_data.get("variableValue", "")
        
        # 渲染变量值（支持模板）
        rendered_value = self.render_template(var_value, context)
        
        # 如果值引用了output变量，使用output
        if var_value == "{{output}}" or var_value == "output":
            rendered_value = context.get_variable("output", "")
        
        # 如果值引用了前一个节点的输出，使用标准输出变量
        if var_value == "{{__node_output__}}" or var_value == "__node_output__":
            rendered_value = self.get_previous_output(context)
        
        # 如果配置了显性输出映射，优先使用映射配置
        if self.output_mappings:
            # 遍历输出映射，设置变量
            for target_var, source_expr in self.output_mappings.items():
                resolved_value = self._resolve_expression(source_expr, context, rendered_value)
                context.set_variable(target_var, resolved_value)
                context.outputs[target_var] = resolved_value
            
            # 构建输出值
            output_value = {}
            for target_var in self.output_mappings.keys():
                output_value[target_var] = context.get_variable(target_var, "")
        else:
            # 使用传统方式设置变量
            context.set_variable(var_name, rendered_value)
            context.outputs[var_name] = rendered_value
            output_value = {var_name: rendered_value}
        
        # 使用标准输出变量传递数据
        self.set_output(context, output_value)
        
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
            # 如果配置了显性输入映射，先解析输入变量
            if self.input_mappings:
                resolved_inputs = self.resolve_inputs(context)
                for key, value in resolved_inputs.items():
                    context.set_variable(key, value)
            
            method = self.node_data.get("method", "GET").upper()
            url = self.render_template(self.node_data.get("url", ""), context)
            headers = self.node_data.get("headers", {})
            body = self.node_data.get("body", "")
            
            # 如果body是字符串模板，进行渲染
            if isinstance(body, str):
                body = self.render_template(body, context)
            
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
                    
                    http_result = {
                        "status": status,
                        "data": result,
                        "headers": dict(response.headers)
                    }
                    
                    context.set_variable("httpResult", http_result)
                    context.outputs["httpResult"] = http_result
                    
                    # 使用标准输出变量传递数据（同时处理显性输出映射）
                    self.set_output(context, http_result)
            
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
        
        # 如果配置了显性输入映射，先解析输入变量
        if self.input_mappings:
            resolved_inputs = self.resolve_inputs(context)
            for key, value in resolved_inputs.items():
                context.set_variable(key, value)
        
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
                    "__node_output__": self.get_previous_output(context),  # 添加前一个节点的输出
                }
                
                # 执行代码
                exec(code, {}, exec_locals)
                
                # 收集结果
                if "result" in exec_locals:
                    context.set_variable("codeResult", exec_locals["result"])
                    context.outputs["codeResult"] = exec_locals["result"]
                    
                    # 使用标准输出变量传递数据（同时处理显性输出映射）
                    self.set_output(context, exec_locals["result"])
                else:
                    # 如果没有显式设置result，使用前一个节点的输出作为默认输出
                    self.set_output(context, self.get_previous_output(context))
                
                # 更新上下文中的变量
                for key, value in exec_locals.items():
                    if key not in ["context", "variables", "__node_output__"]:
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
            # 如果配置了显性输入映射，先解析输入变量
            if self.input_mappings:
                resolved_inputs = self.resolve_inputs(context)
                for key, value in resolved_inputs.items():
                    context.set_variable(key, value)
            
            # 获取输入（优先级：显性配置输入 > output变量 > 前一个节点输出）
            input_data = ""
            
            # 检查显性配置的输入
            if self.input_mappings and "input" in self.input_mappings:
                input_data = self._resolve_expression(self.input_mappings["input"], context, "")
            
            # 如果没有显性配置，使用默认行为
            if not input_data:
                input_data = context.get_variable("output", "")
            
            # 如果没有输入，尝试从前一个节点的输出获取
            if not input_data:
                input_data = self.get_previous_output(context)
            
            # 如果input_data是字典，直接使用
            if isinstance(input_data, dict):
                parsed = input_data
                context.set_variable("parsed", parsed)
            else:
                # 尝试解析为JSON
                try:
                    parsed = json.loads(input_data)
                    context.set_variable("parsed", parsed)
                except (json.JSONDecodeError, TypeError):
                    # 如果不是JSON，尝试提取结构化信息
                    parsed = {"text": input_data}
                    context.set_variable("parsed", parsed)
            
            context.outputs["parsed"] = context.variables["parsed"]
            
            # 使用标准输出变量传递数据（同时处理显性输出映射）
            self.set_output(context, parsed)
            
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
            # 如果配置了显性输入映射，先解析输入变量
            if self.input_mappings:
                resolved_inputs = self.resolve_inputs(context)
                for key, value in resolved_inputs.items():
                    context.set_variable(key, value)
            
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
            
            # 使用标准输出变量传递数据（同时处理显性输出映射）
            self.set_output(context, result)
            
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
        
        # 创建消息队列
        import asyncio
        message_queue = asyncio.Queue()
        
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
            
            # 异步执行节点，并收集消息
            async def run_and_collect():
                await self._execute_node_streaming(start_node["id"], context, message_queue.put)
            
            # 并行处理执行和输出
            async def process_queue():
                while True:
                    msg = await message_queue.get()
                    if msg is None:
                        break
                    yield msg
                    message_queue.task_done()
            
            # 启动执行任务
            exec_task = asyncio.create_task(run_and_collect())
            
            # 处理队列中的消息
            async for msg in process_queue():
                yield msg
            
            # 等待执行完成
            await exec_task
            
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
        await yield_fn({
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
        await yield_fn({
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