# -*- coding: utf-8 -*-
"""
任务路由器 - 智能任务路由
===========================
根据任务类型、能力需求、负载情况智能分配任务到合适的Agent
"""

import asyncio
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Dict, List, Optional, Callable
import logging

from .agent_manager import AgentManager, AgentCapability

logger = logging.getLogger(__name__)


class RouteStrategy(str, Enum):
    """路由策略"""
    DIRECT = "direct"              # 直接路由（指定Agent）
    CAPABILITY = "capability"      # 按能力路由
    LOAD_BALANCE = "load_balance"  # 负载均衡
    PRIORITY = "priority"           # 优先级优先
    ROUND_ROBIN = "round_robin"    # 轮询
    LEAST_USED = "least_used"      # 使用最少优先
    AFFINITY = "affinity"          # 亲和性路由


class TaskPriority(str, Enum):
    """任务优先级"""
    LOW = "low"
    NORMAL = "normal"
    HIGH = "high"
    URGENT = "urgent"
    CRITICAL = "critical"


class TaskStatus(str, Enum):
    """任务状态"""
    PENDING = "pending"      # 等待中
    ROUTING = "routing"      # 路由中
    ASSIGNED = "assigned"     # 已分配
    RUNNING = "running"      # 执行中
    COMPLETED = "completed"  # 已完成
    FAILED = "failed"       # 失败
    CANCELLED = "cancelled"  # 已取消
    TIMEOUT = "timeout"      # 超时


@dataclass
class Task:
    """
    任务定义

    Attributes:
        task_id: 任务ID
        name: 任务名称
        description: 任务描述
        task_type: 任务类型（如 "form_recognition"）
        required_capabilities: 所需能力列表
        input_data: 输入数据
        priority: 优先级
        timeout: 超时时间（秒）
        retry_count: 重试次数
        max_retries: 最大重试次数
        status: 当前状态
        assigned_agent: 分配的Agent ID
        result: 执行结果
        error: 错误信息
        created_at: 创建时间
        started_at: 开始时间
        completed_at: 完成时间
        metadata: 元数据
    """
    task_id: str
    name: str
    description: str = ""
    task_type: str = ""
    required_capabilities: List[AgentCapability] = field(default_factory=list)
    input_data: Dict[str, Any] = field(default_factory=dict)
    priority: TaskPriority = TaskPriority.NORMAL
    timeout: float = 60.0  # 默认60秒超时
    retry_count: int = 0
    max_retries: int = 3
    status: TaskStatus = TaskStatus.PENDING
    assigned_agent: Optional[str] = None
    result: Optional[Any] = None
    error: Optional[str] = None
    created_at: datetime = field(default_factory=datetime.now)
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    metadata: Dict[str, Any] = field(default_factory=dict)

    def can_retry(self) -> bool:
        """是否可以重试"""
        return self.retry_count < self.max_retries

    def duration(self) -> Optional[float]:
        """获取执行耗时（秒）"""
        if self.started_at and self.completed_at:
            return (self.completed_at - self.started_at).total_seconds()
        return None


@dataclass
class TaskResult:
    """任务执行结果"""
    task_id: str
    success: bool
    result: Optional[Any] = None
    error: Optional[str] = None
    duration: float = 0.0
    agent_id: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)


