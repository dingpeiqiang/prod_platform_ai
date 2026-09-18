# -*- coding: utf-8 -*-
"""validate_output.py 本地功能自测（SKILL.md 纪律1 强制兜底）。

覆盖（V12.0 单次全量校验 --node all）：
  - --node all 一次性校验完整输出文档（环节3~7 + 汇总块），读取 workdir 下
    result_<node>_<req_id>.json 工件，一次校验通过；
  - 标题头形态封闭（九环节全局编号 3/4/5/6/7）；
  - 非法标题头 / 缺失标题头 / 自造第三形态 → VALIDATE_FAIL；
  - 虚构聚合统计值（10/10 出参不存在）→ VALIDATE_FAIL；
  - 单环节 --node fee / --node test 兼容模式仍可用。
全部离线，不依赖后端与 LLM。
"""
import json
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
PY = sys.executable


def run(args):
    r = subprocess.run([PY, "-X", "utf8", os.path.join(HERE, "validate_output.py")] + args,
                       capture_output=True, text=True, encoding="utf-8")
    return r


REQ = "PLANTEST0001"
FULL_DOC = """【环节3/9·销售品智能配置】✅ 执行成功

已根据《5G-A套餐199元》完成销售品智能配置。
- 销售品ID（offer_id）：920170792
- 产品ID（product_id）：PPLAN202609180320170F7E92

---
【环节4/9·配置规格稽核】✅ 执行成功

> **稽核对象：销售品ID（offer_id）920170792**
> **稽核结果：通过**
> - 基础信息完整性：✅

---
【环节5/9·资费校准】✅ 执行成功

| 项目 | 套餐描述（需求） | 计费配置描述（系统） | 比对结果 |
| :--- | :--- | :--- | :--- |
| 套餐月租 | 199元 | 199元 | 一致 |

---
【环节6/9·销售品自动测试】✅ 执行成功

已根据销售品配置自动生成并执行测试用例 20条（各场景合计）。

| 测试类型 | 用例数 | 结果 |
| :--- | :--- | :--- |
| S_O_TC 套餐新装 | 10 | ✅ |
| S_U_TC 套餐退订 | 10 | ✅ |

---
【环节7/9·受理验证】✅ 执行成功

- 受理凭证：orderId：ORD1，offerInstId：OI1
- 新用户订购（S_O_TC）：✅

---
【执行主干全部完成】✅ 共4个执行环节（全局环节3~6）成功（受理验证环节7 含于环节6 内独立成节）
"""


