#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""xlsx 配置逻辑报文规范 → JSON Schema 转换器 V1.0。

把《产品配置结构化映射逻辑模型规范.xlsx》中的每个「配置逻辑报文规范」工作表
转换为**机器可执行 JSON Schema**（模板机器化，供工具取模板/渲染/校验使用）。

输出：方案/templates/<templateId>.schema.json（每个模板一个）
      + 方案/templates/_index.json（模板索引：templateId ↔ 中文名 ↔ 产品类型 ↔ 源表）

设计要点：
- 由层级列(A..F)重建 JSONPath，映射到 schema 的嵌套 properties；
- 字段 = 叶子节点；容器 = 对象节点；
- 每字段捕获 elementType / default / enum / required / description(含展示条件原文)；
- 源码中的「展示条件」为自然语言逻辑（如"月租有效期不是长期有效时显示"），
  暂以 x-show-when 注解原样保留，供后续规则层解析成 DSL。
"""
import io
import json
import os
import re
import sys
import openpyxl

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE = os.path.dirname(os.path.abspath(__file__))
XLSX = os.path.join(BASE, "产品配置结构化映射逻辑模型规范.xlsx")
OUTDIR = os.path.join(BASE, "templates")

# 源表 → (templateId, 中文模板名, 产品类型)
SHEET_TO_TEMPLATE = {
    "5.3.1.个人主资费配置逻辑报文规范": ("personMainPrc", "个人主资费", "个人主套餐"),
    "5.3.2.宽带主资费配置逻辑报文规范": ("broadBandMainPrc", "宽带主资费", "宽带主套餐"),
    "5.3.3.个人附加资费配置逻辑报文规范": ("personAddPrc", "个人附加资费", "个人附加资费"),
    "5.3.4.1.宽带加速包配置逻辑报文规范": ("broadBandOptSpeedPrc", "宽带加速包", "宽带附加资费"),
    "5.3.5.1.家庭基础套餐配置逻辑报文规范": ("familyBasePrc", "家庭基础套餐", "家庭基础套餐"),
    "5.3.5.2.家庭附加业务配置逻辑报文规范": ("familyAddPrc", "家庭附加业务", "家庭附加资费"),
}

# 层级列候选（表头含“节点”字样的列为层级列）
LEVEL_COL_NAMES = ["一级节点", "二级节点", "三级节点", "四级节点", "五级节点", "六级节点"]
FIELD_COL = "取值说明"
TYPE_COL = "元素类型"
ENUM_COL = "默认值（枚举值）"
DESC_COL = "元素说明"  # 枚举补充/展示条件常落在此列
REQ_COL = "是否必填"
SCENE_COL = "使用場景"  # 有的表叫 使用場景，有的 使用場景説明


def parse_enum(v):
    """把枚举/默认值文本解析成 enum 列表。

    源表两类枚举落点：
    1) 默认值（枚举值）列：多行枚举，可能带编号前缀（"1: xxx"）或编码后缀（"长期有效 0"）；
    2) 元素说明列（desc）：形如 "0 表示使用默认税率\\n1 表示拆分税率"、"送费\\n打折"。
    返回 (enum列表, 是否来自说明列)。说明列的枚举行须形如"编号+说明"或纯短词，
    说明性长句（含"时/才/根据/显示"等）剔除。
    """
    if v is None:
        return None, False
    text = str(v).strip()
    if text in ("无", "无默认值", "无 ", ""):
        return None, False
    return _split_enum_lines(text)


def _split_enum_lines(text):
    """按行拆枚举，剔除说明性文字；返回 (items, polluted) 。"""
    results = []
    polluted = False
    NOISE_WORDS = ("时展示", "才展示", "才显示", "时显示", "根据", "弹出", "提示",
                   "校验", "调用", "查询", "按数据模型", "按现有规则", "页面隐藏",
                   "选择后", "勾选", "不能", "前台", "后台", "右侧", "展示", "该值",
                   "再地域", "需要", "单击", "选择完成")
    for ln in text.splitlines():
        s = ln.strip()
        if not s:
            continue
        s = re.sub(r"^(默认[:：]|默认值[:：])\s*", "", s).strip()
        # 编号行："1: xxx" / "1：xxx" —— 枚举项，取编号后内容
        m_num = re.match(r"^(\d+)\s*[:：]\s*(.+)$", s)
        if m_num:
            s = m_num.group(2).strip()
        # "0 表示使用默认税率" / "长期有效 0"（说明列编码行）
        m_desc = re.match(r"^(\d+)\s+(?:表示|代指)\s*(.+)$", s)
        if m_desc:
            s = m_desc.group(2).strip()
        # 行尾孤立编码后缀："长期有效 0" → 长期有效（保留编码）
        m_code = re.match(r"^(.{2,}?)\s+(\d)$", s)
        if m_code and not re.match(r"^\d", s):
            s = m_code.group(1).strip()
        # 尾部"-2/-a/-1/-R"编码后缀（calcMode 形态）："首月免费，次月按月收取-2" → 剥离
        m_dash = re.match(r"^(.+?)\s*-[0-9a-zA-R]$", s)
        if m_dash and len(m_dash.group(1)) >= 4 and ("，" in s or "," in s):
            s = m_dash.group(1).strip()
        # "10+5(...)" 描述中冒号引导的说明行剔除（outChargeMode 列8 的连带说明）
        if s.startswith(("月租费连带", "该参数", "根据月租", "月租费>=", "月租费<")):
            polluted = True
            continue
        # 说明性文字剔除
        if any(w in s for w in NOISE_WORDS) or len(s) > 30 or s.endswith(("：", ":")):
            polluted = True
            continue
        if s in ("", "待提供", "自动生成", "按现有规则自动生成", "页面隐藏元素", "码表配置"):
            polluted = True
            continue
        if s not in results:
            results.append(s)
    return (results or None), polluted


def parse_enum_from_desc(desc):
    """从元素说明列提取枚举补充项。

    覆盖两种形态：
    1) "编号 表示/： 值"行（isSplitRate/billSmsCfg.resourceType）；
    2) 纯短词行（favType 的"送费\\n打折"、favCondition 的"优惠月末生效"）。
    说明性长句（含噪声词/长句）剔除。
    """
    if not desc:
        return None
    hits = []
    NOISE = ("时展示", "才展示", "才显示", "根据", "提示", "校验", "调用", "查询",
             "按数据", "按现有", "页面", "选择", "勾选", "不能", "显示", "弹出",
             "默认", "账户", "后台", "前台", "科", "配置")
    for ln in str(desc).splitlines():
        s = ln.strip()
        if not s:
            continue
        m = re.match(r"^(\d+)\s+(?:表示|代指)\s*(.+)$", s)
        if m:
            hits.append(m.group(2).strip())
            continue
        m = re.match(r"^(\d+)\s*[:：]\s*(.+)$", s)
        if m:
            hits.append(m.group(2).strip())
            continue
        # 纯短词行：无编号、无句读、长度适中、不含噪声 → 视为枚举项
        if (len(s) <= 20 and not any(w in s for w in NOISE)
                and not s.endswith(("：", ":", "。", "，", ",")) and "：" not in s and ":" not in s):
            hits.append(s)
    return hits or None


def parse_default(v):
    """单独抓取"默认：xxx"中的默认值。"""
    if not v:
        return None
    text = str(v).strip()
    m = re.search(r"默认[:：]\s*([^\n　]+)", text)
    return m.group(1).strip() if m else None


# 金额/数量类字段名 → number（源元素类型"文本框"未标数字，但业务语义为数值）
NUMBER_FIELD_RE = re.compile(
    r"(fixFee|prcMonthFee|favFee|fav1Fee|favDiscount|theshold|outCharge|"
    r"splitRate9|splitRate6|isSplitRate|phoneMbrFee|dlowFavFee|effNum|"
    r"chgYue|fixCircle|favValidityVAlue|fixValidityVAlue|fav1ValidityVAlue)$", re.IGNORECASE)


def type_mapping(t, raw_name=""):
    """元素类型 → JSON Schema type。金额/数量/比例类字段名优先判 number。"""
    if raw_name and NUMBER_FIELD_RE.search(field_name(raw_name) or ""):
        return "number"
    if not t:
        return "string"
    t = str(t).strip()
    if "数字" in t or "正整数" in t or "数字框" in t:
        return "number"
    return "string"


def field_name(raw):
    """与主循环一致的括号字段名提取（供 type_mapping 复用）。"""
    m = re.search(r"[（(]([A-Za-z][A-Za-z0-9_]*)[）)]", str(raw))
    return m.group(1) if m else None


def is_descriptive_enum(enum):
    """说明型 enum 判别（S3a 改造）：P0 复盘确认 6/9 enum_violation 误报源于此。

    真实封闭枚举 vs 取值说明的形态区分：
    - 说明型（降级为 x-hint，不参与枚举校验）：
      "按资费表字符长度限制" / "1-9之间正整数" / "1-6之间整数" / "默认为36" / "置灰不可修改"
      ——特征：范围描述/格式描述/约束描述，非封闭值列举；
    - 真实枚举（保留 enum）：封闭值列举（"是、否"/编号行码表/多值列举）。
    """
    if not enum:
        return False
    DESC_PATTERNS = (
        r"^\d+-\d+之间",                 # 1-9之间正整数
        r"^(正整数|整数|数字)$",          # 正整数/整数
        r"^(按|根据).*(限制|规则|模型|长度)",  # 按资费表字符长度限制
        r"^置灰",                        # 置灰不可修改
        r"^默认",                        # 默认为36
        r"^\d+个?字符",                  # 20个字符以内
        r"以内$",
        r"长度限制$",
    )
    if any(re.search(p, s) for s in enum for p in DESC_PATTERNS):
        return True
    # 单值且含约束措辞：多为取值说明（如 prodPrcName 的"按资费表字符长度限制"）
    if len(enum) == 1 and any(w in enum[0] for w in ("限制", "之间", "以内", "长度", "规则", "说明")):
        return True
    return False


def main_convert():
    os.makedirs(OUTDIR, exist_ok=True)
    wb = openpyxl.load_workbook(XLSX, data_only=True)
    index = {"version": "V1.0", "templates": []}

    for sheet_name, (tid, cname, ptype) in SHEET_TO_TEMPLATE.items():
        if sheet_name not in wb.sheetnames:
            print("[跳过] 缺表:", sheet_name)
            continue
        ws = wb[sheet_name]
        rows = list(ws.iter_rows(values_only=True))

        # 定位表头行：含 一级节点
        header_idx = None
        for i, row in enumerate(rows):
            if any(col and "一级节点" in str(col) for col in row[:8]):
                header_idx = i
                break
        if header_idx is None:
            print("[跳过] 找不到表头:", sheet_name)
            continue
        header = [str(c) if c is not None else "" for c in rows[header_idx]]
        # 层级列索引
        level_cols = []
        field_col = type_col = enum_col = desc_col = req_col = scene_col = None
        for col_idx, h in enumerate(header):
            h = h.strip()
            if h in LEVEL_COL_NAMES:
                level_cols.append(col_idx)
            elif h == FIELD_COL:
                field_col = col_idx
            elif h == TYPE_COL:
                type_col = col_idx
            elif h == ENUM_COL:
                enum_col = col_idx
            elif h == DESC_COL:
                desc_col = col_idx
            elif h and "必填" in h:
                req_col = col_idx
            elif h and "使用場景" in h:
                scene_col = col_idx

        # 迭代建树：path = [(显示名, field名), ...]
        root = {"type": "object", "properties": {}, "x-template": tid, "x-label": cname}
        data_rows = rows[header_idx + 1:]

        FIELD_NAME_ALIAS = {"templateId": "orderNo"}  # V1.1：工单编号字段 baseInfo.templateId → orderNo（schema 已改名，xlsx 源表未同步）

        def field_name(raw):
            # 提取全角/半角括号内拉丁字段名；无括号则用中文名驼峰化
            m = re.search(r"[（(]([A-Za-z][A-Za-z0-9_]*)[）)]", str(raw))
            if m:
                return FIELD_NAME_ALIAS.get(m.group(1), m.group(1))
            if not raw:
                return None
            base = re.sub(r"^(模板编码|二批代码|资费代码|产品代码|拆分代码)\s*", "", str(raw))
            base = re.sub(r"[:：（）()]", "", base)
            return "f_" + base

        # 合并单元格前向填充（父级祖先空档补前值），重建完整层级路径
        ffill = [""] * len(level_cols)
        payload_seen = False  # 遇到 {} 载荷根标记后才进入 payload 树
        for row in data_rows:
            raw_levels = []
            for ci in level_cols:
                v = row[ci]
                if v is not None and str(v).strip():
                    raw_levels.append(str(v).strip())
                else:
                    raw_levels.append("")
            # 前向填充祖先层
            for i, v in enumerate(raw_levels):
                if v:
                    ffill[i] = v
                else:
                    raw_levels[i] = ffill[i]
            # 载荷根标记：{} 之前是 ROOT.BODY/BUSI_INFO 信封（opType/templateId 元信息），整段跳过
            if not payload_seen:
                if raw_levels and raw_levels[0] == "{}":
                    payload_seen = True
                else:
                    continue
            # 去掉 {} 载荷根标记
            if raw_levels and raw_levels[0] == "{}":
                raw_levels = raw_levels[1:]
            # 去掉模板中文名容器根（形如"个人主资费（）"）
            while raw_levels and "（）" in raw_levels[0]:
                raw_levels = raw_levels[1:]
            # 跳过空行
            if not raw_levels or not any(x for x in raw_levels):
                continue
            # 去掉尾部空层级（列数大于实际层级深度）
            while raw_levels and not raw_levels[-1]:
                raw_levels.pop()

            desc = str(row[field_col]) if field_col is not None and row[field_col] else ""

            # 客户端构建路径
            cur = root
            path_ok = True
            for i, lv in enumerate(raw_levels):
                is_leaf = (i == len(raw_levels) - 1)
                key = field_name(lv)
                if not key:
                    path_ok = False
                    break
                if is_leaf:
                    break
                node = cur["properties"].get(key)
                if node is None:
                    node = {"type": "object", "properties": {}, "x-label": re.sub(r"（.*）", "", lv)}
                    cur["properties"][key] = node
                cur = node
            if not path_ok:
                continue

            # 叶子字段
            leaf_key = field_name(raw_levels[-1])
            element_type = str(row[type_col]).strip() if type_col is not None and row[type_col] else ""
            enum_raw = str(row[enum_col]) if enum_col is not None and row[enum_col] else ""
            desc_text = str(row[desc_col]) if desc_col is not None and row[desc_col] else ""
            required_raw = str(row[req_col]).strip() if req_col is not None and row[req_col] else ""
            scene = str(row[scene_col]).strip() if scene_col is not None and row[scene_col] else ""

            enum, polluted = parse_enum(enum_raw)
            # 默认值列的枚举若只有1项且说明列另有"编号 表示 值"枚举 → 合并（favType/isSplitRate 形态）
            desc_enum = parse_enum_from_desc(desc_text)
            if desc_enum:
                enum = list(dict.fromkeys((enum or []) + desc_enum)) or enum
            default = parse_default(enum_raw)
            prop = {
                "type": type_mapping(element_type, raw_levels[-1]),
                "x-label": re.sub(r"[（(][A-Za-z][A-Za-z0-9_]*[）)]", "", raw_levels[-1]).strip(),
                "x-element": element_type or "text",
                "description": desc or desc_text,
            }
            if scene:
                prop["x-scene"] = scene
            # S3a：说明型 enum 降级为 x-hint（保留提示语义，不参与枚举校验）
            if enum and is_descriptive_enum(enum):
                prop["x-hint"] = "、".join(enum)
            elif enum:
                prop["enum"] = enum
            if default:
                prop["default"] = default
            if required_raw and required_raw != "否":
                prop["x-required"] = True

            # 展示条件：优先从元素说明列提取（源表条件大多落此列），其次取值说明列
            cond = extract_condition(desc_text) or extract_condition(desc)
            if cond:
                prop["x-show-when"] = cond

            cur["properties"][leaf_key] = prop

        # 计算 required 列表（x-required 字段）
        def annotate_required(obj):
            if obj.get("type") == "object":
                reqs = [k for k, v in obj["properties"].items()
                        if v.get("x-required")]
                if reqs:
                    obj["required"] = reqs
                for v in obj["properties"].values():
                    annotate_required(v)
        annotate_required(root)

        # 写文件
        out_path = os.path.join(OUTDIR, tid + ".schema.json")
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(root, f, ensure_ascii=False, indent=2)

        index["templates"].append({
            "templateId": tid,
            "name_cn": cname,
            "product_type": ptype,
            "source_sheet": sheet_name,
            "schema_file": tid + ".schema.json",
            "leaf_count": count_leafs(root),
        })
        print("[OK]", tid, "|", cname, "| leafs:", count_leafs(root))

    index_path = os.path.join(OUTDIR, "_index.json")
    with open(index_path, "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=2)
    print("索引:", index_path)
    emit_registry(index, OUTDIR)


def extract_condition(desc):
    """从描述中提取展示条件（自然语言），覆盖：才展示/才显示/时展示/时才展示/时显示/选择…时展示。"""
    if not desc:
        return None
    m = re.search(r"([^。#\n]*?(?:才展示|才显示|时展示|时才展示|时显示|才显示，|时才显示)[^。#\n]*)", desc)
    if m:
        return m.group(1).strip().rstrip("，,；;")
    return None


def count_leafs(obj):
    if obj.get("type") != "object":
        return 1
    return sum(count_leafs(v) for v in obj["properties"].values())


# ---------------- S1b 模板注册表生成（--emit-registry） ----------------

# 价格字段 x-label 关键词（与 merge_nested.is_price_field 口径一致）
PRICE_KEYWORDS = ("档位", "月功能费", "月租", "月费", "固定费", "费用")


def walk_registry(schema, path, price_paths, enum_total, show_when, stats):
    """递归统计：价格字段路径表 / enum 总数 / x-show-when 计数 / 必填数 / number 数。"""
    for key, v in (schema.get("properties") or {}).items():
        p = (path + "." + key) if path else key
        if v.get("type") == "object":
            walk_registry(v, p, price_paths, enum_total, show_when, stats)
            continue
        stats["leafs"] += 1
        label = v.get("x-label", "")
        if any(kw in label for kw in PRICE_KEYWORDS):
            price_paths.append({"path": p, "label": label, "type": v.get("type")})
        if v.get("enum"):
            enum_total[0] += len(v["enum"])
        if v.get("x-show-when"):
            show_when[0] += 1
        if v.get("x-required"):
            stats["required"] += 1
        if v.get("type") == "number":
            stats["numbers"] += 1


def emit_registry(index, outdir):
    """从 templates 目录 schema 自动统计生成注册表 JSON（供 references/templates-registry.md 数据源）。
    禁止手写数字：一切统计值来自 schema 本体。"""
    registry = {"version": index.get("version", "V1.0"), "templates": []}
    for t in index["templates"]:
        sf = os.path.join(outdir, t["schema_file"])
        with open(sf, "r", encoding="utf-8") as f:
            schema = json.load(f)
        price_paths, enum_total, show_when, stats = [], [0], [0], {"leafs": 0, "required": 0, "numbers": 0}
        walk_registry(schema, "", price_paths, enum_total, show_when, stats)
        registry["templates"].append({
            "templateId": t["templateId"],
            "name_cn": t["name_cn"],
            "product_type": t["product_type"],
            "source_sheet": t["source_sheet"],
            "schema_file": t["schema_file"],
            "leaf_count": stats["leafs"],
            "required_count": stats["required"],
            "number_count": stats["numbers"],
            "enum_total": enum_total[0],
            "show_when_count": show_when[0],
            "price_fields": price_paths,
        })
    out = os.path.join(outdir, "_registry.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(registry, f, ensure_ascii=False, indent=2)
    print("注册表:", out)
    return registry


def main_registry():
    import argparse
    ap = argparse.ArgumentParser(description="xlsx 配置逻辑报文规范 → JSON Schema 转换器")
    ap.add_argument("--emit-registry", action="store_true",
                    help="从 templates/*.schema.json 统计生成 _registry.json（不重跑 xlsx 转换）")
    args = ap.parse_args()

    if args.emit_registry:
        index_path = os.path.join(OUTDIR, "_index.json")
        with open(index_path, "r", encoding="utf-8") as f:
            index = json.load(f)
        reg = emit_registry(index, OUTDIR)
        for t in reg["templates"]:
            print("[OK]", t["templateId"], "| leafs:", t["leaf_count"],
                  "| required:", t["required_count"], "| enum:", t["enum_total"],
                  "| show-when:", t["show_when_count"], "| price:", len(t["price_fields"]))
        return


def main():
    main_registry()
    if "--emit-registry" in sys.argv:
        return
    main_convert()

if __name__ == "__main__":
    main()
