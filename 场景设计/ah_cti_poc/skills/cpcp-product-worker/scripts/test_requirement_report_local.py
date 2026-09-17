# -*- coding: utf-8 -*-
"""需求提报单渲染器本地冒烟：确定性渲染（模板引擎替代 LLM 手工渲染）+ 待补充判定 + 幂等 + 单/融合分支。

不依赖后端、不依赖真实存量。断言：
- render() 输出《销售品需求提报单》业务可读中文文档；
- 待补充口径从宽：仅必要字段（资费价格/资费免费资源）缺失判待补充，其余字段缺失不催补；
- 单/融合品模板分支：单商品不渲染"融合成员"，融合品有值渲染、缺失从宽不催补；
- 同输入同 req_id/日期 → 输出逐字节幂等（可回归）；
- CLI 端到端可运行且正确落盘 requirement_report_<req_id>.json。
"""
import hashlib
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
PY = sys.executable

import render_requirement_report as rrr  # noqa: E402

FULL = {
    "name": "5G畅享融合套餐199元",
    "product_type": "家庭基础套餐",
    "series": "5G-A融合套餐",
    "members": "宽带、天翼高清、副卡功能费",
    "price": "199",
    "resources": "国内流量60GB、国内语音1000分钟、短信200条",
    "out_price": "套外流量3元/GB",
    "billing_cycle": "月",
    "effective_way": "新入网立即生效",
    "validity": "2年",
    "change_rule": "可变更至在售套餐",
    "cancel_rule": "可退订，次月生效",
}


def main():
    ok = 0

    # 1) 完整字段：全渲染、无待补充、业务可读、分节表格
    text, pending, data = rrr.render(FULL, req_id="PLAN_DEMO_0001", reporter="张三",
                                     need_summary="5G融合家庭套餐需求", today="2026-09-17")
    assert "## 销售品需求提报单" in text
    assert "### 1. 需求基本信息" in text and "### 2. 产品/销售品信息" in text
    assert "### 3. 资费方案要点" in text and "### 4. 订购/变更/退订规则" in text
    assert "| 需求单号 | PLAN_DEMO_0001 |" in text
    assert "| PLAN_DEMO_0001 |" in text and "| 2026-09-17 |" in text
    assert "| 套餐档位（月费） | 199 元/月 |" in text
    assert "| 融合成员 | 宽带、天翼高清、副卡功能费 |" in text
    assert pending == [], pending
    assert data["req_id"] == "PLAN_DEMO_0001"
    assert "无技术键名" not in text
    print("1-ok 完整字段 分节表格渲染齐全 + 无待补充")
    ok += 1

    # 2) 幂等：同输入同 req_id/日期 → 逐字节一致（可回归）
    h1, h2 = [], []
    for _ in range(2):
        t, p, d = rrr.render(FULL, req_id="PLAN_DEMO_0001", today="2026-09-17")
        h1.append(hashlib.md5(t.encode("utf-8")).hexdigest())
        h2.append(hashlib.md5(t.encode("utf-8")).hexdigest())
    assert h1[0] == h1[1] == h2[0] == h2[1], "重复渲染应逐字节一致"
    print("2-ok 确定性幂等（同输入逐字节稳定）")
    ok += 1

    # 3) 部分字段缺失：仅必要字段（价格/免费资源）判待补充，其余字段从宽不催补
    partial = {"name": "某宽带套餐", "price": "待补充", "billing_cycle": "月"}
    text, pending, data = rrr.render(partial, req_id="PLAN_DEMO_0002", today="2026-09-17")
    assert "| 套餐档位（月费） | 待补充 |" in text
    assert "套餐档位（月费）" in pending, "价格字段缺失应整体待补充"
    assert "套内资源" in pending, "免费资源缺失应待补充"
    assert "产品类型" not in pending and "退订/拆机规则" not in pending, "非必要字段缺失不催补: %r" % pending
    assert data["pending_fields"] == pending
    assert "### 5. 待补充字段" in text and "、".join(pending) in text
    print("3-ok 部分字段 仅必要字段判待补充（%d 项）：价格+免费资源，其余不催补" % len(pending))
    ok += 1

    # 4) CLI 端到端：正确落盘工件 requirement_report_<req_id>.json
    src = os.path.join(HERE, "_tmp_req_cli.json")
    with open(src, "w", encoding="utf-8") as f:
        json.dump(FULL, f, ensure_ascii=False)
    r = subprocess.run([PY, "-X", "utf8", os.path.join(HERE, "render_requirement_report.py"),
                        "--req-id", "PLAN_CLI_0001", "--elements-json-file", src,
                        "--reporter", "张三", "--need-summary", "CLI 冒烟",
                        "--workdir", HERE],
                       capture_output=True, text=True, encoding="utf-8")
    os.unlink(src)
    assert r.returncode == 0, r.stdout + r.stderr
    out = json.loads(r.stdout)
    assert out["req_id"] == "PLAN_CLI_0001"
    artifact = os.path.join(HERE, "requirement_report_PLAN_CLI_0001.json")
    assert os.path.exists(artifact), "工件未落盘"
    with open(artifact, "r", encoding="utf-8") as f:
        art = json.load(f)
    assert art["req_id"] == "PLAN_CLI_0001" and art["report_text"] == out["report_text"]
    os.unlink(artifact)
    print("4-ok CLI 端到端 工件落盘 requirement_report_PLAN_CLI_0001.json")
    ok += 1

    # 5) 单商品分支（个人主套餐）：members 不渲染、非必要字段缺失不催补
    single = {"product_type": "个人主套餐", "price": "129"}
    text, pending, data = rrr.render(single, req_id="PLAN_DEMO_0003", today="2026-09-17")
    assert "融合成员" not in text, "单商品不应渲染融合成员行"
    assert "套内资源" in pending, "单商品免费资源缺失应待补充"
    assert not any(u == "融合成员" for u in pending), "单商品不得把融合成员判为待补充: %r" % pending
    print("5-ok 单商品分支 members 不渲染、免费资源判待补充（%d 项）" % len(pending))
    ok += 1

    # 6) 融合品分支（家庭基础套餐）：members 有值渲染，缺失不判待补充（从宽）
    fusion = {"product_type": "家庭基础套餐", "price": "199", "resources": "国内流量60GB"}
    text, pending, data = rrr.render(fusion, req_id="PLAN_DEMO_0004", today="2026-09-17")
    assert pending == [], "价格+免费资源齐备应无待补充: %r" % pending
    fusion_no_member = {"product_type": "家庭基础套餐", "price": "199", "resources": "国内流量60GB"}
    t2, p2, _ = rrr.render(fusion_no_member, req_id="PLAN_DEMO_0005", today="2026-09-17")
    assert not any(u == "融合成员" for u in p2), "融合品成员缺失从宽不催补: %r" % p2
    print("6-ok 融合品分支 members 缺失不判待补充（从宽口径）")
    ok += 1

    print("全部 %d 项需求提报单渲染冒烟通过" % ok)


if __name__ == "__main__":
    main()
