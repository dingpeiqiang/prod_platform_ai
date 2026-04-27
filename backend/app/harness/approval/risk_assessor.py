# -*- coding: utf-8 -*-
"""
风险评估器 - 风险评估
======================
评估AI输出和操作的风险等级

风险维度：
1. 数据敏感性 - 是否涉及敏感信息
2. 操作可逆性 - 操作是否可撤销
3. 影响范围 - 影响的用户/系统范围
4. 财务影响 - 潜在的财务损失
5. 合规风险 - 是否符合法规要求
6. 声誉风险 - 对品牌/声誉的影响
"""

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any, Callable, Dict, List, Optional, Set
import logging

logger = logging.getLogger(__name__)


class RiskLevel(str, Enum):
    """风险级别"""
    NONE = "none"           # 无风险
    LOW = "low"            # 低风险
    MEDIUM = "medium"      # 中风险
    HIGH = "high"          # 高风险
    CRITICAL = "critical"   # 极高风险


class RiskFactor(str, Enum):
    """风险因素"""
    DATA_SENSITIVITY = "data_sensitivity"          # 数据敏感性
    OPERATION_REVERSIBILITY = "operation_reversibility"  # 操作可逆性
    IMPACT_SCOPE = "impact_scope"                  # 影响范围
    FINANCIAL_IMPACT = "financial_impact"         # 财务影响
    COMPLIANCE_RISK = "compliance_risk"            # 合规风险
    REPUTATION_RISK = "reputation_risk"           # 声誉风险
    SECURITY_RISK = "security_risk"              # 安全风险
    PRIVACY_RISK = "privacy_risk"                # 隐私风险


@dataclass
class RiskScore:
    """单项风险评分"""
    factor: RiskFactor
    level: RiskLevel
    score: float  # 0-1
    weight: float  # 权重
    reason: str
    evidence: Optional[str] = None


@dataclass
class RiskAssessment:
    """
    风险评估结果

    Attributes:
        overall_level: 总体风险级别
        overall_score: 总体风险评分
        scores: 各因素评分
        factors: 检测到的风险因素
        recommendations: 建议
        requires_approval: 是否需要审批
        approval_level: 建议的审批级别
        metadata: 附加信息
    """
    overall_level: RiskLevel
    overall_score: float
    scores: List[RiskScore]
    factors: List[RiskFactor]
    recommendations: List[str]
    requires_approval: bool
    approval_level: str
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "overall_level": self.overall_level.value,
            "overall_score": self.overall_score,
            "scores": [
                {
                    "factor": s.factor.value,
                    "level": s.level.value,
                    "score": s.score,
                    "weight": s.weight,
                    "reason": s.reason
                }
                for s in self.scores
            ],
            "factors": [f.value for f in self.factors],
            "recommendations": self.recommendations,
            "requires_approval": self.requires_approval,
            "approval_level": self.approval_level
        }


