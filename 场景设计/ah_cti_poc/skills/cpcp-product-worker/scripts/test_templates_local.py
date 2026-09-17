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

    print("全部 %d 项模板轨冒烟通过（含 3 个无存量模板的模拟示例验证）" % ok)


if __name__ == "__main__":
    main()
