# -*- coding: utf-8 -*-
"""为 ops_rules.json alertGrading 增加 metricAnomaly 阈值 + R-O suggestions。"""
import json, io, collections

P = 'backend-app/src/main/resources/ontology/ops_rules.json'
d = json.load(io.open(P, encoding='utf-8'), object_pairs_hook=collections.OrderedDict)
ag = d['alertGrading']
assert 'metricAnomaly' not in ag, 'already patched'

ag['metricAnomaly'] = collections.OrderedDict([
    ('ruleVersion', 'MetricAnomaly-v1.0'),
    ('note', '指标异常判定阈值（环比 delta 比例）；正值表示上升、负值表示下降；布尔 flag 由后端按此计算后断言入本体，SWRL/R-O 据此给建议'),
    ('metrics', collections.OrderedDict([
        ('revenue', collections.OrderedDict([('name', '收入'), ('unit', '元'), ('compare', 'mom'),
                                             ('ruleId', 'R-O01'), ('dropLte', -0.10), ('riseGte', 0.10)])),
        ('order_cnt', collections.OrderedDict([('name', '订单量'), ('unit', '笔'), ('compare', 'mom'),
                                               ('ruleId', 'R-O01'), ('dropLte', -0.10), ('riseGte', 0.10)])),
        ('new_users', collections.OrderedDict([('name', '新增用户'), ('unit', '户'), ('compare', 'mom'),
                                               ('ruleId', 'R-O01'), ('dropLte', -0.10), ('riseGte', 0.10)])),
        ('churn_users', collections.OrderedDict([('name', '流失用户'), ('unit', '户'), ('compare', 'mom'),
                                                 ('ruleId', 'R-O02'), ('dropLte', -0.05), ('riseGte', 0.05)])),
        ('active_users', collections.OrderedDict([('name', '活跃用户'), ('unit', '户'), ('compare', 'mom'),
                                                  ('ruleId', 'R-O05'), ('dropLte', -0.05), ('riseGte', 0.05)])),
        ('arpu', collections.OrderedDict([('name', 'ARPU'), ('unit', '元'), ('compare', 'mom'),
                                          ('ruleId', 'R-O04'), ('dropLte', -0.05), ('riseGte', 0.05)])),
        ('attach_rate', collections.OrderedDict([('name', '绑定率'), ('unit', '%'), ('compare', 'mom'),
                                                 ('ruleId', 'R-O03'), ('dropLte', -0.05), ('riseGte', 0.05)])),
        ('net_growth', collections.OrderedDict([('name', '净增用户'), ('unit', '户'), ('compare', 'mom'),
                                                ('ruleId', 'R-O01'), ('dropLte', -0.10), ('riseGte', 0.10)])),
    ])),
])

ag['suggestions'] = collections.OrderedDict([
    ('ruleVersion', 'SuggestionRules-v1.0'),
    ('rules', collections.OrderedDict([
        ('R-O01', collections.OrderedDict([
            ('name', '渠道促销建议'), ('enabled', True), ('engine', 'swrl'),
            ('condition', '收入/订单量/新增用户环比下降超阈'),
            ('advice', '建议加大渠道投放与限时促销，主推高转化渠道；联动门店/APP 做定向曝光与首月优惠')])),
        ('R-O02', collections.OrderedDict([
            ('name', '客户保留建议'), ('enabled', True), ('engine', 'swrl'),
            ('condition', '流失用户环比上升超阈'),
            ('advice', '建议启动存量保有：到期前 30 天外呼提醒、挽回权益包、满意度回访，抑制离网')])),
        ('R-O03', collections.OrderedDict([
            ('name', '组合交叉销售建议'), ('enabled', True), ('engine', 'swrl'),
            ('condition', '绑定率环比下降超阈'),
            ('advice', '建议基于本体兼容/互斥关系推荐主副卡、宽带+权益组合销售，提升绑定率')])),
        ('R-O04', collections.OrderedDict([
            ('name', '定价/套餐优化建议'), ('enabled', True), ('engine', 'swrl'),
            ('condition', 'ARPU 环比下降超阈'),
            ('advice', '建议复核套餐结构，增加高价值权益与升档引导，按本体价位分位优化定价')])),
        ('R-O05', collections.OrderedDict([
            ('name', '活跃运营建议'), ('enabled', True), ('engine', 'swrl'),
            ('condition', '活跃用户环比下降超阈'),
            ('advice', '建议开展活跃激励：签到/流量包/权益专区促活，结合用户群标签做精准运营')])),
    ])),
])

io.open(P, 'w', encoding='utf-8').write(json.dumps(d, ensure_ascii=False, indent=2) + '\n')
print('ops_rules.json patched')
print('alertGrading keys:', list(ag.keys()))
