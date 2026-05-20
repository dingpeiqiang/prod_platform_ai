"""
资费备案业务规则加载器
从YAML配置文件中动态加载业务规则
"""
import yaml
import os
import logging
from typing import Dict, Any, Optional

logger = logging.getLogger("tariff_rules_loader")

# 单例模式
_rules_cache = None


def load_tariff_rules() -> Dict[str, Any]:
    """加载资费备案业务规则（单例模式）"""
    global _rules_cache
    if _rules_cache is not None:
        return _rules_cache
    
    # 获取配置文件路径（从 backend/config/business_rules 读取）
    config_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../config/business_rules"))
    rules_file = os.path.join(config_dir, "tariff_rules.yaml")
    
    try:
        with open(rules_file, 'r', encoding='utf-8') as f:
            _rules_cache = yaml.safe_load(f)
        logger.info(f"[TariffRules] 成功加载业务规则: {rules_file}")
        return _rules_cache
    except FileNotFoundError:
        logger.error(f"[TariffRules] 配置文件不存在: {rules_file}")
        return _get_default_rules()
    except Exception as e:
        logger.error(f"[TariffRules] 加载配置失败: {e}")
        return _get_default_rules()


def _get_default_rules() -> Dict[str, Any]:
    """获取默认规则（配置文件加载失败时使用）"""
    return {
        "default_values": {
            "action_type": "A",
            "reporter": "JT1",
            "type1": "1",
            "type2": "1",
            "tariff_attr": "1",
            "applicable_area": "000",
            "valid_period": "长期有效",
            "channel": "线上渠道",
            "duration": "无",
            "unsubscribe": "线上办理",
            "responsibility": "无",
            "fees_unit": "元/月"
        },
        "enums": {},
        "validation_rules": {},
        "field_mapping": {},
        "extraction_rules": {},
        "tariff_code_patterns": [
            "^[A-Za-z][A-Za-z0-9]+$",
            "^P\\d{6,}$",
            "^TC\\d{6,}$",
            "^\\d{7,}$"
        ]
    }


def get_default_value(field_code: str) -> Optional[Any]:
    """获取字段的默认值"""
    rules = load_tariff_rules()
    return rules.get("default_values", {}).get(field_code)


def get_enum_values(enum_name: str) -> list:
    """获取枚举值列表"""
    rules = load_tariff_rules()
    return rules.get("enums", {}).get(enum_name, [])


def get_validation_rule(field_code: str) -> Optional[Dict[str, Any]]:
    """获取字段的校验规则"""
    rules = load_tariff_rules()
    return rules.get("validation_rules", {}).get(field_code)


def get_field_mapping(target_field: str) -> list:
    """获取字段映射关系"""
    rules = load_tariff_rules()
    return rules.get("field_mapping", {}).get(target_field, [])


def get_extraction_rule(field_code: str) -> Optional[Dict[str, Any]]:
    """获取字段的提取规则"""
    rules = load_tariff_rules()
    return rules.get("extraction_rules", {}).get(field_code)


def get_tariff_code_patterns() -> list:
    """获取套餐编码提取模式"""
    rules = load_tariff_rules()
    return rules.get("tariff_code_patterns", [
        "^[A-Za-z][A-Za-z0-9]+$",
        "^P\\d{6,}$",
        "^TC\\d{6,}$",
        "^\\d{7,}$"
    ])


def get_all_fields() -> list:
    """获取所有配置的字段列表"""
    rules = load_tariff_rules()
    fields = set()
    
    # 从默认值中获取字段
    fields.update(rules.get("default_values", {}).keys())
    
    # 从校验规则中获取字段
    fields.update(rules.get("validation_rules", {}).keys())
    
    # 从字段映射中获取字段
    fields.update(rules.get("field_mapping", {}).keys())
    
    return sorted(list(fields))
