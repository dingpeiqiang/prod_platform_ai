from enum import Enum


class IntentType(Enum):
    """意图类型枚举"""
    CHAT = "chat"  # 对话
    FORM = "form"  # 表单
    TARIFF_FILING = "tariff_filing"  # 资费备案
    VALIDATION = "validation"  # 验证
    EXTERNAL_API_DEMO = "external_api_demo"  # 外部API演示
    CONFIG = "config"  # 配置
    DELETE_FORM = "delete_form"  # 删除表单
    MANAGE_HISTORY = "manage_history"  # 管理历史

    @classmethod
    def list(cls):
        """列出所有类型"""
        return [t.value for t in cls]

    @classmethod
    def is_valid(cls, value):
        """验证是否为有效类型"""
        return value in cls.list()


class ActionType(Enum):
    """动作类型枚举"""
    FORM_GENERATION = "form_generation"  # 标准表单生成
    FORM_WITH_MCP = "form_with_mcp"  # 带MCP工具调用的表单
    DIRECT_RESPONSE = "direct_response"  # 直接响应
    TOOL_CALL = "tool_call"  # 工具调用
    MULTI_STEP = "multi_step"  # 多步流程

    @classmethod
    def list(cls):
        """列出所有类型"""
        return [t.value for t in cls]

    @classmethod
    def is_valid(cls, value):
        """验证是否为有效类型"""
        return value in cls.list()

    @classmethod
    def get_description(cls, value):
        """获取类型描述"""
        descriptions = {
            cls.FORM_GENERATION.value: "标准表单生成",
            cls.FORM_WITH_MCP.value: "带MCP工具调用的表单",
            cls.DIRECT_RESPONSE.value: "直接响应",
            cls.TOOL_CALL.value: "工具调用",
            cls.MULTI_STEP.value: "多步流程",
        }
        return descriptions.get(value, value)
