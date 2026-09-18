"""生成批量导入工作流的请求体（对接平台 aiFlow/workFlowScriptSaveV3 保存接口）。

用法：
  python gen_import_requests.py                      # 仅生成请求体 JSON 到 out/ 目录（默认）
  python gen_import_requests.py --post               # 直接 POST 到平台（需提供登录态 Header）
  python gen_import_requests.py --out ./myout        # 自定义输出目录

说明：
- 读取同目录下 12 个工作流 JSON，将其顶层对象作为请求 body 的 schema 字段。
- 外层补充 action / orgId / projectId（默认取自示例）与 schema 平级。
- action=A 表示新增保存；如需覆盖可自行改 --action。
- 生成模式：每个工作流生成 <flowId>__import.json（UTF-8，ensure_ascii=False），
  并在 out/_import_all.json 生成含所有请求体的数组，便于统一查看/批量提交。
- 直接调用模式：--post 时逐个 POST 到 --url，请求头从 --headers-file 读取（JSON dict，
  包含 Cookie/Content-Type 等，运行期登录态）；返回非 2xx 会记录并继续。
"""
import argparse
import json
import os
import sys
import urllib.request

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_OUT = os.path.join(BASE_DIR, "import_requests_out")
DEFAULT_URL = "https://devcloud.sitechcloud.com/km/sagentPlatform/aiFlow/workFlowScriptSaveV3"

# 示例默认值（平台会话上下文，可与 --org-id / --project-id 覆盖）
DEFAULT_ORG_ID = "10000"
DEFAULT_PROJECT_ID = "c436c568-c42c-497e-b55d-de21cd98fdd8"
DEFAULT_ACTION = "A"

# 本方案 12 个工作流：前缀 -> flowId（中文部分通过 glob 前缀解析，避免硬编码中文文件名）
WORKFLOW_PREFIXES = [
    ("wf_main_intent", "wf_main_intent_"),
    ("wf_sub_00", "wf_sub_00_"),
    ("wf_sub_01", "wf_sub_01_"),
    ("wf_sub_02", "wf_sub_02_"),
    ("wf_sub_03", "wf_sub_03_"),
    ("wf_sub_04", "wf_sub_04_"),
    ("wf_sub_05", "wf_sub_05_"),
    ("wf_sub_06", "wf_sub_06_"),
    ("wf_sub_07", "wf_sub_07_"),
    ("wf_sub_08", "wf_sub_08_"),
    ("wf_sub_09", "wf_sub_09_"),
    ("wf_sub_10", "wf_sub_10_"),
]


def build_request(flow_path, action, org_id, project_id, session_project_id=""):
    with open(flow_path, "r", encoding="utf-8") as fp:
        schema = json.load(fp)
    required = {"flowName", "flowRemark", "flowIco", "workFlowSchema", "workFlowSchemaJSON"}
    missing = required - set(schema.keys())
    if missing:
        raise ValueError("{}: schema 缺少字段 {}".format(flow_path, sorted(missing)))
    schema.setdefault("workFlowSchema", None)
    schema.setdefault("userScope", 4)
    schema["projectId"] = session_project_id if session_project_id else schema.get("projectId", "")
    return {
        "schema": schema,
        "action": action,
        "orgId": org_id,
        "projectId": project_id,
    }


def resolve_workflow_files():
    import glob as _glob
    files = []
    for flow_id, prefix in WORKFLOW_PREFIXES:
        matches = _glob.glob(os.path.join(BASE_DIR, prefix + "*.json"))
        if matches:
            files.append((flow_id, os.path.basename(matches[0])))
        else:
            print("[警告] 未找到: {}*.json".format(prefix))
    return files


def gen_requests(out_dir, action, org_id, project_id):
    os.makedirs(out_dir, exist_ok=True)
    all_requests = []
    for flow_id, filename in resolve_workflow_files():
        path = os.path.join(BASE_DIR, filename)
        if not os.path.exists(path):
            print("[跳过] 不存在: {}".format(path))
            continue
        req = build_request(path, action, org_id, project_id)
        all_requests.append({flow_id: req})
        out_file = os.path.join(out_dir, "{}__import.json".format(flow_id))
        with open(out_file, "w", encoding="utf-8") as fp:
            json.dump(req, fp, ensure_ascii=False, indent=2)
        print("[生成] {} -> {}".format(flow_id, os.path.relpath(out_file, BASE_DIR)))
    with open(os.path.join(out_dir, "_import_all.json"), "w", encoding="utf-8") as fp:
        json.dump(all_requests, fp, ensure_ascii=False, indent=2)
    print("[生成] 汇总 {} 个请求体 -> {}".format(len(all_requests), os.path.relpath(os.path.join(out_dir, "_import_all.json"), BASE_DIR)))


def do_post(req, url, headers):
    data = json.dumps(req, ensure_ascii=False).encode("utf-8")
    req_headers = dict(headers)
    req_headers.setdefault("Content-Type", "application/json;charset=UTF-8")
    request = urllib.request.Request(url, data=data, headers=req_headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=60) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            return resp.status, body
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")
    except Exception as e:
        return -1, str(e)


def post_requests(out_dir, url, headers_file):
    if not headers_file or not os.path.exists(headers_file):
        print("[错误] --post 需要 --headers-file 提供登录态请求头 JSON（含 Cookie 等）")
        sys.exit(1)
    with open(headers_file, "r", encoding="utf-8") as fp:
        headers = json.load(fp)
    summary_path = os.path.join(out_dir, "_import_all.json")
    with open(summary_path, "r", encoding="utf-8") as fp:
        items = json.load(fp)
    for item in items:
        flow_id = next(iter(item))
        req = item[flow_id]
        status, body = do_post(req, url, headers)
        status_str = str(status)
        print("[POST] {} -> {} {}".format(flow_id, status_str, body[:300] if status != 200 else "OK"))
    print("[POST] 完成，共 {} 个请求，详见上方逐条结果".format(len(items)))


def main():
    parser = argparse.ArgumentParser(description="生成/提交 工作流批量导入请求体")
    parser.add_argument("--out", default=DEFAULT_OUT, help="输出目录（默认 import_requests_out/）")
    parser.add_argument("--action", default=DEFAULT_ACTION, help="action，A=新增（默认）")
    parser.add_argument("--org-id", default=DEFAULT_ORG_ID, help="orgId（默认示例值）")
    parser.add_argument("--project-id", default=DEFAULT_PROJECT_ID, help="外层 projectId（默认示例值）")
    parser.add_argument("--session-project-id", default="", help="schema.projectId（默认沿用各文件内值）")
    parser.add_argument("--post", action="store_true", help="生成后直接 POST 到平台")
    parser.add_argument("--url", default=DEFAULT_URL, help="POST URL（默认 devcloud 保存接口）")
    parser.add_argument("--headers-file", default="", help="--post 时登录态请求头 JSON 文件路径")
    args = parser.parse_args()

    gen_requests(args.out, args.action, args.org_id, args.project_id)
    if args.post:
        post_requests(args.out, args.url, args.headers_file)


if __name__ == "__main__":
    main()
