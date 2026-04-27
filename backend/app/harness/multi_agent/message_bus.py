# -*- coding: utf-8 -*-
"""
消息总线 - Agent间消息通信
===========================
支持Pub/Sub模式、Request/Response模式的消息传递
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


class MessageType(str, Enum):
    """消息类型"""
    # 点对点消息
    DIRECT = "direct"            # 直接消息
    REQUEST = "request"         # 请求消息（等待响应）
    RESPONSE = "response"       # 响应消息

    # 发布订阅
    PUBLISH = "publish"         # 发布消息
    SUBSCRIBE = "subscribe"     # 订阅消息

    # 系统消息
    BROADCAST = "broadcast"     # 广播消息
    HEARTBEAT = "heartbeat"     # 心跳消息
    SYSTEM = "system"           # 系统消息


class MessagePriority(str, Enum):
    """消息优先级"""
    LOW = "low"
    NORMAL = "normal"
    HIGH = "high"
    CRITICAL = "critical"


@dataclass
class Message:
    """
    消息定义

    Attributes:
        message_id: 消息ID
        msg_type: 消息类型
        sender: 发送者
        receiver: 接收者（可选，用于DIRECT/REQUEST类型）
        topic: 主题（用于PUBLISH/SUBSCRIBE类型）
        content: 消息内容
        priority: 优先级
        correlation_id: 关联ID（用于请求响应配对）
        ttl: 生存时间（秒）
        headers: 消息头
        created_at: 创建时间
        metadata: 元数据
    """
    message_id: str
    msg_type: MessageType
    sender: str
    receiver: Optional[str] = None
    topic: Optional[str] = None
    content: Any = None
    priority: MessagePriority = MessagePriority.NORMAL
    correlation_id: Optional[str] = None
    ttl: float = 300.0  # 默认5分钟
    headers: Dict[str, str] = field(default_factory=dict)
    created_at: datetime = field(default_factory=datetime.now)
    metadata: Dict[str, Any] = field(default_factory=dict)

    def is_expired(self) -> bool:
        """检查消息是否过期"""
        elapsed = (datetime.now() - self.created_at).total_seconds()
        return elapsed > self.ttl


@dataclass
class AgentMessage:
    """
    Agent间通信消息

    扩展Message，添加Agent特定字段
    """
    action: Optional[str] = None       # 操作类型
    data: Optional[Dict[str, Any]] = None
    callback: Optional[str] = None     # 回调方法


class MessageBus:
    """
    消息总线

    功能：
    - 点对点消息发送
    - 请求/响应模式
    - 发布/订阅模式
    - 广播
    - 消息持久化（可选）
    - 死信队列
    """

    def __init__(self):
        # 订阅者表（topic -> agent_ids）
        self._subscribers: Dict[str, Set[str]] = defaultdict(set)

        # 消息处理器（agent_id -> handler）
        self._handlers: Dict[str, Callable] = {}

        # 待处理消息队列
        self._queues: Dict[str, asyncio.Queue] = defaultdict(asyncio.Queue)

        # 响应Future（correlation_id -> future）
        self._pending_responses: Dict[str, asyncio.Future] = {}

        # 死信队列
        self._dead_letter_queue: List[Message] = []

        # 统计
        self._stats = {
            "messages_sent": 0,
            "messages_received": 0,
            "messages_failed": 0,
            "messages_expired": 0
        }

        # 锁
        self._lock = asyncio.Lock()

        # 心跳检查任务
        self._heartbeat_task: Optional[asyncio.Task] = None

        logger.info("MessageBus initialized")

    # ==================== 消息发送 ====================

    async def send(
        self,
        sender: str,
        receiver: str,
        content: Any,
        msg_type: MessageType = MessageType.DIRECT,
        priority: MessagePriority = MessagePriority.NORMAL,
        headers: Optional[Dict[str, str]] = None,
        metadata: Optional[Dict[str, Any]] = None
    ) -> str:
        """
        发送消息

        Args:
            sender: 发送者
            receiver: 接收者
            content: 消息内容
            msg_type: 消息类型
            priority: 优先级
            headers: 消息头
            metadata: 元数据

        Returns:
            消息ID
        """
        message = Message(
            message_id=f"msg_{uuid.uuid4().hex[:12]}",
            msg_type=msg_type,
            sender=sender,
            receiver=receiver,
            content=content,
            priority=priority,
            headers=headers or {},
            metadata=metadata or {}
        )

        await self._deliver(message)

        return message.message_id

    async def request(
        self,
        sender: str,
        receiver: str,
        content: Any,
        timeout: float = 30.0,
        priority: MessagePriority = MessagePriority.NORMAL
    ) -> Any:
        """
        发送请求并等待响应（Request/Response模式）

        Args:
            sender: 发送者
            receiver: 接收者
            content: 请求内容
            timeout: 超时时间
            priority: 优先级

        Returns:
            响应内容

        Raises:
            TimeoutError: 请求超时
        """
        correlation_id = f"corr_{uuid.uuid4().hex[:8]}"

        # 创建Future用于接收响应
        future: asyncio.Future = asyncio.get_event_loop().create_future()
        self._pending_responses[correlation_id] = future

        try:
            # 发送请求消息
            message = Message(
                message_id=f"msg_{uuid.uuid4().hex[:12]}",
                msg_type=MessageType.REQUEST,
                sender=sender,
                receiver=receiver,
                content=content,
                priority=priority,
                correlation_id=correlation_id
            )

            await self._deliver(message)

            # 等待响应
            try:
                response = await asyncio.wait_for(future, timeout=timeout)
                return response
            except asyncio.TimeoutError:
                raise TimeoutError(f"Request to {receiver} timed out after {timeout}s")

        finally:
            # 清理
            self._pending_responses.pop(correlation_id, None)

    async def respond(
        self,
        sender: str,
        correlation_id: str,
        content: Any,
        success: bool = True
    ) -> str:
        """
        发送响应

        Args:
            sender: 发送者
            correlation_id: 关联ID（请求的correlation_id）
            content: 响应内容
            success: 是否成功

        Returns:
            消息ID
        """
        # 找到原始请求（从metadata中获取receiver）
        future = self._pending_responses.get(correlation_id)

        if not future:
            logger.warning(f"No pending request found for correlation_id: {correlation_id}")
            return ""

        # 发送响应
        message = Message(
            message_id=f"msg_{uuid.uuid4().hex[:12]}",
            msg_type=MessageType.RESPONSE,
            sender=sender,
            content={
                "success": success,
                "data": content
            },
            correlation_id=correlation_id
        )

        # 直接设置Future结果
        if not future.done():
            future.set_result(content)

        return message.message_id

    # ==================== 发布订阅 ====================

    async def publish(
        self,
        sender: str,
        topic: str,
        content: Any,
        priority: MessagePriority = MessagePriority.NORMAL,
        metadata: Optional[Dict[str, Any]] = None
    ) -> str:
        """
        发布消息到主题

        Args:
            sender: 发布者
            topic: 主题
            content: 消息内容
            priority: 优先级
            metadata: 元数据

        Returns:
            消息ID
        """
        message = Message(
            message_id=f"msg_{uuid.uuid4().hex[:12]}",
            msg_type=MessageType.PUBLISH,
            sender=sender,
            topic=topic,
            content=content,
            priority=priority,
            metadata=metadata or {}
        )

        await self._deliver(message)

        return message.message_id

    async def subscribe(
        self,
        agent_id: str,
        topic: str,
        handler: Optional[Callable] = None
    ) -> None:
        """
        订阅主题

        Args:
            agent_id: 订阅者
            topic: 主题
            handler: 消息处理函数
        """
        async with self._lock:
            self._subscribers[topic].add(agent_id)

        if handler:
            self._handlers[agent_id] = handler

        logger.debug(f"Agent {agent_id} subscribed to topic: {topic}")

    async def unsubscribe(self, agent_id: str, topic: str) -> None:
        """
        取消订阅

        Args:
            agent_id: 订阅者
            topic: 主题
        """
        async with self._lock:
            if topic in self._subscribers:
                self._subscribers[topic].discard(agent_id)

        logger.debug(f"Agent {agent_id} unsubscribed from topic: {topic}")

    # ==================== 广播 ====================

    async def broadcast(
        self,
        sender: str,
        content: Any,
        priority: MessagePriority = MessagePriority.NORMAL,
        metadata: Optional[Dict[str, Any]] = None
    ) -> str:
        """
        广播消息（发送给所有Agent）

        Args:
            sender: 发送者
            content: 消息内容
            priority: 优先级
            metadata: 元数据

        Returns:
            消息ID
        """
        message = Message(
            message_id=f"msg_{uuid.uuid4().hex[:12]}",
            msg_type=MessageType.BROADCAST,
            sender=sender,
            content=content,
            priority=priority,
            metadata=metadata or {}
        )

        await self._deliver(message)

        return message.message_id

    # ==================== 消息投递 ====================

    async def _deliver(self, message: Message) -> None:
        """投递消息"""
        try:
            if message.is_expired():
                self._stats["messages_expired"] += 1
                logger.warning(f"Message {message.message_id} expired")
                return

            if message.msg_type == MessageType.DIRECT:
                await self._deliver_direct(message)

            elif message.msg_type == MessageType.REQUEST:
                await self._deliver_direct(message)

            elif message.msg_type == MessageType.RESPONSE:
                # 响应通过correlation_id处理
                pass

            elif message.msg_type == MessageType.PUBLISH:
                await self._deliver_publish(message)

            elif message.msg_type == MessageType.BROADCAST:
                await self._deliver_broadcast(message)

            elif message.msg_type == MessageType.HEARTBEAT:
                await self._handle_heartbeat(message)

            self._stats["messages_sent"] += 1

        except Exception as e:
            self._stats["messages_failed"] += 1
            logger.error(f"Failed to deliver message {message.message_id}: {e}")

            # 加入死信队列
            self._dead_letter_queue.append(message)

    async def _deliver_direct(self, message: Message) -> None:
        """投递直接消息"""
        receiver = message.receiver
        if not receiver:
            logger.warning(f"Direct message {message.message_id} has no receiver")
            return

        # 加入接收者队列
        self._queues[receiver].put_nowait(message)

        # 如果有处理器，立即处理
        if receiver in self._handlers:
            try:
                await self._handlers[receiver](message)
                self._stats["messages_received"] += 1
            except Exception as e:
                logger.error(f"Handler error for {receiver}: {e}")

    async def _deliver_publish(self, message: Message) -> None:
        """投递发布消息"""
        topic = message.topic
        if not topic:
            logger.warning(f"Publish message {message.message_id} has no topic")
            return

        # 获取订阅者
        subscribers = self._subscribers.get(topic, set())

        # 投递给所有订阅者
        for subscriber in subscribers:
            self._queues[subscriber].put_nowait(message)

        logger.debug(
            f"Published to {len(subscribers)} subscribers on topic: {topic}"
        )

    async def _deliver_broadcast(self, message: Message) -> None:
        """投递广播消息"""
        # 获取所有订阅者
        all_subscribers = set()
        for subscribers in self._subscribers.values():
            all_subscribers.update(subscribers)

        # 投递给所有人
        for subscriber in all_subscribers:
            if subscriber != message.sender:  # 不发给自己
                self._queues[subscriber].put_nowait(message)

        logger.debug(f"Broadcast to {len(all_subscribers)} agents")

    async def _handle_heartbeat(self, message: Message) -> None:
        """处理心跳消息"""
        # 可以扩展为Agent健康检查
        logger.debug(f"Heartbeat from {message.sender}")

    # ==================== 消息接收 ====================

    async def receive(
        self,
        agent_id: str,
        timeout: Optional[float] = None
    ) -> Optional[Message]:
        """
        接收消息

        Args:
            agent_id: Agent ID
            timeout: 超时时间

        Returns:
            消息或None
        """
        try:
            message = await asyncio.wait_for(
                self._queues[agent_id].get(),
                timeout=timeout
            )
            self._stats["messages_received"] += 1
            return message
        except asyncio.TimeoutError:
            return None

    async def register_handler(self, agent_id: str, handler: Callable) -> None:
        """
        注册消息处理器

        Args:
            agent_id: Agent ID
            handler: 处理函数
        """
        self._handlers[agent_id] = handler
        logger.debug(f"Handler registered for agent: {agent_id}")

    async def unregister_handler(self, agent_id: str) -> None:
        """注销消息处理器"""
        self._handlers.pop(agent_id, None)
        logger.debug(f"Handler unregistered for agent: {agent_id}")

    # ==================== 队列管理 ====================

    async def get_queue_size(self, agent_id: str) -> int:
        """获取Agent队列大小"""
        return self._queues[agent_id].qsize()

    async def clear_queue(self, agent_id: str) -> int:
        """
        清空Agent队列

        Args:
            agent_id: Agent ID

        Returns:
            清空的消息数
        """
        count = 0
        while not self._queues[agent_id].empty():
            try:
                self._queues[agent_id].get_nowait()
                count += 1
            except asyncio.QueueEmpty:
                break
        return count

    # ==================== 死信队列 ====================

    async def get_dead_letters(self) -> List[Message]:
        """获取死信队列"""
        return list(self._dead_letter_queue)

    async def retry_dead_letter(self, message_id: str) -> bool:
        """
        重试死信

        Args:
            message_id: 消息ID

        Returns:
            是否成功
        """
        for i, msg in enumerate(self._dead_letter_queue):
            if msg.message_id == message_id:
                # 重新投递
                self._dead_letter_queue.pop(i)
                await self._deliver(msg)
                return True
        return False

    # ==================== 统计 ====================

    def get_stats(self) -> Dict[str, Any]:
        """获取消息统计"""
        return {
            **self._stats,
            "total_queues": len(self._queues),
            "total_topics": len(self._subscribers),
            "pending_responses": len(self._pending_responses),
            "dead_letter_count": len(self._dead_letter_queue)
        }

    # ==================== 生命周期 ====================

    async def start_heartbeat(self, interval: float = 30.0) -> None:
        """启动心跳检查"""
        async def heartbeat():
            while True:
                await asyncio.sleep(interval)
                message = Message(
                    message_id=f"hb_{uuid.uuid4().hex[:6]}",
                    msg_type=MessageType.HEARTBEAT,
                    sender="message_bus",
                    content={"interval": interval}
                )
                await self._deliver(message)

        self._heartbeat_task = asyncio.create_task(heartbeat())
        logger.info(f"Heartbeat started with interval: {interval}s")

    async def stop_heartbeat(self) -> None:
        """停止心跳检查"""
        if self._heartbeat_task:
            self._heartbeat_task.cancel()
            try:
                await self._heartbeat_task
            except asyncio.CancelledError:
                pass
            logger.info("Heartbeat stopped")


# 全局实例
_message_bus: Optional[MessageBus] = None


def get_message_bus() -> MessageBus:
    """获取全局消息总线"""
    global _message_bus
    if _message_bus is None:
        _message_bus = MessageBus()
    return _message_bus
