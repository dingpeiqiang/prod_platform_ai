# -*- coding: utf-8 -*-
"""
Agent管理器 - Agent注册与生命周期管理
======================================
支持多Agent的注册、发现、状态管理和协作调度
"""

import asyncio
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Callable, Dict, List, Optional, Set
from collections import defaultdict
import logging

logger = logging.getLogger(__name__)


class AgentStatus(str, Enum):
    """Agent状态枚举"""
    IDLE = "idle"                    # 空闲
    BUSY = "busy"                    # 工作中
    OFFLINE = "offline"              # 离线
    ERROR = "error"                  # 错误
    MAINTENANCE = "maintenance"      # 维护中


class AgentCapability(str, Enum):
    """Agent能力枚举"""
    FORM_RECOGNITION = "form_recognition"    # 表单识别
    FIELD_EXTRACTION = "field_extraction"    # 字段提取
    VALIDATION = "validation"                # 表单验证
    APPROVAL = "approval"                    # 审批
    DATA_ANALYSIS = "data_analysis"          # 数据分析
    IMAGE_UNDERSTANDING = "image_understanding"  # 图片理解
    CODE_GENERATION = "code_generation"      # 代码生成
    GENERAL = "general"                      # 通用能力


@dataclass
class Agent:
    """Agent定义"""
    agent_id: str
    name: str
    capabilities: Set[AgentCapability]
    status: AgentStatus = AgentStatus.IDLE
    description: str = ""
    max_concurrent: int = 1  # 最大并发任务数
    current_tasks: int = 0   # 当前任务数
    priority: int = 0         # 优先级（数字越大优先级越高）
    metadata: Dict[str, Any] = field(default_factory=dict)
    created_at: datetime = field(default_factory=datetime.now)
    last_active: datetime = field(default_factory=datetime.now)

    def is_available(self) -> bool:
        """检查Agent是否可用"""
        return (
            self.status == AgentStatus.IDLE and
            self.current_tasks < self.max_concurrent
        )

    def can_handle(self, capability: AgentCapability) -> bool:
        """检查Agent是否能处理指定能力"""
        return capability in self.capabilities


@dataclass
class AgentMetrics:
    """Agent性能指标"""
    agent_id: str
    total_tasks: int = 0
    successful_tasks: int = 0
    failed_tasks: int = 0
    total_duration: float = 0.0  # 总耗时(秒)
    avg_duration: float = 0.0   # 平均耗时
    last_success: Optional[datetime] = None
    last_failure: Optional[datetime] = None

    @property
    def success_rate(self) -> float:
        """成功率"""
        if self.total_tasks == 0:
            return 0.0
        return self.successful_tasks / self.total_tasks


