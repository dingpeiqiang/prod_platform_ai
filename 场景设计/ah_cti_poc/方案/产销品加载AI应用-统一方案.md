# 产销品加载 AI 应用 · 统一方案

> 本方案为《产销品加载 AI 应用》**统一方案**，整合精简自原三份文档：主方案《产销品加载AI应用开发方案.md》（原整合去向：第 1~3 章、第 5 章、第 8~11 章及附录）、专项设计《-专项设计方案汇编.md》（原第 6 章：融合商品加载/模板驱动重构/本体推理/存量实例化/数据清洗）、细化设计《-细化设计方案.md》（原第 4 章工具契约、第 5 章工作流细化、第 7 章部署/命名/异常矩阵/知识库）。原三份文档已删除，本方案即权威唯一口径（业务口径/字段体系/子流编号/判定字段/fail_node 映射/req_id/落库 node_name/审批硬校验契约保持不变）。
>
> - **平台**：AI应用开发（九思大模型 · 低代码智能体平台）
> - **场景**：安徽电信 CPCP 产销品域 · 数字员工（必选场景）
> - **当前基线**：V3.8（2026-09-20）——12 个工作流 JSON（`wf_sub_00`~`wf_sub_11`）+ 智能体（LLM）语义识别直调 + `knowledge/` 知识库 + 后端 `/api/v1/appstore/*` 适配端点。原始版本履历（V1.2~V3.8）见原主方案（已删除），本方案不再逐条罗列，仅在必要时备注关键口径沿革。
> - **相关文档**：《工作流JSON开发规范》（`工作流配置/`）、《知识文档来源与采编指南》《端到端演示剧本》《本体应用方案》、`cpcp_product_ontology.ttl`、《SitechAI开发平台配置规范》。

---

## 1. 方案总览

### 1.1 建设目标

以数字员工替代产销品上架全流程人工操作，实现：

**需求提报（环节1/8）→ 需求分析（环节2/8，五列模块表格《加载方案》）→ 需求工单审批（用户确认后发起）→ 用户确认 →【智能配置（环节3/8）→ 规格稽核（环节4/8）→ 资费校准（环节5/8）→ 自动测试（环节6/8，含受理验证子集）】**逐环节步进推进（每环节处理完输出结果 + 下一步建议，用户反馈确认后才进入下一环节）**→ 上线审批（环节7/8，用户二次确认后发起）→ 监控运维（环节8/8，确认上线后生成监控运维方案）** 的端到端闭环。

**关键口径（沿革备注）**：
- **8 环节统一编号**：1 需求提报 / 2 需求分析 / 3 智能配置 / 4 规格稽核 / 5 资费校准 / 6 自动测试（**受理验证并入环节6，不独立成节**）/ 7 上线审批 / 8 监控运维；标题头统一 `【环节N/8·全名】`。
- **步进式人机协同（V3.6 起替代 V3.2 自动串行口径）**：用户确认配置后**逐环节调度**（`wf_sub_02`→`wf_sub_03`→`wf_sub_05`→`wf_sub_04`），每一环节处理成功后先输出该环节结果 + 下一步建议，随即暂停等待用户反馈，用户确认后才调度下一环节；仅环节失败/异常时提前中断。
- **需求工单审批/上线审批均二次确认**：提报单就绪即止于确认点（V3.3 起 `wf_sub_00` 不再自动发起审批，由新增子流 `wf_sub_11` 在用户明确回复【发起需求审批】后发起）；上线审批须执行主干全部成功后由用户确认发起，后端四环节硬校验兜底。

### 1.2 全流程 8 环节总表（环节编号全文以此为准）

| 环节 | 全名 | 承载子流 | 关键动作 | 判定字段 |
| --- | --- | --- | --- | --- |
| 1 | 需求提报 + 需求工单审批 | `wf_sub_00`（止于确认点）+ `wf_sub_11`（发起需求审批） | 要素提取 → CODE_RENDER_REQ 渲染提报单 →**止于确认点**；用户回复【发起需求审批】后由 `wf_sub_11` 发起 `submit_release_approval`(approval-type=requirement) | 提报单就绪 / 审批单号 |
| 2 | 需求分析 | `wf_sub_01`（模板轨） | 要素提取 → validate_elements 质量门禁 → merge_nested → validate_nested 本体校验闸 → render_table → 保存 requirement → **输出方案止于确认点** | plan_json / plan_md |
| 3 | 智能配置 | `wf_sub_02` | 自查 requirement → save_product_config → CODE_FUSION_GROUP_ECHO 融合成员回显 | status==SUCCESS/PARTIAL |
| 4 | 规格稽核 | `wf_sub_03`（组维度） | 自查 config → realtime_spec_audit（error_list 含 group 类目） | pass==1 |
| 5 | 资费校准 | `wf_sub_05`（成员分组） | 自查 config → check_billing_rule（check_scene=all，member_role 分组） | pass==1 |
| 6 | 自动测试（含受理验证） | `wf_sub_04` | 自查 config → offer_test → get_test_scenes → CODE_POLL_PROGRESS 轮询 → get_test_result → CODE_MAP_FIXED_CASES（31 条固定用例）→ 九章节报告 → 报告下载；**受理验证为其子集**（orderId/offerInstId 结论随报告输出，不独立成节） | test_passed==通过 |
| 7 | 上线审批 | `wf_sub_06`（双轨） | CODE_SUMMARY_APPROVAL 汇总 → submit_release_approval(approval-type=launch) → **推送即结束**（不轮询/不分流）；审批状态由 wf_sub_08 查询 | approval_id / status |
| 8 | 监控运维 | `wf_sub_07` | query_product_monitor → 异常分支 CODE_OP_ROOT_CAUSE 根因推理 + CODE_OP_CREATE_WO 建工单闭环 | 指标 / 工单号 |

> 辅助子流（不占环节编号）：`wf_sub_08` 审批进度查询（双轨）、`wf_sub_09` 存量产品查询（只读）、`wf_sub_10` 存量合规扫描。

### 1.3 总体架构（平台能力映射）

| 平台能力 | 在本场景中的角色 |
| --- | --- |
| 智能体（助手） | 产销品数字员工统一入口（对话式 + 流程式调度中枢）；由智能体（LLM）按 3.2 提示词【意图→工作流映射表】**语义识别直调** 12 个子工作流（逐环节步进推进；需求工单审批须用户明确确认后才发起）；流程结束后承接**消息查询**（审批进度/监控结果）与**异常处置引导**（重新执行/修改执行方案） |
| 工作流 | 承载 8 个业务环节的 12 个子工作流 JSON（`wf_sub_00`~`wf_sub_11`；req_id 单必填入参自查链路，无意图调度主流程）；子流内部含环节结果存储节点（save_node_result） |
| 插件/工具 | 封装 HTTP API 能力接口（相似度分析/智能稽核/智能测试等 13 工具 + 7 个后端适配端点 + 节点结果存储查询插件，共 21 个），全部自研模拟实现（兼容《产品信息.txt》18 个销售品） |
| 知识库 | `knowledge/` 目录：K1 业务规范 / K2 资费规则 / K3 测试规范 / K4 存量销售品资料 / K5 FAQ + `ontology-fields.json`、`seed_offer_groups.json`、templates 注册表、存量产品目录、K5 存量报文等结构化资产 |
| 本体库（TTL） | 产销品域结构化知识（`cpcp_product_ontology.ttl`），支撑需求校验、关系推理、配置生成约束、术语对齐；模板轨经 `/validate-nested` 网关端点接入 Java 推理平台 |
| 大模型节点 | 需求解析（要素翻译、执行方案生成）、稽核整改建议、测试报告生成（九章节，含受理验证结论）、资费风险解读、上线报告生成 |
| 选择器节点 | 稽核通过/驳回、资费通过/驳回、测试通过/失败等分支控制（子流内部）；确认门禁由智能体语义识别 + 后端硬校验兜底 |

### 1.4 实现方式（V2.0 工作流重塑，当前基线）

