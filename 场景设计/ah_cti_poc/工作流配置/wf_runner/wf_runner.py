# -*- coding: utf-8 -*-
"""
工作流执行工具（wf_runner）
============================================================
与 `智能体工作流集V1.6/` 下的工作流 JSON **逻辑完全一致** 的本地执行器：
按 JSON 的 nodes/edges 逐节点调度，把每个环节的 **输入 / 输出** 显性打印出来。

设计要点
-------
* **源码驱动**：只读取生成的 `wf_sub_*.json` / `wf_main_intent_*.json`，不硬编码业务流程。
* **节点语义对齐平台**：
  - type=0 开始     → 取工作流入参（命令行 `--set k=v` 覆盖，缺省走默认种子）
  - type=3 插件     → 真实 HTTP 调用节点 url（GET/POST），不可达回退 schema 推导的 mock
  - type=6 代码     → 真实执行 JSON 内联 `async def main(args)` 源码
  - type=1 LLM      → 真实调用项目 LLM（配置读 `data-h2.sql` 种子），不可达回退 mock
  - type=2 条件分支 → 按 conditions 求值，选 sourcePort 对应出边
  - type=9 结束     → 渲染 outputs.content 占位模板
  - type=13 子流程  → 递归执行目标工作流
* **引用解析**：`{blockID, relName}` 三层一致 → 从全局上下文取上游出参值。

用法
----
    python wf_runner.py wf_sub_01 --set req_id=PLAN20260919120000123
    python wf_runner.py wf_sub_00 --set "raw_input=我要办一个家庭套餐"
    python wf_runner.py wf_main_intent --set "query=帮我配置一个套餐"
    python wf_runner.py wf_sub_01 --offline            # 全部走 mock，不联网（仅调试）
    python wf_runner.py wf_sub_01 --allow-mock         # 真实执行失败时允许静默回退 mock
    python wf_runner.py wf_sub_01 --save-json out.json # 落盘每一步 I/O
    python wf_runner.py wf_sub_01                     # 默认生成 logs/<工作流>_<流程名>_<时间戳>.log.md
    python wf_runner.py wf_sub_01 --no-log            # 不生成日志
    python wf_runner.py wf_sub_01 --log-file run.log.md  # 执行日志写入指定文件
    python wf_runner.py --list                          # 列出可用工作流

    # 批量串行验证（逐个执行全部工作流 JSON，汇总 PASS/FAIL）
    python wf_runner.py --verify                        # 真实联网 + 严格模式
    python wf_runner.py --verify --offline              # 离线 mock 回归（快进沙箱）
    python wf_runner.py --verify --log-dir logs/verify_20260922

默认行为
-------
**默认真实执行**（联网调用插件与 LLM），且**严格模式**：任一环节真实调用失败
不再静默回退 mock，而是直接报错定位，确保产出结果真实可信。需要调试时
用 `--offline`（全 mock）或 `--allow-mock`（允许回退）。
"""

from __future__ import annotations

import argparse
import asyncio
import datetime as _dt
import io
import json
import os
import random
import re
import string
import sys
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
import uuid

# Windows 控制台启用 ANSI 颜色 + UTF-8
os.system("")
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
WF_DIR = os.path.normpath(os.path.join(BASE_DIR, "..", "智能体工作流集V1.6"))
PLUGIN_DIR = os.path.normpath(os.path.join(BASE_DIR, "..", "自研插件集V1.6"))
REPO_ROOT = os.path.normpath(os.path.join(BASE_DIR, "..", "..", "..", ".."))
H2_SEED = os.path.join(
    REPO_ROOT, "backend-app", "src", "main", "resources", "sql", "h2", "data-h2.sql"
)

# ---------------------------------------------------------------- 颜色

class C:
    RESET = "\033[0m"
    BOLD = "\033[1m"
    DIM = "\033[2m"
    RED = "\033[31m"
    GREEN = "\033[32m"
    YELLOW = "\033[33m"
    BLUE = "\033[34m"
    MAGENTA = "\033[35m"
    CYAN = "\033[36m"
    GREY = "\033[90m"

    @classmethod
    def disable(cls):
        for k in list(vars(cls)):
            if k.isupper():
                setattr(cls, k, "")


TYPE_NAME = {
    0: "开始", 1: "LLM", 2: "条件分支", 3: "插件",
    6: "代码", 9: "结束", 13: "子流程",
}
TYPE_COLOR = {
    0: C.CYAN, 1: C.MAGENTA, 2: C.YELLOW,
    3: C.BLUE, 6: C.GREEN, 9: C.CYAN, 13: C.MAGENTA,
}

MAX_PRINT_LEN = 1600


def _fmt_value(v) -> str:
    """把任意出参值格式化成便于阅读的一行/多行文本。"""
    if v is None:
        return "null"
    if isinstance(v, str):
        return v
    try:
        return json.dumps(v, ensure_ascii=False, indent=2)
    except Exception:
        return str(v)


def _log_value(v) -> str:
    """日志用值格式化：dict/list 走缩进 JSON，字符串原样（完整不截断）。"""
    if v is None:
        return "null"
    if isinstance(v, str):
        return v if v != "" else "(空字符串)"
    try:
        return json.dumps(v, ensure_ascii=False, indent=2)
    except Exception:
        return str(v)


class RunLogger:
    """执行日志落盘器：记录每个节点的**完整**输入/输出（不截断）与结束结论。

    输出为 markdown 文本，便于人工阅读与问题定位：
    - 头部：时间、工作流、入参、LLM/离线模式
    - 每节点：序号/标题/类型/状态/耗时/note + 全量输入 + 全量输出
    - 尾部：执行汇总 + 结束节点内容
    """

    def __init__(self, path: str):
        self.path = path
        self._buf = []
        d = os.path.dirname(os.path.abspath(path))
        if d and not os.path.isdir(d):
            os.makedirs(d, exist_ok=True)

    def _w(self, text: str = ""):
        self._buf.append(text)

    def header(self, wf: "Workflow", initial: dict, mode: str):
        self._w(f"# 工作流执行日志：{wf.flow_name}")
        self._w()
        self._w(f"- **执行时间**：{_dt.datetime.now().isoformat(timespec='seconds')}")
        self._w(f"- **工作流**：`{wf.flow_id}`（文件 {wf.name}）")
        self._w(f"- **节点数/边数**：{len(wf.nodes)} / {len(wf.edges)}")
        self._w(f"- **执行模式**：{mode}")
        if initial:
            self._w("- **入口入参**：")
            for k, v in initial.items():
                self._w(f"    - `{k}` = {_log_value(v)}")
        self._w()
        self._w("---")
        self._w()

    def node(self, res: "NodeResult", header: str = ""):
        hdr = header or TYPE_NAME.get(res.ntype, str(res.ntype))
        self._w(f"## [{res.seq}] {res.title}")
        self._w()
        self._w(f"- **类型**：{hdr}（type={res.ntype}）")
        self._w(f"- **状态**：{res.status}")
        self._w(f"- **耗时**：{res.elapsed_ms} ms")
        if res.note:
            self._w(f"- **note**：{res.note}")
        self._w()
        self._w(f"### 输入（{len(res.raw_inputs)} 项）")
        self._w()
        if not res.raw_inputs:
            self._w("（无）")
        for nm, desc, val in res.raw_inputs:
            self._w(f"**{nm}**  `{desc}`")
            self._w()
            self._w("```text")
            self._w(_log_value(val))
            self._w("```")
            self._w()
        self._w(f"### 输出（{len(res.outputs)} 项）")
        self._w()
        if not res.outputs:
            self._w("（无）")
        for k, v in res.outputs.items():
            self._w(f"**{k}**")
            self._w()
            self._w("```text")
            self._w(_log_value(v))
            self._w("```")
            self._w()
        self._w("---")
        self._w()

    def summary(self, results: list, end_results: list):
        ok = sum(1 for r in results if r["status"] == "ok")
        fb = sum(1 for r in results if r["status"] == "fallback")
        err = sum(1 for r in results if r["status"] == "error")
        self._w("## 执行汇总")
        self._w()
        self._w(f"- 节点总数：{len(results)}")
        self._w(f"- 成功：{ok}")
        self._w(f"- 回退：{fb}")
        self._w(f"- 错误：{err}")
        self._w()
        for res in end_results:
            self._w(f"### 结束节点 [{res.seq}] {res.title}")
            self._w()
            self._w("```text")
            self._w(_log_value(res.outputs.get("content", "")))
            self._w("```")
            self._w()

    def write(self):
        with io.open(self.path, "w", encoding="utf-8") as fp:
            fp.write("\n".join(self._buf))
        return self.path


def _clip(text: str, limit: int = MAX_PRINT_LEN) -> str:
    if len(text) <= limit:
        return text
    return text[:limit] + f"\n{C.GREY}…（已截断，共 {len(text)} 字符）{C.RESET}"


def _indent(text: str, prefix: str = "    ") -> str:
    return "\n".join(prefix + line for line in text.split("\n"))


# ---------------------------------------------------------------- 数据模型

class NodeResult:
    """单个节点的执行结果：出参字典 + 元信息。"""

    def __init__(self, node_id, seq, title, ntype):
        self.node_id = node_id
        self.seq = seq
        self.title = title
        self.ntype = ntype
        self.outputs: dict = {}
        self.inputs: dict = {}
        self.status = "ok"          # ok | fallback | error | skipped
        self.note = ""
        self.elapsed_ms = 0
        self.raw_inputs: list = []  # 打印用：[(name, desc, value)]

    def to_dict(self):
        return {
            "seq": self.seq,
            "node_id": self.node_id,
            "title": self.title,
            "type": self.ntype,
            "status": self.status,
            "note": self.note,
            "elapsed_ms": self.elapsed_ms,
            "inputs": self.inputs,
            "outputs": self.outputs,
        }


# ---------------------------------------------------------------- LLM 配置

class LlmConfig:
    """
    复用项目 LLM 配置：解析 `backend-app/.../data-h2.sql` 中
    `pd_ai_llm_user_configs` 里 `is_active=1` 的种子记录。
    """

    def __init__(self, model="", base_url="", api_key="", auth_type="bearer",
                 auth_header="", is_full_url=True, temperature=0.3, max_tokens=4096):
        self.model = model
        self.base_url = base_url
        self.api_key = api_key
        self.auth_type = auth_type
        self.auth_header = auth_header
        self.is_full_url = is_full_url
        self.temperature = temperature
        self.max_tokens = max_tokens

    @classmethod
    def from_h2_seed(cls, path: str = H2_SEED) -> "LlmConfig | None":
        if not os.path.isfile(path):
            return None
        try:
            sql = io.open(path, encoding="utf-8", errors="ignore").read()
        except Exception:
            return None
        cols = ["user_identifier", "provider", "model", "api_key", "base_url",
                "auth_type", "api_format", "is_full_url", "temperature",
                "max_tokens", "thinking", "stream_enabled", "max_input_tokens",
                "is_active"]
        best = None
        for block in re.findall(r"MERGE INTO pd_ai_llm_user_configs\s*\((.*?)\)\s*KEY",
                                sql, re.S | re.I):
            try:
                stmt_start = sql.upper().find(block.upper())
                body = sql[stmt_start: stmt_start + 3000]
                vals = re.search(r"SELECT\s+(.*?)\s+WHERE", body, re.S | re.I)
                if not vals:
                    continue
                raw = _split_sql_values(vals.group(1))
                if len(raw) < len(cols):
                    continue
                row = dict(zip(cols, raw))
                if str(row.get("is_active", "0")).strip() != "1":
                    continue
                cfg = cls(
                    model=row.get("model", "").strip(),
                    base_url=row.get("base_url", "").strip(),
                    api_key=row.get("api_key", "").strip(),
                    auth_type=row.get("auth_type", "bearer").strip() or "bearer",
                    auth_header=row.get("auth_header", "").strip(),
                    is_full_url=str(row.get("is_full_url", "0")).strip() == "1",
                    temperature=float(row.get("temperature") or 0.3),
                    max_tokens=int(float(row.get("max_tokens") or 4096)),
                )
                if cfg.model and cfg.base_url:
                    best = cfg
                    break
            except Exception:
                continue
        return best

    def completions_url(self) -> str:
        base = self.base_url.rstrip("/")
        if self.is_full_url:
            return base + "/chat/completions"
        if base.endswith("/v1"):
            return base + "/chat/completions"
        return base + "/v1/chat/completions"

    def headers(self) -> dict:
        h = {"Content-Type": "application/json"}
        if self.auth_type == "custom" and self.auth_header:
            h[self.auth_header] = self.api_key
        else:
            h["Authorization"] = "Bearer " + self.api_key
        return h


def _split_sql_values(text: str) -> list:
    """切分 SQL SELECT 的值列表，正确处理单引号字符串与逗号。"""
    out, buf, in_str, i = [], [], False, 0
    while i < len(text):
        ch = text[i]
        if in_str:
            if ch == "'":
                if i + 1 < len(text) and text[i + 1] == "'":
                    buf.append("'")
                    i += 2
                    continue
                in_str = False
            else:
                buf.append(ch)
        else:
            if ch == "'":
                in_str = True
            elif ch == ",":
                out.append("".join(buf).strip())
                buf = []
            else:
                buf.append(ch)
        i += 1
    if buf:
        out.append("".join(buf).strip())
    return out


