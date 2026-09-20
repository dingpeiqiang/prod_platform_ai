# 工作流 JSON 开发规范

> 适用范围：`场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/` 下的 12 个子工作流 JSON
> （`wf_sub_00`~`wf_sub_11`，无 `wf_main_intent` 意图调度主流程；智能体按方案 3.2 提示词
> 【意图→工作流映射表】语义识别直调）。
> 本规范以生成器 `gen_workflows.py`（基础生成器）与 `gen_workflows_v2.py`（V2.0）为准，
> 所有 JSON 均为生成器产物，**禁止手工改 JSON**，一律改生成器后重新生成。

---

## 一、总原则（务必先读）

1. **源码驱动**：工作流 JSON 由 `gen_workflows_v2.py`（导入 `gen_workflows.py`）生成，输出到本目录同名 `.json`。
   改工作流 = 改生成器 → 运行生成 → 校验 ALL_OK，绝不应直接编辑 JSON。
2. **12 个子工作流 JSON**：`wf_sub_00`~`wf_sub_11`（12 个；`wf_main_intent`/`wf_merged_exec` 为 V2.0 早期意图调度/执行主干设计，已废弃不再生成，智能体按方案 3.2 提示词【意图→工作流映射表】语义识别直调各子流）。
3. **出参契约为 snake_case**：工作流所有节点出参、插件契约字段统一 snake_case（见 AGENTS.md「JSON 数据传输统一使用 snake_case」）。
4. **插件端点走网关**：所有 HTTP 插件节点 `url` 为 `BASE_URL`（`http://10.86.13.201:31281`）+ `/api/v1/appstore/*` 路径。
5. **优雅回退**：后端/网关不可达时，代码节点 HTTP 调用须回退 `backend_pending=1`，保证离线 Demo/诚实占位口径。
6. **req_id 全程贯穿**：环节1 生成后 2~9 全程沿用（见第五节）。

---

## 二、文件组织与命名

| 项 | 约定 |
|---|---|
| 目录 | `场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/` |
| 生成器 | `gen_workflows_v2.py`（V2.0，被 import 的 `gen_workflows.py` 为 V1.7 基线） |
| 输出文件 | `wf_sub_00_需求提报.json`、`wf_sub_01_需求分析.json` … `wf_sub_11_发起需求审批.json`（12 个） |
| 文件命名 | `wf_{类型}_{中文名}.json`；子流 `wf_sub_{两位序号}_{中文名}`（无 `wf_main_intent` 意图调度主流程） |
| 生成命令 | 在含 `gen_workflows_v2.py` 的目录运行 `python gen_workflows_v2.py`（需与 `gen_workflows.py` 同目录） |

> 目录名 `智能体工作流集V1.6` **不要改**（避免破坏导入/平台路径），版本语义以生成器文件头 `V2.0` 为准。

---

## 三、JSON 顶层结构

`gen_workflows.workflow(flow_name, flow_remark, flow_id, nodes, edges)` 产出：

```jsonc
{
  "flowName": "产销品-智能配置",                 // 展示名（与文件名中文部分一致）
  "flowRemark": "子工作流2（融合组扩展）：…",     // 流程说明（改流程时同步更新）
  "flowIco": "workflowIcon",                    // 固定
  "workFlowSchema": null,
  "workFlowSchemaJSON": {
    "nodes": [ /* 节点数组，见第四节 */ ],
    "edges": [ /* 有向边数组，见第六节 */ ],
    "flowId": "wf_sub_02",                      // 唯一流程标识，与文件名一致
    "version": "1.6"
  },
  "userScope": 4,
  "projectId": ""
}
```

---

## 四、节点通用结构与类型

每个节点统一含公共字段：

```jsonc
{
  "id": "a1b2c3d4-0000-4000-8000-000000000101",  // UUID 形态，由 nid(seq) 生成
  "type": 0,                                     // 节点类型，见下表
  "position": { "x": 15, "y": 300 },             // 布局，由 apply_layout 重排，可不手填
  "flowJson": null,
  "checkErr": false,
  "dependencyData": []
}
```