| 原平台构件（第 2~6 章业务口径描述） | V2.0 工作流 JSON 实现（当前基线） |
| --- | --- |
| 智能体常驻提示词 | 智能体（LLM）按 3.2 提示词【意图→工作流映射表】做**语义识别并直调** 12 个子流；无独立意图调度主流程 |
| 子工作流 `wf_sub_01~08` | 12 个工作流 JSON：`wf_sub_00`~`wf_sub_11`（`gen_workflows_v2.py` 确定性生成，位于 `工作流配置/智能体工作流集V1.6/`） |
| 插件工具 1~14 契约 | 后端 AppStoreV16Controller `/api/v1/appstore/*` 适配端点（13 工具 + 7 个 V2.0 新增端点，网关 `BASE_URL=http://10.86.13.201:31281`） |
| 代码节点 | 确定性逻辑内嵌为工作流 **type=6 代码节点**（CODE_RENDER_REQ / CODE_GET_TEMPLATE / CODE_VALIDATE_ELEMENTS / CODE_MERGE_NESTED / CODE_OP_VALIDATE_NESTED / CODE_RENDER_TABLE / CODE_EXTRACT_RECORD / CODE_POLL_PROGRESS / CODE_MAP_FIXED_CASES / CODE_FUSION_GROUP_ECHO / CODE_OP_ROOT_CAUSE / CODE_OP_CREATE_WO / CODE_OP_SHELF_COMPLIANCE / CODE_SUMMARY_APPROVAL / CODE_DOWNLOAD_* 等） |
| 平台知识库 K1~K5 + 向量召回 | `knowledge/` 目录 + 确定性代码节点/后端服务按需读取（K4 按销售品 ID 单文件精确定位，禁止全量读取） |
| 平台门禁 | 后端硬校验保留（req_id 格式 5002 / 四环节门禁 / 幂等）；**确认门禁已移除**——方案确认与审批确认由智能体语义识别保证（提示词【限制】） |

**执行入口**：用户对话 → 智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别（含实体提取 req_id/offer_id/approval_id）→ 直调对应子流 → 子流内经代码节点 + 后端适配端点完成业务处理 → 结束节点输出结果。大报文（plan_json/config_json/fields/report）一律经节点结果存储（req_id+node_name）或后端下载端点传递，不经模型上下文中转。

**重塑核心原则**：业务逻辑零改动（3 模块/9 分类/24 字段、待补充判定、本体推理引擎、req_id 统一键、异常矩阵原样保留）；接口契约零改动（替换真实实现仅改网关 BASE_URL）；确定性逻辑（merge_nested/render_table/validate_elements/get_template/render_requirement_report/map_fixed_cases/extract_record/poll_progress 等）由技能包脚本子命令迁移为工作流 type=6 代码节点，同一份 Python 逻辑整体内嵌、行为可审计。

---

## 2. 智能体设计（产销品数字员工）

### 2.1 基本信息

| 项 | 值 |
| --- | --- |
| 助手代码 / 名称 | `cpcp_product_worker` / 产销品数字员工 |
| 功能介绍 | 覆盖需求提报→需求分析（五列模块表格《加载方案》）→智能配置→规格稽核→资费校准→自动测试（含受理验证）→上线审批→监控运维的 **8 环节**全流程自动化 |
| 模型配置 | 温度 0.2（严谨输出）、top_p 0.5、max_tokens 2048、多轮对话 20 轮；LLM 节点统一 `qwen3-30b-a3b` |
| 开场白 | "您好，我是产销品数字员工，可协助您完成销售品从需求提报、加载方案生成、确认后智能配置、稽核校准、自动测试到上线审批、监控运维的全流程。请上传需求文档或直接描述需求，我将为您生成《加载方案》。" |
| 引导问题 | 1) 我要上新一个 5G 流量套餐，请帮我分析需求并生成加载方案 2) 确认配置刚才的产销品加载方案 3) 查询刚才那个销售品的审批进度 4) 查询销售品 900102308 的运行监控结果 5) 重新执行失败的环节 |
| 答案为空提示 | "抱歉，我仅支持产销品加载相关业务，请更换问题或联系管理员。" |

### 2.2 提示词【角色 + 技能 + 意图映射表 + 限制】（职责分层）

> **职责分层口径**：智能体（LLM）**只负责三件事**——① 语义识别（按【意图→工作流映射表】直调子工作流）；② 业务理解/补全（需求分析提取要素、补全字段、生成《加载方案》）；③ 结果表达（按固定模板输出环节结果、下一步建议、异常引导、汇总块）。**确定性技术细节不进提示词**：req_id 生成与透传、环节结果落库（node_name）、环节成败判定（status/pass/test_passed）、fail_node 续跑、审批四环节硬校验——由子流自查链路（query_node_result）+ type=6 代码节点 + 后端硬校验兜底（见第 5 章）。全流程 8 环节口径、步进式人机协同。

```
【角色】
你是安徽电信产销品域数字员工，精通 CPCP 产销品管理、CRM 配置、计费规则、订单受理与测试验证，
负责销售品从需求到上线的端到端自动化加载（业务理解与表达）。你不直接操作 CRM、不修改配置、
不代替用户做最终业务决策；所有落地动作由对应子工作流在内部完成，你只负责识别意图、直调子
工作流、并按返回结果向用户展示与引导。

【技能】
1. 需求理解与《加载方案》生成（环节1/8+2/8）：理解需求→按 3 模块/9 分类/24 字段拆解要素→补全
   →输出《加载方案》（五列模块表格 + 执行方案）。补全原则：需求原文最高优先、不得虚构；字段来源
   仅"原始需求/AI补全"两态；仅套餐档位（价格）缺失且无参照才标"待补充"（禁推理价格）；套餐编码标
   "系统待生成"。**提报单生成后必须提示"是否发起【需求工单审批】"并止于确认点**；**执行方案生成后
   同样止于确认点**并提示"请确认执行方案，回复【确认配置】后开始配置落地"。
2. 执行主干引导（环节3/8~6/8）：用户确认后**逐环节推进**——每环节子流返回后立即输出结果 + 下一步
   建议并暂停等待用户反馈，用户确认（继续/下一步/同意）后才直调下一环节；仅失败/异常时中断。
3. 审批与上线（环节7/8）：四环节全部成功后输出【执行主干全部完成】汇总块并提示"是否发起上线审批"；
   用户确认后才发起。
4. 查询与运维（环节8/8+辅助）：按用户消息直调 审批进度查询/监控运维/存量产品查询/存量合规扫描
   对应子工作流并展示结果。

【意图→工作流映射表（语义识别 → 业务动作 → 直调子工作流）】
| 用户意图 | 业务动作 / 直调子工作流 | 业务说明 |
| --- | --- | --- |
| 提报/修改需求（环节1/8） | 需求提报 → 先生成提报单**止于确认点**；用户回复【发起需求审批】后再发起审批 | req_id 由子流生成/透传，LLM 不管理 |
| 发起需求工单审批（环节1/8） | 直调 wf_sub_11 发起需求工单审批（须用户明确确认） | 发起即止于回执展示 |
| 生成加载方案（环节2/8） | 需求分析，输出《加载方案》**止于确认点** | 未确认前不得进入执行主干 |
| 确认配置（环节3/8~6/8） | 执行主干**逐环节步进推进**：智能配置→规格稽核→资费校准→自动测试；每环节止于反馈点，用户确认后才推进下一环节，失败即中断 | |
| 重新执行失败环节 | 从上次失败环节继续（子流自查自动续跑，已成功环节不重复） | fail_node 映射 |
| 上线审批（环节7/8） | 发起上线审批（用户确认后发起；后端四环节硬校验） | |
| 查询审批进度（辅助） | 直调 wf_sub_08（需求工单审批/上线审批双轨） | approval_id/offer_id |
| 查询监控运维（环节8/8） | 直调 wf_sub_07（异常根因+工单闭环） | offer_id |
| 查询存量产品（辅助） | 直调 wf_sub_09（只读） | 产品名/ID |
| 存量合规扫描（辅助） | 直调 wf_sub_10 | 存量范围 |
| 业务问答/超范围 | 按 K1~K5 知识库检索回答；超范围按答案为空提示 | 检索词 |

【限制】
1. 仅回答产销品加载相关业务，超范围按"答案为空"提示。
2. 执行方案确认门禁：需求分析生成《加载方案》后必须止于确认点，用户明确回复确认前不得进入执行主干。
3. 确认语义未命中绝不进入执行主干；执行主干不跳步、不并行。
4. 需求工单审批须用户明确确认后才发起：提报单生成后必须提示"是否发起【需求工单审批】"，未明确回复
   【发起需求审批】前不得发起。
5. 逐环节确认推进：执行主干按序推进，每环节结果输出 + 下一步建议后暂停等待用户反馈，严禁未经确认
   擅自推进下一环节；仅失败/异常时提前中断。
6. 结果表达铁律：环节结果、✅ 与统计值必须与子工作流返回结果一一对应，禁止凑数、虚构、自行推断成败。
7. 异常处置：环节失败/异常时中断，按异常模板输出环节/原因/明细/建议，引导【重新执行】/【修改执行方案】。
8. 职责不越界：req_id 透传、环节落库、成败判定、续跑回放、审批硬校验等由子工作流/代码节点/后端保障，
   你不得自行构造、修改或绕过。
```