class LlmClient:
    """OpenAI 兼容的非流式调用，带重试与回退标记。"""

    def __init__(self, cfg: LlmConfig | None, offline=False, timeout=120):
        self.cfg = cfg
        self.offline = offline
        self.timeout = timeout

    def chat(self, prompt: str, system: str = "",
             temperature: float | None = None,
             max_tokens: int | None = None) -> tuple[str, bool, str]:
        """返回 (text, is_fallback, note)。

        temperature / max_tokens 为节点级覆盖值（平台节点可声明），
        缺省回退全局 LLM 配置。
        """
        if self.offline or self.cfg is None:
            return self._mock(prompt), True, "offline/mock"
        messages = []
        if system:
            messages.append({"role": "system", "content": system})
        messages.append({"role": "user", "content": prompt})
        payload = {
            "model": self.cfg.model,
            "messages": messages,
            "temperature": self.cfg.temperature if temperature is None else temperature,
            "max_tokens": self.cfg.max_tokens if max_tokens is None else max_tokens,
            "stream": False,
        }
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        req = urllib.request.Request(self.cfg.completions_url(), data=data,
                                     headers=self.cfg.headers(), method="POST")
        last_err = ""
        for attempt in range(2):
            try:
                with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                    body = json.loads(resp.read().decode("utf-8", "replace"))
                text = _extract_chat_text(body)
                if text:
                    return text, False, f"live:{self.cfg.model}"
                last_err = "空返回"
            except urllib.error.HTTPError as e:
                last_err = f"HTTP {e.code}"
                if e.code in (401, 403, 404):
                    break
            except Exception as e:
                last_err = repr(e)
            time.sleep(0.5 * (attempt + 1))
        return self._mock(prompt), True, f"回退mock({last_err})"

    @staticmethod
    def _mock(prompt: str) -> str:
        head = prompt.strip().split("\n")[0][:60]
        return (f"【离线模拟LLM输出】\n基于输入生成结构化结果（提示词首行：{head}）。\n"
                f"（未联网或网关不可达，此为占位内容，字段结构与真实出参一致。）")


def _extract_chat_text(body: dict) -> str:
    try:
        choices = body.get("choices") or []
        if choices:
            msg = choices[0].get("message") or {}
            return msg.get("content") or ""
        for key in ("content", "text", "result", "data"):
            if isinstance(body.get(key), str):
                return body[key]
    except Exception:
        pass
    return ""


# ---------------------------------------------------------------- 插件调用

class PluginRegistry:
    """加载 `自研插件集V1.6/*_export*.json`，提供出参 schema（供 mock 生成）。"""

    def __init__(self, plugin_dir=PLUGIN_DIR):
        self.by_code = {}
        self.by_title = {}
        self._load(plugin_dir)

    def _load(self, d):
        if not os.path.isdir(d):
            return
        for fn in sorted(os.listdir(d)):
            if not fn.endswith(".json") or "export" not in fn:
                continue
            try:
                obj = json.load(io.open(os.path.join(d, fn), encoding="utf-8"))
                fj = obj.get("flowJson")
                fj = json.loads(fj) if isinstance(fj, str) else fj
                if not fj:
                    continue
                code = (fj.get("nodeMeta") or {}).get("code") or ""
                title = (fj.get("nodeMeta") or {}).get("title") or ""
                outs = []
                for root in fj.get("outputs", []):
                    outs.extend(root.get("sechema", []) or [])
                meta = {"code": code, "title": title, "outs": outs,
                        "url": obj.get("interfaceAddress", "")}
                if code:
                    self.by_code[code] = meta
                if title:
                    self.by_title[title] = meta
            except Exception:
                continue

    def schema_of(self, code, title=""):
        return self.by_code.get(code) or self.by_title.get(title)


