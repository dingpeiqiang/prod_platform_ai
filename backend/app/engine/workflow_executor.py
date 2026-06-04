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
from app.core.logger import get_logger

logger = get_logger(__name__)
import re
from enum import Enum

from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import JsonOutputParser, StrOutputParser

from app.langchain.llm_wrapper import get_langchain_llm
from app.core.config_loader import config_loader
from app.services.recommendations import HighPerformanceRecommendationEngine



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
    """节点执行器基类
    
    实现此基类来创建自定义节点类型，遵循节点自治原则：
    - 每个节点自己管理执行逻辑和配置
    - 新增节点不需要修改编辑器和工作流执行器
    - 通过注册机制自动发现
    
    子类必须实现：
    - NODE_TYPE: 节点类型标识符（唯一）
    - execute(): 执行节点逻辑
    - get_node_metadata(): 返回节点元数据（用于编辑器）
    """
    
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
    
    @classmethod
    def get_node_metadata(cls) -> Dict[str, Any]:
        """获取节点元数据，用于编辑器显示
        
        Returns:
            节点元数据字典，包含：
            - type: 节点类型（与NODE_TYPE一致）
            - label: 显示名称
            - category: 分类（start, end, action, control, data等）
            - icon: 图标名称
            - description: 描述
            - inputs: 输入参数定义
            - outputs: 输出参数定义
            - config: 配置项定义
        """
        return {
            "type": cls.NODE_TYPE,
            "label": cls.NODE_TYPE,
            "category": "action",
            "icon": "circle",
            "description": "",
            "inputs": [],
            "outputs": [],
            "config": []
        }
    
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
        
        输出格式: {'output': <实际结果>}，支持表达式引用格式: node-id.output.field
        """
        # 包装输出格式为 {'output': value}，支持表达式引用
        wrapped_output = {"output": value} if value is not None else {"output": {}}
        
        context.set_variable(self.OUTPUT_VAR_NAME, wrapped_output)
        # 同时更新节点专属输出（保持向后兼容）
        context.outputs[self.NODE_TYPE] = wrapped_output
        
        # 保存原始输出到节点专属变量（保持向后兼容）
        context.set_variable(f"{self.node_id}_output", value)
        
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
                # 返回原始输出值（去掉 output 包装）
                prev_output = self.get_previous_output(context)
                if isinstance(prev_output, dict) and "output" in prev_output:
                    return prev_output["output"]
                return prev_output
            elif var_path.startswith(self.OUTPUT_VAR_NAME + "."):
                field_name = var_path[len(self.OUTPUT_VAR_NAME) + 1:]
                prev_output = self.get_previous_output(context)
                # 从 output 字段中获取实际数据
                actual_output = prev_output.get("output", prev_output) if isinstance(prev_output, dict) else prev_output
                if isinstance(actual_output, dict) and field_name in actual_output:
                    return actual_output[field_name]
                return ""
            
            # 尝试从上下文变量获取
            value = context.get_variable(var_path, None)
            if value is not None:
                return value
            
            # 尝试从前一个节点输出的字段获取（支持新的 output 包装格式）
            prev_output = self.get_previous_output(context)
            actual_output = prev_output.get("output", prev_output) if isinstance(prev_output, dict) else prev_output
            if isinstance(actual_output, dict) and var_path in actual_output:
                return actual_output[var_path]
            
            return ""
        
        # 直接返回字符串常量
        return expr
    
    def resolve_inputs(self, context: WorkflowContext) -> Dict[str, Any]:
        """根据显性配置解析输入变量

        返回一个字典，包含所有配置的输入变量及其解析后的值。
        如果没有配置inputs，则返回空字典（使用默认行为）。

        支持两种 inputs 格式：
        1. 字典格式: {"input": "{{variable}}", "param1": "value"}
        2. 数组格式: [{"name": "param1", "valueType": "input", "defaultValue": "xxx"}, ...]
        """
        resolved = {}

        if not self.input_mappings:
            return resolved

        # 如果是数组格式（前端发送的格式）
        if isinstance(self.input_mappings, list):
            for param in self.input_mappings:
                if not param or not param.get("name"):
                    continue
                
                param_name = param["name"]
                value_type = param.get("valueType", "input")
                
                if value_type == "input":
                    # 直接输入类型，使用 defaultValue
                    resolved[param_name] = param.get("defaultValue", "")
                elif value_type == "reference":
                    # 引用类型，需要解析引用的变量
                    ref_value = param.get("refValue", "")
                    if ref_value:
                        current_output = context.get_variable(self.OUTPUT_VAR_NAME, "")
                        resolved[param_name] = self._resolve_expression(ref_value, context, current_output)
                    else:
                        resolved[param_name] = ""
        
        # 如果是字典格式（原有格式）
        elif isinstance(self.input_mappings, dict):
            for input_key, source_expr in self.input_mappings.items():
                # 获取当前节点输出（可能还不存在，用于自引用）
                current_output = context.get_variable(self.OUTPUT_VAR_NAME, "")
                resolved[input_key] = self._resolve_expression(source_expr, context, current_output)

        return resolved
    
    def render_template(self, template: str, context: WorkflowContext) -> str:
        """渲染模板，替换变量
        
        支持的变量引用方式：
        1. {{variable_name}} - 标准模板语法（推荐）
        2. {variable_name} - 简化语法（兼容前端）
        3. {{{variable_name}}} - 三重花括号，用于需要保留花括号的场景
        4. {{variable_name.field}} - 访问对象字段
        5. {{variable_name[index]}} - 访问数组元素
        
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
        
        # 先处理三重花括号 {{{variable}}}（避免被单花括号替换影响）
        def replace_triple_braces(match):
            var_expr = match.group(1).strip()
            value = self._resolve_variable_expression(var_expr, context)
            return "{{" + str(value if value is not None else "") + "}}"
        
        result = re.sub(r"\{\{\{([^{}]+)\}\}\}", replace_triple_braces, template)
        
        # 支持 {variable} 语法（简化语法，兼容前端）
        # 使用负向前瞻，避免匹配已经是 {{variable}} 的情况
        result = re.sub(r"\{([^{}]+)\}(?!\})", replace_var, result)
        
        # 支持 {{variable}} 语法（支持复杂表达式）
        result = re.sub(r"\{\{([^{}]+)\}\}", replace_var, result)
        
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
            
            # 获取输入内容
            prompt_input = ""
            node_prompt = self.node_data.get("prompt", "")
            
            # 检查显性配置的输入（字典格式的 input_mappings 中的 "input" 键）
            if self.input_mappings and isinstance(self.input_mappings, dict) and "input" in self.input_mappings:
                prompt_input = self._resolve_expression(self.input_mappings["input"], context, "")
            
            # 如果没有显性配置，从上下文获取输入
            if not prompt_input:
                prompt_input = context.get_variable("input", "")
            
            # 如果没有输入，尝试从标准输出变量获取（前一个节点的输出）
            if not prompt_input:
                prompt_input = self.get_previous_output(context)
            
            # 如果仍然没有输入，尝试从output变量获取
            if not prompt_input:
                prompt_input = context.get_variable("output", "")
            
            # 始终使用节点自带的prompt作为基础模板，将输入内容嵌入其中
            if node_prompt:
                # 渲染节点prompt（支持变量引用）
                rendered_prompt = self.render_template(node_prompt, context)
                # 如果有额外的输入内容，将其拼接到prompt中
                if prompt_input and prompt_input.strip():
                    prompt_input = rendered_prompt.replace("{input}", prompt_input) if "{input}" in rendered_prompt else rendered_prompt + "\n" + prompt_input
                else:
                    prompt_input = rendered_prompt
            
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
            
            # 优先检测新的 branches 格式（前端 Vue Flow 风格）
            branches = self.node_data.get("branches", [])
            
            if branches:
                # 使用新的 branches 格式评估
                result, matched_handle = await self._evaluate_branches(context)
                context.set_variable("condition_result", result)
                context.set_variable("matched_handle", matched_handle)
                self.set_output(context, {"condition_result": result, "matched_handle": matched_handle})
                
                logger.info(f"Condition (branches format) evaluated: matched_handle={matched_handle}, result={result}")
                
                # 如果没有匹配任何分支，记录警告
                if not result:
                    logger.warning(f"Condition node {self.node_id} has no matching branch")
                
                # 根据匹配的分支 handle 选择输出边
                next_nodes = [
                    e["target"] for e in edges 
                    if e["source"] == self.node_id and e.get("sourceHandle") == matched_handle
                ]
                
                # 如果没有找到对应分支的边，尝试兼容旧格式
                if not next_nodes:
                    logger.debug(f"No edges found for handle {matched_handle}, trying legacy format")
                    output_key = "true" if result else "false"
                    next_nodes = [
                        e["target"] for e in edges 
                        if e["source"] == self.node_id and e.get("sourceHandle") == output_key
                    ]
                
                # 如果仍然没有找到边，记录错误
                if not next_nodes:
                    logger.error(f"Condition node {self.node_id} has no outgoing edges for handle {matched_handle}")
            else:
                # 兼容旧格式：leftType/leftValue/operator/rightType/rightValue
                result, matched_branch_index = await self._evaluate_legacy_format(context)
                context.set_variable("condition_result", result)
                self.set_output(context, {"condition_result": result, "input": result})
                
                logger.info(f"Condition (legacy format) evaluated: {result}")
                
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
    
    async def _evaluate_branches(self, context: WorkflowContext) -> tuple:
        """评估 branches 格式的条件（前端 Vue Flow 风格）
        
        Args:
            context: 工作流上下文
            
        Returns:
            (result, handle): 条件结果和匹配分支的 handle
        """
        branches = self.node_data.get("branches", [])
        
        for branch_index, branch in enumerate(branches):
            branch_type = branch.get("type", "")
            conditions = branch.get("conditions", [])
            
            # 获取分支的 handle（优先使用分支定义中的 handle，否则生成默认值）
            branch_handle = branch.get("handle")
            if not branch_handle:
                if branch_type == "else":
                    branch_handle = "branch_else"
                else:
                    branch_handle = f"branch_{branch_index}"
            
            # "否则"分支直接匹配
            if branch_type == "else":
                return True, branch_handle
            
            # 评估当前分支的所有条件（AND 关系）
            # 注意：all_conditions_met 需要在分支循环开始时重置为 True
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
                    
                    # 获取左操作数（变量值）
                    left_operand = self._get_variable_value(var_name, context)
                    
                    # 获取右操作数
                    if value_type == "reference":
                        # 引用变量
                        right_operand = self._get_variable_value(value, context)
                    else:
                        # 直接输入值
                        right_operand = value
                    
                    # 执行条件判断
                    if not self._evaluate_condition(left_operand, operator, right_operand):
                        all_conditions_met = False
                        break
                
                if all_conditions_met:
                    return True, branch_handle
        
        # 没有匹配任何分支，返回 False 和默认 handle
        return False, "branch_0"
    
    async def _evaluate_legacy_format(self, context: WorkflowContext) -> tuple:
        """评估旧格式的条件（leftType/leftValue/operator/rightType/rightValue）
        
        Args:
            context: 工作流上下文
            
        Returns:
            (result, matched_branch_index): 条件结果（始终返回0作为分支索引）
        """
        left_type = self.node_data.get("leftType", "variable")
        left_value = self.node_data.get("leftValue", "")
        operator = self.node_data.get("operator", "==")
        right_type = self.node_data.get("rightType", "constant")
        right_value = self.node_data.get("rightValue", "")
        
        # 获取左操作数（使用统一的变量获取方法）
        if left_type == "variable":
            left_operand = self._get_variable_value(left_value, context)
        else:
            left_operand = left_value
        
        # 获取右操作数（使用统一的变量获取方法）
        if right_type == "variable":
            right_operand = self._get_variable_value(right_value, context)
        else:
            right_operand = right_value
        
        result = self._evaluate_condition(left_operand, operator, right_operand)
        return result, 0
    
    def _get_variable_value(self, var_path: str, context: WorkflowContext) -> Any:
        """获取变量值，支持多种引用格式：
        
        1. 普通变量名：直接从上下文获取
        2. 节点ID + 输出路径：如 "code-211a6b31.output.tariff_code"
           从指定节点的输出中获取值
        
        Args:
            var_path: 变量路径
            context: 工作流上下文
            
        Returns:
            变量值，如果未找到返回空字符串
        """
        if not var_path:
            return ""
        
        # 尝试从上下文直接获取
        value = context.get_variable(var_path, None)
        if value is not None:
            return value
        
        # 尝试从前一个节点输出获取
        prev_output = self.get_previous_output(context)
        if isinstance(prev_output, dict) and var_path in prev_output:
            return prev_output[var_path]
        
        # 检查是否是节点ID + 输出路径格式（如 "code-211a6b31.output.tariff_code"）
        # 节点ID通常包含短横线，这是区分普通变量和节点引用的特征
        if '-' in var_path:
            parts = var_path.split('.', 1)  # 分割成2部分：节点ID 和 剩余路径
            if len(parts) >= 2:
                node_id = parts[0]
                rest_path = parts[1]
                
                # 从节点输出中查找
                node_output = context.get_node_output(node_id)
                if node_output:
                    # 解析剩余路径
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


