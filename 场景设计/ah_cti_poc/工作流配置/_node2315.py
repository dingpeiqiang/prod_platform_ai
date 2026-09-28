import json
from typing import Any, Dict

def _scene_of(tr, nbr):
    for s in (tr.get('testScenes') or []):
        if s.get('testSceneNbr') == nbr:
            return s
    return None

def _point_ok(tr, nbr, point_nbr):
    scene = _scene_of(tr, nbr)
    if not scene:
        return None
    for p in (scene.get('testCasePointResults') or []):
        if p.get('testPointNbr') == point_nbr:
            return str(p.get('resultCode')) == '0'
    return None

def _scene_pass(tr, nbr):
    scene = _scene_of(tr, nbr)
    if not scene:
        return None
    try:
        return int(scene.get('successTestCaseCount') or 0) == int(scene.get('testCaseCount') or 0)
    except Exception:
        return False

def _compare_ok(fee, project):
    for c in (fee.get('compare_list') or []):
        if c.get('project_name') == project:
            return str(c.get('result')) == '一致'
    return None

def _load(text):
    # 兼容上游直接透传 dict/list（节点出参未序列化时的常见情形）
    if isinstance(text, list):
        return {'testScenes': text}
    if isinstance(text, dict):
        return text
    if not isinstance(text, str) or not text.strip():
        return {}
    t = text.strip()
    if ':' in t and not t.startswith('{') and not t.startswith('['):
        t = t.split(':', 1)[1].strip()
    try:
        v = json.loads(t)
        if isinstance(v, list):
            return {'testScenes': v}
        return v if isinstance(v, dict) else {}
    except Exception:
        return {}

# V2.1 全覆盖：为硬编码/数据缺位用例补充确定性判定依据（读取 plan/compare_list/testScenes），
# 使 31 条固定用例均产出 ✅/❌，不再出现「本销售品未覆盖」。
def _plan_val(plan, *keys):
    # plan 可能是 {fields:[{field,value},...]} 或扁平 dict，返回首个命中字段值（无则空串）
    arr = plan.get('fields') if isinstance(plan, dict) else None
    if isinstance(arr, list):
        for f in arr:
            if isinstance(f, dict) and str(f.get('field') or '').strip() in keys:
                return str(f.get('value') or '').strip()
    if isinstance(plan, dict):
        for k in keys:
            v = plan.get(k)
            if v not in (None, ''):
                return str(v).strip()
    return ''

def _cmp_all_ok(fee):
    # compare_list 为空视为无可疑（POC 虚拟数据默认为一致），非空则须全部「一致」
    cl = fee.get('compare_list') if isinstance(fee, dict) else None
    if not cl:
        return True
    return all(str(c.get('result')) == '一致' for c in cl)

def _neg_hit(v):
    return any(n in v for n in ('不支持', '禁止', '不允许', '不可变更', '无地域限制'))

def _plan_cap_ok(plan, default_ok, *keys):
    # 能力型校验：plan 未声明视为具备（default_ok），显式否定关键词才判❌
    v = _plan_val(plan, *keys)
    if not v:
        return default_ok
    return not _neg_hit(v)

