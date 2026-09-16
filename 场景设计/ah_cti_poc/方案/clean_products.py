#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""存量产品数据清洗器 V1.0：把 产品信息.txt 清洗成规整目录 JSON。

规则见 方案/存量产品数据清洗规则.md（C1~C15）。可重复执行、可审计。
输出：方案/存量产品目录_清洗后.json + 清洗报告（stdout）。
"""
import io
import json
import os
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

BASE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(BASE, "产品信息.txt")
OUT = os.path.join(BASE, "存量产品目录_清洗后.json")

TIER_RE = re.compile(r"(\d+(?:\.\d+)?)元")
MEMBER_KW = [("宽带", "宽带"), ("天翼高清", "天翼高清"), ("副卡", "副卡功能费")]


def clean_name(raw):
    name = re.sub(r"\s+", "", raw)
    name = name.replace("（", "(").replace("）", ")")
    return name


def detect_type(name, body):
    if "融合" in name or ("天翼高清" in body and "宽带" in body):
        return "融合套餐"
    if "权益随心选" in name:
        return "权益包"
    return "单品套餐"


def detect_series(name):
    if "权益随心选" in name:
        return "权益随心选"
    if "5G-A" in name:
        return "5G-A主套餐"
    return "未知系列"


def detect_version(body):
    if "22号" in body or "优化5G-A" in body:
        return "22号文"
    if "6号" in body:
        return "6号文"
    return "其他"


def detect_members(body):
    members = []
    for kw, role in MEMBER_KW:
        if kw in body:
            members.append(role)
    return members


def map_template(ptype):
    return {
        "融合套餐": "familyBasePrc",
        "权益包": "personAddPrc",
        "单品套餐": "personMainPrc",
    }.get(ptype, "")


def main():
    with open(SRC, "r", encoding="utf-8") as f:
        raw = f.read()
    lines = [l for l in raw.splitlines() if l.strip()]

    records = []
    for idx, line in enumerate(lines, start=1):
        parts = line.split("\t")
        if len(parts) < 3:
            print("[脏行] 行%d 列数不足: %r" % (idx, line[:40]))
            continue
        offer_id, name_orig, body = parts[0], parts[1], parts[2]
        name_clean = clean_name(name_orig)
        ptype = detect_type(name_clean, body)
        rec = {
            "offer_id": offer_id,
            "name_orig": name_orig,
            "name_clean": name_clean,
            "product_type": ptype,
            "biz_series": detect_series(name_clean),
            "tier": TIER_RE.search(name_clean).group(1) + "元" if TIER_RE.search(name_clean) else "",
            "template": map_template(ptype),
            "desc_version": detect_version(body),
            "members": detect_members(body) if ptype == "融合套餐" else [],
            "source_line": idx,
            "status": "active",
            "duplicate_of": "",
        }
        records.append(rec)

    # C11~C13 同名去重：同 name_clean 归一族，22号文更全 → active
    by_name = {}
    for r in records:
        by_name.setdefault(r["name_clean"], []).append(r)
    dup_groups = []
    for name, group in by_name.items():
        if len(group) <= 1:
            continue
        active = None
        for r in group:
            if r["desc_version"] == "22号文":
                active = r
                break
        if active is None:
            active = sorted(group, key=lambda x: -len(str(x))) [0]
        for r in group:
            if r is active:
                r["status"] = "active"
            else:
                r["status"] = "dup"
                r["duplicate_of"] = active["offer_id"]
        dup_groups.append({"name_clean": name, "active": active["offer_id"],
                           "dups": [r["offer_id"] for r in group if r is not active]})

    records.sort(key=lambda r: r["offer_id"])
    catalog = {"version": "V1.0", "source": "方案/产品信息.txt",
               "total": len(records),
               "dup_groups": dup_groups,
               "products": records}

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)

    print("=== 清洗报告 ===")
    print("总记录数:", catalog["total"])
    from collections import Counter
    print("product_type:", dict(Counter(r["product_type"] for r in records)))
    print("biz_series:", dict(Counter(r["biz_series"] for r in records)))
    print("desc_version:", dict(Counter(r["desc_version"] for r in records)))
    print("去重组数:", len(dup_groups), dup_groups)
    active = [r for r in records if r["status"] == "active"]
    print("active 规范记录:", len(active))
    print("输出:", OUT)


if __name__ == "__main__":
    main()
