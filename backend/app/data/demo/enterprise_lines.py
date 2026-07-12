"""
政企专线套餐演示数据 - 场景二:智读・批量生成
包含5档不同档位的政企专线套餐配置
"""

DEMO_ENTERPRISE_PACKAGES = [
    {
        "package_id": "PKG_ENT_001",
        "package_name": "政企专线100M基础版",
        "scene_type": "政企专线",
        "target_audience": "中小微企业",
        "bandwidth": "100M",
        "monthly_fee": 199,
        "contract_period": 12,
        "acceptance_restrictions": "企业营业执照认证,同城安装",
        "promotional_activities": "首月免费,安装费减免",
        "sla_level": "标准级",
        "upload_bandwidth": "20M",
        "static_ip_count": 1,
        "status": "草稿"
    },
    {
        "package_id": "PKG_ENT_002",
        "package_name": "政企专线200M商务版",
        "scene_type": "政企专线",
        "target_audience": "成长型企业",
        "bandwidth": "200M",
        "monthly_fee": 299,
        "contract_period": 24,
        "acceptance_restrictions": "企业营业执照认证,同城安装,需现场勘测",
        "promotional_activities": "首月免费,赠送路由器,安装费减免",
        "sla_level": "商务级",
        "upload_bandwidth": "50M",
        "static_ip_count": 2,
        "status": "草稿"
    },
    {
        "package_id": "PKG_ENT_003",
        "package_name": "政企专线500M专业版",
        "scene_type": "政企专线",
        "target_audience": "中型企业",
        "bandwidth": "500M",
        "monthly_fee": 599,
        "contract_period": 24,
        "acceptance_restrictions": "企业营业执照认证,同城安装,需现场勘测,签订SLA协议",
        "promotional_activities": "首月免费,赠送路由器,安装费减免,7x24小时专属客服",
        "sla_level": "专业级",
        "upload_bandwidth": "100M",
        "static_ip_count": 5,
        "status": "草稿"
    },
    {
        "package_id": "PKG_ENT_004",
        "package_name": "政企专线1G企业版",
        "scene_type": "政企专线",
        "target_audience": "大型企业",
        "bandwidth": "1G",
        "monthly_fee": 999,
        "contract_period": 36,
        "acceptance_restrictions": "企业营业执照认证,同城安装,需现场勘测,签订SLA协议,专属客户经理对接",
        "promotional_activities": "首月免费,赠送路由器,安装费减免,7x24小时专属客服,季度网络巡检",
        "sla_level": "企业级",
        "upload_bandwidth": "200M",
        "static_ip_count": 10,
        "status": "草稿"
    },
    {
        "package_id": "PKG_ENT_005",
        "package_name": "政企专线10G旗舰版",
        "scene_type": "政企专线",
        "target_audience": "集团企业",
        "bandwidth": "10G",
        "monthly_fee": 2999,
        "contract_period": 36,
        "acceptance_restrictions": "企业营业执照认证,同城安装,需现场勘测,签订SLA协议,专属客户经理对接,定制化方案",
        "promotional_activities": "首月免费,赠送路由器,安装费减免,7x24小时专属客服,季度网络巡检,年度网络优化",
        "sla_level": "旗舰级",
        "upload_bandwidth": "1G",
        "static_ip_count": 20,
        "status": "草稿"
    }
]


def get_all_enterprise_packages():
    return DEMO_ENTERPRISE_PACKAGES
