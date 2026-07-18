"""
本体 MVP 推理服务

配置助手规则集 C（R-C01~C08）+ 智读映射 D（R-D01~D05）
运营助手规则集 A（R-A01~A05）+ 风险稽核 B（R-B01~B05）

权威结论由本服务给出；大模型仅负责表达层，不改写 passFlag。
"""
from __future__ import annotations

import json
import re
from copy import deepcopy
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from app.core.logger import get_logger

logger = get_logger(__name__)

_GRAPH_PATH = Path(__file__).resolve().parents[2] / "config" / "ontology_mvp" / "mock_graph.json"
_graph_cache: Optional[Dict[str, Any]] = None
_risk_rule_overrides: Dict[str, Any] = {}


def _expand_shelf_offerings(base: List[Dict[str, Any]], plan: Dict[str, Any]) -> List[Dict[str, Any]]:
    """按演示方案扩充至约 80 条在架样本（保留重点演示商品）。"""
    by_id = {o["offeringId"]: deepcopy(o) for o in base}
    total = int(plan.get("total", 80))
    need_zero = int(plan.get("zeroFee", 8))
    need_disc = int(plan.get("abnormalDiscount", 5))
    need_low = int(plan.get("lowEff", 7))

    for i in range(1, need_zero + 1):
        oid = f"OF-RISK-{i:03d}"
        if oid in by_id:
            continue
        by_id[oid] = {
            "offeringId": oid,
            "offeringName": f"体验测试流量包0元-{i:02d}",
            "state": "上架",
            "monthlyFee": 0,
            "oneTimeFee": 0,
            "mutexGroup": "ADDON",
            "offeringType": "addon",
            "shelfDays": 35 + i * 3,
            "salesCnt30d": 10 + i,
            "revenue30d": 0,
            "hasContract": False,
            "strategicTag": False,
            "category": "zero_fee",
            "nameHint": "体验",
        }

    for i in range(1, need_disc + 1):
        oid = f"OF-DISC-{i:03d}"
        if oid in by_id:
            continue
        by_id[oid] = {
            "offeringId": oid,
            "offeringName": f"全额赠送可重复包-{i:02d}",
            "state": "上架",
            "monthlyFee": 19,
            "oneTimeFee": 0,
            "discountPercent": 100,
            "repeatable": True,
            "targetCustomerGroup": "",
            "mutexGroup": "ADDON",
            "offeringType": "addon",
            "shelfDays": 30 + i * 2,
            "salesCnt30d": 20 + i,
            "revenue30d": 50,
            "hasContract": False,
            "strategicTag": False,
            "category": "abnormal_discount",
        }

    # 低效样本：默认阈值 180 天即命中，凑齐演示口径「中风险 7 / 建议下架 7」
    # OF-LOW-019 已在 base 中，再补 need_low-1 条
    low_existing = sum(1 for o in by_id.values() if o.get("category") == "low_eff")
    low_seq = 1
    while low_existing < need_low:
        oid = f"OF-LOW-{low_seq:03d}"
        low_seq += 1
        if oid in by_id:
            continue
        idx = low_existing + 1
        by_id[oid] = {
            "offeringId": oid,
            "offeringName": f"旧版加装包-长期零销-{idx:02d}",
            "state": "上架",
            "monthlyFee": 5 + idx,
            "oneTimeFee": 0,
            "mutexGroup": "ADDON",
            "offeringType": "addon",
            "shelfDays": 190 + idx * 14,
            "salesCnt30d": 0,
            "revenue30d": 0,
            "hasContract": False,
            "strategicTag": False,
            "category": "low_eff",
        }
        low_existing += 1

    # 阈值演示样本：在架 90～180 天，仅当阈值收紧到 90 时命中 R-B03
    for i in range(1, 4):
        oid = f"OF-LOW-T{i:02d}"
        if oid in by_id:
            continue
        by_id[oid] = {
            "offeringId": oid,
            "offeringName": f"旧版加装包-阈值演示-{i:02d}",
            "state": "上架",
            "monthlyFee": 6 + i,
            "oneTimeFee": 0,
            "mutexGroup": "ADDON",
            "offeringType": "addon",
            "shelfDays": 100 + i * 12,
            "salesCnt30d": 0,
            "revenue30d": 2 + i,
            "hasContract": False,
            "strategicTag": False,
            "category": "threshold_demo",
        }

    # 强制保留重点演示 ID 语义
    if "OF-RISK-001" in by_id:
        by_id["OF-RISK-001"]["offeringName"] = "校园体验流量包0元"
    if "OF-LOW-019" in by_id:
        by_id["OF-LOW-019"]["offeringName"] = "旧版彩铃包-2019"
        by_id["OF-LOW-019"]["shelfDays"] = 287
        by_id["OF-LOW-019"]["salesCnt30d"] = 0
        by_id["OF-LOW-019"]["revenue30d"] = 0
        by_id["OF-LOW-019"]["category"] = "low_eff"

    seed = 1
    while len(by_id) < total:
        oid = f"OF-N-{seed:03d}"
        if oid not in by_id:
            by_id[oid] = {
                "offeringId": oid,
                "offeringName": f"标准套餐-{seed:03d}",
                "state": "上架",
                "monthlyFee": 39 + (seed % 20) * 5,
                "oneTimeFee": 0,
                "mutexGroup": "MAIN_PKG" if seed % 3 else "ADDON",
                "offeringType": "main_pkg" if seed % 3 else "addon",
                "shelfDays": 60 + seed,
                "salesCnt30d": 80 + seed * 3,
                "revenue30d": 5000 + seed * 120,
                "hasContract": True,
                "strategicTag": seed % 7 == 0,
                "category": "normal",
            }
        seed += 1

    # 稳定顺序：重点商品置前
    priority = ["OF-HF-128", "OF-RISK-001", "OF-LOW-019", "OF-GIFT-WL", "OF-DISC-001"]
    ordered: List[Dict[str, Any]] = []
    for pid in priority:
        if pid in by_id:
            ordered.append(by_id.pop(pid))
    ordered.extend(sorted(by_id.values(), key=lambda x: x["offeringId"]))
    return ordered


