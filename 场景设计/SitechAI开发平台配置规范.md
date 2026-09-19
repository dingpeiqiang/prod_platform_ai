# Sitech AI 开发平台配置规范

> 适用范围：九思大模型 · 低代码智能体平台的配置开发（插件/工作流/智能体/知识库）。
> 依据：`场景设计/ah_cti_poc/老版本方案/`（产销品加载AI应用 5 份方案）、
> `场景设计/ah_cti_poc/工作流配置/工作流JSON开发规范.md`、
> 生成器 `gen_workflows.py`/`gen_plugins.py` 及实际 JSON 产物。
> 用途：平台配置开发的通用规范与最佳实践。

---

## 一、平台整体架构（配置层面四层）

```
智能体/助手（对话式统一入口 + 调度中枢）
  ├── 提示词（角色+技能+限制 / 意图→子工作流映射表）
  ├── 插件（工具，HTTP URL 封装原子能力）
  ├── 工作流（节点+边的有向图，承载多环节编排）
  └── 知识库（K1~K5 分类，挂载到工作流节点）
```

**核心职责分工原则**：
- 大模型只做"理解与生成"，不直接触碰生产系统；
- 一切系统交互封装为**插件工具（HTTP）**；
- 确定性逻辑抽**代码节点(type=6)**，LLM 推理抽 **LLM节点(type=1)**，外部能力走**插件节点(type=3)**；
- 流程化编排用**工作流**，调度中枢用**智能体（LLM 按意图映射表直调子流）**。

---

## 二、源码驱动（最重要的工程纪律）

1. **所有工作流 JSON 与插件 Export JSON 都是生成器产物，一律禁止手工改 JSON**；
2. 改工作流 = 改生成器（`gen_workflows_v2.py` / `gen_plugins.py`）→ 运行生成 → 全量校验 ALL_OK；
3. 校验项（交叉引用断链是最高频问题，务必全查）：
   - 每个 JSON 可被 `json.load`（`ensure_ascii=False, indent=2` 写出）；
   - 所有 `edges` 的 `startId/endId` 均存在于 `nodes` 的 `id`；
   - 所有 `dependencyData`/`dep_node` 引用的节点与出参真实存在；
   - 所有 `inp(..., ref_block=…)` 引用的是有效上游节点；
   - 所有引用出参（`ref_rel`）在对应上游节点 `outputs` 中已声明；
   - 插件 array 出参 `sechema` 有 item 树（对齐 `ARRAY_ITEM_FIELDS` 白名单）。

---

## 三、工作流 JSON 顶层结构

```jsonc
{
  "flowName": "产销品-智能配置",          // 展示名 = 文件名中文部分
  "flowRemark": "…",                      // 流程说明（改流程时同步更新）
  "flowIco": "workflowIcon",              // 固定
  "workFlowSchemaJSON": {
    "nodes": [ /* 节点数组 */ ],
    "edges": [ /* 有向边数组 */ ],
    "flowId": "wf_sub_02",                // 唯一流程标识 = 文件名
    "version": "1.6"
  },
  "userScope": 4,
  "projectId": ""
}
```

文件命名：`wf_{类型}_{中文名}.json`；子流 `wf_sub_{两位序号}`，主调度 `wf_main_intent`。

---

## 四、节点类型（type 全集）

| type | 节点 | 生成器函数 | 必备字段 | 配置要点 |
|---:|---|---|---|---|
| 0 | 开始 | `start_node(seq, inputs)` | `inputs` | 声明工作流入参 |
| 1 | LLM | `llm_node(seq, title, prompt, in_refs, outputs, sys_prompt, model)` | `outputs`、`inputs.llmParam[].content`(提示词)、`inputParameters`、`max_tokens=2048/temperature=0.2/top_p=0.5/model=qwen3-30b-a3b` | 提示词用 `{}` 占位引用上游出参 |
| 2 | 条件分支 | `selector_node2(seq, title, deps, branches)` | `inputs.condition`(conditions数组)、`dependencyData`(dep_node) | **sourcePort=-1 否则分支 / 0 如果分支** |
| 3 | HTTP插件 | `plugin_node(seq, title, code, desc, url, inputs, outputs, method)` | `url`、`submit_way`(post/get)、`outputs`(含 sechema)、`nodeMeta.code` | 走网关 `BASE_URL + /api/v1/appstore/*` |
| 6 | 代码节点 | `code_node(seq, title, code, in_refs, outputs)` | `code`(Python `async def main(args)`)、`language=1` | 入参 `args.params`，出参 `Output` dict |
| 9 | 结束 | `end_node(seq, title, inputs, out_content)` | `outputs.content`(模板)、`settings/output_mode`、`terminatePlan` | `{}` 占位符引用入参 |
| 13 | 子流程 | `subflow_node(seq, title, desc, work_flow_id, inputs, outputs)` | `nodeMeta.workFlowId`、`history` | 路由到目标 wf_sub_* |

