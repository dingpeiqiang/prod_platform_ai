# -*- coding: utf-8 -*-
# 校验自研插件集V1.6各工具 与 9个工作流JSON中 plugin节点(type=3) 引用 的一致性
# 插件侧口径（对齐平台真实样例）：
#   入参 = toolJson.parameters 中 isParameter=1 的叶子参数（工作流按叶子名引用）
#   出参 = flowJson.outputs 树中 OUT_DATA 直接子节点的叶子字段
# 另校验 schemaJson 请求/响应均从 ROOT(object) 展开且含 BODY 容器
import json, io, os, sys

sys.stdout.reconfigure(encoding="utf-8")
BASE = os.path.dirname(os.path.abspath(__file__))
WF_DIR = os.path.normpath(os.path.join(BASE, "..", "..", "工作流配置", "V1.6"))


def get_in_leaves(tool_json):
    """toolJson.parameters 中 isParameter=1 的叶子参数名"""
    return set(p["name"] for p in tool_json.get("parameters", []) if p.get("isParameter") == 1)


def get_out_leaves(flow_json):
    """flowJson.outputs 树中 ROOT 直接子节点的业务叶子字段名（无BODY/OUT_DATA容器）"""
    res = set()
    for root in flow_json.get("outputs", []):
        for leaf in root.get("sechema", []):
            res.add(leaf["name"])
    return res


def get_out_types(flow_json):
    res = {}
    for root in flow_json.get("outputs", []):
        for leaf in root.get("sechema", []):
            res[leaf["name"]] = leaf.get("type", "string")
    return res


# ---------- 1. 载入插件定义 ----------
plugins = {}
for fn in sorted(os.listdir(BASE)):
    if fn.endswith("_export.json") or fn.endswith("_export_V1.6.json"):
        d = json.load(io.open(os.path.join(BASE, fn), encoding="utf-8"))
        fj = json.loads(d["flowJson"])
        tj = json.loads(d["toolJson"])
        code = fj["nodeMeta"]["code"]
        url = d["interfaceAddress"]
        plugins[code] = {
            "file": fn, "url": url,
            "ins": get_in_leaves(tj), "outs": get_out_leaves(fj),
            "out_types": get_out_types(fj),
            "title": fj["nodeMeta"]["title"],
        }