class UserInputNodeExecutor(NodeExecutor):
    """用户输入节点执行器 - 暂停工作流等待用户反馈"""
    
    NODE_TYPE = "user_input"
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        # 设置暂停标志，通知执行器暂停
        context.status = ExecutionStatus.PENDING
        context.current_node_id = self.node_id
        
        # 保存需要收集的信息
        prompt = self.node_data.get("prompt", "请输入反馈：")
        input_type = self.node_data.get("inputType", "text")
        options = self.node_data.get("options", "")
        required = self.node_data.get("required", True)
        output_var = self.node_data.get("outputVar", "user_input")
        validation_enabled = self.node_data.get("validationEnabled", self.node_data.get("validation_enabled", False))
        validation_rules = self.node_data.get("validationRules", self.node_data.get("validation_rules", []))
        validation_error_message = self.node_data.get("validationErrorMessage", self.node_data.get("validation_error_message", ""))
        
        # 将暂停信息存入上下文，供外部调用获取
        context.set_variable(
            "__pending_input__",
            {
                "node_id": self.node_id,
                "prompt": prompt,
                "input_type": input_type,
                "options": [opt.strip() for opt in options.split('\n') if opt.strip()] if options else [],
                "required": required,
                "output_var": output_var,
                "validation_enabled": validation_enabled,
                "validation_rules": validation_rules,
                "validation_error_message": validation_error_message
            }
        )
        
        logger.info(f"Workflow paused waiting for user input at node: {self.node_id}")
        
        # 返回空列表，触发执行器暂停
        return []


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
        output_params = self.node_data.get("outputParams", [])
        
        try:
            if language == "python":
                # 创建安全的执行环境（仅暴露必要的变量）
                exec_locals = {
                    "context": context,
                    "variables": context.variables,
                    "output": context.get_variable("output", ""),
                    "input": context.get_variable("input", ""),
                    "__node_output__": self.get_previous_output(context),
                    "set_var": lambda k, v: context.set_variable(k, v),
                    "get_var": lambda k, d=None: context.get_variable(k, d),
                }
                
                # 执行代码
                exec(code, {}, exec_locals)
                
                # 收集结果 - 仅处理显式的 result 变量
                if "result" in exec_locals:
                    result_value = exec_locals["result"]
                    context.set_variable("codeResult", result_value)
                    context.outputs["codeResult"] = result_value
                    
                    # 使用标准输出变量传递数据（同时处理显性输出映射）
                    self.set_output(context, result_value)
                    
                    # 如果配置了输出参数，验证并提取指定字段
                    if output_params and isinstance(output_params, list):
                        for param in output_params:
                            param_name = param.get("name")
                            if param_name and isinstance(result_value, dict) and param_name in result_value:
                                context.set_variable(param_name, result_value[param_name])
                else:
                    # 如果没有显式设置result，使用前一个节点的输出作为默认输出
                    self.set_output(context, self.get_previous_output(context))
            
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
        """通用工具调用 - 支持 MCP 工具动态执行"""
        from app.mcp_tools import get_toolhub

        hub = get_toolhub()

        # 优先通过 MCPToolHub 执行真实的 MCP 工具
        if hub.has_tool(tool_type):
            logger.info(f"[ToolNodeExecutor] 执行 MCP 工具: {tool_type}, 参数: {params}")
            result = hub.execute_sync(tool_type, params)
            return result

        # 兜底：未被识别的工具类型（兼容旧占位符逻辑）
        logger.warning(f"[ToolNodeExecutor] 工具 '{tool_type}' 不在 MCP Hub 中，使用 placeholder 返回")
        return {
            "toolType": tool_type,
            "params": params,
            "message": f"工具 '{tool_type}' 执行完成（placeholder）"
        }
    
    def _get_next_nodes(self, edges: List[Dict[str, Any]]) -> List[str]:
        return [e["target"] for e in edges if e["source"] == self.node_id]


