# -*- coding: utf-8 -*-
"""模板轨本地冒烟：对 6 模板逐一验证 render_table / derive_flat24 可用且幂等（不依赖后端、不依赖真实存量）。

说明（重要）：
- 真实 K5 存量报文仅覆盖 familyBasePrc / personMainPrc / personAddPrc 三模板。
- broadBandMainPrc（宽带主资费）/ broadBandOptSpeedPrc（宽带加速包）/ familyAddPrc（家庭附加业务）
  在本 POC 数据源（产品信息.txt / 存量产品目录）中**无存量产品事实数据**，无法推演真实报文。
- 为验证这 3 模板的 schema 与工具链可正常渲染/派生，此处用**模拟示例报文（非存量、仅供冒烟）**
  覆盖，明确标注，**不进入 references/K5存量报文 检索主源**。
- 冒烟断言：render_table 非空输出（含概览卡片/分节表）且逐字节幂等；derive_flat24 返回 0 且出参键完整。
"""
import hashlib
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PY = sys.executable
TEMPLATES = os.path.join(HERE, "templates")
MAPPING = os.path.join(HERE, "..", "references", "ontology-fields.json")

# ---- 模拟示例报文（非存量，仅 3 个无存量模板）----
EXAMPLES = {
    "broadBandMainPrc": {
        "baseInfo": {
            "prodPrcName": "示例-家庭宽带1000M主资费", "effRuleId": "立即生效",
            "cancelRuleId": "预约生效", "effDate": "20260901", "expDate": "20991231",
            "domainName": "家庭宽带", "downStreamId": "1000M", "upStreamId": "100M",
            "ifAddFamily": "是", "prodId": "EXAMPLE_BB0001", "prodPrcId": "EXAMPLE_BBPRC0001",
            "orderNo": "EXAMPLE_ORDER0001",
        },
        "releaseInfo": {
            "chnClassLimit": "实体渠道、电子渠道、直销渠道", "groupId": "全省", "groupIdMessage": "22个地市",
        },
        "optionalInfo": {
            "printContent": {
                "prcMonthFee": 100, "containResource": "1000M下行、100M上行",
                "chargeStandard": "超出提速档按标准资费计收", "limitCondition": "示例限定条件",
                "otherEquity": "示例权益说明",
            },
            "prcSmsCfg": {"opType": "成功短信", "sysNoteNow": "您已成功订购宽带主资费",
                          "sysNoteNext": "您已成功预约订购宽带主资费",
                          "sysNoteCancle": "您已成功退订宽带主资费", "sysNoteErke": "请确认订购宽带主资费"},
            "acctMonth": {"calcMode": "周期型缴费：月", "fixFee": 100, "acctItem": "月租费",
                          "isSplitRate": "使用默认税率", "splitRate9": "", "splitRate6": "",
                          "fixValidity": "长期有效", "fixValidityVAlue": ""},
            "acctFav": {"favCondition": "无条件优惠（实时优惠）", "favItemType": "全科目(不包含特殊科目）",
                        "favGroup": "", "favType": "送费", "favDiscount": "", "favFee": 10,
                        "favValidity": "长期有效", "favValidityVAlue": "", "favDesc": "示例优惠说明"},
            "wkBillCfg": {"wkBillFlag": "是"},
        },
    },
    "broadBandOptSpeedPrc": {
        "baseInfo": {
            "prodPrcName": "示例-家庭宽带上行提速包", "effRuleId": "立即生效",
            "cancelRuleId": "长期有效", "prcEffCycle": "相对时间", "prcEffType": "月", "prcEffNum": 12,
            "prcEffDate": "", "effDate": "20260901", "expDate": "20991231",
            "downStreamId": "1000M", "upStreamId": "300M", "prodId": "EXAMPLE_BBOPT0001",
            "prodPrcId": "EXAMPLE_BBOPTPRC0001", "orderNo": "EXAMPLE_ORDER0002",
        },
        "releaseInfo": {
            "chnClassLimit": "实体渠道、电子渠道", "groupId": "全省", "groupIdMessage": "22个地市",
        },
        "optionalInfo": {
            "printContent": {
                "prcMonthFee": 20, "containResource": "上行提速至300M，有效期12个月",
                "chargeStandard": "超出按提速档计收", "limitCondition": "示例限定条件", "otherEquity": "",
            },
            "prcSmsCfg": {"opType": "成功短信", "sysNoteNow": "您已成功订购宽带加速包",
                          "sysNoteNext": "您已成功预约订购宽带加速包",
                          "sysNoteCancle": "您已成功退订宽带加速包",
                          "sysNoteExpire": "您订购的宽带加速包即将到期", "sysNoteErke": "请确认订购宽带加速包"},
            "acctMonth": {"calcMode": "首月全额收取，次月按月收取", "fixFee": 20, "acctItem": "月租费",
                          "isSplitRate": "使用默认税率", "splitRate9": "", "splitRate6": "",
                          "fixValidity": "长期有效", "fixValidityVAlue": ""},
            "acctFav": {"favCondition": "无条件优惠（实时优惠）", "favItemType": "全科目(不包含特殊科目）",
                        "favGroup": "", "favType": "送费", "favDiscount": "", "favFee": 5,
                        "favValidity": "长期有效", "favValidityVAlue": "", "favDesc": "示例优惠说明"},
        },
    },
    "familyAddPrc": {
        "baseInfo": {
            "prodType": "共享流量产品", "prodPrcName": "示例-家庭共享流量包10GB", "effRuleId": "立即生效",
            "cancelRuleId": "预约生效", "prcEffCycle": "长期有效", "prcEffType": "", "prcEffNum": "",
            "prcEffDate": "", "effDate": "20260901", "expDate": "20991231", "effNum": 1,
            "reFeeFlag": "否", "canBaseFamilyPrc": "5G-A融合套餐199元", "familyUpIfEnd": "否",
            "chgYueIfCanDown": "否", "chgYue": 12, "prodId": "EXAMPLE_FAMADD0001", "pricingId": "",
            "prodPrcId": "EXAMPLE_FAMADDPRC0001", "orderNo": "EXAMPLE_ORDER0003",
        },
        "releaseInfo": {
            "chnClassLimit": "实体渠道、电子渠道、直销渠道", "groupId": "全省", "groupIdMessage": "22个地市",
        },
        "optionalInfo": {
            "printContent": {
                "prcMonthFee": 10, "containResource": "家庭共享流量10GB",
                "chargeStandard": "超出按套外流量计收", "limitCondition": "示例限定条件", "otherEquity": "",
            },
            "prcSmsCfg": {"opType": "成功短信", "sysNoteNow": "您已成功订购家庭共享流量包",
                          "sysNoteNext": "", "sysNoteCancle": "您已成功退订家庭共享流量包",
                          "sysNoteErke": "请确认订购家庭共享流量包"},
            "equityContent": {"kdEquity": "", "tvEquity": ""},
            "acctMonth": {"calcMode": "首月全额收取，次月按月收取", "fixCircle": "", "fixFee": 10,
                          "acctItem": "月租费", "isSplitRate": "使用默认税率", "splitRate9": "",
                          "splitRate6": "", "fixValidity": "长期有效", "fixValidityVAlue": ""},
            "acctFav": {"favCondition": "无条件优惠（实时优惠）", "favItemType": "全科目(不包含特殊科目）",
                        "favGroup": "", "favType": "送费", "favDiscount": "", "favFee": 5,
                        "favValidity": "长期有效", "favValidityVAlue": "", "favDesc": "示例优惠说明"},
            "billGprsCfg": {"prodPrc": "", "billPrcId": "", "subBillPrcIds": "", "conditionCode": "",
                            "subConditionCodes": "", "resourceType": "国内-通用-单月", "theshold": 10,
                            "unit": "GB", "adjust": "全额赠送流量", "carryFlag": "是", "remindFlag": "提醒",
                            "shareFlag": "是"},
            "billVoiceCfg": {"prodPrc": "", "billPrcId": "", "subBillPrcIds": "", "conditionCode": "",
                             "subConditionCodes": "", "resourceType": "国内主叫", "theshold": 100,
                             "adjust": "全额赠送分钟数,表示资源不折算", "remindFlag": "否", "shareFlag": "是"},
        },
    },
}


