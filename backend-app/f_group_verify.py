# -*- coding: utf-8 -*-
"""F1~F8 融合商品加载联调验证（直连后端 mock 接口，不依赖 Agent 链路）。"""
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.request

BASE = os.environ.get("CPCP_BASE_URL", "http://127.0.0.1:6174").rstrip("/")
SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                      "场景设计", "ah_cti_poc", "skills", "cpcp-product-worker", "scripts", "cpcp_api.py")
if not os.path.exists(SCRIPT):
    SCRIPT = r"D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai\场景设计\ah_cti_poc\skills\cpcp-product-worker\scripts\cpcp_api.py"

sys.stdout.reconfigure(encoding="utf-8")
ok_count = 0
fail_lines = []


def call(path, payload, method="POST"):
    import urllib.parse
    body = json.dumps(payload, ensure_ascii=False)
    req = urllib.request.Request(BASE + path, data=body.encode("utf-8") if method == "POST" else None,
                                 headers={"Content-Type": "application/json"}, method=method)
    if method == "GET":
        req = urllib.request.Request(BASE + path + "?" + urllib.parse.urlencode(payload))
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def check(name, cond, detail=""):
    global ok_count
    if name is not None:
        if cond:
            ok_count += 1
            print("PASS |", name)
        else:
            print("FAIL |", name, "|", str(detail)[:400])
    else:
        print("INFO |", str(detail)[:400])


def run_script(args):
    env = {**os.environ, "PYTHONIOENCODING": "utf-8", "CPCP_BASE_URL": BASE}
    r = subprocess.run([sys.executable, "-X", "utf8", SCRIPT] + args,
                       capture_output=True, env=env, timeout=120)
    out = r.stdout.decode("utf-8", errors="replace")
    try:
        return json.loads(out)
    except json.JSONDecodeError:
        return {"_raw": out, "_rc": r.returncode, "_err": r.stderr.decode("utf-8", errors="replace")}


# ---------- F1：融合需求提报 ----------
print("=" * 20, "F1 融合需求提报")
desc = "我要上新一个5G-A融合套餐199元，主卡套餐含100GB国内流量和1000分钟国内通话，允许办理副卡，流量可结转，同时绑定1000M宽带和天翼高清，副卡功能费10元/月"
sim = call("/api/v1/appstore/similar/offer/query", {"businessDesc": desc})
og = sim.get("offer_group") or {}
check("F1-1 similar_offer 命中融合组并下发 offer_group", og.get("group_id") == "GP900113046", sim)
roles = [m["role"] for m in og.get("members", [])]
check("F1-2 成员构成与组定义一致（主卡套餐/宽带/天翼高清/副卡功能费）",
      roles == ["主卡套餐", "宽带", "天翼高清", "副卡功能费"], roles)

group_fields = {
    "offer_type": "融合",
    "main_offer": {"role": "主卡套餐", "fields": [
        {"field": "套餐名称", "category": "产品属性", "value": "5G-A融合套餐199元", "source": "原始需求"},
        {"field": "套餐档位", "category": "产品属性", "value": "199元", "source": "原始需求"},
        {"field": "国内通用流量", "category": "套餐内基础资源", "value": "100GB", "source": "原始需求"},
        {"field": "国内语音拨打", "category": "套餐内基础资源", "value": "1000分钟", "source": "原始需求"},
        {"field": "是否允许办理副卡", "category": "套餐内权益配置", "value": "允许", "source": "原始需求"},
        {"field": "流量结转规则", "category": "计费/支付/风控", "value": "结转", "source": "原始需求"},
    ]},
    "member_offers": [
        {"member_role": "宽带", "fields": [
            {"field": "产品名称", "category": "产品属性", "value": "千兆宽带", "source": "原始需求"},
            {"field": "宽带速率", "category": "套餐内基础资源", "value": "1000M起", "source": "AI补全"},
            {"field": "月功能费", "category": "套外资费标准", "value": "待补充", "source": "原始需求"},
        ]},
        {"member_role": "天翼高清", "fields": [
            {"field": "产品名称", "category": "产品属性", "value": "天翼高清", "source": "AI补全"},
            {"field": "月功能费", "category": "套外资费标准", "value": "10元/月", "source": "原始需求"},
        ]},
        {"member_role": "副卡功能费", "fields": [
            {"field": "产品名称", "category": "产品属性", "value": "5G体验副卡功能费", "category2": "", "source": "AI补全"},
            {"field": "月功能费", "category": "套外资费标准", "value": "10元/月", "source": "原始需求"},
        ]},
    ],
    "group_rules": og.get("group_rules", {}),
}
with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8") as f:
    json.dump(group_fields, f, ensure_ascii=False)
    group_file = f.name

