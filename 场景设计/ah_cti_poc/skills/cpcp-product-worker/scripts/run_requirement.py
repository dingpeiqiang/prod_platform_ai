#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""需求分析确定性状态机（run_requirement V2.0，flow-A 模板轨收敛）。

背景：flow-A 六步串行、相似检索双源降级、文件切分、校验判定、异常路由原先全部交给
模型按文字纪律手工编排，导致往返 ~12 次、E31 打回整段重跑、且依赖 LLM 服从度。
V1.0 已把确定性步骤收敛为三阶段状态机；V2.0 进一步：

  · 纯本地确定性步骤改为直接库导入（merge_nested/validate_elements/render_table），
    identify_products/derive_flat24 内联纯逻辑 → 消除 5 次子进程派生、降低 argv/解析失败面；
    仅后端 HTTP 步骤（validate_nested/explain_nested/save_node_result/query_offer/similar_offer）保留子进程。
  · 渲染表格落盘 plan_md_<req_id>.md、推理依据落盘 explain_<req_id>.json、全量结果落盘
    requirement_result_<req_id>.json；stdout 只回【紧凑摘要】→ 大幅降低模型往返 token。
  · 新增 requirement_state_<req_id>.json 阶段状态持久化 + 前置校验（products→prepare→merge-save），
    防错舞台调用/重复执行。
  · E31 增量补提：--elements-supplement 与既有 elements 叠层合并，免整段重提（方案B 增强）。

只保留两处 LLM 翻译点（步骤① 产品识别、步骤④ 要素提取），模型只在两处翻译 + 读渲染结果。

三阶段：
  --stage products    步骤①后置闸（identify_products 内联）→ products_valid.json
  --stage prepare     步骤②相似检索（本地 query_offer 优先 + 远端 similar_offer 兜底，脚本自动降级）
                      + ③取模板 + 落盘步骤④提取提示词资产
  --stage merge-save  ④校验闸(validate_elements 直接导入) → ⑤merge_nested(直接导入，三工件落盘)
                      → ⑤.5 validate_nested+explain_nested → ⑦ derive_flat24+save_node_result(无待补充)
                      → ⑥render_table(直接导入) → 落盘 plan_md/explain/result
                      退出码：0=出口B(已保存) / 1=出口A(待补充未保存) / 2=参数/工件缺失
                             / 3=E30 产品识别失败 / 4=E31 提取质量门禁 / 5=E32 模板缺失
                             / 6=E33 嵌套校验未通过 / 7=E1 相似检索双源均未命中