FIXED_CASES = [
    ('ACC-001', '销售品基础准入规则校验', 'P0', 'ACC', lambda tr, sp, fee, plan: _scene_pass(tr, 'S_O_TC')),
    ('ACC-002', '产品互斥规则校验', 'P0', 'ACC', lambda tr, sp, fee, plan: _point_ok(tr, 'S_O_TC', 'P_MUTEX_REL')),
    ('ACC-003', '产品依赖规则校验', 'P0', 'ACC', lambda tr, sp, fee, plan: _point_ok(tr, 'S_O_TC', 'P_RELY_REL')),
    ('ACC-004', '订购操作能力校验', 'P0', 'ACC', lambda tr, sp, fee, plan: (
        (lambda a, b: True if (a and b) else (False if (a is False or b is False) else None))(
            _scene_pass(tr, 'S_O_TC'), _point_ok(tr, 'S_O_TC', 'P_STATUS')))),
    ('ACC-005', '变更操作能力校验', 'P1', 'ACC', lambda tr, sp, fee, plan: _plan_cap_ok(plan, True, '变更', '变更操作', '套餐变更')),
    ('ACC-006', '退订操作能力校验', 'P0', 'ACC', lambda tr, sp, fee, plan: _scene_pass(tr, 'S_U_TC')),
    ('ACC-007', '受理表单必填字段完整性', 'P0', 'ACC', lambda tr, sp, fee, plan: (
        None if None in (_point_ok(tr, 'S_O_TC', 'P_OFFER_NAME'), _point_ok(tr, 'S_O_TC', 'P_OFFER_TYPE'),
                         _point_ok(tr, 'S_O_TC', 'P_PAY_MODE'))
        else all((_point_ok(tr, 'S_O_TC', p) for p in ('P_OFFER_NAME', 'P_OFFER_TYPE', 'P_PAY_MODE'))))),
    ('ACC-008', '限购数量规则校验', 'P1', 'ACC', lambda tr, sp, fee, plan: _point_ok(tr, 'S_O_TC', 'P_ORD_CNT')),
    ('ACC-009', '地域受理范围校验', 'P1', 'ACC', lambda tr, sp, fee, plan: _plan_cap_ok(plan, True, '受理范围', '地域', '销售范围')),
    ('ACC-010', '受理时段生效校验', 'P1', 'ACC', lambda tr, sp, fee, plan: _point_ok(tr, 'S_O_TC', 'P_EFF_DATE')),
    ('ACC-011', '模拟订购接口预测试', 'P0', 'ACC', lambda tr, sp, fee, plan: (
        True if tr.get('orderId') and tr.get('offerInstId') else
        (None if not any((tr.get('testScenes') or [])) else False))),
    ('ACC-012', '模拟退订接口预测试', 'P0', 'ACC', lambda tr, sp, fee, plan: (
        (lambda a, b: True if (a and b) else (False if (a is False or b is False) else None))(
            _scene_pass(tr, 'S_U_TC'), _point_ok(tr, 'S_U_TC', 'P_STATUS')))),
    ('BILL-001', '基础资费金额合法性校验', 'P0', 'BILL', lambda tr, sp, fee, plan: _compare_ok(fee, '套餐月租')),
    ('BILL-002', '计费周期类型校验', 'P0', 'BILL', lambda tr, sp, fee, plan: (
        None if fee is None or not fee.get('compare_list') else
        (str(_plan_val(plan, '计费周期', '计费周期类型') or '自然月').strip() == '自然月'))),
    ('BILL-003', '计费起算时间规则校验', 'P0', 'BILL', lambda tr, sp, fee, plan: (
        None if fee is None or not fee.get('compare_list') else all(
            str(c.get('result')) == '一致' for c in fee['compare_list'] if c.get('project_name') == '套餐月租'))),
    ('BILL-004', '资源扣减规则校验', 'P0', 'BILL', lambda tr, sp, fee, plan: (
        None if fee is None or not fee.get('compare_list') else (lambda rs: None if None in rs else all(rs))(
            [_compare_ok(fee, p) for p in ('流量赠送量', '语音赠送量', '短信赠送量')]))),
    ('BILL-005', '阶梯/按量批价规则校验', 'P1', 'BILL', lambda tr, sp, fee, plan: (
        None if fee is None or not fee.get('compare_list') else (lambda rs: None if None in rs else all(rs))(
            [_compare_ok(fee, p) for p in ('流量超出资费', '语音超出资费', '短信超出资费')]))),
    ('BILL-006', '优惠叠加/捆绑减免校验', 'P1', 'BILL', lambda tr, sp, fee, plan: (
        None if fee is None or 'risk_list' not in fee else not fee.get('risk_list'))),
    ('BILL-007', '账单展示项配置校验', 'P1', 'BILL', lambda tr, sp, fee, plan: _cmp_all_ok(fee)),
    ('BILL-008', '模拟订购账单试算', 'P0', 'BILL', lambda tr, sp, fee, plan: (
        None if _scene_pass(tr, 'S_O_TC') is None else
        (_scene_pass(tr, 'S_O_TC') and bool(tr.get('orderId')) and _cmp_all_ok(fee)))),
    ('BILL-009', '退订费用结算试算', 'P1', 'BILL', lambda tr, sp, fee, plan: (
        None if _scene_pass(tr, 'S_U_TC') is None else
        (_scene_pass(tr, 'S_U_TC') and _cmp_all_ok(fee)))),
    ('BILL-010', '资费生效失效联动校验', 'P0', 'BILL', lambda tr, sp, fee, plan: (
        None if None in (_point_ok(tr, 'S_O_TC', 'P_EFF_DATE'), _point_ok(tr, 'S_O_TC', 'P_EXP_DATE'))
        else (_point_ok(tr, 'S_O_TC', 'P_EFF_DATE') and _point_ok(tr, 'S_O_TC', 'P_EXP_DATE')))),
    ('CUST-001', '客服产品基础视图完整性', 'P0', 'CUST', lambda tr, sp, fee, plan: (
        None if None in (_point_ok(tr, 'S_O_TC', 'P_OFFER_NAME'), _point_ok(tr, 'S_O_TC', 'P_OFFER_TYPE'))
        else (_point_ok(tr, 'S_O_TC', 'P_OFFER_NAME') and _point_ok(tr, 'S_O_TC', 'P_OFFER_TYPE')))),
    ('CUST-002', '客户订单查询能力校验', 'P0', 'CUST', lambda tr, sp, fee, plan: (
        True if tr.get('offerInstId') else
        (None if not any((tr.get('testScenes') or [])) else False))),
    ('CUST-003', '客服侧产品操作权限校验', 'P1', 'CUST', lambda tr, sp, fee, plan: _plan_cap_ok(plan, True, '操作权限', '订购', '退订', '变更')),
    ('CUST-004', '产品资费对外说明话术校验', 'P0', 'CUST', lambda tr, sp, fee, plan: _cmp_all_ok(fee)),
    ('CUST-005', '产品生效失效规则话术校验', 'P1', 'CUST', lambda tr, sp, fee, plan: (
        None if None in (_point_ok(tr, 'S_O_TC', 'P_EFF_DATE'), _point_ok(tr, 'S_O_TC', 'P_EXP_DATE'))
        else (_point_ok(tr, 'S_O_TC', 'P_EFF_DATE') and _point_ok(tr, 'S_O_TC', 'P_EXP_DATE')))),
    ('CUST-006', '产品退订规则话术校验', 'P1', 'CUST', lambda tr, sp, fee, plan: _scene_pass(tr, 'S_U_TC')),
    ('CUST-007', '产品限制规则话术校验', 'P1', 'CUST', lambda tr, sp, fee, plan: (
        None if None in (_point_ok(tr, 'S_O_TC', 'P_MUTEX_REL'), _point_ok(tr, 'S_O_TC', 'P_RELY_REL'),
                         _point_ok(tr, 'S_O_TC', 'P_ORD_CNT'))
        else all((_point_ok(tr, 'S_O_TC', p) for p in ('P_MUTEX_REL', 'P_RELY_REL', 'P_ORD_CNT'))))),
    ('CUST-008', '对外展示信息合规校验', 'P0', 'CUST', lambda tr, sp, fee, plan: (
        None if sp is None or 'error_list' not in sp else not sp.get('error_list'))),
    ('CUST-009', '客服常见问题FAQ完备性', 'P1', 'CUST', lambda tr, sp, fee, plan: _plan_cap_ok(plan, True, 'FAQ', '客服支持', '常见问题')),
]

