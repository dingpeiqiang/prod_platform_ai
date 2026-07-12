"""
家庭宽带配置演示数据 - 场景三:智聊・对话式配置
包含家庭宽带本体定义和初始配置模板
"""

HOME_BROADBAND_ONTOLOGY = {
    "ontology_code": "home_broadband",
    "ontology_name": "家庭宽带套餐",
    "description": "家庭宽带套餐配置本体,定义产品、资费、约束、优惠等实体及字段约束",
    "entities": [
        {
            "entity_code": "product",
            "entity_name": "产品信息",
            "fields": [
                {"code": "product_name", "name": "产品名称", "type": "string", "required": True, "max_length": 50},
                {"code": "bandwidth", "name": "带宽", "type": "enum", "required": True, "options": ["50M", "100M", "200M", "300M", "500M", "1G"]},
                {"code": "product_type", "name": "产品类型", "type": "enum", "required": True, "options": ["单宽带", "宽带+手机", "宽带+电视", "全屋光WiFi"]}
            ]
        },
        {
            "entity_code": "tariff",
            "entity_name": "资费规则",
            "fields": [
                {"code": "monthly_fee", "name": "月费", "type": "number", "required": True, "min": 0, "max": 9999},
                {"code": "first_month_discount", "name": "首月优惠", "type": "enum", "required": False, "options": ["无", "半价", "免费"]},
                {"code": "contract_period", "name": "合约期", "type": "enum", "required": True, "options": ["无", "12个月", "24个月", "36个月"]},
                {"code": "deposit", "name": "押金", "type": "number", "required": False, "min": 0}
            ]
        },
        {
            "entity_code": "sub_card",
            "entity_name": "副卡配置",
            "fields": [
                {"code": "sub_card_count", "name": "副卡数量", "type": "number", "required": False, "min": 0, "max": 5},
                {"code": "sub_card_monthly_fee", "name": "副卡月功能费", "type": "number", "required": False, "min": 0, "max": 100},
                {"code": "sub_card_traffic", "name": "副卡流量", "type": "string", "required": False}
            ]
        },
        {
            "entity_code": "constraint",
            "entity_name": "受理约束",
            "fields": [
                {"code": "region_limit", "name": "地域限制", "type": "string", "required": False},
                {"code": "user_limit", "name": "用户限制", "type": "string", "required": False},
                {"code": "install_limit", "name": "安装限制", "type": "string", "required": False}
            ]
        }
    ],
    "validation_rules": [
        {
            "rule_code": "R001",
            "description": "首月半价优惠要求合约期至少12个月",
            "condition": "first_month_discount == '半价' AND contract_period == '无'",
            "severity": "error"
        },
        {
            "rule_code": "R002",
            "description": "副卡数量超过2张需收取月功能费",
            "condition": "sub_card_count > 2 AND sub_card_monthly_fee == 0",
            "severity": "warning"
        },
        {
            "rule_code": "R003",
            "description": "地域限制必须明确到市级或小区级",
            "condition": "region_limit != '' AND '市' not in region_limit AND '小区' not in region_limit",
            "severity": "error"
        },
        {
            "rule_code": "R004",
            "description": "100M以上带宽建议合约期12个月以上",
            "condition": "bandwidth in ['200M','300M','500M','1G'] AND contract_period == '无'",
            "severity": "warning"
        }
    ]
}

INITIAL_CANVAS_TEMPLATE = {
    "nodes": [
        {
            "node_id": "node_product",
            "node_type": "product",
            "label": "产品信息",
            "fields": {
                "product_name": "",
                "bandwidth": "",
                "product_type": "单宽带"
            },
            "position": {"x": 300, "y": 50}
        },
        {
            "node_id": "node_tariff",
            "node_type": "tariff",
            "label": "资费规则",
            "fields": {
                "monthly_fee": 0,
                "first_month_discount": "无",
                "contract_period": "无",
                "deposit": 0
            },
            "position": {"x": 150, "y": 250}
        },
        {
            "node_id": "node_sub_card",
            "node_type": "sub_card",
            "label": "副卡配置",
            "fields": {
                "sub_card_count": 0,
                "sub_card_monthly_fee": 0,
                "sub_card_traffic": ""
            },
            "position": {"x": 450, "y": 250}
        },
        {
            "node_id": "node_constraint",
            "node_type": "constraint",
            "label": "受理约束",
            "fields": {
                "region_limit": "",
                "user_limit": "",
                "install_limit": ""
            },
            "position": {"x": 300, "y": 450}
        }
    ],
    "edges": [
        {"source_id": "node_product", "target_id": "node_tariff", "relation": "contains"},
        {"source_id": "node_product", "target_id": "node_sub_card", "relation": "contains"},
        {"source_id": "node_product", "target_id": "node_constraint", "relation": "constrains"}
    ]
}
