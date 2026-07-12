"""
演示数据服务 - 产品智能配置助手三大场景数据管理
提供套餐查询、语义检索、配置克隆、差异对比、文档读取等能力
"""
import copy
import uuid
import re
from typing import Dict, Any, List, Optional, Tuple
from datetime import datetime
from pathlib import Path

from app.core.logger import get_logger
from app.data.demo.broadband_packages import DEMO_PACKAGES, get_package_by_id
from app.data.demo.enterprise_lines import DEMO_ENTERPRISE_PACKAGES
from app.data.demo.home_broadband import HOME_BROADBAND_ONTOLOGY, INITIAL_CANVAS_TEMPLATE

logger = get_logger(__name__)

DEMO_DATA_DIR = Path(__file__).parent.parent / "data" / "demo"


class DemoDataService:
    """演示数据管理服务"""

    def __init__(self):
        self._clone_store: Dict[str, Dict] = {}

    def get_packages_by_scene(self, scene_type: str) -> List[Dict]:
        """按场景类型获取套餐列表"""
        if scene_type == "校园宽带":
            return [self._simplify_package(p) for p in DEMO_PACKAGES]
        elif scene_type == "政企专线":
            return [self._simplify_package(p) for p in DEMO_ENTERPRISE_PACKAGES]
        return []

    def search_packages_semantic(self, query: str, top_k: int = 5) -> List[Dict]:
        """语义检索历史配置(基于关键词匹配的MVP实现)"""
        query_lower = query.lower()
        scored_results = []

        all_packages = DEMO_PACKAGES + DEMO_ENTERPRISE_PACKAGES

        for pkg in all_packages:
            score = self._calculate_similarity(query_lower, pkg)
            if score > 0:
                scored_results.append({
                    "package": self._simplify_package(pkg),
                    "similarity": round(score * 100, 1),
                    "matched_fields": self._get_matched_fields(query_lower, pkg)
                })

        scored_results.sort(key=lambda x: x["similarity"], reverse=True)
        return scored_results[:top_k]

    def _calculate_similarity(self, query: str, package: Dict) -> float:
        """计算查询与套餐的相似度分数(0-1)"""
        keywords = self._extract_keywords(query)
        if not keywords:
            return 0.0

        package_text = self._package_to_text(package).lower()
        matched = 0
        total_weight = 0

        for kw, weight in keywords.items():
            total_weight += weight
            if kw in package_text:
                matched += weight

        return matched / total_weight if total_weight > 0 else 0.0

    def _extract_keywords(self, query: str) -> Dict[str, int]:
        """从查询中提取关键词及权重"""
        keywords = {}

        keyword_map = {
            "高校": 3, "学生": 3, "校园": 3, "大学": 3,
            "宽带": 2, "手机": 2, "融合": 2, "phone": 2,
            "200m": 2, "100m": 2, "300m": 2, "500m": 2, "1g": 2,
            "政企": 3, "专线": 3, "企业": 2,
            "家庭": 3, "副卡": 2, "半价": 2,
            "在售": 1, "历史": 1, "套餐": 1
        }

        for kw, weight in keyword_map.items():
            if kw in query:
                keywords[kw] = weight

        return keywords

    def _package_to_text(self, package: Dict) -> str:
        """将套餐转换为可搜索的文本"""
        parts = [
            package.get("package_name", ""),
            package.get("scene_type", ""),
            package.get("target_audience", ""),
            package.get("bandwidth", ""),
            package.get("status", ""),
            str(package.get("bind_phone", False)),
            str(package.get("monthly_fee", ""))
        ]
        for rule in package.get("tariff_rules", []):
            parts.append(str(rule.get("name", "")))
        for constraint in package.get("constraints", []):
            parts.append(str(constraint.get("value", "")))
        return " ".join(parts)

    def _get_matched_fields(self, query: str, package: Dict) -> List[str]:
        """获取匹配的字段列表"""
        matched = []
        field_map = {
            "target_audience": package.get("target_audience", ""),
            "bandwidth": package.get("bandwidth", ""),
            "scene_type": package.get("scene_type", ""),
            "bind_phone": "融合" if package.get("bind_phone") else ""
        }
        for field, value in field_map.items():
            if value and str(value).lower() in query:
                matched.append(field)
        return matched

    def _simplify_package(self, pkg: Dict) -> Dict:
        """简化套餐信息用于列表展示"""
        return {
            "package_id": pkg.get("package_id"),
            "package_name": pkg.get("package_name"),
            "scene_type": pkg.get("scene_type"),
            "target_audience": pkg.get("target_audience"),
            "status": pkg.get("status"),
            "bandwidth": pkg.get("bandwidth"),
            "bind_phone": pkg.get("bind_phone"),
            "monthly_fee": pkg.get("monthly_fee"),
            "contract_period": pkg.get("contract_period"),
            "discount_period": pkg.get("discount_period"),
            "orientation_traffic": pkg.get("orientation_traffic"),
            "created_at": pkg.get("created_at"),
            "created_by": pkg.get("created_by")
        }

    def get_package_detail(self, package_id: str) -> Optional[Dict]:
        """获取套餐完整详情"""
        if package_id in self._clone_store:
            return copy.deepcopy(self._clone_store[package_id])

        pkg = get_package_by_id(package_id)
        if not pkg:
            for epkg in DEMO_ENTERPRISE_PACKAGES:
                if epkg["package_id"] == package_id:
                    pkg = epkg
                    break

        if pkg:
            return copy.deepcopy(pkg)
        return None

    def clone_package(self, package_id: str, modifications: Optional[Dict] = None) -> Optional[Dict]:
        """克隆套餐配置,可附带修改字段"""
        source_pkg = self.get_package_detail(package_id)
        if not source_pkg:
            return None

        cloned = copy.deepcopy(source_pkg)
        new_id = f"PKG_CLONE_{uuid.uuid4().hex[:8].upper()}"
        cloned["package_id"] = new_id
        cloned["package_name"] = f"{source_pkg['package_name']}_副本"
        cloned["status"] = "草稿"
        cloned["created_at"] = datetime.now().isoformat()
        cloned["source_package_id"] = package_id

        if modifications:
            for key, value in modifications.items():
                if key in cloned:
                    cloned[key] = value
                elif key == "discount_period":
                    cloned["discount_period"] = value
                elif key == "orientation_traffic":
                    cloned["orientation_traffic"] = value
                elif key == "package_name":
                    cloned["package_name"] = value

        self._clone_store[new_id] = cloned
        logger.info(f"[DemoData] 克隆套餐: {package_id} -> {new_id}")
        return cloned

    def diff_configs(self, source_config: Dict, target_config: Dict) -> List[Dict]:
        """对比两个配置的差异,返回差异字段列表"""
        diff_fields = []
        compare_keys = [
            "package_name", "bandwidth", "monthly_fee", "contract_period",
            "discount_period", "orientation_traffic", "general_traffic",
            "target_audience", "bind_phone"
        ]

        for key in compare_keys:
            source_val = source_config.get(key)
            target_val = target_config.get(key)
            if source_val != target_val:
                diff_fields.append({
                    "field": key,
                    "field_label": self._get_field_label(key),
                    "source_value": source_val,
                    "target_value": target_val,
                    "category": self._get_field_category(key),
                    "editable": True
                })

        return diff_fields

    def _get_field_label(self, field: str) -> str:
        labels = {
            "package_name": "套餐名称",
            "bandwidth": "带宽",
            "monthly_fee": "月费",
            "contract_period": "合约期",
            "discount_period": "优惠时长",
            "orientation_traffic": "定向流量",
            "general_traffic": "通用流量",
            "target_audience": "目标用户",
            "bind_phone": "绑定手机号"
        }
        return labels.get(field, field)

    def _get_field_category(self, field: str) -> str:
        if field in ["discount_period", "orientation_traffic", "general_traffic"]:
            return "优惠"
        elif field in ["monthly_fee", "bandwidth"]:
            return "资费"
        elif field in ["contract_period"]:
            return "合约"
        elif field in ["package_name", "target_audience"]:
            return "基础"
        return "其他"

    def get_enterprise_proposal(self) -> str:
        """获取政企专线方案文档内容"""
        proposal_path = DEMO_DATA_DIR / "enterprise_proposal.txt"
        try:
            return proposal_path.read_text(encoding="utf-8")
        except FileNotFoundError:
            logger.warning(f"[DemoData] 方案文档不存在: {proposal_path}")
            return ""

    def get_home_broadband_ontology(self) -> Dict:
        """获取家庭宽带本体定义"""
        return copy.deepcopy(HOME_BROADBAND_ONTOLOGY)

    def get_initial_canvas(self) -> Dict:
        """获取初始画布模板"""
        return copy.deepcopy(INITIAL_CANVAS_TEMPLATE)

    def validate_config(self, config: Dict, ontology_code: str = "home_broadband") -> Dict:
        """校验配置是否符合本体规则"""
        if ontology_code != "home_broadband":
            return {"valid": True, "errors": [], "warnings": []}

        ontology = HOME_BROADBAND_ONTOLOGY
        errors = []
        warnings = []

        rules = ontology.get("validation_rules", [])
        fields = {}
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                fields[field["code"]] = field

        for node in config.get("nodes", []):
            node_type = node.get("node_type")
            node_fields = node.get("fields", {})

            if node_type == "product":
                bandwidth = node_fields.get("bandwidth", "")
                if not bandwidth and fields.get("bandwidth", {}).get("required"):
                    errors.append({"rule": "required", "field": "bandwidth", "message": "带宽为必填项"})

            if node_type == "tariff":
                first_month_discount = node_fields.get("first_month_discount", "无")
                contract_period = node_fields.get("contract_period", "无")
                if first_month_discount == "半价" and contract_period == "无":
                    errors.append({
                        "rule": "R001",
                        "field": "first_month_discount",
                        "message": "首月半价优惠要求合约期至少12个月"
                    })

                bandwidth = ""
                for n in config.get("nodes", []):
                    if n.get("node_type") == "product":
                        bandwidth = n.get("fields", {}).get("bandwidth", "")
                        break
                if bandwidth in ["200M", "300M", "500M", "1G"] and contract_period == "无":
                    warnings.append({
                        "rule": "R004",
                        "field": "contract_period",
                        "message": f"{bandwidth}带宽建议合约期12个月以上"
                    })

            if node_type == "sub_card":
                sub_card_count = node_fields.get("sub_card_count", 0)
                sub_card_monthly_fee = node_fields.get("sub_card_monthly_fee", 0)
                if sub_card_count > 2 and sub_card_monthly_fee == 0:
                    warnings.append({
                        "rule": "R002",
                        "field": "sub_card_monthly_fee",
                        "message": "副卡数量超过2张需收取月功能费"
                    })

            if node_type == "constraint":
                region_limit = node_fields.get("region_limit", "")
                if region_limit and "市" not in region_limit and "小区" not in region_limit:
                    errors.append({
                        "rule": "R003",
                        "field": "region_limit",
                        "message": "地域限制必须明确到市级或小区级"
                    })

        return {
            "valid": len(errors) == 0,
            "errors": errors,
            "warnings": warnings
        }


demo_data_service = DemoDataService()