def _load_graph() -> Dict[str, Any]:
    global _graph_cache
    if _graph_cache is None:
        with open(_GRAPH_PATH, "r", encoding="utf-8") as f:
            raw = json.load(f)
        raw["shelfOfferings"] = _expand_shelf_offerings(
            raw.get("shelfOfferings", []),
            raw.get("samplePlan", {}),
        )
        _graph_cache = raw
    return _graph_cache


def _reload_graph() -> Dict[str, Any]:
    global _graph_cache
    _graph_cache = None
    return _load_graph()


def _risk_rules() -> Dict[str, Any]:
    graph = _load_graph()
    base = dict(graph.get("riskRuleDefaults", {}))
    base.update(_risk_rule_overrides)
    return base


def _num(value: Any, default: float = 0.0) -> float:
    if value is None or value == "":
        return default
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).strip().replace("元", "").replace("/月", "")
    try:
        return float(text)
    except ValueError:
        m = re.search(r"[\d.]+", text)
        return float(m.group()) if m else default


def _truthy(value: Any) -> bool:
    if isinstance(value, bool):
        return value
    if value is None:
        return False
    return str(value).strip().lower() in {"1", "true", "yes", "y", "是"}


def _empty(value: Any) -> bool:
    return value is None or str(value).strip() == ""


