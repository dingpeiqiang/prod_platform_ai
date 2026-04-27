"""
增强版工具注册器 - 支持权限控制和工具分类
"""

from typing import Dict, Any, Callable, List, Optional, Set
from dataclasses import dataclass, field
from enum import Enum
import logging
import inspect

logger = logging.getLogger(__name__)


class PermissionLevel(Enum):
    """权限级别"""
    PUBLIC = "public"              # 公开，无需认证
    AUTHENTICATED = "authenticated"  # 需要登录
    ADMIN = "admin"                # 需要管理员权限
    RESTRICTED = "restricted"     # 受限使用


class ToolCategory(Enum):
    """工具分类"""
    FORM = "form"                 # 表单相关
    VALIDATION = "validation"      # 验证相关
    SYSTEM = "system"             # 系统相关
    DATA = "data"                 # 数据相关
    FILE = "file"                 # 文件相关
    EXTERNAL = "external"         # 外部调用


@dataclass
class ToolMetadata:
    """工具元数据"""
    name: str
    description: str
    category: ToolCategory
    permission: PermissionLevel
    func: Callable
    parameters: Dict[str, Any] = field(default_factory=dict)
    tags: Set[str] = field(default_factory=set)
    deprecated: bool = False
    deprecation_message: Optional[str] = None
    
    def to_dict(self) -> Dict:
        """转换为 OpenAI Function Calling 格式"""
        return {
            "name": self.name,
            "description": self.description,
            "parameters": self.parameters or self._generate_parameters()
        }
    
    def _generate_parameters(self) -> Dict:
        """从函数签名生成参数 Schema"""
        sig = inspect.signature(self.func)
        params = {
            "type": "object",
            "properties": {},
            "required": []
        }
        
        for name, param in sig.parameters.items():
            if name == "self":
                continue
            
            param_info = {"type": "string"}  # 默认类型
            
            # 尝试从类型注解推断
            if param.annotation != inspect.Parameter.empty:
                if param.annotation in (int, "int"):
                    param_info["type"] = "integer"
                elif param.annotation in (float, "number"):
                    param_info["type"] = "number"
                elif param.annotation in (bool, "boolean"):
                    param_info["type"] = "boolean"
                elif param.annotation in (list, "array"):
                    param_info["type"] = "array"
                elif param.annotation in (dict, "object"):
                    param_info["type"] = "object"
            
            params["properties"][name] = param_info
            
            if param.default == inspect.Parameter.empty:
                params["required"].append(name)
        
        return params