四层架构：确定性逻辑全部脚本化，LLM 只做 ①④ 两处"自然语言→结构化翻译"，逐字节可回归。
"""
import argparse
import io
import json
import os
import re
import subprocess
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SKILL_DIR = os.path.dirname(SCRIPT_DIR)
TEMPLATE_DIR = os.path.join(SCRIPT_DIR, "templates")
K5_DIR = os.path.join(SKILL_DIR, "references", "K5存量报文")
MAPPING_FILE = os.path.join(SKILL_DIR, "references", "ontology-fields.json")

# 纯本地确定性库（与 cpcp_api 同目录，直接导入，避免子进程）
sys.path.insert(0, SCRIPT_DIR)
import merge_nested
import render_table
import validate_elements

TIER_AMOUNT_PAT = re.compile(r"[\d]+(?:\.\d+)?元")
STAGE_ORDER = ("products", "prepare", "merge-save")


def _emit(obj, exit_code=0):
    print(json.dumps(obj, ensure_ascii=False))
    sys.exit(exit_code)


def _api(args_list):
    """仅后端 HTTP 步骤使用：调用 cpcp_api.py 子命令，返回 (ok, out_dict)。"""
    cmd = [sys.executable, os.path.join(SCRIPT_DIR, "cpcp_api.py")] + args_list
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=180,
                           cwd=SCRIPT_DIR, encoding="utf-8", errors="replace")
        raw = (r.stdout or "").strip()
        if not raw:
            return False, {"resultCode": "SCRIPT_ERROR",
                           "resultMsg": "cpcp_api 无输出（stderr=%s）" % (r.stderr or "")[:300]}
        return True, json.loads(raw)
    except subprocess.TimeoutExpired:
        return False, {"resultCode": "TIMEOUT", "resultMsg": "cpcp_api 调用超时"}
    except json.JSONDecodeError:
        return False, {"resultCode": "PARSE_ERROR", "resultMsg": "cpcp_api 出参不是合法 JSON"}
    except Exception as e:
        return False, {"resultCode": "SCRIPT_ERROR", "resultMsg": str(e)}


def _read_json(path):
    with open(path, "r", encoding="utf-8-sig") as f:
        return json.load(f)


def _write_json(path, obj):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)


def _write_text(path, text):
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def _read_text(path):
    with open(path, "r", encoding="utf-8-sig") as f:
        return f.read()


def _art(workdir, name):
    return os.path.join(workdir, name)


def _read_schema(template):
    path = os.path.join(TEMPLATE_DIR, "%s.schema.json" % template)
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


# ---------------- 阶段状态持久化（优化3） ----------------

def _state_path(workdir, req_id):
    return _art(workdir, "requirement_state_%s.json" % req_id)


def load_state(workdir, req_id):
    p = _state_path(workdir, req_id)
    if os.path.exists(p):
        try:
            return _read_json(p)
        except Exception:
            pass
    return {"req_id": req_id, "done_stages": []}


def save_state(workdir, state):
    _write_json(_state_path(workdir, state["req_id"]), state)


def _require_prior(state, stage):
    """前置校验：目标 stage 之前的所有阶段必须已完成，防错舞台调用（优化3）。"""
    idx = STAGE_ORDER.index(stage)
    need = STAGE_ORDER[:idx]
    done = set(state.get("done_stages") or [])
    missing = [s for s in need if s not in done]
    if missing:
        _emit({"resultCode": "PARAM_MISSING", "stage": stage, "e_code": "E30",
               "resultMsg": "前置阶段未完成，请依次执行：%s（当前缺 %s）"
                            % (" → ".join(need), "、".join(missing))}, 2)


# ---------------- 步骤① 产品识别（内联纯逻辑） ----------------

def _template_product_types():
    types, seen = {}, set()
    if os.path.isdir(TEMPLATE_DIR):
        for name in sorted(os.listdir(TEMPLATE_DIR)):
            if not name.endswith(".schema.json"):
                continue
            tid = name[: -len(".schema.json")]
            try:
                with open(os.path.join(TEMPLATE_DIR, name), "r", encoding="utf-8") as f:
                    schema = json.load(f)
                pt = schema.get("x-product-type")
            except Exception:
                pt = None
            if pt and pt not in seen:
                seen.add(pt)
                types[tid] = pt
    return types


def stage_products(args, req_id, workdir, state):
    products_file = args.products_file or _art(workdir, "products_%s.json" % req_id)
    if not os.path.exists(products_file):
        _emit({"resultCode": "PARAM_MISSING", "stage": "products", "e_code": "E30",
               "resultMsg": "缺少步骤①产品识别文件 --products-file（LLM 翻译产物）"}, 2)
    data = _read_json(products_file)
    products = data.get("products") if isinstance(data, dict) else data
    if not isinstance(products, list) or not products:
        _emit({"resultCode": "PARAM_MISSING", "stage": "products", "e_code": "E30",
               "resultMsg": "products_json 应含非空 products 数组（步骤① LLM 识别产出）"}, 2)
    valid_types = _template_product_types()
    ok, bad = [], []
    for p in products:
        if isinstance(p, dict) and p.get("prodName") and p.get("prodType") in valid_types.values():
            ok.append(p)
        else:
            bad.append(p)
    if bad:
        _emit({"resultCode": "VALIDATE_FAIL", "stage": "products", "e_code": "E30",
               "resultMsg": "产品类型无法归类", "invalid_products": bad,
               "valid_product_types": sorted(set(valid_types.values()))}, 3)
    _write_json(_art(workdir, "products_valid.json"), ok)
    state["done_stages"] = list(dict.fromkeys((state.get("done_stages") or []) + ["products"]))
    save_state(workdir, state)
    _emit({"resultCode": "0", "stage": "products",
           "resultMsg": "步骤①校验通过",
           "valid_products": ok,
           "products_file": products_file,
           "next_stage": "prepare"}, 0)


# ---------------- 步骤② 相似检索 + ③ 模板 + ④ 提取提示词资产 ----------------

def _product_keywords(prod):
    name = str(prod.get("prodName") or "").strip()
    cands = []
    bare = TIER_AMOUNT_PAT.sub("", name).replace("套餐", "").strip()
    if bare:
        cands.append(bare)
    core = name.split("套餐")[0].strip() if "套餐" in name else name
    if core and core not in cands:
        cands.append(core)
    if name not in cands:
        cands.append(name)
    return [c for c in cands if c]


def _resolve_k5_report(offer_id):
    path = os.path.join(K5_DIR, "%s.json" % offer_id)
    return path if os.path.exists(path) else None


def _wrapper_template(offer_path):
    try:
        data = _read_json(offer_path)
    except Exception:
        return None
    if isinstance(data, dict) and len(data) == 1:
        k = next(iter(data))
        if isinstance(data[k], dict):
            return k
    return None


# 需求产品类型 → 存量目录 product_type 对齐表（避免宽泛关键词命中他类型产品导致模板路由错配，
# 例如"个人主套餐"应命中"单品套餐"而非"融合套餐"，从而落到 personMainPrc 而非 familyBasePrc）。
_PRODTYPE_TO_CATALOG_TYPE = {
    "个人主套餐": "单品套餐",
    "宽带主套餐": "单品套餐",
    "个人附加资费": "权益包",
    "宽带附加资费": "单品套餐",
    "家庭基础套餐": "融合套餐",
    "家庭附加资费": "融合套餐",
}


def _local_query(prod):
    target_type = _PRODTYPE_TO_CATALOG_TYPE.get(str(prod.get("prodType") or ""))
    for kw in _product_keywords(prod):
        ok, out = _api(["query_offer", "--keyword", kw])
        matched = (out or {}).get("matched") or []
        if ok and matched:
            if target_type:
                type_matched = [m for m in matched if m.get("product_type") == target_type]
                if type_matched:
                    return out, type_matched[0]
            return out, matched[0]
    return None, None


def _remote_query(need_summary):
    ok, out = _api(["similar_offer", "--desc", need_summary])
    if not ok:
        return False, out or {}
    rc = str((out or {}).get("resultCode", "1"))
    so = (out or {}).get("similarOffer") if isinstance(out, dict) else None
    return (rc == "0" and bool(so)), (out or {})


def _leaf_list(schema):
    leaves = []

    def walk(node, prefix=""):
        props = node.get("properties") if isinstance(node, dict) else None
        if not isinstance(props, dict):
            return
        for k, v in props.items():
            p2 = (prefix + "." + k) if prefix else k
            if isinstance(v, dict) and v.get("type") == "object":
                walk(v, p2)
            else:
                v = v or {}
                enum = "、".join(str(e) for e in (v.get("enum") or [])[:6])
                cond = (v.get("x-show-when") or "")
                if cond.endswith("才展示"):
                    cond = cond[:-3] + "才展示"
                leaves.append({"path": p2, "label": v.get("x-label") or k, "type": v.get("type") or "",
                               "enum": enum, "required": bool(v.get("x-required")), "cond": cond})
    walk(schema)
    return leaves


def stage_prepare(args, req_id, workdir, state):
    _require_prior(state, "prepare")
    products_file = args.products_file or _art(workdir, "products_valid.json")
    if not os.path.exists(products_file):
        _emit({"resultCode": "PARAM_MISSING", "stage": "prepare", "e_code": "E30",
               "resultMsg": "缺少 products_valid.json，请先执行 --stage products（或传 --products-file）"}, 2)
    products_data = _read_json(products_file)
    products = products_data.get("products") if isinstance(products_data, dict) else products_data
    if not isinstance(products, list) or not products:
        _emit({"resultCode": "PARAM_MISSING", "stage": "prepare", "e_code": "E30",
               "resultMsg": "products_valid.json 为空"}, 2)
    need_summary = args.requirement_summary or ""
    if args.requirement_file:
        try:
            need_summary = _read_text(args.requirement_file)
        except Exception as e:
            _emit({"resultCode": "PARSE_ERROR", "stage": "prepare", "e_code": "E30",
                   "resultMsg": "需求原文读取失败：%s" % e}, 2)
    if not need_summary.strip():
        _emit({"resultCode": "PARAM_MISSING", "stage": "prepare", "e_code": "E30",
               "resultMsg": "缺少需求原文 --requirement-file/--requirement-summary（步骤④ 提取素材）"}, 2)

    prep = []
    for i, prod in enumerate(products):
        instr = {"index": i, "prodName": prod.get("prodName"), "prodType": prod.get("prodType"),
                 "members": prod.get("members") or []}
        local_out, hit = _local_query(prod)
        similar_meta = {}
        offer_obj = None
        template = ""
        if hit:
            oid = str(hit.get("offer_id") or "")
            oname = hit.get("name") or oid
            similar_meta = {"similarOfferId": oid, "similarOfferName": oname}
            template = str(hit.get("template") or "")
            rpt = _resolve_k5_report(oid)
            if rpt:
                offer_obj = _read_json(rpt)
                wrapper = _wrapper_template(rpt)
                if wrapper and not template:
                    template = wrapper
            instr["similar_source"] = "local"
            instr["offer_id"] = oid
        else:
            ok_remote, ro = _remote_query(need_summary)
            if not ok_remote:
                _emit({"resultCode": "VALIDATE_FAIL", "stage": "prepare", "e_code": "E1",
                       "resultMsg": "相似产品服务暂不可用/未命中（本地+远端双源均未命中）："
                                    "回复【继续】跳过相似产品仅用需求原文补全，或回复【修改需求】",
                       "product": prod.get("prodName")}, 7)
            so = ro.get("similarOffer") or {}
            oid = str(so.get("offerId") or so.get("similarOfferId") or "")
            oname = str(so.get("offerName") or so.get("similarOfferName") or prod.get("prodName") or "")
            similar_meta = {"similarOfferId": oid, "similarOfferName": oname}
            template = str(so.get("template") or "")
            offer_template = so.get("offerTemplate")
            if isinstance(offer_template, dict):
                offer_obj = offer_template
            else:
                rpt = _resolve_k5_report(oid)
                if rpt:
                    offer_obj = _read_json(rpt)
                    wrapper = _wrapper_template(rpt)
                    if wrapper and not template:
                        template = wrapper
            instr["similar_source"] = "remote"
            instr["offer_id"] = oid
        if not template:
            _emit({"resultCode": "VALIDATE_FAIL", "stage": "prepare", "e_code": "E32",
                   "resultMsg": "相似产品未解析出 template 字段，模板路由失败（模型不猜 templateId）",
                   "product": prod.get("prodName")}, 5)
        schema_path = os.path.join(TEMPLATE_DIR, "%s.schema.json" % template)
        if not os.path.exists(schema_path):
            _emit({"resultCode": "VALIDATE_FAIL", "stage": "prepare", "e_code": "E32",
                   "resultMsg": "模板不存在：%s（可用模板见 scripts/templates/）" % template}, 5)
        with open(schema_path, "r", encoding="utf-8") as f:
            schema = json.load(f)
        leaf_list = _leaf_list(schema)

        offer_file = _art(workdir, "similar_offer_%s_%d.json" % (req_id, i))
        if offer_obj is not None:
            _write_json(offer_file, offer_obj)
        instr["offer_file"] = offer_file if offer_obj is not None else ""
        _write_json(_art(workdir, "similar_offer_meta_%s_%d.json" % (req_id, i)), similar_meta)
        instr["template"] = template

        prompt_asset = {"index": i, "prodName": prod.get("prodName"), "prodType": prod.get("prodType"),
                        "template": template, "similar": similar_meta,
                        "leaf_list": leaf_list, "requirement_text": need_summary}
        prompt_file = _art(workdir, "extract_prompt_%s_%d.json" % (req_id, i))
        _write_json(prompt_file, prompt_asset)
        instr["prompt_file"] = prompt_file
        prep.append(instr)

    _write_json(_art(workdir, "prepare_%s.json" % req_id), prep)
    state["done_stages"] = list(dict.fromkeys((state.get("done_stages") or []) + ["prepare"]))
    save_state(workdir, state)
    _emit({"resultCode": "0", "stage": "prepare",
           "resultMsg": "步骤②/③完成，步骤④ 提取资产已就绪",
           "products": prep,
           "next_stage": "merge-save"}, 0)


# ---------------- 阶段三：④ 校验闸 + ⑤ 合并 + ⑤.5 校验 + ⑦ 保存 + ⑥ 渲染 ----------------

def _elements_map(workdir, req_id, elements_file):
    if not os.path.exists(elements_file):
        _emit({"resultCode": "PARAM_MISSING", "stage": "merge-save", "e_code": "E31",
               "resultMsg": "缺少步骤④提取文件 --elements-file（LLM 翻译产物）"}, 4)
    data = _read_json(elements_file)
    prep = _load_prepare(workdir, req_id)
    n = len(prep)
    if isinstance(data, dict) and n > 1:
        keys = set(data.keys())
        if keys == {str(i) for i in range(n)} or keys == {i for i in range(n)}:
            return {int(k): v for k, v in data.items()}
    return {0: data}


def _load_prepare(workdir, req_id):
    p = _art(workdir, "prepare_%s.json" % req_id)
    if not os.path.exists(p):
        _emit({"resultCode": "PARAM_MISSING", "stage": "merge-save", "e_code": "E32",
               "resultMsg": "缺少 prepare_%s.json，请先执行 --stage prepare" % req_id}, 5)
    return _read_json(p)


def _deep_merge(base, supplement):
    """deep merge supplement 到 base（仅叶子覆盖，容器递归）——E31 增量补提叠层（优化4）。"""
    if not isinstance(base, dict) or not isinstance(supplement, dict):
        return supplement
    out = dict(base)
    for k, v in supplement.items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = _deep_merge(out[k], v)
        else:
            out[k] = v
    return out


def _merge_nested_payload(schema, elements, offer_obj):
    """merge_nested 纯函数直调（优化1）。
    返回 (payload, meta, pending)。与 merge_nested.main 逐字节一致。"""
    template_id = schema.get("x-template", "")
    offer = offer_obj or {}
    if isinstance(offer, dict) and template_id in offer:
        offer = offer[template_id]
    elements_map = merge_nested.flatten_elements(elements)
    offer_map = merge_nested.flatten_offer(offer)
    meta, pending = {}, []
    merged = merge_nested.merge(schema, elements_map, offer_map, meta, pending=pending, mode="normal")
    return merged, meta, pending


def _derive_flat24(schema, payload):
    """derive_flat24 内联（优化1）：嵌套报文 → flat24 字段数组。与 cmd_derive_flat24 口径一致。"""
    mapping = {}
    if os.path.exists(MAPPING_FILE):
        with open(MAPPING_FILE, "r", encoding="utf-8") as f:
            mapping = json.load(f)

    def walk(node, prefix=""):
        flat = {}
        if not isinstance(node, dict):
            return flat
        for k, v in node.items():
            p2 = (prefix + "." + k) if prefix else k
            if isinstance(v, dict):
                flat.update(walk(v, p2))
            elif v not in ("", None):
                flat[p2] = v
        return flat

    flat = walk(payload)
    label_map = {}

    def collect_label(sub, prefix=""):
        for k, s in (sub.get("properties") or {}).items():
            p2 = (prefix + "." + k) if prefix else k
            lbl = s.get("x-label")
            if lbl:
                label_map[p2] = lbl
            if s.get("type") == "object":
                collect_label(s, p2)
    collect_label(schema)

    fields, used = [], set()
    for path, field_name in (mapping.get("path_to_field") or {}).items():
        val = flat.get(path)
        if val is None:
            continue
        fields.append({"field": field_name, "value": str(val), "source": "模板轨派生"})
        used.add(field_name)
    seen = set()
    for path, val in flat.items():
        if path in (mapping.get("path_to_field") or {}):
            continue
        if path == "orderNo" or path.split(".")[-1] in ("templateId", "prodId", "prodPrcId", "pricingId", "opType"):
            continue
        name = label_map.get(path) or path.split(".")[-1]
        if not name or name in used or name in seen:
            continue
        seen.add(name)
        fields.append({"field": name, "value": str(val), "source": "模板轨派生(自动)"})
    return fields


def stage_merge_save(args, req_id, workdir, state):
    _require_prior(state, "merge-save")
    prep = _load_prepare(workdir, req_id)
    elements_map = _elements_map(workdir, req_id, args.elements_file)
    threshold = args.threshold

    per_product = []
    all_pending = []
    render_parts = []
    explanations = []
    flat_counts = []
    canonical_plan_written = False

    for i, pinfo in enumerate(prep):
        template = pinfo["template"]
        elements = elements_map.get(i)
        if elements is None:
            _emit({"resultCode": "PARAM_MISSING", "stage": "merge-save", "e_code": "E31",
                   "resultMsg": "步骤④提取缺少产品 index=%d 的元素" % i}, 4)
        # E31 增量补提：--elements-supplement 与 base elements 叠层合并（优化4）
        if args.elements_supplement:
            if not os.path.exists(args.elements_supplement):
                _emit({"resultCode": "PARAM_MISSING", "stage": "merge-save", "e_code": "E31",
                       "resultMsg": "--elements-supplement 文件不存在"}, 4)
            sup = _read_json(args.elements_supplement)
            elements_map_i = sup.get(str(i), sup.get(i, sup)) if isinstance(sup, dict) else sup
            elements = _deep_merge(elements, elements_map_i)

        elements_file_local = _art(workdir, "elements_%s_%d.json" % (req_id, i))
        _write_json(elements_file_local, elements)
        schema = _read_schema(template)

        # ---- 步骤④.1 校验闸（validate_elements 纯函数直调，优化1）----
        vout = validate_elements.validate(schema, elements, mode="normal", threshold=threshold)
        if vout.get("quality_gate") == "FAIL":
            missing = vout.get("missing_required") or []
            missing_file = _art(workdir, "missing_required_%s_%d.json" % (req_id, i))
            _write_json(missing_file, missing)
            _emit({"resultCode": "VALIDATE_FAIL", "stage": "merge-save", "e_code": "E31",
                   "resultMsg": "提取质量门禁未通过（可提取必填命中率 %.4f < 阈值 %.2f）。"
                                "已落盘缺失必填清单 %s：只补缺失项写 supplement 文件后，"
                                "以 --elements-supplement 重跑本阶段（自动叠层合并，免整段重提，方案B）。" %
                                (vout["stats"]["extractable_hit_rate"], threshold, missing_file),
                   "stats": vout.get("stats"), "missing_required": missing,
                   "missing_file": missing_file,
                   "next": "run_requirement.py --stage merge-save --req-id %s --workdir <会话可写目录> "
                           "--elements-file <原elements> --elements-supplement <补提文件>" % req_id}, 4)

        # ---- 步骤⑤ merge_nested（纯函数直调，三工件落盘）----
        offer_file = pinfo.get("offer_file") or ""
        offer_obj = _read_json(offer_file) if offer_file else None
        payload, meta, pending = _merge_nested_payload(schema, elements, offer_obj)
        payload_file = _art(workdir, "payload_%s_%d.json" % (req_id, i))
        meta_file = _art(workdir, "meta_%s_%d.json" % (req_id, i))
        _write_json(payload_file, payload)
        _write_json(meta_file, meta)
        all_pending.extend(pending)

        # ---- 步骤⑤.5 嵌套本体校验闸 validate_nested + explain_nested（后端 HTTP，子进程）----
        vn_args = ["validate_nested", "--template", template, "--payload-json-file", payload_file]
        if offer_file:
            vn_args += ["--similar-offer-file", offer_file]
        ok, vnout = _api(vn_args)
        vn_pass = True
        if ok and (vnout or {}).get("pass") is not None:
            vn_pass = bool(vnout.get("pass")) or str(vnout.get("pass")) == "1"
        if not vn_pass:
            violations = (vnout or {}).get("violations") or []
            high = [v for v in violations if v.get("issueLevel") == "HIGH"]
            _emit({"resultCode": "VALIDATE_FAIL", "stage": "merge-save", "e_code": "E33",
                   "resultMsg": "嵌套本体校验未通过（存在 HIGH 或 R-C06 违反），禁止产出执行方案",
                   "violations": high or violations}, 6)
        trace_id = (vnout or {}).get("trace_id") or ""
        explanation = {}
        explain_failed = False
        if trace_id:
            eok, eout = _api(["explain_nested", "--trace-id", trace_id, "--audience", args.audience])
            if eok:
                explanation = eout or {}
            else:
                explain_failed = True  # E34 提示型：不阻断主干

        # ---- 步骤⑦ 下游兼容派生 + 保存 ----
        flat_fields = _derive_flat24(schema, payload)
        plan_json_v2 = {"req_id": req_id, "template": template, "payload": payload,
                        "_meta": meta, "pending_required": pending, "flat_fields": flat_fields}
        saved = False
        if not pending:
            plan_file = _art(workdir, "plan_json_v2_%s_%d.json" % (req_id, i))
            _write_json(plan_file, plan_json_v2)
            _, sout = _api(["save_node_result", "--req-id", req_id,
                            "--node", "requirement", "--result-json-file", plan_file])
            saved = str((sout or {}).get("resultCode", "0")) in ("0", "None", "")
            # V10.2 规范别名：flow-B（run_pipeline 环节3 起）按 plan_json_<req_id>.json 读配置原文，
            # 与 v2 按产品索引命名错位会导致环节3 读不到工件。此处取首个已保存产品同时落盘规范名，
            # 使环节2→环节3 无缝衔接（单商品即主配置原文；多商品时取第一个，统一环节3 配置对象口径）。
            if saved and not canonical_plan_written:
                _write_json(_art(workdir, "plan_json_%s.json" % req_id), plan_json_v2)
                canonical_plan_written = True

        # ---- 步骤⑥ render_table（纯函数直调，优化1）----
        similar_meta_file = _art(workdir, "similar_offer_meta_%s_%d.json" % (req_id, i))
        similar_offer = _read_json(similar_meta_file) if os.path.exists(similar_meta_file) else {}
        data = payload.get(schema.get("x-template", ""), payload)
        table_text = render_table.render(schema, data, str(pinfo.get("prodName") or ""), meta, similar_offer)

        per_product.append({"index": i, "name": pinfo.get("prodName"), "prodType": pinfo.get("prodType"),
                            "template": template, "pending_required": pending, "saved": saved,
                            "trace_id": trace_id, "explain_failed": explain_failed,
                            "payload_file": payload_file, "meta_file": meta_file,
                            "flat_field_count": len(flat_fields)})
        if table_text:
            render_parts.append(table_text)
        if explanation:
            explanations.append(explanation)
        flat_counts.append(len(flat_fields))

    # ---- 落盘全量结果工件（优化2）：reduce 模型往返 token ----
    full = {"resultCode": "0", "stage": "merge-save", "req_id": req_id,
            "exit": "1" if all_pending else "0",
            "channel": "EXIT_A" if all_pending else "EXIT_B",
            "products": per_product, "pending_required": all_pending,
            "tables": "\n\n---\n\n".join(render_parts),
            "explanations": explanations,
            "next_action": ("补充待补充字段后重跑" if all_pending
                            else "回复【确认配置】进入【销售品智能配置】")}
    result_file = _art(workdir, "requirement_result_%s.json" % req_id)
    plan_md_file = _art(workdir, "plan_md_%s.md" % req_id)
    explain_file = _art(workdir, "explain_%s.json" % req_id)
    _write_json(result_file, full)
    _write_text(plan_md_file, full["tables"])
    _write_json(explain_file, explanations)
    state["done_stages"] = list(dict.fromkeys((state.get("done_stages") or []) + ["merge-save"]))
    save_state(workdir, state)

    # ---- 紧凑摘要 stdout（优化2）----
    _emit({"resultCode": "0", "stage": "merge-save",
           "resultMsg": "需求分析完成",
           "req_id": req_id,
           "exit": full["exit"], "channel": full["channel"],
           "pending_required": all_pending,
           "products": [{"index": p["index"], "name": p["name"], "template": p["template"],
                         "pending_required": p["pending_required"], "saved": p["saved"],
                         "trace_id": p["trace_id"], "flat_field_count": p["flat_field_count"],
                         "explain_failed": p["explain_failed"],
                         "payload_file": p["payload_file"], "meta_file": p["meta_file"]}
                        for p in per_product],
           "artifacts": {"result_file": result_file, "plan_md": plan_md_file, "explain": explain_file},
           "next_action": full["next_action"]},
          (1 if all_pending else 0))


def main():
    p = argparse.ArgumentParser(description="需求分析确定性状态机（flow-A 模板轨收敛，三阶段）")
    p.add_argument("--stage", required=True, choices=STAGE_ORDER)
    p.add_argument("--req-id", required=True)
    p.add_argument("--workdir", required=True, help="会话可写目录（工件与状态落盘）")
    p.add_argument("--products-file", default="", help="步骤① LLM 识别产物（stage=products 输入）")
    p.add_argument("--requirement-file", default="", help="需求原文文本文件（stage=prepare；步骤④ 提取素材）")
    p.add_argument("--requirement-summary", default="", help="需求原文摘要（stage=prepare；免建文件）")
    p.add_argument("--elements-file", default="", help="步骤④ LLM 提取产物（stage=merge-save 输入）")
    p.add_argument("--elements-supplement", default="", help="E31 增量补提文件，与 elements 叠层合并（方案B）")
    p.add_argument("--threshold", type=float, default=0.30, help="提取质量门禁阈值（默认0.30）")
    p.add_argument("--audience", default="sales", help="explain_nested 受众（默认 sales）")
    args = p.parse_args()

    req_id, workdir = args.req_id, os.path.abspath(args.workdir)
    os.makedirs(workdir, exist_ok=True)
    state = load_state(workdir, req_id)
    if args.stage == "products":
        stage_products(args, req_id, workdir, state)
    elif args.stage == "prepare":
        stage_prepare(args, req_id, workdir, state)
    else:
        stage_merge_save(args, req_id, workdir, state)


if __name__ == "__main__":
    main()