---

## 五、入参 / 出参契约（核心）

### 5.1 入参两态（`inp()`，由 `ref_block` 是否为空区分）

**常量入参**（`ref_block=""`）：
```jsonc
{ "blockID": "", "relName": "", "name": "node_name",
  "description": "环节名=config", "type": "string",
  "required": true, "content": "config" }
```

**引用入参**（`ref_block=nid(上游seq)`，需 `nameValue/currValue`）：
```jsonc
{ "blockID": "a1b2c3d4-…-0101", "relName": "req_id", "name": "req_id",
  "description": "存储键（=开始节点 req_id）", "type": "ref", "required": true, "content": "",
  "nameValue": ["a1b2c3d4-…-0101", "a1b2c3d4-…-0101,req_id"],
  "currValue": "a1b2c3d4-…-0101,req_id" }
```

> **引用入参必须三层一致**：`blockID`、`nameValue[0]`、`currValue` 前缀均为上游 `id`；
> `nameValue[1]`/`currValue` 的整体为 `{上游id},{出参名}`。任何一层不一致即断链。

### 5.2 出参（`out()`）

- LLM/代码节点出参用 `out(name, desc)`，导出扁平对象 `{name, description, type, content:""}`；
- 插件节点用 `out` 三元组并在 `plugin_out` 中声明 `sechema` item 树；
- **array 型出参必须声明 item 结构**（`ARRAY_ITEM_FIELDS` 白名单），否则校验 FAIL；嵌套 array（如 testScenes→testCasePointResults）递归生成内层 item。

### 5.3 命名铁律

- JSON 数据传输、出参名、插件字段统一 **snake_case**（`offerId` 为唯一 camelCase 特例，因后端参数名一致）；
- 环节存储 `node_name` 固定枚举：`requirement_report` / `requirement` / `config` / `spec` / `fee` / `test` / `report`。

---

## 六、边与条件分支

### 6.1 普通边
```jsonc
{ "sourcePort": null, "startId": nid(start), "endId": nid(end) }
```

### 6.2 分支边（`edge(start, end, port)`）
- `sourcePort = -1` → **否则/兜底分支**（条件定义在 `port=-1`）；
- `sourcePort = 0` → **如果分支**（无条件定义时由平台自动路由）。

### 6.3 条件构造
- `cond_ref(block_seq, rel, block_title)`：条件左值 = 引用某节点出参；
- `cond_str(value)`：条件右值 = 字符串常量（`currValue` 固定 `","`，常量放 `content`）；
- `cond_item(left, operator, right)`：单项条件，`operator` 取 `OPS`（1等于/2不等于/3长度大于/…/9为空/10不为空/15长度等于）；
- `dep_node(block_seq, title, rel_names)`：`dependencyData` 条目，声明分支条件依赖的节点与出参；
- 每个分支 `conditions` 项结构：`{"sourcePort": port, "itemflag": true, "logic": 1, "conditions": [cond_item,...]}`。

---

## 七、ID 规范

`nid(seq, ns="a1b2c3d4")` → `"a1b2c3d4-0000-4000-8000-{12位seq}"`（UUID 形态）。

- 同一子流内 seq 唯一；边、分支、引用都用 `nid(seq)`，保证全局唯一；
- seq 建议按子流分段（sub_00 用1~99、sub_01 用101起、sub_03 用201、sub_04 用301、sub_05 用401、sub_07 用701、sub_09 用901、sub_10 用1001），避免冲突；
- 新增节点后务必备注其 `nid` 并同步更新引用（断链最高频）。

---

## 八、HTTP 插件节点规范

```python
plugin_node(seq, title, code, desc, url, inputs, outputs, method="post")
```