| type | 含义 | 生成器函数 | 必带字段 |
|---|---|---|---|
| 0 | 开始节点 | `start_node(seq, inputs)` | `inputs`（工作流入参） |
| 1 | LLM 节点 | `llm_node(seq, title, prompt, in_refs, outputs, sys_prompt, model)` | `outputs`、`inputs.llmParam[].content`（prompt）、`inputs.inputParameters`、`max_tokens/temperature/top_p/prompt_system/model` |
| 2 | 条件分支节点 | `selector_node2(seq, title, deps, branches)` | `inputs.condition`（conditions 数组）、`dependencyData`（dep_node） |
| 3 | HTTP 插件节点 | `plugin_node(seq, title, code, desc, url, inputs, outputs, method)` | `url`、`submit_way`（post/get）、`outputs`（含 sechema item 树）、`nodeMeta.code` |
| 6 | 代码节点 | `code_node(seq, title, code, in_refs, outputs)` | `code`（Python 源码字符串，`async def main(args)`）、`language=1` |
| 9 | 结束节点 | `end_node(seq, title, inputs, out_content)` | `outputs.content`（模板字符串，`{}` 占位符引用入参）、`settings/output_mode`、`terminatePlan`、`top_p/top_k/temperature` |
| 13 | 子流程节点 | `subflow_node(seq, title, desc, work_flow_id, inputs, outputs)` | `nodeMeta.workFlowId`、`history` |

---

## 五、入参 / 出参契约（核心）

### 5.1 入参：`inp(name, desc, ptype, required, content, ref_block, ref_rel)`

两种形态由 `ref_block` 是否为空区分：

**常量入参**（`ref_block=""`）：
```jsonc
{ "blockID": "", "relName": "", "name": "node_name", "description": "环节名=config",
  "type": "string", "required": true, "content": "config" }
```

**引用入参**（`ref_block=nid(上游seq)`，需 `nameValue/currValue`）：
```jsonc
{ "blockID": "a1b2c3d4-0000-4000-8000-000000000101", "relName": "req_id",
  "name": "req_id", "description": "存储键（=开始节点 req_id）", "type": "ref",
  "required": true, "content": "",
  "nameValue": ["a1b2c3d4-0000-4000-8000-000000000101", "a1b2c3d4-0000-4000-8000-000000000101,req_id"],
  "currValue": "a1b2c3d4-0000-4000-8000-000000000101,req_id" }
```

> 引用入参必须三层一致：`blockID`、`nameValue[0]`、`currValue` 前缀均为上游 `id`；
> `nameValue[1]`/`currValue` 的整体为 `{上游id},{出参名}`。

### 5.2 出参：`out()` / `code_out()`

- LLM/插件节点出参用 `out(name, desc, ptype)`，导出为 `{"name","description","type","content":""}` 扁平对象。
- **代码节点（type=6）出参必须用 `code_out(name, seq, ptype)`**（`gen_workflows.py` 定义），导出为 `{"relName": "<nodeId>,<name>", "name": "<name>", "type": ptype}`。
  - 平台按 `relName`（`<nodeId>,<出参名>`）匹配下游引用入参；**严禁**给代码节点传元组 `("name","desc")`，否则下游引用该出参的入参在平台上显示"未关联"（历次修复点）。
  - `seq` 必须是该代码节点自身序号（`code_node` 第 1 个参数，即节点 id 尾号）。
- 插件节点用 `out` 三元组并在 `plugin_out` 中声明 `sechema` item 树（array 出参必须给 item 叶子）。

### 5.3 插件出参 item 树（重要）

array 型出参必须声明 item 结构，`ARRAY_ITEM_FIELDS`（`gen_workflows.py:33`）为各工具 array 出参的叶子字段白名单，**与自研插件集 V1.6 的 `gen_plugins.py` 严格同步**：

| 工具 | array 出参 | 是否配 item 树 |
|---|---|---|
| `field_ontology_reason` | violations/fixed/completed/fields | 是 |
| `query_similar_offer` | similarOffer(object) | 是 |
| `realtime_spec_audit` | error_list | 是 |
| `get_test_scenes` | testScenes | 是 |
| `get_test_result` | testScenes（含二层 testCasePointResults，用 TEST_POINT_FIELDS） | 是 |
| `check_billing_rule` | risk_list | 是 |
| `query_product_monitor` | alarm_list | 是 |
| `query_approval_status` | approval_matrix | 是 |

