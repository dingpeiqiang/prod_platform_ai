"""
工作流节点模块 - 接口规范

每个工作流节点是一个接口组件，按照统一接口规范实现。

┌──────────────────────────────────────────────────────────┐
│      节点系统设计原则                                     │
├──────────────────────────────────────────────────────────┤
│  约定大于配置 (Convention over Configuration)              │
│  - 基类提供所有钩子的默认实现                               │
│  - 子类只需覆盖需要定制的部分                               │
│  - 调度引擎统一管理节点生命周期                             │
│                                                          │
│  借鉴 Flowable BPMN 引擎架构                              │
│  - 节点 = Service Task (JavaDelegate 模式)                │
│  - 变量传递通过 DelegateExecution.get/set 结构化访问        │
│  - Palette Schema 声明输入输出 (单一信源)                   │
│  - 支持静态参数（已知参数名）和动态参数（运行时决定的参数名）    │
└──────────────────────────────────────────────────────────┘
"""
from typing import Dict, Any, Optional, List, Type, Union
from abc import ABC, abstractmethod
from dataclasses import dataclass, field, asdict
from app.core.logger import get_logger

logger = get_logger(__name__)


# ============================================================
# 类型定义
# ============================================================

@dataclass
class ParamSchema:
    """参数 Schema 定义

    描述一个参数的元信息，支持：
    - type: 参数类型（str/int/float/bool/list/dict/any）
    - required: 是否必填
    - description: 描述信息
    - default: 默认值
    """
    type: str = "str"
    required: bool = True
    description: str = ""
    default: Any = None

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'ParamSchema':
        return cls(
            type=data.get("type", "str"),
            required=data.get("required", True),
            description=data.get("description", ""),
            default=data.get("default", None),
        )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "type": self.type,
            "required": self.required,
            "description": self.description,
            "default": self.default,
        }


# ============================================================
# 节点注册表
# ============================================================

_node_registry: Dict[str, Type['WorkflowNode']] = {}


def register_node(node_class: Type['WorkflowNode']):
    """注册节点装饰器"""
    _node_registry[node_class.name] = node_class
    logger.info(f"注册节点: {node_class.name}")
    return node_class


def get_node(name: str) -> Optional[Type['WorkflowNode']]:
    """获取节点类"""
    return _node_registry.get(name)


def get_all_nodes() -> Dict[str, Type['WorkflowNode']]:
    """获取所有已注册的节点"""
    return _node_registry


def initialize_nodes():
    """初始化所有节点（自动导入所有节点模块）"""
    import os
    import importlib

    current_dir = os.path.dirname(__file__)
    for filename in sorted(os.listdir(current_dir)):
        if filename.endswith(".py") and filename != "__init__.py":
            module_name = f"app.langchain.workflow_nodes.{filename[:-3]}"
            try:
                importlib.import_module(module_name)
            except Exception as e:
                logger.warning(f"加载节点模块失败 {module_name}: {e}")


# ============================================================
# 委托执行上下文 (DelegateExecution)
# ============================================================

class DelegateExecution:
    """委托执行上下文（借鉴 Flowable 的 DelegateExecution）

    职责：
    1. 提供 get/set 变量访问（引擎在调用前后自动做变量映射）
    2. 持有工作流上下文信息（流程 ID、步骤 ID 等）

    变量访问优先级：
      execution 本地变量 → context.step_results → context.outputs → context.inputs
    """

    def __init__(self, context: 'ExecutionContext', step_def: 'StepDefinition', is_resume: bool = False):
        self._context = context
        self._step_def = step_def
        self._variables: Dict[str, Any] = {}
        self.is_resume = is_resume  # 是否是恢复执行

    def get(self, name: str, default=None) -> Any:
        """获取变量"""
        val = self._variables.get(name)
        if val is not None:
            return val
        return self._context.get_variable(name, default)

    def set(self, name: str, value: Any):
        """设置变量（支持动态参数名）"""
        self._variables[name] = value

    @property
    def context(self) -> 'ExecutionContext':
        """获取执行上下文（用于访问 step_results、inputs、outputs 等）"""
        return self._context

    @property
    def variables(self) -> Dict[str, Any]:
        """获取所有已设置的变量"""
        return dict(self._variables)

    @property
    def workflow_id(self) -> str:
        return self._context.workflow_id

    @property
    def step_id(self) -> str:
        return self._step_def.id

    @property
    def step_name(self) -> str:
        return self._step_def.name


# ============================================================
# 节点基类 - 接口规范
# ============================================================

