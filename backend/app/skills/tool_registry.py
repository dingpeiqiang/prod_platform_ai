from typing import Dict, Any, Callable, List
import json

class Tool:
    def __init__(self, name: str, description: str, func: Callable):
        self.name = name
        self.description = description
        self.func = func
    
    def to_dict(self) -> Dict:
        """将工具转换为 LLM 可理解的描述格式"""
        return {
            "name": self.name,
            "description": self.description
        }

class ToolRegistry:
    _instance = None
    _tools: Dict[str, Tool] = {}

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    @classmethod
    def register(cls, name: str, description: str, func: Callable):
        """注册一个工具"""
        cls._tools[name] = Tool(name, description, func)

    @classmethod
    def get_tool(cls, name: str) -> Tool:
        return cls._tools.get(name)

    @classmethod
    def get_all_tools(cls) -> List[Dict]:
        """获取所有工具的元数据，用于注入 Prompt"""
        return [tool.to_dict() for tool in cls._tools.values()]

    @classmethod
    def execute(cls, name: str, **kwargs) -> Any:
        """执行指定的工具，并自动过滤不需要的参数"""
        tool = cls.get_tool(name)
        if not tool:
            raise ValueError(f"Tool '{name}' not found")
        
        # 获取函数的签名
        import inspect
        sig = inspect.signature(tool.func)
        valid_params = sig.parameters.keys()
        
        # 只保留函数需要的参数
        filtered_kwargs = {k: v for k, v in kwargs.items() if k in valid_params}
        
        return tool.func(**filtered_kwargs)