**新增 array 出参时必须在 `ARRAY_ITEM_FIELDS` 补齐 item 树**，否则校验 FAIL。

### 5.4 命名约定（AGENTS.md）

- 出参名、插件字段：`snake_case`。
- 环节存储 `node_name` 固定枚举：`requirement_report`（需求提报单）、`requirement`（执行方案）、`config`（智能配置）、`spec`（规格稽核）、`fee`（资费校准）、`test`（自动测试）。

---

## 六、边与条件分支

### 6.1 普通边：`edge(start, end)`

```jsonc
{ "sourcePort": null, "startId": nid(start), "endId": nid(end) }
```

### 6.2 分支边：`edge(start, end, port)`

分支节点（type=2）的出边 `sourcePort` 语义（平台约定，对齐样例 wf_sub_01）：

- `sourcePort = -1` → **否则/兜底分支**（条件定义在 `port=-1`）。
- `sourcePort = 0` → **如果分支**（无条件定义时由平台自动路由）。

示例（需求分析 wf_sub_01 s1）：
```python
e1 = [edge(41,5), edge(5,6,0), edge(5,7,-1)]
```

### 6.3 条件构造

- `cond_ref(block_seq, rel, block_title)`：条件左值 = 引用某节点出参。
- `cond_str(value)`：条件右值 = 字符串常量（`currValue` 固定 `","`，常量放 `content`）。
- `cond_item(left, operator, right)`：单个条件项，`operator` 取 `OPS` 表（1等于/2不等于/3长度大于/…/10不为空/15长度等于）。
- `dep_node(block_seq, title, rel_names)`：`dependencyData` 条目，声明分支条件依赖的节点与出参。

```python
selector_node2(4, "待补充判定",
    [dep_node(3, "需求提报单渲染", ["pending_count"])],
    [(-1, [cond_item(cond_ref(3, "pending_count", "需求提报单渲染"), 10, cond_str(""))])])
```

> 每个分支 `conditions` 项结构：`{"sourcePort": port, "itemflag": true, "logic": 1, "conditions": [cond_item, ...]}`。

### 6.4 节点出边单线约束（平台限制，历次踩坑点）

平台**不允许一个节点连接两条及以上出边**（fan-out），否则导入/渲染异常。因此**除条件分支节点（type=2）外，其余所有类型节点（type=0/1/3/6/9/13）至多只能有一条出边**：

- type=2 条件分支节点**是唯一允许 fan-out 的类型**，其多条出边按 `sourcePort`（0/-1）路由（见 6.2），属正常设计。
- 其余节点若出现多条出边（如 202 同时连 203 与 208），平台判定违规，必须修正。

**修正原则（源码驱动）**：

1. **消除冗余边**：若两条出边指向同一竞态汇聚点（如插件节点同时连下游插件与其后的封包节点：`edge(202,203), edge(202,208)` 而 `208` 本就是 `203` 的后继），删除直达汇聚点的冗余边，改走 `202→203→…→208` 单链。数据流不变——执行器 `wf_runner.py` 用全局上下文 `ctx`（按 blockID）解析引用，**不要求有直接边**，只要上游节点先执行即可，下游引用上游出参照常有效。
2. **串行化并行分支**：同一节点需分叉到多条互不依赖的并行分支时（如开始节点分叉主线/自检A/自检B），改为首尾相接的单链（`主线→自检A→自检B→汇聚`），仅保留一条出边。因参考解析看全局 ctx 而非直接边，串行化后各分支下游节点仍能引用任意上游出参，前提是拓扑顺序保证引用的上游已先执行。
3. **真需要并行路由时用 type=2**：若分叉必须运行时按条件决定，用条件分支节点承载 fan-out，而非在普通节点上拉多条线。

> 校验口径：对每个 `edges` 统计各 `startId` 出度，`出度>1 且该节点 type≠2` 即为 FAIL（见第十四节校验项）。

---

## 七、ID 规范（nid）

`nid(seq, ns="a1b2c3d4")` → `"{ns}-0000-4000-8000-{12位seq}"`。