class WorkflowNode(ABC):
    """工作流节点接口基类

    借鉴 Flowable Service Task + JavaDelegate 模式。

    ┌─────────────────────────────────────────────┐
    │  类属性（palette schema - 单一信源）          │
    │  ├── name:         节点唯一标识               │
    │  ├── display_name: 编辑器中显示的名称          │
    │  ├── description:  节点描述                   │
    │  ├── config_fields: 编辑器配置字段声明         │
    │  ├── output_fields: 输出字段声明              │
    │  └── has_dynamic_output: 是否有动态输出       │
    ├─────────────────────────────────────────────┤
    │  实例方法                                    │
    │  ├── execute()       核心执行（必须实现）       │
    │  └── get_dynamic_outputs()  动态输出声明（可选）│
    └─────────────────────────────────────────────┘

    约定大于配置：
    - 编辑器通过 config_fields 渲染配置表单
    - 引擎通过 execute() 执行节点逻辑
    - 变量传递通过 DelegateExecution.get/set
    - 动态参数在 execute() 中通过 execution.set() 输出
    - 引擎自动收集 outputs 到上下文
    """

    # ---- 类级别声明（palette schema） ----
    name: str = ""
    display_name: str = ""
    description: str = ""
    config_fields: Dict[str, ParamSchema] = {}
    output_fields: Dict[str, ParamSchema] = {}
    has_dynamic_output: bool = False

    # ---- 向后兼容标记 ----
    # 设为 True 时引擎会以旧方式调用（execute(context, **kwargs) -> dict）
    # tariff 节点使用此模式
    _legacy: bool = False

    # ---- 兼容旧属性 inputs/outputs ----
    @property
    def inputs(self) -> Dict[str, dict]:
        return {k: v.to_dict() if isinstance(v, ParamSchema) else v
                for k, v in self.config_fields.items()}

    @inputs.setter
    def inputs(self, value: Dict[str, dict]):
        if value:
            self.config_fields = {
                k: ParamSchema.from_dict(v) if isinstance(v, dict) else v
                for k, v in value.items()
            }

    @property
    def outputs(self) -> Dict[str, dict]:
        return {k: v.to_dict() if isinstance(v, ParamSchema) else v
                for k, v in self.output_fields.items()}

    @outputs.setter
    def outputs(self, value: Dict[str, dict]):
        if value:
            self.output_fields = {
                k: ParamSchema.from_dict(v) if isinstance(v, dict) else v
                for k, v in value.items()
            }

    # ============================================================
    # 生命周期方法
    # ============================================================

    async def execute(self, execution: DelegateExecution) -> None:
        """执行节点逻辑（新接口）

        节点通过 execution.get/set 读写变量。
        变量名可以是任意字符串（支持动态输出）。

        Args:
            execution: 委托执行上下文
        """
        raise NotImplementedError(
            f"节点 [{self.name}] 未实现 execute 方法"
        )

    def get_dynamic_outputs(self, config_data: Dict[str, Any]) -> Dict[str, ParamSchema]:
        """返回动态输出 schema

        引擎调用此方法获知节点会输出哪些动态变量。
        主要用于引擎收集输出。

        例如 set_variable 节点：
          config_data = {"variable_name": "my_var"}
          → 返回 {"my_var": ParamSchema(type="any")}

        Args:
            config_data: 配置数据（来自编辑器）

        Returns:
            动态输出的字段名 → ParamSchema 映射
        """
        return {}

    @classmethod
    def get_palette_schema(cls) -> dict:
        """获取完整的 palette schema（给编辑器用）"""
        return {
            "name": cls.name,
            "display_name": cls.display_name,
            "description": cls.description,
            "config_fields": {
                k: v.to_dict() if isinstance(v, ParamSchema) else v
                for k, v in cls.config_fields.items()
            },
            "output_fields": {
                k: v.to_dict() if isinstance(v, ParamSchema) else v
                for k, v in cls.output_fields.items()
            },
            "has_dynamic_output": cls.has_dynamic_output,
        }

    # ============================================================
    # 辅助方法
    # ============================================================

    @staticmethod
    def _cast_type(name: str, value: Any, schema: ParamSchema) -> Any:
        """类型转换"""
        if value is None:
            return value

        t = schema.type.lower()
        try:
            if t == "str":
                return str(value)
            if t == "int":
                return int(value)
            if t == "float":
                return float(value)
            if t == "bool":
                if isinstance(value, str):
                    return value.lower() in ("true", "1", "yes", "y")
                return bool(value)
            if t in ("list", "tuple"):
                if isinstance(value, str):
                    import json
                    return json.loads(value)
                return list(value) if hasattr(value, '__iter__') else [value]
            if t in ("dict", "object"):
                if isinstance(value, str):
                    import json
                    return json.loads(value)
                return dict(value) if hasattr(value, 'items') else {}
            return value
        except (ValueError, TypeError) as e:
            logger.warning(f"参数 '{name}' 类型转换失败: {value} -> {t}, {e}")
            return value

    # ---- 日志辅助（向后兼容） ----

    def _log_input(self, **kwargs):
        """记录入参日志"""
        logger.info(f"[{self.name}] 入参: {self._fmt(kwargs)}")

    def _log_processing(self, msg: str):
        """记录处理逻辑"""
        logger.info(f"[{self.name}] 处理: {msg}")

    def _log_output(self, **kwargs):
        """记录出参日志"""
        logger.info(f"[{self.name}] 出参: {self._fmt(kwargs)}")

    @staticmethod
    def _fmt(data: Dict[str, Any]) -> str:
        parts = []
        for k, v in data.items():
            s = str(v)
            if len(s) > 100:
                s = s[:100] + "..."
            parts.append(f"{k}={s}")
        return ", ".join(parts)


# 自动初始化节点
initialize_nodes()