class EnhancedToolRegistry:
    """
    增强版工具注册器
    
    支持：
    - 工具分类
    - 权限控制
    - 工具分组
    - 上下文感知获取
    """
    
    _instance: Optional["EnhancedToolRegistry"] = None
    
    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._initialized = False
        return cls._instance
    
    def __init__(self, config: Optional[Dict[str, Any]] = None):
        if self._initialized:
            return
        
        self._initialized = True
        self.config = config or {}
        self._tools: Dict[str, ToolMetadata] = {}
        self._categories: Dict[ToolCategory, Set[str]] = {
            cat: set() for cat in ToolCategory
        }
        self._tags_index: Dict[str, Set[str]] = {}
        
        # 注册默认工具
        self._register_default_tools()
    
    def _register_default_tools(self):
        """注册默认工具"""
        # 表单相关工具
        self.register_tool(
            name="recognize_scene",
            description="识别用户输入的场景（表单类型），返回表单编码",
            category=ToolCategory.FORM,
            permission=PermissionLevel.PUBLIC,
            func=self._default_recognize_scene,
            tags={"ai", "form", "recognition"}
        )
        
        self.register_tool(
            name="extract_fields",
            description="从用户输入中提取字段值",
            category=ToolCategory.FORM,
            permission=PermissionLevel.AUTHENTICATED,
            func=self._default_extract_fields,
            tags={"ai", "form", "extraction"}
        )
        
        self.register_tool(
            name="get_available_forms",
            description="获取所有可用的表单类型列表",
            category=ToolCategory.FORM,
            permission=PermissionLevel.PUBLIC,
            func=self._default_get_forms,
            tags={"form", "list"}
        )
        
        # 验证相关工具
        self.register_tool(
            name="validate_field",
            description="验证单个字段的值是否符合规则",
            category=ToolCategory.VALIDATION,
            permission=PermissionLevel.AUTHENTICATED,
            func=self._default_validate_field,
            tags={"validation", "field"}
        )
        
        self.register_tool(
            name="validate_form",
            description="验证整个表单数据是否完整有效",
            category=ToolCategory.VALIDATION,
            permission=PermissionLevel.AUTHENTICATED,
            func=self._default_validate_form,
            tags={"validation", "form"}
        )
        
        # 系统相关工具
        self.register_tool(
            name="get_status",
            description="获取系统状态",
            category=ToolCategory.SYSTEM,
            permission=PermissionLevel.PUBLIC,
            func=self._default_get_status,
            tags={"system", "status"}
        )
        
        self.register_tool(
            name="health_check",
            description="健康检查",
            category=ToolCategory.SYSTEM,
            permission=PermissionLevel.PUBLIC,
            func=self._default_health_check,
            tags={"system", "health"}
        )
    
    def register_tool(
        self,
        name: str,
        description: str,
        category: ToolCategory,
        permission: PermissionLevel,
        func: Callable,
        parameters: Optional[Dict] = None,
        tags: Optional[Set[str]] = None
    ):
        """
        注册工具
        
        Args:
            name: 工具名称
            description: 工具描述
            category: 工具分类
            permission: 权限级别
            func: 工具函数
            parameters: 参数 Schema
            tags: 标签
        """
        metadata = ToolMetadata(
            name=name,
            description=description,
            category=category,
            permission=permission,
            func=func,
            parameters=parameters or {},
            tags=tags or set()
        )
        
        self._tools[name] = metadata
        self._categories[category].add(name)
        
        # 更新标签索引
        for tag in metadata.tags:
            if tag not in self._tags_index:
                self._tags_index[tag] = set()
            self._tags_index[tag].add(name)
        
        logger.info(f"Registered tool: {name} (category={category.value}, permission={permission.value})")
    
    def get_tool(self, name: str) -> Optional[ToolMetadata]:
        """获取工具元数据"""
        return self._tools.get(name)
    
    def get_all_tools(self) -> List[Dict]:
        """获取所有工具的 Function Calling 格式"""
        return [tool.to_dict() for tool in self._tools.values() if not tool.deprecated]
    
    def execute(self, name: str, user_level: PermissionLevel = PermissionLevel.PUBLIC, **kwargs) -> Any:
        """
        执行工具（带权限检查）
        
        Args:
            name: 工具名称
            user_level: 用户权限级别
            **kwargs: 工具参数
            
        Returns:
            工具执行结果
            
        Raises:
            PermissionError: 权限不足
            ValueError: 工具不存在
        """
        tool = self.get_tool(name)
        
        if not tool:
            raise ValueError(f"Tool '{name}' not found")
        
        if tool.deprecated:
            raise ValueError(f"Tool '{name}' is deprecated: {tool.deprecation_message}")
        
        # 权限检查
        if not self._check_permission(user_level, tool.permission):
            raise PermissionError(
                f"Permission denied: tool '{name}' requires {tool.permission.value}, "
                f"user has {user_level.value}"
            )
        
        # 获取函数签名并过滤参数
        sig = inspect.signature(tool.func)
        valid_params = sig.parameters.keys()
        filtered_kwargs = {k: v for k, v in kwargs.items() if k in valid_params}
        
        return tool.func(**filtered_kwargs)
    
    def get_tools_for_context(
        self,
        context: str,
        user_level: PermissionLevel = PermissionLevel.PUBLIC,
        category: Optional[ToolCategory] = None
    ) -> List[Dict]:
        """
        根据上下文获取可用工具
        
        Args:
            context: 上下文标识（如 "form_fill", "validation"）
            user_level: 用户权限级别
            category: 工具分类过滤
            
        Returns:
            可用工具列表
        """
        tools = []
        
        for tool in self._tools.values():
            # 跳过已废弃工具
            if tool.deprecated:
                continue
            
            # 权限检查
            if not self._check_permission(user_level, tool.permission):
                continue
            
            # 分类过滤
            if category and tool.category != category:
                continue
            
            # 上下文匹配（通过标签或名称）
            context_keywords = context.lower().split("_")
            if any(kw in tool.name.lower() or kw in " ".join(tool.tags) 
                   for kw in context_keywords):
                tools.append(tool.to_dict())
        
        return tools
    
    def get_tools_by_category(self, category: ToolCategory) -> List[Dict]:
        """获取指定分类的所有工具"""
        return [
            self._tools[name].to_dict() 
            for name in self._categories.get(category, set())
            if not self._tools[name].deprecated
        ]
    
    def get_tools_by_tags(self, tags: Set[str]) -> List[Dict]:
        """获取包含指定标签的工具"""
        result = set()
        for tag in tags:
            if tag in self._tags_index:
                result.update(self._tags_index[tag])
        
        return [
            self._tools[name].to_dict() 
            for name in result 
            if not self._tools[name].deprecated
        ]
    
    def deprecate_tool(self, name: str, message: Optional[str] = None):
        """标记工具为废弃"""
        if name in self._tools:
            self._tools[name].deprecated = True
            self._tools[name].deprecation_message = message or f"Tool '{name}' is deprecated"
            logger.warning(f"Tool deprecated: {name}")
    
    def _check_permission(self, user_level: PermissionLevel, tool_level: PermissionLevel) -> bool:
        """检查权限级别"""
        # 权限级别从低到高: PUBLIC < AUTHENTICATED < ADMIN < RESTRICTED
        level_order = {
            PermissionLevel.PUBLIC: 0,
            PermissionLevel.AUTHENTICATED: 1,
            PermissionLevel.ADMIN: 2,
            PermissionLevel.RESTRICTED: 3,
        }
        
        return level_order.get(user_level, 0) >= level_order.get(tool_level, 0)
    
    # 默认工具实现
    def _default_recognize_scene(self, user_input: str) -> str:
        """默认场景识别"""
        # 这里应该调用实际的 AI 服务
        return "general"
    
    def _default_extract_fields(self, user_input: str, form_code: str = "") -> Dict:
        """默认字段提取"""
        return {}
    
    def _default_get_forms(self) -> List[Dict]:
        """获取表单列表"""
        return [
            {"code": "leave", "name": "请假申请", "description": "员工请假申请表单"},
            {"code": "expense", "name": "报销申请", "description": "差旅费用报销表单"},
        ]
    
    def _default_validate_field(self, field_name: str, value: Any, rules: Dict) -> Dict:
        """验证字段"""
        return {"valid": True}
    
    def _default_validate_form(self, form_data: Dict) -> Dict:
        """验证表单"""
        return {"valid": True, "errors": []}
    
    def _default_get_status(self) -> Dict:
        """获取状态"""
        return {"status": "running", "version": "1.0"}
    
    def _default_health_check(self) -> Dict:
        """健康检查"""
        return {"healthy": True}


# 全局实例获取函数
_registry: Optional[EnhancedToolRegistry] = None


def get_tool_registry(config: Optional[Dict] = None) -> EnhancedToolRegistry:
    """获取全局工具注册器实例"""
    global _registry
    if _registry is None:
        _registry = EnhancedToolRegistry(config)
    return _registry