### 2.3 配置项

| 配置项 | 取值 |
| --- | --- |
| 插件 | 挂载第 4 章全部插件工具与后端适配端点（21 个：13 工具 + 节点结果存储查询插件 + 7 适配端点） |
| 工作流 | 挂载 12 个子工作流 JSON（`wf_sub_00`~`wf_sub_11`，见 `工作流配置/智能体工作流集V1.6/`，`gen_workflows_v2.py` 生成；无意图调度主流程，由智能体语义识别直调） |
| 知识库 | 产销品业务规范库 / 资费规则库 / 测试规范库 / 存量销售品资料库 / FAQ（K1~K5，`knowledge/` 目录） |

---

## 3. 需求到平台能力映射

### 3.1 业务环节 → 平台实现载体

| 业务环节 | 业务目标 | 平台实现载体 |
| --- | --- | --- |
| 需求提报（环节1/8） | 收集需求、生成需求工单；审批须用户明确确认后才发起 | `wf_sub_00` 止于确认点（LLM 要素提取 → CODE_RENDER_REQ 渲染提报单 + "是否发起【需求工单审批】？"）；用户回复【发起需求审批】后由 `wf_sub_11` 承接：自查 requirement_report → 提取原文 → `submit_release_approval`(approval-type=requirement) |
| 需求分析（环节2/8） | 业务要素→配置字段映射，生成《加载方案》并存储；生成后止于确认点 | `wf_sub_01` 模板轨：要素提取 → CODE_VALIDATE_ELEMENTS 质量门禁 → CODE_MERGE_NESTED → CODE_OP_VALIDATE_NESTED 本体校验闸 → CODE_RENDER_TABLE → 保存 requirement → 输出方案即止于确认点 |
| 用户确认 | 人工确认执行方案后再配置落地 | 智能体提示词【限制】识别确认语义（未命中确认不调度 `wf_sub_02`） |
| 执行主干（步进式） | 配置→稽核→资费→测试逐环节推进 | 智能体语义识别"确认配置"后逐环节调度 `wf_sub_02`→`wf_sub_03`→`wf_sub_05`→`wf_sub_04`（每环节止于反馈点，失败即中断）；异常按 fail_node 续跑 |
| 智能配置（环节3/8） | 按 req_id 自查执行方案 JSON 并落地 CRM | `wf_sub_02`：自查 requirement → `save_product_config` → CODE_FUSION_GROUP_ECHO 融合成员回显 |
| 规格稽核（环节4/8） | 配置落地后实时稽核 | `wf_sub_03`（组维度）：`realtime_spec_audit` |
| 资费校准（环节5/8） | 校验计费逻辑、优惠叠加冲突 | `wf_sub_05`（成员分组）：`check_billing_rule`(check_scene=all) |
| 自动测试（环节6/8，含受理验证） | 发起测试→轮询→取结果，报告含受理验证结论 | `wf_sub_04`：`offer_test`/`get_test_scenes`/CODE_POLL_PROGRESS/`get_test_result` + CODE_MAP_FIXED_CASES + 九章节报告 + 报告下载 |
| 上线审批（环节7/8） | 汇总报告、推送审批；**推送即结束（不轮询/不分流）**；审批状态由 `wf_sub_08` 事后查询 | `wf_sub_06`（双轨）：CODE_SUMMARY_APPROVAL → `submit_release_approval` → 推送后即结束，返回 approval_id/status |
| 审批进度查询（辅助） | 查询审批单当前状态（双轨） | `wf_sub_08` + `query_approval_status` |
| 存量产品查询（辅助） | 查询存量/在售销售品（只读） | `wf_sub_09` + CODE_OP_QUERY_OFFER |
| 存量合规扫描（辅助） | 存量销售品上架合规扫描 | `wf_sub_10` + CODE_OP_SHELF_COMPLIANCE（/shelf-compliance） |
| 监控运维（环节8/8） | 上线后持续监控，异常根因推理+建工单闭环 | `wf_sub_07`：`query_product_monitor` → 异常时 CODE_OP_ROOT_CAUSE（/ops/root-cause）+ CODE_OP_CREATE_WO（/ops/work-orders） |

### 3.2 原子能力 → 工具清单（21 个）

#### A. 自研能力接口工具（工具1~6，全部自研模拟实现，兼容 18 个销售品种子）

| # | 工具名 | 用途说明 |
| --- | --- | --- |
| 1 | `query_similar_offer` | 相似度分析：按业务需求描述查询相似销售品（返回相似度最高 1 个 + offerInfo 同构 24 字段），支撑 AI补全 |
| 2 | `realtime_spec_audit` | 实时稽核：按销售品/配置内容实时发起稽核并同步返回结果（原文件上传+异步链路废除） |
| 3 | `offer_test` | 测试发起动作：返回测试流水 `globalId`，测试平台自动执行受理类场景 |
| 4 | `get_test_scenes` | 查询本次测试匹配的测试场景集合（即受理验证覆盖范围） |
| 5 | `get_test_progress` | 轮询测试步骤、是否完成/失败（done/failed/failIndex） |
| 6 | `get_test_result` | 查询逐场景测点比对明细；`orderId`/`offerInstId` 为受理生成的订单号/实例 ID（受理验证依据） |

#### B. 平台复用插件（不自研）

| # | 插件 | 说明 |
| --- | --- | --- |
| 7 | 节点结果存储查询插件 | 执行方案 JSON 与各环节结果保存/查询（save_node_result / query_node_result，后端 `pd_ai_node_results` 表持久化；req_id 单键；node_name=requirement/config/spec/fee/test/report） |

#### C. 自研能力接口工具（工具8~11、13）

| # | 工具名 | 用途说明 |
| --- | --- | --- |
| 8 | `save_product_config` | 配置落地：读执行方案 JSON 写入 CRM 销售品配置，生成 offer_id；不自动重试（防重复写入）；出参含 `script_url` 上线脚本绝对 URL |
| 9 | `check_billing_rule` | 计费规则校验：套餐计费逻辑、优惠叠加规则；出参含 8 项比对明细 `compare_list[]` |
| 10 | `submit_release_approval` | 审批推送：requirement/launch 双轨；后端四环节硬校验（config/spec/fee/test 齐全）；幂等 |
| 11 | `query_product_monitor` | 监控查询：订单量/异常量/计费差错率/告警（确定性模拟生成） |
| 12 | `send_alert` | 异常告警：高/中/低三级，返回告警单号 |
| 13 | `query_approval_status` | 审批进度查询（双轨，approval_id/offer_id 至少一个） |

#### D. V2.0 新增后端适配端点（AppStoreV16Controller，7 个）

| # | 端点（POST） | 用途 | 承载环节 |
| --- | --- | --- | --- |
| 14 | `/ops/root-cause` | 监控异常根因推理 | wf_sub_07（CODE_OP_ROOT_CAUSE） |
| 15 | `/ops/work-orders` | 运维建工单闭环 | wf_sub_07（CODE_OP_CREATE_WO） |
| 16 | `/shelf-compliance` | 存量上架合规扫描 | wf_sub_10（CODE_OP_SHELF_COMPLIANCE） |
| 17 | `/validate-nested` | 嵌套本体校验闸 | wf_sub_01（CODE_OP_VALIDATE_NESTED） |
| 18 | `/explain` | 校验/合规结论可解释说明 | 各子流解释类输出 |
| 19 | `/report/download` | 九章节正式版测试报告下载 | wf_sub_04（CODE_DOWNLOAD_TEST_REPORT） |
| 20 | `/script/download` | CRM/billing 落库 SQL 上线脚本下载 | wf_sub_06（V2.1 已随审批推送即结束移除 CODE_DOWNLOAD_LAUNCH_SCRIPT，端点保留但子流不再调用） |

