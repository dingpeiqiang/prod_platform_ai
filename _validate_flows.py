import sys, json, os
sys.stdout.reconfigure(encoding='utf-8')
base = r'D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai\场景设计\ah_cti_poc\工作流配置\智能体工作流集V1.6'
files = ['wf_sub_00_需求提报.json', 'wf_sub_01_需求分析.json', 'wf_sub_02_智能配置.json',
         'wf_sub_03_规格稽核.json', 'wf_sub_04_自动测试.json', 'wf_sub_05_资费校准.json',
         'wf_sub_06_上线审批.json', 'wf_sub_07_监控运维.json', 'wf_sub_08_审批进度查询.json',
         'wf_sub_09_存量产品查询.json', 'wf_sub_10_存量合规扫描.json']
ALLOWED = {0, 1, 2, 3, 6, 9, 13}


def node_outputs(node):
    """Return dict plain_name -> full_relName. Empty dict if node has no declared outputs."""
    t = node.get('type')
    d = {}
    if t == 6:
        for o in node.get('outputs') or []:
            if isinstance(o, dict) and o.get('relName') and o.get('name'):
                d[o['name']] = o['relName']
    elif t == 0:
        # start node: inputs are implicit outputs
        for i in node.get('inputs') or []:
            if isinstance(i, dict) and i.get('name'):
                d[i['name']] = i['name']
    else:
        for o in node.get('outputs') or []:
            if isinstance(o, dict) and o.get('name'):
                d[o['name']] = o['name']
    return d


for fn in files:
    p = os.path.join(base, fn)
    if not os.path.exists(p):
        print(fn, 'MISSING FILE')
        continue
    d = json.load(open(p, encoding='utf-8'))
    schema = d['workFlowSchemaJSON']
    if isinstance(schema, str):
        schema = json.loads(schema)
    nodes = schema.get('nodes', schema.get('node_list', []))
    by_id = {n.get('id'): n for n in nodes}
    problems = []

    # A) code node (type=6) outputs must have relName = <id>,<name>
    for n in nodes:
        if n.get('type') == 6:
            nid = n.get('id')
            outs = n.get('outputs') or []
            if not outs:
                problems.append('code node %s has empty outputs' % nid)
            for o in outs:
                rel = o.get('relName')
                nm = o.get('name')
                if not rel:
                    problems.append('code node %s output %s missing relName' % (nid, nm))
                    continue
                if rel != '%s,%s' % (nid, nm):
                    problems.append('code node %s output relName mismatch: rel=%r name=%r' % (nid, rel, nm))

    # A2) LLM node (type=1) output binding contract (2026-09 修复点):
    #     if a single-output LLM node's prompt asks for a JSON object, the top-level key must be
    #     the output name (e.g. {"elements_json": "<json string>"}), not a bare multi-key object.
    for n in nodes:
        if n.get('type') == 1:
            nid = n.get('id')
            outs = n.get('outputs') or []
            if len(outs) == 1:
                oname = outs[0].get('name')
                lp = (n.get('inputs') or {}).get('llmParam') or []
                prompt = (lp[0].get('content') or '') if lp else ''
                if 'JSON 对象' in prompt or 'json 对象' in prompt:
                    # must instruct top-level key = output name
                    if ('键' not in prompt or oname not in prompt) and 'elements_json' not in prompt:
                        problems.append('LLM node %s: 单出参 %s 但 prompt 要求输出 JSON 对象，未见"顶层键=出参名"绑定契约（应如 {"%s":"<json字符串>"}）' %
                                        (nid, oname, oname))

    # B) every ref input resolves + three-layer consistency
    for n in nodes:
        nid = n.get('id')
        for inp in n.get('inputs') or []:
            if not isinstance(inp, dict) or inp.get('type') != 'ref':
                continue
            block_id = inp.get('blockID')
            rel = inp.get('relName')
            nv = inp.get('nameValue')
            cv = inp.get('currValue')
            if block_id not in by_id:
                problems.append('node %s ref input "%s" -> blockID %s missing' % (nid, inp.get('name'), block_id))
                continue
            up = by_id[block_id]
            decl = node_outputs(up)
            if rel not in decl:
                problems.append('node %s ref "%s": output "%s" not declared in node %s (declared: %s)' %
                                (nid, inp.get('name'), rel, block_id, sorted(decl.keys())))
                continue
            expected_full = '%s,%s' % (block_id, rel)
            # nameValue must be [blockID, blockID,rel]
            if not (isinstance(nv, list) and len(nv) == 2 and nv[0] == block_id and nv[1] == expected_full):
                problems.append('node %s ref "%s": nameValue=%r expect [%r, %r]' %
                                (nid, inp.get('name'), nv, block_id, expected_full))
            if cv != expected_full:
                problems.append('node %s ref "%s": currValue=%r expect %r' %
                                (nid, inp.get('name'), cv, expected_full))

    ts = set(n.get('type') for n in nodes)
    bad = ts - ALLOWED
    status = 'OK' if (not bad and not problems) else 'PROBLEMS'
    print('====', fn, 'nodes:', len(nodes), 'types:', sorted(ts), status)
    if bad:
        print('   BAD TYPES:', sorted(bad))
    for pr in problems:
        print('   !', pr)
