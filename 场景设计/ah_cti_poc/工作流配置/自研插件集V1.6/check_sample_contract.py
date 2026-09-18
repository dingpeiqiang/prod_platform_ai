# -*- coding: utf-8 -*-
# 与平台真实样例《产品相似度匹配》(prodSimMatch) 契约逐项 diff 验证
import json, glob, os, sys

sys.stdout.reconfigure(encoding="utf-8")
BASE = os.path.dirname(os.path.abspath(__file__))

SAMPLE_TOP = ['createUserId', 'interfaceAddress', 'releaseTime', 'sequ', 'flowJson', 'userScope',
              'createUserName', 'releaseUser', 'toolAuthenticType', 'ontologyValidation', 'orgId',
              'toolProto', 'toolDesc', 'induction', 'llmType', 'toolIco', 'schemaJson',
              'knowledgeCategory', 'id', 'toolAuthenticInfo', 'ontology', 'toolName',
              'usedBySceneNum', 'updateTime', 'versionInfo', 'toolType', 'version', 'isDefault',
              'toolCode', 'observationField', 'submit_way', 'createTime', 'knowledgeCategoryName',
              'pluginName', 'toolJson', 'pluginsId', 'prompt', 'remarks', 'status', 'wsHeads']
FJ_KEYS = sorted(['authentic_info', 'authentic_info_new', 'id', 'inputs', 'nodeMeta',
                  'ontologyValidation', 'outputs', 'position', 'submit_way', 'type', 'url'])
TJ_KEYS = sorted(['authentic_info', 'authentic_info_new', 'candidate', 'description_for_model',
                  'flow_is_placeholder', 'input_parameters', 'is_llm', 'llm_type', 'name_for_human',
                  'name_for_model', 'observationField', 'outparameters', 'parameters',
                  'prompt_template', 'submit_way', 'type_for_tool', 'type_for_url', 'url_for_model'])


def out_leaf_nodes(fj):
    res = []
    for root in fj.get("outputs", []):
        for c in root.get("sechema", []):
            res.append(c)
    return res


bad = 0
for f in sorted(glob.glob(os.path.join(BASE, "*_export*.json"))):
    d = json.load(open(f, encoding="utf-8"))
    fn = os.path.basename(f)
    errs = []
    top = set(d.keys())
    miss_top = [k for k in SAMPLE_TOP if k not in top]
    extra_top = [k for k in top if k not in SAMPLE_TOP]
    if miss_top:
        errs.append("顶层缺键: " + str(miss_top))
    if extra_top:
        errs.append("顶层多键: " + str(extra_top))
    fj = json.loads(d["flowJson"])
    if sorted(fj.keys()) != FJ_KEYS:
        errs.append("flowJson键不一致: " + str(sorted(fj.keys())))
    if str(fj.get("type")) != "3":
        errs.append("flowJson.type != 3")
    ain = fj.get("authentic_info_new", {})
    if ain.get("auth_info") != "null":
        errs.append("auth_info 不是字符串null: " + repr(ain.get("auth_info")))
    if len(fj.get("inputs", [])) != 1 or fj["inputs"][0].get("name") != "ROOT":
        errs.append("inputs 非单根ROOT")
    in_names = [c["name"] for c in fj["inputs"][0].get("sechema", [])]
    if "BODY" in in_names or "BUSI_INFO" in in_names or "PROD_INFO" in in_names:
        errs.append("inputs 仍含 BODY/BUSI_INFO/PROD_INFO 容器")
    if len(fj.get("outputs", [])) != 1 or fj["outputs"][0].get("name") != "ROOT":
        errs.append("outputs 非单根ROOT")
    if "blockID" in fj["outputs"][0]:
        errs.append("outputs 含 blockID（样例无）")
    # 出参树不应含 BODY/OUT_DATA 容器（用户确认口径：仅 ROOT→业务叶子）
    out_names = [c["name"] for c in fj["outputs"][0].get("sechema", [])]
    if "BODY" in out_names or "OUT_DATA" in out_names:
        errs.append("outputs 仍含 BODY/OUT_DATA 容器")
    # toolJson.parameters 不应含容器节点
    tj = json.loads(d["toolJson"])
    for p in tj.get("parameters", []):
        if p["name"] in ("BODY", "BUSI_INFO", "PROD_INFO", "OUT_DATA"):
            errs.append("toolJson.parameters 仍含容器节点: " + p["name"])
    if sorted(tj.keys()) != TJ_KEYS:
        errs.append("toolJson键不一致: " + str(sorted(tj.keys())))
    ip = tj.get("input_parameters", {})
    if "ROOT" not in ip:
        errs.append("input_parameters 缺 ROOT 键")
    for k in ip:
        if "BODY" in k or "BUSI_INFO" in k or "PROD_INFO" in k:
            errs.append("input_parameters 键仍含容器: " + k)
    for node in out_leaf_nodes(fj):
        if node.get("type") == "array":
            items = node.get("sechema", [])
            if len(items) != 1 or items[0].get("name") != "item" or items[0].get("type") != "object":
                errs.append("array出参 " + node["name"] + " sechema 不是单个item object")
    if d.get("version") != "1":
        errs.append('version != "1"')
    if d.get("userScope") != 2:
        errs.append("userScope != 2")
    if errs:
        bad += 1
        print("BAD", fn)
        for e in errs:
            print("   ", e)
    else:
        print("OK ", fn)

print()
print("结论:", "全部通过" if bad == 0 else str(bad) + " 个文件需修正")