bp = run_script(["build_plan", "--fields-json-file", group_file])
check("F1-3 build_plan 组结构 → 六列表格",
      "| 商品 | 模块 | 分类 | 字段名称 | 字段值 | 备注 |" in bp.get("plan_md", ""), bp)
check("F1-4 pending_fields 携带 role 定位（宽带-月功能费）",
      bp.get("pending_fields") == [{"role": "宽带", "field": "月功能费"}], bp.get("pending_fields"))
check("F1-5 req_id 生成（出口B）", str(bp.get("req_id", "")).startswith("PLAN"), bp.get("req_id"))

# ---------- F2：成员价格缺失 → 出口A 口径（pending 定位成员） ----------
print("=" * 20, "F2 成员价格缺失")
check("F2-1 pending 定位到 role=宽带", any(p.get("role") == "宽带" and p.get("field") == "月功能费"
      for p in bp.get("pending_fields", []) if isinstance(p, dict)) or
      bp.get("pending_fields") == [{"role": "宽带", "field": "月功能费"}], bp.get("pending_fields"))

# ---------- F3/F6：组级稽核（越界成员 + 重复成员） ----------
print("=" * 20, "F3/F6 组级稽核")
group_config = {
    "req_id": bp.get("req_id"),
    "offer_type": "融合",
    "main_offer": {"role": "主卡套餐", "offer_id": "900113046", "fields": group_fields["main_offer"]["fields"]},
    "member_offers": [
        {"role": "宽带", "fields": group_fields["member_offers"][0]["fields"]},
        {"role": "权益包", "fields": [{"field": "产品名称", "value": "权益包A", "source": "原始需求"}]},
        {"role": "权益包", "fields": [{"field": "产品名称", "value": "权益包B", "source": "原始需求"}]},
    ],
    "group_rules": og.get("group_rules", {}),
}
audit = call("/api/v1/appstore/audit/realtime",
             {"offer_id": "900113046", "config_json": json.dumps(group_config, ensure_ascii=False),
              "audit_scene": "all"})
err_items = [e.get("item", "") for e in audit.get("error_list", [])]
check("F3-1 成员越界/重复 → group 类目 error 项", any(i.startswith("group:") for i in err_items), err_items)
check("F3-2 审计通过性 pass=0（存在 error 级组检查）", audit.get("pass") == "0", audit.get("pass"))

# 依赖警告：OPTIONAL_DEPEND 副卡功能费单提 → warning 不阻断
group_config_ok = {
    "req_id": bp.get("req_id"),
    "offer_type": "融合",
    "main_offer": {"role": "主卡套餐", "offer_id": "900113046", "fields": group_fields["main_offer"]["fields"]},
    "member_offers": [
        {"role": "宽带", "fields": [{"field": "月功能费", "value": "10元/月", "source": "原始需求"}]},
        {"role": "天翼高清", "fields": [{"field": "月功能费", "value": "10元/月", "source": "原始需求"}]},
        {"role": "副卡功能费", "fields": [{"field": "月功能费", "value": "10元/月", "source": "原始需求"}]},
    ],
    "group_rules": og.get("group_rules", {}),
}
audit2 = call("/api/v1/appstore/audit/realtime",
              {"offer_id": "900113046", "config_json": json.dumps(group_config_ok, ensure_ascii=False),
               "audit_scene": "all"})
levels = [e.get("level") for e in audit2.get("error_list", [])]
check("F3-3 合法组配置 pass=1", audit2.get("pass") == "1", audit2)
check("F3-4 OPTIONAL_DEPEND 提示为 warning（不阻断）", "warning" in levels and "error" not in levels, levels)

# ---------- F7：资费比对逐成员 ----------
print("=" * 20, "F7 资费比对逐成员")
billing = call("/api/v1/appstore/billing/rules/verify",
               {"config_json": json.dumps(group_config_ok, ensure_ascii=False), "check_scene": "all"})
compare = billing.get("compare_list", [])
member_roles_in_compare = [c.get("member_role") for c in compare if isinstance(c, dict) and c.get("member_role")]
check("F7-1 组结构 compare_list 逐成员（member_role 键）", "主卡套餐" in member_roles_in_compare
      and "宽带" in member_roles_in_compare and "天翼高清" in member_roles_in_compare, member_roles_in_compare)
check("F7-2 组级价格待补充风险入 risk_list",
      any(r.get("risk_type") == "member_fee_pending" for r in billing.get("risk_list", [])),
      billing.get("risk_list"))
# 单品 billing：compare_list 行无 member_role 键（零差异）
single_config = {"req_id": "PLAN20260916000000000", "fields": group_fields["main_offer"]["fields"],
                 "pending_fields": []}
billing_single = call("/api/v1/appstore/billing/rules/verify",
                      {"config_json": json.dumps(single_config, ensure_ascii=False), "check_scene": "all"})
