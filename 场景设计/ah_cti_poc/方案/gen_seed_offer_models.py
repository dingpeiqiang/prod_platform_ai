#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""存量产品销售品逻辑模型报文离线生成器（V1.0）。

背景：需求分析（wf_sub_01）模板轨要求 query_similar_offer 出参为「模板同构逻辑模型报文」，
但后端 seed_offers.json 只存扁平资费规则、无现成嵌套实例。本脚本一次性把每个存量销售品
对 6 套模板分别实例化为逻辑模型报文，产物 seed_offer_models.json 作为后端资源直接读取，
匹配后直接获取报文，不在运行期实时生成。

实例化规则（与方案/merge_nested.py 口径一致）：
1. 以 schema 为骨架递归，schema 有而 seed 无的叶子建键、值为 ""；
2. seed 字段 → schema 叶子按 x-label 对位（含常见别名，见 LABEL_ALIAS）；
3. schema default → x-default-rule（system_date=当期日期、2099-12-31）兜底；
4. 价格字段（档位/月功能费/月租/月费/固定费/费用）是存量产品自身资费，正常回填；
5. 输出 {"version","generated_at","models":{offer_id:{templateId:report}}}。
"""
import datetime
import io
import json
import os
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.abspath(__file__))
TEMPLATE_DIR = os.path.join(ROOT, "templates")
SEED_FILE = os.path.join(
    ROOT, "..", "..", "..", "backend-app", "src", "main", "resources", "appstore", "seed_offers.json"
)
OUT_FILE = os.path.join(
    ROOT, "..", "..", "..", "backend-app", "src", "main", "resources", "appstore", "seed_offer_models.json"
)

TEMPLATES = [
    "personMainPrc",
    "broadBandMainPrc",
    "personAddPrc",
    "broadBandOptSpeedPrc",
    "familyBasePrc",
    "familyAddPrc",
]

# schema x-label → 值抽取口径（缺省直接取 seed 同名字段）。值为可调用(offer)->str，或 seed 键路径。
# 只覆盖核心资费/规则向叶子；模板特有字段（带宽/域名/成员关系）seed 无数据则留空。
LABEL_RESOLVERS = {
    "资费名称": lambda o: o.get("offer_name", ""),
    "套餐月费": lambda o: _fee_text(o),
    "套餐固定费（元）": lambda o: _fee_num(o),
    "包含资源": lambda o: _contain_resource(o),
    "超套收费标准": lambda o: _charge_standard(o),
    "限定条件": lambda o: _limit_condition(o),
    "其他权益": lambda o: _other_equity(o),
    "订购生效方式": lambda o: "立即生效、预约生效",
    "退订生效方式": lambda o: "立即生效、预约生效",
    "销售开始日期": None,  # x-default-rule 兜底
    "销售终止日期": None,
    "发布渠道": lambda o: "、".join(o.get("sale_channels") or []) or "实体渠道、电子渠道、直销渠道",
    "发布地市": lambda o: "全省",
    "发布地市明细": lambda o: "",
    "收费方式": lambda o: "首月按天折算，一次性收取，次月按月收取",
    "收费科目": lambda o: "套餐费",
    "税率": lambda o: "使用默认税率",
    "月租有效期": lambda o: "长期有效",
    "优惠条件": lambda o: "无条件优惠（实时优惠）",
    "优惠科目类型": lambda o: "全科目(不包含特殊科目）",
    "优惠类型": lambda o: "送费",
    "优惠有效期": lambda o: "长期有效",
    "成功短信（立即）": lambda o: "订购成功，立即生效",
    "成功短信（预约）": lambda o: "预约成功，次月生效",
    "退订短信": lambda o: "退订成功，次月失效",
    "前台二确短信": lambda o: "前台二次确认订购",
    "短信内容": lambda o: "0",
    # 资源配置
    "流量类型": lambda o: "国内-通用-单月",
    "资源量": lambda o: "",  # 由 _resource_leaf 按上下文判定（流量/语音/短信）
    "流量单位": lambda o: "GB",
    "套外计费标准": lambda o: _gprs_out_charge(o),
    "流量提醒": lambda o: "提醒",
    "是否结转到次月": lambda o: "是" if o.get("flow_carry_over") else "否",
    "资源类型": lambda o: "",
    "套外计费费率": lambda o: "",
    "首月资源赠送方式": lambda o: "全额赠送流量",
    "是否提醒": lambda o: "否",
    # 宽带
    "下行带宽": lambda o: "",
    "上行带宽": lambda o: "",
    "域名": lambda o: "",
    "是否允许加入家庭": lambda o: "否",
    "话单优惠": lambda o: "是",
    # 固定枚举叶子
    "0000统一查询退订": lambda o: "是",
    "重复订购重复收费": lambda o: "否",
    "允许重复订购数量": lambda o: "1",
    "是否5G资费": lambda o: "是" if o.get("series") == "5g_a" else "否",
}

# 资源量分派：按叶子所在配置段决定取流量/语音/短信 seed 值
RESOURCE_BY_SECTION = {
    "billGprsCfg": lambda o: _num_only((o.get("in_fee") or {}).get("国内通用流量")),
    "billVoiceCfg": lambda o: _num_only((o.get("in_fee") or {}).get("国内语音拨打")),
    "billSmsCfg": lambda o: _sms_num(o),
}


def _num_only(text):
    import re
    m = re.search(r"\d+", str(text or ""))
    return m.group(0) if m else ""


def _fee_num(o):
    import re
    m = re.search(r"\d+(?:\.\d+)?", str(o.get("monthly_fee") or ""))
    return m.group(0) if m else ""


def _fee_text(o):
    fee = _fee_num(o)
    return (fee + "元/月") if fee else ""


def _sms_num(o):
    in_fee = o.get("in_fee") or {}
    sat = str(in_fee.get("卫星权益") or "")
    import re
    m = re.search(r"(\d+)条", sat)
    return m.group(1) if m else ""


def _contain_resource(o):
    in_fee = o.get("in_fee") or {}
    parts = []
    if in_fee.get("国内通用流量"):
        parts.append("国内通用流量" + str(in_fee["国内通用流量"]))
    if in_fee.get("国内语音拨打"):
        parts.append("国内语音" + str(in_fee["国内语音拨打"]))
    if in_fee.get("网络权益"):
        parts.append(str(in_fee["网络权益"]))
    return "；".join(parts)


def _charge_standard(o):
    out_fee = o.get("out_fee") or {}
    if out_fee.get("套外流量"):
        return str(out_fee["套外流量"])
    return ""


def _gprs_out_charge(o):
    out_fee = o.get("out_fee") or {}
    return str(out_fee.get("套外流量") or "")


def _limit_condition(o):
    return str(o.get("validity") or "")


def _other_equity(o):
    in_fee = o.get("in_fee") or {}
    return str(in_fee.get("卫星权益") or "")


def _today():
    return datetime.date.today().strftime("%Y%m%d")


def build_report(schema, offer):
    """schema 骨架递归 + seed 按 x-label 回填。"""
    def walk(node, path):
        out = {}
        for key, sub in (node.get("properties") or {}).items():
            cur = (path + "." + key) if path else key
            if sub.get("type") == "object":
                out[key] = walk(sub, cur)
                continue
            out[key] = resolve_leaf(sub, cur, offer)
        return out
    return walk(schema, "")


def resolve_leaf(sub, path, offer):
    label = sub.get("x-label", "")
    if sub.get("x-default-rule"):
        rule = sub["x-default-rule"]
        return _today() if rule == "system_date" else rule
    # 资源量按配置段分派
    tail_section = path.split(".")[-2] if "." in path else ""
    if label == "资源量":
        resolver = RESOURCE_BY_SECTION.get(tail_section)
        if resolver:
            val = resolver(offer)
            if val:
                return val
    if label in LABEL_RESOLVERS:
        fn = LABEL_RESOLVERS[label]
        if fn is not None:
            val = fn(offer)
            if val:
                return val
    if sub.get("default") is not None:
        return sub["default"]
    return ""


def main():
    with io.open(SEED_FILE, "r", encoding="utf-8") as f:
        seed = json.load(f)
    schemas = {}
    for t in TEMPLATES:
        with io.open(os.path.join(TEMPLATE_DIR, t + ".schema.json"), "r", encoding="utf-8") as f:
            schemas[t] = json.load(f)

    models = {}
    for offer in seed.get("offers", []):
        oid = str(offer.get("offer_id"))
        models[oid] = {t: build_report(schemas[t], offer) for t in TEMPLATES}

    result = {
        "version": "V1.0",
        "generated_at": datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "source": "seed_offers.json × 6 模板 schema 离线实例化",
        "models": models,
    }
    with io.open(OUT_FILE, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print("written:", OUT_FILE)
    print("offers:", len(models), "templates:", len(TEMPLATES),
          "reports:", len(models) * len(TEMPLATES))


if __name__ == "__main__":
    main()