- 同一子流内 seq 唯一；普通边分支等都用 `nid(seq)` 引用，保证全局唯一。
- 建议 seq 分段：`wf_sub_00` 用 1~99；`wf_sub_01` 用 101 起；`wf_sub_03` 用 201 起；
  `wf_sub_05` 用 401 起；`wf_sub_04` 用 301 起；`wf_sub_07` 用 701 起；
  `wf_sub_09` 用 901 起；`wf_sub_10` 用 1001 起；`wf_sub_11` 用 1101 起 …（每个子流独立分段，避免冲突）。

---

## 八、HTTP 插件节点规范

```python
plugin_node(seq, title, code, desc, url, inputs, outputs, method="post")
```

- `code`：插件标识（如 `save_product_config`、`query_node_result`），写入 `nodeMeta.code`，**与自研插件集 JSON 定义一致**。
- `url`：`BASE_URL + "/api/v1/appstore/..."`。
- `submit_way`：`post` 或 `get`（如 `query_node_result` 用 `method="get"`）。
- `authentic_info`/`authentic_info_new` 由生成器统一填充（固定），无需手改。
- 工具 code 与 URL 路径映射（现有）：
  - `query_similar_offer` → `/similar/offer/query`
  - `realtime_spec_audit` → `/audit/realtime`
  - `save_product_config` → `/product/config/save`
  - `test_offer_start` → `/test/offer/start`、`get_test_scenes` → `/test/offer/scenes`、`get_test_result` → `/test/offer/result`
  - `check_billing_rule` → `/billing/rules/verify`
  - `submit_release_approval` → `/approval/submit`、`query_approval_status` → `/approval/status`
  - `query_product_monitor` → `/product/monitor`、`send_alert` → `/alert/send`
  - `query_offer` → `/similar/offer/query`（或后续独立端点）
  - `save_node_result` → `/result/save`、`query_node_result` → `/result/query`（method=get）
  - 阶段1.1 新增 7 个工作流适配端点：`/ops/root-cause`、`/ops/work-orders`、`/shelf-compliance`、`/validate-nested`、`/explain`、`/report/download`、`/script/download`

---

## 九、代码节点（type=6）规范

写法约束（对齐已有 `CODE_*` 常量）：

```python
CODE_XXX = (
    "import json\n"
    "from typing import Any, Dict\n"
    "async def main(args):\n"
    "    p = args.params\n"
    "    ...\n"
    "    ret: Output = { 'k1': v1, ... }\n"
    "    return ret"
)
```

要点：
1. 入口固定 `async def main(args)`，入参从 `args.params`（dict）读取，出参为 `Output`（dict）。
2. 入参名与 `code_node(...)` 的 `in_refs` 的 `name` 一致。
3. 需要 HTTP 调用时用 `urllib.request`（代码节点无平台工具调用能力），URL 用占位符 `BASE_URL` 并在定义后 `CODE_XXX.replace("BASE_URL", BASE_URL)`（见 `CODE_POLL_PROGRESS`、`CODE_OP_VALIDATE_NESTED`）。
4. **后端/网关不可达一律回退**：设 `backend_pending=1` + `note`（诚实占位口径），禁止抛异常中断或编造结论。
5. 确定性逻辑可从 skill 脚本直搬（去 argparse/CLI、改 `async main(args)`），见 `CODE_EXTRACT_RECORD`、`CODE_MERGE_NESTED`、`CODE_POLL_PROGRESS`、`CODE_DISPATCHER` 等。
6. 出参名含 `_json` 的存 JSON 字符串（如 `plan_json`、`summary_json`、`entities_json`），下游需 `json.loads` 解析（含"标签: {json}"前缀容错，见 `CODE_MAP_FIXED_CASES._load`）。

---

## 十、LLM 节点规范

```python
llm_node(seq, title, prompt, in_refs, outputs, sys_prompt="", model="qwen3-30b-a3b")
```

- `inputs.llmParam[].content` 为完整提示词；`{}` 为引用占位（如 `{elements_json}`），平台运行时以对应出参替换。
- `inputs.inputParameters` 为引用入参数组（`inp(...,ref_block=nid(上游), ref_rel=...)`）。
- 固定参数：`temperature=0.2`、`top_p=0.5`、`max_tokens=2048`、`prompt_system`。
- **模型字段**：`model`（LLM 节点默认模型，固定 `qwen3-30b-a3b`；由 `llm_node` 生成器统一写入，平台运行时按此模型执行 LLM 节点）。
- 提示词末尾约定"输出要求：仅输出…对应出参…，不输出其他多余文字"，保证单出参/多出参隔离（对齐现有所有 LLM 节点）。