class FormNodeExecutor(NodeExecutor):
    """表单生成节点执行器 - 重构版
    
    功能特性：
    1. 基于本体生成表单结构
    2. 智能推荐初始化表单（通过推荐引擎）
    3. 大模型智能校验（可选）
    4. 通过 MCP 工具提交
    """
    
    NODE_TYPE = "form"
    
    def __init__(self, node: Dict[str, Any]):
        super().__init__(node)
        self.llm = None
    
    def _get_llm(self):
        """延迟初始化 LLM，只在需要时初始化"""
        if self.llm is None:
            try:
                self.llm = get_langchain_llm().llm
            except Exception as e:
                logger.warning(f"[FormNodeExecutor] LLM 初始化失败: {e}")
                raise RuntimeError(f"无法初始化 LLM: {e}")
        return self.llm
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        try:
            # 如果配置了显性输入映射，先解析输入变量
            if self.input_mappings:
                resolved_inputs = self.resolve_inputs(context)
                for key, value in resolved_inputs.items():
                    context.set_variable(key, value)
            
            # 获取配置
            ontology_code = self.node_data.get("ontologyCode", "")
            tool_name = self.node_data.get("toolType", "")
            
            # 获取大模型配置（用于表单智能推荐和智能校验）
            model = self.node_data.get("model", "qwen-plus")
            temperature = self.node_data.get("temperature", 0.3)
            input_variable = self.node_data.get("inputVariable", "")
            
            if not ontology_code:
                raise ValueError("表单节点必须配置 ontologyCode")
            
            logger.info(f"[FormNodeExecutor] 执行表单节点，本体: {ontology_code}, 提交工具: {tool_name or '未配置'}, 模型: {model}, 温度: {temperature}")
            
            # 获取本体定义
            ontology = config_loader.get_ontology(ontology_code)
            if not ontology:
                raise ValueError(f"未找到本体定义: {ontology_code}")
            
            # 步骤 1: 根据本体生成表单结构
            form_schema = self._generate_form_schema(ontology)
            
            # 步骤 2: 通过智能推荐初始化表单数据（使用大模型）
            form_data = await self._initialize_form_data_with_recommendations(ontology, context, model, temperature, input_variable)
            
            # 设置结果到上下文（不执行智能校验和工具提交，由后续动作触发）
            context.set_variable("form_schema", form_schema)
            context.set_variable("form_data", form_data)
            context.set_variable("ontology_code", ontology_code)
            context.set_variable("form_validation", None)
            context.set_variable("form_submit_result", None)
            
            context.outputs["form_schema"] = form_schema
            context.outputs["form_data"] = form_data
            context.outputs["ontology_code"] = ontology_code
            context.outputs["form_validation"] = None
            context.outputs["form_submit_result"] = None
            
            # 使用标准输出变量传递数据（同时处理显性输出映射）
            self.set_output(context, {
                "form_schema": form_schema,
                "form_data": form_data,
                "ontology_code": ontology_code,
                "form_validation": None,
                "form_submit_result": None
            })
            
            logger.info(f"[FormNodeExecutor] 表单初始化完成: {len(form_data)} 个字段")
            
            context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
            
        except Exception as e:
            logger.exception(f"[FormNodeExecutor] 表单节点执行失败: {e}")
            context.error = str(e)
            context.update_node_status(self.node_id, ExecutionStatus.FAILED)
            raise
        
        return self._get_next_nodes(edges)
    
    def _generate_form_schema(self, ontology: Dict[str, Any]) -> Dict[str, Any]:
        """根据本体生成表单结构"""
        form_schema = {
            "formName": ontology.get("ontologyName", "表单"),
            "formCode": ontology.get("ontologyCode", ""),
            "ontologyCode": ontology.get("ontologyCode", ""),
            "ontologyName": ontology.get("ontologyName", ""),
            "description": ontology.get("description", ""),
            "entities": [],
            "fields": []
        }
        
        # 遍历本体中的实体和字段
        for entity in ontology.get("entities", []):
            entity_info = {
                "entityCode": entity.get("entityCode", ""),
                "entityName": entity.get("entityName", ""),
                "fields": []
            }
            
            for field in entity.get("fields", []):
                field_def = {
                    "fieldCode": field.get("fieldCode"),
                    "fieldName": field.get("fieldName"),
                    "fieldType": field.get("fieldType", "string"),
                    "required": field.get("required", False),
                    "default": field.get("default", ""),
                    "description": field.get("description", ""),
                    "options": field.get("options", []),
                    "validation": field.get("validation", {}),
                    "placeholder": field.get("placeholder", "")
                }
                
                entity_info["fields"].append(field_def)
                form_schema["fields"].append(field_def)
            
            form_schema["entities"].append(entity_info)
        
        return form_schema
    
    async def _initialize_form_data_with_recommendations(self, ontology: Dict[str, Any], context: WorkflowContext, model: str = "qwen-plus", temperature: float = 0.3, input_variable: str = "") -> Dict[str, Any]:
        """通过智能推荐初始化表单数据（使用高性能推荐引擎）"""
        form_data = {}
        
        # 兼容 ontologyCode 和 formCode 两种命名
        ontology_code = ontology.get("ontologyCode") or ontology.get("formCode", "")
        
        logger.info(f"[FormNodeExecutor] 开始初始化表单数据，ontology: {ontology_code}, 模型: {model}, 温度: {temperature}")
        
        # 获取上下文变量和工具结果
        context_values = {}
        tool_result = context.get_variable("tariff_info", {})
        
        # 收集所有上下文变量
        all_vars = context.get_all_variables()
        for var_name, var_value in all_vars.items():
            context_values[var_name] = var_value
        
        # 获取用户输入
        user_input = context.get_variable(input_variable, "") or context.get_variable("user_input", "")
        user_id = context.get_variable("user_id", "default")
        
        # 使用高性能推荐引擎
        high_perf_engine = HighPerformanceRecommendationEngine()
        
        try:
            form_data, decision_log = await high_perf_engine.recommend_form_data(
                form_code=ontology_code,
                ontology=ontology,
                user_input=user_input,
                user_id=user_id,
                context_values=context_values,
                tool_result=tool_result
            )
            
            logger.info(f"[FormNodeExecutor] 高性能推荐引擎执行完成")
            
            # 记录决策日志
            for field_code, log in decision_log.items():
                source = log.get("source", "unknown")
                confidence = log.get("confidence", 0.0)
                reason = log.get("reason", "")
                
                if log.get("is_definite", False):
                    logger.info(f"[FormNodeExecutor] 字段 {field_code} 使用确定性值 (来源: {source})")
                else:
                    rec_count = log.get("recommendations_count", 0)
                    if rec_count > 0:
                        logger.info(f"[FormNodeExecutor] 字段 {field_code} 使用推荐值 (来源: {source}, 置信度: {confidence:.2f}, 理由: {reason})")
                    else:
                        logger.info(f"[FormNodeExecutor] 字段 {field_code} 无推荐值")
        
        except Exception as e:
            logger.error(f"[FormNodeExecutor] 高性能推荐引擎执行失败: {e}")
            # 降级到旧逻辑
            form_data = await self._initialize_form_data_fallback(ontology, context, model, temperature, input_variable)
        
        logger.info(f"[FormNodeExecutor] 表单数据初始化完成，共 {len(form_data)} 个字段")
        return form_data
    
    async def _initialize_form_data_fallback(self, ontology: Dict[str, Any], context: WorkflowContext, model: str = "qwen-plus", temperature: float = 0.3, input_variable: str = "") -> Dict[str, Any]:
        """降级方案：使用旧逻辑初始化表单数据"""
        form_data = {}
        
        logger.info(f"[FormNodeExecutor] 使用降级方案初始化表单数据")
        
        # 获取推荐引擎
        rec_engine = None
        try:
            from app.services.recommendation_engine import get_recommendation_engine
            rec_engine = get_recommendation_engine()
        except Exception as e:
            logger.warning(f"[FormNodeExecutor] 无法获取推荐引擎: {e}")
        
        ontology_code = ontology.get("ontologyCode", "")
        
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_code = field.get("fieldCode")
                if not field_code:
                    continue
                
                value = None
                
                # 1. 尝试从上下文变量中获取
                if not value:
                    value = context.get_variable(field_code)
                
                # 2. 尝试从工具结果中获取
                if not value:
                    tool_result = context.get_variable("tariff_info", {})
                    mapping = field.get("mapping")
                    if tool_result:
                        if mapping and mapping in tool_result:
                            value = tool_result[mapping]
                        elif field_code in tool_result:
                            value = tool_result[field_code]
                
                # 3. 使用推荐引擎
                if not value and rec_engine:
                    try:
                        user_input = context.get_variable("user_input", "")
                        user_id = context.get_variable("user_id", "default")
                        
                        rec_result = rec_engine.recommend(
                            form_code=ontology_code,
                            field_code=field_code,
                            user_input=user_input,
                            user_id=user_id
                        )
                        
                        if rec_result and rec_result.recommendations and len(rec_result.recommendations) > 0:
                            value = rec_result.recommendations[0].value
                    except Exception as e:
                        logger.warning(f"[FormNodeExecutor] 字段 {field_code} 推荐失败: {e}")
                
                # 4. 使用枚举选项
                if not value:
                    enum_options = self._get_enum_options(field)
                    if enum_options and len(enum_options) > 0:
                        first_option = enum_options[0]
                        if isinstance(first_option, dict):
                            value = first_option.get('value')
                        else:
                            value = first_option
                
                # 5. 使用默认值
                if value is None:
                    value = field.get("default", "")
                
                form_data[field_code] = value
        
        return form_data
    
    async def _extract_field_value_with_llm(self, field: Dict[str, Any], user_input: str, llm) -> Optional[str]:
        """使用大模型从用户输入中提取字段值"""
        from langchain_core.prompts import ChatPromptTemplate
        from langchain_core.output_parsers import StrOutputParser
        
        field_code = field.get("fieldCode", "")
        field_name = field.get("fieldName", field_code)
        field_type = field.get("fieldType", "string")
        field_desc = field.get("description", "")
        enum_options = self._get_enum_options(field)
        
        # 构建提示词
        prompt_parts = [
            f"你是一个智能表单助手，需要从用户输入中提取特定字段的值。\n\n",
            f"字段信息：\n",
            f"- 字段名称：{field_name}\n",
            f"- 字段编码：{field_code}\n",
            f"- 字段类型：{field_type}\n"
        ]
        
        if field_desc:
            prompt_parts.append(f"- 字段描述：{field_desc}\n")
        
        if enum_options:
            valid_values = []
            for opt in enum_options:
                if isinstance(opt, dict):
                    valid_values.append(f"{opt.get('value')} ({opt.get('label', '')})")
                else:
                    valid_values.append(str(opt))
            prompt_parts.append(f"- 可选值：{', '.join(valid_values)}\n")
        
        prompt_parts.extend([
            f"\n用户输入：\n{user_input}\n\n",
            f"请提取上述用户输入中与该字段最匹配的值。",
            f"如果找不到合适的值，请直接返回空字符串。",
            f"只返回提取到的值，不要解释。",
            f"\n提取结果："
        ])
        
        prompt = "".join(prompt_parts)
        
        # 调用大模型
        chain = ChatPromptTemplate.from_messages([("user", prompt)]) | llm | StrOutputParser()
        result = await chain.ainvoke({})
        
        # 清理结果
        result = result.strip()
        
        # 如果结果为空或无效，返回 None
        if not result or result.lower() in ["null", "none", "无", "找不到", ""]:
            return None
        
        return result
    
    def _create_llm(self, model: str, temperature: float = 0.3):
        """创建大模型实例"""
        try:
            from langchain_openai import ChatOpenAI
            from app.core.database import SessionLocal
            from app.models.llm_user_config import LLMUserConfig
            
            # 支持两种格式：带前缀的模型ID（如 custom-minimax-m2.7）和不带前缀的模型名称（如 minimax-m2.7）
            model_name = model
            if model_name.startswith('custom-'):
                model_name = model_name[7:]  # 移除 custom- 前缀
            
            # 从数据库获取配置
            db = SessionLocal()
            try:
                model_db_config = db.query(LLMUserConfig).filter(
                    LLMUserConfig.model == model_name,
                    LLMUserConfig.is_active == True
                ).first()
                
                if not model_db_config:
                    raise ValueError(f"未找到模型配置: {model}。请先在'模型配置'面板中添加该模型的配置。")
                
                # 使用数据库配置
                api_key = (model_db_config.api_key or '').strip().strip('`')
                base_url = (model_db_config.base_url or '')
                
                logger.info(f"[FormNodeExecutor] 从数据库找到模型配置: {model}")
            finally:
                db.close()
            
            # 创建 LLM 实例
            llm = ChatOpenAI(
                model=model_name,
                temperature=temperature,
                api_key=api_key,
                base_url=base_url,
                max_retries=2,
                timeout=30
            )
            
            logger.info(f"[FormNodeExecutor] 成功创建 LLM 实例: {model}, 温度: {temperature}")
            return llm
        except Exception as e:
            logger.error(f"[FormNodeExecutor] 创建 LLM 实例失败: {e}")
            raise
    
    def _get_enum_options(self, field: Dict[str, Any]) -> List:
        """获取字段的枚举选项"""
        # 优先从 enumConfig.options 获取
        enum_config = field.get("enumConfig", {})
        if enum_config:
            options = enum_config.get("options", [])
            if options:
                return options
        
        # 尝试直接从 options 字段获取
        options = field.get("options", [])
        if options:
            return options
        
        return []
    
    async def _validate_form_data(self, ontology: Dict[str, Any], form_data: Dict[str, Any], model: str = "qwen-plus", temperature: float = 0.3) -> Dict[str, Any]:
        """基于本体执行智能校验（使用大模型）"""
        validation_result = {
            "is_valid": True,
            "errors": [],
            "warnings": [],
            "info": []
        }
        
        logger.info(f"[FormNodeExecutor] 开始智能校验，模型: {model}, 温度: {temperature}")
        
        # 遍历本体中的实体和字段进行基础校验
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_code = field.get("fieldCode")
                field_name = field.get("fieldName", field_code)
                value = form_data.get(field_code, "")
                
                # 必填校验
                required = field.get("required", False)
                if required and (value is None or value == "" or (isinstance(value, str) and value.strip() == "")):
                    validation_result["errors"].append({
                        "field": field_code,
                        "fieldName": field_name,
                        "message": "此字段不能为空",
                        "type": "required"
                    })
                    validation_result["is_valid"] = False
                    continue
                
                # 空值跳过后续校验
                if value is None or value == "" or (isinstance(value, str) and value.strip() == ""):
                    continue
                
                # 类型校验
                field_type = field.get("fieldType", "string")
                try:
                    if field_type == "number" or field_type == "integer":
                        if not str(value).replace(".", "", 1).isdigit():
                            validation_result["errors"].append({
                                "field": field_code,
                                "fieldName": field_name,
                                "message": "必须是数字类型",
                                "type": "type"
                            })
                            validation_result["is_valid"] = False
                    elif field_type == "boolean":
                        if not isinstance(value, bool) and str(value).lower() not in ["true", "false", "1", "0", "是", "否"]:
                            validation_result["errors"].append({
                                "field": field_code,
                                "fieldName": field_name,
                                "message": "必须是布尔类型",
                                "type": "type"
                            })
                            validation_result["is_valid"] = False
                except Exception as e:
                    logger.debug(f"[FormNodeExecutor] 类型校验异常: {e}")
                
                # 长度校验
                max_length = field.get("maxLength")
                min_length = field.get("minLength")
                if max_length and len(str(value)) > max_length:
                    validation_result["errors"].append({
                        "field": field_code,
                        "fieldName": field_name,
                        "message": f"长度超过限制（最大{max_length}字符）",
                        "type": "max_length"
                    })
                    validation_result["is_valid"] = False
                if min_length and len(str(value)) < min_length:
                    validation_result["errors"].append({
                        "field": field_code,
                        "fieldName": field_name,
                        "message": f"长度不足（最小{min_length}字符）",
                        "type": "min_length"
                    })
                    validation_result["is_valid"] = False
                
                # 正则校验
                validation = field.get("validation", {})
                pattern = validation.get("pattern")
                if pattern:
                    import re
                    try:
                        if not re.match(pattern, str(value)):
                            validation_result["errors"].append({
                                "field": field_code,
                                "fieldName": field_name,
                                "message": validation.get("patternError", "格式不正确"),
                                "type": "pattern"
                            })
                            validation_result["is_valid"] = False
                    except Exception as e:
                        logger.warning(f"[FormNodeExecutor] 正则校验异常: {e}")
                
                # 枚举值校验
                options = field.get("options", [])
                if options and len(options) > 0:
                    valid_values = []
                    for opt in options:
                        if isinstance(opt, dict):
                            valid_values.append(opt.get("value", opt.get("label")))
                        else:
                            valid_values.append(opt)
                    
                    if value not in valid_values:
                        validation_result["warnings"].append({
                            "field": field_code,
                            "fieldName": field_name,
                            "message": f"值 {value} 不在推荐选项中",
                            "type": "enum"
                        })
        
        # 如果配置了大模型，执行大模型智能校验
        if model:
            try:
                llm = self._create_llm(model, temperature)
                logger.info(f"[FormNodeExecutor] 基础校验完成，开始大模型智能校验")
                
                llm_validation = await self._validate_with_llm(ontology, form_data, llm)
                
                # 合并大模型校验结果
                if llm_validation:
                    # 添加大模型发现的问题
                    for error in llm_validation.get("errors", []):
                        validation_result["errors"].append({
                            "field": error.get("field", ""),
                            "fieldName": error.get("fieldName", ""),
                            "message": error.get("message", ""),
                            "type": "llm",
                            "source": "大模型"
                        })
                    
                    for warning in llm_validation.get("warnings", []):
                        validation_result["warnings"].append({
                            "field": warning.get("field", ""),
                            "fieldName": warning.get("fieldName", ""),
                            "message": warning.get("message", ""),
                            "type": "llm",
                            "source": "大模型"
                        })
                    
                    # 更新有效性判断
                    if not llm_validation.get("is_valid", True):
                        validation_result["is_valid"] = False
                    
                    logger.info(f"[FormNodeExecutor] 大模型智能校验完成: is_valid={llm_validation.get('is_valid', True)}")
            except Exception as e:
                logger.warning(f"[FormNodeExecutor] 大模型智能校验失败: {e}，使用基础校验结果")
        
        return validation_result
    
    async def _validate_with_llm(self, ontology: Dict[str, Any], form_data: Dict[str, Any], llm) -> Dict[str, Any]:
        """使用大模型进行智能校验"""
        from langchain_core.prompts import ChatPromptTemplate
        from langchain_core.output_parsers import JsonOutputParser
        
        ontology_code = ontology.get("ontologyCode", "")
        ontology_name = ontology.get("ontologyName", "")
        description = ontology.get("description", "")
        
        # 提取字段信息
        fields_info = []
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_name = field.get("fieldName", field.get("fieldCode", ""))
                field_code = field.get("fieldCode", "")
                field_type = field.get("fieldType", "string")
                required = field.get("required", False)
                description_field = field.get("description", "")
                
                field_info = f"- {field_name} ({field_code})"
                field_info += f" [类型: {field_type}]"
                if required:
                    field_info += " [必填]"
                if description_field:
                    field_info += f" - {description_field}"
                
                # 添加枚举选项
                options = self._get_enum_options(field)
                if options and len(options) > 0:
                    valid_values = []
                    for opt in options:
                        if isinstance(opt, dict):
                            valid_values.append(f"{opt.get('value')} ({opt.get('label', '')})")
                        else:
                            valid_values.append(str(opt))
                    field_info += f" [可选值: {', '.join(valid_values)}]"
                
                fields_info.append(field_info)
        
        fields_text = "\n".join(fields_info) if fields_info else "无"
        
        # 格式化表单数据
        form_data_text = json.dumps(form_data, ensure_ascii=False, indent=2)
        
        prompt = f"""## 任务
你是一个专业的表单数据校验助手。请根据给定的本体定义和表单数据，校验数据的准确性、完整性和合理性。

## 本体信息
- 本体编码：{ontology_code}
- 本体名称：{ontology_name}
- 本体描述：{description}

## 本体字段定义
{fields_text}

## 用户填写的表单数据
```json
{form_data_text}
```

## 校验要求
1. 检查必填字段是否已填写
2. 检查数据类型是否正确
3. 检查字段值是否符合业务逻辑和常识
4. 检查字段值是否在合理范围内
5. 检查字段之间的关联性（如起始日期应早于结束日期）
6. 检查是否有明显的输入错误或遗漏

## 输出要求
请以 JSON 格式返回校验结果：
{{
    "is_valid": true/false,  // 是否通过校验
    "errors": [  // 错误列表（严重问题，必须修正）
        {{"field": "字段编码", "fieldName": "字段名称", "message": "错误信息"}}
    ],
    "warnings": [  // 警告列表（建议修正）
        {{"field": "字段编码", "fieldName": "字段名称", "message": "警告信息"}}
    ]
}}

请只返回 JSON 格式的校验结果，不要包含其他内容。"""
        
        logger.info(f"[FormNodeExecutor] 大模型智能校验: prompt长度={len(prompt)}")
        
        # 调用大模型
        messages = [
            ("system", "你是一个专业的表单数据校验助手。请严格校验表单数据的准确性。"),
            ("user", prompt)
        ]
        
        prompt_template = ChatPromptTemplate.from_messages(messages)
        chain = prompt_template | llm | JsonOutputParser()
        
        try:
            result = await chain.ainvoke({})
            logger.info(f"[FormNodeExecutor] 大模型智能校验完成: {len(result.get('errors', []))} 个错误, {len(result.get('warnings', []))} 个警告")
            return result
        except Exception as e:
            logger.warning(f"[FormNodeExecutor] 大模型智能校验解析失败: {e}")
            return {
                "is_valid": False,
                "errors": [{"field": "", "fieldName": "", "message": f"大模型智能校验解析失败: {str(e)}"}],
                "warnings": []
            }
    
    async def _call_mcp_tool(self, tool_name: str, form_data: Dict[str, Any], context: WorkflowContext) -> Dict[str, Any]:
        """调用 MCP 工具提交表单"""
        from app.mcp_tools import get_toolhub
        
        hub = get_toolhub()
        
        if not hub.has_tool(tool_name):
            raise ValueError(f"MCP 工具 {tool_name} 不存在")
        
        # 准备工具参数 - 从节点配置获取参数模板并替换
        tool_params = self.node_data.get("params", {})
        
        # 渲染参数模板
        rendered_params = {}
        for key, value in tool_params.items():
            if isinstance(value, str):
                rendered_params[key] = self.render_template(value, context)
            else:
                rendered_params[key] = value
        
        # 将表单数据作为参数传递（如果没有显式配置）
        if "formData" not in rendered_params:
            rendered_params["formData"] = form_data
        
        # 调用 MCP 工具
        logger.info(f"[FormNodeExecutor] 调用 MCP 工具: {tool_name}, 参数: {list(rendered_params.keys())}")
        result = hub.execute_sync(tool_name, rendered_params)
        
        return result
    
    def _get_next_nodes(self, edges: List[Dict[str, Any]]) -> List[str]:
        return [e["target"] for e in edges if e["source"] == self.node_id]


