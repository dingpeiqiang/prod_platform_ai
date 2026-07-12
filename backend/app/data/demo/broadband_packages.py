"""
高校宽带套餐演示数据 - 场景一:智查・一键复制
包含5-8条历史高校宽带套餐配置,90%相似,差异在优惠时长/流量包等字段
"""

DEMO_PACKAGES = [
    {
        "package_id": "PKG_EDU_001",
        "package_name": "高校基础宽带包",
        "scene_type": "校园宽带",
        "target_audience": "高校学生",
        "status": "在售",
        "bandwidth": "200M",
        "bind_phone": True,
        "monthly_fee": 59,
        "contract_period": 12,
        "discount_period": 6,
        "orientation_traffic": "30G",
        "general_traffic": "10G",
        "effective_rules": {
            "effective_type": "立即生效",
            "effective_delay": 0,
            "expiry_type": "合约到期自动失效"
        },
        "tariff_rules": [
            {"name": "基础月租", "amount": 59, "cycle": "月"},
            {"name": "首月优惠", "amount": 0, "cycle": "首月", "discount_type": "全额减免"},
            {"name": "合约期优惠", "amount": 10, "cycle": "月", "discount_type": "减免", "duration": 6}
        ],
        "constraints": [
            {"type": "target_audience", "value": "高校学生", "description": "仅限高校学生办理"},
            {"type": "region", "value": "校园内", "description": "仅限校园内安装"},
            {"type": "contract", "value": "12个月", "description": "需签订12个月合约"}
        ],
        "created_at": "2025-09-01T10:00:00",
        "created_by": "张经理"
    },
    {
        "package_id": "PKG_EDU_002",
        "package_name": "校园融合套餐2019",
        "scene_type": "校园宽带",
        "target_audience": "高校学生",
        "status": "在售",
        "bandwidth": "200M",
        "bind_phone": True,
        "monthly_fee": 59,
        "contract_period": 12,
        "discount_period": 3,
        "orientation_traffic": "20G",
        "general_traffic": "5G",
        "effective_rules": {
            "effective_type": "立即生效",
            "effective_delay": 0,
            "expiry_type": "合约到期自动失效"
        },
        "tariff_rules": [
            {"name": "基础月租", "amount": 59, "cycle": "月"},
            {"name": "首月优惠", "amount": 0, "cycle": "首月", "discount_type": "全额减免"},
            {"name": "合约期优惠", "amount": 10, "cycle": "月", "discount_type": "减免", "duration": 3}
        ],
        "constraints": [
            {"type": "target_audience", "value": "高校学生", "description": "仅限高校学生办理"},
            {"type": "region", "value": "校园内", "description": "仅限校园内安装"},
            {"type": "contract", "value": "12个月", "description": "需签订12个月合约"}
        ],
        "created_at": "2019-08-15T14:30:00",
        "created_by": "李经理"
    },
    {
        "package_id": "PKG_EDU_003",
        "package_name": "校园学霸宽带包",
        "scene_type": "校园宽带",
        "target_audience": "高校学生",
        "status": "在售",
        "bandwidth": "300M",
        "bind_phone": True,
        "monthly_fee": 69,
        "contract_period": 24,
        "discount_period": 12,
        "orientation_traffic": "40G",
        "general_traffic": "15G",
        "effective_rules": {
            "effective_type": "立即生效",
            "effective_delay": 0,
            "expiry_type": "合约到期自动失效"
        },
        "tariff_rules": [
            {"name": "基础月租", "amount": 69, "cycle": "月"},
            {"name": "首月优惠", "amount": 0, "cycle": "首月", "discount_type": "全额减免"},
            {"name": "合约期优惠", "amount": 15, "cycle": "月", "discount_type": "减免", "duration": 12}
        ],
        "constraints": [
            {"type": "target_audience", "value": "高校学生", "description": "仅限高校学生办理"},
            {"type": "region", "value": "校园内", "description": "仅限校园内安装"},
            {"type": "contract", "value": "24个月", "description": "需签订24个月合约"}
        ],
        "created_at": "2024-09-01T09:00:00",
        "created_by": "王经理"
    },
    {
        "package_id": "PKG_EDU_004",
        "package_name": "高校畅享宽带包",
        "scene_type": "校园宽带",
        "target_audience": "高校学生",
        "status": "在售",
        "bandwidth": "200M",
        "bind_phone": True,
        "monthly_fee": 49,
        "contract_period": 12,
        "discount_period": 6,
        "orientation_traffic": "30G",
        "general_traffic": "10G",
        "effective_rules": {
            "effective_type": "立即生效",
            "effective_delay": 0,
            "expiry_type": "合约到期自动失效"
        },
        "tariff_rules": [
            {"name": "基础月租", "amount": 49, "cycle": "月"},
            {"name": "首月优惠", "amount": 0, "cycle": "首月", "discount_type": "全额减免"},
            {"name": "合约期优惠", "amount": 10, "cycle": "月", "discount_type": "减免", "duration": 6}
        ],
        "constraints": [
            {"type": "target_audience", "value": "高校学生", "description": "仅限高校学生办理"},
            {"type": "region", "value": "校园内", "description": "仅限校园内安装"},
            {"type": "contract", "value": "12个月", "description": "需签订12个月合约"}
        ],
        "created_at": "2024-03-01T11:00:00",
        "created_by": "赵经理"
    },
    {
        "package_id": "PKG_EDU_005",
        "package_name": "校园宽带+手机融合套餐",
        "scene_type": "校园宽带",
        "target_audience": "高校学生",
        "status": "在售",
        "bandwidth": "200M",
        "bind_phone": True,
        "monthly_fee": 59,
        "contract_period": 12,
        "discount_period": 6,
        "orientation_traffic": "30G",
        "general_traffic": "10G",
        "effective_rules": {
            "effective_type": "立即生效",
            "effective_delay": 0,
            "expiry_type": "合约到期自动失效"
        },
        "tariff_rules": [
            {"name": "基础月租", "amount": 59, "cycle": "月"},
            {"name": "首月优惠", "amount": 0, "cycle": "首月", "discount_type": "全额减免"},
            {"name": "合约期优惠", "amount": 10, "cycle": "月", "discount_type": "减免", "duration": 6}
        ],
        "constraints": [
            {"type": "target_audience", "value": "高校学生", "description": "仅限高校学生办理"},
            {"type": "region", "value": "校园内", "description": "仅限校园内安装"},
            {"type": "contract", "value": "12个月", "description": "需签订12个月合约"}
        ],
        "created_at": "2023-09-01T10:00:00",
        "created_by": "张经理"
    },
    {
        "package_id": "PKG_EDU_006",
        "package_name": "教职工专属宽带包",
        "scene_type": "校园宽带",
        "target_audience": "高校教职工",
        "status": "在售",
        "bandwidth": "500M",
        "bind_phone": False,
        "monthly_fee": 89,
        "contract_period": 24,
        "discount_period": 6,
        "orientation_traffic": "50G",
        "general_traffic": "20G",
        "effective_rules": {
            "effective_type": "立即生效",
            "effective_delay": 0,
            "expiry_type": "合约到期自动失效"
        },
        "tariff_rules": [
            {"name": "基础月租", "amount": 89, "cycle": "月"},
            {"name": "首月优惠", "amount": 0, "cycle": "首月", "discount_type": "全额减免"},
            {"name": "合约期优惠", "amount": 20, "cycle": "月", "discount_type": "减免", "duration": 6}
        ],
        "constraints": [
            {"type": "target_audience", "value": "高校教职工", "description": "仅限高校教职工办理"},
            {"type": "region", "value": "校园内", "description": "仅限校园内安装"},
            {"type": "contract", "value": "24个月", "description": "需签订24个月合约"}
        ],
        "created_at": "2024-09-01T10:00:00",
        "created_by": "李经理"
    }
]


def get_all_packages():
    return DEMO_PACKAGES


def get_package_by_id(package_id: str):
    for pkg in DEMO_PACKAGES:
        if pkg["package_id"] == package_id:
            return pkg
    return None