### 10.1 出参绑定契约（重要，历次踩坑点）

平台对 LLM 节点出参的绑定语义（已实测确认）：

- 平台回包为 `{出参名: <模型返回>}`，即把模型返回装配到声明的出参上。
- **模型返回纯文本**时：整段文本直接作为该出参值（对齐 `audit_suggest`、`risk_summary`、`combo_report` 等导出节点）。
- **模型返回 JSON 对象**时：平台按**出参名做键匹配**取值；若返回对象的顶层键与出参名**不一致**，该出参取不到 → 值为空（`{"elements_json": ""}`）。

据此约定：

1. **单出参承载一段 JSON 文本**（如 `elements_json`、`plan_json`，下游需 `json.loads`）：让模型只输出顶层键 = 出参名、值为该 JSON 的字符串，形如
   `{"elements_json": "{\"name\":\"...\",\"price\":\"...\"}"}`。
   切勿让模型直接输出顶层为字段键的裸对象（`{"name":...}`），否则平台键匹配不到 `elements_json` → 空。
2. **让模型输出多字段**：应声明为多出参（每个字段一个 `out`），prompt 让模型按出参名返回对应键，不要用一个出参去吞整个对象。
3. **纯文本出参**：让模型直接返回文本，不要包成 JSON 对象。
4. 一律加"严禁 Markdown 代码块包裹（```json``` 围栏）"，避免把 JSON 文本包进代码块导致解析失败。

> 已在 `需求字段抽取`（wf_sub_00）落地：单出参 `elements_json`，prompt 要求输出 `{"elements_json": "{\"...\"}"}` 两层结构。

---

## 十一、结束节点规范

```python
end_node(seq, title, inputs, out_content)
```

