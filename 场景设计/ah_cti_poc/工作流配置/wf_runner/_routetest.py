import sys, os, io, re, datetime, random, string
sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wf_runner as w

names = w._discover_workflows()
sysprompt = w._load_orchestrator_prompt(names)
assert "【路由表" in sysprompt and "【角色】" in sysprompt, "prompt 抽取失败，仍走兜底"
llm = w.LlmClient(w.LlmConfig.from_h2_seed())

# (用户消息, 上下文req_id, 期望workflow 或 None 表示只回话)
CASES = [
    ("我要上新四档5G畅享套餐，请先提报需求再生成加载方案", "", "wf_sub_00"),
    ("需求分析", "", "wf_sub_00"),
    ("生成加载方案", "", "wf_sub_00"),
    ("分析需求", "", "wf_sub_00"),
    ("描述需求：月费199家庭融合套餐", "", "wf_sub_00"),
    ("生成加载方案", "PLAN20260101000000001", "wf_sub_01"),
    ("需求分析", "PLAN20260101000000001", "wf_sub_01"),
    ("查询审批进度", "PLAN20260101000000001", "wf_sub_08"),
    ("审批到哪一步了", "PLAN20260101000000001", "wf_sub_08"),
    ("审批状态", "PLAN20260101000000001", "wf_sub_08"),
    ("发起需求审批", "PLAN20260101000000001", "wf_sub_11"),
    ("需求工单审批发起一下", "PLAN20260101000000001", "wf_sub_11"),
    ("发起优化工单", "PLAN20260101000000001", "wf_sub_14"),
    ("确认配置，开始执行", "PLAN20260101000000001", "wf_sub_02"),
    ("查询稽核结果", "PLAN20260101000000001", "wf_sub_12"),
    ("查询测试结果", "PLAN20260101000000001", "wf_sub_13"),
    ("查询监控运维", "PLAN20260101000000001", "wf_sub_07"),
    ("查询存量产品：5G畅享融合套餐", "", "wf_sub_09"),
]

carried = lambda rid: {"req_id": rid, "chat_id": "chk"}
ok = 0
for msg, rid, exp in CASES:
    prompt = w._build_demo_decision_prompt([], msg, carried(rid), {}, [])
    text, fb, note = llm.chat(prompt, system=sysprompt, max_tokens=w.ORCHESTRATOR_MAX_TOKENS)
    d = w._extract_json_object(text) or {}
    wf = w._wf_code(d.get("workflow") or "")
    act = (d.get("action") or "").strip()
    good = (exp is None and act == "reply_only") or (act == "call_workflow" and wf == exp)
    ok += good
    print(f"{'✓' if good else '✗'} [{msg[:22]:<22}] rid={'有' if rid else '无'} "
          f"→ {act}/{wf or '-'} 期望={exp or 'reply_only'}{' FB' if fb else ''}")
print(f"\n路由 battery: {ok}/{len(CASES)}")
sys.exit(0 if ok == len(CASES) else 1)
