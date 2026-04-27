# -*- coding: utf-8 -*-
"""
审批工作流 - 分级审批系统
==========================
基于风险评估的自动化审批工作流

审批级别：
- AUTO: 自动通过（无风险）
- FAST: 快速审批（低风险）
- NORMAL: 正常审批（中风险）
- STRICT: 严格审批（高风险）
- MANUAL: 人工审批（极高风险）
"""

import uuid
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Callable, Dict, List, Optional, Set
import logging

logger = logging.getLogger(__name__)


class ApprovalLevel(str, Enum):
    """审批级别"""
    AUTO = "auto"              # 自动通过
    FAST = "fast"             # 快速审批
    NORMAL = "normal"         # 正常审批
    STRICT = "strict"         # 严格审批
    MANUAL = "manual"         # 人工审批


class ApprovalStatus(str, Enum):
    """审批状态"""
    PENDING = "pending"        # 待审批
    IN_PROGRESS = "in_progress"  # 审批中
    APPROVED = "approved"      # 已批准
    REJECTED = "rejected"      # 已拒绝
    CANCELLED = "cancelled"    # 已取消
    EXPIRED = "expired"        # 已过期


class ApprovalAction(str, Enum):
    """审批动作"""
    APPROVE = "approve"        # 批准
    REJECT = "reject"          # 拒绝
    REVISE = "revise"          # 修改后批准
    ESCALATE = "escalate"      # 升级


@dataclass
class ApprovalStep:
    """
    审批步骤

    Attributes:
        step_id: 步骤ID
        level: 审批级别
        approver: 审批人
        status: 状态
        comment: 审批意见
        decided_at: 审批时间
        deadline: 截止时间
    """
    step_id: str
    level: ApprovalLevel
    approver: str
    status: ApprovalStatus = ApprovalStatus.PENDING
    comment: Optional[str] = None
    decided_at: Optional[datetime] = None
    deadline: Optional[datetime] = None

    def duration(self) -> Optional[float]:
        """获取审批耗时（小时）"""
        if self.decided_at:
            return (self.decided_at - datetime.now()).total_seconds() / 3600
        return None


@dataclass
class ApprovalRequest:
    """
    审批请求

    Attributes:
        request_id: 请求ID
        title: 请求标题
        content: 请求内容
        requester: 请求人
        risk_level: 风险级别
        approval_level: 审批级别
        required_levels: 需要的审批级别列表
        current_level: 当前审批级别
        steps: 审批步骤列表
        status: 状态
        created_at: 创建时间
        updated_at: 更新时间
        metadata: 元数据
    """
    request_id: str
    title: str
    content: Any
    requester: str
    risk_level: str = "medium"
    approval_level: ApprovalLevel = ApprovalLevel.NORMAL
    required_levels: List[ApprovalLevel] = field(default_factory=list)
    current_level: int = 0
    steps: List[ApprovalStep] = field(default_factory=list)
    status: ApprovalStatus = ApprovalStatus.PENDING
    created_at: datetime = field(default_factory=datetime.now)
    updated_at: datetime = field(default_factory=datetime.now)
    metadata: Dict[str, Any] = field(default_factory=dict)

    def current_step(self) -> Optional[ApprovalStep]:
        """获取当前审批步骤"""
        for step in self.steps:
            if step.status == ApprovalStatus.IN_PROGRESS:
                return step
        # 返回下一个待审批步骤
        for step in self.steps:
            if step.status == ApprovalStatus.PENDING:
                return step
        return None

    def progress(self) -> float:
        """获取审批进度（0-1）"""
        if not self.steps:
            return 0.0
        completed = sum(
            1 for s in self.steps
            if s.status in (ApprovalStatus.APPROVED, ApprovalStatus.REJECTED)
        )
        return completed / len(self.steps)


