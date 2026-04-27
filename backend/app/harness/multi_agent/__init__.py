# -*- coding: utf-8 -*-
"""
Phase 3: 多Agent协作系统
=========================
支持多Agent注册、任务路由、消息总线

核心组件：
- AgentManager: Agent注册与生命周期管理
- TaskRouter: 智能任务路由
- MessageBus: Agent间消息通信
"""

from .agent_manager import AgentManager, Agent, AgentStatus, AgentCapability
from .task_router import TaskRouter, RouteStrategy, Task
from .message_bus import MessageBus, Message, MessageType, AgentMessage

__all__ = [
    # Agent管理
    "AgentManager",
    "Agent",
    "AgentStatus",
    "AgentCapability",
    # 任务路由
    "TaskRouter",
    "RouteStrategy",
    "Task",
    # 消息总线
    "MessageBus",
    "Message",
    "MessageType",
    "AgentMessage",
]
