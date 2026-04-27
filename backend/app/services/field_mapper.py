"""
历史推荐字段映射器 - 解决不同业务数据模型差异

核心问题：
1. 字段名不一致：applicant_name vs customer_name vs person_name
2. 嵌套结构：data.customer.name vs data.customer_name
3. 数组字段：data.items[0].price

解决方案：
1. 字段别名映射表：建立跨表单的语义关联
2. JSON路径提取：支持嵌套结构 data->'customer'->>'name'
3. 表单专属字段解析：根据 form_code 解析对应结构
"""

from typing import Dict, Any, List, Optional, Tuple, Callable
from dataclasses import dataclass, field
from datetime import datetime
from collections import defaultdict
import logging
import re

logger = logging.getLogger("field_mapper")


@dataclass
class FieldMapping:
    """字段映射定义"""
    form_code: str
    field_code: str
    json_path: str
    value_type: str = "string"
    aliases: List[str] = field(default_factory=list)


class FieldMapper:
    """
    字段映射器 - 适配不同业务数据模型

    功能：
    1. 表单专属路径解析：根据 form_code 找到正确的 JSON 路径
    2. 字段别名匹配：支持同义字段名映射
    3. 嵌套结构提取：支持 JSONB 嵌套路径提取
    4. 数组字段提取：支持 data.items[0].price 格式
    """

    def __init__(self):
        self._field_mappings: Dict[str, Dict[str, FieldMapping]] = {}
        self._alias_index: Dict[str, Dict[str, str]] = defaultdict(dict)
        self._load_default_mappings()

    def _load_default_mappings(self):
        """加载默认字段映射"""
        default_mappings = [
            FieldMapping("leave", "leave_type", "leave_type"),
            FieldMapping("leave", "leave_days", "leave_days"),
            FieldMapping("leave", "reason", "reason"),
            FieldMapping("leave", "start_date", "start_date"),
            FieldMapping("leave", "end_date", "end_date"),
            FieldMapping("leave", "applicant_name", "applicant.name"),
            FieldMapping("leave", "department", "applicant.dept"),

            FieldMapping("sales_order", "customer_name", "customer_name"),
            FieldMapping("sales_order", "customer_phone", "customer_phone"),
            FieldMapping("sales_order", "order_amount", "order_amount"),
            FieldMapping("sales_order", "order_date", "order_date"),
            FieldMapping("sales_order", "customer_name_nested", "customer.name"),
            FieldMapping("sales_order", "customer_phone_nested", "customer.phone"),

            FieldMapping("expense", "expense_type", "expense_type"),
            FieldMapping("expense", "amount", "amount"),
            FieldMapping("expense", "description", "description"),
            FieldMapping("expense", "receipt_ids", "receipt_ids"),
        ]

        for mapping in default_mappings:
            self.register_mapping(mapping)

        logger.info(f"[FieldMapper] 已加载 {len(default_mappings)} 个默认字段映射")

    def register_mapping(self, mapping: FieldMapping):
        """注册字段映射"""
        if mapping.form_code not in self._field_mappings:
            self._field_mappings[mapping.form_code] = {}

        self._field_mappings[mapping.form_code][mapping.field_code] = mapping

        for alias in mapping.aliases:
            self._alias_index[mapping.form_code][alias.lower()] = mapping.field_code

    def get_json_path(self, form_code: str, field_code: str) -> Optional[str]:
        """获取字段的 JSON 路径"""
        form_mappings = self._field_mappings.get(form_code, {})
        mapping = form_mappings.get(field_code)
        return mapping.json_path if mapping else None

    def extract_value(self, data: Dict[str, Any], json_path: str) -> Optional[Any]:
        """
        从 JSON 数据中提取值

        支持的路径格式：
        - "field_name" → data['field_name']
        - "parent.child" → data['parent']['child']
        - "items[0].name" → data['items'][0]['name']
        """
        if not json_path or not data:
            return None

        try:
            parts = self._parse_path(json_path)
            current = data

            for part in parts:
                if isinstance(part, int):
                    if isinstance(current, list) and len(current) > part:
                        current = current[part]
                    else:
                        return None
                elif isinstance(current, dict) and part in current:
                    current = current[part]
                else:
                    return None

            return current if not isinstance(current, (dict, list)) else None

        except Exception as e:
            logger.debug(f"[FieldMapper] 路径提取失败: {json_path}, {e}")
            return None

    def _parse_path(self, path: str) -> List[Union[str, int]]:
        """解析路径为parts列表"""
        parts = []
        for segment in path.split('.'):
            match = re.match(r'(\w+)\[(\d+)\]', segment)
            if match:
                parts.append(match.group(1))
                parts.append(int(match.group(2)))
            else:
                parts.append(segment)
        return parts

    def resolve_field(self, form_code: str, field_hint: str) -> Optional[Tuple[str, str]]:
        """
        根据字段提示解析为 (form_code, field_code)

        用于跨表单推荐时，根据语义找到对应字段
        """
        field_hint_lower = field_hint.lower()

        if form_code and form_code in self._alias_index:
            alias_map = self._alias_index[form_code]
            if field_hint_lower in alias_map:
                field_code = alias_map[field_hint_lower]
                return (form_code, field_code)

        for fc, alias_map in self._alias_index.items():
            if field_hint_lower in alias_map:
                return (fc, alias_map[field_hint_lower])

        return None


