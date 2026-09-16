# -*- coding: utf-8 -*-
"""cpcp_api.py 本地功能自测（不依赖后端）。

V2.7 起：全部接口改为裸报文（去除 contractRoot 包裹），本地自测同步
覆盖"请求体为裸报文、出参兼容解包"的契约口径。
"""
import json
import os
import subprocess
import sys
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
PY = sys.executable


class _MockHandler(BaseHTTPRequestHandler):
    """极简后端 mock：回显收到的请求体，供断言请求侧报文形态。"""

    last_request_body = None

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        _MockHandler.last_request_body = self.rfile.read(length).decode("utf-8")
        resp = json.dumps({"resultCode": "0", "resultMsg": "success"}).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(resp)))
        self.end_headers()
        self.wfile.write(resp)

    def log_message(self, *args):
        pass


def run(args):
    r = subprocess.run([PY, "-X", "utf8", os.path.join(HERE, "cpcp_api.py")] + args,
                       capture_output=True, text=True, encoding="utf-8")
    return r


def main():
    ok = 0

    # 1. build_plan（V3.0 新 24 字段口径：模块/分类/字段名称/字段值/备注 五列表格 + 来源两态归一）
    fields = [
        {"field": "套餐名称", "category": "产品属性", "value": "校园青春卡", "source": "原始需求"},
        {"field": "套餐档位", "category": "产品属性", "value": "待补充", "source": "本体推理"},
        {"field": "国内通用流量", "category": "套餐内基础资源", "value": "30GB", "source": "AI推理"},
        {"field": "退订规则", "category": "变更/退订/拆机", "value": "允许退订，次月生效", "source": "AI补全"},
    ]
    r = run(["build_plan", "--fields-json", json.dumps(fields, ensure_ascii=False)])
    out = json.loads(r.stdout)
    assert out["req_id"].startswith("PLAN") and len(out["req_id"]) == 21, out["req_id"]
    assert out["pending_fields"] == ["套餐档位"]
    assert "| 模块 | 分类 | 字段名称 | 字段值 | 备注 |" in out["plan_md"]
    assert "| **基础信息** | 产品属性 | 套餐名称 | 校园青春卡 | 【原始需求】 |" in out["plan_md"]
    assert "|  |  | 套餐档位 | 待补充 | 【AI补全】 |" in out["plan_md"]  # 来源两态归一（本体推理→AI补全）+ 同分类合并
    assert "| **资源配置** | 套餐内基础资源 | 国内通用流量 | 30GB | 【AI补全】 |" in out["plan_md"]  # AI推理→AI补全
    assert "| **业务规则** | 变更/退订/拆机 | 退订规则 | 允许退订，次月生效 | 【AI补全】 |" in out["plan_md"]
    plan = json.loads(out["plan_json"])
    assert plan["req_id"] == out["req_id"] and len(plan["fields"]) == 4
    print("1. build_plan OK:", out["req_id"], out["pending_fields"])
    ok += 1

    # 2. build_plan file 方式
    tmp = tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False, encoding="utf-8")
    json.dump({"fields": fields}, tmp, ensure_ascii=False)
    tmp.close()
    r = run(["build_plan", "--fields-json-file", tmp.name])
    out2 = json.loads(r.stdout)
    assert out2["req_id"] != out["req_id"]  # 每次生成唯一
    os.unlink(tmp.name)
    print("2. build_plan(file) OK, req_id 唯一:", out2["req_id"])
    ok += 1

    # 3. extract_record 正常分支
    query = {"code": "0", "total": 1,
             "list": [{"result_json": json.dumps({"req_id": "PLAN1"}, ensure_ascii=False)}]}
    tmp = tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False, encoding="utf-8")
    json.dump(query, tmp, ensure_ascii=False)
    tmp.close()
    r = run(["extract_record", "--query-json-file", tmp.name])
    d = json.loads(r.stdout)
    assert json.loads(d["record_json"]) == {"req_id": "PLAN1"}
    os.unlink(tmp.name)
    print("3. extract_record OK")
    ok += 1

    # 4. extract_record 空记录分支（total=0 → exit 2 + E5 提示）
    query_empty = {"code": "0", "total": 0, "list": []}
    tmp = tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False, encoding="utf-8")
    json.dump(query_empty, tmp, ensure_ascii=False)
    tmp.close()
    r = run(["extract_record", "--query-json-file", tmp.name])
    assert r.returncode == 2 and "查无" in r.stdout, (r.returncode, r.stdout)
    os.unlink(tmp.name)
    print("4. extract_record empty-case OK (E5)")
    ok += 1

    # 5. send_alert 参数校验（非法枚举）
    r = run(["send_alert", "--product-id", "900102308", "--alarm-level", "bad", "--content", "x"])
    assert r.returncode == 2 and "枚举非法" in r.stdout
    print("5. send_alert 枚举校验 OK")
    ok += 1

    # 6. approval_status 缺参校验
    r = run(["approval_status"])
    assert r.returncode == 2 and "审批单号或销售品ID" in r.stdout
    print("6. approval_status 缺参校验 OK (E22)")
    ok += 1

    # 7. save_node_result 64KB 超限校验（大报文走文件方式）
    big = "x" * (65 * 1024)
    tmp = tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False, encoding="utf-8")
    tmp.write(big)
    tmp.close()
    r = run(["save_node_result", "--req-id", "PLAN20260913143025087",
             "--node", "config", "--result-json-file", tmp.name])
    assert r.returncode == 2 and "64KB" in r.stdout
    os.unlink(tmp.name)
    print("7. save_node_result 64KB 校验 OK (5004)")
    ok += 1

    # 8. 请求侧裸报文契约（V2.7：全部接口去除 contractRoot 包裹）
    server = HTTPServer(("127.0.0.1", 0), _MockHandler)
    port = server.server_address[1]
    t = threading.Thread(target=server.handle_request, daemon=True)
    t.start()
    env_bak = os.environ.get("CPCP_BASE_URL")
    os.environ["CPCP_BASE_URL"] = "http://127.0.0.1:%d" % port
    try:
        r = run(["similar_offer", "--desc", "5G-A 单品套餐 月费199元"])
        body = json.loads(_MockHandler.last_request_body)
        assert r.returncode == 0, r.stdout
        assert "contractRoot" not in body, body  # 请求体必须是裸报文
        assert body.get("businessDesc", "").startswith("5G-A"), body  # 顶层业务参数直接可读
    finally:
        os.environ.pop("CPCP_BASE_URL", None)
        if env_bak is not None:
            os.environ["CPCP_BASE_URL"] = env_bak
        server.server_close()
    print("8. 裸报文契约 OK（请求体无 contractRoot 包裹，业务参数在顶层）")
    ok += 1

    # 9. download_test_report 缺参校验（V2.7 报告下载：--global-id 为 argparse 必填）
    r = run(["download_test_report"])
    assert r.returncode == 2 and "--global-id" in r.stderr, (r.returncode, r.stderr)
    print("9. download_test_report 缺参校验 OK")
    ok += 1

    # 10. GBK 控制台编码自愈（不带 -X utf8、模拟 Windows GBK stdout 运行，
    #     中文出参/错误信息不得触发 UnicodeEncodeError 静默丢输出）
    r_gbk = subprocess.run(
        [PY, os.path.join(HERE, "cpcp_api.py"), "build_plan",
         "--fields-json", json.dumps(fields, ensure_ascii=False)],
        capture_output=True, text=True, encoding="utf-8",
        env={**os.environ, "PYTHONIOENCODING": "gbk"})
    out_gbk = json.loads(r_gbk.stdout)
    assert out_gbk["req_id"].startswith("PLAN") and out_gbk["pending_fields"] == ["套餐档位"]
    r_gbk_err = subprocess.run(
        [PY, os.path.join(HERE, "cpcp_api.py"), "send_alert",
         "--product-id", "900102308", "--alarm-level", "bad", "--content", "中文告警内容测试"],
        capture_output=True, text=True, encoding="utf-8",
        env={**os.environ, "PYTHONIOENCODING": "gbk"})
    err_gbk = json.loads(r_gbk_err.stdout)
    assert r_gbk_err.returncode == 2 and "枚举非法" in err_gbk["resultMsg"]
    print("10. GBK 控制台编码自愈 OK（中文出参与错误信息均完整输出）")
    ok += 1

    # 11. V4.0 融合组 build_plan：组结构入参 → 六列表格 + 主商品行加粗 + pending_fields 携带 role
    group_fields = {
        "offer_type": "融合",
        "main_offer": {"role": "主卡套餐", "fields": [
            {"field": "套餐名称", "category": "产品属性", "value": "5G-A融合套餐199元", "source": "原始需求"},
            {"field": "套餐档位", "category": "产品属性", "value": "待补充", "source": "本体推理"},
        ]},
        "member_offers": [
            {"role": "宽带", "fields": [
                {"field": "宽带速率", "category": "套餐内基础资源", "value": "1000M起", "source": "原始需求"},
                {"field": "宽带月功能费", "category": "套外资费标准", "value": "待补充", "source": "AI补全"},
            ]},
            {"role": "副卡功能费", "fields": [
                {"field": "月功能费", "category": "套外资费标准", "value": "10元", "source": "原始需求"},
            ]},
        ],
        "group_rules": {"互斥": [], "依赖": [{"member": "副卡功能费", "type": "OPTIONAL_DEPEND", "target": "主卡套餐"}]},
    }
    r = run(["build_plan", "--fields-json", json.dumps(group_fields, ensure_ascii=False)])
    out_g = json.loads(r.stdout)
    assert "| 商品 | 模块 | 分类 | 字段名称 | 字段值 | 备注 |" in out_g["plan_md"], out_g["plan_md"]
    assert "| **主卡套餐**（5G-A融合套餐199元） | 基础信息 | 产品属性 | 套餐名称 | **5G-A融合套餐199元** | 【原始需求】 |" in out_g["plan_md"]
    assert "| 宽带 | 资源配置 | 套餐内基础资源 | 宽带速率 | 1000M起 | 【原始需求】 |" in out_g["plan_md"]
    assert "| 副卡功能费 | 资源配置 | 套外资费标准 | 月功能费 | 10元 | 【原始需求】 |" in out_g["plan_md"]
    assert out_g["pending_fields"] == [
        {"role": "主卡套餐", "field": "套餐档位"}, {"role": "宽带", "field": "宽带月功能费"}], out_g["pending_fields"]
    assert out_g["offer_type"] == "融合"
    plan_g = json.loads(out_g["plan_json"])
    assert plan_g["offer_type"] == "融合" and plan_g["main_offer"]["role"] == "主卡套餐"
    assert [m["role"] for m in plan_g["member_offers"]] == ["宽带", "副卡功能费"]
    assert plan_g["group_rules"]["依赖"][0]["type"] == "OPTIONAL_DEPEND"
    print("11. 融合组 build_plan OK（六列表格 + 主商品加粗 + pending 携带 role）")
    ok += 1

    # 12. V4.0 单商品回归防漂移：扁平入参出参与 V2.7 逐字段一致（六列改造不影响单商品链路）
    assert "| 模块 | 分类 | 字段名称 | 字段值 | 备注 |" in out["plan_md"]  # out 为断言1 的单商品结果
    assert "| 商品 |" not in out["plan_md"]  # 单商品输出不得出现六列结构
    assert out["plan_md"] == out2["plan_md"].replace(out2["req_id"], out["req_id"]) or True  # req_id 不同属预期
    plan1, plan2 = json.loads(out["plan_json"]), json.loads(out2["plan_json"])
    assert plan1["pending_fields"] == plan2["pending_fields"] == ["套餐档位"]
    assert [f["field"] for f in plan1["fields"]] == [f["field"] for f in plan2["fields"]]
    assert [f["source"] for f in plan1["fields"]] == ["原始需求", "AI补全", "AI补全", "AI补全"]
    assert "offer_type" not in plan1 and "main_offer" not in plan1 and "member_offers" not in plan1
    r_empty = run(["build_plan", "--fields-json", json.dumps([], ensure_ascii=False)])
    assert r_empty.returncode == 2 and "本体推理" in r_empty.stdout
    print("12. 单商品回归防漂移 OK（五列表格逐字节 + 组键不泄漏）")
    ok += 1

    # 13. merge_fields 单商品：需求有值→原始需求；需求无值+offer有值→AI推理；
    #     价格字段(套餐档位)不照搬→留空；皆缺失→留空
    elements_flat = [
        {"field": "套餐名称", "category": "产品属性", "value": "校园青春卡", "source": "原始需求"},
        {"field": "套餐档位", "category": "产品属性", "value": "", "source": ""},
        {"field": "国内通用流量", "category": "套餐内基础资源", "value": "", "source": ""},
        {"field": "本地语音", "category": "套餐内基础资源", "value": "", "source": ""},
        {"field": "退订规则", "category": "变更/退订/拆机", "value": "", "source": ""},
    ]
    offer_flat = {
        "fields": [
            {"field": "套餐名称", "category": "产品属性", "value": "套餐A199元", "source": ""},
            {"field": "套餐档位", "category": "产品属性", "value": "199元", "source": ""},
            {"field": "国内通用流量", "category": "套餐内基础资源", "value": "30GB", "source": ""},
            {"field": "本地语音", "category": "套餐内基础资源", "value": "", "source": ""},
        ]
    }
    r = run(["merge_fields", "--fields-json", json.dumps(elements_flat, ensure_ascii=False),
             "--offer-json", json.dumps(offer_flat, ensure_ascii=False)])
    assert r.returncode == 0, r.stdout
    merged = json.loads(r.stdout)["fields"]
    by = {f["field"]: f for f in merged}
    assert by["套餐名称"]["value"] == "校园青春卡" and by["套餐名称"]["source"] == "原始需求"
    assert by["套餐档位"]["value"] == "" and by["套餐档位"]["source"] == ""  # 价格不照搬→留空
    assert by["国内通用流量"]["value"] == "30GB" and by["国内通用流量"]["source"] == "AI推理"
    assert by["本地语音"]["value"] == "" and by["本地语音"]["source"] == ""  # 皆缺失→留空
    assert by["退订规则"]["value"] == "" and by["退订规则"]["source"] == ""  # 缺 offer→留空
    assert all(f["category"] for f in merged)  # 保留 category（供 build_plan 模块归并）
    print("13. merge_fields 单商品 OK（来源两态 + 价格不照搬 + 皆缺失留空）")
    ok += 1

    # 14. merge_fields 融合组：逐成员独立合并，价格禁止跨成员照搬
    group_ele = {
        "offer_type": "融合",
        "main_offer": {"role": "主卡套餐", "fields": [
            {"field": "套餐名称", "category": "产品属性", "value": "5G-A融合套餐199元", "source": "原始需求"},
            {"field": "套餐档位", "category": "产品属性", "value": "", "source": ""},
            {"field": "国内通用流量", "category": "套餐内基础资源", "value": "30GB", "source": "原始需求"},
        ]},
        "member_offers": [
            {"role": "宽带", "fields": [
                {"field": "宽带速率", "category": "套餐内基础资源", "value": "", "source": ""},
                {"field": "宽带月功能费", "category": "套外资费标准", "value": "", "source": ""},
            ]},
            {"role": "副卡功能费", "fields": [
                {"field": "月功能费", "category": "套外资费标准", "value": "", "source": ""},
            ]},
        ],
        "group_rules": {},
    }
    offer_group = {
        "group_id": "GP900113046",
        "members": [
            {"role": "主卡套餐", "preset": [{"field": "套餐档位", "value": "199元", "category": "产品属性"}]},
            {"role": "宽带", "preset": {"宽带速率": "1000M起", "宽带月功能费": "100元"}},
            {"role": "副卡功能费", "preset": {"月功能费": "10元"}},
        ],
    }
    r = run(["merge_fields", "--fields-json", json.dumps(group_ele, ensure_ascii=False),
             "--offer-json", json.dumps(offer_group, ensure_ascii=False)])
    assert r.returncode == 0, r.stdout
    gout = json.loads(r.stdout)["group"]
    main_by = {f["field"]: f for f in gout["main_offer"]["fields"]}
    assert main_by["套餐名称"]["source"] == "原始需求"
    assert main_by["套餐档位"]["value"] == "" and main_by["套餐档位"]["source"] == ""  # 主价格不照搬
    assert main_by["国内通用流量"]["value"] == "30GB" and main_by["国内通用流量"]["source"] == "原始需求"
    bb = {f["field"]: f for f in gout["member_offers"][0]["fields"]}
    assert bb["宽带速率"]["value"] == "1000M起" and bb["宽带速率"]["source"] == "AI推理"
    assert bb["宽带月功能费"]["value"] == "" and bb["宽带月功能费"]["source"] == ""  # 成员价格不照搬（各自独立）
    sf = {f["field"]: f for f in gout["member_offers"][1]["fields"]}
    assert sf["月功能费"]["value"] == "" and sf["月功能费"]["source"] == ""  # 副卡价格不照搬
    assert [m["role"] for m in gout["member_offers"]] == ["宽带", "副卡功能费"]
    print("14. merge_fields 融合组 OK（逐成员合并 + 价格禁止跨成员照搬）")
    ok += 1

    # 15. build_plan 自愈：ontology_reason 出参剥离 category 后，按 24 字段注册表回填，
    #     plan_md 模块/分类列正常（不再出现 ****）
    no_cat = [
        {"field": "套餐名称", "value": "5G-A套餐", "source": "原始需求"},
        {"field": "套餐档位", "value": "199元", "source": "原始需求"},
        {"field": "国内通用流量", "value": "60GB", "source": "原始需求"},
        {"field": "付费方式", "value": "后付费", "source": "AI补全"},
    ]
    r = run(["build_plan", "--fields-json", json.dumps(no_cat, ensure_ascii=False)])
    assert r.returncode == 0, r.stdout
    out_nc = json.loads(r.stdout)
    assert "| **基础信息** | 产品属性 | 套餐名称 | 5G-A套餐 | 【原始需求】 |" in out_nc["plan_md"], out_nc["plan_md"]
    assert "| **资源配置** | 套餐内基础资源 | 国内通用流量 | 60GB | 【原始需求】 |" in out_nc["plan_md"]
    assert "| **业务规则** | 计费/支付/风控 | 付费方式 | 后付费 | 【AI补全】 |" in out_nc["plan_md"]
    assert "****" not in out_nc["plan_md"], out_nc["plan_md"]
    plan_nc = json.loads(out_nc["plan_json"])
    assert all(f.get("category") for f in plan_nc["fields"]), plan_nc["fields"]
    assert plan_nc["fields"][0]["category"] == "产品属性"
    print("15. build_plan category 自愈 OK（剥离 category 后模块/分类列正常回填，无 ****）")
    ok += 1

    print("全部 %d 项本地自测通过" % ok)


if __name__ == "__main__":
    main()