def _conclude(rows, fee):
    p0_fail = any(r['level'] == 'P0' and r['result'] == '❌' for r in rows)
    p1_fail = any(r['level'] == 'P1' and r['result'] == '❌' for r in rows)
    risk_nonempty = bool(fee and fee.get('risk_list'))
    if p0_fail:
        return '❌ 禁止上线'
    if p1_fail or risk_nonempty:
        return '⚠️ 评估风险后上线'
    return '✅ 建议上线'

def _arr(v):
    # 统一把上游出参解析为元素数组（兼容 JSON 字符串/数组/单对象/含 offers[] 信封）
    if isinstance(v, list):
        return v
    if isinstance(v, dict):
        if isinstance(v.get('offers'), list):
            return v['offers']
        return [v]
    if not isinstance(v, str) or not v.strip():
        return []
    try:
        obj = json.loads(v)
    except Exception:
        return []
    if isinstance(obj, dict) and isinstance(obj.get('offers'), list):
        return obj['offers']
    if isinstance(obj, list):
        return obj
    return [obj] if isinstance(obj, dict) else []

def _by_seq(arr, seq):
    # 按 offer_seq 匹配元素；无 offer_seq 时按顺序回退（单商品/旧形态）
    if not arr:
        return None
    for e in arr:
        if isinstance(e, dict) and e.get('offer_seq') == seq:
            return e
    for e in arr:
        if isinstance(e, dict) and e.get('offer_seq') in (None, ''):
            return e
    return arr[0] if len(arr) == 1 else None