class TaskRouter:
    """
    任务路由器

    功能：
    - 任务注册与管理
    - 智能路由选择
    - 任务调度与执行
    - 失败重试与超时处理
    """

    def __init__(
        self,
        agent_manager: Optional[AgentManager] = None,
        strategy: RouteStrategy = RouteStrategy.LOAD_BALANCE
    ):
        """
        初始化任务路由器

        Args:
            agent_manager: Agent管理器
            strategy: 默认路由策略
        """
        self._agent_manager = agent_manager or get_agent_manager()
        self._default_strategy = strategy

        # 任务存储
        self._tasks: Dict[str, Task] = {}

        # 轮询索引
        self._round_robin_index: Dict[str, int] = {}

        # 任务处理器
        self._handlers: Dict[str, Callable] = {}

        # 锁
        self._lock = asyncio.Lock()

        # 事件回调
        self._callbacks: Dict[str, List[Callable]] = {}

        logger.info(f"TaskRouter initialized with strategy: {strategy}")

    # ==================== 任务管理 ====================

    def register_handler(self, task_type: str, handler: Callable) -> None:
        """
        注册任务处理器

        Args:
            task_type: 任务类型
            handler: 处理函数，签名: async def handler(task: Task, agent_id: str) -> Any
        """
        self._handlers[task_type] = handler
        logger.debug(f"Handler registered for task type: {task_type}")

    async def create_task(
        self,
        name: str,
        task_type: str,
        input_data: Dict[str, Any],
        required_capabilities: Optional[List[AgentCapability]] = None,
        priority: TaskPriority = TaskPriority.NORMAL,
        timeout: float = 60.0,
        max_retries: int = 3,
        metadata: Optional[Dict[str, Any]] = None,
        description: str = ""
    ) -> Task:
        """
        创建任务

        Args:
            name: 任务名称
            task_type: 任务类型
            input_data: 输入数据
            required_capabilities: 所需能力
            priority: 优先级
            timeout: 超时时间
            max_retries: 最大重试次数
            metadata: 元数据
            description: 任务描述

        Returns:
            创建的任务
        """
        task_id = f"task_{uuid.uuid4().hex[:12]}"

        # 推断能力
        if required_capabilities is None:
            required_capabilities = self._infer_capabilities(task_type)

        task = Task(
            task_id=task_id,
            name=name,
            description=description,
            task_type=task_type,
            required_capabilities=required_capabilities,
            input_data=input_data,
            priority=priority,
            timeout=timeout,
            max_retries=max_retries,
            metadata=metadata or {}
        )

        async with self._lock:
            self._tasks[task_id] = task

        logger.debug(f"Task created: {task_id} ({name})")

        # 触发事件
        await self._emit("task_created", {"task": task})

        return task

    def _infer_capabilities(self, task_type: str) -> List[AgentCapability]:
        """根据任务类型推断所需能力"""
        capability_map = {
            "form_recognition": [AgentCapability.FORM_RECOGNITION],
            "field_extraction": [AgentCapability.FIELD_EXTRACTION],
            "validation": [AgentCapability.VALIDATION],
            "approval": [AgentCapability.APPROVAL],
            "data_analysis": [AgentCapability.DATA_ANALYSIS],
            "image_understand": [AgentCapability.IMAGE_UNDERSTANDING],
            "code_generate": [AgentCapability.CODE_GENERATION],
        }
        return capability_map.get(task_type, [AgentCapability.GENERAL])

    async def get_task(self, task_id: str) -> Optional[Task]:
        """获取任务"""
        return self._tasks.get(task_id)

    async def cancel_task(self, task_id: str) -> bool:
        """
        取消任务

        Args:
            task_id: 任务ID

        Returns:
            是否成功
        """
        task = self._tasks.get(task_id)
        if not task:
            return False

        if task.status in (TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED):
            return False

        task.status = TaskStatus.CANCELLED
        task.completed_at = datetime.now()

        # 如果有分配的Agent，释放它
        if task.assigned_agent:
            await self._agent_manager.decrement_tasks(task.assigned_agent)

        logger.info(f"Task cancelled: {task_id}")

        await self._emit("task_cancelled", {"task": task})

        return True

    async def list_tasks(
        self,
        status: Optional[TaskStatus] = None,
        assigned_agent: Optional[str] = None,
        limit: int = 100
    ) -> List[Task]:
        """列出任务"""
        tasks = list(self._tasks.values())

        if status:
            tasks = [t for t in tasks if t.status == status]

        if assigned_agent:
            tasks = [t for t in tasks if t.assigned_agent == assigned_agent]

        # 按创建时间倒序
        tasks.sort(key=lambda t: t.created_at, reverse=True)

        return tasks[:limit]

    # ==================== 路由选择 ====================

    async def _select_agent(
        self,
        task: Task,
        strategy: Optional[RouteStrategy] = None
    ) -> Optional[str]:
        """
        为任务选择Agent

        Args:
            task: 任务
            strategy: 路由策略

        Returns:
            选中的Agent ID
        """
        strategy = strategy or self._default_strategy

        if task.required_capabilities:
            primary_cap = task.required_capabilities[0]
        else:
            primary_cap = AgentCapability.GENERAL

        if strategy == RouteStrategy.LOAD_BALANCE:
            agent = await self._agent_manager.select_agent(
                primary_cap, strategy="load_balance"
            )
        elif strategy == RouteStrategy.PRIORITY:
            agent = await self._agent_manager.select_agent(
                primary_cap, strategy="priority"
            )
        elif strategy == RouteStrategy.ROUND_ROBIN:
            # 轮询策略
            agent_ids = []
            for cap in task.required_capabilities:
                agents = await self._agent_manager.list_by_capability(cap)
                agent_ids.extend([a.agent_id for a in agents if a.is_available()])

            if not agent_ids:
                return None

            # 获取当前索引
            idx = self._round_robin_index.get(primary_cap.value, 0)
            selected_id = agent_ids[idx % len(agent_ids)]
            self._round_robin_index[primary_cap.value] = (idx + 1) % len(agent_ids)

            return selected_id
        elif strategy == RouteStrategy.LEAST_USED:
            agent = await self._agent_manager.select_agent(
                primary_cap, strategy="least_used"
            )
        else:
            # 默认负载均衡
            agent = await self._agent_manager.select_agent(
                primary_cap, strategy="load_balance"
            )

        return agent.agent_id if agent else None

    # ==================== 任务执行 ====================

    async def submit_task(
        self,
        name: str,
        task_type: str,
        input_data: Dict[str, Any],
        required_capabilities: Optional[List[AgentCapability]] = None,
        priority: TaskPriority = TaskPriority.NORMAL,
        timeout: float = 60.0,
        max_retries: int = 3,
        wait: bool = True,
        metadata: Optional[Dict[str, Any]] = None,
        description: str = "",
        strategy: Optional[RouteStrategy] = None
    ) -> Task:
        """
        提交任务

        Args:
            name: 任务名称
            task_type: 任务类型
            input_data: 输入数据
            required_capabilities: 所需能力
            priority: 优先级
            timeout: 超时时间
            max_retries: 最大重试次数
            wait: 是否等待完成
            metadata: 元数据
            description: 任务描述
            strategy: 路由策略

        Returns:
            任务对象
        """
        # 创建任务
        task = await self.create_task(
            name=name,
            task_type=task_type,
            input_data=input_data,
            required_capabilities=required_capabilities,
            priority=priority,
            timeout=timeout,
            max_retries=max_retries,
            metadata=metadata,
            description=description
        )

        # 触发执行
        asyncio.create_task(self._execute_task(task, strategy))

        # 如果需要等待
        if wait:
            await self._wait_for_completion(task)

        return task

    async def _execute_task(
        self,
        task: Task,
        strategy: Optional[RouteStrategy] = None
    ) -> TaskResult:
        """
        执行任务

        Args:
            task: 任务
            strategy: 路由策略

        Returns:
            任务结果
        """
        task_id = task.task_id

        try:
            # 路由选择
            task.status = TaskStatus.ROUTING
            agent_id = await self._select_agent(task, strategy)

            if not agent_id:
                raise Exception(f"No available agent for task: {task_id}")

            # 分配Agent
            task.assigned_agent = agent_id
            task.status = TaskStatus.ASSIGNED
            await self._agent_manager.increment_tasks(agent_id)

            logger.info(f"Task {task_id} assigned to agent {agent_id}")

            await self._emit("task_assigned", {
                "task": task,
                "agent_id": agent_id
            })

            # 执行
            task.status = TaskStatus.RUNNING
            task.started_at = datetime.now()

            await self._emit("task_started", {"task": task})

            # 检查是否有处理器
            if task.task_type in self._handlers:
                result = await asyncio.wait_for(
                    self._handlers[task.task_type](task, agent_id),
                    timeout=task.timeout
                )
            else:
                # 默认处理：模拟执行
                await asyncio.sleep(0.1)  # 模拟处理
                result = {"status": "processed", "task_id": task_id}

            # 完成
            task.status = TaskStatus.COMPLETED
            task.result = result
            task.completed_at = datetime.now()

            duration = task.duration() or 0.0

            # 记录指标
            await self._agent_manager.record_success(agent_id, duration)

            logger.info(f"Task {task_id} completed in {duration:.2f}s")

            await self._emit("task_completed", {
                "task": task,
                "duration": duration
            })

            return TaskResult(
                task_id=task_id,
                success=True,
                result=result,
                duration=duration,
                agent_id=agent_id
            )

        except asyncio.TimeoutError:
            return await self._handle_failure(
                task, "Task timeout", is_timeout=True
            )

        except Exception as e:
            return await self._handle_failure(task, str(e))

    async def _handle_failure(
        self,
        task: Task,
        error: str,
        is_timeout: bool = False
    ) -> TaskResult:
        """处理任务失败"""
        task.error = error
        duration = task.duration() or 0.0

        # 释放Agent
        if task.assigned_agent:
            await self._agent_manager.decrement_tasks(task.assigned_agent)
            await self._agent_manager.record_failure(task.assigned_agent, duration)

        # 检查是否可以重试
        if task.can_retry():
            task.retry_count += 1
            task.status = TaskStatus.PENDING
            task.assigned_agent = None
            task.started_at = None
            task.error = None

            logger.warning(
                f"Task {task.task_id} failed, retry {task.retry_count}/{task.max_retries}"
            )

            # 延迟重试
            await asyncio.sleep(min(2 ** task.retry_count, 10))
            asyncio.create_task(self._execute_task(task))

            return TaskResult(
                task_id=task.task_id,
                success=False,
                error=error,
                duration=duration,
                agent_id=task.assigned_agent,
                metadata={"retry_scheduled": True}
            )
        else:
            task.status = TaskStatus.FAILED if is_timeout else TaskStatus.FAILED
            task.completed_at = datetime.now()

            if is_timeout:
                task.status = TaskStatus.TIMEOUT

            logger.error(f"Task {task.task_id} failed after {task.retry_count} retries: {error}")

            await self._emit("task_failed", {
                "task": task,
                "error": error
            })

            return TaskResult(
                task_id=task.task_id,
                success=False,
                error=error,
                duration=duration,
                agent_id=task.assigned_agent
            )

    async def _wait_for_completion(self, task: Task, poll_interval: float = 0.1) -> None:
        """等待任务完成"""
        while task.status in (TaskStatus.PENDING, TaskStatus.ROUTING, TaskStatus.ASSIGNED, TaskStatus.RUNNING):
            await asyncio.sleep(poll_interval)

    # ==================== 批处理 ====================

    async def submit_batch(
        self,
        tasks: List[Dict[str, Any]],
        max_concurrent: int = 5,
        wait: bool = True
    ) -> List[Task]:
        """
        批量提交任务

        Args:
            tasks: 任务列表
            max_concurrent: 最大并发数
            wait: 是否等待完成

        Returns:
            提交的任务列表
        """
        # 优先级排序
        priority_order = {
            TaskPriority.CRITICAL: 0,
            TaskPriority.URGENT: 1,
            TaskPriority.HIGH: 2,
            TaskPriority.NORMAL: 3,
            TaskPriority.LOW: 4
        }

        sorted_tasks = sorted(
            tasks,
            key=lambda t: priority_order.get(
                TaskPriority(t.get("priority", "normal")),
                TaskPriority.NORMAL
            )
        )

        created_tasks = []

        # 分批提交
        for i in range(0, len(sorted_tasks), max_concurrent):
            batch = sorted_tasks[i:i + max_concurrent]

            for task_def in batch:
                t = await self.submit_task(
                    name=task_def["name"],
                    task_type=task_def["task_type"],
                    input_data=task_def["input_data"],
                    required_capabilities=task_def.get("required_capabilities"),
                    priority=TaskPriority(task_def.get("priority", "normal")),
                    timeout=task_def.get("timeout", 60.0),
                    max_retries=task_def.get("max_retries", 3),
                    wait=False,
                    metadata=task_def.get("metadata"),
                    description=task_def.get("description", "")
                )
                created_tasks.append(t)

        if wait:
            await asyncio.gather(*[self._wait_for_completion(t) for t in created_tasks])

        return created_tasks

    # ==================== 事件系统 ====================

    def on(self, event: str, callback: Callable) -> None:
        """注册事件回调"""
        self._callbacks[event].append(callback)

    async def _emit(self, event: str, data: Dict[str, Any]) -> None:
        """触发事件"""
        for callback in self._callbacks.get(event, []):
            try:
                if asyncio.iscoroutinefunction(callback):
                    await callback(data)
                else:
                    callback(data)
            except Exception as e:
                logger.error(f"Callback error: {e}")

    # ==================== 统计 ====================

    async def get_stats(self) -> Dict[str, Any]:
        """获取路由统计"""
        tasks = list(self._tasks.values())

        status_counts = {}
        for t in tasks:
            status_counts[t.status.value] = status_counts.get(t.status.value, 0) + 1

        return {
            "total_tasks": len(tasks),
            "status_distribution": status_counts,
            "agent_metrics": await self._agent_manager.health_check()
        }


# 全局实例
_task_router: Optional[TaskRouter] = None


def get_task_router() -> TaskRouter:
    """获取全局任务路由器"""
    global _task_router
    if _task_router is None:
        _task_router = TaskRouter(get_agent_manager())
    return _task_router