class UniversalFieldExtractor:
    """
    通用字段提取器

    自动适配不同表单的数据结构，提取目标字段值
    """

    def __init__(self, field_mapper: Optional[FieldMapper] = None):
        self.field_mapper = field_mapper or FieldMapper()
        self._extraction_strategies: Dict[str, Callable] = {
            "direct": self._extract_direct,
            "nested": self._extract_nested,
            "array_first": self._extract_array_first,
        }

    def extract(
        self,
        form_code: str,
        field_code: str,
        data: Dict[str, Any]
    ) -> Optional[Any]:
        """
        从表单数据中提取目标字段值

        提取策略优先级：
        1. 直接匹配：data[field_code]
        2. 路径匹配：根据 form_code 的映射提取
        3. 别名匹配：模糊查找同名字段
        4. 递归搜索：在嵌套结构中搜索
        """
        json_path = self.field_mapper.get_json_path(form_code, field_code)

        if json_path:
            value = self.field_mapper.extract_value(data, json_path)
            if value is not None:
                return value

        if field_code in data:
            return data[field_code]

        value = self._fuzzy_extract(field_code, data)
        if value is not None:
            return value

        return None

    def _extract_direct(self, data: Dict, field_code: str) -> Any:
        return data.get(field_code)

    def _extract_nested(self, data: Dict, field_code: str) -> Any:
        for key, value in data.items():
            if isinstance(value, dict):
                if field_code in value:
                    return value[field_code]
                result = self._extract_nested(value, field_code)
                if result is not None:
                    return result
        return None

    def _extract_array_first(self, data: Dict, field_code: str) -> Any:
        for key, value in data.items():
            if isinstance(value, list) and len(value) > 0:
                first_item = value[0]
                if isinstance(first_item, dict) and field_code in first_item:
                    return first_item[field_code]
        return None

    def _fuzzy_extract(self, field_code: str, data: Dict, max_depth: int = 3) -> Any:
        """模糊提取：在嵌套结构中查找同名字段"""
        if max_depth <= 0:
            return None

        field_code_lower = field_code.lower()

        for key, value in data.items():
            if key.lower() == field_code_lower and not isinstance(value, (dict, list)):
                return value

            if isinstance(value, dict):
                result = self._fuzzy_extract(field_code, value, max_depth - 1)
                if result is not None:
                    return result

            elif isinstance(value, list) and len(value) > 0:
                if isinstance(value[0], dict):
                    result = self._fuzzy_extract(field_code, value[0], max_depth - 1)
                    if result is not None:
                        return result

        return None

    def extract_batch(
        self,
        form_code: str,
        field_codes: List[str],
        data: Dict[str, Any]
    ) -> Dict[str, Any]:
        """批量提取多个字段"""
        result = {}
        for field_code in field_codes:
            value = self.extract(form_code, field_code, data)
            if value is not None:
                result[field_code] = value
        return result


class AdaptiveRecommendationEngine:
    """
    自适应推荐引擎 - 适配不同业务数据模型

    核心能力：
    1. 自动识别表单数据结构
    2. 根据 form_code 选择正确的字段解析策略
    3. 支持嵌套结构和数组字段
    4. 跨表单语义映射（可选）
    """

    def __init__(self, config: Optional[Dict[str, Any]] = None):
        self.config = config or {}
        self.field_mapper = FieldMapper()
        self.extractor = UniversalFieldExtractor(self.field_mapper)
        self.recommendation_config = config_loader.get_recommendation_config() if 'config_loader' in dir() else {}

    def extract_field_values(
        self,
        form_code: str,
        field_code: str,
        form_instances: List[Any]
    ) -> Dict[str, Dict[str, Any]]:
        """
        从表单实例列表中提取指定字段的值

        自动适配不同的数据模型，提取统一的字段值
        """
        value_stats = defaultdict(lambda: {
            'count': 0,
            'last_used': None,
            'user_count': 0
        })

        for instance in form_instances:
            data = instance.data if hasattr(instance, 'data') else instance.get('data', {})

            value = self.extractor.extract(form_code, field_code, data)
            if value is None:
                continue

            value_str = str(value).strip()
            if not value_str:
                continue

            stats = value_stats[value_str]
            stats['count'] += 1

            if hasattr(instance, 'submitted_at') and instance.submitted_at:
                if not stats['last_used'] or instance.submitted_at > stats['last_used']:
                    stats['last_used'] = instance.submitted_at

            if hasattr(instance, 'user_id') and instance.user_id:
                stats['user_id'] = instance.user_id

        return dict(value_stats)

    def register_custom_mapping(
        self,
        form_code: str,
        field_code: str,
        json_path: str,
        value_type: str = "string",
        aliases: Optional[List[str]] = None
    ):
        """注册自定义字段映射"""
        mapping = FieldMapping(
            form_code=form_code,
            field_code=field_code,
            json_path=json_path,
            value_type=value_type,
            aliases=aliases or []
        )
        self.field_mapper.register_mapping(mapping)

    def get_supported_forms(self) -> List[str]:
        """获取支持的表单类型列表"""
        return list(self.field_mapper._field_mappings.keys())


_field_mapper_instance: Optional[FieldMapper] = None
_extractor_instance: Optional[UniversalFieldExtractor] = None


def get_field_mapper() -> FieldMapper:
    global _field_mapper_instance
    if _field_mapper_instance is None:
        _field_mapper_instance = FieldMapper()
    return _field_mapper_instance


def get_universal_extractor() -> UniversalFieldExtractor:
    global _extractor_instance
    if _extractor_instance is None:
        _extractor_instance = UniversalFieldExtractor(get_field_mapper())
    return _extractor_instance