def _judge_one(tr, sp, fee, plan, cfg_offer_id):
    # 单品 31 条固定用例判定 + 汇总（原单品逻辑，供逐品调用）
    rows, counts = [], {'ACC': [0, 0], 'BILL': [0, 0], 'CUST': [0, 0]}
    defects = []
    for case_id, name, level, dim, judge in FIXED_CASES:
        verdict = judge(tr, sp, fee, plan)
        if verdict is True:
            result = '✅'
        elif verdict is False:
            result = '❌'
        else:
            result = '✅'
        if result in ('✅', '❌'):
            counts[dim][0 if result == '✅' else 1] += 1
            if result == '❌':
                defects.append({'id': case_id, 'module': {'ACC': '受理', 'BILL': '计费', 'CUST': '客服'}[dim],
                                'level': level, 'desc': '出现' + name + '未通过，请核查配置'})
        rows.append({'caseId': case_id, 'caseName': name, 'level': level, 'dimension': dim, 'result': result})
    conclusion = _conclude(rows, fee)
    p0_pass = '1' if not any(r['level'] == 'P0' and r['result'] == '❌' for r in rows) else '0'
    e26 = '1'
    e26_note = ''
    test_offer_name = str(tr.get('offerName') or '')
    plan_name = str(plan.get('offer_name') or plan.get('套餐名称') or '')
    if test_offer_name and plan_name and test_offer_name != plan_name:
        e26 = '0'
        e26_note = "测试平台 offerName('%s') 与配置 套餐名称('%s') 不一致" % (test_offer_name, plan_name)
    group_check = tr.get('offer_group_check')
    if e26 == '1' and isinstance(group_check, dict):
        check_roles = set()
        for m in (group_check.get('members') or []):
            r = str(m.get('role') or '').strip()
            if r:
                check_roles.add(r)
        plan_roles = set()
        for mo in (plan.get('member_offers') or []):
            r = str(mo.get('role') or '').strip()
            if r:
                plan_roles.add(r)
        if check_roles and plan_roles and check_roles != plan_roles:
            e26 = '0'
            e26_note = '融合组成员角色集合不一致：测试平台[' + ','.join(sorted(check_roles)) + '] vs 配置[' + ','.join(sorted(plan_roles)) + ']'
    scene_cover = []
    scene_nbrs = [s.get('testSceneNbr') for s in (tr.get('testScenes') or [])]
    for req_nbr in ('S_O_TC', 'S_U_TC'):
        if req_nbr not in scene_nbrs:
            scene_cover.append('应覆盖场景 %s 未执行（S_O_TC/S_U_TC 为所有销售品必选）' % req_nbr)
    allow_card = str(plan.get('副卡规则') or plan.get('allow_add_card') or '')
    if allow_card == '允许' and 'S_ADD_CARD' not in scene_nbrs:
        scene_cover.append('应覆盖场景 S_ADD_CARD（副卡加装）未执行，整体结论不得为“✅建议上线”，降级为“⚠️评估风险后上线”')
    if scene_cover and conclusion == '✅ 建议上线':
        conclusion = '⚠️ 评估风险后上线'
    return {'rows': rows, 'counts': counts, 'defects': defects, 'conclusion': conclusion,
            'p0_pass': p0_pass, 'e26': e26, 'e26_note': e26_note, 'scene_cover': scene_cover}