- `code`：插件标识（如 `save_product_config`、`query_node_result`），写入 `nodeMeta.code`，与自研插件集 JSON 定义一致；
- `url`：`BASE_URL + "/api/v1/appstore/..."`；
- `submit_way`：`post` 或 `get`（如 `query_node_result` 用 `get`）；
- `authentic_info`/`authentic_info_new` 由生成器统一填充，无需手改；
- 工具 code 与 URL 路径映射（示例）：
  - `query_similar_offer` → `/similar/offer/query`
  - `realtime_spec_audit` → `/audit/realtime`
  - `save_product_config` → `/product/config/save`
  - `offer_test` → `/test/offer/start`、`get_test_scenes` → `/test/offer/scenes`、`get_test_result` → `/test/offer/result`
  - `check_billing_rule` → `/billing/rules/verify`
  - `submit_release_approval` → `/approval/submit`、`query_approval_status` → `/approval/status`
  - `query_product_monitor` → `/product/monitor`、`send_alert` → `/alert/send`
  - `save_node_result` → `/result/save`、`query_node_result` → `/result/query`(get)
  - 适配端点：`/ops/root-cause`、`/ops/work-orders`、`/shelf-compliance`、`/validate-nested`、`/explain`、`/report/download`、`/script/download`

---

## 九、代码节点（type=6）规范

写法约束：
```python
import json
from typing import Any, Dict
async def main(args):
    p = args.params          # 入参从 args.params(dict) 读取
    ...                       # 确定性逻辑
    ret: Output = {"k": v}
    return ret
```

要点：
1. 入口固定 `async def main(args)`，入参名与 `code_node(...)` 的 `in_refs` 的 `name` 一致；
2. 需要 HTTP 调用时用 `urllib.request`（代码节点无平台工具调用能力），URL 用占位符 `BASE_URL` 并在定义后 `.replace("BASE_URL", BASE_URL)`；
3. **后端/网关不可达一律优雅回退**：设 `backend_pending=1` + `note`（诚实占位口径），禁止抛异常中断或编造结论；
4. 确定性逻辑可从脚本直搬（去 argparse/CLI、改 `async main(args)`）；
5. 出参名含 `_json` 的存 JSON 字符串（如 `plan_json`），下游需 `json.loads` 解析（含"标签: {}"前缀容错）。

---

## 十、LLM 节点规范

```python
llm_node(seq, title, prompt, in_refs, outputs, sys_prompt="", model="qwen3-30b-a3b")
```

- `inputs.llmParam[].content` 为完整提示词；`{}` 为引用占位（如 `{elements_json}`），平台运行时以对应出参替换；
- `inputs.inputParameters` 为引用入参数组；
- 固定参数：`temperature=0.2`、`top_p=0.5`、`max_tokens=2048`、`prompt_system`；
- **模型字段**：`model`（LLM 节点默认模型，固定 `qwen3-30b-a3b`）由生成器统一写入；
- 提示词末尾约定"输出要求：仅输出…对应出参…，不输出其他多余文字"，保证单出参/多出参隔离。

---

## 十一、结束节点规范

```python
end_node(seq, title, inputs, out_content)
```

- `inputs`：要回显的入参（引用上游出参）；
- `out_content`：模板字符串，用 `{入参名}` 占位（如 `"《执行方案》已生成（req_id：{req_id}）\n\n{plan_md}"`）；
- 常附加`【下一步】`/`【确认执行】`引导语，衔接下游子流；
- 分支结束节点各自独立 `end_node`（如 `结束(有待补充)` / `结束(无待补充)`），title 区分语义。

---

## 十二、子流程节点（type=13）

`wf_main_intent` 通过 `subflow_node` 路由到各 `wf_sub_*`：

```python
subflow_node(seq, title, desc, work_flow_id, inputs, outputs)
```

- `nodeMeta.workFlowId` = 目标子流 `flowId`（如 `"wf_sub_00"`），必须存在对应 JSON；
- `inputs` 引用上游出参（如 dispatcher 的 `entities_json`），`outputs` 声明子流回传的摘要出参；
- 分支路由模式：`selector_node2` 判断 `intent` → `cond_str("REQ_REPORT")` 命中即出边到对应 `subflow_node`；其余意图走 `port=0` 进入下一判定（级联）。

---

## 十三、插件工具 Export 规范

### 13.1 构成（`build_plugin` 产物，对齐平台真实导出格式）

- **flowJson**：`inputs`/`outputs` 均以单一 **ROOT 节点**（type=object），参数树挂在 `sechema` 嵌套链（每层含 `UpNodeName`，叶子 string 带 content）；
- **schemaJson**：OpenAPI 3.1，请求/响应均由 ROOT(object) 展开 properties；array 节点 sechema 内为 `item` object；
- **toolJson**：`input_parameters` 键为全路径（本方案简化 `ROOT-参数名`）；`parameters` 平铺全部节点（容器 `isParameter=0`、叶子 `=1`），带 `id/upId/upNodeName`；
- **export 顶层**：`toolCode`/`toolName`/`submit_way`/`interfaceAddress`/`schemaJson`/`toolJson`/`flowJson`(JSON字符串)/`induction`/`orgId:"10000"`/`userScope` 等。

