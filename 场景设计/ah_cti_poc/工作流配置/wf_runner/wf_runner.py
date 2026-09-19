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
                outs = self._http(url, method, inputs)
                if outs is not None:
                    return self._align(outs, declared), False, f"live:{method} {url}"
                note = "HTTP 返回无法解析"
            except urllib.error.HTTPError as e:
                note = f"HTTP {e.code}"
            except Exception as e:
                note = repr(e)
        else:
            note = "offline"
        outs = self._mock(code, title, declared)
        return outs, True, f"回退mock({note})"

    def _http(self, url, method, inputs):
        if method == "get":
            qs = urllib.parse.urlencode({k: _to_str(v) for k, v in inputs.items()})
            sep = "&" if "?" in url else "?"
            full = url + (sep + qs if qs else "")
            req = urllib.request.Request(full, method="GET",
                                         headers={"Accept": "application/json"})
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
    ap.add_argument("--list", action="store_true", help="列出可用工作流")
    args = ap.parse_args(argv)

    if args.no_color:
        C.disable()
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