class ValidateNodeExecutor(NodeExecutor):
    """表单校验节点执行器"""
    
    NODE_TYPE = "validate"
    
    async def execute(self, context: WorkflowContext, edges: List[Dict[str, Any]]) -> List[str]:
        context.update_node_status(self.node_id, ExecutionStatus.RUNNING)
        
        try:
            # 如果配置了显性输入映射，先解析输入变量
            if self.input_mappings:
                resolved_inputs = self.resolve_inputs(context)
                for key, value in resolved_inputs.items():
                    context.set_variable(key, value)
            
            # 获取表单数据
            form_data = context.get_variable("form_data", {})
            
            if not form_data:
                raise ValueError("表单数据为空，请确保表单节点已正确执行")
            
            logger.info(f"Validate node executing with {len(form_data)} fields")
            
            # 执行表单校验
            validation_results = await self._validate_form(form_data, context)
            
            # 设置校验结果到上下文
            context.set_variable("validation_result", validation_results)
            context.outputs["validation_result"] = validation_results
            
            # 计算校验统计
            stats = self._calculate_stats(validation_results)
            context.set_variable("validation_stats", stats)
            context.outputs["validation_stats"] = stats
            
            # 使用标准输出变量传递数据（同时处理显性输出映射）
            self.set_output(context, {
                "validation_results": validation_results,
                "stats": stats
            })
            
            logger.info(f"Validation completed: {stats['passed']} passed, {stats['errors']} errors, {stats['warnings']} warnings")
            
            context.update_node_status(self.node_id, ExecutionStatus.COMPLETED)
            
        except Exception as e:
            logger.error(f"Validate node execution failed: {e}")
            context.error = str(e)
            context.update_node_status(self.node_id, ExecutionStatus.FAILED)
            raise
        
        return self._get_next_nodes(edges)
    
    async def _validate_form(self, form_data: Dict[str, Any], context: WorkflowContext) -> List[Dict[str, Any]]:
        """执行表单校验"""
        # 获取本体编码
        ontology_code = context.get_variable("ontology_code", "tariff_filing")
        
        # 使用基础校验（TariffProcessor 已移除）
        return self._basic_validate(form_data, ontology_code)
    
    def _basic_validate(self, form_data: Dict[str, Any], ontology_code: str) -> List[Dict[str, Any]]:
        """基础表单校验（当TariffProcessor不可用时）"""
        results = []
        
        # 获取本体定义
        ontology = config_loader.get_ontology(ontology_code)
        if not ontology:
            return results
        
        # 遍历字段进行校验
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_code = field.get("fieldCode")
                field_name = field.get("fieldName")
                value = form_data.get(field_code, "")
                required = field.get("required", False)
                
                result = {
                    "field": field_code,
                    "fieldName": field_name,
                    "value": value,
                    "result": "pass",
                    "reason": "",
                    "suggestion": ""
                }
                
                # 必填校验
                if required and not value:
                    result["result"] = "error"
                    result["reason"] = "此字段不能为空"
                    results.append(result)
                    continue
                
                # 长度校验
                max_length = field.get("maxLength")
                if max_length and len(str(value)) > max_length:
                    result["result"] = "error"
                    result["reason"] = f"长度超过限制（最大{max_length}字符）"
                    results.append(result)
                    continue
                
                # 正则校验
                validation = field.get("validation", {})
                pattern = validation.get("pattern")
                if pattern:
                    import re
                    if value and not re.match(pattern, str(value)):
                        result["result"] = "error"
                        result["reason"] = validation.get("patternError", "格式不正确")
                        results.append(result)
                        continue
                
                # 类型校验
                field_type = field.get("fieldType")
                if field_type == "number" and value:
                    try:
                        float(value)
                    except (ValueError, TypeError):
                        result["result"] = "error"
                        result["reason"] = "必须是数字类型"
                        results.append(result)
                        continue
                
                results.append(result)
        
        return results
    
    def _calculate_stats(self, validation_results: List[Dict[str, Any]]) -> Dict[str, int]:
        """计算校验统计"""
        stats = {
            "total": len(validation_results),
            "passed": 0,
            "errors": 0,
            "warnings": 0
        }
        
        for result in validation_results:
            if result["result"] == "pass":
                stats["passed"] += 1
            elif result["result"] == "error":
                stats["errors"] += 1
            elif result["result"] == "warning":
                stats["warnings"] += 1
        
        return stats
    
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
        "user_input": UserInputNodeExecutor,
        "http": HttpNodeExecutor,
        "code": CodeNodeExecutor,
        "parser": ParserNodeExecutor,
        "tool": ToolNodeExecutor,
        "form": FormNodeExecutor,
        "validate": ValidateNodeExecutor,
    }
    
    @classmethod
    def register_executor(cls, node_type: str, executor_class):
        """注册自定义节点执行器"""
        cls._executor_registry[node_type] = executor_class
    
    @classmethod
    def get_registered_nodes(cls) -> List[Dict[str, Any]]:
        """获取所有已注册的节点类型元数据
        
        Returns:
            所有节点类型的元数据列表，用于编辑器动态加载节点类型
        """
        return [
            executor_class.get_node_metadata()
            for executor_class in cls._executor_registry.values()
        ]
    
    @classmethod
    def get_node_metadata(cls, node_type: str) -> Optional[Dict[str, Any]]:
        """获取指定节点类型的元数据
        
        Args:
            node_type: 节点类型标识符
        
        Returns:
            节点元数据，如果未找到返回None
        """
        executor_class = cls._executor_registry.get(node_type)
        if executor_class:
            return executor_class.get_node_metadata()
        return None
    
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
            
            # 通知消息队列处理完成，防止 process_queue 死循环
            await message_queue.put(None)
            
            yield {
                "type": "workflow_complete",
                "workflow_id": workflow_id,
                "status": context.status.value,
                "outputs": context.outputs,
                "timestamp": datetime.now().isoformat()
            }
            
        except Exception as e:
            # 异常时也通知消息队列
            await message_queue.put(None)
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
        # 检查是否已终止或完成
        if context.status in [ExecutionStatus.COMPLETED, ExecutionStatus.FAILED, ExecutionStatus.TERMINATED]:
            return
        
        node = self.node_map.get(node_id)
        if not node:
            logger.warning(f"节点不存在: {node_id}")
            return
        
        node_type = node.get("type", "")
        executor_class = self._executor_registry.get(node_type)
        
        if not executor_class:
            logger.warning(f"未找到节点类型的执行器: {node_type}")
            return
        
        try:
            executor = executor_class(node)
            next_node_ids = await executor.execute(context, self.edges)
            
            # 检查节点执行是否失败
            node_status = context.node_statuses.get(node_id, ExecutionStatus.COMPLETED)
            if node_status == ExecutionStatus.FAILED:
                logger.error(f"节点执行失败，终止工作流: {node_id}")
                context.status = ExecutionStatus.FAILED
                return
            
            # 节点执行成功，递归执行下一个节点
            for next_node_id in next_node_ids:
                await self._execute_node(next_node_id, context)
                # 每次递归后检查是否已终止
                if context.status in [ExecutionStatus.FAILED, ExecutionStatus.TERMINATED]:
                    return
                    
        except Exception as e:
            logger.exception(f"节点执行异常，终止工作流: {node_id}, 错误: {e}")
            context.status = ExecutionStatus.FAILED
            context.error = f"节点 {node_id} 执行失败: {str(e)}"
            context.update_node_status(node_id, ExecutionStatus.FAILED)
            return
    
    async def _execute_node_streaming(self, node_id: str, context: WorkflowContext, yield_fn):
        """递归流式执行节点"""
        # 检查是否已终止或完成
        if context.status in [ExecutionStatus.COMPLETED, ExecutionStatus.FAILED, ExecutionStatus.TERMINATED]:
            return
        
        node = self.node_map.get(node_id)
        if not node:
            logger.warning(f"节点不存在: {node_id}")
            await yield_fn({
                "type": "error",
                "node_id": node_id,
                "message": f"节点不存在: {node_id}",
                "timestamp": datetime.now().isoformat()
            })
            return
        
        node_type = node.get("type", "")
        executor_class = self._executor_registry.get(node_type)
        
        if not executor_class:
            logger.warning(f"未找到节点类型的执行器: {node_type}")
            await yield_fn({
                "type": "error",
                "node_id": node_id,
                "message": f"未找到节点类型的执行器: {node_type}",
                "timestamp": datetime.now().isoformat()
            })
            return
        
        # 发送节点开始事件
        await yield_fn({
            "type": "node_start",
            "node_id": node_id,
            "node_type": node_type,
            "node_label": node.get("data", {}).get("label", node_type),
            "timestamp": datetime.now().isoformat()
        })
        
        try:
            executor = executor_class(node)
            next_node_ids = await executor.execute(context, self.edges)
            
            # 检查节点执行状态
            node_status = context.node_statuses.get(node_id, ExecutionStatus.COMPLETED)
            
            # 发送节点完成事件
            await yield_fn({
                "type": "node_complete",
                "node_id": node_id,
                "node_type": node_type,
                "status": node_status.value,
                "outputs": context.outputs,
                "timestamp": datetime.now().isoformat()
            })
            
            # 如果节点执行失败，终止工作流
            if node_status == ExecutionStatus.FAILED:
                logger.error(f"节点执行失败，终止工作流: {node_id}")
                context.status = ExecutionStatus.FAILED
                await yield_fn({
                    "type": "workflow_error",
                    "node_id": node_id,
                    "message": f"节点执行失败，终止工作流: {node_id}",
                    "error": context.error,
                    "timestamp": datetime.now().isoformat()
                })
                return
            
            # 节点执行成功，递归执行下一个节点
            logger.info(f"Node [{node_id}] completed: next_nodes={next_node_ids}")
            for next_node_id in next_node_ids:
                logger.info(f"Executing next node: {next_node_id}")
                await self._execute_node_streaming(next_node_id, context, yield_fn)
                # 每次递归后检查是否已终止
                if context.status in [ExecutionStatus.FAILED, ExecutionStatus.TERMINATED]:
                    return
                    
        except Exception as e:
            logger.exception(f"节点执行异常，终止工作流: {node_id}, 错误: {e}")
            error_message = f"节点 {node_id} 执行失败: {str(e)}"
            context.status = ExecutionStatus.FAILED
            context.error = error_message
            context.update_node_status(node_id, ExecutionStatus.FAILED)
            
            # 发送错误事件
            await yield_fn({
                "type": "node_error",
                "node_id": node_id,
                "node_type": node_type,
                "message": error_message,
                "error": str(e),
                "timestamp": datetime.now().isoformat()
            })
            
            # 发送工作流错误事件
            await yield_fn({
                "type": "workflow_error",
                "node_id": node_id,
                "message": error_message,
                "error": str(e),
                "timestamp": datetime.now().isoformat()
            })
            
            return