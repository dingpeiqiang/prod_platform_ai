# -*- coding: utf-8 -*-
"""dispatcher.py 本地功能自测（四层架构第0层，规则优先）。

覆盖：意图封闭枚举、确认门禁、实体抽取、会话兜底、LLM 兜底（needs_llm）、
知识库分流、超范围拒绝。全部离线，不依赖后端与 LLM。
"""
import json
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
PY = sys.executable


def run(args):
    r = subprocess.run([PY, "-X", "utf8", os.path.join(HERE, "dispatcher.py")] + args,
                       capture_output=True, text=True, encoding="utf-8")
    return r


def main():
    ok = 0

    def check(msg, expect_intent, extra=None, session=None):
        nonlocal ok
        args = ["--message", msg]
        session_file = None
        if session:
            tmp = tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False, encoding="utf-8")
            json.dump(session, tmp, ensure_ascii=False)
            tmp.close()
            session_file = tmp.name
            args += ["--session-file", session_file]
        r = run(args)
        out = json.loads(r.stdout)
        if session_file:
            os.unlink(session_file)
        assert r.returncode == 0, (r.returncode, r.stdout)
        assert out["intent"] == expect_intent, (msg, out["intent"], expect_intent)
        if extra:
            for k, v in extra.items():
                assert out.get(k) == v, (msg, k, out.get(k), v)
        print("OK  %s → %s" % (msg, expect_intent))
        ok += 1
        return out

    # 1. 意图路由（规则命中，needs_llm=false）
    check("我刚提报一个5G-A套餐需求，套餐名称叫校园青春卡", "REQ_REPORT",
          {"needs_llm": False, "route": "A"})
    check("确认配置", "CONFIRM_EXEC", {"confirmed": True, "route": "B"})
    check("执行吧", "CONFIRM_EXEC", {"confirmed": True})
    check("重新执行上次失败的环节", "RESUME_EXEC", {"confirmed": True, "resume": True})
    check("从失败环节继续", "RESUME_EXEC", {"resume": True})
    check("发起上线审批", "APPROVAL", {"route": "C"})
    check("查询审批进度", "QUERY_APPROVAL", {"route": "D1"})
    check("查询销售品运行监控", "QUERY_MONITOR", {"route": "D2"})
    check("确认上线", "CONFIRM_ONLINE", {"route": "D3"})
    check("生成监控运维方案", "CONFIRM_ONLINE")
    check("查询受理验证结果", "ACCEPTANCE_PLAYBACK")
    check("销售品命名有什么规范", "QNA", {"kb_target": "K1"})
    check("这个套餐资费怎么收费", "QNA", {"kb_target": "K2"})
    check("自动测试用例怎么设计", "QNA", {"kb_target": "K3"})

    # 2. 实体抽取 + 会话兜底
    out = check("查询审批单 APPR20260916 的进度", "QUERY_APPROVAL",
                {"needs_llm": False})
    assert out["entities"]["approval_id"] == "APPR20260916", out["entities"]
    out = check("重新执行", "RESUME_EXEC", session={"req_id": "PLAN20260916100000001",
                                                     "offer_id": "P900102308"})
    assert out["entities"]["req_id"] == "PLAN20260916100000001", out["entities"]
    assert out["entities"]["offer_id"] == "P900102308", out["entities"]

    # 3. 需求提报带套餐名 → offer_name 抽取
    out = check("帮我校园青春卡套餐的流量配置改一下", "REQ_REPORT",
                {"needs_llm": False})
    assert out["entities"]["offer_name"] == "校园青春卡", out["entities"]

    # 4. 规则未命中 → LLM 兜底（needs_llm=true，intent 空，llm_prompt 为封闭枚举 schema）
    r = run(["--message", "请帮我处理一下销售品的后续"])
    out = json.loads(r.stdout)
    assert r.returncode == 0
    assert out["needs_llm"] is True, out
    assert out["intent"] == "", out
    assert out["llm_prompt"], out
    lp = json.loads(out["llm_prompt"])
    assert lp["task"] == "intent_classify" and "allowed_intents" in lp, lp
    print("OK  规则未命中 → LLM 兜底（封闭枚举, needs_llm=true, intent 空）")
    ok += 1

    # 5. 超范围明确拒绝 → rule 兜底 out_of_scope（不触发 LLM）
    out = check("给我推荐一只股票", "OUT_OF_SCOPE", {"needs_llm": False, "route": "NONE"})

    # 6. 缺消息 → PARAM_MISSING
    r = run(["--message", "  "])
    assert r.returncode == 2 and "缺少用户消息" in r.stdout, (r.returncode, r.stdout)
    print("OK  缺消息 → PARAM_MISSING")
    ok += 1

    # 7. 消息文件方式（绕过大报文内联）
    tmp = tempfile.NamedTemporaryFile(mode="w", suffix=".txt", delete=False, encoding="utf-8")
    tmp.write("我要查询审批单状态")
    tmp.close()
    r = run(["--message-file", tmp.name])
    os.unlink(tmp.name)
    out = json.loads(r.stdout)
    assert out["intent"] == "QUERY_APPROVAL", out["intent"]
    print("OK  --message-file 读取 OK")
    ok += 1

    # 8. 否定/拒绝 → REJECT（不视为确认、不归流程，confirmed=False）
    out = check("我不同意", "REJECT", {"confirmed": False, "needs_llm": False})
    out = check("不要执行", "REJECT", {"confirmed": False, "needs_llm": False})
    out = check("暂不执行", "REJECT", {"confirmed": False, "needs_llm": False})

    # 9. 完整监控查询含 ID → QUERY_MONITOR（长间隔容忍 + product_id 抽取）
    out = check("查一下销售品 900102308 的监控", "QUERY_MONITOR", {"needs_llm": False})
    assert out["entities"]["product_id"] == "900102308", out["entities"]

    # 10. 存量查询 → QNA K4
    out = check("查 5G-A融合套餐199元 的存量信息", "QNA", {"kb_target": "K4"})

    # 11. BUG① 回归：裸"执行"仅短句确认；长文档正文含"执行"不得误判为 CONFIRM_EXEC
    out = check("执行", "CONFIRM_EXEC", {"confirmed": True, "route": "B"})
    assert out["matched_rule"] == "bare-exec-short", out["matched_rule"]
    long_doc = ("套餐生效方式：新入网立即生效，当月执行过渡期资费；老用户次月1日生效，"
                "当月执行原套餐资费。退订当月费用不退还，且执行完毕后不可回退。")
    out = check(long_doc, "QNA", {"needs_llm": False})
    assert out["intent"] != "CONFIRM_EXEC", out["intent"]

    # 12. BUG② 回归：10 位服务号不可截成 9 位产品 ID
    out = check("存量销售品 4008610000 的资费信息", "QNA")
    assert out["entities"]["product_id"] == "", out["entities"]
    out = check("查一下销售品 900102308 的监控", "QUERY_MONITOR")
    assert out["entities"]["product_id"] == "900102308", out["entities"]

    # 13. BUG③ 回归：不得把"不包含…"的动词"包"抓成 offer_name（"不包"）
    out = check("上网流量不包含港澳台的资费使用范围", "QNA", {"kb_target": "K2"})
    assert out["entities"]["offer_name"] != "不包", out["entities"]

    # 14. 完整产品规格文档 → REQ_REPORT（flow-A），优先于 QNA；且不触发执行确认
    full_doc = ("一、套内资费方案（一）套餐内资费档位：199元；计费周期：周期型缴费：月；"
                "国内通用流量：120GB；国内语音拨打：1000分钟。1.国内通用流量仅限中国内地使用。"
                "（二）套餐外资费：套外流量阶梯计费，套外语音0.15元/分钟。"
                "（三）过渡期资费：当月月费按日计扣，套餐内容按天折算。"
                "（四）副卡：允许办理，共享主卡语音流量权益。"
                "四、套餐订购（二）生效方式：新入网立即生效，老用户次月1日生效。"
                "（三）套餐有效期2年，届满前30日无异议自动续展。"
                "五、套餐变更：（一）可变更至在售其他套餐，次月1日生效。"
                "六、套餐退订拆机：（一）退订允许，次月生效，当月费用不退还。")
    out = check(full_doc, "REQ_REPORT", {"needs_llm": False, "route": "A"})
    assert out["matched_rule"] == "product-doc", out["matched_rule"]
    assert out["confirmed"] is False, out["confirmed"]

    # 15. 文档检测负例：带疑问/查询意图的长文本不得误判为 REQ_REPORT
    out = check("查一下这个套餐资费如何收费，能否给出详细的计费规则说明和优惠叠加方案？", "QNA")
    assert out["intent"] != "REQ_REPORT", out["intent"]

    print("全部 %d 项调度器自测通过" % ok)


if __name__ == "__main__":
    main()