### 13.2 关键口径
- `induction`（出参归纳）：查询类 `Y` / 执行类 `N`（避免二次加工）；
- `submit_way`：`post` 或 `get`；
- `type_for_tool:"url"`、`type_for_url:"http"`、`url_for_model`；
- **入参必填参数必须配"为空提示"**：格式 `缺少{参数中文名}，请{获取方式}`（如"缺少销售品ID，请先完成配置落地"）；
- **大报文参数**（`config_json`/`plan_json`）"是否提参=否"，由工作流变量引用，不走 LLM 提取。

---

## 十四、知识库配置

| 分类 | 用途示例 | 挂载点 |
|---|---|---|
| K1 业务规范库 | 稽核判定依据、需求解析参照 | 需求分析节点、智能体对话 |
| K2 资费规则库 | 资费校准依据、风险解读 | 资费校准节点 |
| K3 测试规范库 | 用例设计、报告模板 | 自动测试节点 |
| K4 存量资料库 | AI推理取值参照、相似度解读 | 需求分析节点（核心） |
| K5 FAQ | 智能体问答 | 智能体对话 |

- 切片：512 token/片（K4 按章节自然边界，单片 ≤800）、重叠 50、混合检索语义权重 0.7、top_k=3(K4)/2(其他)、score 阈值 0.75、引用展示开启；
- 命名：`[分类代码]_[文档名]_[版本号]`；版本更新"先传新再删旧"避免召回空窗；
- 固定问答回归：每次更新后执行若干组固定问答。

---

## 十五、关键工程约束（平台能力边界与通用约定）

1. **无循环节点** → 轮询用 type=6 代码节点内嵌 `asyncio.sleep(5)`（360次/30min），超时转人工并保留 `globalId`；
2. **req_id 统一贯穿**：环节1 生成（`PLAN+yyyyMMddHHmmss+3位随机数`，代码节点以系统时钟生成，**LLM 不参与**），2~9 全程沿用；
3. **状态/续跑回放**：`save_node_result` + `query_node_result` 自查链路（req_id + node_name），写接口不重复调用；
4. **布局自动**：`apply_layout` Kahn 拓扑排序 + 最长路径主干重排（列距360/行距260），无需手填坐标；
5. **优雅回退**：所有外部依赖都要 `backend_pending=1` 降级，保证离线 Demo 与诚实口径。

---

## 十六、可复用最佳实践（踩坑提炼）

1. **确认门禁取舍**：后端硬门禁防跳步 vs LLM 语义识别。联调发现 LLM 跳步/漏写标记致合法调用被误拒（`NOT_CONFIRMED`），**移除后端门禁、交由 LLM 语义识别**（防跳步职责回归提示词）；但审批等关键环节仍保留结果硬校验（四环节 result 齐全才放行）。
2. **取值断链（最高频 bug）**：`query_node_result` 出参 list 是记录数组，必须**代码节点提取 `list[0].result_json` 原文**再透传，禁止数组整体传参。
3. **出参单一事实源**：当 LLM 输出可能非最新值时（如 plan_json），下游改由代码节点/推理引擎统一组装，避免表格与引擎结果偏差。
4. **字段补全引擎化**：枚举/格式/默认值/来源由后端本体推理引擎（`field_ontology_reason`）以代码为单一事实源提供，替代"LLM 自觉遵守提示词"，保证稳定。
5. **模拟结果兼容**：自研接口以真实种子数据（如 18 个销售品）为底座，任一输入均返回结构一致的可复现结果，保证端到端可随时演示；契约（路径/入参/出参）与真实实现一致，便于后续替换。

---

## 十七、校验与发布流程

1. 修改生成器 → 运行生成，逐一打印 `written: <文件名>`；
2. 全量校验 ALL_OK（见第二节交叉引用校验）→ 平台逐个导入并人工核对（条件分支 sourcePort 语义、结束节点模板、子流路由）；
3. 工作流/插件保持草稿/发布双状态，保留可回退版本；
4. 智能体发布：先草稿调试 → 验收 → "所有人可见"。

---

**最后更新**：2026-09-18
**版本**：v1.0