> **代码节点化**：原技能包脚本子命令（build_plan/extract_record/poll/dispatcher/map_fixed_cases 等）全部重构为工作流 type=6 代码节点，确定性逻辑内嵌于 12 个工作流 JSON。
>
> **模拟结果兼容性要求（V1.6）**：模拟输出不得写死单一套餐，须以《产品信息.txt》全部 **18 个销售品**（5G-A 系列 10 个 + 权益随心选系列 8 个）为种子数据；任一套餐输入时相似度分析、稽核、资费校验、测试（场景/进度/结果/受理验证）、监控均返回与该品资费规则一致的结构化结果（presetValue 取自助品规则值）。后续替换真实实现仅改网关 `BASE_URL`，契约不变。

### 3.3 知识库设计（K1~K5 + 结构化资产）

| 知识分类 | 内容 | 用途 |
| --- | --- | --- |
| K1 产销品业务规范 | 管理办法、配置规范、命名规则、上架流程 | 规格稽核判定、需求解析参照 |
| K2 资费规则库 | 资费模板、叠加优惠约束、计费口径 | 资费校准、风险解读增强 |
| K3 测试规范库 | 测试用例设计规范、受理/计费测试标准、报告模板（九章节） | 测试用例映射、报告生成 |
| K4 存量销售品资料库 | 《产品信息.txt》18 个销售品（拆分单文件，按销售品 ID 精确定位） | AI补全字段参照、相似度结果解读、presetValue 核对基准 |
| K5 FAQ | 高频问答 | 智能体直接问答 |
| 结构化资产 | `ontology-fields.json/.md`（24 字段注册表）、`seed_offer_groups.json`（融合组种子 4 组）、templates 注册表（6 模板 schema）、`存量产品目录_清洗后.json`、`K5存量报文/`（18 份实例化报文） | 模板轨/代码节点确定性读取 |

> 上传/命名规范：`[分类代码]_[文档名]_[版本号]`（如 `K4存量_产品信息900102308_V1.0.md`）；K4 一销售品一文件，**禁止全量读取 18 份**。
>
> 《产品信息.txt》用途边界：仅用于 ① AI补全取值参照；② 需求样例改写测试；③ presetValue 人工核对基准；**不得作为新需求字段来源覆盖用户原始需求**。

---

## 4. 插件/工具契约要点（工具契约权威，业务口径零改动）

### 4.1 插件集公共约定

| 项 | 约定 |
| --- | --- |
| 插件名称 | 产销品加载插件集（13 个工具全部自研模拟实现；V2.0 起由子工作流插件节点调用后端 `/api/v1/appstore/*`，确定性逻辑由内嵌代码节点承载） |
| 接口协议 | http/https（网关 `BASE_URL=http://10.86.13.201:31281`，代理 `/api/v1/appstore/*`） |
| 请求报文 | **V2.7 起全部裸报文**：请求体直接为业务参数 JSON（置于顶层），tcpCont 报文头与 contractRoot 包裹结构整体移除；出参侧兼容解包保留 |
| 超时/重试 | 同步类 60s（稽核类重试 1 次）、异步轮询类 30s；网络类错误重试 1 次；**写操作（save_product_config）不自动重试** |
| 错误码归一 | PARAM_MISSING / HTTP_xxx / NET_ERROR / TIMEOUT / PARSE_ERROR / ONTOLOGY_EMPTY；出参 JSON 原样打印，模型逐字引用不加工 |
| 出参归纳 | 查询类工具（2/4/5/6/10）出参 JSON 原样透出由 LLM 归纳；执行类工具（1/3/7/8/9/11）结束节点逐字引用拼装 |

### 4.2 工具契约汇总表

| 工具 | 方法/路径 | 主要入参 | 主要出参 | 备注 |
| --- | --- | --- | --- | --- |
| 1 query_similar_offer | POST /api/v1/appstore/similar/offer/query | businessDesc(≤5000) | similarOffer(similarOfferId/Name/Score/Desc/offerInfo.fields 24 字段) | 未命中走"无相似产品"分支不中断（E1） |
| 2 realtime_spec_audit | POST /api/v1/appstore/audit/realtime | offer_id, config_json, audit_scene(spec/fee/all) | pass / error_list[] / audit_summary / resultCode | 同步返回；pass=0 中断引导（E8） |
| 3 offer_test | POST /api/v1/appstore/test/offer/start | offerId | globalId(50+14位+10位随机) | 发起动作；resultCode=1 终止（E10） |
| 4 get_test_scenes | POST /api/v1/appstore/test/offer/scenes | globalId | testScenes[]（S_O_TC/S_ADD_CARD/S_U_TC...） | 空场景列表终止（E11） |
| 5 get_test_progress | POST /api/v1/appstore/test/offer/progress | globalId | totalSteps/activeIndex/done/failed/failIndex | 由 CODE_POLL_PROGRESS 轮询 |
| 6 get_test_result | POST /api/v1/appstore/test/offer/result | globalId | testScenes[] / orderId / offerInstId | done=true 后查询；受理凭证 |
| 7 save_product_config | POST /api/v1/appstore/product/config/save | req_id, plan_json(原文透传), operator | offer_id / status / save_result / script_url | 不设确认门禁（V2.2 移除）；幂等；script_url 绝对 URL |
| 8 check_billing_rule | POST /api/v1/appstore/billing/rules/verify | config_json, check_scene(fee/overlay/superposition/all) | pass / risk_list[] / compare_list[]（8 项比对） | pass=0 中断（E9） |
| 9 submit_release_approval | POST /api/v1/appstore/approval/submit | req_id, offer_id, report_url, approval_flow(standard/urgent) | approval_id / status | **后端四环节硬校验**；双轨（requirement/launch）；幂等 |
| 10 query_product_monitor | GET /api/v1/appstore/product/monitor | offer_id, date_range, metric(order/error/fee/all) | order_count / error_count / fee_error_rate / alarm_list[] | 确定性模拟指标 |
| 11 send_alert | POST /api/v1/appstore/alert/send | offer_id, alarm_level(high/middle/low), content | alert_id / status | 联动稽核驳回/监控异常 |
| 13 query_approval_status | GET /api/v1/appstore/approval/status | approval_id / offer_id（至少一个） | approval_id / status / current_node / approver / opinion / submit_time / update_time | 查无审批单（E21） |
| 节点结果存储 | POST /api/v1/appstore/result/save、GET /api/v1/appstore/result/query | req_id / node_name / result_json（保存）；req_id/node_name/latest_only（查询） | code/msg/total/list（取 list[0].result_json 为原文） | req_id 格式校验 5002；≤64KB（5004）；同键覆盖写 |

### 4.3 工具级自测要点（节选；完整 14 项见《细化设计方案》2.4）

globalId 格式（50+19 位）；实时稽核 60s 内同步返回；工具6 orderId/offerInstId 非空；save_product_config 幂等（同 plan_json 不重复写入）；**裸报文契约**（请求体顶层可直接读到业务参数，无 contractRoot/tcpCont 包裹）；必填入参 PARAM_MISSING 提示；18 套餐模拟数据全覆盖（presetValue 与《产品信息.txt》逐项一致）；要素提取质量闸防护（`CODE_VALIDATE_ELEMENTS` quality_gate=FAIL → E31）；`CODE_OP_VALIDATE_NESTED` 端点不可达回退 backend_pending=1。

---

## 5. 工作流设计

### 5.1 智能体调度模式与执行主干时序

> **核心口径**：无独立意图调度主流程，智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别直调 12 个子工作流。确定性纪律（req_id/落库/成败判定/续跑/四环节硬校验）由子流自查链路 + type=6 代码节点 + 后端硬校验兜底，**不依赖 LLM 自觉**。

```
前置：wf_sub_00 提报单就绪止于确认点 → 用户回复【发起需求审批】→ wf_sub_11 发起需求工单审批
  → wf_sub_01 生成《加载方案》止于确认点（未回复确认前不调度 wf_sub_02）
用户回复"确认配置"（携带上轮 req_id）
  ▼
环节3/8 直调 wf_sub_02 → status==SUCCESS/PARTIAL 打印结果+下一步建议 → **止于反馈点**
  ▼（用户回复【下一步】/【继续】）
环节4/8 直调 wf_sub_03 → pass==1 打印结果+下一步建议 → **止于反馈点**
  ▼
环节5/8 直调 wf_sub_05 → pass==1 打印结果+下一步建议 → **止于反馈点**
  ▼
环节6/8 直调 wf_sub_04 → test_passed==通过 打印结果（含受理验证小节）→ 止于汇总点
  ▼
⑦ 智能体汇总打印成功详情 + 提示"是否发起上线审批"
用户回复"发起审批" → wf_sub_06（串行自查 5 类环节结果 → 报告 → submit_release_approval 推送
  → 推送即结束，返回 approval_id/status；不轮询/不分流，审批状态由 wf_sub_08 事后查询）
```

