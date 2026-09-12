# -*- coding: utf-8 -*-
"""
K4 存量销售品资料库切片生成脚本
输入：方案\产品信息.txt（18 个销售品，Tab 分隔 ID/名称/内容）
输出：knowledge\K4存量\ 下 18 个 .md 文档
拆分规则（细化设计 4.2.3）：
  - 一级拆分：按销售品 ID 拆分为 18 个独立文档
  - 二级拆分：5G-A 系列（10 个）按 12 章节切片；权益随心选系列（8 个）按 2 章节切片
  - 每个切片首行元信息：[销售品ID] [销售品名称] [章节名]
"""
import os
import re

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(BASE, "方案", "产品信息.txt")
OUT = os.path.join(BASE, "knowledge", "K4存量")

# 5G-A 系列 12 章节切片定义：(切片名, 定位正则列表 - 命中任一即作为该章节起点)
G5A_SECTIONS = [
    ("套内资费",       [r"（一）套餐内资费档位"]),
    ("套外资费",       [r"（二）套餐外资费"]),
    ("过渡期资费",     [r"（三）过渡期资费"]),
    ("副卡",           [r"（四）副卡"]),
    ("流量结转",       [r"（五）流量结转规则"]),
    ("断网授权",       [r"（六）断网授权"]),
    ("停机规则",       [r"（七）停机规则"]),
    ("计费周期与付费方式", [r"（八）计费周期"]),
    ("销售渠道",       [r"三、销售渠道"]),
    ("套餐订购",       [r"四、套餐订购"]),
    ("套餐变更",       [r"五、套餐变更"]),
    ("退订拆机携出",   [r"六、套餐退订、拆机、携出"]),
]

# 权益随心选系列章节切片
EQ_SECTIONS = [
    ("资费内容", [r"（一）资费内容"]),
    ("业务规则", [r"（二）业务规则"]),
]


def parse_source(path):
    """解析产品信息.txt：每行 ID\t名称\t内容"""
    offers = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n").rstrip("\r")
            if not line.strip():
                continue
            parts = line.split("\t", 2)
            if len(parts) != 3:
                continue
            offer_id, name, content = parts[0].strip(), parts[1].strip(), parts[2].strip()
            offers.append((offer_id, name, content))
    return offers


def find_pos(content, patterns):
    """返回最早命中位置及匹配文本，找不到返回 (None, None)"""
    best_pos, best_pat = None, None
    for pat in patterns:
        m = re.search(pat, content)
        if m and (best_pos is None or m.start() < best_pos):
            best_pos, best_pat = m.start(), pat
    return best_pos, best_pat


def split_sections(content, sections_def):
    """
    按章节定义切分内容。
    sections_def: [(切片名, [正则...]), ...]
    返回 [(切片名, 文本)]，正文按锚点先后排序；首锚点之前的内容并入上一片段或首个切片。
    """
    marks = []  # (pos, slice_name)
    for name, pats in sections_def:
        pos, _ = find_pos(content, pats)
        if pos is not None:
            marks.append((pos, name))
    marks.sort()
    if not marks:
        return [(sections_def[0][0] if sections_def else "全文", content)]
    slices = []
    if marks[0][0] > 0:
        # 锚点前有内容（如无编号前言），归入"总述"
        slices.append(("总述", content[: marks[0][0]]))
    for i, (pos, name) in enumerate(marks):
        end = marks[i + 1][0] if i + 1 < len(marks) else len(content)
        slices.append((name, content[pos:end]))
    return slices


def write_offer(offer_id, name, slices):
    """写单个销售品文档，文件名 K4存量_产品信息{ID}_V1.0.md"""
    fname = "K4存量_产品信息{}_V1.0.md".format(offer_id)
    fpath = os.path.join(OUT, fname)
    lines = []
    lines.append("# 销售品资料：{}（{}）".format(name, offer_id))
    lines.append("")
    lines.append("> 来源：《产品信息.txt》 | 分类：K4 存量销售品资料库 | 版本：V1.0")
    lines.append("> 用途边界：仅用于 AI 补全字段参照、需求样例改写测试、测试预期值（presetValue）人工核对基准；不得作为新需求字段来源覆盖用户原始需求。")
    lines.append("")
    for slice_name, text in slices:
        text = text.strip()
        if not text:
            continue
        lines.append("[{}] [{}] [{}]".format(offer_id, name, slice_name))
        lines.append("")
        lines.append(text)
        lines.append("")
    with open(fpath, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    return fname, len([s for s in slices if s[1].strip()])


def main():
    offers = parse_source(SRC)
    print("解析到 {} 个销售品".format(len(offers)))
    summary = []
    for offer_id, name, content in offers:
        # 系列判定：含"权益随心选"为权益系列，否则按 5G-A 12 章节拆分
        if "权益随心选" in name:
            slices = split_sections(content, EQ_SECTIONS)
        else:
            slices = split_sections(content, G5A_SECTIONS)
        fname, n = write_offer(offer_id, name, slices)
        summary.append((offer_id, name, fname, n))
        print("  {} {} -> {} （{} 个切片）".format(offer_id, name, fname, n))
    print("\n共生成 {} 个文档".format(len(summary)))


if __name__ == "__main__":
    main()