class AgentManager:
    """
    Agent管理器

    功能：
    - Agent注册与注销
    - Agent状态管理
    - Agent发现与选择
    - 负载均衡
    - 指标收集
    """

    def __init__(self):
        # Agent注册表
        self._agents: Dict[str, Agent] = {}
        # Agent指标
        self._metrics: Dict[str, AgentMetrics] = defaultdict(
            lambda: AgentMetrics(agent_id="")
        )
        # 能力索引（能力 -> Agent列表）
        self._capability_index: Dict[AgentCapability, List[str]] = defaultdict(list)
        # 锁
        self._lock = asyncio.Lock()
        # 事件回调
        self._event_callbacks: Dict[str, List[Callable]] = defaultdict(list)

    # ==================== Agent注册管理 ====================

    async def register(
        self,
        name: str,
        capabilities: List[AgentCapability],
        description: str = "",
        max_concurrent: int = 1,
        priority: int = 0,
        metadata: Optional[Dict[str, Any]] = None
    ) -> str:
        """
        注册Agent

        Args:
            name: Agent名称
            capabilities: Agent能力列表
            description: Agent描述
            max_concurrent: 最大并发任务数
            priority: 优先级
            metadata: 元数据

        Returns:
            Agent ID
        """
        agent_id = f"agent_{uuid.uuid4().hex[:8]}"

        agent = Agent(
            agent_id=agent_id,
            name=name,
            capabilities=set(capabilities),
            description=description,
            max_concurrent=max_concurrent,
            priority=priority,
            metadata=metadata or {}
        )

        async with self._lock:
            self._agents[agent_id] = agent
            self._metrics[agent_id] = AgentMetrics(agent_id=agent_id)

            # 更新能力索引
            for cap in capabilities:
                if agent_id not in self._capability_index[cap]:
                    self._capability_index[cap].append(agent_id)

        logger.info(f"Agent registered: {agent_id} ({name}) with capabilities: {capabilities}")

        # 触发事件
        await self._emit_event("agent_registered", {"agent": agent})

        return agent_id

    async def unregister(self, agent_id: str) -> bool:
        """
        注销Agent

        Args:
            agent_id: Agent ID

        Returns:
            是否成功
        """
        async with self._lock:
            if agent_id not in self._agents:
                return False

            agent = self._agents[agent_id]

            # 从能力索引中移除
            for cap in agent.capabilities:
                if agent_id in self._capability_index[cap]:
                    self._capability_index[cap].remove(agent_id)

            del self._agents[agent_id]

        logger.info(f"Agent unregistered: {agent_id}")

        # 触发事件
        await self._emit_event("agent_unregistered", {"agent_id": agent_id})

        return True

    async def get(self, agent_id: str) -> Optional[Agent]:
        """获取Agent信息"""
        return self._agents.get(agent_id)

    async def list_all(self) -> List[Agent]:
        """列出所有Agent"""
        return list(self._agents.values())

    async def list_by_capability(self, capability: AgentCapability) -> List[Agent]:
        """列出具有特定能力的Agent"""
        agent_ids = self._capability_index.get(capability, [])
        return [self._agents[aid] for aid in agent_ids if aid in self._agents]

    # ==================== Agent状态管理 ====================

    async def set_status(self, agent_id: str, status: AgentStatus) -> bool:
        """
        设置Agent状态

        Args:
            agent_id: Agent ID
            status: 新状态

        Returns:
            是否成功
        """
        async with self._lock:
            if agent_id not in self._agents:
                return False

            self._agents[agent_id].status = status
            self._agents[agent_id].last_active = datetime.now()

        logger.debug(f"Agent {agent_id} status changed to {status}")

        # 触发事件
        await self._emit_event("status_changed", {
            "agent_id": agent_id,
            "status": status
        })

        return True

    async def increment_tasks(self, agent_id: str) -> bool:
        """增加Agent当前任务数"""
        async with self._lock:
            if agent_id not in self._agents:
                return False

            self._agents[agent_id].current_tasks += 1
            if self._agents[agent_id].current_tasks >= self._agents[agent_id].max_concurrent:
                self._agents[agent_id].status = AgentStatus.BUSY

            return True

    async def decrement_tasks(self, agent_id: str) -> bool:
        """减少Agent当前任务数"""
        async with self._lock:
            if agent_id not in self._agents:
                return False

            self._agents[agent_id].current_tasks = max(
                0,
                self._agents[agent_id].current_tasks - 1
            )
            if self._agents[agent_id].current_tasks < self._agents[agent_id].max_concurrent:
                self._agents[agent_id].status = AgentStatus.IDLE

            self._agents[agent_id].last_active = datetime.now()

            return True

    # ==================== Agent选择策略 ====================

    async def select_agent(
        self,
        capability: AgentCapability,
        strategy: str = "load_balance"
    ) -> Optional[Agent]:
        """
        选择最合适的Agent

        Args:
            capability: 所需能力
            strategy: 选择策略
                - "load_balance": 负载均衡
                - "priority": 优先级优先
                - "least_used": 使用最少优先
                - "random": 随机

        Returns:
            选中的Agent或None
        """
        candidates = await self.list_by_capability(capability)

        if not candidates:
            logger.warning(f"No agent found for capability: {capability}")
            return None

        # 过滤可用Agent
        available = [a for a in candidates if a.is_available()]

        if not available:
            logger.warning(f"No available agent for capability: {capability}")
            # 返回负载最轻的（即使忙碌）
            return min(candidates, key=lambda a: a.current_tasks / a.max_concurrent)

        if strategy == "load_balance":
            # 负载均衡：选择负载最低的
            return min(available, key=lambda a: a.current_tasks / a.max_concurrent)

        elif strategy == "priority":
            # 优先级：选择优先级最高的
            return max(available, key=lambda a: a.priority)

        elif strategy == "least_used":
            # 使用最少优先：选择总任务数最少的
            return min(available, key=lambda a: self._metrics[a.agent_id].total_tasks)

        elif strategy == "random":
            import random
            return random.choice(available)

        else:
            # 默认负载均衡
            return min(available, key=lambda a: a.current_tasks / a.max_concurrent)

    async def select_multi_agents(
        self,
        capabilities: List[AgentCapability],
        count: Optional[int] = None
    ) -> List[Agent]:
        """
        选择多个Agent（用于需要协作的场景）

        Args:
            capabilities: 所需能力列表
            count: 选择数量，默认与capabilities数量一致

        Returns:
            选中的Agent列表
        """
        if count is None:
            count = len(capabilities)

        selected = []
        used_ids = set()

        # 贪心选择
        for cap in capabilities:
            candidates = await self.list_by_capability(cap)
            for candidate in sorted(candidates, key=lambda a: a.current_tasks / a.max_concurrent):
                if candidate.agent_id not in used_ids and candidate.is_available():
                    selected.append(candidate)
                    used_ids.add(candidate.agent_id)
                    break

            if len(selected) >= count:
                break

        return selected

    # ==================== 指标收集 ====================

    async def record_success(self, agent_id: str, duration: float) -> None:
        """记录任务成功"""
        async with self._lock:
            if agent_id not in self._metrics:
                self._metrics[agent_id] = AgentMetrics(agent_id=agent_id)

            m = self._metrics[agent_id]
            m.total_tasks += 1
            m.successful_tasks += 1
            m.total_duration += duration
            m.avg_duration = m.total_duration / m.total_tasks
            m.last_success = datetime.now()

    async def record_failure(self, agent_id: str, duration: float) -> None:
        """记录任务失败"""
        async with self._lock:
            if agent_id not in self._metrics:
                self._metrics[agent_id] = AgentMetrics(agent_id=agent_id)

            m = self._metrics[agent_id]
            m.total_tasks += 1
            m.failed_tasks += 1
            m.total_duration += duration
            m.avg_duration = m.total_duration / m.total_tasks
            m.last_failure = datetime.now()

    async def get_metrics(self, agent_id: str) -> Optional[AgentMetrics]:
        """获取Agent指标"""
        return self._metrics.get(agent_id)

    async def get_all_metrics(self) -> Dict[str, AgentMetrics]:
        """获取所有Agent指标"""
        return dict(self._metrics)

    # ==================== 事件系统 ====================

    def on_event(self, event: str, callback: Callable) -> None:
        """注册事件回调"""
        self._event_callbacks[event].append(callback)

    async def _emit_event(self, event: str, data: Dict[str, Any]) -> None:
        """触发事件"""
        for callback in self._event_callbacks.get(event, []):
            try:
                if asyncio.iscoroutinefunction(callback):
                    await callback(data)
                else:
                    callback(data)
            except Exception as e:
                logger.error(f"Event callback error: {e}")

    # ==================== 生命周期钩子 ====================

    async def health_check(self) -> Dict[str, Any]:
        """
        健康检查

        Returns:
            健康状态信息
        """
        total = len(self._agents)
        available = sum(1 for a in self._agents.values() if a.is_available())
        busy = sum(1 for a in self._agents.values() if a.status == AgentStatus.BUSY)

        total_success = sum(m.successful_tasks for m in self._metrics.values())
        total_failed = sum(m.failed_tasks for m in self._metrics.values())

        return {
            "total_agents": total,
            "available_agents": available,
            "busy_agents": busy,
            "offline_agents": total - available - busy,
            "total_tasks": total_success + total_failed,
            "successful_tasks": total_success,
            "failed_tasks": total_failed,
            "overall_success_rate": (
                total_success / (total_success + total_failed)
                if (total_success + total_failed) > 0 else 0.0
            )
        }


# 全局实例
_agent_manager: Optional[AgentManager] = None


def get_agent_manager() -> AgentManager:
    """获取全局Agent管理器"""
    global _agent_manager
    if _agent_manager is None:
        _agent_manager = AgentManager()
    return _agent_manager