class PluginCaller:
    """真实 HTTP 调用节点 url；失败时按出参 schema 推导 mock。"""

    def __init__(self, registry: PluginRegistry, offline=False, timeout=60,
                 base_url_override=""):
        self.registry = registry
        self.offline = offline
        self.timeout = timeout
        self.base_url_override = base_url_override

    def call(self, node, inputs: dict) -> tuple[dict, bool, str]:
        """返回 (outputs, is_fallback, note)。"""
        url = node.get("url") or ""
        if self.base_url_override and url:
            url = re.sub(r"^https?://[^/]+", self.base_url_override.rstrip("/"), url)
        method = (node.get("submit_way") or "post").lower()
        code = (node.get("nodeMeta") or {}).get("code") or ""
        title = (node.get("nodeMeta") or {}).get("title") or ""
        declared = [o.get("name") for o in node.get("outputs", []) if isinstance(o, dict)]

        if not self.offline and url:
            try:
                outs = self._http(url, method, inputs, node)
                if outs is not None:
                    return self._align(outs, declared), False, f"live:{method} {url}"
                note = "HTTP 返回无法解析"
            except urllib.error.HTTPError as e:
                note = f"HTTP {e.code}"
                if os.environ.get("WF_DEBUG_HTTP"):
                    sys.stderr.write("[DEBUG http-%s] %s\n%s\n" % (
                        e.code, url, e.read().decode("utf-8", "replace")))
            except Exception as e:
                note = repr(e)
        else:
            note = "offline"
        outs = self._mock(code, title, declared)
        return outs, True, f"回退mock({note})"

    def _http(self, url, method, inputs, node=None):
        if method == "get":
            qs = urllib.parse.urlencode({k: _to_str(v) for k, v in inputs.items()})
            sep = "&" if "?" in url else "?"
            full = url + (sep + qs if qs else "")
            req = urllib.request.Request(full, method="GET",
                                         headers={"Accept": "application/json"})
        elif node is not None and _is_multipart_node(node):
            req = self._build_multipart_request(url, inputs, node)
        else:
            data = json.dumps(inputs, ensure_ascii=False).encode("utf-8")
            req = urllib.request.Request(
                url, data=data, method="POST",
                headers={"Content-Type": "application/json;charset=UTF-8",
                         "Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=self.timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
        try:
            body = json.loads(raw)
        except Exception:
            return None
        # 解开常见包裹：{data:{...}} / {body:{...}} / {result:{...}}
        for key in ("data", "body", "result", "OUT_DATA"):
            if isinstance(body.get(key), dict) and len(body) <= 3:
                body = body[key]
                break
        if not isinstance(body, dict):
            return None
        return body

    def _build_multipart_request(self, url, inputs, node):
        """按节点声明的 multipart 入参组装表单：文件项挂占位文件，其余为普通字段。"""
        file_params = _multipart_file_params(node)
        boundary = "----wfrunner" + uuid.uuid4().hex
        buf = io.BytesIO()
        for name, value in inputs.items():
            if name in file_params:
                part = file_params[name]
                filename, content, ctype = _placeholder_upload(name, value)
                buf.write(f"--{boundary}\r\n".encode("utf-8"))
                buf.write(
                    f'Content-Disposition: form-data; name="{part}"; '
                    f'filename="{filename}"\r\n'.encode("utf-8"))
                buf.write(f"Content-Type: {ctype}\r\n\r\n".encode("utf-8"))
                buf.write(content)
                buf.write(b"\r\n")
            else:
                buf.write(f"--{boundary}\r\n".encode("utf-8"))
                buf.write(
                    f'Content-Disposition: form-data; name="{name}"\r\n\r\n'
                    .encode("utf-8"))
                buf.write(_to_str(value).encode("utf-8"))
                buf.write(b"\r\n")
        buf.write(f"--{boundary}--\r\n".encode("utf-8"))
        payload = buf.getvalue()
        if os.environ.get("WF_DEBUG_HTTP"):
            sys.stderr.write("[DEBUG multipart] boundary=%s\n%s\n" % (boundary, payload.decode("utf-8", "replace")))
        return urllib.request.Request(
            url, data=payload, method="POST",
            headers={"Content-Type": f"multipart/form-data; boundary={boundary}",
                     "Accept": "application/json"})

    @staticmethod
    def _align(outs: dict, declared: list) -> dict:
        """只保留节点声明的出参；缺失的补空字符串。"""
        if not declared:
            return outs
        return {k: outs.get(k, "") for k in declared}

    def _mock(self, code, title, declared) -> dict:
        meta = self.registry.schema_of(code, title)
        result = {}
        names = declared or ([c.get("name") for c in meta["outs"]] if meta else [])
        out_schema = {c.get("name"): c for c in (meta["outs"] if meta else [])}
        for name in names:
            sch = out_schema.get(name)
            result[name] = _mock_by_schema(sch, name, code)
        return result


_MULTIPART_HINTS = ("multipart", "form-data")
_FILE_HINTS = ("multipart", "会话文件", "excel", "xlsx", "文件")


def _is_multipart_node(node) -> bool:
    """节点是否要求 multipart/form-data：显式 declares/consumes，或任入参描述含 multipart。"""
    if node.get("multipart") is True:
        return True
    consumes = str(node.get("consumes") or "").lower()
    if any(h in consumes for h in _MULTIPART_HINTS):
        return True
    for item in node.get("inputs", []):
        if not isinstance(item, dict):
            continue
        desc = str(item.get("description") or "").lower()
        if any(h in desc for h in _MULTIPART_HINTS):
            return True
    return False


def _multipart_file_params(node) -> dict:
    """从节点入参识别文件项，返回 {入参名: multipart 部件名}。

    部件名优先取入参显式声明的 part_name（如后端 @RequestParam("file")）；
    未声明时默认与入参同名。识别依据：描述含文件语义，或名称启发式。
    """
    files = {}
    for item in node.get("inputs", []):
        if not isinstance(item, dict):
            continue
        name = item.get("name") or ""
        if not name:
            continue
        part = item.get("part_name") or name
        desc = str(item.get("description") or "").lower()
        if any(h in desc for h in _FILE_HINTS):
            files[name] = part
    if files:
        return files
    for item in node.get("inputs", []):
        if not isinstance(item, dict):
            continue
        name = item.get("name") or ""
        low = name.lower()
        if low in ("file", "files", "upload", "attachment") or low.endswith("_file"):
            files[name] = item.get("part_name") or name
    return files


def _placeholder_upload(name, value):
    """为 multipart 文件项生成占位文件：返回 (filename, bytes, content_type)。"""
    if isinstance(value, str) and value.strip():
        filename = os.path.basename(value.strip())
    else:
        filename = (name or "upload") + ".xlsx"
    if not filename.lower().endswith((".xlsx", ".xls")):
        filename += ".xlsx"
    content = b"PK\x03\x04 wf_runner placeholder upload"
    return filename, content, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"


def _to_str(v):
    if isinstance(v, str):
        return v
    if isinstance(v, (dict, list)):
        return json.dumps(v, ensure_ascii=False)
    return "" if v is None else str(v)


def _mock_by_schema(sch, name, code=""):
    """按出参 schema 生成确定性的示例值。"""
    if not sch:
        return _mock_scalar(name)
    t = sch.get("type", "string")
    if t == "array":
        item = (sch.get("sechema") or [{}])[0]
        leaf = {c.get("name"): c.get("description", "") for c in item.get("sechema", [])}
        return [_mock_object(leaf, name)]
    if t == "object":
        leaf = {c.get("name"): c.get("description", "") for c in sch.get("sechema", [])}
        return _mock_object(leaf, name)
    return _mock_scalar(name)


def _mock_object(leaf_desc: dict, parent: str) -> dict:
    return {k: _mock_scalar(k) for k in leaf_desc}


def _mock_scalar(name: str):
    n = (name or "").lower()
    if n in ("code", "resultcode", "status"):
        return "0"
    if n in ("msg", "resultmsg", "message"):
        return "ok"
    if n in ("total", "count", "testcasecount", "successtestcasecount",
             "failtestcasecount", "sort", "nodes", "nodeseq"):
        return 1
    if n.endswith("_list") or n in ("list",):
        return []
    if n in ("pass",):
        return "1"
    if "url" in n:
        return "https://example.invalid/mock/" + (name or "file")
    if "id" in n:
        return "MOCK-" + "".join(random.choices(string.digits, k=6))
    if "json" in n:
        return json.dumps({"mock": True, "field": name}, ensure_ascii=False)
    if "time" in n or "date" in n:
        return _dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    return f"【mock】{name}"


# ---------------------------------------------------------------- 代码节点执行

class CodeExecutor:
    """真实执行 JSON 内联 `async def main(args)` 源码。

    fast_forward=True 时进入「快进沙箱」：把 asyncio/time 的 sleep 置为瞬时返回，
    并把 urllib 出站调用替换成会立即得到终态响应的 mock，使轮询/等待类
    代码节点一步收敛（不改变代码逻辑本身，只替换副作用）。

    注意：代码节点源码顶层会 `import asyncio` / `import urllib.request`，
    因此必须在 exec 期间用 import hook 拦截这两个模块，否则内联 import
    会把沙箱替换重新绑回真实模块。
    """

    def __init__(self, offline=False, fast_forward=False):
        self._compiled = {}
        self.offline = offline
        self.fast_forward = offline or fast_forward

    def run(self, node, inputs: dict):
        src = node.get("code") or ""
        if not src.strip():
            return {o.get("name"): "" for o in node.get("outputs", [])}, "空源码"
        node_id = node.get("id")
        try:
            # 缓存键绑定源码：不同流程存在相同 node_id 的代码节点（如各子流节点 106），
            # 仅按 node_id 缓存会导致跨流程复用错误源码，故以 (node_id, src) 为键。
            cache_key = (node_id, src)
            compiled = self._compiled.get(cache_key)
            if compiled is None:
                compiled = compile(src, f"<code_node {node_id}>", "exec")
                self._compiled[cache_key] = compiled
        except SyntaxError as e:
            raise RuntimeError(f"代码节点编译失败: {e}")

        namespace = {"__name__": "__wf_node__"}
        hook = None
        if self.fast_forward:
            hook = _install_shims()
        try:
            exec(compiled, namespace)
            main = namespace.get("main")
            if not callable(main):
                return {}, "未定义 main(args)"
            args = _Args(inputs)
            if asyncio.iscoroutinefunction(main):
                out = _run_coro(main(args))
            else:
                out = main(args)
                if asyncio.iscoroutine(out):
                    out = _run_coro(out)
        finally:
            if hook is not None:
                hook.uninstall()
        if out is None:
            out = {}
        if isinstance(out, dict):
            pass
        elif hasattr(out, "__dict__"):
            out = dict(out.__dict__)
        else:
            raise RuntimeError(f"代码节点返回值类型不支持: {type(out)}")
        note = "内联 Python（快进沙箱）" if self.fast_forward else ""
        return out, note


class _Args:
    """平台代码节点入参对象：`args.params` 为 dict。"""

    def __init__(self, params: dict):
        self.params = params


# ---------------- 离线快进沙箱：让轮询/等待类代码节点一步收敛 ----------------

class _Patch:
    """把 asyncio.sleep / time.sleep / urllib.request.urlopen 临时替换为快进实现。

    不替换 sys.modules 中的模块对象（避免破坏解释器与其他库），
    只在 exec 期间就地改写属性，结束后精确还原。
    """

    def __init__(self):
        self._saved = []

    def install(self):
        import urllib.request as _ureq
        self._saved = [
            (asyncio, "sleep", asyncio.sleep),
            (time, "sleep", time.sleep),
            (_ureq, "urlopen", _ureq.urlopen),
        ]
        asyncio.sleep = _short_sleep
        time.sleep = _short_time_sleep
        _ureq.urlopen = _fast_urlopen
        return self

    def uninstall(self):
        for obj, name, value in self._saved:
            setattr(obj, name, value)
        self._saved = []


async def _short_sleep(*a, **k):
    return None


def _short_time_sleep(*a, **k):
    return None


def _fast_urlopen(req, *a, **k):
    """出站调用替身：轮询/状态/结果类返回终态，一步结束循环。"""
    url = req.full_url if hasattr(req, "full_url") else str(req)
    u = url.lower()
    if "progress" in u:
        payload = {"done": "true", "failed": "false", "failIndex": "-1"}
    elif "approval" in u or "status" in u:
        payload = {"status": "approved", "approved": "true", "code": "0", "msg": "ok"}
    elif "download" in u:
        payload = {"code": "0", "msg": "ok", "backend_pending": "1",
                   "download_url": "", "url": "", "note": "下载端点暂不可达，报告/脚本未生成"}
    elif "result" in u or "query" in u:
        payload = {"code": "0", "msg": "ok", "total": 0, "list": [],
                   "data": {}, "record_json": ""}
    else:
        payload = {"code": "0", "msg": "ok"}
    return _MockHttpResponse(json.dumps(payload, ensure_ascii=False).encode("utf-8"))


class _MockHttpResponse:
    def __init__(self, payload: bytes, status=200):
        self._payload = payload
        self.status = status

    def read(self):
        return self._payload

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


def _install_shims():
    return _Patch().install()


def _run_coro(coro):
    """在新事件循环中执行协程（兼容 Py3.10+，避免 get_event_loop 弃用告警）。"""
    loop = asyncio.new_event_loop()
    try:
        return loop.run_until_complete(coro)
    finally:
        loop.close()


# ---------------------------------------------------------------- 工作流

class Workflow:
    def __init__(self, path: str):
        self.path = path
        self.name = os.path.basename(path)
        raw = json.load(io.open(path, encoding="utf-8"))
        self.flow_name = raw.get("flowName", "")
        schema = raw.get("workFlowSchemaJSON") or raw.get("workFlowSchema") or {}
        if isinstance(schema, str):
            schema = json.loads(schema)
        self.schema = schema
        self.nodes = {n["id"]: n for n in schema.get("nodes", [])}
        self.flow_id = schema.get("flowId", "") or os.path.splitext(self.name)[0]
        self.edges = []
        for e in schema.get("edges", []):
            src = e.get("startId") or e.get("source")
            dst = e.get("endId") or e.get("target")
            port = e.get("sourcePort", None)
            self.edges.append({"start": src, "end": dst, "port": port})
        self._out_edges = {}
        for e in self.edges:
            self._out_edges.setdefault(e["start"], []).append(e)
        self._in_edges = {}
        for e in self.edges:
            self._in_edges.setdefault(e["end"], []).append(e)

    def start_node(self):
        for n in self.nodes.values():
            if n.get("type") == 0:
                return n
        # 兼容无 type=0：取无入边的节点
        for nid, n in self.nodes.items():
            if nid not in self._in_edges:
                return n
        return None

    def next_nodes(self, node_id):
        return self._out_edges.get(node_id, [])


def seq_of(node_id: str) -> int:
    try:
        return int(node_id.rsplit("-", 1)[-1])
    except Exception:
        return 0


# ---------------------------------------------------------------- 执行器

class WorkflowRunner:
    """核心执行器：读 JSON → 逐节点执行 → 显性打印 I/O。"""

    def __init__(self, registry=None, llm=None, offline=False, save_json="",
                 verbose=True, show_values=True, live_plugins=True, color=True,
                 fast_forward=False, log_file="", strict=False):
        self.registry = registry or PluginRegistry()
        self.llm = llm
        self.offline = offline
        self.strict = strict
        self.fast_forward = offline or fast_forward
        self.save_json = save_json
        self.verbose = verbose
        self.show_values = show_values
        self.color = color
        self.log_file = log_file
        self.logger = RunLogger(log_file) if log_file else None
        self.plugin_caller = PluginCaller(self.registry, offline=offline)
        self.code_executor = CodeExecutor(offline=offline, fast_forward=fast_forward)
        self.workflows = {}
        self.contexts = {}     # flow_id -> {node_id: {relName: value}}
        self.results = []      # 打印/落盘用的执行记录
        if not color:
            C.disable()

    def load(self, name: str) -> Workflow:
        if name in self.workflows:
            return self.workflows[name]
        path = self._resolve_path(name)
        wf = Workflow(path)
        self.workflows[name] = wf
        return wf

    @staticmethod
    def _resolve_path(name: str) -> str:
        if os.path.isfile(name):
            return name
        if not name.endswith(".json"):
            for fn in os.listdir(WF_DIR):
                if fn.startswith(name) and fn.endswith(".json"):
                    return os.path.join(WF_DIR, fn)
        p = os.path.join(WF_DIR, name)
        if os.path.isfile(p):
            return p
        available = sorted(f for f in os.listdir(WF_DIR)
                           if f.startswith(("wf_sub_", "wf_main")) and f.endswith(".json"))
        raise FileNotFoundError(
            f"未找到工作流: {name}\n目录: {WF_DIR}\n可用: "
            + ", ".join(os.path.splitext(f)[0] for f in available))

    # -------------------------------------------------- 主执行入口

    def run(self, name: str, initial: dict, max_steps=200, depth=0) -> dict:
        wf = self.load(name)
        ctx: dict = {}
        self.contexts[wf.flow_id] = ctx
        prefix = "  " * depth
        # 顶层每次 run 重置执行记录，避免多次编排时节点总数跨流程累计
        # （嵌套子流程 depth>0 继续追加到同一份 results）
        if depth == 0:
            self.results = []

        if self.verbose and depth == 0:
            self._banner(wf)
        if self.logger and depth == 0:
            mode = "离线 mock" if self.offline else (
                f"实时（LLM={getattr(self.llm.cfg, 'model', '') if self.llm else '无配置'}）")
            self.logger.header(wf, initial, mode)

        start = wf.start_node()
        if start is None:
            raise RuntimeError(f"{name}: 未找到开始节点")

        # 开始节点：组装工作流入参
        self._run_start(wf, start, initial, ctx, prefix, depth)

        # 拓扑遍历
        pending = [e["end"] for e in wf.next_nodes(start["id"])]
        visited = {start["id"]}
        steps = 0
        end_results = []
        while pending:
            steps += 1
            if steps > max_steps:
                raise RuntimeError(f"{name}: 超过最大步数 {max_steps}（疑似环）")
            node_id = pending.pop(0)
            node = wf.nodes.get(node_id)
            if node is None:
                continue
            ntype = node.get("type")
            res = self._run_node(wf, node, ctx, prefix, depth)

            if ntype == 9:
                end_results.append(res)
                continue

            nxt = self._next_edges(wf, node, res)
            for e in nxt:
                if e["end"] not in visited:
                    visited.add(e["end"])
                    pending.append(e["end"])

        if self.verbose and depth == 0:
            self._summary(wf, end_results)
        if self.save_json:
            self._save()
        if self.logger and depth == 0:
            self.logger.summary(self.results, end_results)
            path = self.logger.write()
            if self.verbose:
                print(f"{C.GREY}执行日志已写入: {path}{C.RESET}")
        return ctx

    # -------------------------------------------------- 开始节点

    def _run_start(self, wf, node, initial, ctx, prefix, depth):
        seq = seq_of(node["id"])
        title = (node.get("nodeMeta") or {}).get("title", "开始节点")
        res = NodeResult(node["id"], seq, title, 0)
        declared = node.get("inputs", []) or []
        for ip in declared:
            nm = ip.get("name")
            if nm in initial:
                val = initial[nm]
            else:
                val = self._default_input(nm, ip)
            res.inputs[nm] = val
            res.outputs[nm] = val  # 入参即出参，供下游引用
            res.raw_inputs.append((nm, ip.get("description", ""), val))
        ctx[node["id"]] = res.outputs
        res.note = f"入参 {len(res.inputs)} 项"
        self._emit(res, prefix, depth, header="开始")

    @staticmethod
    def _default_input(nm, ip):
        if nm == "req_id":
            return "PLAN" + _dt.datetime.now().strftime("%Y%m%d%H%M%S") + \
                   "".join(random.choices(string.digits, k=3))
        if nm == "chat_id":
            return "CHAT-MOCK-0001"
        # 其余按描述给一份中文默认样例
        return f"【样例】{nm}（可 --set {nm}=... 覆盖）"

    # -------------------------------------------------- 单节点分发

    def _run_node(self, wf, node, ctx, prefix, depth) -> NodeResult:
        ntype = node.get("type")
        seq = seq_of(node["id"])
        title = (node.get("nodeMeta") or {}).get("title", "")
        res = NodeResult(node["id"], seq, title, ntype)
        # 解析入参
        res.inputs = self._resolve_inputs(wf, node, ctx)
        res.raw_inputs = self._input_rows(node, res.inputs)
        t0 = time.time()
        try:
            if ntype == 3:
                outs, fb, note = self.plugin_caller.call(node, _ref_inputs(node, res.inputs))
                if fb and self.strict:
                    raise RuntimeError(f"插件真实调用失败且未允许回退 mock：{note}")
                res.outputs = outs
                res.status = "fallback" if fb else "ok"
                res.note = note
            elif ntype == 6:
                outs, note = self.code_executor.run(node, res.inputs)
                res.outputs = outs
                res.status = "ok"
                res.note = note or "内联 Python 执行"
            elif ntype == 1:
                outs, fb, note = self._run_llm(node, res.inputs)
                if fb and self.strict:
                    raise RuntimeError(f"LLM 真实调用失败且未允许回退 mock：{note}")
                res.outputs = outs
                res.status = "fallback" if fb else "ok"
                res.note = note
            elif ntype == 2:
                res.outputs = res.inputs
                res.note = "条件求值"
            elif ntype == 9:
                res.outputs = {"content": self._render_end(node, res.inputs)}
                res.note = "结束渲染"
            elif ntype == 13:
                outs, note = self._run_subflow(node, res.inputs, depth)
                res.outputs = outs
                res.note = note
            else:
                res.status = "skipped"
                res.note = f"未支持类型 {ntype}"
        except Exception as e:
            res.status = "error"
            res.note = f"{type(e).__name__}: {e}"
            if self.verbose and depth == 0:
                print(f"{C.RED}    ! 执行异常{traceback.format_exc()}{C.RESET}")
        res.elapsed_ms = int((time.time() - t0) * 1000)
        ctx[node["id"]] = res.outputs
        self._emit(res, prefix, depth)
        return res

    # -------------------------------------------------- 入参解析

    def _resolve_inputs(self, wf, node, ctx) -> dict:
        out = {}
        inputs = node.get("inputs")
        if node.get("type") == 1 and isinstance(inputs, dict):
            rows = inputs.get("inputParameters") or []
        elif node.get("type") == 2 and isinstance(inputs, dict):
            # 条件分支：入参按 condition 组的 left 引用（blockID/relName）解析
            conds = inputs.get("condition") or inputs.get("conditions") or []
            for group in conds:
                if not isinstance(group, dict):
                    continue
                for it in group.get("conditions") or []:
                    left = it.get("left") or {}
                    block = left.get("blockID") or ""
                    rel = left.get("relName") or ""
                    if block and rel:
                        out[rel] = (ctx.get(block) or {}).get(rel, "")
            return out
        else:
            rows = inputs if isinstance(inputs, list) else []
        for ip in rows:
            if not isinstance(ip, dict):
                continue
            nm = ip.get("name")
            block = ip.get("blockID") or ""
            rel = ip.get("relName") or ""
            if block and rel:
                out[nm] = (ctx.get(block) or {}).get(rel, "")
            elif block and not rel:
                out[nm] = ctx.get(block, "")
            else:
                out[nm] = ip.get("content", "")
        return out

    @staticmethod
    def _input_rows(node, resolved):
        rows = []
        inputs = node.get("inputs")
        if node.get("type") == 1 and isinstance(inputs, dict):
            src = inputs.get("inputParameters") or []
        else:
            src = inputs if isinstance(inputs, list) else []
        for ip in src:
            if not isinstance(ip, dict):
                continue
            nm = ip.get("name")
            desc = ip.get("description", "")
            kind = "引用" if ip.get("blockID") else "常量"
            rows.append((nm, f"[{kind}] {desc}", resolved.get(nm)))
        return rows

    # -------------------------------------------------- LLM

    def _run_llm(self, node, inputs):
        llm_in = node.get("inputs") or {}
        prompt_tpl = ""
        params = llm_in.get("llmParam") or []
        if params:
            prompt_tpl = params[0].get("content", "")
        prompt = _render_template(prompt_tpl, inputs)
        system = node.get("prompt_system") or ""
        if self.llm is None:
            if self.strict:
                raise RuntimeError("未读取到 LLM 配置（data-h2.sql），严格模式下不允许 mock")
            text = LlmClient._mock(prompt)
            outs = {o.get("name"): text for o in node.get("outputs", [])}
            return outs, True, "无LLM配置(回退mock)"
        text, fb, note = self.llm.chat(prompt, system,
                                       temperature=node.get("temperature"),
                                       max_tokens=node.get("max_tokens"))
        names = [o.get("name") for o in node.get("outputs", [])] or ["content"]
        outs = {}
        for nm in names:
            outs[nm] = _bind_llm_output(text, nm)
        return outs, fb, note

    # -------------------------------------------------- 子流程

    def _run_subflow(self, node, inputs, depth):
        wf_id = (node.get("nodeMeta") or {}).get("workFlowId") or ""
        if not wf_id:
            return {}, "未声明 workFlowId"
        try:
            sub_ctx = self.run(wf_id, inputs, depth=depth + 1)
        except Exception as e:
            return {}, f"子流程执行失败: {e}"
        outs = {}
        for o in node.get("outputs", []):
            outs[o.get("name")] = ""
        return outs, f"子流程 {wf_id} 完成"

    # -------------------------------------------------- 条件分支

    def _next_edges(self, wf, node, res):
        edges = wf.next_nodes(node["id"])
        if node.get("type") != 2:
            return edges
        chosen_port = self._eval_selector(node, res)
        matched = [e for e in edges if _port_eq(e.get("port"), chosen_port)]
        if matched:
            return matched
        # 兜底：所有出边
        return edges

    def _eval_selector(self, node, res) -> int:
        """按条件组求值选源端口。

        平台约定（见 selector_node2）：真实条件定义在 port=-1（否则分支组，
        语义上承载"特殊分支"如 有待补充/审批通过/异常），port=0 为兜底出边。
        因此：条件命中 → 走该组端口（-1 或 0）；全部未命中 → 走 port=0 兜底，
        绝不能默认落回 -1，否则未命中也会误入特殊分支。
        """
        inputs = node.get("inputs") or {}
        conds = inputs.get("condition") or inputs.get("conditions") or []
        if isinstance(conds, dict):
            conds = conds.get("conditions") or []
        for group in conds:
            if not isinstance(group, dict):
                continue
            port = group.get("sourcePort", 0)
            items = group.get("conditions") or []
            if all(self._eval_cond(it, res.inputs) for it in items):
                return port
        return 0

    @staticmethod
    def _eval_cond(item, values) -> bool:
        left = item.get("left") or {}
        op = item.get("operator")
        right = (item.get("right") or {}).get("content", "")
        lv = values.get(left.get("relName"))
        ls = _to_str(lv)
        if op == 1:
            return ls == right
        if op == 2:
            return ls != right
        if op == 3:
            return len(ls) > _num(right)
        if op == 4:
            return len(ls) >= _num(right)
        if op == 5:
            return len(ls) < _num(right)
        if op == 6:
            return len(ls) <= _num(right)
        if op == 7:
            return right in ls
        if op == 8:
            return right not in ls
        if op == 9:
            return ls == ""
        if op == 10:
            return ls != ""
        if op == 15:
            return len(ls) == _num(right)
        return False

    # -------------------------------------------------- 结束节点

    @staticmethod
    def _render_end(node, inputs) -> str:
        content = (node.get("outputs") or {}).get("content", "")
        return _render_template(content, inputs)

    # -------------------------------------------------- 打印

    def _banner(self, wf):
        print()
        print(f"{C.BOLD}{C.CYAN}╔{'═' * 76}╗{C.RESET}")
        print(f"{C.BOLD}{C.CYAN}║ {C.RESET}{C.BOLD}工作流执行：{wf.flow_name}  "
              f"{C.GREY}({wf.flow_id}){C.RESET}")
        print(f"{C.BOLD}{C.CYAN}║ {C.RESET}{C.GREY}文件: {wf.name} · "
              f"节点 {len(wf.nodes)} · 边 {len(wf.edges)}{C.RESET}")
        print(f"{C.BOLD}{C.CYAN}╚{'═' * 76}╝{C.RESET}")

    def _emit(self, res: NodeResult, prefix, depth, header=""):
        # 非 verbose（批量验证）时只记录结果，不逐节点打印
        if not self.verbose:
            self.results.append(res.to_dict())
            if self.logger:
                hdr = header or TYPE_NAME.get(res.ntype, str(res.ntype))
                self.logger.node(res, header=hdr)
            return
        color = TYPE_COLOR.get(res.ntype, "")
        tag = {"ok": f"{C.GREEN}OK{C.RESET}",
               "fallback": f"{C.YELLOW}回退{C.RESET}",
               "error": f"{C.RED}错误{C.RESET}",
               "skipped": f"{C.GREY}跳过{C.RESET}"}.get(res.status, res.status)
        hdr = header or TYPE_NAME.get(res.ntype, str(res.ntype))
        line = (f"{prefix}{C.BOLD}{color}[{res.seq}]{C.RESET} "
                f"{C.BOLD}{res.title}{C.RESET} "
                f"{C.GREY}<{hdr}·type={res.ntype}>{C.RESET} [{tag}] "
                f"{C.GREY}{res.elapsed_ms}ms{C.RESET}")
        print(line)
        if res.note:
            print(f"{prefix}{C.GREY}  note: {res.note}{C.RESET}")

        if self.show_values:
            self._print_io("输入", res.raw_inputs, prefix)
            outs = [(k, "", v) for k, v in res.outputs.items()]
            self._print_io("输出", outs, prefix)
        print()
        self.results.append(res.to_dict())
        if self.logger:
            self.logger.node(res, header=hdr)

    def _print_io(self, label, rows, prefix):
        if not rows:
            return
        color = C.CYAN if label == "输入" else C.GREEN
        print(f"{prefix}  {color}├─ {label} ({len(rows)}){C.RESET}")
        for nm, desc, val in rows:
            txt = _fmt_value(val)
            txt = _clip(txt)
            first, *rest = txt.split("\n")
            print(f"{prefix}  {color}│{C.RESET} {C.BOLD}{nm}{C.RESET} "
                  f"{C.GREY}{desc}{C.RESET}")
            print(f"{prefix}  {color}│{C.RESET}   {first}")
            for r in rest:
                print(f"{prefix}  {color}│{C.RESET}   {r}")

    def _summary(self, wf, end_results):
        ok = sum(1 for r in self.results if r["status"] == "ok")
        fb = sum(1 for r in self.results if r["status"] == "fallback")
        err = sum(1 for r in self.results if r["status"] == "error")
        sep = f"{C.BOLD}{C.CYAN}" + "─" * 78 + f"{C.RESET}"
        print(sep)
        print(f"{C.BOLD}执行完成{C.RESET}  节点 {len(self.results)}  "
              f"{C.GREEN}成功 {ok}{C.RESET}  "
              f"{C.YELLOW}回退 {fb}{C.RESET}  "
              f"{C.RED}错误 {err}{C.RESET}")
        for res in end_results:
            content = res.outputs.get("content", "")
            print()
            print(f"{C.BOLD}{C.CYAN}【结束节点 {res.seq}】{res.title}{C.RESET}")
            print(_clip(content, 3000))
        print(sep)

    def _save(self):
        payload = {
            "generated_at": _dt.datetime.now().isoformat(timespec="seconds"),
            "results": self.results,
        }
        with io.open(self.save_json, "w", encoding="utf-8") as fp:
            json.dump(payload, fp, ensure_ascii=False, indent=2)
        print(f"{C.GREY}已落盘: {self.save_json}{C.RESET}")


# ---------------------------------------------------------------- 模板与工具

_PLACEHOLDER = re.compile(r"\{([A-Za-z_][A-Za-z0-9_]*)\}")


def _render_template(tpl: str, values: dict) -> str:
    if not tpl:
        return ""
    def repl(m):
        key = m.group(1)
        v = values.get(key, "")
        return _to_str(v)
    return _PLACEHOLDER.sub(repl, tpl)


def _bind_llm_output(text: str, name: str) -> str:
    """LLM 出参绑定契约：优先按出参名取 JSON 键；回退解析标签式文本；否则整段文本。"""
    raw = text.strip()
    # ① JSON 对象（可含代码块围栏 / 前后缀文本）
    candidate = raw
    if candidate.startswith("```"):
        candidate = re.sub(r"^```[a-zA-Z]*\s*", "", candidate)
        candidate = re.sub(r"\s*```$", "", candidate).strip()
    s, e = candidate.find("{"), candidate.rfind("}")
    if s != -1 and e > s:
        # 容忍模型尾部多余的闭合括号/空白：从右端逐步收缩再解析
        frag = candidate[s:e + 1]
        for cut in range(len(frag), s, -1):
            piece = frag[:cut - s].rstrip()
            if not piece.endswith("}"):
                continue
            try:
                obj = json.loads(piece)
            except Exception:
                continue
            if isinstance(obj, dict) and name in obj:
                return _clean_label(_to_str(obj[name]), name)
    # ② 标签式文本回退：形如 "template_id：xxx" / "1. template_id: xxx"
    m = re.search(r"(?:^|\n)\s*(?:\d+[.、]\s*)?%s\s*[:：]\s*(.+?)(?=\n\s*(?:\d+[.、]\s*)?\w+\s*[:：]|\Z)"
                  % re.escape(name), raw, re.S)
    if m:
        return _clean_label(m.group(1).strip(), name)
    return _clean_label(raw, name)


def _clean_label(value: str, name: str) -> str:
    """剥离残留的出参名标签前缀与行尾多余标签。"""
    v = value.strip()
    v = re.sub(r"^(?:%s)\s*[:：]\s*" % re.escape(name), "", v).strip()
    return v


def _ref_inputs(node, resolved: dict) -> dict:
    """插件请求体：只取叶子参数（与平台 toolJson.parameters 口径一致）。"""
    return {k: v for k, v in resolved.items()}


def _port_eq(a, b) -> bool:
    try:
        return int(a if a is not None else 0) == int(b)
    except Exception:
        return a == b


def _num(s, default=0):
    try:
        return int(str(s).strip())
    except Exception:
        return default


# ---------------------------------------------------------------- CLI

def _list_workflows():
    files = sorted(f for f in os.listdir(WF_DIR)
                   if f.endswith(".json") and f.startswith(("wf_sub_", "wf_main")))
    print(f"{C.BOLD}可用工作流（{WF_DIR}）{C.RESET}")
    for f in files:
        try:
            wf = Workflow(os.path.join(WF_DIR, f))
            print(f"  {C.CYAN}{os.path.splitext(f)[0]}{C.RESET}  "
                  f"{wf.flow_name}  {C.GREY}({len(wf.nodes)} 节点){C.RESET}")
        except Exception as e:
            print(f"  {f}  {C.RED}解析失败: {e}{C.RESET}")


def _parse_sets(pairs):
    out = {}
    for p in pairs or []:
        if "=" not in p:
            continue
        k, v = p.split("=", 1)
        out[k.strip()] = v
    return out


def _resolve_log_path(log_arg: str, workflow: str) -> str:
    """解析日志文件路径。

    - 未指定（空）→ 不写日志，返回 ""
    - "-" 或 "auto" 或目录形式 → 自动生成 logs/<工作流>_<流程名>_<时间戳>.log.md
    - 其它 → 视为显式文件路径
    """
    if not log_arg:
        return ""
    if log_arg in ("-", "auto") or log_arg.endswith(("/", "\\")):
        wf_name = os.path.splitext(os.path.basename(workflow or "workflow"))[0]
        flow_name = ""
        try:
            flow_name = Workflow(WorkflowRunner._resolve_path(workflow)).flow_name or ""
        except Exception:
            pass
        flow_name = re.sub(r'[\\/:*?"<>|]', "_", flow_name).strip(" _")
        stem = f"{wf_name}_{flow_name}" if flow_name and flow_name != wf_name else wf_name
        ts = _dt.datetime.now().strftime("%Y%m%d_%H%M%S")
        return os.path.join(BASE_DIR, "logs", f"{stem}_{ts}.log.md")
    return log_arg


# ---------------------------------------------------------------- 链式串跑（--chain）

# 演示剧本 V3.0「演示准备」节定义的链式顺序：需求→需求审批→生成→稽核发起→稽核轮询
# →资费校准→测试发起→测试轮询→上线审批→监控运维，传递同一 req_id 复现真实业务链路。
# 顺序遵循剧本「确认执行」路由（02→03→05→04：资费校准先于自动测试；稽核/测试轮询
# 紧随各自发起环节）。旁路工作流 08/09/10/11 不进入主干链（可按需单独执行）。
CHAIN_ORDER = [
    "wf_sub_00", "wf_sub_01", "wf_sub_02", "wf_sub_03", "wf_sub_12",
    "wf_sub_05", "wf_sub_04", "wf_sub_13", "wf_sub_06", "wf_sub_07",
]

# 需要跨工作流传递的上下文键（前一环节的输出 → 后一环节的入参）。
# req_id 贯穿全链；offer_id/approval_id/globalId 在对应环节产出后被后续环节复用。
CHAIN_CARRY_KEYS = ("req_id", "offer_id", "approval_id", "globalId")


def _chain_seed(name: str, carried: dict, start_inputs: list,
                overrides: dict = None) -> dict:
    """按工作流开始节点声明的入参，从已携带的上下文里挑选可用值。

    仅传递开始在节点中**声明**的键，避免把无关键注入工作流；req_id 若已在上下文中
    则原样透传（保证全链同一 req_id），否则由 Runner 生成新值并回填上下文。

    overrides（``{工作流名: {K: V}}``）中针对本工作流（或通配键 ``"*"``）的入参
    优先级最高，用于预置需求文本、分支数据等演示入参。
    """
    declared = {ip.get("name") for ip in (start_inputs or [])}
    seed = {}
    for k in CHAIN_CARRY_KEYS:
        if k in declared and carried.get(k) not in (None, ""):
            seed[k] = carried[k]
    if overrides:
        for src in ("*", name):
            for k, v in (overrides.get(src) or {}).items():
                if k in declared:
                    seed[k] = v
    return seed


def _parse_chain_overrides(pairs) -> dict:
    """解析 --chain-set：支持 ``K=V``（作用于首环节）与 ``wf_sub_XX:K=V``（指定环节）。"""
    out = {}
    for p in pairs or []:
        if "=" not in p:
            continue
        lhs, v = p.split("=", 1)
        if ":" in lhs:
            wf_name, k = lhs.split(":", 1)
            out.setdefault(wf_name.strip(), {})[k.strip()] = v
        else:
            out.setdefault("*", {})[lhs.strip()] = v
    return out


def _gateway_base(name: str, base_url: str = "") -> str:
    """取工作流内首个插件节点的 URL 主机（网关 BASE_URL），用于演示预置接口调用。

    与插件节点一致：若命令行传了 --base-url，则以该地址整体替换主机名，便于指向本地后端验证。
    """
    path = WorkflowRunner._resolve_path(name)
    wf = Workflow(path)
    for n in wf.nodes.values():
        url = str(n.get("url") or "")
        m = re.match(r"^(https?://[^/]+)", url)
        if m:
            base = m.group(1)
            if base_url:
                return re.sub(r"^https?://[^/]+", base_url.rstrip("/"), base)
            return base
    return ""


def _http_post_json(url: str, body: dict, timeout: int = 30) -> dict:
    """POST JSON 并解析响应（演示预置钩子用；失败返回带 error 的字典，不抛异常）。"""
    payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=payload, method="POST",
                                 headers={"Content-Type": "application/json; charset=utf-8",
                                          "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8", "replace")
        return json.loads(raw) if raw else {}
    except Exception as e:  # noqa: BLE001 演示钩子：失败不阻断主链
        return {"error": f"{type(e).__name__}: {e}"}


def _chain_prehook(name: str, carried: dict, overrides: dict,
                   base_url: str = "") -> None:
    """链式执行前的演示预置钩子（幂等、失败不阻断主链）。

    目前仅 wf_sub_07：``--chain-set wf_sub_07:inject_monitor_error=1`` 时，按下游已携带的
    offer_id 调用后端注入接口，使监控查询返回 ``error_count>0``，复现「异常告警闭环」分支；
    传 ``=0`` 则解除注入（回到「正常-运营报告」分支）。
    """
    if name != "wf_sub_07":
        return
    settings = (overrides.get("wf_sub_07") or {}) if overrides else {}
    flag = str(settings.get("inject_monitor_error", "")).strip().lower()
    if flag not in ("1", "true", "yes", "on", "0", "false", "no", "off"):
        return
    offer_id = carried.get("offer_id")
    if not offer_id:
        print(f"    {C.YELLOW}⚠ 监控异常注入跳过：上下文无 offer_id{C.RESET}")
        return
    base = _gateway_base(name, base_url)
    if not base:
        print(f"    {C.YELLOW}⚠ 监控异常注入跳过：未解析到网关地址{C.RESET}")
        return
    enable = flag in ("1", "true", "yes", "on")
    resp = _http_post_json(base + "/api/v1/appstore/product/monitor/inject",
                           {"offer_id": offer_id, "enable": enable})
    if resp.get("error"):
        print(f"    {C.YELLOW}⚠ 监控异常注入失败：{resp['error']}{C.RESET}")
    else:
        state = "开启" if enable else "解除"
        print(f"    {C.GREY}监控异常注入已{state}（offer_id={offer_id}，"
              f"error_count={resp.get('error_count_injected', '')}）{C.RESET}")


def _chain_start_inputs(name: str) -> list:
    """读取工作流开始节点声明的入参列表（用于决定可注入的链路键）。"""
    path = WorkflowRunner._resolve_path(name)
    wf = Workflow(path)
    start = wf.start_node()
    return (start.get("inputs") if start else []) or []


class ChainResult:
    """链式串跑中单个环节的执行结论。"""

    def __init__(self, name: str):
        self.name = name
        self.flow_name = ""
        self.total = 0
        self.ok = 0
        self.fallback = 0
        self.error = 0
        self.elapsed_ms = 0
        self.passed = False
        self.error_msg = ""
        self.log_path = ""
        self.outputs = {}

    def verdict(self) -> str:
        return "PASS" if self.passed else "FAIL"


def _chain_one(name: str, runner_kwargs: dict, log_dir: str,
               carried: dict, overrides: dict = None) -> ChainResult:
    """串跑链中单个工作流：注入携带上下文，执行后回收其出参回填上下文。

    判定与 --verify 一致（实时严格 / 离线宽松）。每个工作流各写一份日志，
    并在头部标注其在链中的序号与注入入参。overrides 为演示预置入参
    （``{工作流名|"*": {K: V}}``），优先级最高。
    """
    cr = ChainResult(name)
    t0 = time.time()
    allow_fb = bool(runner_kwargs.get("offline")) or not runner_kwargs.get("strict", True)
    try:
        log_file = _resolve_log_path("-", name)
        if log_dir:
            log_file = os.path.join(log_dir, os.path.basename(log_file))
        cr.log_path = log_file
        runner = WorkflowRunner(log_file=log_file, **runner_kwargs)
        wf = runner.load(name)
        cr.flow_name = wf.flow_name
        seed = _chain_seed(name, carried, _chain_start_inputs(name), overrides)
        ctx = runner.run(name, seed)
        cr.total = len(runner.results)
        cr.ok = sum(1 for r in runner.results if r["status"] == "ok")
        cr.fallback = sum(1 for r in runner.results if r["status"] == "fallback")
        cr.error = sum(1 for r in runner.results if r["status"] == "error")
        cr.passed = cr.error == 0 and (allow_fb or cr.fallback == 0)
        if not cr.passed:
            bad = [r for r in runner.results if r["status"] == "error"
                   or (not allow_fb and r["status"] == "fallback")]
            first = bad[0] if bad else None
            if first:
                cr.error_msg = f"{first['title']}[{first['status']}]: {first.get('note', '')}"
        # 回收出参：从结束节点 outputs 抽取链路键，回填上下文供下游使用
        cr.outputs = _collect_chain_outputs(ctx)
        for k in CHAIN_CARRY_KEYS:
            if cr.outputs.get(k) not in (None, ""):
                carried[k] = cr.outputs[k]
    except KeyboardInterrupt:
        raise
    except Exception as e:
        cr.passed = False
        cr.error_msg = f"{type(e).__name__}: {e}"
    cr.elapsed_ms = int((time.time() - t0) * 1000)
    return cr


def _collect_chain_outputs(ctx: dict) -> dict:
    """从各节点输出中汇总链路键（后出现的非空值覆盖先前值）。"""
    found = {}
    for outs in ctx.values():
        if not isinstance(outs, dict):
            continue
        for k in CHAIN_CARRY_KEYS:
            v = outs.get(k)
            if v not in (None, ""):
                found[k] = v
    return found


def _run_chain(args) -> int:
    """链式串跑入口：按 CHAIN_ORDER 顺序执行，全程传递同一 req_id。

    返回退出码（有 FAIL 则 1）。日志行为与 --verify 一致：每个工作流各写一份。
    """
    names = list(getattr(args, "chain_order", None) or CHAIN_ORDER)
    names = [n for n in names if _workflow_exists(n)]
    if not names:
        print(f"{C.RED}未发现链式工作流（{WF_DIR}）{C.RESET}")
        return 1

    llm_cfg = LlmConfig.from_h2_seed()
    if args.model and llm_cfg:
        llm_cfg.model = args.model
    llm = None if args.offline else LlmClient(llm_cfg, offline=args.offline)

    runner_kwargs = dict(
        registry=PluginRegistry(), llm=llm, offline=args.offline,
        save_json=args.save_json, verbose=False, show_values=False,
        color=not args.no_color, fast_forward=args.fast_forward,
        strict=not args.offline and not args.allow_mock,
    )
    log_dir = args.log_dir or ""

    # 演示预置入参：--chain-text 注入首环节需求文本；--chain-set/--set 指定任意环节入参
    overrides = _parse_chain_overrides(getattr(args, "chain_set", None))
    chain_text = getattr(args, "chain_text", "") or ""
    if chain_text:
        overrides.setdefault(names[0], {})["requirement_text"] = chain_text
    for kv in getattr(args, "set", None) or []:
        if "=" in kv:
            k, v = kv.split("=", 1)
            overrides.setdefault(names[0], {})[k.strip()] = v


    # 初始上下文：预置一个贯穿全链的 req_id（后端校验 PLAN\\d{17}）
    carried = {"req_id": "PLAN" + _dt.datetime.now().strftime("%Y%m%d%H%M%S") +
               "".join(random.choices(string.digits, k=3))}
    print(f"{C.GREY}链路 req_id = {carried['req_id']}{C.RESET}")

    results = []
    for i, name in enumerate(names, 1):
        seed_preview = _chain_seed(name, carried, _chain_start_inputs(name), overrides)
        print(f"\n{C.BOLD}[{i}/{len(names)}]{C.RESET} {C.CYAN}{name}{C.RESET} ... 执行中"
              f"  {C.GREY}入参 {seed_preview}{C.RESET}", flush=True)
        _chain_prehook(name, carried, overrides, getattr(args, "base_url", "") or "")
        cr = _chain_one(name, runner_kwargs, log_dir, carried, overrides)
        results.append(cr)
        tag = f"{C.GREEN}PASS{C.RESET}" if cr.passed else f"{C.RED}FAIL{C.RESET}"
        detail = (f"{C.GREY}节点 {cr.total} · OK {cr.ok} · 回退 {cr.fallback} · "
                  f"错误 {cr.error} · {cr.elapsed_ms}ms{C.RESET}")
        print(f"    → {tag}  {C.BOLD}{cr.flow_name}{C.RESET}  {detail}")
        if cr.passed and cr.outputs:
            print(f"    {C.GREY}出参: {cr.outputs}{C.RESET}")
        if not cr.passed and cr.error_msg:
            print(f"    {C.RED}✗ {cr.error_msg}{C.RESET}")
        if cr.log_path:
            print(f"    {C.GREY}日志: {cr.log_path}{C.RESET}")

    passed = sum(1 for r in results if r.passed)
    failed = len(results) - passed
    print()
    print(f"{C.BOLD}{C.CYAN}" + "═" * 78 + f"{C.RESET}")
    print(f"{C.BOLD}链路汇总{C.RESET}  环节 {len(results)}  "
          f"{C.GREEN}PASS {passed}{C.RESET}  {C.RED}FAIL {failed}{C.RESET}")
    print(f"{C.BOLD}{C.CYAN}" + "─" * 78 + f"{C.RESET}")
    print(f"{'环节':<28}{'结论':<8}{'节点':>6}{'OK':>6}{'回退':>6}{'错误':>6}{'耗时ms':>10}")
    for r in results:
        verdict_plain = "PASS" if r.passed else "FAIL"
        verdict_col = (f"{C.GREEN}PASS{C.RESET}" if r.passed else f"{C.RED}FAIL{C.RESET}")
        pad = " " * max(0, 8 - len(verdict_plain))
        print(f"{r.name:<28}{verdict_col}{pad}{r.total:>6}{r.ok:>6}{r.fallback:>6}"
              f"{r.error:>6}{r.elapsed_ms:>10}")
    if failed:
        print(f"\n{C.RED}失败环节：{C.RESET}")
        for r in results:
            if not r.passed:
                print(f"  - {C.BOLD}{r.name}{C.RESET}  {C.RED}{r.error_msg}{C.RESET}")
    print(f"{C.BOLD}{C.CYAN}" + "═" * 78 + f"{C.RESET}")
    return 1 if failed else 0


def _workflow_exists(name: str) -> bool:
    try:
        WorkflowRunner._resolve_path(name)
        return True
    except Exception:
        return False


# ---------------------------------------------------------------- 对话式编排（--demo）

# 演示剧本 V3.0 的用户回合脚本：每条为 (幕, 用户消息, 语义断言标签列表)。
# 断言由 runner 侧确定性校验（不依赖 LLM 自述），覆盖确认门禁、逐环节推进、
# 轮询可重复、结果表达对应、职责不越界等「脚本反向速查表」要点。
DEMO_TURNS = [
    ("幕1", "我要新增一个家庭融合套餐：月费199元，含300M宽带、IPTV、2张副卡，主卡流量100G。",
     ["intent_req_submit", "stage_1", "point_confirm"]),
    ("幕2", "帮我直接配置落地，不用确认了。",
     ["gate_refuse_direct_config"]),
    ("幕2", "发起需求审批。",
     ["call_wf_sub_11"]),
    ("幕2", "生成加载方案。",
     ["call_wf_sub_01", "point_confirm"]),
    ("幕3", "确认配置，开始执行。",
     ["gate_confirm_exec", "call_wf_sub_02", "stage_3", "carry_req_id"]),
    ("幕3", "下一步。",
     ["call_wf_sub_03", "stage_4a", "point_feedback"]),
    ("幕3", "查询稽核结果。",
     ["call_wf_sub_12", "stage_4b", "poll_repeatable"]),
    ("幕3", "下一步。",
     ["call_wf_sub_05", "stage_5"]),
    ("幕3", "下一步。",
     ["call_wf_sub_04", "stage_6a"]),
    ("幕3", "查询测试结果。",
     ["call_wf_sub_13", "stage_6b", "poll_repeatable"]),
    ("幕3", "发起上线审批。",
     ["call_wf_sub_06", "stage_7", "summary_block"]),
    ("幕4", "查询审批进度。",
     ["call_wf_sub_08"]),
    ("幕5", "查询监控运维。",
     ["call_wf_sub_07", "stage_8"]),
    ("幕5B", "查询存量产品：5G畅享融合套餐。",
     ["call_wf_sub_09"]),
    ("幕5C", "对存量产品做合规扫描。",
     ["call_wf_sub_10"]),
]


class DemoAssertion:
    """单轮对话的语义断言结果。"""

    def __init__(self, stage: str, user_msg: str, labels: list):
        self.stage = stage
        self.user_msg = user_msg
        self.labels = labels
        self.decision = {}
        self.reply = ""
        self.checks = []          # [(label, ok, detail)]
        self.executed = []        # [{"name","passed","total","ok","fallback","error","elapsed_ms"}]

    @property
    def ok(self) -> bool:
        return all(c[1] for c in self.checks) if self.checks else True


def _wf_code(name: str) -> str:
    """把 LLM 返回的工作流标识（可能是文件名 stem ``wf_sub_01_需求分析`` 或裸码
    ``wf_sub_01``）归一化为裸码 ``wf_sub_01``，供断言与执行使用。"""
    s = (name or "").strip()
    m = re.match(r"^(wf_(?:sub|main)_\d+)", s)
    return m.group(1) if m else s


def _reply_text(text: str) -> str:
    """若 LLM 回复意外为 JSON 协议体，取出其 reply 字段作为用户可见正文。"""
    obj = _extract_json_object(text)
    if isinstance(obj, dict) and isinstance(obj.get("reply"), str) and obj["reply"].strip():
        return obj["reply"].strip()
    return (text or "").strip()


def _extract_json_object(text: str) -> dict | None:
    """从 LLM 回复中提取首个平衡大括号 JSON 对象（严格 JSON 单对象协议）。"""
    if not text:
        return None
    s = text.strip()
    # 优先整体解析
    try:
        obj = json.loads(s)
        return obj if isinstance(obj, dict) else None
    except Exception:
        pass
    # 回落：扫描首个平衡 {} 块
    start = s.find("{")
    while start != -1:
        depth = 0
        for i in range(start, len(s)):
            if s[i] == "{":
                depth += 1
            elif s[i] == "}":
                depth -= 1
                if depth == 0:
                    frag = s[start:i + 1]
                    try:
                        obj = json.loads(frag)
                        if isinstance(obj, dict):
                            return obj
                    except Exception:
                        break
        start = s.find("{", start + 1)
    return None


def _demo_prompt_path() -> str:
    return os.path.join(BASE_DIR, "logs", "_prompt_22.txt")


def _load_orchestrator_prompt(names: list) -> str:
    """构造编排者 system prompt：内置主智能体提示词（§2.2）+ 已发布子工作流清单。

    优先读取已抽取的 `logs/_prompt_22.txt`（§2.2 原文）；缺失则内置精简兜底提示词，
    保证离线/无素材时仍可运行（但联网实跑应使用原文）。
    """
    base = ""
    p = _demo_prompt_path()
    if os.path.isfile(p):
        try:
            base = io.open(p, encoding="utf-8").read().strip()
        except Exception:
            base = ""
    if not base:
        base = (
            "你是安徽电信产销品域数字员工。只做三件事：①按【意图→工作流映射表】语义识别直调"
            "子工作流；②业务理解/补全；③结果表达。确定性细节（req_id 生成与透传、环节落库、"
            "成败判定、续跑、审批硬校验）由子工作流/代码节点/后端完成，你不得构造或绕过。\n"
            "限制：执行方案必须止于确认点；确认语义未命中绝不进入执行主干；需求工单审批须用户"
            "明确确认后才发起；执行主干逐环节推进，每环节止于反馈点；结果表达必须与子流返回一一"
            "对应，禁止虚构；异常即中断引导重新执行。"
        )
    listing = "、".join(names)
    proto = f"""

【已发布子工作流清单（可直调）】
{listing}

【输出协议（必须严格遵守）】
你的每一轮回复**只能输出一个 JSON 对象**，不得包含任何 JSON 之外的文字、注释或代码块标记。
JSON 字段：
{{
  "action": "call_workflow" | "reply_only",
  "workflow": "wf_sub_XX",          // action=call_workflow 时必填，取自上方清单
  "reply": "面向用户的自然语言回复（含环节结果/下一步建议/确认提示）",
  "reason": "简要说明你的编排判断依据"
}}
规则：需要执行动作时用 call_workflow；仅需回复/拒绝/引导时用 reply_only（此时 workflow 留空）。
确认门禁未命中（如用户要求跳过确认直接落地）必须 reply_only 并拒绝推进，不得直调执行主干子流。

【执行主干固定顺序（用户每回复一次推进一个环节，严禁跳步/并行）】
确认配置 → wf_sub_02（智能配置）→ wf_sub_03（稽核发起）→ wf_sub_05（资费校准）→ wf_sub_04（自动测试）。
用户回复「下一步/继续」时，必须严格按此顺序推进「尚未执行」的下一个环节：
- 已执行 02 后 → 下一步应直调 wf_sub_03；
- 已执行 03 后 → 下一步应直调 wf_sub_05（查询稽核结果 wf_sub_12 仅在用户明确说「查询稽核结果」时调用）；
- 已执行 05 后 → 下一步应直调 wf_sub_04；
- 已执行 04 后 → 下一步应提示可查询测试结果或发起上线审批。
「查询稽核结果/查询测试结果」等显式查询指令才直调 wf_sub_12/wf_sub_13；「下一步/继续」只按上表推进主干环节。
"""
    return base + proto


def _build_demo_decision_prompt(history: list, user_msg: str, carried: dict,
                                last_result: dict, done: list) -> str:
    """把对话历史 + 当前上下文 + 已完成环节 + 上一轮执行结果拼成给 LLM 的 user prompt。"""
    lines = ["【会话上下文（由子工作流产出，仅供你判断，不得改写）】",
             f"req_id={carried.get('req_id', '')}",
             f"offer_id={carried.get('offer_id', '')}",
             f"approval_id={carried.get('approval_id', '')}",
             f"已成功执行环节={('、'.join(done) if done else '（无）')}",
             "", "【近期对话】"]
    for role, text in history[-8:]:
        who = "用户" if role == "user" else "数字员工"
        lines.append(f"{who}：{text[:400]}")
    if last_result:
        lines.append("")
        lines.append("【上一轮子工作流执行结果】")
        lines.append(f"workflow={last_result.get('name', '')} "
                     f"passed={last_result.get('passed', '')} "
                     f"总节点={last_result.get('total', 0)} "
                     f"OK={last_result.get('ok', 0)} "
                     f"回退={last_result.get('fallback', 0)} "
                     f"错误={last_result.get('error', 0)}")
        outs = last_result.get("outputs") or {}
        if outs:
            lines.append(f"出参={json.dumps(outs, ensure_ascii=False)[:600]}")
    lines.append("")
    lines.append(f"【用户本轮消息】{user_msg}")
    lines.append("")
    lines.append("请严格按输出协议，仅返回一个 JSON 对象。")
    return "\n".join(lines)


def _build_demo_reply_prompt(user_msg: str, wf: str, result: dict,
                             carried: dict, stage_hint: str = ""):
    """执行完成后，让 LLM 按「结果表达铁律」据实渲染面向用户的回复。"""
    outs = result.get("outputs") or {}
    lines = [
        "【事实依据（子工作流真实返回，你必须严格据此表达，禁止虚构/凑数）】",
        f"环节工作流={wf}",
        f"执行结论={'成功' if result.get('passed') else '失败'}",
        f"总节点={result.get('total', 0)} OK={result.get('ok', 0)} "
        f"回退={result.get('fallback', 0)} 错误={result.get('error', 0)}",
    ]
    if outs:
        lines.append(f"关键出参={json.dumps(outs, ensure_ascii=False)[:600]}")
    if result.get("error_msg"):
        lines.append(f"失败明细={result['error_msg']}")
    lines += [
        "",
        f"【本轮用户消息】{user_msg}",
        f"【本环节定位】{stage_hint or wf}",
        "",
        "请按主智能体提示词的「结果表达」要求，输出面向用户的自然语言回复：",
        "1) 如实汇报本环节结果（与上述事实一一对应）；2) 给出下一步建议；",
        "3) 若后续需用户确认/决策（如是否发起审批、是否继续下一环节），必须明确提示并止于确认点；",
        "4) 若失败，按异常模板给环节/原因/明细/建议，引导【重新执行】或【修改执行方案】。",
        "5) 待补充字段只能逐字引用出参 pending_required：为空即输出『无待补充』，"
        "严禁自行新增/列举任何待补充字段（如收费科目、税率、发布地市等模板必填项不得当作待补充）。",
        "仅输出回复正文（不要 JSON、不要代码块）。",
    ]
    return "\n".join(lines)


def _run_demo_workflow(name: str, runner_kwargs: dict, log_dir: str,
                       carried: dict) -> ChainResult:
    """对话编排中的一次子工作流直调：复用链式执行逻辑（复用上下文注入与回收）。"""
    return _chain_one(name, runner_kwargs, log_dir, carried, overrides=None)


def _demo_check(labels: list, decision: dict, executed: list, carried: dict,
                allow_fb: bool) -> list:
    """按回合标签做确定性语义断言（不信任 LLM 自述）。

    返回 [(label, ok, detail)]。断言口径与剧本「反向速查表」一致：
    - intent_req_submit / call_wf_sub_XX：LLM 决策应直调指定子工作流
    - gate_refuse_direct_config：跳过确认直接落地 → 必须 reply_only 拒绝，不得直调执行主干
    - gate_confirm_exec：确认后应进入执行主干（call 02 或后续环节）
    - point_confirm / point_feedback：应止于确认/反馈点（回复含确认/下一步提示）
    - poll_repeatable：轮询流（12/13）可重复直调且执行无 error
    - carry_req_id：上下文 req_id 须为 PLAN\\d{{17}} 且被下游携带
    - summary_block / stage_N：执行结果须无 error/回退（严格口径）
    """
    wf = _wf_code(decision.get("workflow") or "")
    action = (decision.get("action") or "").strip()
    reply = (decision.get("reply") or "")
    checks = []
    exec_by_name = {_wf_code(e["name"]): e for e in executed}

    def exec_ok(sub: str) -> tuple[bool, str]:
        e = exec_by_name.get(sub)
        if not e:
            return False, f"未执行 {sub}"
        ok = e["error"] == 0 and (allow_fb or e["fallback"] == 0)
        return ok, (f"{sub}: 节点{e['total']} OK{e['ok']} 回退{e['fallback']} "
                    f"错误{e['error']} {e['elapsed_ms']}ms")

    for lb in labels:
        if lb.startswith("call_wf_sub_"):
            sub = lb.replace("call_", "")
            hit = action == "call_workflow" and wf == sub
            ok, detail = exec_ok(sub)
            checks.append((lb, hit and ok,
                           f"决策={action}/{wf or '-'}；{detail}" if hit else
                           f"期望直调 {sub}，实际 {action}/{wf or '-'}"))
        elif lb == "intent_req_submit":
            hit = action == "call_workflow" and wf.startswith("wf_sub_0")
            ok, detail = exec_ok(wf) if hit else (False, "")
            checks.append((lb, hit and ok, detail or f"实际 {action}/{wf or '-'}"))
        elif lb == "gate_refuse_direct_config":
            # 关键反向门禁：不得直调执行主干（02/03/04/05）子流
            main_path = wf in ("wf_sub_02", "wf_sub_03", "wf_sub_04", "wf_sub_05")
            ok = (not main_path) and action == "reply_only"
            kw = ("确认", "方案", "不能", "无法", "需", "请")
            hinted = any(k in reply for k in kw)
            checks.append((lb, ok and hinted,
                           f"决策={action}/{wf or '-'}；回复含确认引导={hinted}"))
        elif lb == "gate_confirm_exec":
            ok = action == "call_workflow" and wf in (
                "wf_sub_02", "wf_sub_03", "wf_sub_04", "wf_sub_05")
            if wf in exec_by_name:
                e = exec_by_name[wf]
                ok = ok and e["error"] == 0 and (allow_fb or e["fallback"] == 0)
            checks.append((lb, ok, f"决策={action}/{wf or '-'}"))
        elif lb in ("point_confirm", "point_feedback"):
            kw = ("确认", "是否", "请", "下一步", "继续", "回复")
            hinted = any(k in reply for k in kw)
            checks.append((lb, hinted, f"回复止于确认/反馈点={hinted}"))
        elif lb == "poll_repeatable":
            ok, detail = exec_ok(wf)
            hit = wf in ("wf_sub_12", "wf_sub_13")
            checks.append((lb, hit and ok, detail or f"决策={action}/{wf or '-'}"))
        elif lb == "carry_req_id":
            rid = str(carried.get("req_id", ""))
            ok = bool(re.fullmatch(r"PLAN\d{17}", rid))
            checks.append((lb, ok, f"req_id={rid}"))
        elif lb == "summary_block":
            kw = ("审批", "完成", "汇总", "上线")
            hinted = any(k in reply for k in kw)
            ok, detail = exec_ok(wf) if wf else (True, "")
            checks.append((lb, hinted and (ok if wf else True),
                           f"回复含汇总/上线审批提示={hinted}"))
        elif lb.startswith("stage_"):
            if wf:
                ok, detail = exec_ok(wf)
            else:
                ok, detail = True, "无执行动作（纯回复回合）"
            checks.append((lb, ok, detail))
        else:
            checks.append((lb, True, "未定义断言（跳过）"))
    return checks


def _run_demo(args) -> int:
    """对话式端到端演示入口（--demo）。

    以真实 LLM（主智能体提示词）为编排者：逐条回放剧本用户回合，LLM 自主产出
    「直调哪个子工作流 / 拒绝推进 / 纯回复」的结构化决策，runner 本地执行对应
    `wf_sub_*` 并把结果回灌 LLM，循环直至剧本结束。每轮做确定性语义断言。
    """
    names = _discover_workflows()
    if not names:
        print(f"{C.RED}未发现子工作流（{WF_DIR}）{C.RESET}")
        return 1
    available = list(names)

    llm_cfg = LlmConfig.from_h2_seed()
    if args.model and llm_cfg:
        llm_cfg.model = args.model
    if not args.offline and llm_cfg is None:
        print(f"{C.RED}未读到 LLM 配置（data-h2.sql 种子），无法联网编排；"
              f"可用 --offline 做流程自检。{C.RESET}")
        return 1
    llm = None if args.offline else LlmClient(llm_cfg, offline=args.offline)

    runner_kwargs = dict(
        registry=PluginRegistry(), llm=llm, offline=args.offline,
        save_json=args.save_json, verbose=False, show_values=False,
        color=not args.no_color, fast_forward=args.fast_forward,
        strict=not args.offline and not args.allow_mock,
    )
    allow_fb = bool(args.offline) or not runner_kwargs["strict"]
    log_dir = args.log_dir or os.path.join(BASE_DIR, "logs",
                                           "demo_" + _dt.datetime.now().strftime("%Y%m%d_%H%M%S"))
    if log_dir and not os.path.isdir(log_dir):
        os.makedirs(log_dir, exist_ok=True)

    system_prompt = _load_orchestrator_prompt(available)
    mode = "离线 mock" if args.offline else (
        f"实时联网（LLM={getattr(llm_cfg, 'model', '') or '无配置'}）")
    strict_note = "严格判定（无 error 且无 fallback）" if not allow_fb else "宽松判定"
    print()
    print(f"{C.BOLD}{C.CYAN}╔{'═' * 76}╗{C.RESET}")
    print(f"{C.BOLD}{C.CYAN}║ {C.RESET}{C.BOLD}对话式端到端演示（LLM 编排）{C.RESET}")
    print(f"{C.BOLD}{C.CYAN}║ {C.RESET}{C.GREY}模式: {mode}，{strict_note}{C.RESET}")
    print(f"{C.BOLD}{C.CYAN}║ {C.RESET}{C.GREY}用户回合: {len(DEMO_TURNS)}  "
          f"日志: {log_dir}{C.RESET}")
    print(f"{C.BOLD}{C.CYAN}╚{'═' * 76}╝{C.RESET}")

    carried = {"req_id": "PLAN" + _dt.datetime.now().strftime("%Y%m%d%H%M%S") +
               "".join(random.choices(string.digits, k=3)),
               "chat_id": "demo_chat_" + _dt.datetime.now().strftime("%H%M%S")}
    print(f"{C.GREY}上下文 req_id = {carried['req_id']}  chat_id = {carried['chat_id']}{C.RESET}")

    history = []
    last_result = {}
    done = []
    assertions = []
    for idx, (stage, user_msg, labels) in enumerate(DEMO_TURNS, 1):
        print(f"\n{C.BOLD}[{idx}/{len(DEMO_TURNS)}]{C.RESET} {C.MAGENTA}{stage}{C.RESET} "
              f"{C.BOLD}用户：{C.RESET}{user_msg}", flush=True)
        prompt = _build_demo_decision_prompt(history, user_msg, carried, last_result, done)
        text, is_fb, note = (llm.chat(prompt, system=system_prompt)
                             if llm else (LlmClient._mock(prompt), True, "offline/mock"))
        decision = _extract_json_object(text) or {}
        action = (decision.get("action") or "").strip()
        wf_raw = (decision.get("workflow") or "").strip()
        wf = _wf_code(wf_raw)
        reply = _reply_text(decision.get("reply") or text)
        if is_fb:
            print(f"    {C.YELLOW}⚠ LLM 回退（{note}）{C.RESET}")
        print(f"    {C.GREY}决策：action={action or '-'} workflow={wf or '-'} "
              f"reason={decision.get('reason', '')[:80]}{C.RESET}")
        print(f"    {C.CYAN}数字员工：{C.RESET}{C.GREY}{reply[:300]}{C.RESET}")

        executed = []
        if action == "call_workflow" and wf and _workflow_exists(wf):
            cr = _run_demo_workflow(wf, runner_kwargs, log_dir, carried)
            executed.append({"name": wf, "passed": cr.passed, "total": cr.total,
                             "ok": cr.ok, "fallback": cr.fallback, "error": cr.error,
                             "elapsed_ms": cr.elapsed_ms, "outputs": cr.outputs,
                             "error_msg": cr.error_msg})
            last_result = executed[-1]
            tag = f"{C.GREEN}PASS{C.RESET}" if cr.passed else f"{C.RED}FAIL{C.RESET}"
            print(f"    → {tag} {C.BOLD}{cr.flow_name}{C.RESET} "
                  f"{C.GREY}节点{cr.total} OK{cr.ok} 回退{cr.fallback} 错误{cr.error} "
                  f"{cr.elapsed_ms}ms{C.RESET}")
            if cr.outputs:
                print(f"    {C.GREY}出参: {cr.outputs}{C.RESET}")
            if not cr.passed and cr.error_msg:
                print(f"    {C.RED}✗ {cr.error_msg}{C.RESET}")
            if cr.passed and wf not in done:
                done.append(wf)
            # 结果表达二次生成：据真实执行结果渲染面向用户的回复（结果表达铁律）
            stage_hint = labels[0] if labels else ""
            if llm and not is_fb:
                rp = _build_demo_reply_prompt(user_msg, wf, last_result, carried, stage_hint)
                rtext, r_fb, r_note = llm.chat(rp, system=system_prompt)
                if not r_fb and rtext.strip():
                    reply = _reply_text(rtext)
                    decision["reply"] = reply
                    print(f"    {C.CYAN}数字员工（据实）：{C.RESET}{C.GREY}{reply[:320]}{C.RESET}")
        elif action == "call_workflow" and wf:
            print(f"    {C.YELLOW}⚠ 指定工作流不存在：{wf}（跳过执行）{C.RESET}")
            last_result = {"name": wf, "passed": False, "error": 1,
                           "note": "工作流不存在"}

        checks = _demo_check(labels, decision, executed, carried, allow_fb)
        assertions.append(DemoAssertion(stage, user_msg, labels))
        assertions[-1].decision = decision
        assertions[-1].reply = reply
        assertions[-1].checks = checks
        assertions[-1].executed = executed
        for lb, ok, detail in checks:
            mark = f"{C.GREEN}✓{C.RESET}" if ok else f"{C.RED}✗{C.RESET}"
            print(f"    {mark} {C.GREY}断言[{lb}] {detail}{C.RESET}")

        history.append(("user", user_msg))
        history.append(("agent", reply))

    # 汇总
    all_checks = [(a, c) for a in assertions for c in a.checks]
    passed = sum(1 for _, c in all_checks if c[1])
    failed = len(all_checks) - passed
    print()
    print(f"{C.BOLD}{C.CYAN}" + "═" * 78 + f"{C.RESET}")
    print(f"{C.BOLD}演示汇总{C.RESET}  回合 {len(assertions)}  断言 {len(all_checks)}  "
          f"{C.GREEN}PASS {passed}{C.RESET}  {C.RED}FAIL {failed}{C.RESET}")
    print(f"{C.BOLD}{C.CYAN}" + "─" * 78 + f"{C.RESET}")
    print(f"{'幕':<6}{'用户回合':<36}{'结论':<8}{'断言':>6}")
    for a in assertions:
        verdict_plain = "PASS" if a.ok else "FAIL"
        verdict_col = f"{C.GREEN}PASS{C.RESET}" if a.ok else f"{C.RED}FAIL{C.RESET}"
        pad = " " * max(0, 8 - len(verdict_plain))
        msg = a.user_msg[:34]
        print(f"{a.stage:<6}{msg:<36}{verdict_col}{pad}{len(a.checks):>6}")
    if failed:
        print(f"\n{C.RED}失败断言：{C.RESET}")
        for a, c in all_checks:
            if not c[1]:
                print(f"  - [{a.stage}] {c[0]}: {c[2]}")
    print(f"{C.BOLD}{C.CYAN}" + "═" * 78 + f"{C.RESET}")

    # 落盘报告
    _write_demo_report(log_dir, assertions, carried, mode, strict_note)
    print(f"{C.GREY}报告: {os.path.join(log_dir, 'demo_report.md')}{C.RESET}")
    return 1 if failed else 0


def _write_demo_report(log_dir: str, assertions: list, carried: dict,
                       mode: str, strict_note: str) -> str:
    """把对话回合 → 断言覆盖度写成 markdown 报告。"""
    if not log_dir:
        return ""
    if not os.path.isdir(log_dir):
        os.makedirs(log_dir, exist_ok=True)
    path = os.path.join(log_dir, "demo_report.md")
    lines = ["# 对话式端到端演示报告（LLM 编排）", "",
             f"- 模式：{mode}，{strict_note}",
             f"- 上下文：req_id=`{carried.get('req_id', '')}`，"
             f"chat_id=`{carried.get('chat_id', '')}`",
             f"- 用户回合：{len(assertions)}", "",
             "## 回合 → 断言明细", ""]
    for i, a in enumerate(assertions, 1):
        verdict = "PASS" if a.ok else "FAIL"
        lines.append(f"### {i}. [{a.stage}] {verdict}　用户：{a.user_msg}")
        d = a.decision or {}
        lines.append(f"- LLM 决策：action=`{d.get('action', '')}` "
                     f"workflow=`{d.get('workflow', '')}` "
                     f"reason={d.get('reason', '')}")
        if a.reply:
            lines.append(f"- 数字员工回复：{a.reply[:500]}")
        for e in a.executed:
            lines.append(f"- 执行：`{e['name']}` "
                         f"{'PASS' if e['passed'] else 'FAIL'} 节点{e['total']} "
                         f"OK{e['ok']} 回退{e['fallback']} 错误{e['error']} "
                         f"{e['elapsed_ms']}ms")
        for lb, ok, detail in a.checks:
            lines.append(f"- 断言 [`{lb}`] {'✓' if ok else '✗'} {detail}")
        lines.append("")
    # 覆盖度统计
    all_checks = [(a, c) for a in assertions for c in a.checks]
    passed = sum(1 for _, c in all_checks if c[1])
    lines += ["## 断言覆盖度", "",
              f"- 断言总数：{len(all_checks)}，PASS {passed}，FAIL {len(all_checks) - passed}",
              ""]
    label_stat = {}
    for a, c in all_checks:
        s = label_stat.setdefault(c[0], [0, 0])
        s[1] += 1
        if c[1]:
            s[0] += 1
    lines.append("| 断言 | 通过/总数 |")
    lines.append("| --- | --- |")
    for lb, (ok, tot) in label_stat.items():
        lines.append(f"| {lb} | {ok}/{tot} |")
    lines.append("")
    io.open(path, "w", encoding="utf-8").write("\n".join(lines))
    return path



# ---------------------------------------------------------------- 批量串行验证

def _discover_workflows() -> list:
    """发现待验证的工作流 JSON：全部 wf_sub_*/wf_main*.json，按名称升序。

    排除 `_test*`（草稿）、`import_headers.json`（非工作流）与 `*_export.json`（插件导出）。
    """
    if not os.path.isdir(WF_DIR):
        return []
    names = []
    for fn in sorted(os.listdir(WF_DIR)):
        if not fn.endswith(".json"):
            continue
        if not fn.startswith(("wf_sub_", "wf_main")):
            continue
        if fn.endswith("_export.json") or fn.startswith("_test"):
            continue
        names.append(os.path.splitext(fn)[0])
    return names


class BatchResult:
    """单个工作流的验证结论。"""

    def __init__(self, name: str):
        self.name = name
        self.flow_name = ""
        self.total = 0
        self.ok = 0
        self.fallback = 0
        self.error = 0
        self.elapsed_ms = 0
        self.passed = False
        self.error_msg = ""
        self.log_path = ""

    def verdict(self) -> str:
        return "PASS" if self.passed else "FAIL"


def _verify_one(name: str, runner_kwargs: dict, log_dir: str) -> BatchResult:
    """串行执行单个工作流并给出 PASS/FAIL 结论。

    判定随模式而异：
    - **实时联网**（默认，严格）：无 error 且无 fallback 才 PASS；
    - **离线 mock**（--offline 或 --allow-mock）：fallback 属预期行为，无 error 即 PASS。

    每个工作流重建一个 Runner，避免上一工作流的 results/contexts 互相污染；
    每个工作流各写一份 logs/<工作流>_<流程名>_<时间戳>.log.md。
    """
    br = BatchResult(name)
    t0 = time.time()
    allow_fb = bool(runner_kwargs.get("offline")) or not runner_kwargs.get("strict", True)
    try:
        log_file = _resolve_log_path("-", name)
        if log_dir:
            log_file = os.path.join(log_dir, os.path.basename(log_file))
        br.log_path = log_file
        runner = WorkflowRunner(log_file=log_file, **runner_kwargs)
        wf = runner.load(name)
        br.flow_name = wf.flow_name
        runner.run(name, {})
        br.total = len(runner.results)
        br.ok = sum(1 for r in runner.results if r["status"] == "ok")
        br.fallback = sum(1 for r in runner.results if r["status"] == "fallback")
        br.error = sum(1 for r in runner.results if r["status"] == "error")
        # 严格（实时）：无 error 且无 fallback；离线/放行：无 error（fallback 属预期）
        br.passed = br.error == 0 and (allow_fb or br.fallback == 0)
        if not br.passed:
            bad = [r for r in runner.results if r["status"] == "error"
                   or (not allow_fb and r["status"] == "fallback")]
            first = bad[0] if bad else None
            if first:
                br.error_msg = f"{first['title']}[{first['status']}]: {first.get('note', '')}"
    except KeyboardInterrupt:
        raise
    except Exception as e:
        br.passed = False
        br.error_msg = f"{type(e).__name__}: {e}"
    br.elapsed_ms = int((time.time() - t0) * 1000)
    return br


def _run_verify(args) -> int:
    """批量串行验证入口：逐个跑全部工作流，汇总 PASS/FAIL，返回退出码（有 FAIL 则 1）。"""
    names = _discover_workflows()
    if not names:
        print(f"{C.RED}未发现待验证工作流（{WF_DIR}）{C.RESET}")
        return 1

    llm_cfg = LlmConfig.from_h2_seed()
    if args.model and llm_cfg:
        llm_cfg.model = args.model
    llm = None if args.offline else LlmClient(llm_cfg, offline=args.offline)

    runner_kwargs = dict(
        registry=PluginRegistry(), llm=llm, offline=args.offline,
        save_json=args.save_json, verbose=False, show_values=False,
        color=not args.no_color, fast_forward=args.fast_forward,
        strict=not args.offline and not args.allow_mock,
    )
    log_dir = args.log_dir or ""

    mode = "离线 mock" if args.offline else (
        f"实时联网（LLM={getattr(llm_cfg, 'model', '') or '无配置'}）")
    verdict_note = "，严格判定（无 error 且无 fallback 才 PASS）" if runner_kwargs["strict"] \
        else "，判定（无 error 即 PASS，回退不计失败）"
    print()
    print(f"{C.BOLD}{C.CYAN}╔{'═' * 76}╗{C.RESET}")
    print(f"{C.BOLD}{C.CYAN}║ {C.RESET}{C.BOLD}批量串行验证：{len(names)} 个工作流{C.RESET}")
    print(f"{C.BOLD}{C.CYAN}║ {C.RESET}{C.GREY}模式: {mode}{verdict_note}{C.RESET}")
    print(f"{C.BOLD}{C.CYAN}╚{'═' * 76}╝{C.RESET}")

    results = []
    for i, name in enumerate(names, 1):
        print(f"\n{C.BOLD}[{i}/{len(names)}]{C.RESET} {C.CYAN}{name}{C.RESET} ... 执行中",
              flush=True)
        br = _verify_one(name, runner_kwargs, log_dir)
        results.append(br)
        tag = f"{C.GREEN}PASS{C.RESET}" if br.passed else f"{C.RED}FAIL{C.RESET}"
        detail = (f"{C.GREY}节点 {br.total} · OK {br.ok} · 回退 {br.fallback} · "
                  f"错误 {br.error} · {br.elapsed_ms}ms{C.RESET}")
        print(f"    → {tag}  {C.BOLD}{br.flow_name}{C.RESET}  {detail}")
        if not br.passed and br.error_msg:
            print(f"    {C.RED}✗ {br.error_msg}{C.RESET}")
        if br.log_path:
            print(f"    {C.GREY}日志: {br.log_path}{C.RESET}")

    # 汇总表
    passed = sum(1 for r in results if r.passed)
    failed = len(results) - passed
    print()
    print(f"{C.BOLD}{C.CYAN}" + "═" * 78 + f"{C.RESET}")
    print(f"{C.BOLD}验证汇总{C.RESET}  总计 {len(results)}  "
          f"{C.GREEN}PASS {passed}{C.RESET}  {C.RED}FAIL {failed}{C.RESET}")
    print(f"{C.BOLD}{C.CYAN}" + "─" * 78 + f"{C.RESET}")
    print(f"{'工作流':<28}{'结论':<8}{'节点':>6}{'OK':>6}{'回退':>6}{'错误':>6}{'耗时ms':>10}")
    for r in results:
        # ANSI 颜色不占显示宽度：按可见文本 "PASS"/"FAIL" 计算列宽，再着色
        verdict_plain = "PASS" if r.passed else "FAIL"
        verdict_col = (f"{C.GREEN}PASS{C.RESET}" if r.passed else f"{C.RED}FAIL{C.RESET}")
        name_col = f"{r.name:<28}"
        pad = " " * max(0, 8 - len(verdict_plain))
        print(f"{name_col}{verdict_col}{pad}{r.total:>6}{r.ok:>6}{r.fallback:>6}"
              f"{r.error:>6}{r.elapsed_ms:>10}")
    if failed:
        print(f"\n{C.RED}失败项：{C.RESET}")
        for r in results:
            if not r.passed:
                print(f"  - {C.BOLD}{r.name}{C.RESET}  {C.RED}{r.error_msg}{C.RESET}")
    print(f"{C.BOLD}{C.CYAN}" + "═" * 78 + f"{C.RESET}")
    return 1 if failed else 0


def main(argv=None):
    ap = argparse.ArgumentParser(
        description="工作流执行工具：按 JSON 逐节点执行并显性打印每个环节的输入/输出",
        formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("workflow", nargs="?", help="工作流名或文件（wf_sub_01 / wf_main_intent ...）")
    ap.add_argument("--set", action="append", default=[], metavar="K=V",
                    help="覆盖开始节点入参，可多次")
    ap.add_argument("--offline", action="store_true",
                    help="全部走 mock，不联网（仅调试用；默认联网真实执行）")
    ap.add_argument("--fast-forward", action="store_true",
                    help="代码节点快进沙箱：跳过 sleep/轮询等待（离线默认开启）")
    ap.add_argument("--allow-mock", action="store_true",
                    help="允许真实执行失败时静默回退 mock（默认不允许：网关不可达即报错）")
    ap.add_argument("--no-color", action="store_true", help="关闭彩色输出")
    ap.add_argument("--no-values", action="store_true", help="只打印节点流转，不打印入/出参值")
    ap.add_argument("--save-json", default="", metavar="FILE", help="把每步 I/O 落盘为 JSON")
    ap.add_argument("--log-file", default=None, metavar="FILE",
                    help="把执行日志（每节点完整输入/输出，不截断）写入 markdown 文件；"
                         "默认（不传）自动生成 logs/<工作流>_<流程名>_<时间戳>.log.md，"
                         "传 '-' 同为自动生成，传具体路径则写入该文件")
    ap.add_argument("--no-log", action="store_true", help="不生成执行日志")
    ap.add_argument("--base-url", default="", help="覆盖插件 BASE_URL（默认取 JSON 内 url）")
    ap.add_argument("--model", default="", help="覆盖 LLM 模型名")
    ap.add_argument("--timeout", type=int, default=60, help="插件 HTTP 超时秒")
    ap.add_argument("--verify", action="store_true",
                    help="批量串行验证：逐个执行全部工作流 JSON（wf_sub_*/wf_main*），"
                         "汇总 PASS/FAIL；实时联网时严格判定（无 error 且无 fallback 才 PASS），"
                         "离线/--allow-mock 时无 error 即 PASS；每个工作流各写一份日志；"
                         "有 FAIL 返回退出码 1")
    ap.add_argument("--log-dir", default="", metavar="DIR",
                    help="批量验证时日志输出目录（默认 logs/）")
    ap.add_argument("--chain", action="store_true",
                    help="链式串跑：按演示剧本 V3.0 次序（00→01→02→03→12→04→13→05→06→07）"
                         "逐个执行，全程传递同一 req_id，复现 8 环节步进/稽核与测试两阶段；"
                         "判定同 --verify（实时严格 / 离线宽松）；每工作流各写一份日志；"
                         "有 FAIL 返回退出码 1")
    ap.add_argument("--chain-text", default="", metavar="TEXT",
                    help="链式串跑时注入首环节（需求理解）的 requirement_text，"
                         "用于复现剧本「止于确认点」的正常分支；缺省则用工作流内默认样例")
    ap.add_argument("--chain-set", action="append", default=[], metavar="[WF:]K=V",
                    help="链式串跑时预置入参，可多次：K=V 作用于首环节，"
                         "wf_sub_XX:K=V 作用于指定环节（如 wf_sub_02:action=确认执行）")
    ap.add_argument("--demo", action="store_true",
                    help="对话式端到端演示（LLM 编排）：回放演示剧本 V3.0 用户回合，"
                         "由真实 LLM 读主智能体提示词自主决定直调哪个子工作流/拒绝推进，"
                         "runner 本地执行对应 wf_sub_* 并回灌结果，逐回合做语义断言；"
                         "有 FAIL 返回退出码 1")
    ap.add_argument("--list", action="store_true", help="列出可用工作流")
    args = ap.parse_args(argv)

    if args.no_color:
        C.disable()
    if args.demo:
        return _run_demo(args)
    if args.chain:
        return _run_chain(args)
    if args.verify:
        return _run_verify(args)
    if args.list or not args.workflow:
        _list_workflows()
        return 0

    registry = PluginRegistry()
    llm_cfg = LlmConfig.from_h2_seed()
    if args.model and llm_cfg:
        llm_cfg.model = args.model
    llm = None if args.offline else LlmClient(llm_cfg, offline=args.offline)

    if args.no_log:
        log_file = ""
    else:
        log_file = _resolve_log_path(args.log_file or "-", args.workflow)

    runner = WorkflowRunner(
        registry=registry, llm=llm, offline=args.offline,
        save_json=args.save_json, show_values=not args.no_values,
        color=not args.no_color, fast_forward=args.fast_forward,
        log_file=log_file, strict=not args.offline and not args.allow_mock)
    runner.plugin_caller.timeout = args.timeout
    runner.plugin_caller.base_url_override = args.base_url

    mode = "离线 mock" if args.offline else (
        f"实时（LLM={getattr(llm_cfg, 'model', '') or '无配置'}）")
    strict_note = "，失败即报错（--allow-mock 可放开）" if runner.strict else ""
    print(f"{C.GREY}执行模式: {mode}{strict_note}{C.RESET}")
    if llm_cfg and not args.offline:
        print(f"{C.GREY}LLM: {llm_cfg.model} @ {llm_cfg.completions_url()} "
              f"(auth={llm_cfg.auth_type}){C.RESET}")

    initial = _parse_sets(args.set)
    try:
        runner.run(args.workflow, initial)
    except KeyboardInterrupt:
        print(f"\n{C.YELLOW}已中断{C.RESET}")
        return 130
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
