"""
工作流节点模块

每个节点是一个独立的文件，包含：
1. 输入参数定义
2. 处理逻辑
3. 输出结果
4. 详细的执行日志

节点注册机制：
- 每个节点文件在模块加载时自动注册到工作流引擎
- 使用 @register_node 装饰器标记节点
"""
from typing import Dict, Any, Type
from abc import ABC, abstractmethod
from app.core.logger import get_logger

logger = get_logger(__name__)

# 节点注册表
_node_registry = {}


class WorkflowNode(ABC):
    """
    工作流节点基类
    
    每个节点必须实现：
    - name: 节点名称
    - description: 节点描述
    - inputs: 输入参数定义
    - outputs: 输出参数定义
    - execute(): 执行逻辑
    """
    
    name: str = "base_node"
    description: str = "基础节点"
    inputs: Dict[str, dict] = {}  # 输入参数定义 {"param_name": {"type": "str", "required": True, "description": "..."}}
    outputs: Dict[str, dict] = {}  # 输出参数定义 {"output_name": {"type": "str", "description": "..."}}
    
    @abstractmethod
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        """
        执行节点逻辑
        
        返回结果必须包含：
        - success: bool - 是否成功
        - input: dict - 输入参数
        - processing: str - 处理逻辑描述
        - output: dict - 输出结果
        - 其他自定义字段
        """
        pass
    
    def _log_input(self, **kwargs):
        """记录输入日志"""
        logger.info(f"[{self.name}] 📥 输入信息:")
        for key, value in kwargs.items():
            value_str = str(value)
            logger.info(f"  ├── {key}: {value_str[:100]}..." if len(value_str) > 100 else f"  ├── {key}: {value_str}")
    
    def _log_processing(self, processing: str):
        """记录处理逻辑日志"""
        logger.info(f"[{self.name}] ⚙️ 处理逻辑: {processing}")
    
    def _log_output(self, **kwargs):
        """记录输出日志"""
        logger.info(f"[{self.name}] 📤 输出信息:")
        for key, value in kwargs.items():
            value_str = str(value)
            logger.info(f"  ├── {key}: {value_str[:100]}..." if len(value_str) > 100 else f"  ├── {key}: {value_str}")


def register_node(node_class: Type[WorkflowNode]):
    """
    注册节点装饰器
    
    使用方式：
    @register_node
    class MyNode(WorkflowNode):
        ...
    """
    _node_registry[node_class.name] = node_class
    logger.info(f"[WorkflowNodes] 注册节点: {node_class.name}")
    return node_class


def get_node(name: str) -> Type[WorkflowNode]:
    """获取节点类"""
    return _node_registry.get(name)


def get_all_nodes() -> Dict[str, Type[WorkflowNode]]:
    """获取所有已注册的节点"""
    return _node_registry


def initialize_nodes():
    """初始化所有节点（自动导入所有节点模块）"""
    import os
    import importlib
    
    # 导入当前目录下所有模块
    current_dir = os.path.dirname(__file__)
    for filename in os.listdir(current_dir):
        if filename.endswith(".py") and filename != "__init__.py":
            module_name = f"app.langchain.workflow_nodes.{filename[:-3]}"
            try:
                importlib.import_module(module_name)
                logger.debug(f"[WorkflowNodes] 加载节点模块: {module_name}")
            except Exception as e:
                logger.warning(f"[WorkflowNodes] 加载节点模块失败 {module_name}: {e}")


# 自动初始化节点
initialize_nodes()