class RiskAssessor:
    """
    风险评估器

    功能：
    - 多维度风险评估
    - 自动权重调整
    - 审批级别建议
    - 风险缓解建议
    """

    # 默认权重
    DEFAULT_WEIGHTS = {
        RiskFactor.DATA_SENSITIVITY: 0.20,
        RiskFactor.OPERATION_REVERSIBILITY: 0.15,
        RiskFactor.IMPACT_SCOPE: 0.15,
        RiskFactor.FINANCIAL_IMPACT: 0.20,
        RiskFactor.COMPLIANCE_RISK: 0.15,
        RiskFactor.REPUTATION_RISK: 0.10,
        RiskFactor.SECURITY_RISK: 0.25,
        RiskFactor.PRIVACY_RISK: 0.20
    }

    # 敏感关键词
    SENSITIVE_KEYWORDS = {
        "password", "secret", "key", "token", "credential",
        "ssn", "身份证", "护照", "银行卡", "credit card",
        "bank account", "社保", "医保"
    }

    # 高风险关键词
    HIGH_RISK_KEYWORDS = {
        "delete", "drop", "truncate", "remove",
        "fire", "terminate", "开除", "删除",
        "transfer", "转账", "payment", "支付",
        "admin", "root", "sudo", "权限"
    }

    def __init__(self):
        # 权重配置
        self._weights = dict(self.DEFAULT_WEIGHTS)

        # 阈值配置
        self._thresholds = {
            "auto": 0.1,    # 自动通过
            "low": 0.3,     # 低风险
            "medium": 0.5,  # 中风险
            "high": 0.7,    # 高风险
            "critical": 1.0  # 极高风险
        }

        # 审批级别映射
        self._approval_mapping = {
            "none": "auto",
            "low": "fast",
            "medium": "normal",
            "high": "strict",
            "critical": "manual"
        }

        # 自定义评估器
        self._custom_evaluators: Dict[RiskFactor, Callable] = {}

        # 历史评估（用于调优）
        self._history: List[RiskAssessment] = []

        logger.info("RiskAssessor initialized")

    # ==================== 配置 ====================

    def set_weight(self, factor: RiskFactor, weight: float) -> None:
        """设置风险因素权重"""
        self._weights[factor] = max(0.0, min(1.0, weight))
        # 归一化
        total = sum(self._weights.values())
        for f in self._weights:
            self._weights[f] /= total

    def set_threshold(self, level: str, threshold: float) -> None:
        """设置风险阈值"""
        self._thresholds[level] = max(0.0, min(1.0, threshold))

    def register_evaluator(self, factor: RiskFactor, evaluator: Callable) -> None:
        """
        注册自定义评估器

        Args:
            factor: 风险因素
            evaluator: 评估函数，签名: def evaluator(context) -> RiskScore
        """
        self._custom_evaluators[factor] = evaluator

    # ==================== 核心评估 ====================

    def assess(
        self,
        operation: str,
        context: Dict[str, Any],
        data: Optional[Any] = None
    ) -> RiskAssessment:
        """
        执行风险评估

        Args:
            operation: 操作类型
            context: 上下文信息
            data: 相关数据

        Returns:
            风险评估结果
        """
        scores = []

        # 1. 数据敏感性评估
        scores.append(self._assess_data_sensitivity(operation, context, data))

        # 2. 操作可逆性评估
        scores.append(self._assess_reversibility(operation, context))

        # 3. 影响范围评估
        scores.append(self._assess_impact_scope(operation, context))

        # 4. 财务影响评估
        scores.append(self._assess_financial_impact(operation, context))

        # 5. 合规风险评估
        scores.append(self._assess_compliance(operation, context, data))

        # 6. 声誉风险评估
        scores.append(self._assess_reputation(operation, context))

        # 7. 安全风险评估
        scores.append(self._assess_security(operation, context, data))

        # 8. 隐私风险评估
        scores.append(self._assess_privacy(operation, context, data))

        # 计算总体评分
        overall_score = sum(s.score * self._weights.get(s.factor, 0.1) for s in scores)

        # 确定风险级别
        overall_level = self._determine_level(overall_score)

        # 确定审批级别
        approval_level = self._approval_mapping.get(overall_level.value, "normal")

        # 检测到的风险因素
        factors = [s.factor for s in scores if s.level not in (RiskLevel.NONE, RiskLevel.LOW)]

        # 生成建议
        recommendations = self._generate_recommendations(scores, overall_level)

        # 判断是否需要审批
        requires_approval = overall_level in (
            RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL
        )

        result = RiskAssessment(
            overall_level=overall_level,
            overall_score=overall_score,
            scores=scores,
            factors=factors,
            recommendations=recommendations,
            requires_approval=requires_approval,
            approval_level=approval_level,
            metadata={
                "operation": operation,
                "context": context,
                "assessed_at": datetime.now().isoformat()
            }
        )

        # 保存历史
        self._history.append(result)

        return result

    def _determine_level(self, score: float) -> RiskLevel:
        """根据评分确定风险级别"""
        if score <= self._thresholds["auto"]:
            return RiskLevel.NONE
        elif score <= self._thresholds["low"]:
            return RiskLevel.LOW
        elif score <= self._thresholds["medium"]:
            return RiskLevel.MEDIUM
        elif score <= self._thresholds["high"]:
            return RiskLevel.HIGH
        else:
            return RiskLevel.CRITICAL

    # ==================== 各维度评估 ====================

    def _assess_data_sensitivity(
        self,
        operation: str,
        context: Dict[str, Any],
        data: Any
    ) -> RiskScore:
        """评估数据敏感性"""
        score = 0.0
        reason = ""
        evidence = None

        # 检查数据内容
        data_str = str(data).lower() if data else ""

        # 敏感关键词检测
        sensitive_count = sum(1 for kw in self.SENSITIVE_KEYWORDS if kw in data_str)

        if sensitive_count > 0:
            score = min(0.5 + sensitive_count * 0.1, 1.0)
            reason = f"检测到 {sensitive_count} 个敏感关键词"
            evidence = data_str[:200] if len(data_str) > 200 else data_str

        # 特定数据类型
        if context.get("data_type") == "personal_info":
            score = max(score, 0.8)
            reason = "涉及个人信息"
        elif context.get("data_type") == "financial":
            score = max(score, 0.7)
            reason = "涉及财务信息"
        elif context.get("data_type") == "health":
            score = max(score, 0.8)
            reason = "涉及健康信息"

        level = self._determine_level(score)

        return RiskScore(
            factor=RiskFactor.DATA_SENSITIVITY,
            level=level,
            score=score,
            weight=self._weights.get(RiskFactor.DATA_SENSITIVITY, 0.2),
            reason=reason or "无敏感数据",
            evidence=evidence
        )

    def _assess_reversibility(
        self,
        operation: str,
        context: Dict[str, Any]
    ) -> RiskScore:
        """评估操作可逆性"""
        score = 0.5  # 默认中等风险
        reason = ""

        # 不可逆操作
        irreversible_keywords = ["delete", "drop", "remove", "永久", "删除"]

        if any(kw in operation.lower() for kw in irreversible_keywords):
            score = 0.9
            reason = "检测到不可逆操作"

        # 可逆操作
        reversible_keywords = ["create", "add", "update", "edit", "新建", "编辑"]

        if any(kw in operation.lower() for kw in reversible_keywords):
            if "update" in operation.lower() or "edit" in operation.lower():
                score = 0.3
                reason = "可编辑/更新操作"
            else:
                score = 0.4
                reason = "相对安全的创建操作"

        # 显式标记
        if context.get("reversible", True):
            score *= 0.5
            reason = "标记为可逆操作"

        level = self._determine_level(score)

        return RiskScore(
            factor=RiskFactor.OPERATION_REVERSIBILITY,
            level=level,
            score=min(score, 1.0),
            weight=self._weights.get(RiskFactor.OPERATION_REVERSIBILITY, 0.15),
            reason=reason or "默认风险"
        )

    def _assess_impact_scope(
        self,
        operation: str,
        context: Dict[str, Any]
    ) -> RiskScore:
        """评估影响范围"""
        score = 0.3
        reason = "默认范围"

        # 影响用户数
        affected_users = context.get("affected_users", 0)
        if affected_users > 1000:
            score = 0.8
            reason = f"影响 {affected_users} 用户"
        elif affected_users > 100:
            score = 0.5
            reason = f"影响 {affected_users} 用户"
        elif affected_users > 0:
            score = 0.3
            reason = f"影响 {affected_users} 用户"

        # 影响系统范围
        scope = context.get("scope", "single")
        if scope == "global" or scope == "all":
            score = max(score, 0.9)
            reason = "影响全局系统"
        elif scope == "department":
            score = max(score, 0.6)
            reason = "影响部门范围"

        level = self._determine_level(score)

        return RiskScore(
            factor=RiskFactor.IMPACT_SCOPE,
            level=level,
            score=score,
            weight=self._weights.get(RiskFactor.IMPACT_SCOPE, 0.15),
            reason=reason
        )

    def _assess_financial_impact(
        self,
        operation: str,
        context: Dict[str, Any]
    ) -> RiskScore:
        """评估财务影响"""
        score = 0.0
        reason = "无财务影响"

        # 涉及金额
        amount = context.get("amount", 0)
        if amount > 1000000:  # 100万
            score = 1.0
            reason = f"涉及金额 ¥{amount:,.2f}"
        elif amount > 100000:  # 10万
            score = 0.8
            reason = f"涉及金额 ¥{amount:,.2f}"
        elif amount > 10000:  # 1万
            score = 0.5
            reason = f"涉及金额 ¥{amount:,.2f}"
        elif amount > 0:
            score = 0.2
            reason = f"涉及金额 ¥{amount:,.2f}"

        # 财务相关操作
        financial_keywords = ["payment", "transfer", "refund", "转账", "退款", "支付"]
        if any(kw in operation.lower() for kw in financial_keywords):
            score = max(score, 0.6)
            reason = f"财务操作: {reason}"

        level = self._determine_level(score)

        return RiskScore(
            factor=RiskFactor.FINANCIAL_IMPACT,
            level=level,
            score=min(score, 1.0),
            weight=self._weights.get(RiskFactor.FINANCIAL_IMPACT, 0.2),
            reason=reason
        )

    def _assess_compliance(
        self,
        operation: str,
        context: Dict[str, Any],
        data: Any
    ) -> RiskScore:
        """评估合规风险"""
        score = 0.2
        reason = "默认合规"

        # 合规要求级别
        compliance_level = context.get("compliance_level", "normal")

        if compliance_level == "strict":
            score = 0.7
            reason = "严格合规要求"
        elif compliance_level == "financial":
            score = 0.6
            reason = "金融合规要求"

        # 法规关键词
        regulations = ["GDPR", "CCPA", "PCI-DSS", "SOC2", "等保"]
        data_str = str(data).lower() if data else ""

        for reg in regulations:
            if reg.lower() in data_str:
                score = max(score, 0.8)
                reason = f"涉及 {reg} 合规"
                break

        level = self._determine_level(score)

        return RiskScore(
            factor=RiskFactor.COMPLIANCE_RISK,
            level=level,
            score=score,
            weight=self._weights.get(RiskFactor.COMPLIANCE_RISK, 0.15),
            reason=reason
        )

    def _assess_reputation(
        self,
        operation: str,
        context: Dict[str, Any]
    ) -> RiskScore:
        """评估声誉风险"""
        score = 0.1
        reason = "低声誉风险"

        # 公开可见操作
        if context.get("public", False):
            score = 0.6
            reason = "公开可见操作"

        # 客户相关
        if context.get("customer_facing", False):
            score = max(score, 0.5)
            reason = "面向客户"

        # 媒体相关
        if context.get("media_sensitive", False):
            score = 0.8
            reason = "媒体敏感"

        level = self._determine_level(score)

        return RiskScore(
            factor=RiskFactor.REPUTATION_RISK,
            level=level,
            score=score,
            weight=self._weights.get(RiskFactor.REPUTATION_RISK, 0.1),
            reason=reason
        )

    def _assess_security(
        self,
        operation: str,
        context: Dict[str, Any],
        data: Any
    ) -> RiskScore:
        """评估安全风险"""
        score = 0.2
        reason = "默认安全"

        # 检查操作类型
        if any(kw in operation.lower() for kw in self.HIGH_RISK_KEYWORDS):
            score = 0.8
            reason = "高风险操作关键词"

        # 权限提升
        if context.get("privilege_escalation", False):
            score = 0.9
            reason = "权限提升操作"

        # SQL/代码注入风险
        data_str = str(data).lower() if data else ""
        injection_patterns = ["union select", "drop table", "exec(", "eval("]

        for pattern in injection_patterns:
            if pattern in data_str:
                score = 1.0
                reason = f"检测到注入风险: {pattern}"
                break

        level = self._determine_level(score)

        return RiskScore(
            factor=RiskFactor.SECURITY_RISK,
            level=level,
            score=score,
            weight=self._weights.get(RiskFactor.SECURITY_RISK, 0.25),
            reason=reason
        )

    def _assess_privacy(
        self,
        operation: str,
        context: Dict[str, Any],
        data: Any
    ) -> RiskScore:
        """评估隐私风险"""
        score = 0.2
        reason = "默认隐私安全"

        # 涉及个人数据
        personal_data_types = [
            "name", "email", "phone", "address",
            "姓名", "邮箱", "电话", "地址"
        ]

        data_str = str(data).lower() if data else ""

        if any(dt in data_str for dt in personal_data_types):
            score = 0.5
            reason = "涉及个人联系信息"

        # 敏感个人数据
        sensitive_types = [
            "ssn", "id_card", "passport",
            "身份证", "护照", "生物识别"
        ]

        if any(dt in data_str for dt in sensitive_types):
            score = 0.9
            reason = "涉及敏感个人数据"

        # 批量导出
        if context.get("bulk_export", False):
            score = max(score, 0.7)
            reason = "批量数据导出"

        level = self._determine_level(score)

        return RiskScore(
            factor=RiskFactor.PRIVACY_RISK,
            level=level,
            score=score,
            weight=self._weights.get(RiskFactor.PRIVACY_RISK, 0.2),
            reason=reason
        )

    # ==================== 建议生成 ====================

    def _generate_recommendations(
        self,
        scores: List[RiskScore],
        overall_level: RiskLevel
    ) -> List[str]:
        """生成风险缓解建议"""
        recommendations = []

        for s in scores:
            if s.level == RiskLevel.HIGH or s.level == RiskLevel.CRITICAL:
                if s.factor == RiskFactor.DATA_SENSITIVITY:
                    recommendations.append("对敏感数据进行脱敏处理")
                elif s.factor == RiskFactor.SECURITY_RISK:
                    recommendations.append("进行安全审查，添加输入验证")
                elif s.factor == RiskFactor.OPERATION_REVERSIBILITY:
                    recommendations.append("确保有备份/回滚方案")
                elif s.factor == RiskFactor.FINANCIAL_IMPACT:
                    recommendations.append("增加财务审批流程")
                elif s.factor == RiskFactor.PRIVACY_RISK:
                    recommendations.append("确保符合隐私法规要求")

        if overall_level == RiskLevel.CRITICAL:
            recommendations.append("建议人工审核所有关键决策")
            recommendations.append("考虑分步执行，设置检查点")

        if overall_level == RiskLevel.HIGH:
            recommendations.append("建议增加审批级别")

        return recommendations

    # ==================== 统计 ====================

    def get_history_stats(self) -> Dict[str, Any]:
        """获取历史统计"""
        if not self._history:
            return {"count": 0}

        level_counts = {level.value: 0 for level in RiskLevel}
        for a in self._history:
            level_counts[a.overall_level.value] += 1

        avg_score = sum(a.overall_score for a in self._history) / len(self._history)

        return {
            "count": len(self._history),
            "average_score": avg_score,
            "level_distribution": level_counts,
            "requires_approval_count": sum(
                1 for a in self._history if a.requires_approval
            )
        }


# 全局实例
_risk_assessor: Optional[RiskAssessor] = None


def get_risk_assessor() -> RiskAssessor:
    """获取全局风险评估器"""
    global _risk_assessor
    if _risk_assessor is None:
        _risk_assessor = RiskAssessor()
    return _risk_assessor