def make_workdir():
    tmp = tempfile.mkdtemp(prefix="vo_test_")
    fee = {"pass": 1, "compare_list": [{"project_name": "套餐月租", "requirement_desc": "199元",
                                        "billing_desc": "199元", "result": "一致"}],
           "risk_list": []}
    test = {"test_passed": True, "offerName": "新增销售品920170792", "orderId": "ORD1",
            "offerInstId": "OI1", "globalId": "G1",
            "testScenes": [{"testSceneName": "S_O_TC 套餐新装", "testCaseCount": 10,
                            "successTestCaseCount": 10, "failTestCaseCount": 0},
                           {"testSceneName": "S_U_TC 套餐退订", "testCaseCount": 10,
                            "successTestCaseCount": 10, "failTestCaseCount": 0}]}
    config = {"status": "SUCCESS", "offer_id": "920170792",
              "product_id": "PPLAN202609180320170F7E92", "script_url": "http://x/script"}
    spec = {"pass": 1, "error_list": []}
    plan = {"fields": [{"field": "套餐名称", "value": "5G-A套餐199元"}]}
    # 环节工件命名须与 run_pipeline 一致：config→config_result_，spec/fee/test→result_<node>_
    for n, obj in (("config", config), ("spec", spec), ("fee", fee), ("test", test)):
        name = "config_result_%s.json" % REQ if n == "config" else "result_%s_%s.json" % (n, REQ)
        with open(os.path.join(tmp, name), "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False)
    with open(os.path.join(tmp, "plan_json_%s.json" % REQ), "w", encoding="utf-8") as f:
        json.dump(plan, f, ensure_ascii=False)
    return tmp


def write_draft(tmp, content):
    p = os.path.join(tmp, "draft.md")
    with open(p, "w", encoding="utf-8") as f:
        f.write(content)
    return p


def main():
    ok = 0

    def check(name, cond, detail=""):
        nonlocal ok
        assert cond, "%s -> %s" % (name, detail)
        print("OK  %s" % name)
        ok += 1

    # 1. --node all：完整文档单次全量校验通过
    tmp = make_workdir()
    r = run(["--node", "all", "--workdir", tmp, "--req-id", REQ, "--output-file", write_draft(tmp, FULL_DOC)])
    out = json.loads(r.stdout)
    check("--node all 全量校验通过", r.returncode == 0 and out["resultCode"] == "0", r.stdout)
    check("--node all 含多环节标题头校验清单", any("标题头形态封闭" in c for c in out["checklist"]), r.stdout)

    # 2. --node all：缺失标题头 → FAIL
    r = run(["--node", "all", "--workdir", tmp, "--req-id", REQ,
             "--output-file", write_draft(tmp, "没有标题头的裸段落")])
    out = json.loads(r.stdout)
    check("--node all 缺失标题头 FAIL", r.returncode == 1 and out["resultCode"] == "VALIDATE_FAIL", r.stdout)

    # 3. --node all：虚构全局统计值（与场景合计 20 不符的 20/30 比率）→ FAIL
    bad = FULL_DOC.replace("20条（各场景合计）", "20/30 条（各场景合计）")
    r = run(["--node", "all", "--workdir", tmp, "--req-id", REQ, "--output-file", write_draft(tmp, bad)])
    out = json.loads(r.stdout)
    check("--node all 虚构统计值 FAIL", r.returncode == 1 and out["resultCode"] == "VALIDATE_FAIL", r.stdout)

    # 4. --node all：自造第三形态标题头 → FAIL
    bad2 = FULL_DOC.replace("【环节3/9·销售品智能配置】✅ 执行成功", "【环节3/9·销售品智能配置】执行中断")
    r = run(["--node", "all", "--workdir", tmp, "--req-id", REQ, "--output-file", write_draft(tmp, bad2)])
    out = json.loads(r.stdout)
    check("--node all 自造标题形态 FAIL", r.returncode == 1 and out["resultCode"] == "VALIDATE_FAIL", r.stdout)

    # 5. 单环节兼容模式 --node fee 仍可用
    r = run(["--node", "fee", "--result-file", os.path.join(tmp, "result_fee_%s.json" % REQ),
             "--output-file", write_draft(tmp, FULL_DOC)])
    out = json.loads(r.stdout)
    check("--node fee 兼容模式通过", r.returncode == 0 and out["resultCode"] == "0", r.stdout)

    # 6. 单环节兼容模式 --node test 仍可用
    r = run(["--node", "test", "--result-file", os.path.join(tmp, "result_test_%s.json" % REQ),
             "--output-file", write_draft(tmp, FULL_DOC)])
    out = json.loads(r.stdout)
    check("--node test 兼容模式通过", r.returncode == 0 and out["resultCode"] == "0", r.stdout)

    # 7. --node all 缺 --workdir/--req-id → PARAM_MISSING
    r = run(["--node", "all", "--output-file", write_draft(tmp, FULL_DOC)])
    out = json.loads(r.stdout)
    check("--node all 缺参 PARAM_MISSING", r.returncode == 2 and out["resultCode"] == "PARAM_MISSING", r.stdout)

    # 8. full-success 文档缺失某个环节结构化标题头（环节5 资费校准块被合并/删除）→ FAIL（防结构化丢失）
    merged = FULL_DOC.replace(
        "【环节5/9·资费校准】✅ 执行成功\n\n| 项目 | 套餐描述", "| 项目 | 套餐描述").replace(
        "【环节5/9·资费校准】✅ 执行成功\n\n", "")
    r = run(["--node", "all", "--workdir", tmp, "--req-id", REQ, "--output-file", write_draft(tmp, merged)])
    out = json.loads(r.stdout)
    check("--node all 缺失环节结构化标题头 FAIL", r.returncode == 1 and out["resultCode"] == "VALIDATE_FAIL", r.stdout)

    print("\n%s checks passed" % ok)


if __name__ == "__main__":
    main()