def run(args):
    return subprocess.run([PY, "-X", "utf8", os.path.join(HERE, "cpcp_api.py")] + args,
                          capture_output=True, text=True, encoding="utf-8")


def main():
    ok = 0
    for tid in sorted(EXAMPLES):
        payload = {tid: EXAMPLES[tid]}
        schema = os.path.join(TEMPLATES, tid + ".schema.json")

        # render_table：非空 + 幂等（两次输出逐字节一致）
        hashes = []
        for _ in range(2):
            tmp = os.path.join(HERE, "_tmp_example_%s.json" % tid)
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(payload, f, ensure_ascii=False)
            r = run(["render_table", "--schema-file", schema, "--json-file", tmp, "--title", tid])
            txt = r.stdout
            os.unlink(tmp)
            hashes.append(hashlib.md5(txt.encode("utf-8")).hexdigest())
        assert r.returncode == 0, r.stdout + r.stderr
        assert "## " in txt and "套餐概览" in txt, "render 输出为空（E32 语义）"
        assert hashes[0] == hashes[1], "render_table 应幂等"
        print("1-ok render_table %-20s 幂等=是" % tid)
        ok += 1

        # derive_flat24：带 --template，出参键完整
        tmp = os.path.join(HERE, "_tmp_example_%s.json" % tid)
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False)
        r = run(["derive_flat24", "--template", tid, "--payload-json-file", tmp, "--mapping-file", MAPPING])
        os.unlink(tmp)
        assert r.returncode == 0, r.stdout + r.stderr
        d = json.loads(r.stdout)
        assert {"resultCode", "resultMsg", "template",
                "fields", "note"} <= set(d.keys()), d.keys()
        assert d["template"] == tid
        print("2-ok derive_flat24 %-20s 出参键完整" % tid)
        ok += 1

    # 取值来源列（V3.0）：--meta-file 逐叶子溯源 → 原始需求提取/复用相似产品/本体推理/默认值（确定性映射）
    schema = os.path.join(TEMPLATES, "familyAddPrc.schema.json")
    data = json.dumps({"familyAddPrc": {
        "baseInfo": {"prodType": "共享流量产品", "prodPrcName": "示例包", "effDate": "20260901"},
        "optionalInfo": {"printContent": {"prcMonthFee": 10},
                         "acctMonth": {"calcMode": "首月全额收取", "fixFee": 10}},
    }}, ensure_ascii=False)
    meta = json.dumps({
        "baseInfo.prodPrcName": {"label": "资费名称", "value": "示例包", "source": "原始需求"},
        "baseInfo.effDate": {"label": "销售开始日期", "value": "20260901", "source": "AI补全"},
        "optionalInfo.printContent.prcMonthFee": {"label": "套餐月费", "value": 10, "source": "本体推理"},
        "optionalInfo.acctMonth.fixFee": {"label": "固定费", "value": 10, "source": "默认值"},
    }, ensure_ascii=False)
    p_tmp = os.path.join(HERE, "_tmp_src_payload.json")
    m_tmp = os.path.join(HERE, "_tmp_src_meta.json")
    with open(p_tmp, "w", encoding="utf-8") as f:
        f.write(data)
    with open(m_tmp, "w", encoding="utf-8") as f:
        f.write(meta)
    r = run(["render_table", "--schema-file", schema, "--json-file", p_tmp,
             "--meta-file", m_tmp, "--title", "源测试"])
    os.unlink(p_tmp)
    os.unlink(m_tmp)
    assert r.returncode == 0, r.stdout + r.stderr
    txt = r.stdout
    assert "取值来源" in txt, "缺少取值来源列"
    for label in ("原始需求提取", "复用相似产品", "本体推理", "默认值"):
        assert label in txt, "取值来源未渲染：%s" % label
    print("3-ok render_table 取值来源 四态齐全（原始需求提取/复用相似产品/本体推理/默认值）")
    ok += 1

    # 3b) 取值来源列·复用相似产品拼接：传入相似品出参（similarOfferId+similarOfferName）时，
    #     AI补全 → "参考相似产品: {id} {name}"；未传相似品时回退固定标签"复用相似产品"
    data2 = json.dumps({"familyAddPrc": {
        "baseInfo": {"prodType": "共享流量产品", "prodPrcName": "示例包", "effDate": "20260901"},
        "optionalInfo": {"printContent": {"prcMonthFee": 10}},
    }}, ensure_ascii=False)
    meta2 = json.dumps({
        "baseInfo.prodPrcName": {"label": "资费名称", "value": "示例包", "source": "原始需求"},
        "baseInfo.effDate": {"label": "销售开始日期", "value": "20260901", "source": "AI补全"},
    }, ensure_ascii=False)
    sim2 = json.dumps({"similarOfferId": "12121212",
                       "similarOfferName": "5G-A轻享单品129元"}, ensure_ascii=False)
    p_tmp = os.path.join(HERE, "_tmp_sim_payload.json")
    m_tmp = os.path.join(HERE, "_tmp_sim_meta.json")
    s_tmp = os.path.join(HERE, "_tmp_sim_offer.json")
    with open(p_tmp, "w", encoding="utf-8") as f:
        f.write(data2)
    with open(m_tmp, "w", encoding="utf-8") as f:
        f.write(meta2)
    with open(s_tmp, "w", encoding="utf-8") as f:
        f.write(sim2)
    r = run(["render_table", "--schema-file", schema, "--json-file", p_tmp,
             "--meta-file", m_tmp, "--similar-offer-file", s_tmp, "--title", "源测试"])
    r2 = run(["render_table", "--schema-file", schema, "--json-file", p_tmp,
              "--meta-file", m_tmp, "--title", "源测试"])
    os.unlink(p_tmp)
    os.unlink(m_tmp)
    os.unlink(s_tmp)
    assert r.returncode == 0, r.stdout + r.stderr
    assert "参考相似产品: 12121212 5G-A轻享单品129元" in r.stdout, r.stdout
    assert r2.returncode == 0, r2.stdout + r2.stderr
    assert "复用相似产品" in r2.stdout, "未传相似品应回退固定标签"
    print("3b-ok render_table 复用相似产品拼接 → 参考相似产品: 12121212 5G-A轻享单品129元（无相似品回退）")
    ok += 1

    # V9.2 放松必填枚举判定：personMainPrc 套外计费标准（outChargeMode，必填枚举）需求以自由文本写入
    # 超套收费标准（chargeStandard）时，不应再进 pending_required（避免 5G-A 阶梯计费被误判为待补充）
    schema = os.path.join(TEMPLATES, "personMainPrc.schema.json")
    elements = json.dumps({
        "baseInfo.prodPrcName": "5G-A单品499元（高阶旗舰版）",
        "optionalInfo.printContent.chargeStandard": (
            "套外流量前100MB按0.03元/MB收费，达到100MB（3元）时额外赠送924MB（即3元/1GB），"
            "流量超过1GB按每超出1GB 3元收费，以此类推；套外语音国内0.15元/分钟；套外短彩信0.1元/条"),
    }, ensure_ascii=False)
    e_tmp = os.path.join(HERE, "_tmp_charge_elems.json")
    with open(e_tmp, "w", encoding="utf-8") as f:
        f.write(elements)
    r = run(["merge_nested", "--schema-file", schema, "--elements-json-file", e_tmp,
             "--mode", "normal"])
    os.unlink(e_tmp)
    assert r.returncode == 0, r.stdout + r.stderr
    mr = json.loads(r.stdout)
    pending = mr.get("pending_required", [])
    assert "optionalInfo.billGprsCfg.outChargeMode" not in pending, (
        "V9.2 应放松：套外计费标准不得进待补充，实际 pending=%s" % pending)
    print("4-ok merge_nested 放松必填枚举 套外计费标准已覆盖（不进待补充）")
    ok += 1

    print("全部 %d 项模板轨冒烟通过（含 3 个无存量模板的模拟示例验证）" % ok)


if __name__ == "__main__":
    main()