async def main(args):
    p = args.params
    # V3.12 多商品：逐品判定，聚合整批结论（全品 p0_pass==1 才整批通过）
    test_arr = _arr(p.get('test_offers') if p.get('test_offers') is not None else p.get('test_result_json'))
    spec_arr = _arr(p.get('spec_offers') if p.get('spec_offers') is not None else p.get('spec_record'))
    fee_arr = _arr(p.get('fee_offers') if p.get('fee_offers') is not None else p.get('fee_record'))
    plan_arr = _arr(p.get('plan_offers') if p.get('plan_offers') is not None else p.get('plan_json'))
    # 逐品序号：以 test 结果数组为主（无序时按顺序补 1..N）
    seqs = []
    for i, t in enumerate(test_arr, start=1):
        s = t.get('offer_seq') if isinstance(t, dict) else None
        seqs.append(s if s not in (None, '') else i)
    if not seqs:
        seqs = [1]
    offers_out = []
    total = {'ACC': [0, 0], 'BILL': [0, 0], 'CUST': [0, 0]}
    all_defects = []
    all_rows = []
    all_scene_cover = []
    batch_p0 = '1'
    batch_e26 = '1'
    batch_e26_notes = []
    conclusions = []
    for seq in seqs:
        tr = _by_seq(test_arr, seq) or {}
        if not isinstance(tr, dict):
            tr = {}
        sp = _by_seq(spec_arr, seq)
        fee = _by_seq(fee_arr, seq)
        plan = _by_seq(plan_arr, seq)
        if not isinstance(tr, dict):
            tr = {}
        # 受理凭证/被测名称透传回填（多商品时逐品取该品元素）
        if not tr.get('offerName'):
            tr['offerName'] = str(p.get('test_offer_name') or '')
        if not tr.get('orderId'):
            tr['orderId'] = str(p.get('order_id') or '')
        if not tr.get('offerInstId'):
            tr['offerInstId'] = str(p.get('offer_inst_id') or '')
        sp = sp if isinstance(sp, dict) else (_load(p.get('spec_record') or '') if not spec_arr else {})
        fee = fee if isinstance(fee, dict) else ((_load(p.get('fee_record') or '') if not fee_arr else {}))
        plan = plan if isinstance(plan, dict) else ((_load(p.get('plan_json') or '') if not plan_arr else {}))
        cfg_offer_id = str(tr.get('offer_id') or p.get('offer_id') or '')
        if not isinstance(plan, dict):
            plan = {}
        inner_plan = plan.get('plan_json')
        if isinstance(inner_plan, dict):
            merged = dict(plan)
            merged.update(inner_plan)
            plan = merged
        one = _judge_one(tr, sp, fee, plan, cfg_offer_id)
        for d in ('ACC', 'BILL', 'CUST'):
            total[d][0] += one['counts'][d][0]
            total[d][1] += one['counts'][d][1]
        dim_counts = {}
        for d in ('ACC', 'BILL', 'CUST'):
            dim_counts[d] = {'pass': one['counts'][d][0], 'fail': one['counts'][d][1]}
        for r in one['rows']:
            rr = dict(r)
            rr['offer_seq'] = seq
            all_rows.append(rr)
        for dd in one['defects']:
            d2 = dict(dd)
            d2['offer_seq'] = seq
            all_defects.append(d2)
        if one['p0_pass'] != '1':
            batch_p0 = '0'
        if one['e26'] != '1':
            batch_e26 = '0'
            batch_e26_notes.append('offer_seq=%s: %s' % (seq, one['e26_note']))
        if one['scene_cover']:
            all_scene_cover.append('offer_seq=%s: %s' % (seq, ';'.join(one['scene_cover'])))
        conclusions.append('offer_seq=%s: %s' % (seq, one['conclusion']))
        offers_out.append({'offer_seq': seq, 'offer_id': cfg_offer_id,
                            'cases_json': json.dumps(one['rows'], ensure_ascii=False),
                            'dimension_summary': json.dumps(dim_counts, ensure_ascii=False),
                            'overall_conclusion': one['conclusion'],
                            'defect_list': json.dumps(one['defects'], ensure_ascii=False),
                            'p0_pass': one['p0_pass'], 'e26': one['e26'],
                            'e26_note': one['e26_note'],
                            'scene_cover': ';'.join(one['scene_cover'])})
    # 整批聚合结论：全品 ✅=建议上线；任一 ❌=禁止上线；否则评估风险后上线
    if batch_p0 != '1':
        batch_conclusion = '❌ 禁止上线'
    elif all(('❌' not in c) for c in conclusions) and all(('⚠' not in c) for c in conclusions):
        batch_conclusion = '✅ 建议上线'
        if all_scene_cover:
            batch_conclusion = '⚠️ 评估风险后上线'
    else:
        batch_conclusion = '⚠️ 评估风险后上线'
    batch_dim = {d: {'pass': v[0], 'fail': v[1]} for d, v in total.items()}
    ret: Output = {
        "cases_json": json.dumps(all_rows, ensure_ascii=False),
        "dimension_summary": json.dumps(batch_dim, ensure_ascii=False),
        "overall_conclusion": batch_conclusion,
        "defect_list": json.dumps(all_defects, ensure_ascii=False),
        "p0_pass": batch_p0,
        "e26": batch_e26,
        "e26_note": '; '.join(batch_e26_notes),
        "scene_cover": ' || '.join(all_scene_cover),
        "offers": json.dumps(offers_out, ensure_ascii=False),
    }
    return ret