- `inputs`：要回显的入参（引用上游出参）。
- `out_content`：模板字符串，用 `{入参名}` 占位（如 `"《执行方案》已生成（req_id：{req_id}）\n\n{plan_md}"`）。
- 常附加 `【下一步】`/`【确认执行】` 引导语，衔接下游子流。
- 分支结束节点各自独立 `end_node`（如 `结束(有待补充)` / `结束(无待补充)`），title 区分语义。
- **页面地址输出（统一约定）**：每个子工作流结束节点**不再内联** `xsbot-panel` 代码块，而是由**独立代码节点（type=6，`panel_code_node(...)`）专门构建** ```` ```xsbot-panel ```` 片段（节点出参 `panel`），结束节点模板末尾仅引用 `{panel}`。对齐参考示例形态：**单面板、单 url**（`panels` 仅一个外链面板）。生成辅助见 `gen_workflows_v2.py`（`OPS_WEB_BASE`/`CONFIG_WB_PAGE`/`gen_panel_code`/`panel_inputs`/`panel_code_node`）。
  - 每个结束节点前须插入一个 `panel_code_node`，其入参用 `panel_inputs(...)` 绑定上游 `chat_id`（起始节点透传）及可选 `offer_id/offer_name`（无法 `name==ref_rel` 之处按上游出参名绑定）；`offer_id` 仅在**智能配置落地后**（02~09）绑定真实落地 offer_id，落地前（00/01）**不绑定、传空**；并将 `panel` 入参（`ref_block=该代码节点`、`ref_rel="panel"`）加入结束节点 `inputs`，模板以 `{panel}` 收尾。
  - 单面板 url 统一用环节业务页 `config-workbench.html?offer_id=..&name=..&chatId=..&req_id=..&stage=<N>&view=stage`（页面按 `stage/view` 渲染对应业务），`title` 用环节业务名；`chatId` 运行期以 `chat_id` 实值填充，`req_id` 运行期以 `req_id` 实值填充（有 req_id 的环节 00~06 透传；07~10 无 req_id 则为空串）。**不再**使用产品详情页/监控看板（`product-detail.html`）与"配置工作台（view=workbench）"冗余面板。
  - **JSON 必须紧凑（无空格）**，逐字对齐参考示例：`{"version":"1.0","message_id":"<chat_id>","panels":[{"panel":"right","mode":"external","url":"<url>","title":"<环节业务名>"}]}`——冒号/逗号后**不得有空格**；`panel` 无 `**页面地址：**` 前缀，整段形如 `\n\n```xsbot-panel\n{紧凑JSON}\n```\n`。本节 v1.4 起由 `gen_panel_code` 直接拼装紧凑字符串（不用 `json.dumps` 默认带空格输出）。
   - 用户标识：仅**智能配置落地后**（02~09）面板 `offer_id` 取真实落地 offer_id（02 `save_product_config` 出参、03~06 config 环节结果解析、07~09 会话开始节点传入）；**落地前（00/01）无 offer_id，不传**；`req_id` 另作独立 url 参数透传（供页面按需求单号定位），有 req_id 的环节（00~06）`panel_inputs(...)` 传 `req_seq=<起始节点>` 绑定。
  - 页面落库：`frontend/public/ops-web/config-workbench.html`（原型 `配置工作台-独立页面.html` 落库并解析 `offer_id/name/chatId/req_id/stage/view` query，导航进度与默认 Tab 按 `stage` 动态加载）。

### 11.1 占位符语法红线（历次踩坑点）

平台按 `{入参名}` **精确匹配** `inputs` 中已绑定的参数名做替换。**严禁**把条件提示、说明文字、中文逗号、引号写进花括号**内部**：

```text
❌ 错误：{quality_gate，为空省略}　{valid，为空显示"未执行（…）"}　{value_dl}
```

占位符内任何多余字符（含中文逗号、引号、"为空省略"等备注）都会使模板引擎匹配不到对应入参，导致**结束节点渲染/导入异常、节点无法显示**。正确写法是**提示文字全部移到花括号外**：

```text
✅ 正确：{quality_gate}（为空省略）　{valid}（为空显示"未执行（…）"）　{download_url}（为空填写"暂不可用"）
```

要点：
1. 花括号内**只能**是 `inputs` 中真实存在的入参名（字母数字下划线），且必须逐字一致（含大小写）。
2. 空值兜底文案一律放花括号外，如 `（为空省略）`、`（为空显示"…"）`、`（为空填写"…"）`。
3. 引用入参名必须以 `inp(..., ref_block=..., ref_rel=...)` 声明，且 `ref_rel` 与上游出参名一致（复用 5.1 三层一致规则）。
4. xsbot-panel 外链不再写在结束节点模板内（已抽为独立代码节点 type=6 构建**单面板** `panel` 片段，运行期完成 `chat_id/offer_id/offer_name` 实值替换），结束节点模板只引用 `{panel}`；故除 `{panel}` 外，结束节点模板无需容纳内嵌 JSON 的占位引用。

---

## 十二、子流程节点（type=13）

> @deprecated：V2.0 早期曾以 `wf_main_intent` 主调度通过 `subflow_node` 路由到各 `wf_sub_*`，
> 已废弃（不生成 `wf_main_intent`）。当前 12 个子工作流由智能体按方案 3.2 提示词
> 【意图→工作流映射表】语义识别直调，**不使用 type=13 子流程节点**，本节仅作历史参考保留。

```python
subflow_node(seq, title, desc, work_flow_id, inputs, outputs)
```

- `nodeMeta.workFlowId` = 目标子流 `flowId`（如 `"wf_sub_00"`），必须存在对应 JSON。
- `inputs` 引用上游出参（如 dispatcher 的 `entities_json`），`outputs` 声明子流回传的摘要出参。
- 分支路由模式：`selector_node2` 判断 `intent` → `cond_str("REQ_REPORT")` 命中即出边到对应 `subflow_node`；其余意图走 `port=0` 进入下一判定（级联，见 wf_main_intent）。

---

## 十三、布局（apply_layout）

`apply_layout(data)` 用 Kahn 拓扑排序 + 最长路径主干定位，自动重排所有节点 `position`（列距 360 / 行距 260）。生成器写出前统一调用，**无需手填坐标**（`code_node`/`plugin_node` 的 `pos` 仅为生成器内初始占位）。

---

## 十四、生成与校验流程

1. 修改 `gen_workflows_v2.py`（新增/改节点、边、常量）。
2. 运行 `python gen_workflows_v2.py`，逐一打印 `written: <文件名>`。
3. 校验（重建后需 **12 个子工作流全量 ALL_OK**）：
   - 每个 `.json` 可被 `json.load`（`ensure_ascii=False, indent=2` 写出）。
   - 所有 `edges` 的 `startId/endId` 均存在于 `nodes` 的 `id`。
   - **fan-out 校验（节点出边单线约束）**：统计各 `startId` 出度，任一节点出度>1 且其 `type≠2`（非条件分支）即 FAIL（对齐 6.4）。允许的 fan-out 仅限 type=2 分支节点。
   - 所有 `dependencyData`/`dep_node` 引用的节点与出参真实存在。
   - 所有 `inp(..., ref_block=nid(x))` 的 `x` 是有效上游节点。
   - 所有引用出参（`ref_rel`）在对应上游节点 `outputs` 中已声明。
   - 插件 array 出参 `sechema` 有 item 树（对齐 `ARRAY_ITEM_FIELDS`）。
   - **结束节点占位符校验**：对每个 type=9 节点，提取 `outputs.content` 中所有 `{...}` 占位符（跳过 `xsbot-panel` 代码块内的内嵌 JSON），逐个核对是否与 `inputs[].name` 逐字一致（含大小写）；不一致即 FAIL（对齐 11.1，防止"节点无法显示"）。
4. 到平台上逐个导入并人工核对（条件分支 sourcePort 语义、结束节点模板、子流路由）。

> 交叉引用错误（断链）是最高频问题：新增节点后务必同步更新 `edge(...)`，以及上游的出参声明、下有的 `inp(...ref_block=...)`。

---

## 十五、编码规范红线（对照 AGENTS.md）

- 圈复杂度 ≤10、函数 ≤50 行、嵌套 ≤4；代码节点内联 Python 同样遵守。
- 注释解释"为什么"，删除魔法数字（用常量如 `MAX_RETRY`、`INTERVAL`）。
- 遵守 SOLID/高内聚低耦合：确定性逻辑抽代码节点（type=6），LLM 推理抽 LLM 节点（type=1），外部能力走插件节点（type=3）。
- 旧代码同步清理（去旧留新）；废弃节点/常量立即删除并同步 `ARRAY_ITEM_FIELDS` 等注册表。
- JSON 数据传输统一 snake_case。

---

**最后更新**：2026-09-20
**版本**：v1.9

### 变更记录
- v1.9（2026-09-20）：**补充节点出边单线约束（6.4）**：明确平台不允许一个节点连接两条及以上出边（fan-out），非条件分支（type≠2）节点至多一条出边，仅 type=2 分支节点允许 fan-out（按 sourcePort 路由）；给出修正原则（消除冗余边/串行化并行分支/必要时用 type=2 路由）并说明执行器用全局 ctx 解析引用、不依赖直接边故数据流不变；§十四校验流程同步新增 fan-out 校验项（任一节点出度>1 且 type≠2 即 FAIL）。依据本轮 wf_sub_03(节点202)/wf_sub_05(节点402)冗余边删除与 wf_sub_04 开始节点301三分支串行化修复沉淀。
- v1.8（2026-09-20）：**wf_sub_11 纳入生成器 + 数量口径统一为 12 个子工作流 JSON**：① `gen_workflows_v2.py` 新增 `wf_sub_11_发起需求审批` 生成段（对齐 V3.3 契约手写版行为：自查 requirement_report → 提取原文 → submit_release_approval(approval-type=requirement) → 回执渲染），并修复手写版引用未定义常量 `PANEL_00_TPL` 的缺陷，生成并写出 **12 个子工作流 JSON（`wf_sub_00`~`wf_sub_11`）**；② `wf_sub_00` 移除审批节点、结束节点改为止于确认点（对齐 V3.3"未确认不发起审批"契约）；③ 适用范围/文件组织/总原则/七节 seq 分段/十四节校验条数由"11/12/13 个"历史口径统一为 **12 个子工作流 JSON（无 `wf_main_intent` 意图调度主流程，智能体按方案 3.2 提示词【意图→工作流映射表】语义识别直调）**；④ 十二节子流程节点（type=13）标注 @deprecated（`wf_main_intent` 主调度已废弃）。12 个 JSON 已重生成并全量校验 OK。
- v1.7（2026-09-20）：**offer_id 仅智能配置落地后传真实值**：00/01 落地前不再把 `req_id` 误绑到 `offer_id` 参数（`off_seq/off_rel="req_id"` 走法废弃），面板 offer_id 传空、页面以 `req_id` 定位；02~06 保持取自落地 config 结果（`save_product_config` 出参 / config 记录解析），07~09 保持会话开始节点传入。12 个 JSON（15 个 panel 代码节点，含 wf_sub_11）已重生成，panel 执行校验 15/15 OK。
- v1.6（2026-09-20）：**面板 url 透传 `req_id` + 页面按 stage 动态联动**：`gen_panel_code` 生成的面板 url 追加 `&req_id=<实值>`（读取 `args.params.req_id`，有则实值、无则空串），`panel_inputs(...)` 新增 `req_seq` 参数，有 req_id 的环节（00~06）绑定起始节点 `req_id` 出参；`config-workbench.html` 解析 `req_id`，导航进度与默认 Tab 按 `stage` 动态加载（8 环节口径，08/09/10 辅助子流按就近展示）。12 个 JSON 已重生成并全量校验 OK。
- v1.5（2026-09-19）：**panel JSON 收敛为紧凑格式（无空格）**，逐字对齐参考示例 `{"version":"1.0","message_id":"<chat_id>","panels":[{"panel":"right","mode":"external","url":"<url>","title":"<环节业务名>"}]}`——`gen_panel_code` 改为直接拼装紧凑字符串（去掉 `json.dumps` 默认带空格输出），`title` 生成期烘焙为字面量；§十一补"JSON 必须紧凑"子项。11 个 JSON（14 个 panel 代码节点）已重生成，紧凑格式 + 结构校验全 OK。
- v1.4（2026-09-19）：xsbot-panel 收敛为**单面板、单 url**（对齐参考示例形态）：每个结束节点固定一个外链面板，url 统一用环节业务页 `config-workbench.html?offer_id=..&name=..&chatId=..&stage=<N>&view=stage`，`title` 用环节业务名；移除之前误做的"配置工作台（view=workbench）+ 环节业务页"双面板与 `product-detail.html` 监控看板，删除生成器死代码 `config_workbench_panel/biz_panel/xsbot_panel_block/MONITOR_PAGE`；`panel` 无 `**页面地址：**` 前缀，格式为 `\n\n```xsbot-panel {json} ```\n`（十一节）。11 个 JSON（14 个 panel 代码节点）已重生成，`_validate_flows.py` + panel asyncio 执行 + 占位符/边校验全部 OK。
- v1.3（2026-09-19）：xsbot-panel 从"结束节点模板内联"改为"独立代码节点构建"（十一节）：新增生成器辅助 `gen_panel_code`/`panel_inputs`/`panel_code_node`（type=6，出参 `panel`），11 个子工作流全部结束节点改为引用 `panel_code_node` 输出的 `{panel}`，删除结束节点模板内联 `xsbot-panel` 代码块与相应 `chat_id/offer_id/offer_name` 内联绑定；需求/方案阶段（00/01）面板 `offer_id` 取 `req_id` 兜底。生成器 + 11 个 JSON 已重生成并全量校验 OK。
- v1.2（2026-09-19）：新增「结束节点页面地址输出」统一约定（十一节）：11 个子工作流全部结束节点末尾追加双面板 `xsbot-panel` 外链块（配置工作台 + 环节业务页）；新增生成器辅助 `OPS_WEB_BASE/config_workbench_panel/biz_panel/xsbot_panel_block`；原型页落库 `frontend/public/ops-web/config-workbench.html`（解析 `offer_id/name/chatId/stage/view`）。修复 wf_sub_02 业务页与工作台入口重复问题（改用 `view=workbench|stage` 区分）。
- v1.1（2026-09-19）：新增 11.1 结束节点占位符语法红线（占用说明提示不得写入花括号内部，否则平台模板匹配失败导致节点无法显示）；十四节同步新增"结束节点占位符校验"项；修复 wf_sub_01/04/06/07 结束节点模板并重新生成。
- v1.0（2026-09-18）：首版，对齐 gen_workflows_v2.py 生成器与 12 个工作流。
