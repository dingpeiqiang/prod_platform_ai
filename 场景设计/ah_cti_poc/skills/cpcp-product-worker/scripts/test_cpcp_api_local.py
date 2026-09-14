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

    print("全部 9 项本地自测通过")


if __name__ == "__main__":
    main()