# ---------- 2. 扫描工作流 plugin 节点 ----------
issues = []
used = set()
for fn in sorted(os.listdir(WF_DIR)):
    if not fn.endswith(".json"):
        continue
    wf = json.load(io.open(os.path.join(WF_DIR, fn), encoding="utf-8"))
    flow = wf["workFlowSchemaJSON"]
    nodes = {n["id"]: n for n in flow["nodes"]}
    for n in flow["nodes"]:
        if n.get("type") != 3:
            continue
        code = n["nodeMeta"]["code"]
        title = n["nodeMeta"]["title"]
        url = n.get("url", "")
        used.add(code)
        if code not in plugins:
            issues.append(f"[{fn}] 节点「{title}」引用工具 code={code} 在插件集中不存在")
            continue
        p = plugins[code]
        d_fj = json.load(io.open(os.path.join(BASE, p["file"]), encoding="utf-8"))["flowJson"]
        if p["url"] != url:
            issues.append(f"[{fn}] 节点「{title}」url 不一致:\n    工作流: {url}\n    插件:   {p['url']}")
        # 输入参数名匹配（叶子名）
        wf_in = set(i["name"] for i in n.get("inputs", []))
        miss = wf_in - p["ins"]
        extra = p["ins"] - wf_in
        if miss:
            issues.append(f"[{fn}] 节点「{title}」传了插件未定义的输入参数: {sorted(miss)} (插件叶子参数: {sorted(p['ins'])})")
        if extra:
            issues.append(f"[{fn}] 节点「{title}」未传插件必填输入参数（提示核对 required）: {sorted(extra)}")
        # 出参名匹配：下游节点引用 blockID=本节点 的 relName
        outs_ref = set()
        for m in flow["nodes"]:
            vals = m.get("inputs")
            if isinstance(vals, list):
                for ip in vals:
                    if isinstance(ip, dict) and ip.get("blockID") == n["id"] and ip.get("relName"):
                        outs_ref.add(ip["relName"])
            elif isinstance(vals, dict):
                for k, v in vals.items():
                    if isinstance(v, list):
                        for ip in v:
                            if isinstance(ip, dict) and ip.get("blockID") == n["id"] and ip.get("relName"):
                                outs_ref.add(ip["relName"])
        unknown_out = [o for o in outs_ref if o not in p["outs"]]
        if unknown_out:
            issues.append(f"[{fn}] 节点「{title}」下游引用了插件未定义的出参: {unknown_out} (插件出参: {sorted(p['outs'])})")
        # 出参类型一致性：工作流 outputs 声明 vs 插件定义
        for o in n.get("outputs", []):
            if o["name"] in p["outs"] and o.get("type") != p["out_types"].get(o["name"]):
                issues.append(f"[{fn}] 节点「{title}」出参 {o['name']} 类型不一致: 工作流={o.get('type')} 插件={p['out_types'].get(o['name'])}")
            # array 出参须填充 item 树（sechema 非空且首节点为 item object）
            if o.get("type") == "array":
                scm = o.get("sechema") or []
                if not scm or scm[0].get("name") != "item" or scm[0].get("type") != "object":
                    issues.append(f"[{fn}] 节点「{title}」array 出参 {o['name']} sechema 缺 item object 节点")
                else:
                    if not scm[0].get("sechema"):
                        issues.append(f"[{fn}] 节点「{title}」array 出参 {o['name']} item 无叶子字段")
                    # 与插件定义叶子字段名比对
                    def leaf_names(node):
                        res = {}
                        for c in node.get("sechema", []):
                            res[c["name"]] = c
                        return res
                    wf_leaves = leaf_names(scm[0])
                    pj_item = None
                    for root in json.loads(d_fj)["outputs"]:
                        for lf in root.get("sechema", []):
                            if lf["name"] == o["name"] and lf.get("sechema"):
                                pj_item = lf["sechema"][0]
                    if pj_item:
                        pj_leaves = leaf_names(pj_item)
                        miss_l = set(wf_leaves) - set(pj_leaves)
                        extra_l = set(pj_leaves) - set(wf_leaves)
                        if miss_l:
                            issues.append(f"[{fn}] 节点「{title}」array 出参 {o['name']} item 多字段: {sorted(miss_l)}")
                        if extra_l:
                            issues.append(f"[{fn}] 节点「{title}」array 出参 {o['name']} item 缺字段: {sorted(extra_l)}")

# ---------- 3. schemaJson 结构校验 ----------
for code, p in sorted(plugins.items()):
    d = json.load(io.open(os.path.join(BASE, p["file"]), encoding="utf-8"))
    sj = d.get("schemaJson", {})
    for path, methods in sj.get("paths", {}).items():
        for m, op in methods.items():
            req = op.get("requestBody", {}).get("content", {}).get("application/json", {}).get("schema", {})
            resp = op.get("responses", {}).get("200", {}).get("content", {}).get("application/json", {}).get("schema", {})
            if req.get("type") != "object":
                issues.append(f"[{p['file']}] schemaJson {m} {path} 请求schema 未从 ROOT(object) 展开")
            elif "BODY" in req.get("properties", {}):
                issues.append(f"[{p['file']}] schemaJson {m} {path} 请求schema 仍含 BODY 容器（应已去掉）")
            if resp.get("type") != "object":
                issues.append(f"[{p['file']}] schemaJson {m} {path} 响应schema 未从 ROOT(object) 展开")
            elif "BODY" in resp.get("properties", {}):
                issues.append(f"[{p['file']}] schemaJson {m} {path} 响应schema 仍含 BODY 容器（应已去掉）")

unused = [c for c in plugins if c not in used]
print("=== 插件定义 ===")
for c, p in sorted(plugins.items()):
    print(f"{c} ({p['title']})  file={p['file']}")
    print(f"  in : {sorted(p['ins'])}")
    print(f"  out: {sorted(p['outs'])}")
print()
print("=== 工作流引用检查结果 ===")
if issues:
    for i in issues:
        print("!!", i)
else:
    print("全部一致，未发现问题。")
if unused:
    print()
    print("=== 插件集中未被任何工作流引用的工具 ===")
    for c in unused:
        print("-", c, plugins[c]["title"])