**调度要点（确定性契约权威描述）**：
1. **确认门禁**：`wf_sub_01` 输出方案即止于确认点，未命中确认语义前不调度 `wf_sub_02`；后端 `save_product_config` 不再校验 confirmed/CONFIRMED 标记。`submit_release_approval` 仍硬校验四环节结果齐全（跳步必被拒）。
2. **逐环节步进推进纪律**：02→03→05→04 严格按序，严禁并行/跳过；每环节必须得到用户反馈才调度下一环节，仅失败/异常中断；环节成败由子流/代码节点按出参字段裁决（status/pass/test_passed）。
3. **结果存储**：wf_sub_02~05 各内置 save_node_result（req_id=入参，node_name=config/spec/fee/test），成功即自动落库。
4. **续跑**：`重新执行`按 fail_node 从失败环节续调（STAGE1_CONFIG→wf_sub_02、STAGE2_AUDIT→wf_sub_03、STAGE3_FEE→wf_sub_05、STAGE4_TEST→wf_sub_04），已成功环节按存储回放不重复调用写接口。
5. **消息查询**：审批进度/监控结果由智能体直调 `query_approval_status`、`query_product_monitor`。
6. **两个二次确认点**：需求工单审批（提报单就绪止于确认点，用户回复【发起需求审批】后 `wf_sub_11` 发起）、上线审批（执行主干全成后用户确认发起）。

**结果打印/异常处置/成功汇总三套模板（LLM 输出统一格式）**：
```
【环节N/8·{名称}】✅ 执行成功
- 关键数据：{该环节关键输出（offer_id/save_result、audit_summary、compare_list、测试统计+受理验证
  orderId/offerInstId）}
- 下一步建议：确认后可继续进入【下一环节】——请回复【下一步】/【继续】推进（末环节改为输入"发起审批"）

【环节N/8·{名称}】❌ 执行异常
- 异常原因：{resultCode/resultMsg 或 pass=0 摘要}
- 关键明细：{error_list/risk_list/失败测点/超时信息}
- 整改建议：{依据出参生成的建议}
请选择下一步：① 回复【重新执行】从失败环节继续（已成功不重复）；② 回复【修改执行方案】重新生成并确认

【执行主干全部完成】✅ 共4个环节执行成功：
1. 智能配置：offer_id={...} 全部写入成功； 2. 规格稽核：通过 {audit_summary}；
3. 资费校准：通过，未发现叠加/互斥冲突； 4. 自动测试：场景N个测点M个全部一致；
   受理验证：orderId={...}，offerInstId={...}，各受理场景均通过。
是否发起上线审批？回复【发起审批】将提交审批流；回复【暂不】可稍后继续。
```

### 5.2 12 个子工作流清单（当前实现基线）

> 位于 `场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/`，由 `gen_workflows_v2.py` 确定性生成（禁止手改 JSON）。各子流统一 **req_id 单必填入参**，内部 query_node_result 自查上游 + 结束前 save_node_result 落库；确定性逻辑内嵌 type=6 代码节点。

| 子工作流 | 编码 | 入参 | 关键链路 / 代码节点 |
| --- | --- | --- | --- |
| 需求提报（环节1/8，止于确认点） | `wf_sub_00` | requirement_text, requirement_file | LLM 要素提取 → CODE_RENDER_REQ 渲染提报单+req_id 生成 → save(node=requirement_report) → **止于确认点**（"是否发起【需求工单审批】？"，不直接发起审批） |
| 发起需求审批（环节1/8） | `wf_sub_11` | req_id | 自查 requirement_report → CODE_EXTRACT_RECORD 取原文 → submit_release_approval(approval-type=requirement) → LLM 回执渲染 |
| 需求分析（环节2/8，模板轨，止于确认点） | `wf_sub_01` | req_id | CODE_EXTRACT_RECORD → 相似查询(节点105) → CODE_GET_TEMPLATE(节点106) → 要素提取 LLM → CODE_VALIDATE_ELEMENTS(节点108 质量闸) → CODE_MERGE_NESTED(节点109) → CODE_OP_VALIDATE_NESTED(节点110 本体校验闸 /validate-nested) → CODE_RENDER_TABLE(节点111) → 保存 requirement → **输出方案止于确认点** |
| 智能配置（环节3/8） | `wf_sub_02` | req_id | 自查 requirement → CODE_EXTRACT_RECORD → save_product_config → CODE_FUSION_GROUP_ECHO 融合成员回显 → save(node=config) |
| 规格稽核（环节4/8，组维度） | `wf_sub_03` | req_id | 自查 config → CODE_EXTRACT_RECORD → realtime_spec_audit（error_list 含 group 类目）→ 整改建议 → save(node=spec) |
| 资费校准（环节5/8，成员分组） | `wf_sub_05` | req_id | 自查 config → CODE_EXTRACT_RECORD → check_billing_rule(check_scene=all, member_role 分组) → 风险解读 → save(node=fee) |
| 自动测试（环节6/8，含受理验证） | `wf_sub_04` | req_id | 自查 config → offer_test → get_test_scenes → CODE_POLL_PROGRESS 轮询 → get_test_result → CODE_MAP_FIXED_CASES(节点315：31 条固定用例/九章节报告，受理验证子集) → CODE_DOWNLOAD_TEST_REPORT → save(node=test) |
| 上线审批（环节7/8，双轨+推送即结束） | `wf_sub_06` | req_id | 串行自查 5 类结果 → CODE_SUMMARY_APPROVAL 汇总 → LLM 报告（node=report）→ submit_release_approval(approval-type=launch) → **推送即结束**（不轮询/不分流），返回 approval_id/status；审批状态由 wf_sub_08 查询 |
| 监控运维（环节8/8，异常分支） | `wf_sub_07` | offer_id / date_range | query_product_monitor → 异常 → send_alert → CODE_OP_ROOT_CAUSE(/ops/root-cause) → CODE_OP_CREATE_WO(/ops/work-orders) |
| 审批进度查询（辅助，双轨） | `wf_sub_08` | approval_id / offer_id | query_approval_status → LLM 状态摘要 |
| 存量产品查询（辅助，只读） | `wf_sub_09` | 产品名称/ID | CODE_OP_QUERY_OFFER（内嵌 knowledge/存量产品目录_清洗后.json） |
| 存量合规扫描（辅助） | `wf_sub_10` | 存量范围 | CODE_OP_SHELF_COMPLIANCE(/shelf-compliance) |

### 5.3 关键算法与提示词要点

1. **req_id 生成规则（统一键）**：由 `wf_sub_00` 内嵌代码节点 CODE_RENDER_REQ 以系统时钟生成 `PLAN` + yyyyMMddHHmmss + 3 位随机数（如 `PLAN20260913143025087`），LLM 不参与；会话内沿用最近值。后端硬校验：格式 `PLAN\d{17}`（非法 5002）；requirement 环节同键覆盖写（删除旧记录后重插）。
2. **字段体系与补全策略（3 模块/9 分类/24 字段）**：字段来源仅"原始需求/AI补全"两态；取值链——**原始需求 → 相似产品（最高相似度，标 AI补全）→ 本体默认值（引擎补全，标 AI补全）→ 待补充**，禁止跳级虚构。**仅套餐档位（价格）未提取到时维持"待补充"**（禁推理/禁照搬）；套餐编码恒填"系统待生成"（不计入待补充）；引擎对"待补充"字段按本体默认值推理补全（三类资源→"无"、适用地区→全国、计费周期→自然月等）。pending_fields 判定唯一事实源=引擎反查（value="待补充"），LLM 不自判。
3. **门禁体系**：① 方案确认门禁（智能体语义识别；后端 save_product_config 已移除 CONFIRMED 标记校验）；② 需求工单审批二次确认（`wf_sub_11` 承接）；③ 上线审批四环节硬校验（后端 latestRecord(req_id,"config"/"spec"/"fee"/"test") 全非空）；④ 待补充字段时**不保存执行方案、不产出 req_id**（从源头禁止进入智能配置）。
4. **测试进度轮询（CODE_POLL_PROGRESS）**：间隔 5s、最多 360 次（30 分钟超时）；连续 5 次查询失败终止（E12）；done=true 通过、failed=true 取完整结果供报告定位失败原因（E12）；超时（E13）；分支出参 `done/failed/failIndex/fail_reason`。
5. **31 条固定用例（CODE_MAP_FIXED_CASES 节点315）**：确定性构建 ACC-001~012 / BILL-001~010 / CUST-001~009（维度 ACC|BILL|CUST、等级 P0 拦截/P1 警告/P2 提示），+ 维度汇总 + 整体结论 + 缺陷清单 + 场景覆盖核对 + 被测一致性核对（E26）；LLM 按 K3 模板渲染九章节正式版报告（4.1 受理验证独立成节，4.2 计费验证，4.3 客服验证，等）；31 条清单禁止增删改，结果仅依出参判定。
6. **模型纪律**：LLM 节点固定 `model=qwen3-30b-a3b`、temperature=0.2、top_p=0.5、max_tokens=2048；提示词末尾"仅输出对应出参字段"；生成/汇总/解读场景"逐字引用输入数据，不新增结论"。