class OntologyMvpService:
    """产商品配置/运营本体 MVP 推理引擎"""

    @classmethod
    def get_graph_summary(cls) -> Dict[str, Any]:
        graph = _load_graph()
        rules = _risk_rules()
        offerings = graph.get("shelfOfferings", [])
        anomaly_count = sum(1 for oid in graph.get("opsGraph", {}) if oid)
        return {
            "success": True,
            "scenarios": list(graph.get("bizScenarios", {}).keys()),
            "templates": list(graph.get("templates", {}).keys()),
            "shelfCount": len(offerings),
            "anomalyOfferingCount": anomaly_count,
            "ruleVersion": rules.get("ruleVersion", "RiskRules-v1.2"),
            "riskRules": rules,
            "shelfOfferings": [
                {
                    "offeringId": o["offeringId"],
                    "offeringName": o["offeringName"],
                    "state": o.get("state"),
                    "monthlyFee": o.get("monthlyFee"),
                    "category": o.get("category"),
                }
                for o in offerings
                if o.get("category") in {"zero_fee", "low_eff", "abnormal_discount", "whitelist"}
                or o["offeringId"] == "OF-HF-128"
            ],
            "classes": [
                {"classCode": "OfferingConfig", "className": "商品配置草稿"},
                {"classCode": "ProductElement", "className": "产品要素属性"},
                {"classCode": "ConfigRule", "className": "配置规则"},
                {"classCode": "TargetUser", "className": "目标用户"},
                {"classCode": "BizScenario", "className": "业务场景"},
                {"classCode": "MarketPolicy", "className": "营销政策"},
                {"classCode": "PricePlan", "className": "商品定价"},
                {"classCode": "GoodsRelation", "className": "商品关系"},
                {"classCode": "ConfigTemplate", "className": "配置模板"},
                {"classCode": "ComplianceIssue", "className": "合规问题"},
            ],
            "relations": [
                "hasElement", "hasPricePlan", "constrainedBy", "forTargetUser",
                "inScenario", "appliesPolicy", "basedOnTemplate", "hasRelation",
                "hasIssue", "blocksCombination", "suggestsDefault", "definesElement",
            ],
            "ruleSets": {
                "config": ["R-C01", "R-C02", "R-C03", "R-C04", "R-C05", "R-C06", "R-C07", "R-C08"],
                "batch": ["R-D01", "R-D02", "R-D03", "R-D04", "R-D05"],
                "opsRootCause": ["R-A01", "R-A02", "R-A03", "R-A04", "R-A05"],
                "opsRisk": ["R-B01", "R-B02", "R-B03", "R-B04", "R-B05"],
            },
        }

    @classmethod
    def get_ontology_meta(cls) -> Dict[str, Any]:
        graph = _load_graph()
        summary = cls.get_graph_summary()
        return {
            "success": True,
            "classes": summary["classes"],
            "bizScenarios": graph.get("bizScenarios", {}),
            "templates": graph.get("templates", {}),
            "equityGiftWhitelist": graph.get("equityGiftWhitelist", []),
            "riskRuleDefaults": _risk_rules(),
        }

    @classmethod
    def get_ops_dashboard(cls) -> Dict[str, Any]:
        """运营助手首页看板：异动数 / 高风险数 / 规则版本"""
        root = cls.analyze_root_cause("OF-HF-128")
        risk = cls.audit_risks()
        rules = _risk_rules()
        return {
            "success": True,
            "anomalyOfferingCount": 1 if root.get("success") else 0,
            "highRiskCount": risk.get("highCount", 0),
            "mediumRiskCount": risk.get("mediumCount", 0),
            "suggestDelistCount": risk.get("suggestDelistCount", 0),
            "shelfCount": risk.get("scannedCount", 0),
            "ruleVersion": rules.get("ruleVersion", "RiskRules-v1.2"),
            "lastAuditAt": risk.get("auditedAt"),
            "alerts": [
                {
                    "id": "alert-hf-128",
                    "type": "anomaly",
                    "tag": "异动",
                    "offeringId": "OF-HF-128",
                    "text": "OF-HF-128 累计收入环比 -18%",
                },
                {
                    "id": "alert-risk",
                    "type": "risk",
                    "tag": "风险",
                    "text": f"高风险在架商品 {risk.get('highCount', 0)} 个待处置",
                },
            ],
        }

    @classmethod
    def update_risk_rules(cls, overrides: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """演示用：动态调整稽核阈值（如零销在架天数 180→90）"""
        global _risk_rule_overrides
        if overrides:
            allowed = {
                "zeroSalesShelfDays",
                "zeroSalesDaysWindow",
                "highRiskReviewDays",
                "lowRevenuePercentile",
                "ruleVersion",
            }
            for k, v in overrides.items():
                if k in allowed:
                    _risk_rule_overrides[k] = v
        return {"success": True, "riskRules": _risk_rules()}

    @classmethod
    def reset_risk_rules(cls) -> Dict[str, Any]:
        global _risk_rule_overrides
        _risk_rule_overrides = {}
        return {"success": True, "riskRules": _risk_rules()}

    @classmethod
    def parse_slots_from_text(cls, text: str) -> Dict[str, Any]:
        """轻量槽位抽取（演示用；生产可由 LLM 替换）"""
        slots: Dict[str, Any] = {}
        if not text:
            return slots

        if any(k in text for k in ("家庭融合", "家庭用户", "融合套餐")):
            slots["bizScenario"] = "家庭融合"
            slots["targetUser"] = "家庭"
            slots["offeringType"] = "fusion"
        elif any(k in text for k in ("校园", "大学生", "迎新")):
            slots["bizScenario"] = "校园体验"
            slots["targetUser"] = "校园"
            slots["offeringType"] = "main_pkg"
        elif "5G" in text or "5g" in text:
            slots["bizScenario"] = "5G个人主套餐"
            slots["targetUser"] = "个人"
            slots["offeringType"] = "main_pkg"

        fee_m = re.search(r"月费\s*(\d+(?:\.\d+)?)", text)
        if not fee_m:
            fee_m = re.search(r"(\d+(?:\.\d+)?)\s*元", text)
        if fee_m:
            slots["monthlyFee"] = float(fee_m.group(1))

        bb_m = re.search(r"(\d+)\s*[Mm](?:宽带)?", text)
        if "宽带" in text and bb_m:
            slots["includeBroadband"] = f"{bb_m.group(1)}M"
        elif bb_m and "家庭" in text:
            slots["includeBroadband"] = f"{bb_m.group(1)}M"

        if "全渠道" in text:
            slots["channelScope"] = "全渠道"
        elif "电渠" in text and "厅店" in text:
            slots["channelScope"] = "电渠+厅店"
        elif "电渠" in text:
            slots["channelScope"] = "仅电渠"

        name_m = re.search(r"(?:叫|名称[是为]?)\s*[「\"]?([^「」\"，。\s]+)[」\"]?", text)
        if name_m:
            slots["offeringName"] = name_m.group(1)
        elif "家庭融合畅享158" in text:
            slots["offeringName"] = "家庭融合畅享158"

        # 冲突解除优先于绑定（演示步骤5：那不加128了，就单独上158）
        if any(k in text for k in ("不加128", "不加畅享128", "不绑128", "单独上", "取消绑定", "解除互斥")):
            slots["bindExistingMainPkg"] = ""
            slots["clearBindExisting"] = True
        elif any(k in text for k in ("再绑", "再加", "一起上", "畅享128", "OF-HF-128")):
            slots["bindExistingMainPkg"] = "OF-HF-128"

        if "无合约" in text or "没有合约" in text:
            slots["hasContract"] = "0"
        if "有合约" in text or "协议期" in text or "补协议" in text:
            slots["hasContract"] = "1"
            months_m = re.search(r"(\d+)\s*个?月", text)
            if months_m:
                slots["contractMonths"] = int(months_m.group(1))
        if "可重复" in text:
            slots["repeatable"] = "true"
        if "不可重复" in text or "不能重复" in text:
            slots["repeatable"] = "false"
        if "0元" in text or "零元" in text:
            slots["monthlyFee"] = 0
        if "内部验证" in text:
            slots["channelScope"] = "内部验证"

        return slots

    @classmethod
    def infer_fields(cls, slots: Dict[str, Any], draft: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """字段推理填报：R-C01 / R-C02 + 模板推荐"""
        graph = _load_graph()
        result = deepcopy(draft or {})
        fill_sources: Dict[str, str] = {}
        applied_rules: List[str] = []

        if (slots or {}).get("clearBindExisting"):
            result["bindExistingMainPkg"] = ""
            fill_sources["bindExistingMainPkg"] = "user_said"

        for key, value in (slots or {}).items():
            if key in ("clearBindExisting",):
                continue
            if key == "bindExistingMainPkg":
                result[key] = value
                if not _empty(value):
                    fill_sources[key] = "user_said"
                continue
            if not _empty(value):
                result[key] = value
                fill_sources[key] = "user_said"

        scenario = result.get("bizScenario") or slots.get("bizScenario")
        scenario_cfg = graph.get("bizScenarios", {}).get(scenario or "", {})
        defaults = scenario_cfg.get("defaults", {})

        # R-C01: 家庭融合缺省补全
        if scenario == "家庭融合":
            for field, default_val in defaults.items():
                if _empty(result.get(field)):
                    result[field] = default_val
                    fill_sources[field] = "scenario_default"
                    applied_rules.append("R-C01")
            if _empty(result.get("includeBroadband")):
                result["includeBroadband"] = defaults.get("includeBroadband", "500M")
                fill_sources["includeBroadband"] = "scenario_default"
                applied_rules.append("R-C01")

        # R-C02: 校园主套餐月费空 → 推荐 59（附加包不自动补月费，留给 R-C06 演示）
        is_campus = result.get("targetUser") == "校园" or scenario == "校园体验"
        offering_type = result.get("offeringType") or slots.get("offeringType")
        if is_campus and _empty(result.get("monthlyFee")) and offering_type != "addon":
            result["monthlyFee"] = defaults.get("monthlyFee", 59)
            fill_sources["monthlyFee"] = "template"
            applied_rules.append("R-C02")
            for field, default_val in defaults.items():
                if field != "monthlyFee" and _empty(result.get(field)):
                    result[field] = default_val
                    fill_sources[field] = "scenario_default"
        elif is_campus and offering_type == "addon":
            for field, default_val in defaults.items():
                if field in ("monthlyFee", "mutexGroup"):
                    continue
                if _empty(result.get(field)):
                    result[field] = default_val
                    fill_sources[field] = "scenario_default"

        template_id = scenario_cfg.get("templateId")
        if template_id and _empty(result.get("basedOnTemplate")):
            result["basedOnTemplate"] = template_id
            fill_sources["basedOnTemplate"] = "template"

        if _empty(result.get("channelScope")):
            result["channelScope"] = "全渠道"
            fill_sources["channelScope"] = "scenario_default"

        if _empty(result.get("mutexGroup")):
            result["mutexGroup"] = defaults.get("mutexGroup", "MAIN_PKG")
            fill_sources["mutexGroup"] = "scenario_default"

        result["fillSources"] = fill_sources
        inferred = [
            {"field": k, "value": result.get(k), "fillSource": src, "rule": "R-C01" if src == "scenario_default" else ("R-C02" if src == "template" else None)}
            for k, src in fill_sources.items()
            if src in ("scenario_default", "template")
        ]

        return {
            "success": True,
            "draft": result,
            "inferredFields": inferred,
            "appliedRules": sorted(set(applied_rules)),
            "recommendedTemplates": [template_id] if template_id else [],
        }

    @classmethod
    def check_compliance(cls, draft: Dict[str, Any]) -> Dict[str, Any]:
        """合规校验：R-C03 ~ R-C08"""
        graph = _load_graph()
        issues: List[Dict[str, Any]] = []
        draft = draft or {}

        # R-C06 必填
        required = [
            ("offeringName", "商品名称"),
            ("monthlyFee", "月费"),
            ("targetUser", "目标用户"),
            ("channelScope", "销售渠道"),
        ]
        for code, name in required:
            if _empty(draft.get(code)):
                issues.append({
                    "ruleId": "R-C06",
                    "issueType": "必填缺失",
                    "issueLevel": "MEDIUM",
                    "field": code,
                    "message": f"缺少必填字段：{name}",
                    "evidence": [f"{code}=empty"],
                })

        # R-C03 互斥
        mutex_group = draft.get("mutexGroup") or "MAIN_PKG"
        bind_id = draft.get("bindExistingMainPkg")
        shelf = {o["offeringId"]: o for o in graph.get("shelfOfferings", [])}
        if bind_id and bind_id in shelf:
            existing = shelf[bind_id]
            if existing.get("mutexGroup") == mutex_group and draft.get("offeringType") in ("main_pkg", "fusion", None):
                issues.append({
                    "ruleId": "R-C03",
                    "issueType": "资费/关系冲突",
                    "issueLevel": "HIGH",
                    "field": "mutexGroup",
                    "message": f"与在架商品 {existing['offeringName']}({bind_id}) 同属互斥组 {mutex_group}，不可同时上架",
                    "evidence": [
                        f"当前草稿—互斥组—{mutex_group}",
                        f"{bind_id}—互斥组—{existing.get('mutexGroup')}",
                        f"当前草稿—冲突对象—{existing['offeringName']}",
                    ],
                    "triples": [
                        {"s": draft.get("offeringName") or "当前草稿", "p": "mutexGroup", "o": mutex_group},
                        {"s": bind_id, "p": "mutexGroup", "o": existing.get("mutexGroup")},
                        {"s": draft.get("offeringName") or "当前草稿", "p": "blocksCombination", "o": existing["offeringName"]},
                    ],
                })

        # R-C04 依赖
        offering_type = draft.get("offeringType")
        if offering_type == "addon" and _empty(draft.get("dependOn")):
            issues.append({
                "ruleId": "R-C04",
                "issueType": "规则漏洞",
                "issueLevel": "HIGH",
                "field": "dependOn",
                "message": "附加包缺少依赖的主服务/宽带",
                "evidence": ["offeringType=addon", "dependOn=empty"],
            })

        # R-C05 零元高风险
        monthly = _num(draft.get("monthlyFee"), default=-1)
        one_time = _num(draft.get("oneTimeFee"), default=0)
        scenario = draft.get("bizScenario") or ""
        whitelist = graph.get("equityGiftWhitelist", [])
        if monthly == 0 and one_time == 0 and not _truthy(draft.get("hasContract")):
            if scenario not in whitelist and draft.get("channelScope") != "内部验证":
                issues.append({
                    "ruleId": "R-C05",
                    "issueType": "高风险资费",
                    "issueLevel": "HIGH",
                    "field": "monthlyFee",
                    "message": "月费/一次性费均为0且无合约，非权益赠送白名单",
                    "evidence": ["monthlyFee=0", "oneTimeFee=0", "hasContract=0"],
                })

        # R-C07 异常优惠
        discount = _num(draft.get("discountPercent"), default=-1)
        if discount == 100 and _truthy(draft.get("repeatable")):
            issues.append({
                "ruleId": "R-C07",
                "issueType": "异常优惠漏洞",
                "issueLevel": "HIGH",
                "field": "discountPercent",
                "message": "优惠折扣100%且可重复订购，存在异常优惠漏洞",
                "evidence": ["discountPercent=100", "repeatable=true"],
            })

        has_high = any(i["issueLevel"] == "HIGH" for i in issues)
        required_ok = not any(i["ruleId"] == "R-C06" for i in issues)
        compliance_pass = (not has_high) and required_ok

        if compliance_pass:
            applied = ["R-C08"]
        else:
            applied = sorted({i["ruleId"] for i in issues})

        return {
            "success": True,
            "issues": issues,
            "compliancePass": compliance_pass,
            "appliedRules": applied,
            "canSubmit": compliance_pass,
        }

    @classmethod
    def chat_configure(cls, text: str, draft: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """智聊·对话配置主链路"""
        slots = cls.parse_slots_from_text(text)
        infer = cls.infer_fields(slots, draft)
        compliance = cls.check_compliance(infer["draft"])
        return {
            "success": True,
            "intent": "create_offering_config",
            "slots": slots,
            "draft": infer["draft"],
            "inferredFields": infer["inferredFields"],
            "recommendedTemplates": infer["recommendedTemplates"],
            "issues": compliance["issues"],
            "compliancePass": compliance["compliancePass"],
            "appliedRules": sorted(set(infer["appliedRules"] + compliance["appliedRules"])),
            "canSubmit": compliance["canSubmit"],
        }

    @classmethod
    def batch_from_document(cls, document_text: str = "", packages: Optional[List[Dict[str, Any]]] = None) -> Dict[str, Any]:
        """智读·批量生成：R-D01~D05"""
        graph = _load_graph()
        if not packages:
            packages = cls._default_campus_packages()
            if document_text:
                packages = cls._extract_packages_mock(document_text) or packages

        items: List[Dict[str, Any]] = []
        for idx, pkg in enumerate(packages):
            slots = deepcopy(pkg)
            if _empty(slots.get("bizScenario")):
                slots["bizScenario"] = "校园体验"
            infer = cls.infer_fields(slots)
            compliance = cls.check_compliance(infer["draft"])
            status = "通过" if compliance["compliancePass"] else "待修正"
            items.append({
                "index": idx + 1,
                "sourceExcerpt": pkg.get("sourceExcerpt", ""),
                "draft": infer["draft"],
                "inferredFields": infer["inferredFields"],
                "issues": compliance["issues"],
                "compliancePass": compliance["compliancePass"],
                "status": status,
                "appliedRules": sorted(set(infer["appliedRules"] + compliance["appliedRules"] + ["R-D01", "R-D02", "R-D04"])),
            })

        passed = [i for i in items if i["compliancePass"]]
        return {
            "success": True,
            "total": len(items),
            "passedCount": len(passed),
            "pendingCount": len(items) - len(passed),
            "items": items,
            "appliedRules": ["R-D01", "R-D02", "R-D03", "R-D04", "R-D05"],
            "confirmableDrafts": [
                {"index": i["index"], "offeringName": i["draft"].get("offeringName")}
                for i in passed
            ],
            "scenario": graph.get("bizScenarios", {}).get("校园体验", {}).get("scenarioId"),
        }

    @classmethod
    def analyze_root_cause(cls, offering_id: str = "OF-HF-128") -> Dict[str, Any]:
        """运营 MVP-1：指标异动根因分析 R-A01~A05"""
        from datetime import datetime, timezone

        graph = _load_graph()
        node = graph.get("opsGraph", {}).get(offering_id)
        offering = next((o for o in graph.get("shelfOfferings", []) if o["offeringId"] == offering_id), None)
        if not node or not offering:
            return {"success": False, "message": f"未找到商品图谱节点 {offering_id}"}

        anomalies = []
        for m in node.get("metrics", []):
            delta = m.get("metricDelta")
            if delta is not None and delta <= -0.10:
                anomalies.append({
                    "metricCode": m["metricCode"],
                    "metricValue": m.get("metricValue"),
                    "metricDelta": delta,
                    "ruleId": "R-A01",
                    "anomalyFlag": True,
                    "message": f"{m['metricCode']}环比 {delta * 100:.0f}%",
                })
            elif m.get("anomaly"):
                anomalies.append({
                    "metricCode": m["metricCode"],
                    "metricValue": m.get("metricValue"),
                    "metricDeltaPp": m.get("metricDeltaPp"),
                    "ruleId": "R-A01",
                    "anomalyFlag": True,
                    "message": f"{m['metricCode']}异动 {m.get('metricDeltaPp')}pp",
                })

        candidates: List[Dict[str, Any]] = []
        triples: List[Dict[str, Any]] = []

        for ch in node.get("channels", []):
            if ch.get("orderDelta", 0) <= -0.20 and ch.get("contribRatio", 0) >= 0.30:
                weight = ch.get("weightHint", ch["contribRatio"])
                candidates.append({
                    "type": "Channel",
                    "id": ch["channelId"],
                    "name": ch["name"],
                    "score": weight,
                    "weight": weight,
                    "ruleId": "R-A02",
                    "evidence": [
                        f"订购量变化 {ch['orderDelta'] * 100:.0f}%",
                        f"渠道贡献占比 {ch['contribRatio'] * 100:.0f}%",
                    ],
                    "path": [
                        f"{offering_id}-hasMetric->累计收入",
                        f"{offering_id}-soldOn->{ch['channelId']}",
                        f"Metric-relatedToChannel->{ch['channelId']}",
                    ],
                    "drill": {
                        "orderDelta": ch["orderDelta"],
                        "contribRatio": ch["contribRatio"],
                        "trend": ch.get("trend") or [
                            {"label": "T-2", "value": 100},
                            {"label": "T-1", "value": 82},
                            {"label": "T0", "value": round(100 * (1 + ch["orderDelta"]))},
                        ],
                    },
                })
                triples.extend([
                    {"s": offering_id, "p": "soldOn", "o": ch["channelId"]},
                    {"s": ch["channelId"], "p": "orderDelta", "o": ch["orderDelta"]},
                    {"s": ch["channelId"], "p": "contribRatio", "o": ch["contribRatio"]},
                ])

        for pr in node.get("promotions", []):
            if pr.get("daysToExpire", 999) <= 7 and pr.get("drivenOrderRatio", 0) >= 0.25:
                weight = pr.get("weightHint", pr["drivenOrderRatio"])
                candidates.append({
                    "type": "Promotion",
                    "id": pr["promoId"],
                    "name": pr["name"],
                    "score": weight,
                    "weight": weight,
                    "ruleId": "R-A03",
                    "evidence": [
                        f"{pr['daysToExpire']} 日后到期",
                        f"历史带动订购占比 {pr['drivenOrderRatio'] * 100:.0f}%",
                    ],
                    "path": [
                        f"{offering_id}-participatesIn->{pr['promoId']}",
                        f"{pr['promoId']}-daysToExpire->{pr['daysToExpire']}",
                    ],
                })
                triples.extend([
                    {"s": offering_id, "p": "participatesIn", "o": pr["promoId"]},
                    {"s": pr["promoId"], "p": "daysToExpire", "o": pr["daysToExpire"]},
                    {"s": pr["promoId"], "p": "drivenOrderRatio", "o": pr["drivenOrderRatio"]},
                ])

        for cp in node.get("competitors", []):
            if cp.get("priceGapRatio", 0) >= 0.15 and cp.get("penetrationDeltaPp", 0) > 0:
                weight = cp.get("weightHint", round(cp["priceGapRatio"], 2))
                candidates.append({
                    "type": "Competitor",
                    "id": cp["competitorId"],
                    "name": cp["name"],
                    "score": weight,
                    "weight": weight,
                    "ruleId": "R-A04",
                    "evidence": [
                        f"月费低 {cp.get('priceGap')} 元（约 {cp['priceGapRatio'] * 100:.1f}%）",
                        f"本地渗透率 +{cp['penetrationDeltaPp']}pp",
                    ],
                    "path": [
                        f"{offering_id}-competesWith->{cp['competitorId']}",
                        f"{cp['competitorId']}-priceGapRatio->{cp['priceGapRatio']}",
                    ],
                })
                triples.extend([
                    {"s": offering_id, "p": "competesWith", "o": cp["competitorId"]},
                    {"s": cp["competitorId"], "p": "priceGap", "o": cp.get("priceGap")},
                    {"s": cp["competitorId"], "p": "penetrationDeltaPp", "o": cp["penetrationDeltaPp"]},
                ])

        for ub in node.get("behaviors", []):
            weight = ub.get("weightHint", 0.08)
            candidates.append({
                "type": "UserBehavior",
                "id": ub["behaviorId"],
                "name": ub["name"],
                "score": weight,
                "weight": weight,
                "ruleId": "R-A05",
                "evidence": [ub["name"], "行为佐证"],
                "path": [f"{offering_id}-influencedBy->{ub['behaviorId']}"],
            })

        # R-A05：按影响权重排序，输出 Top3
        candidates.sort(key=lambda x: x["score"], reverse=True)
        top3 = candidates[:3]

        suggestions_map = graph.get("actionSuggestions", {})
        action_list = []
        for c in top3:
            action_list.extend(suggestions_map.get(c["type"], []))

        paths = [
            {
                "rank": i + 1,
                "rootCauseType": c["type"],
                "name": c["name"],
                "weight": c["weight"],
                "ruleId": c["ruleId"],
                "evidence": c["evidence"],
                "path": c.get("path", []),
                "drill": c.get("drill"),
                "isPrimary": i == 0,
            }
            for i, c in enumerate(top3)
        ]

        work_order = {
            "title": f"{offering['offeringName']}产品优化工单草稿",
            "offeringId": offering_id,
            "anomalySummary": anomalies[0]["message"] if anomalies else "指标异动",
            "actions": list(dict.fromkeys(action_list)),
            "rootCauses": [{"type": p["rootCauseType"], "name": p["name"], "ruleId": p["ruleId"]} for p in paths],
            "status": "draft",
            "source": "ontology_rules",
        }

        report_evidence = {
            "intent": "root_cause_analysis",
            "offeringId": offering_id,
            "offeringName": offering["offeringName"],
            "anomaly": {
                "metric": anomalies[0]["metricCode"] if anomalies else "累计收入",
                "delta": anomalies[0].get("metricDelta", -0.18) if anomalies else -0.18,
            },
            "rootCauses": [
                {"type": p["rootCauseType"], "name": p["name"], "score": p["weight"], "rule": p["ruleId"]}
                for p in paths
            ],
            "snapshotAt": datetime.now(timezone.utc).isoformat(),
        }

        market = node.get("market") or {}
        return {
            "success": True,
            "offeringId": offering_id,
            "offeringName": offering["offeringName"],
            "anomalies": anomalies,
            "candidates": candidates,
            "paths": paths,
            "actionList": list(dict.fromkeys(action_list)),
            "workOrder": work_order,
            "evidenceTriples": triples,
            "reportEvidence": report_evidence,
            "market": market,
            "graphScope": {
                "center": offering_id,
                "nodes": ["Metric", "Channel", "Promotion", "Competitor", "UserBehavior", "MarketScope"],
            },
            "appliedRules": sorted(
                {a["ruleId"] for a in anomalies} | {c["ruleId"] for c in candidates[:3]} | {"R-A05"}
            ),
            "snapshotAt": report_evidence["snapshotAt"],
        }

    @classmethod
    def audit_risks(cls, offering_ids: Optional[List[str]] = None) -> Dict[str, Any]:
        """运营 MVP-2：高风险稽核与优胜劣汰 R-B01~B05"""
        from datetime import datetime, timezone

        graph = _load_graph()
        rules = _risk_rules()
        offerings = graph.get("shelfOfferings", [])
        scanned_count = len(offerings)
        if offering_ids:
            offerings = [o for o in offerings if o["offeringId"] in offering_ids]

        whitelist = set(graph.get("equityGiftWhitelist", []))
        risk_actions = graph.get("riskActions", {})
        zero_shelf_days = int(rules.get("zeroSalesShelfDays", 180))
        review_days = int(rules.get("highRiskReviewDays", 30))
        low_pct = float(rules.get("lowRevenuePercentile", 0.05))

        all_revenues = sorted(o.get("revenue30d", 0) for o in graph.get("shelfOfferings", []))
        cutoff_idx = max(0, int(len(all_revenues) * low_pct) - 1) if all_revenues else 0
        low_threshold = all_revenues[cutoff_idx] if all_revenues else 0

        results = []
        for o in offerings:
            risks = []
            risk_level = "LOW"
            actions = []
            evidence_triples = []
            suggest_delist = False

            monthly = _num(o.get("monthlyFee"))
            one_time = _num(o.get("oneTimeFee"))
            wl_tag = o.get("whitelistTag") or ""
            in_whitelist = wl_tag in whitelist or any(t in (o.get("offeringName") or "") for t in whitelist)

            # R-B01 / R-B02：零元资费（白名单不误报）
            if monthly == 0 and one_time == 0 and not in_whitelist:
                risks.append({
                    "ruleId": "R-B01",
                    "feature": "零元资费",
                    "message": "月费与一次性费均为0且非权益赠送白名单",
                })
                evidence_triples.extend([
                    {"s": o["offeringId"], "p": "hasPricePlan", "o": f"PP-{o['offeringId']}"},
                    {"s": f"PP-{o['offeringId']}", "p": "monthlyFee", "o": 0},
                    {"s": f"PP-{o['offeringId']}", "p": "oneTimeFee", "o": 0},
                ])
                if o.get("state") == "上架" and not o.get("hasContract"):
                    risks.append({
                        "ruleId": "R-B02",
                        "feature": "零元无合约在架",
                        "message": "零元资费已上架且无合约约束",
                    })
                    evidence_triples.append({"s": o["offeringId"], "p": "hasContract", "o": False})
                    risk_level = "HIGH"
                    act = risk_actions.get("零元资费", {})
                    actions.append(act.get("defaultAction", "建议立即下架或转验证渠道"))
                    # 零元高危走「立即下架/转验证」处置，不计入优胜劣汰「建议下架」池

            # 异常全额赠送可重复订购
            if (
                _num(o.get("discountPercent")) >= 100
                and _truthy(o.get("repeatable"))
                and _empty(o.get("targetCustomerGroup"))
            ):
                risks.append({
                    "ruleId": "R-B01",
                    "feature": "异常全额赠送",
                    "message": "折扣100% + 可重复订购 + 无目标客户群",
                })
                risk_level = "HIGH"
                act = risk_actions.get("异常全额赠送", {})
                actions.append(act.get("defaultAction", "限售 + 复核优惠规则"))

            # R-B03：长期零销（优胜劣汰主路径）
            if o.get("salesCnt30d", 0) == 0 and o.get("shelfDays", 0) > zero_shelf_days:
                risks.append({
                    "ruleId": "R-B03",
                    "feature": "长期零销",
                    "message": f"近30日销量0且在架{o.get('shelfDays')}天（阈值>{zero_shelf_days}）",
                })
                if risk_level != "HIGH":
                    risk_level = "MEDIUM"
                act = risk_actions.get("长期零销", {})
                actions.append(act.get("defaultAction", "建议下架/归档"))
                suggest_delist = True
                evidence_triples.extend([
                    {"s": o["offeringId"], "p": "salesCnt30d", "o": 0},
                    {"s": o["offeringId"], "p": "shelfDays", "o": o.get("shelfDays")},
                ])

            # R-B04：收入后 5% 且无战略标签（仅低效/零销类，避免高危重复计入建议下架）
            if (
                o.get("category") in {"low_eff", "threshold_demo"}
                and o.get("revenue30d", 0) <= low_threshold
                and not o.get("strategicTag")
            ):
                risks.append({
                    "ruleId": "R-B04",
                    "feature": "低效产商品",
                    "message": "近90日收入贡献排名后5%且无战略标签",
                })
                if risk_level == "LOW":
                    risk_level = "MEDIUM"
                act = risk_actions.get("低效产商品", {})
                actions.append(act.get("defaultAction", "纳入优胜劣汰池"))
                suggest_delist = True

            # R-B05：高风险未复核升级
            urgent = False
            if risk_level == "HIGH" and o.get("shelfDays", 0) > review_days:
                risks.append({
                    "ruleId": "R-B05",
                    "feature": "预警升级",
                    "message": f"高风险且上架超过{review_days}天未复核",
                })
                actions.append("紧急复核")
                urgent = True

            if risks:
                score = 92 if risk_level == "HIGH" else (70 if risk_level == "MEDIUM" else 40)
                if urgent:
                    score = min(99, score + 5)
                results.append({
                    "offeringId": o["offeringId"],
                    "offeringName": o["offeringName"],
                    "state": o.get("state"),
                    "monthlyFee": o.get("monthlyFee"),
                    "oneTimeFee": o.get("oneTimeFee"),
                    "shelfDays": o.get("shelfDays"),
                    "salesCnt30d": o.get("salesCnt30d"),
                    "revenue30d": o.get("revenue30d"),
                    "hasContract": o.get("hasContract"),
                    "strategicTag": o.get("strategicTag"),
                    "riskLevel": risk_level,
                    "riskScore": score,
                    "urgent": urgent,
                    "suggestDelist": suggest_delist,
                    "risks": risks,
                    "actions": list(dict.fromkeys(actions)),
                    "evidenceTriples": evidence_triples,
                    "disposition": {
                        "defaultAction": (actions[0] if actions else "关注"),
                        "needConfirm": risk_level == "HIGH",
                    },
                })

        results.sort(key=lambda x: x["riskScore"], reverse=True)
        high_count = sum(1 for r in results if r["riskLevel"] == "HIGH")
        medium_count = sum(1 for r in results if r["riskLevel"] == "MEDIUM")
        suggest_delist_count = sum(1 for r in results if r.get("suggestDelist"))

        return {
            "success": True,
            "total": len(results),
            "scannedCount": scanned_count,
            "highCount": high_count,
            "mediumCount": medium_count,
            "suggestDelistCount": suggest_delist_count,
            "items": results,
            "appliedRules": ["R-B01", "R-B02", "R-B03", "R-B04", "R-B05"],
            "ruleVersion": rules.get("ruleVersion", "RiskRules-v1.2"),
            "riskRules": rules,
            "coverageCompare": {
                "manualSampleRate": 0.05,
                "manualHitEstimate": max(1, int(scanned_count * 0.05 * 0.3)),
                "ruleFullCoverage": 1.0,
                "ruleHitCount": len(results),
            },
            "auditedAt": datetime.now(timezone.utc).isoformat(),
        }

    @classmethod
    def _default_campus_packages(cls) -> List[Dict[str, Any]]:
        return [
            {
                "offeringName": "校园青春59",
                "monthlyFee": 59,
                "includeData": "20GB",
                "includeVoice": "200分钟",
                "targetUser": "校园",
                "channelScope": "电渠+厅店",
                "bizScenario": "校园体验",
                "offeringType": "main_pkg",
                "hasContract": "1",
                "contractMonths": 12,
                "sourceExcerpt": "套餐A：校园青春59元；含20GB+200分钟；目标校园；电渠+厅店",
            },
            {
                "offeringName": "校园体验0元流量包",
                "monthlyFee": 0,
                "includeData": "5GB",
                "targetUser": "校园",
                "channelScope": "全渠道",
                "bizScenario": "校园体验",
                "offeringType": "addon",
                "hasContract": "0",
                "repeatable": "true",
                "discountPercent": 100,
                "sourceExcerpt": "套餐B：校园体验0元流量包；无合约；可重复订购",
            },
            {
                "offeringName": "校园融合加装包",
                "targetUser": "校园",
                "channelScope": "电渠+厅店",
                "bizScenario": "校园体验",
                "offeringType": "addon",
                "dependOn": "",
                "sourceExcerpt": "套餐C：校园融合加装包；依赖宽带；未写月费",
            },
        ]

    @classmethod
    def _extract_packages_mock(cls, document_text: str) -> List[Dict[str, Any]]:
        """简单按段落拆分；无结构化段落时回退默认三套餐"""
        if "校园青春" in document_text or "套餐A" in document_text or "0元" in document_text:
            return cls._default_campus_packages()
        return []
