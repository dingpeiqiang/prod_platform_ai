# -*- coding: utf-8 -*-
"""v3: 把每个含 xsbot-panel 围栏块的结束节点，改为经由代码节点(type=6)确定性生成完整 content（话术+面板）。
代码节点输出键 content = 完整话术文本 + 面板围栏块；结束节点 outputs.content = {content}。
"""
import sys, io, json, re, glob
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

FENCE_RE = re.compile(r"\n```xsbot-panel\n(\{.*?\})\n```\n", re.S)


def parse_content(content):
    """把 content 拆成 (text_segs, panel_jsons)。
    text_segs: 文本段列表（保留 {token} 占位，去掉了围栏与面板 JSON）
    panel_jsons: 与 text_segs 间隔出现的面板 JSON 模板（含 {token} 占位）
    布局: SEG0, PANEL0, SEG1, PANEL1, ..., SEGN
    """
    text_segs = []
    panel_jsons = []
    pos = 0
    for m in FENCE_RE.finditer(content):
        text_segs.append(content[pos:m.start()])
        panel_jsons.append(m.group(1))
        pos = m.end()
    text_segs.append(content[pos:])
    return text_segs, panel_jsons


def collect_tokens(*templates):
    toks = set()
    for t in templates:
        for k in re.findall(r"\{(\w+)\}", t):
            toks.add(k)
    return toks


def build_code(code_node_id, seg_tpl_vars, panel_templates, parts, tokens):
    lines = []
    lines.append("import json")
    lines.append("")
    lines.append("")
    lines.append("def _sub(tpl, vals):")
    lines.append("    s = tpl")
    lines.append("    for k in vals:")
    lines.append("        s = s.replace('{' + k + '}', vals[k])")
    lines.append("    return s")
    lines.append("")
    for var in sorted(seg_tpl_vars.keys()):
        lines.append("%s = %s" % (var, repr(seg_tpl_vars[var])))
    for var in sorted(panel_templates.keys()):
        lines.append("%s = %s" % (var, repr(panel_templates[var])))
    lines.append("")
    lines.append("")
    lines.append("PARTS = [")
    for kind, var in parts:
        lines.append("    (%r, %s)," % (kind, var))
    lines.append("]")
    lines.append("")
    lines.append("async def main(args):")
    lines.append("    p = args.params")
    lines.append("")
    lines.append("    def _s(k):")
    lines.append("        v = p.get(k)")
    lines.append("        return '' if v is None else str(v)")
    lines.append("")
    lines.append("    vals = {")
    for t in sorted(tokens):
        lines.append("        %r: _s(%r)," % (t, t))
    lines.append("    }")
    lines.append("")
    lines.append("    def _render_panel(tpl):")
    lines.append("        return \"\\n```xsbot-panel\\n\" + json.dumps(json.loads(_sub(tpl, vals)), ensure_ascii=False) + \"\\n```\\n\"")
    lines.append("")
    lines.append("    buf = []")
    lines.append("    for kind, tpl in PARTS:")
    lines.append("        if kind == 'text':")
    lines.append("            buf.append(_sub(tpl, vals))")
    lines.append("        else:")
    lines.append("            buf.append(_render_panel(tpl))")
    lines.append("    content = \"\".join(buf)")
    lines.append("")
    lines.append("    return {")
    lines.append("        'content': content")
    lines.append("    }")
    return "\n".join(lines)


def input_ref(block_id, rel_name, name, desc, required=True):
    return {
        "blockID": block_id,
        "relName": rel_name,
        "name": name,
        "description": desc,
        "type": "ref",
        "required": required,
        "content": "",
        "nameValue": [block_id, "%s,%s" % (block_id, rel_name)],
        "currValue": "%s,%s" % (block_id, rel_name),
    }


def existing_ids(nodes):
    return set(n['id'] for n in nodes)


def new_code_id(existing, seq):
    while True:
        cid = "a1b2c3d4-0000-4000-8000-%012x" % (0xF0000 + seq)
        if cid not in existing:
            return cid
        seq += 1


def transform_file(fname, dry=False):
    d = json.load(io.open(fname, encoding='utf-8'))
    wsf = d['workFlowSchemaJSON']
    nodes = wsf['nodes']
    edges = wsf['edges']
    used_ids = existing_ids(nodes)
    seq = 0
    # find successor/pred edges
    for n in list(nodes):
        if n.get('type') != 9:
            continue
        content = (n.get('outputs') or {}).get('content', '')
        if '```xsbot-panel' not in content:
            continue
        end_id = n['id']
        # parse
        text_segs, panel_jsons = parse_content(content)
        # build source map from end inputs
        src = {}
        for i in n.get('inputs', []):
            if isinstance(i, dict):
                src.setdefault(i['name'], i)
        tokens = sorted(collect_tokens(*text_segs) | collect_tokens(*panel_jsons))
        # build parts + templates
        seg_vars = {}
        panel_vars = {}
        parts = []
        pi = 0
        for idx, seg in enumerate(text_segs):
            if seg != '':
                var = 'SEG_%d_TPL' % idx
                seg_vars[var] = seg
                parts.append(('text', var))
            if pi < len(panel_jsons):
                pvar = 'PANEL_%d_TPL' % pi
                panel_vars[pvar] = panel_jsons[pi]
                parts.append(('panel', pvar))
                pi += 1
        # new code node id
        cid = new_code_id(used_ids, seq)
        seq += 1
        used_ids.add(cid)
        # code node inputs
        code_inputs = []
        ok = True
        for t in tokens:
            si = src.get(t)
            if si is None:
                print('!! %s end %s: no source for token %r' % (fname, end_id[-4:], t))
                ok = False
                break
            code_inputs.append(input_ref(si['blockID'], si['relName'], t,
                "代码节点变量 %s（来源 %s,%s）" % (t, si['blockID'][-3:], si['relName'])))
        if not ok:
            continue
        code_node = {
            "outputs": [{"relName": "%s,content" % cid, "name": "content", "type": "string"}],
            "code": build_code(cid, seg_vars, panel_vars, parts, tokens),
            "flowJson": "",
            "inputs": code_inputs,
            "checkErr": False,
            "nodeMeta": {
                "description": "代码节点：确定性生成 xsbot-panel 完整内容（话术+面板），替代 LLM 拼装，保证面板结构与 URL 稳定",
                "title": "xsbot-panel 内容生成",
            },
            "language": 1,
            "id": cid,
            "position": {"x": n.get('position', {}).get('x', 0) - 240, "y": n.get('position', {}).get('y', 300)},
            "dependencyData": [],
            "type": 6,
        }
        # end node: outputs + inputs
        n['outputs']['content'] = '{content}'
        n['inputs'] = [input_ref(cid, 'content', 'content',
                                 "代码节点生成的 xsbot-panel 完整内容（话术+面板）")]
        # insert code node before end node in nodes list
        nodes.insert(nodes.index(n), code_node)
        # rewire edges: predecessor -> code -> end
        for e in edges:
            if e['endId'] == end_id:
                e['endId'] = cid
                edges.append({"sourcePort": None, "endId": end_id, "startId": cid})
    if not dry:
        io.open(fname, 'w', encoding='utf-8').write(json.dumps(d, ensure_ascii=False, indent=2))
    return True


if __name__ == '__main__':
    files = sorted(glob.glob('wf_sub_*.json'))
    for f in files:
        transform_file(f)
        print('transformed', f)