### 5.4 配置规范符合性要点（对齐《SitechAI开发平台配置规范.md》）

| 规范条款 | 本方案落点 |
| --- | --- |
| 源码驱动纪律 | 12 子工作流 JSON 全部由 `gen_workflows_v2.py` 生成，禁止手改；改逻辑→改生成器→重新生成+全量校验 |
| 节点 type 全集 | 仅 0/1/2/3/6/9/13；无循环节点（轮询用 CODE_POLL_PROGRESS） |
| 入参两态/引用三层一致 | 常量 vs 引用；`nameValue[1]`/`currValue`=上游 id+出参名 |
| array 出参配 item 树 | ARRAY_ITEM_FIELDS 白名单，无裸数组 |
| 命名 snake_case | JSON 数据传输统一 snake_case；唯一 camelCase 特例 `offerId` |
| 条件分支 sourcePort | 命中=0，否则=-1 |
| nid UUID | `a1b2c3d4-0000-4000-8000-{12位seq}`，seq 按子流分段 |
| type=6 代码节点 | `async def main(args)` + args.params；BASE_URL 占位符；后端不可达一律 backend_pending=1 优雅回退；`_json` 出参名存 JSON 字符串 |
| 页面地址（单面板） | 结束节点输出 `{panel}`（单面板单 url）：`config-workbench.html?offer_id=..&name=..&chatId=<实值>&req_id=<实值>&stage=<N>&view=stage`，JSON 紧凑无空格；00/01 阶段 offer_id 以 req_id 兜底；页面按 stage 动态联动导航与 Tab |

---

## 6. 专项设计要点（原汇编五章精简）

### 6.1 融合商品（多成员）加载（原第 1 章）

- **成员关系数据源**：生产正源=商品目录+融合品关系表；POC 数据源=`knowledge/seed_offer_groups.json`（4 组：900102306/900102307/900102313/900113046）；运行时下发=`similar_offer` 出参内嵌 `offer_group`；**成员构成以组种子为准，禁止模型推理增删成员**（E26 同款纪律）。
- **plan_json 组结构**：`{offer_type ∈ {单品,融合}, main_offer{role,fields}, member_offers[{role,fields}], group_rules, pending_fields[{role,field}]}`；成员 fields 复用 24 字段注册表；**兼容性铁律**——入参不传组结构=单商品模式，行为与旧版完全一致，所有新增字段可缺席。
- **取值链（逐成员独立）**：原始需求（成员级）→ 相似融合品 offer_group → 本体默认值 → 待补充（仅各成员价格类）；价格禁止跨成员照搬。
- **引擎两轮推理**：① 成员内推理（复用 action=reason 逐成员）；② 组级校验（action=group_check：互斥/依赖/退订联动，输出 group_violations[]）。
- **工作流落点**：wf_sub_02 CODE_FUSION_GROUP_ECHO（六列表格/主成员加粗）；wf_sub_03 error_list 新增 `group` 类目；wf_sub_05 compare_list 每项新增 `member_role` 键；wf_sub_04 组场景 S_GROUP_BIND/S_ADDON_SUB + E26 组核对。
- **验收增量 F1~F8**：融合提报六列表格/缺成员价格待补充（role 定位）/成员越界组级 violation/落地 PARTIAL 含成员定位/组级互斥拦截/资费逐成员输出/单商品回归无差异。

### 6.2 需求分析模板驱动重构（原第 2 章）

- **动机**：24 字段是"需求单"口径非"配置报文"口径；6 类模板（personMainPrc/broadBandMainPrc/personAddPrc/familyBasePrc/broadBandOptSpeedPrc/familyAddPrc）结构差异大，需要模板级约束（枚举/show-when/默认值/必填）。
- **模板轨六步（wf_sub_01 现状）**：① 产品识别（LLM 输出 product_type，路由代码定 templateId，防模板名幻觉）→ ② 逐产品相似查询（本地存量目录检索为主动、similar_offer 兜底）→ ③ 取模板（CODE_GET_TEMPLATE）→ ④ LLM 模板化提取（注入模板叶子路径）→ ⑤ merge_nested（JSONPath 对位合并，价格禁照搬）→ ⑤.5 validate_nested 本体校验闸 → ⑥ render_table 层级表格渲染 → ⑦ derive_flat24 单向投影派生 24 字段 plan_json（下游过渡兼容层，禁止反向）。
- **切换口径**：直接切换（不双轨）；`merge_fields` 标 @deprecated；plan_json 双份入库（plan_json_v2 嵌套报文 + plan_json 派生 24 字段）。
- **新异常**：E30 模板路由失败 / E31 提取质量门禁未达标 / E32 schema 文件缺失损坏。

### 6.3 本体推理接入 CPCP 模板轨（原第 3 章）

- **决策前提**：不新造本体、不自拍 TTL——对接 backend-app Java 推理平台（TTL 本体 + OWLAPI/Openllet/RDF4J + SWRL + SHACL + explain/provenance）。
- **落点**：模板轨 merge_nested 之后 → render_table 之前新增确定性校验闸，已由后端 `POST /api/v1/appstore/validate-nested` 实现，`wf_sub_01` 节点110 `CODE_OP_VALIDATE_NESTED` 调用；可解释性走 `POST /api/v1/appstore/explain`（复用 config/explain + config/provenance/{field}）。
- **出参**：`{success, violations[]（path/label/severity/rule_id/desc/suggest）, defaulted[], rule_ids[], trace_id, explain_endpoint}`；violations 含 severity=high → E33 中断；warn → 随 render_table 输出到【风险提示】。
- **纪律边界**：中间推理收敛（不主动倾倒）/逐字引用不加工/explain 不入 render_table/价格类字段不进补全推理分支。

### 6.4 存量产品实例化报文生成（原第 4 章）

- **目标**：为 18 个存量产品（17 active + 1 dup）各生成一份按逻辑模型模板实例化的 JSON 报文（`knowledge/K5存量报文/<offer_id>.json`），作为相似产品检索语料 + AI补全取值来源 + presetValue 人工核对基准。
- **流程**：K4 存量 md → ④ LLM 提取（模板 schema 注入）→ ⑤ merge_nested `--mode legacy`（source 标注"存量提取"、价格正常提取不受禁照搬约束）→ ⑥ render_table 渲染校验 → 入库。
- **三道闸**：规则闸（枚举/数值/必填缺失率/价格一致性）、渲染闸（结构合法性）、人工闸（每模板抽 1 品对照 K4 核对）。
- **分批**：P0 试产（900113046 familyBasePrc）→ P1（900102308 personMainPrc）→ P2（900117020 personAddPrc）→ P3 其余 14 个批量。

### 6.5 存量产品数据清洗规则（原第 5 章）

- **产物**：`knowledge/存量产品目录_清洗后.json`（机器化输出）。
- **核心规则**：C1~C4 字符/格式清洗；C5 命名归一（product_type 三态）；C6 系列归集；C7 档位抽取；C8 融合成员识别；C9 模板映射；C10 正文版本识别（22号文/6号文）；**C11~C13 同名去重**（900113046 22号文更全→active，900102306 6号文→dup）；C14 卫星短信≠套餐内短信；C15 价格类字段禁照搬。
- **可审计纪律**：任何清洗动作都有明确规则依据，可重复执行。

---

## 7. 部署、命名约定与异常处理

### 7.1 部署清单（逐项核对）