@dataclass
class ApprovalResult:
    """
    审批结果

    Attributes:
        request_id: 请求ID
        approved: 是否批准
        status: 最终状态
        level: 最终审批级别
        steps: 审批步骤
        final_comment: 最终意见
        duration: 总耗时（小时）
        metadata: 附加信息
    """
    request_id: str
    approved: bool
    status: ApprovalStatus
    level: ApprovalLevel
    steps: List[ApprovalStep]
    final_comment: Optional[str] = None
    duration: Optional[float] = None
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "request_id": self.request_id,
            "approved": self.approved,
            "status": self.status.value,
            "level": self.level.value,
            "steps": [
                {
                    "step_id": s.step_id,
                    "level": s.level.value,
                    "approver": s.approver,
                    "status": s.status.value,
                    "comment": s.comment
                }
                for s in self.steps
            ],
            "duration": self.duration
        }


class ApprovalWorkflow:
    """
    审批工作流

    功能：
    - 自动确定审批级别
    - 创建审批流程
    - 执行审批步骤
    - 处理审批结果
    - 超时管理
    """

    def __init__(self):
        # 审批级别映射
        self._level_mapping: Dict[str, ApprovalLevel] = {
            "auto": ApprovalLevel.AUTO,
            "low": ApprovalLevel.FAST,
            "medium": ApprovalLevel.NORMAL,
            "high": ApprovalLevel.STRICT,
            "critical": ApprovalLevel.MANUAL
        }

        # 审批人配置
        self._approvers: Dict[ApprovalLevel, List[str]] = {
            ApprovalLevel.AUTO: [],  # 自动审批，无需审批人
            ApprovalLevel.FAST: ["auto_approver"],
            ApprovalLevel.NORMAL: ["level1_approver"],
            ApprovalLevel.STRICT: ["level1_approver", "level2_approver"],
            ApprovalLevel.MANUAL: ["manual_approver"]
        }

        # 审批超时时间（小时）
        self._timeout_hours: Dict[ApprovalLevel, float] = {
            ApprovalLevel.AUTO: 0,
            ApprovalLevel.FAST: 1,
            ApprovalLevel.NORMAL: 24,
            ApprovalLevel.STRICT: 72,
            ApprovalLevel.MANUAL: 168  # 一周
        }

        # 审批规则
        self._rules: List[Callable] = []

        # 审批历史
        self._history: List[ApprovalRequest] = []

        # 回调函数
        self._callbacks: Dict[str, List[Callable]] = {}

        logger.info("ApprovalWorkflow initialized")

    # ==================== 配置 ====================

    def set_level_mapping(self, mapping: Dict[str, ApprovalLevel]) -> None:
        """设置风险级别到审批级别的映射"""
        self._level_mapping.update(mapping)

    def set_approvers(self, level: ApprovalLevel, approvers: List[str]) -> None:
        """设置审批级别对应的审批人"""
        self._approvers[level] = approvers

    def set_timeout(self, level: ApprovalLevel, hours: float) -> None:
        """设置审批超时时间"""
        self._timeout_hours[level] = hours

    def add_rule(self, rule: Callable) -> None:
        """
        添加审批规则

        Args:
            rule: 规则函数，签名: def rule(request) -> Optional[ApprovalLevel]
                  返回None表示不覆盖，返回ApprovalLevel表示强制使用该级别
        """
        self._rules.append(rule)

    def on_event(self, event: str, callback: Callable) -> None:
        """注册事件回调"""
        if event not in self._callbacks:
            self._callbacks[event] = []
        self._callbacks[event].append(callback)

    async def _emit(self, event: str, data: Dict[str, Any]) -> None:
        """触发事件"""
        for callback in self._callbacks.get(event, []):
            try:
                if hasattr(callback, "__call__"):
                    result = callback(data)
                    if hasattr(result, "__await__"):
                        await result
            except Exception as e:
                logger.error(f"Callback error: {e}")

    # ==================== 核心功能 ====================

    def determine_level(self, risk_level: str, context: Dict[str, Any]) -> ApprovalLevel:
        """
        确定审批级别

        Args:
            risk_level: 风险级别
            context: 上下文信息

        Returns:
            审批级别
        """
        # 1. 基础级别
        level = self._level_mapping.get(risk_level.lower(), ApprovalLevel.NORMAL)

        # 2. 应用规则
        for rule in self._rules:
            try:
                override = rule({
                    "risk_level": risk_level,
                    "context": context
                })
                if override is not None:
                    level = override
            except Exception as e:
                logger.warning(f"Rule evaluation failed: {e}")

        # 3. 特殊条件升级
        # 金额超限
        amount = context.get("amount", 0)
        if amount > 100000:  # 10万以上
            level = max(level, ApprovalLevel.STRICT)
        if amount > 1000000:  # 100万以上
            level = ApprovalLevel.MANUAL

        # 紧急标记
        if context.get("urgent"):
            level = max(level, ApprovalLevel.STRICT)

        return level

    def create_request(
        self,
        title: str,
        content: Any,
        requester: str,
        risk_level: str = "medium",
        context: Optional[Dict[str, Any]] = None
    ) -> ApprovalRequest:
        """
        创建审批请求

        Args:
            title: 请求标题
            content: 请求内容
            requester: 请求人
            risk_level: 风险级别
            context: 上下文信息

        Returns:
            审批请求
        """
        context = context or {}
        approval_level = self.determine_level(risk_level, context)

        request_id = f"apr_{uuid.uuid4().hex[:12]}"

        # 如果是自动审批，直接通过
        if approval_level == ApprovalLevel.AUTO:
            request = ApprovalRequest(
                request_id=request_id,
                title=title,
                content=content,
                requester=requester,
                risk_level=risk_level,
                approval_level=approval_level,
                status=ApprovalStatus.APPROVED,
                metadata=context
            )
            return request

        # 构建审批步骤
        steps = []
        approver_list = self._approvers.get(approval_level, [])

        for i, approver in enumerate(approver_list):
            level_map = {
                ApprovalLevel.FAST: ApprovalLevel.FAST,
                ApprovalLevel.NORMAL: ApprovalLevel.NORMAL,
                ApprovalLevel.STRICT: ApprovalLevel.STRICT if i == 0 else ApprovalLevel.NORMAL,
                ApprovalLevel.MANUAL: ApprovalLevel.MANUAL
            }

            step = ApprovalStep(
                step_id=f"{request_id}_step_{i+1}",
                level=level_map.get(approval_level, approval_level),
                approver=approver,
                deadline=datetime.now().replace(
                    hour=23, minute=59, second=59
                ) if i == 0 else None
            )
            steps.append(step)

        request = ApprovalRequest(
            request_id=request_id,
            title=title,
            content=content,
            requester=requester,
            risk_level=risk_level,
            approval_level=approval_level,
            steps=steps,
            status=ApprovalStatus.PENDING,
            metadata=context
        )

        # 保存历史
        self._history.append(request)

        logger.info(f"Approval request created: {request_id}, level={approval_level}")

        return request

    async def approve(
        self,
        request_id: str,
        approver: str,
        action: ApprovalAction = ApprovalAction.APPROVE,
        comment: Optional[str] = None
    ) -> ApprovalResult:
        """
        执行审批动作

        Args:
            request_id: 请求ID
            approver: 审批人
            action: 审批动作
            comment: 审批意见

        Returns:
            审批结果
        """
        # 查找请求
        request = self._find_request(request_id)
        if not request:
            raise ValueError(f"Request not found: {request_id}")

        # 验证审批人
        current_step = request.current_step()
        if not current_step:
            raise ValueError("No pending approval step")

        if approver != current_step.approver:
            raise PermissionError(f"Not authorized: {approver}")

        # 执行审批
        current_step.status = ApprovalStatus.IN_PROGRESS
        current_step.decided_at = datetime.now()
        current_step.comment = comment

        if action == ApprovalAction.APPROVE:
            current_step.status = ApprovalStatus.APPROVED

            # 检查是否还有下一步
            remaining = [s for s in request.steps if s.status == ApprovalStatus.PENDING]

            if remaining:
                # 进入下一步
                request.status = ApprovalStatus.IN_PROGRESS
                remaining[0].status = ApprovalStatus.IN_PROGRESS
            else:
                # 全部完成
                request.status = ApprovalStatus.APPROVED
                await self._emit("request_approved", {"request": request})

        elif action == ApprovalAction.REJECT:
            current_step.status = ApprovalStatus.REJECTED
            request.status = ApprovalStatus.REJECTED
            await self._emit("request_rejected", {"request": request})

        elif action == ApprovalAction.ESCALATE:
            # 升级：添加到更高级别
            higher_level = self._get_higher_level(request.approval_level)
            if higher_level:
                request.approval_level = higher_level
                new_approver = self._approvers.get(higher_level, ["escalated_approver"])
                new_step = ApprovalStep(
                    step_id=f"{request_id}_step_escalated",
                    level=higher_level,
                    approver=new_approver[0] if new_approver else "escalated_approver"
                )
                request.steps.append(new_step)
            current_step.status = ApprovalStatus.APPROVED
            await self._emit("request_escalated", {"request": request})

        request.updated_at = datetime.now()

        # 构建结果
        duration = (request.updated_at - request.created_at).total_seconds() / 3600

        return ApprovalResult(
            request_id=request_id,
            approved=request.status == ApprovalStatus.APPROVED,
            status=request.status,
            level=request.approval_level,
            steps=request.steps,
            final_comment=comment,
            duration=duration
        )

    def _find_request(self, request_id: str) -> Optional[ApprovalRequest]:
        """查找审批请求"""
        for req in self._history:
            if req.request_id == request_id:
                return req
        return None

    def _get_higher_level(self, current: ApprovalLevel) -> Optional[ApprovalLevel]:
        """获取更高审批级别"""
        hierarchy = [
            ApprovalLevel.AUTO,
            ApprovalLevel.FAST,
            ApprovalLevel.NORMAL,
            ApprovalLevel.STRICT,
            ApprovalLevel.MANUAL
        ]

        try:
            idx = hierarchy.index(current)
            if idx < len(hierarchy) - 1:
                return hierarchy[idx + 1]
        except ValueError:
            pass

        return None

    # ==================== 查询 ====================

    def get_request(self, request_id: str) -> Optional[ApprovalRequest]:
        """获取审批请求"""
        return self._find_request(request_id)

    def list_pending(self, approver: Optional[str] = None) -> List[ApprovalRequest]:
        """
        列出待审批请求

        Args:
            approver: 审批人过滤

        Returns:
            请求列表
        """
        pending = [
            req for req in self._history
            if req.status in (ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS)
        ]

        if approver:
            pending = [
                req for req in pending
                if req.current_step() and req.current_step().approver == approver
            ]

        return sorted(pending, key=lambda r: r.created_at, reverse=True)

    def list_by_requester(self, requester: str) -> List[ApprovalRequest]:
        """列出某用户的审批请求"""
        return [
            req for req in self._history
            if req.requester == requester
        ]

    def get_history(
        self,
        status: Optional[ApprovalStatus] = None,
        limit: int = 100
    ) -> List[ApprovalRequest]:
        """
        获取审批历史

        Args:
            status: 状态过滤
            limit: 返回数量限制

        Returns:
            请求列表
        """
        history = self._history

        if status:
            history = [req for req in history if req.status == status]

        return sorted(history, key=lambda r: r.updated_at, reverse=True)[:limit]

    # ==================== 统计 ====================

    def get_stats(self) -> Dict[str, Any]:
        """获取审批统计"""
        total = len(self._history)
        if total == 0:
            return {"total": 0}

        approved = sum(1 for r in self._history if r.status == ApprovalStatus.APPROVED)
        rejected = sum(1 for r in self._history if r.status == ApprovalStatus.REJECTED)
        pending = sum(1 for r in self._history
                     if r.status in (ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS))

        return {
            "total": total,
            "approved": approved,
            "rejected": rejected,
            "pending": pending,
            "approval_rate": approved / total if total > 0 else 0,
            "level_distribution": {
                level.value: sum(1 for r in self._history if r.approval_level == level)
                for level in ApprovalLevel
            }
        }


# 全局实例
_approval_workflow: Optional[ApprovalWorkflow] = None


def get_approval_workflow() -> ApprovalWorkflow:
    """获取全局审批工作流"""
    global _approval_workflow
    if _approval_workflow is None:
        _approval_workflow = ApprovalWorkflow()
    return _approval_workflow