no_role = all("member_role" not in c for c in billing_single.get("compare_list", []) if isinstance(c, dict))
check("F7-3 单品 compare_list 无 member_role 键（零差异）", no_role,
      billing_single.get("compare_list", [])[:1])

# ---------- F4：融合配置落地 group 出参 + 测试组场景 + offer_group_check ----------
print("=" * 20, "F4 融合四环节")
save = call("/api/v1/appstore/product/config/save",
            {"req_id": bp.get("req_id"), "plan_json": json.dumps(group_config_ok, ensure_ascii=False),
             "operator": "f-test", "confirmed": True})
g = save.get("group") or {}
check("F4-1 config/save 组结构 → group 出参", bool(g) and g.get("main_offer_id") == "900113046", save)
gm = {m.get("role"): m for m in g.get("members", [])}
check("F4-2 group.members 三成员 + 副卡功能费 offer_id 引用组定义",
      set(gm) == {"宽带", "天翼高清", "副卡功能费"}
      and gm.get("副卡功能费", {}).get("offer_id") == "7320110001600005", g)
offer_id = save.get("offer_id", "")

test_start = call("/api/v1/appstore/test/offer/start", {"offerId": "900113046"})
gid = test_start.get("globalId", "")
check("F4-3 融合品测试发起（globalId）", bool(gid), test_start)
for _ in range(40):
    prog = call("/api/v1/appstore/test/offer/progress", {"globalId": gid})
    if prog.get("done") == "true":
        break
    time.sleep(1)
scenes = call("/api/v1/appstore/test/offer/scenes", {"globalId": gid})
nbrs = [s.get("testSceneNbr") for s in scenes.get("testScenes", [])]
check("F4-4 组场景 S_GROUP_BIND/S_ADDON_SUB 下发",
      "S_GROUP_BIND" in nbrs and "S_ADDON_SUB" in nbrs, nbrs)
result = call("/api/v1/appstore/test/offer/result", {"globalId": gid})
ogc = result.get("offer_group_check") or {}
check("F4-5 offer_group_check 出参（E26 数据源）",
      ogc.get("main_offer_id") == "900113046" and len(ogc.get("members", [])) == 4, ogc)
check("F4-6 overallConclusion 由后端生成", "成员组合验证" in ogc.get("overallConclusion", ""), ogc)
group_points = []
for s in result.get("testScenes", []):
    if s.get("testSceneNbr") == "S_GROUP_BIND":
        group_points = [p.get("testPointNbr") for p in s.get("testCasePointResults", [])]
check("F4-7 组类测点 P_SHARE/P_GROUP_MUTEX/P_MEMBER_STATUS",
      all(p in group_points for p in ["P_SHARE", "P_GROUP_MUTEX", "P_MEMBER_STATUS"]), group_points)

# ---------- F8：单商品零差异回归 ----------
print("=" * 20, "F8 单商品零差异")
sim_single = call("/api/v1/appstore/similar/offer/query", {"businessDesc": "我要上新一个5G-A套餐199元，包含60GB国内流量和1000分钟通话"})
check("F8-1 单品需求 → 无 offer_group 键", "offer_group" not in sim_single, list(sim_single.keys()))
save_single = call("/api/v1/appstore/product/config/save",
                   {"req_id": "PLAN20260916999999001", "plan_json": json.dumps(
                       {"req_id": "PLAN20260916999999001", "fields": group_fields["main_offer"]["fields"],
                        "pending_fields": []}, ensure_ascii=False),
                    "operator": "f-test", "confirmed": True})
check("F8-2 单品 save → 无 group 键", "group" not in save_single, list(save_single.keys()))
audit_single = call("/api/v1/appstore/audit/realtime",
                    {"offer_id": "900102308", "config_json": json.dumps(
                        {"req_id": "PLAN20260916999999001", "fields": group_fields["main_offer"]["fields"]},
                        ensure_ascii=False), "audit_scene": "all"})
check("F8-3 单品 audit → error_list 无 group 类目",
      all(not str(e.get("item", "")).startswith("group:") for e in audit_single.get("error_list", [])),
      audit_single.get("error_list"))
ts_single = call("/api/v1/appstore/test/offer/start", {"offerId": "900102308"})
gid2 = ts_single.get("globalId", "")
for _ in range(40):
    prog2 = call("/api/v1/appstore/test/offer/progress", {"globalId": gid2})
    if prog2.get("done") == "true":
        break
    time.sleep(1)
res_single = call("/api/v1/appstore/test/offer/result", {"globalId": gid2})
sc_single = [s.get("testSceneNbr") for s in res_single.get("testScenes", [])]
check("F8-4 单品测试 → 3 场景且无组场景/无 offer_group_check",
      set(sc_single) == {"S_O_TC", "S_ADD_CARD", "S_U_TC"} and "offer_group_check" not in res_single,
      (sc_single, "offer_group_check" in res_single))

print("=" * 60)
print("PASS total:", ok_count)