| # | 部署项 | 配置值 |
| --- | --- | --- |
| 1 | 工作流 JSON | 12 个子工作流（`工作流配置/智能体工作流集V1.6/`），`gen_workflows_v2.py` 生成，ALL_OK |
| 2 | 智能体提示词 | 3.2 意图→工作流映射表 + 确认语义识别 + 步进式纪律 + 超范围拒答 |
| 3 | 子工作流节点 | 代码节点（CODE_*）与插件端点逐条核对（wf_sub_01 节点106~113、wf_sub_04 节点315） |
| 4 | knowledge/ 知识库 | K1~K5 + K5存量报文 + ontology-fields + seed_offer_groups + 存量产品目录_清洗后 |
| 5 | 生成器 | `gen_workflows_v2.py`（代码节点内嵌 CODE_* 逻辑，固定用例与九章节模板内嵌一致） |
| 6 | 网关地址 | `BASE_URL=http://10.86.13.201:31281`（替换真实实现仅改此值） |
| 7 | 后端依赖 | 13 工具模拟服务 + 7 适配端点（AppStoreV16Controller）+ NodeResultService（pd_ai_node_results 表） |
| 8 | 模型纪律 | 温度 0.2；出参逐字引用不加工 |
| 9 | 环境 | 平台/Agent 运行时加载 12 JSON + knowledge/ + 后端服务 |

### 7.2 变量命名约定

| 风格 | 适用 | 示例 |
| --- | --- | --- |
| 小写下划线 | 节点变量/入出参键/JSON 键 | `req_id`（统一键）、`plan_json`、`config_json`、`pending_fields`、`fail_node`、`result_json`、`test_report` |
| 驼峰 | 接口原始出参字段（不改名；特例 `offerId`） | `globalId`、`testScenes`、`orderId`、`offerInstId`、`presetValue`、`testValue` |
| req_id + node_name | 节点结果存储寻址 | req_id=`PLAN20260913143025087`、node_name=`spec` |
| 枚举 | 字段来源（原始需求/AI补全）；稽核场景（spec/fee/all）；告警级别（high/middle/low）；测试场景编码（S_O_TC/S_ADD_CARD/S_U_TC/S_GROUP_BIND/S_ADDON_SUB）；测点编码（P_EFF_DATE/P_EXP_DATE/P_STATUS/P_MAIN_PROD/P_RELY_REL/P_MUTEX_REL/P_ORD_CNT/P_OFFER_NAME/P_OFFER_TYPE/P_PAY_MODE） | — |
| 失败环节编码 | STAGE1_CONFIG→wf_sub_02 / STAGE2_AUDIT→wf_sub_03 / STAGE3_FEE→wf_sub_05 / STAGE4_TEST→wf_sub_04 | — |

### 7.3 异常处理矩阵（E1~E31，E33/E34）

| # | 异常场景 | 触发点 | 系统行为 / 恢复方式 |
| --- | --- | --- | --- |
| E1 | 相似度分析失败 | wf_sub_01（节点105） | 跳过相似产品，仅用 K4 补全（自动降级） |
| E2 | 输出来源枚举违规 | wf_sub_01 要素提取 | 程序化校验失败→重新生成（最多2次），后转人工 |
| E3 | 未确认即触发配置 | 智能体语义识别 | 拒绝进入执行主干，引导回复"确认配置" |
| E5 | 自查无执行方案 | wf_sub_02 自查 | CODE_EXTRACT_RECORD total==0 终止，引导重走需求分析 |
| E6 | 配置落地 FAIL | wf_sub_02 | 主干中断→异常模板④，引导重新执行/修改执行方案 |
| E7 | 实时稽核超时 | wf_sub_03 | 重试 1 次仍超时→中断，保留请求报文 |
| E8 | 稽核 pass=0 | wf_sub_03 | 中断（可联动 send_alert high），打印 error_list 明细 |
| E9 | 资费校验 pass=0 | wf_sub_05 | 中断，打印风险清单，引导修改执行方案 |
| E10 | 测试发起失败 resultCode=1 | wf_sub_04 | 中断 |
| E11 | 测试场景为空 | wf_sub_04 | 中断，引导修改执行方案后重测 |
| E12 | 轮询连续 5 次失败 / failed=true | wf_sub_04 CODE_POLL_PROGRESS | 中断，保留 globalId 人工续查 |
| E13 | 测试超时 30 分钟 | wf_sub_04 CODE_POLL_PROGRESS | 中断，凭 globalId 人工续查 |
| E14 | orderId/offerInstId 为空 | wf_sub_04 报告生成 | 报告标注"未获取到受理凭证，需人工核实"（不中断） |
| E15 | 未经确认发起审批 | wf_sub_06 触发门禁/后端硬校验 | 四环节结果不齐拒绝推送 |
| E16 | 审批推送失败 | wf_sub_06 | 重试 1 次后终止，修复后重推（幂等） |
| E17 | 监控接口失败 | wf_sub_07 | 终止本轮，下周期自动重试 |
| E18 | 续跑参数非法 | fail_node 非法/req_id 查无 | 按首次执行处理 |
| E19 | 重新执行写接口重复防护 | 各环节自查前置 | 已存成功记录→跳过写接口直接回放 |
| E20 | 主干中段接口网络错误 | wf_sub_03/05/04 | 中断→引导稍后重新执行 |
| E21 | 审批进度查询无审批单 | wf_sub_08 | 提示先发起审批 |
| E22 | 查询缺少必填参数 | wf_sub_07/08 | 先追问补齐，不发起调用 |
| E23 | 环节结果存储写入失败 | 各环节 save_node_result | 打印结果不受影响，续跑退化为全量重跑 |
| E24 | 审批未通过即要求确认上线 | wf_sub_06/07 | 拒绝生成监控运维方案 |
| E26 | 被测一致性核对不一致（e26=0） | wf_sub_04 节点315 | 八章结论从严标注并提示人工核实，不输出通过性明细 |
| E30 | 模板路由失败 | wf_sub_01 产品识别 | product_type ∉ 枚举→中断询问 |
| E31 | 要素提取质量闸 FAIL | wf_sub_01 节点108 | quality_gate=FAIL 输出待补路径，禁止进入合并/组装 |
| E32 | schema 文件缺失/损坏 | wf_sub_01 取模板 | 中断 |
| E33 | 本体校验 high 违规 | wf_sub_01 节点110 | violations severity=high→中断引导 |
| E34 | explain/provenance 取数失败 | explain 端点 | 提示型，不中断 |

### 7.4 知识库文档清单与维护（详见《细化设计方案》第 4 章）

- 文档清单：K1（管理办法/配置规范/命名规则）、K2（资费模板手册/叠加优惠约束说明）、K3（测试用例设计规范/测试报告模板）、K4（产品信息.txt 拆分 18 单文件）、K5（FAQ）。
- 维护机制：新规范发布/新资费政策由知识运营按命名规范替换；同名先放新后删旧；更新后执行 3 组固定问答回归；下线销售品资料保留 6 个月后归档。

---

## 8. 开发实施计划（约 22 个工作日）

| 阶段 | 工作项 | 建议工期 |
| --- | --- | --- |
| 1 基础搭建 | 创建助手/知识分类；确认 knowledge/ 知识资产、`/api/v1/appstore/*` 适配端点与网关 BASE_URL；确认节点结果存储查询插件可用 | 3天 |
| 2 后端适配端点开发 | 既有 13 工具 + 7 新适配端点逐一联调（裸报文契约、异步测试轮询、实时稽核同步返回） | 5天 |
| 3 知识库建设 | 规范文档采编、《产品信息.txt》切片入库（knowledge/）、FAQ 编制 | 3天 |
| 4 工作流编排 | `gen_workflows_v2.py` 生成 12 个子工作流 JSON 并导入调试；代码节点、req_id 自查链路联调 | 6天 |
| 5 智能体集成 | 提示词、插件、工作流、知识库装配；模型参数调优 | 2天 |
| 6 验证与优化 | 全流程验证：方案确认门禁、需求审批二次确认、步进式推进、异常引导与续跑、审批门禁、消息查询、受理验证结论完整性；提示词迭代 | 4天 |

---

## 9. 测试与验收

### 9.1 分层测试

1. **插件级**：逐工具连通性/入参提取/出参归纳（裸报文、实时稽核同步、存储读写一致、globalId 传递）。
2. **工作流级**：正向用例（提报→审批→方案→确认→逐环节推进→审批）；反向用例（**未确认不配置、提报单未确认不发起审批、执行方案未确认不调度 wf_sub_02、跳步审批被拒**）；步进式推进用例（每环节止于反馈点）；异常中断与续跑用例；硬校验用例（四环节不全拒审批）；消息查询用例；分支用例。
3. **智能体级**：多轮对话体验、确认交互、引导问题命中、FAQ 准确率。
4. **专项回归**（见第 6 章）：融合 F1~F8、模板轨六步、本体校验闸 E33、存量报文三道闸、清洗 C1~C15 可重复执行。

### 9.2 验收标准（节选）

| 指标 | 目标 |
| --- | --- |
| 端到端流程贯通率 | 100% |
| 确认门禁有效性 | 未确认时配置落地/审批推送触发率 = 0%；需求审批/执行方案未确认时发起率、调度率 = 0%；**执行主干未获用户反馈确认不推进下一环节率 = 0%** |
| 步进式推进纪律 | 每环节 100% 输出结果 + 下一步建议并止于反馈点；无跳步/并行/擅自推进 |
| 异常处置完整性 | 100% 输出异常环节/原因/建议 + 重新执行/修改执行方案引导 |
| 续跑正确性 | 已成功环节不重复执行，续跑起点与失败环节一致 |
| 消息查询可用性 | 审批进度/监控结果命中率 100% |
| 需求要素映射准确率 | ≥95%（字段名与 24 字段注册表一致） |
| AI补全字段标记正确率 | 100%（备注仅两态） |
| 待补充规则符合率 | 100%（仅价格类待补充；套餐编码恒"系统待生成"） |
| 受理验证结论完整性 | 测试报告 100% 含 orderId/offerInstId 及逐场景结论 |
| 全流程耗时 | 较人工缩短 ≥60% |

### 9.3 端到端演示剧本

- 文件：《产销品加载AI应用-端到端演示剧本.md》；按幕走通 8 环节（幕1 提报止于确认点 / 幕1-2 审批前置【用户确认后触发】/ 幕2 确认门禁 / 幕3 执行主干步进推进 / 幕3A 异常中断与引导 / 幕3B 成功详情与审批确认 / 幕4 稽核+资费 / 幕5 自动测试（含受理验证）/ 幕6-7 上线审批 / 幕8 监控运维）。
- 附"反向分支速查表"，与验收指标直接对应（含"未确认不配置"、"需求审批未确认不发起"等）。

---

## 10. 上线与运维

1. **发布**：智能体发布到小思页面；工作流/插件保持版本可回退。
2. **监控**：每日定时（或运维对话触发）运行 `wf_sub_07`；用户可随时消息触发即时查询。
3. **审批跟踪**：需求工单审批（wf_sub_11 用户确认后发起）与上线审批（launch 轨）推送后可随时消息查询；驳回时同步驳回原因并引导修改后重新发起。
4. **迭代机制**：知识库持续更新（新规范/新资费）；提示词与阈值按误判情况调优；后端端点与工作流代码节点按系统升级同步维护。
5. **运维报表**：每周汇总执行量、拦截量、测试通过率，评估数字员工效能。

---

## 11. 风险与对策

| 风险 | 对策 |
| --- | --- |
| 接口不稳定/字段变更 | 插件层隔离；出参加必填校验；替换真实实现契约不变 |
| 实时稽核慢/超时 | 60s 超时重试 1 次；超时转人工并保留请求报文 |
| 自动测试长时间未完成 | 轮询设 30 分钟上限，超时保留 globalId |
| 大模型解析幻觉 | 知识库增强 + 温度 0.2 + 结构化输出 + 来源标记 + 实时稽核兜底 |
| 配置落地与方案不一致 | 智能配置直读存储 JSON 原样透传，禁二次加工 |
| 用户未确认即触发生产写入 | 方案就绪止于确认点；确认语义识别命中才进入执行主干（后端确认门禁已移除） |
| 续跑重复执行写操作 | 各子流以 req_id+node_name 落库，续跑自查回放已成功环节 |
| 未经确认发起审批 | 需求工单审批二次确认（提报单止于确认点，经 wf_sub_11 发起）；上线审批后端四环节硬校验 |
| 流程环节被跳过 | 智能体步进式推进纪律 + submit_release_approval 四环节硬校验双保险 |
| 资费漏洞漏检 | 知识库持续运营 + check_scene=all 全量校验 |
| 融合组成员关系错误 | 成员构成以 offer_group/组种子为准，禁止模型推理增删（E26 纪律） |
| 存量报文/清洗质量 | 三道闸校验 + C1~C15 规则可审计 + 人工抽检 |

---

## 附：快速实施 Checklist（当前基线 V3.8）

### A. 工作流 JSON 路线（当前基线，已就绪项以 [x] 标注）
- [x] 知识资产迁至 `knowledge/`：K1~K5、ontology-fields、seed_offer_groups、templates 注册表、存量产品目录_清洗后、K5 存量报文
- [x] 后端 `/api/v1/appstore/*` 适配端点就绪（13 工具 + 7 新端点；BASE_URL=http://10.86.13.201:31281）
- [x] `gen_workflows_v2.py` 生成 12 个子工作流 JSON（`wf_sub_00`~`wf_sub_11`）并导入
- [x] 确定性逻辑内嵌 type=6 代码节点（CODE_*）取代技能包脚本子命令
- [x] 智能体按 3.2 提示词语义识别直调（职责分层：只做语义识别/业务理解/结果表达）
- [x] 环节覆盖：需求提报（止于确认点）+ wf_sub_11 发起需求审批、需求分析（模板轨）、智能配置（融合回显）、规格稽核（组维度）、资费校准（成员分组）、自动测试（31 条固定用例+九章节报告）、上线审批（双轨+推送即结束，审批状态由 wf_sub_08 查询）、监控运维（根因闭环）、审批进度/存量查询/存量合规辅助子流
- [x] 主链路端点实跑验证（2026-09-19）：result/save、result/query、audit/realtime、product/config/save、billing/rules/verify、test/offer/start、test/offer/result、approval/submit、product/monitor、similar/offer/query live 生效
- [ ] 14 个既有契约端点 + 7 个适配端点逐一连通（含错误码验证）；仍占位：report/download、script/download（backend_pending=1 回退）、/ops/root-cause、/ops/work-orders、/shelf-compliance、/validate-nested、/explain 待逐项确认
- [ ] 端到端联调：正向全流程 + 反向用例（未确认不配置、需求审批未确认不发起、跳步审批被拒、逐环节推进止于反馈点、稽核驳回中断引导、续跑不重复写）+ 18 销售品兼容回归
- [ ] 全流程各子流处理完成后均输出【下一步建议】引导（V3.6）
- [ ] 按《端到端演示剧本》完成全流程彩排（含反向分支）
- [ ] 发布上线并接入监控运维

### B. 模拟数据 18 销售品兼容核对（逐品勾选）

| # | 销售品 ID | 名称 | 系列 | 兼容 | 端到端 |
| --- | --- | --- | --- | --- | --- |
| 1 | 900102308 | 5G-A套餐199元 | 5G-A | [ ] | [ ] |
| 2 | 900113043 | 5G-A单品239元 | 5G-A | [ ] | [ ] |
| 3 | 900113046 | 5G-A融合199元 | 5G-A | [ ] | [ ] |
| 4 | 900102307 | 5G-A融合299元 | 5G-A | [ ] | [ ] |
| 5 | 900102313 | 5G-A融合399元 | 5G-A | [ ] | [ ] |
| 6 | 900113044 | 5G-A融合239元 | 5G-A | [ ] | [ ] |
| 7 | 900102310 | 5G-A单品299元 | 5G-A | [ ] | [ ] |
| 8 | 900102312 | 5G-A单品399元 | 5G-A | [ ] | [ ] |
| 9 | 900113045 | 5G-A单品199元 | 5G-A | [ ] | [ ] |
| 10 | 900102306 | 5G-A融合199元（dup） | 5G-A | [ ] | [ ] |
| 11~18 | 900117020~900117027 | 权益随心选（娱乐/生活/出行/商超 × 19.9/29.9 元） | 权益随心选 | 逐品 [ ] | 逐品 [ ] |

> 核对要点：① 工具1 命中对应销售品；② 工具6 presetValue 与《产品信息.txt》规则值一致；③ 工具2/8 按该品规则判定；④ 权益随心选重点核对权益类字段 AI补全正确、无"待补充"误标。
