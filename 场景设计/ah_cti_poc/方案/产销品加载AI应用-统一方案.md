# 产销品加载 AI 应用 · 统一方案

> 本方案为《产销品加载 AI 应用》**统一方案**，整合精简自原三份文档：主方案《产销品加载AI应用开发方案.md》（原整合去向：第 1~3 章、第 5 章、第 8~11 章及附录）、专项设计《-专项设计方案汇编.md》（原第 6 章：融合商品加载/模板驱动重构/本体推理/存量实例化/数据清洗）、细化设计《-细化设计方案.md》（原第 4 章工具契约、第 5 章工作流细化、第 7 章部署/命名/异常矩阵/知识库）。原三份文档已删除，本方案即权威唯一口径（业务口径/字段体系/子流编号/判定字段/fail_node 映射/req_id/落库 node_name/审批硬校验契约保持不变）。
>
> - **平台**：AI应用开发（九思大模型 · 低代码智能体平台）
> - **场景**：安徽电信 CPCP 产销品域 · 产销品加载智能体（必选场景）
> - **当前基线**：V3.13（2026-10-08）——14 个工作流 JSON（`wf_sub_00`~`wf_sub_13`）+ 智能体（LLM）语义识别直调 + `knowledge/` 知识库 + 后端 `/api/v1/appstore/*` 适配端点 + **现场接口适配层（9 接口配置化封装，4.5 章）** + **需求文档（Excel）驱动支持：父/子键一品多品 + 品间关联关系复用融合组机制；文档解析由智能体层挂载 skill 完成（工作流保持文本驱动，不接收文件），（6.6 章，V3.10 新增解析能力、V3.11 改为 skill 承载）** + **页面自动巡检（环节4/6 结果与审批进度由配置工作台页面自动巡检获取，智能体不直调 wf_sub_12/13/08，V3.13）** + **智能体提示词业务场景编排强化（2.2 章：一次一调/发起即止/巡检自动取结果/键位门禁，V3.13）**。原始版本履历（V1.2~V3.8）见原主方案（已删除），本方案不再逐条罗列，仅在必要时备注关键口径沿革。
> - **相关文档**：《工作流JSON开发规范》（`工作流配置/`）、《知识文档来源与采编指南》《端到端演示剧本》《本体应用方案》、`cpcp_product_ontology.ttl`、《SitechAI开发平台配置规范》。

---

## 1. 方案总览

### 1.1 建设目标

以产销品加载智能体替代产销品上架全流程人工操作，实现：

**需求提报（环节1/8）→ 需求分析（环节2/8，五列模块表格《加载方案》）→ 需求工单审批（用户确认后发起）→ 用户确认 →【智能配置（环节3/8）→ 规格稽核（环节4/8）→ 资费校准（环节5/8）→ 自动测试（环节6/8，含受理验证子集）】**逐环节步进推进（每环节处理完输出结果 + 下一步建议，用户反馈确认后才进入下一环节）**→ 上线审批（环节7/8，用户二次确认后发起）→ 监控运维（环节8/8，确认上线后生成监控运维方案）** 的端到端闭环。

**关键口径（沿革备注）**：
- **8 环节统一编号**：1 需求提报 / 2 需求分析 / 3 智能配置 / 4 规格稽核 / 5 资费校准 / 6 自动测试（**受理验证并入环节6，不独立成节**）/ 7 上线审批 / 8 监控运维；标题头统一 `【环节N/8·全名】`。
- **步进式人机协同（V3.6 起替代 V3.2 自动串行口径）**：用户确认配置后**逐环节调度**（`wf_sub_02`→`wf_sub_03`→`wf_sub_05`→`wf_sub_04`），每一环节处理成功后先输出该环节结果 + 下一步建议，随即暂停等待用户反馈，用户确认后才调度下一环节；仅环节失败/异常时提前中断。
- **需求工单审批/上线审批均二次确认**：提报单就绪即止于确认点（V3.3 起 `wf_sub_00` 不再自动发起审批，由新增子流 `wf_sub_11` 在用户明确回复【发起需求审批】后发起）；上线审批须执行主干全部成功后由用户确认发起，后端四环节硬校验兜底。
- **需求文档（Excel）驱动与一品多品（V3.10 新增解析能力 / V3.11 改为 skill 承载）**：环节1 需求提报支持上传结构化《需求文档》（.xlsx，如《新增四档5G畅享融合套餐销售品.xlsx》）。**工作流 start 节点无法接收文件/附件，故文档解析不由工作流承担**——当用户上传 .xlsx 时，**智能体（LLM）挂载 skill 解析文档**，将多品结构化需求转为**结构化文本**，再作为 `requirement_text` 直调 `wf_sub_00`（文本轨 LLM 抽取多品）。一份需求文档可含多个新增销售品（4 档套餐）与品间关联关系，采用**父/子键**（父 `req_id` 承载需求 + 子 `offer_seq` 区分各品）在同一 req_id 轨道内并行加载，**品间关联关系复用融合组机制（group_rules 组内依赖）**。详见 6.6 章。

### 1.2 全流程 8 环节总表（环节编号全文以此为准）

| 环节 | 全名 | 承载子流 | 关键动作 | 判定字段 |
| --- | --- | --- | --- | --- |
| 1 | 需求提报 + 需求工单审批 | `wf_sub_00`（止于确认点）+ `wf_sub_11`（发起需求审批）※ .xlsx 上传时解析由智能体 skill 前置完成 | 用户上传 .xlsx → **智能体挂载 skill 解析为多品结构化文本**（工作流不接收文件）→ 作为 `requirement_text` 直调 `wf_sub_00`：LLM 要素提取（多品）→ CODE_RENDER_REQ 渲染提报单 →**止于确认点**；用户回复【发起需求审批】后由 `wf_sub_11` 发起 `submit_release_approval`(approval-type=requirement) | 提报单就绪 / 审批单号 |
| 2 | 需求分析 | `wf_sub_01`（模板轨） | 要素提取 → validate_elements 质量门禁 → merge_nested → validate_nested 本体校验闸 → render_table → 保存 requirement → **输出方案止于确认点** | plan_json / plan_md |
| 3 | 智能配置 | `wf_sub_02` | 自查 requirement → save_product_config → CODE_FUSION_GROUP_ECHO 融合成员回显 | status==SUCCESS/PARTIAL |
| 4 | 规格稽核 | `wf_sub_03`（4a 发起）+ 页面自动巡检（4b 结果取回，后端查询承载） | 上传稽核Excel → 逐品异步通知+落库（秒级返回）→ **页面自动巡检触发 4b**：SQL 轮询 → 取结果，归一 error_list（含 group 类目） | pass==1（`spec` 落库后） |
| 5 | 资费校准 | `wf_sub_05`（成员分组） | 自查 config → check_billing_rule（check_scene=all，member_role 分组） | pass==1 |
| 6 | 自动测试（含受理验证） | `wf_sub_04`（6a 发起）+ 页面自动巡检（6b 结果取回，后端查询承载） | 自查 config → 6a：offer_test 发起 + get_test_scenes 取场景（秒级返回）；6b：页面巡检触发后端短窗口轮询 → get_test_result → CODE_MAP_FIXED_CASES（31 条固定用例）→ 九章节报告 → 报告下载；**受理验证为其子集**（逐场景 orderId/offerInstId 结论随报告输出，不独立成节） | test_passed==通过（`test` 落库后） |
| 7 | 上线审批 | `wf_sub_06`（双轨） | CODE_SUMMARY_APPROVAL 汇总 → submit_release_approval(approval-type=launch) → **推送即结束**（不轮询/不分流）；审批状态由页面自动巡检呈现 | approval_id / status |
| 8 | 监控运维 | `wf_sub_07` | query_product_monitor → 异常分支 CODE_OP_ROOT_CAUSE 根因推理 + CODE_OP_CREATE_WO 建工单闭环 | 指标 / 工单号 |

> 辅助子流（不占环节编号）：`wf_sub_08` 审批进度查询（双轨，页面自动巡检触发）、`wf_sub_09` 存量产品查询（只读）、`wf_sub_10` 存量合规扫描。

### 1.3 总体架构（平台能力映射）

| 平台能力 | 在本场景中的角色 |
| --- | --- |
| 智能体（助手） | 产销品加载智能体统一入口（对话式 + 流程式调度中枢）；由智能体（LLM）按 3.2 提示词【意图→工作流映射表】**语义识别直调** 14 个子工作流（逐环节步进推进；需求工单审批须用户明确确认后才发起）；**上传 .xlsx 时由智能体挂载 skill 解析文档为结构化文本**（工作流不接收文件）；环节结果查询（稽核/测试/审批进度）由**配置工作台页面自动巡检**（前端轮询 `result/query` + 后端查询端点），智能体不再直调查询子流，仅承接**监控结果查询**与**异常处置引导**（重新执行/修改执行方案） |
| 工作流 | 承载 8 个业务环节的 14 个子工作流 JSON（`wf_sub_00`~`wf_sub_13`；req_id 单必填入参自查链路，无意图调度主流程）；子流内部含环节结果存储节点（save_node_result） |
| 插件/工具 | 封装 HTTP API 能力接口（相似度分析/智能稽核/智能测试等 13 工具 + 8 个后端适配端点 + 节点结果存储查询插件，共 22 个），全部自研模拟实现（兼容《产品信息.txt》18 个销售品） |
| 知识库 | `knowledge/` 目录：K1 业务规范 / K2 资费规则 / K3 测试规范 / K4 存量销售品资料 / K5 FAQ + `ontology-fields.json`、`seed_offer_groups.json`、templates 注册表、存量产品目录、K5 存量报文等结构化资产 |
| 本体库（TTL） | 产销品域结构化知识（`cpcp_product_ontology.ttl`），支撑需求校验、关系推理、配置生成约束、术语对齐；模板轨经 `/validate-nested` 网关端点接入 Java 推理平台 |
| 大模型节点 | 需求解析（要素翻译、执行方案生成）、稽核整改建议、测试报告生成（九章节，含受理验证结论）、资费风险解读、上线报告生成 |
| 选择器节点 | 稽核通过/驳回、资费通过/驳回、测试通过/失败等分支控制（子流内部）；确认门禁由智能体语义识别 + 后端硬校验兜底 |

### 1.4 实现方式（V2.0 工作流重塑，当前基线）

| 原平台构件（第 2~6 章业务口径描述） | V2.0 工作流 JSON 实现（当前基线） |
| --- | --- |
| 智能体常驻提示词 | 智能体（LLM）按 3.2 提示词【意图→工作流映射表】做**语义识别并直调** 14 个子流；上传 .xlsx 时挂载 skill 解析文档为结构化文本（工作流不接收文件）；无独立意图调度主流程 |
| 子工作流 `wf_sub_01~08` | 14 个工作流 JSON：`wf_sub_00`~`wf_sub_13`（`gen_workflows.py` 确定性生成，位于 `工作流配置/智能体工作流集V1.6/`） |
| 插件工具 1~14 契约 | 后端 AppStoreV16Controller `/api/v1/appstore/*` 适配端点（13 工具 + 8 个 V2.0 新增端点，网关 `BASE_URL=http://10.86.13.201:31281`） |
| 代码节点 | 确定性逻辑内嵌为工作流 **type=6 代码节点**（CODE_RENDER_REQ / CODE_GET_TEMPLATE / CODE_VALIDATE_ELEMENTS / CODE_MERGE_NESTED / CODE_OP_VALIDATE_NESTED / CODE_RENDER_TABLE / CODE_EXTRACT_RECORD / CODE_POLL_PROGRESS / CODE_MAP_FIXED_CASES / CODE_FUSION_GROUP_ECHO / CODE_OP_ROOT_CAUSE / CODE_OP_CREATE_WO / CODE_OP_SHELF_COMPLIANCE / CODE_SUMMARY_APPROVAL / CODE_DOWNLOAD_* 等）（需求文档解析不在工作流内，由智能体 skill 承担） |
| 平台知识库 K1~K5 + 向量召回 | `knowledge/` 目录 + 确定性代码节点/后端服务按需读取（K4 按销售品 ID 单文件精确定位，禁止全量读取） |
| 平台门禁 | 后端硬校验保留（req_id 格式 5002 / 四环节门禁 / 幂等）；**确认门禁已移除**——方案确认与审批确认由智能体语义识别保证（提示词【限制】） |

**执行入口**：用户对话 → 智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别（含实体提取 req_id/offer_id/approval_id）→ 直调对应子流 → 子流内经代码节点 + 后端适配端点完成业务处理 → 结束节点输出结果。大报文（plan_json/config_json/fields/report）一律经节点结果存储（req_id+node_name）或后端下载端点传递，不经模型上下文中转。

**重塑核心原则**：业务逻辑零改动（3 模块/9 分类/24 字段、待补充判定、本体推理引擎、req_id 统一键、异常矩阵原样保留）；接口契约零改动（替换真实实现仅改网关 BASE_URL）；确定性逻辑（merge_nested/render_table/validate_elements/get_template/render_requirement_report/map_fixed_cases/extract_record/poll_progress 等）由技能包脚本子命令迁移为工作流 type=6 代码节点，同一份 Python 逻辑整体内嵌、行为可审计。

---

## 2. 智能体设计（产销品加载智能体）

### 2.1 基本信息

| 项 | 值 |
| --- | --- |
| 助手代码 / 名称 | `cpcp_product_worker` / 产销品加载智能体 |
| 功能介绍 | 覆盖需求提报→需求分析（五列模块表格《加载方案》）→智能配置→规格稽核→资费校准→自动测试（含受理验证）→上线审批→监控运维的 **8 环节**全流程（**逐环节步进推进，非一次贯通**） |
| 模型配置 | 温度 0.2（严谨输出）、top_p 0.5、max_tokens 2048、多轮对话 20 轮；LLM 节点统一 `qwen3-30b-a3b` |
| 开场白 | "您好，我是产销品加载智能体，可协助您完成销售品从需求提报、加载方案生成、确认后智能配置、稽核校准、自动测试到上线审批、监控运维的全流程。请上传需求文档或直接描述需求，我将为您生成《加载方案》。" |
| 引导问题 | 1) 我要上新四档 5G 畅享套餐，请先提报需求再生成加载方案 2) 确认配置刚才的产销品加载方案 3) 发起上线审批 4) 查询销售品 900102308 的运行监控结果 5) 重新执行失败的环节 |
| 答案为空提示 | "抱歉，我仅支持产销品加载相关业务，请更换问题或联系管理员。" |

### 2.2 提示词【角色 + 技能 + 意图映射表 + 限制】（职责分层）

> **职责分层口径**：智能体（LLM）**只负责三件事**——① 语义识别（按【意图→工作流映射表】直调子工作流）；② 业务理解/补全（需求分析提取要素、补全字段、生成《加载方案》）；③ 结果表达（按固定模板输出环节结果、下一步建议、异常引导、汇总块）。**确定性技术细节不进提示词**：req_id（需求单号）生成与透传、环节结果落库（node_name 结果键）、环节成败判定（status/pass/test_passed 成功态字段）、fail_node（失败环节编码）续跑、审批四环节硬校验——由子流自查链路（query_node_result 结果查询）+ type=6 代码节点 + 后端硬校验兜底（见第 5 章）。全流程 8 环节口径、步进式人机协同。
>
> **业务场景编排强化口径（V3.13 提示词优化，页面自动巡检）**：本轮优化聚焦"环节推进由页面自动巡检驱动"，将编排规则显式化为四个可直接执行的约束——① **一次一调**：一条用户消息只直调一个子流，同轮不重复直调；② **发起即止**：凡"发起"类子流（wf_sub_00/03/04/11）返回后止于反馈点，下一环节由页面自动巡检推进；③ **巡检自动取结果**：环节4/8 稽核与环节6/8 测试的**结果查询（4b/6b）由配置工作台页面自动巡检**（前端轮询 `result/query` 取 `spec`/`test` 落库结果）驱动，**智能体不再直调 `wf_sub_12`（稽核查询）/`wf_sub_13`（测试查询）/`wf_sub_08`（审批查询）**，也不引导用户回复查询类关键词；④ **键位门禁**：`spec`（环节4 巡检落库 pass==1）与 `test`（环节6 巡检落库 test_passed==通过）落库是推进环节5/环节7 的硬前置，主干顺序 `02→03（→页面巡检落spec）→05→04（→页面巡检落test）`；前端按需调用后端 `/audit/sql/query` 与 `/test/offer/progress`·`/test/offer/result` 完成轮询，无需 LLM 介入。**首调守卫保持不变**：会话开头、上下文中**尚无 `req_id`** 时，首条需求消息**必须先调 `wf_sub_00`（需求提报）**，**严禁**直接调 `wf_sub_01`（需求分析）。

```
【角色】
你是安徽电信产销品加载智能体，负责销售品从需求到上线的端到端加载。你只做三件事：①识别意图、直调子工作流；②按返回结果表达；③异常引导。落地动作全由子工作流完成；每次只调一个子流；先判定、再直调。

【子工作流】wf_sub_00 提报 / 01 分析 / 02 配置 / 03 稽核发起 / 04 测试发起 / 05 资费校准 / 06 上线审批 / 07 监控运维 / 09 存量查询 / 10 存量合规 / 11 发起需求审批 / 14 发起优化工单。
【关键键】req_id 需求单号（唯一键，沿用透传不重生成）｜config/spec/fee/test 环节结果键（推进只看是否齐全）｜pass==1 通过、test_passed==通过。

【页面自动巡检（结果获取无需你参与）】
环节4 稽核结果、环节6 测试结果、审批进度**由配置工作台页面自动巡检获取**：页面轮询 `result/query` 拉取 `spec`/`test`/`report` 落库结果并自动翻转页签，前端按需调用后端 `/audit/sql/query`、`/test/offer/progress`、`/test/offer/result`、审批查询端点完成轮询。**你不直调 wf_sub_12/wf_sub_13/wf_sub_08，也不引导用户回复"查询XX结果/审批进度"类关键词**；用户主动询问进度时，如实说明"页面正在自动巡检，请稍候"或引用已落库结果，不自行发起查询调用。

【路由表（唯一判据，逐行比对，命中即直调）】
主入口（无 req_id 或提报阶段）：
| 用户说（触发动词） | 直调 | 前置 |
| 新增/上新/要上/描述需求/上传需求/首条需求 | wf_sub_00 | 无 |
| 需求分析/分析需求/生成加载方案/看方案（无 req_id） | **wf_sub_00** | 无 req_id 必先提报 |
| 需求分析/分析需求/生成加载方案/看方案（已有 req_id） | wf_sub_01 | 有 req_id |
| 发起需求审批/需求工单审批 | wf_sub_11 | 已有 req_id，否则提示先提报 |
| 确认配置/开始配置/确认执行方案 | wf_sub_02 | 方案已确认，否则先 01 |
主干（按 02→03（页面巡检落 spec）→05→04（页面巡检落 test）→06 顺序推进）：
| 下一步/继续/同意（环4未发起） | wf_sub_03 | 已配置，否则先 02 |
| 下一步/继续（环4已完成、环5未做） | wf_sub_05 | spec 已落库（页面巡检已完成稽核） |
| 下一步/继续（环5已过、环6未发起） | wf_sub_04 | 资费已过 |
| 发起上线审批/提交上线审批/裸发起审批 | wf_sub_06 | 四环节齐全，否则提示缺哪环节 |
辅助/旁路（只读或独立，不受主干门禁）：
| 查询监控运维/运行监控 | wf_sub_07 | 有 offer_id 或 req_id |
| 采纳优化建议/发起优化工单 | wf_sub_14 | 监控已出优化建议，否则先 07 |
| 查询存量产品 | wf_sub_09 | 无 |
| 存量合规扫描 | wf_sub_10 | 无 |
| 重新执行/继续失败环节 | 按 fail_node：STAGE1→02 / STAGE2→03（续由页面巡检） / STAGE3→05 / STAGE4→04（续由页面巡检） |
| 查询稽核结果/查询测试结果/审批进度（用户主动问） | reply_only（引用页面巡检已落库结果，不直调查询子流） |
| 纯知识问答/超范围 | reply_only（K1~K5，超范围按答案为空） |

【三条硬规则】
1. **必须调用**：命中路由表且前置可满足 → 一律 action=call_workflow，**严禁只回话不调用**；前置不满足时**改调其前置子流**（仍要调用），不是只回复。reply_only 仅限三种：纯问答/超范围、两难反问、拒绝越门禁（如"不用确认直接配置"）。
2. **首轮判 req_id**：无 req_id 时，任何需求类消息（含"生成方案/需求分析/分析需求"）**一律先调 wf_sub_00**，绝不调 01（01 无提报记录会报错）；有 req_id 且说"分析/方案"才调 01。口诀：先提报后分析，无 req_id 必先提报。
3. **审批只判发起**：审批进度由页面自动巡检，**你不直调 wf_sub_08**；发起类仅含"需求"→11、"优化"→14、"上线/裸审批"→06。调度 06 前四环节须齐，未齐则提示缺哪环节。用户问"审批到哪一步/审批状态"时 reply_only 引用页面巡检结果，不发起查询调用。

【携带与止步】每个子流只带 req_id（沿用）；环4发起另带 chat_id、环6发起另带 offer_id。凡"发起"类（00/03/04/11/06）返回后止于反馈点，结果由页面自动巡检呈现，等用户下条消息推进。

【表达铁律】
- 结果与子流出参一一对应，禁虚构/推断成败。
- 待补充字段只逐字引用出参 pending_required（空则"无待补充"），不自行列模板必填项。
- 稽核明细只引用页面巡检已落库的 audit_suggest/error_list（空则"全部通过"），不臆造稽核项。
- 需求未提及的字段不自行补默认（尤其不臆断"全省/22地市"）。
- 环节异常即中断，按"环节/原因/明细/建议"引导重新执行。

【需求文档（.xlsx）】工作流不接文件：你挂载 skill 解析为结构化文本 → 作为 requirement_text 调 wf_sub_00；解析失败按 E41 引导。
```

### 2.3 配置项

| 配置项 | 取值 |
| --- | --- |
| 插件 | 挂载第 4 章全部插件工具与后端适配端点（22 个：13 工具 + 节点结果存储查询插件 + 8 适配端点） |
| 工作流 | 挂载 14 个子工作流 JSON（`wf_sub_00`~`wf_sub_13`，见 `工作流配置/智能体工作流集V1.6/`，`gen_workflows.py` 生成；无意图调度主流程，由智能体语义识别直调；需求文档解析由挂载 skill 承担，不占工作流） |
| 知识库 | 产销品业务规范库 / 资费规则库 / 测试规范库 / 存量销售品资料库 / FAQ（K1~K5，`knowledge/` 目录） |

### 2.4 产销品数字员工（独立配置）与产销品加载智能体的关系

> **口径**：`产销品数字员工` 与 `产销品加载智能体` 是**两个独立的平台配置项**——前者是**对外数字员工（数字员工/员工档案）**形态的独立配置，后者是承载 8 环节业务能力的**智能体**。数字员工**单独配置**，通过**关联（绑定）产销品加载智能体**复用其全部业务能力。**两者配置表单（字段结构）完全一致**，逐项对应 2.1 基本信息 + 2.3 配置项。

| 项 | 产销品数字员工（独立配置） | 产销品加载智能体 |
| --- | --- | --- |
| 配置表单 | **与加载智能体完全一致**（同字段结构：名称/功能介绍/模型配置/开场白/引导问题/答案为空提示 + 提示词 + 插件/工作流/知识库/关联对象） | 2.1 基本信息 + 2.2 提示词 + 2.3 配置项 |
| 定位 | 对外服务的数字员工形象与交互入口（可被页面/渠道直接调用） | 承载 8 环节全流程的智能体（语义识别 + 直调 14 子流 + skill 解析） |
| 配置方式 | **独立配置**：表单与加载智能体同构，独立填写名称/形象/开场白/**独立提示词（见 2.4.1）**，并**关联加载智能体** | 2.1~2.3 配置（模型/意图映射表/插件/工作流/知识库） |
| 关联关系 | **关联产销品加载智能体**（绑定后继承其工作流/插件/知识库能力）；一份数字员工可关联到加载智能体 | 被数字员工关联；能力与配置零改动 |
| 职责边界 | 面向用户的**对话表达层**（人设、话术、开场/引导、异常安抚、结果包装呈现） | 面向系统的**编排执行层**（意图→子流直调、要素提取、结果表达） |

> **复用原则**：数字员工不重复挂载工作流/插件/知识库，仅通过**关联**加载智能体取得业务能力；加载智能体被更换版本时数字员工无需改动配置（松耦合）。**数字员工仅承担表达层职责，不得绕过加载智能体直接调子流**。

**数字员工配置表单（与 2.1 同构，逐项对应）**：

| 表单项 | 取值（数字员工） | 对应 2.1/2.3 |
| --- | --- | --- |
| 助手代码 / 名称 | `cpcp_product_digital_employee` / 产销品数字员工 | 2.1 助手代码/名称 |
| 功能介绍 | 同 2.1 功能介绍（8 环节全流程，逐环节步进推进） | 2.1 功能介绍 |
| 模型配置 | 同 2.1（温度 0.2、top_p 0.5、max_tokens 2048、多轮 20 轮、`qwen3-30b-a3b`） | 2.1 模型配置 |
| 开场白 | 同 2.1 开场白 | 2.1 开场白 |
| 引导问题 | 同 2.1 引导问题 | 2.1 引导问题 |
| 答案为空提示 | 同 2.1 | 2.1 答案为空提示 |
| 提示词 | **单独配置**（见 2.4.1 数字员工人设与表达提示词） | 2.2 编排提示词 |
| 插件 / 工作流 / 知识库 | 同 2.3（可随关联继承，亦按同表单显式勾选） | 2.3 配置项 |
| **关联对象** | **关联「产销品加载智能体」**（`cpcp_product_worker`） | — |

#### 2.4.1 数字员工提示词（单独配置，表单同 2.2 提示词项）

> 数字员工提示词与 2.2 智能体提示词**分层**：2.2 是加载智能体的**编排提示词**（意图映射/门禁/纪律），本节是数字员工的**人设与表达提示词**（单独配置、独立维护、填写位置同 2.2 提示词项）。数字员工提示词不得重复定义意图映射表，仅按"人设 + 关联的加载智能体 + 表达纪律"编写。

```
【角色】
你是安徽电信产销品域的数字员工，是用户的服务形象与统一对话入口。你已关联"产销品加载智能体"，
由该智能体承载全部产销品加载业务能力（需求提报/需求分析/智能配置/规格稽核/资费校准/自动测试/
上线审批/监控运维）；你负责与用户对话、理解其诉求、转交加载智能体处理，并以其返回结果为主向用户呈现。

【关联】
- 业务能力：全部转由已关联的「产销品加载智能体」承接（语义识别 → 直调子工作流）；
- 你不在数字员工层重复定义意图→工作流映射，按加载智能体的编排结果推进即可。

【人设与话术】
1. 形象：专业、亲和、简洁；以"我"自称，称用户为"您"；不寒暄过度、不空话。
2. 开场白与引导问题沿用加载智能体配置（开场白、引导问题、答案为空提示一致）。
3. 表达纪律：结果、✅/❌、统计值必须与加载智能体（子工作流）返回一一对应，禁止凑数/虚构/推断；
   环节标题头统一 `【环节N/8·全名】`；每环节结果后给出【下一步建议】并明确用户应回复的关键词。

【与加载智能体的协作边界】
4. 确认门禁、步进推进、审批双轨消歧、待补充字段铁律、稽核/测试防幻觉铁律等**编排纪律以加载智能体为准**，
   数字员工只负责把这些结果与引导**如实转述**给用户。
5. 用户上传 .xlsx 需求文档时，交由加载智能体（其挂载 skill）解析，数字员工不自行解析。
6. 超范围问题按"答案为空"提示；异常时按加载智能体输出的异常模板转述原因/建议并安抚。
```

---

## 3. 需求到平台能力映射

### 3.1 业务环节 → 平台实现载体

| 业务环节 | 业务目标 | 平台实现载体 |
| --- | --- | --- |
| 需求提报（环节1/8） | 收集需求（文本/Excel 需求文档）、生成需求工单；审批须用户明确确认后才发起 | **上传 .xlsx 时由智能体挂载 skill 解析为多品结构化文本**（工作流 start 节点不接收文件）；skill 产物作为 `requirement_text` 直调 `wf_sub_00`：LLM 要素提取（多品）→ CODE_RENDER_REQ 渲染提报单 + "是否发起【需求工单审批】？"，止于确认点；用户回复【发起需求审批】后由 `wf_sub_11` 承接：自查 requirement_report → 提取原文 → `submit_release_approval`(approval-type=requirement) |
| 需求分析（环节2/8） | 业务要素→配置字段映射，生成《加载方案》并存储；生成后止于确认点 | `wf_sub_01` 模板轨：要素提取 → CODE_VALIDATE_ELEMENTS 质量门禁 → CODE_MERGE_NESTED → CODE_OP_VALIDATE_NESTED 本体校验闸 → CODE_RENDER_TABLE → 保存 requirement → 输出方案即止于确认点 |
| 用户确认 | 人工确认执行方案后再配置落地 | 智能体提示词【限制】识别确认语义（未命中确认不调度 `wf_sub_02`） |
| 执行主干（步进式） | 配置→稽核→资费→测试逐环节推进 | 智能体语义识别"确认配置"后逐环节调度 `wf_sub_02`→`wf_sub_03`→`wf_sub_05`→`wf_sub_04`（每环节止于反馈点，失败即中断）；稽核/测试结果由**配置工作台页面自动巡检**（前端轮询 `result/query` + 后端查询端点）取回并落 `spec`/`test`；异常按 fail_node 续跑 |
| 智能配置（环节3/8） | 按 req_id 自查执行方案 JSON 并落地 CRM | `wf_sub_02`：自查 requirement → `save_product_config` → CODE_FUSION_GROUP_ECHO 融合成员回显 |
| 规格稽核（环节4/8） | 配置落地后发起稽核并由页面巡检取结果 | `wf_sub_03`（4a 稽核发起：上传 Excel→逐品异步通知+落库，秒级返回）+ **页面自动巡检**（4b：前端轮询 `result/query` + 后端 `/audit/sql/query`：SQL 轮询→取结果→归一 error_list，完成 pass==1 后落 `spec`）详见 4.5.3 场景B |
| 资费校准（环节5/8） | 校验计费逻辑、优惠叠加冲突 | `wf_sub_05`（成员分组）：`check_billing_rule`(check_scene=all) |
| 自动测试（环节6/8，含受理验证） | 发起测试→页面巡检取结果，报告含受理验证结论 | `wf_sub_04`（6a 发起：`offer_test`+`get_test_scenes`，秒级返回）+ **页面自动巡检**（6b：前端轮询 `result/query` + 后端 `/test/offer/progress`、`/test/offer/result`：CODE_POLL_PROGRESS 式轮询 + CODE_MAP_FIXED_CASES + 九章节报告 + 报告下载，完成 test_passed==通过 后落 `test`）详见 4.5.3 场景C |
| 上线审批（环节7/8） | 汇总报告、推送审批；**推送即结束（不轮询/不分流）**；审批状态由页面自动巡检呈现 | `wf_sub_06`（双轨）：CODE_SUMMARY_APPROVAL → `submit_release_approval` → 推送后即结束，返回 approval_id/status |
| 审批进度查询（辅助） | 查询审批单当前状态（双轨） | **页面自动巡检**（前端按需调审批查询端点）+ `query_approval_status` 后端支撑 |
| 存量产品查询（辅助） | 查询存量/在售销售品（只读） | `wf_sub_09` + CODE_OP_QUERY_OFFER |
| 存量合规扫描（辅助） | 存量销售品上架合规扫描 | `wf_sub_10` + CODE_OP_SHELF_COMPLIANCE（/shelf-compliance） |
| 监控运维（环节8/8） | 上线后持续监控，异常根因推理+建工单闭环 | `wf_sub_07`：`query_product_monitor` → 异常时 CODE_OP_ROOT_CAUSE（/ops/root-cause）+ CODE_OP_CREATE_WO（/ops/work-orders） |

### 3.2 原子能力 → 工具清单（22 个）

#### A. 自研能力接口工具（工具1~6，全部自研模拟实现，兼容 18 个销售品种子）

| # | 工具名 | 用途说明 | 现场接口映射（V3.9） |
| --- | --- | --- | --- |
| 1 | `query_similar_offer` | 相似度分析：按业务需求描述查询相似销售品（mock 返回相似度最高 1 个 + offerInfo 同构 24 字段；**real A1 仅 4 字段，offerInfo 由 K4/K5 按 ID 补齐**，见 4.5.5），支撑 AI补全 | A1 相似度分析 `...328` |
| 2 | `realtime_spec_audit` | 实时稽核：按销售品/配置内容实时发起稽核并同步返回结果（**V3.9 起退出环节4主链路，保留单测/兜底**） | — |
| 3 | `offer_test` | 测试发起动作：返回测试流水 `globalId`，测试平台自动执行受理类场景 | C1 `...080` |
| 4 | `get_test_scenes` | 查询本次测试匹配的测试场景集合（即受理验证覆盖范围） | C2 `...880` |
| 5 | `get_test_progress` | 轮询测试步骤、是否完成/失败（done/failed/failIndex） | C3 `...576` |
| 6 | `get_test_result` | 查询逐场景测点比对明细；`orderId`/`offerInstId` 为受理生成的订单号/实例 ID（受理验证依据） | C4 `...856` |
| — | （新增）`audit_file_upload` / `audit_async_notify` / `audit_insert_result` | 智能稽核发起三接口代理：上传解析销售品清单→逐品异步通知→落库（4a 发起流 wf_sub_03，秒级返回） | B1 `...064` / B2 `...912` / B3 `...840` |
| — | （新增）`audit_sql_query` | 智能稽核轮询/取结果代理：SQL 轮询完成态 + 取最终结果（环节4b **页面自动巡检触发的后端查询**，可重复触发） | B4 `...032` |

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

#### D. V2.0 新增后端适配端点（AppStoreV16Controller，8 个）

| # | 端点（POST） | 用途 | 承载环节 |
| --- | --- | --- | --- |
| 14 | `/ops/root-cause` | 监控异常根因推理 | wf_sub_07（CODE_OP_ROOT_CAUSE） |
| 15 | `/ops/work-orders` | 运维建工单闭环 | wf_sub_07（CODE_OP_CREATE_WO） |
| 16 | `/shelf-compliance` | 存量上架合规扫描 | wf_sub_10（CODE_OP_SHELF_COMPLIANCE） |
| 17 | `/validate-nested` | 嵌套本体校验闸 | wf_sub_01（CODE_OP_VALIDATE_NESTED） |
| 18 | `/explain` | 校验/合规结论可解释说明 | 各子流解释类输出 |
| 19 | `/report/download` | 九章节正式版测试报告下载 | wf_sub_04（CODE_DOWNLOAD_TEST_REPORT） |
| 20 | `/script/download` | CRM/billing 落库 SQL 上线脚本下载 | wf_sub_06（V2.1 已随审批推送即结束移除 CODE_DOWNLOAD_LAUNCH_SCRIPT，端点保留但子流不再调用） |
| 21 | `/requirement/parse-xlsx` | **V3.10 新增**：需求文档（.xlsx）解析为结构化需求（多品清单 + 品间关联关系 + 宽表列），与 B1 稽核上传同模式（multipart + 解析） | **智能体挂载 skill 调用**（6.6 章 G1；工作流层不接收文件，V3.11 起无子流节点承载） |

> **代码节点化**：原技能包脚本子命令（build_plan/extract_record/poll/dispatcher/map_fixed_cases 等）全部重构为工作流 type=6 代码节点，确定性逻辑内嵌于 14 个工作流 JSON。
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
| 请求报文 | **V2.7 起全部裸报文**：请求体直接为业务参数 JSON（置于顶层），tcpCont 报文头与 contractRoot 包裹结构整体移除；出参侧兼容解包保留。**V3.9 现场接口（4.5）由后端适配层按规范组装/解包 envelope，工作流侧仍裸传** |
| 运行模式 | `prodai.appstore.interface.mode=mock\|real`（application.yml；开发 mock 返回模拟报文 / 现场 real 直连真实接口）；切现场仅改配置，工作流与插件不变（详见 4.5.4） |
| 超时/重试 | 同步类 60s（稽核类重试 1 次）、异步轮询类 30s；网络类错误重试 1 次；**写操作（save_product_config）不自动重试** |
| 错误码归一 | PARAM_MISSING / HTTP_xxx / NET_ERROR / TIMEOUT / PARSE_ERROR / ONTOLOGY_EMPTY；出参 JSON 原样打印，模型逐字引用不加工 |
| 出参归纳 | 查询类工具（2/4/5/6/10）出参 JSON 原样透出由 LLM 归纳；执行类工具（1/3/7/8/9/11）结束节点逐字引用拼装 |

### 4.2 工具契约汇总表

| 工具 | 方法/路径 | 主要入参 | 主要出参 | 备注 |
| --- | --- | --- | --- | --- |
| 1 query_similar_offer | POST /api/v1/appstore/similar/offer/query | businessDesc(≤5000) | similarOffer(similarOfferId/Name/Score/Desc/offerInfo.fields 24 字段；**real A1 无 offerInfo，按 ID 补 K4/K5**) | 未命中走"无相似产品"分支不中断（E1）；现场映射 A1（4.5.2/4.5.5） |
| 2 realtime_spec_audit | POST /api/v1/appstore/audit/realtime | offer_id, config_json, audit_scene(spec/fee/all) | pass / error_list[] / audit_summary / resultCode | 同步返回；pass=0 中断引导（E8）；**V3.9 起退出环节4主链路，保留单测/兜底** |
| 2a audit_file_upload | POST /api/v1/appstore/audit/file/upload | req_id, chat_id | file_id / sales_id{名称:{offerId,location,sheet_name}} | **新增**；现场映射 B1；后端按 req_id 查需求与配置信息**自行生成稽核 Excel** 后上传（无前端文件）；解析销售品清单（E35） |
| 2b audit_async_notify | POST /api/v1/appstore/audit/async/notify | sales_id, location, sheet_name, file_id | success / request_id | **新增**；现场映射 B2；逐品调用（E36） |
| 2c audit_insert_result | POST /api/v1/appstore/audit/result/insert | records[{batchId,reqOrderId,offerId,offerName,requestId,staffId}] | resultCode / resultMsg | **新增**；现场映射 B3；**紧跟 2b 调用**（回调前置落库，E37） |
| 2d audit_sql_query | POST /api/v1/appstore/audit/sql/query | sqlId(20260626001/20260626002), params(requestIds:id1,id2) | executeSqlResp[](isAllFinished/finishedCount/totalCount) | **新增**；现场映射 B4；轮询完成态+取结果（E38/E39） |
| 2e requirement_parse_xlsx | POST /api/v1/appstore/requirement/parse-xlsx | file(.xlsx) | requirement{offers[{offer_seq,ofr_id,offer_name,sheet_name,fields{...}}], relations[{a_ofr_id,a_name,relation,z_ofr_id,z_name}], source_sheets[]} | **V3.10 新增**；需求文档→结构化需求（多品 + 关联关系 + 宽表列，6.6 章 G1）；**V3.11 由智能体挂载 skill 调用（工作流层不接收文件，不再由任何子流节点调用）**；文件非法/无销售品 → E41 |
| 3 offer_test | POST /api/v1/appstore/test/offer/start | offerId | globalId(50+14位+10位随机) | 发起动作；resultCode=1 终止（E10）；现场映射 C1 |
| 4 get_test_scenes | POST /api/v1/appstore/test/offer/scenes | globalId | testScenes[]（S_O_TC/S_ADD_CARD/S_U_TC...） | 空场景列表终止（E11）；现场映射 C2 |
| 5 get_test_progress | POST /api/v1/appstore/test/offer/progress | globalId | totalSteps/activeIndex/done/failed/failIndex | 由页面自动巡检（后端短窗口轮询）调用；现场映射 C3 |
| 6 get_test_result | POST /api/v1/appstore/test/offer/result | globalId | testScenes[] / orderId / offerInstId | done=true 后查询；受理凭证；现场映射 C4 |
| 7 save_product_config | POST /api/v1/appstore/product/config/save | req_id, plan_json(原文透传), operator | offer_id / status / save_result / script_url | 不设确认门禁（V2.2 移除）；幂等；script_url 绝对 URL |
| 8 check_billing_rule | POST /api/v1/appstore/billing/rules/verify | config_json, check_scene(fee/overlay/superposition/all) | pass / risk_list[] / compare_list[]（8 项比对） | pass=0 中断（E9） |
| 9 submit_release_approval | POST /api/v1/appstore/approval/submit | req_id, offer_id, report_url, approval_flow(standard/urgent) | approval_id / status | **后端四环节硬校验**；双轨（requirement/launch）；幂等 | **审批单号 `approval_id` 为工单级单值（顶层），一个工单可含多个商品（`offers[]`）；商品级审批状态见 `offers[].status`**
| 10 query_product_monitor | GET /api/v1/appstore/product/monitor | offer_id, date_range, metric(order/error/fee/all) | order_count / error_count / fee_error_rate / alarm_list[] | 确定性模拟指标 |
| 11 send_alert | POST /api/v1/appstore/alert/send | offer_id, alarm_level(high/middle/low), content | alert_id / status | 联动稽核驳回/监控异常 |
| 13 query_approval_status | GET /api/v1/appstore/approval/status | approval_id / offer_id（至少一个） | approval_id / status / current_node / approver / opinion / submit_time / update_time | 查无审批单（E21） |
| 节点结果存储 | POST /api/v1/appstore/result/save、GET /api/v1/appstore/result/query | req_id / node_name / result_json（保存）；req_id/node_name/latest_only（查询） | code/msg/total/list（取 list[0].result_json 为原文） | req_id 格式校验 5002；≤64KB（5004）；同键覆盖写 |

### 4.3 工具级自测要点（节选；完整 14 项见《细化设计方案》2.4）

globalId 格式（50+19 位）；实时稽核 60s 内同步返回；工具6 orderId/offerInstId 非空；save_product_config 幂等（同 plan_json 不重复写入）；**裸报文契约**（请求体顶层可直接读到业务参数，无 contractRoot/tcpCont 包裹）；必填入参 PARAM_MISSING 提示；18 套餐模拟数据全覆盖（presetValue 与《产品信息.txt》逐项一致）；要素提取质量闸防护（`CODE_VALIDATE_ELEMENTS` quality_gate=FAIL → E31）；`CODE_OP_VALIDATE_NESTED` 端点不可达回退 backend_pending=1。

---

### 4.5 现场接口适配与配置化封装（V3.9 新增，产销品场景接口规范落地）

> 依据《营销服超级数字员工-产销品场景接口规范.md》（**9 个接口全部纳入业务链路**，服务域产销品域，EOP 基础地址 `https://eop-ops.cloud.anhuitelecom.com:10008`）。**目标**：现场演示必须真实调用这 9 个接口；开发环境在 `application.yml` 返回模拟报文（`prodai.appstore.interface.mode=mock`），客户现场**仅改配置**（`mode=real` + base-url）即直连真实接口，工作流/插件零改动。

#### 4.5.1 封装原则（最小化改造）

| 原则 | 说明 |
| --- | --- |
| 对外契约不变 | 子工作流仍只认 `/api/v1/appstore/*` 与既有出参字段，**9 接口全部由后端适配层封装**，工作流 JSON 不改调用路径（仅环节4 稽核链路重构，见 4.5.3） |
| 一一映射 + 补齐新接口 | 重叠接口（相似度/测试四件套）映射到既有工具，仅补信封解码；全新接口（上传/异步通知/插入结果/SQL 查询）新增后端适配端点 |
| 配置化切换 | `application.yml` 新增配置组 `prodai.appstore.interface.*`（`mode=mock\|real` + `eop.*` + `poll.*`）；后端 `OfferSimV16Service`（mock）与 `EopInterfaceAdapter`（real）实现同一接口，按 `mode` 经 Spring `@ConditionalOnProperty` 装配（工厂+策略），改配置即切换 |
| 报文双向适配 | real 模式：请求按规范组装 `contractRoot/tcpCont/svcCont` 信封、`transactionId=yyyyMMddHHmmssSSS+随机`、固定 `svcCode/appKey/dstSysId/sign`；响应解包 `contractRoot.svcCont.resultObject` → 归一为既有裸出参 |
| 零模型参与 | 信封组装/解包/轮询均在 type=6 代码节点或后端完成，LLM 不接触 tcpCont 字段 |

#### 4.5.2 9 接口归一映射表（现场接口 → 内部工具 → 承载环节）

> **接口溯源（EOP ApiID 全号）**：A1 `2069789780752355328`、B1 `2069625643688792064`、B2 `2069670925484838912`、B3 `2092438683005603840`、B4 `2077203852248748032`、C1 `2070425775141294080`、C2 `2070440544124538880`、C3 `2071410624769560576`、C4 `2070435348195577856`。规范中 9 个接口**全部有内部映射并纳入业务链路**（A1→环节2；B1~B4→环节4；C1/C2→环节6a、C3/C4→环节6b），无接口遗漏。

| # | 现场接口 (ApiId 尾号) | 路径 | 内部映射 | 承载 | 适配动作 |
| --- | --- | --- | --- | --- | --- |
| A1 | 相似度分析 `...328` | `/api/ah/cpcp/similar` | 工具1 `query_similar_offer` | 环节2 wf_sub_01 | real：`{businessDesc}`→`contractRoot.tcpCont+svcCont.requestObject` 信封；出参 `contractRoot.svcCont.resultObject.similarOfferList[]`（**仅 4 字段**：similarOfferId/Name/Score/Desc）→ 取 Top1 归一为 `similarOffer`；**22 字段 offerInfo 不由 A1 提供**，见 4.5.5 第 6 点 |
| B1 | 智能稽核文件上传 `...064` | `/api/ah/cpcp/upload` | **新增** `audit_file_upload` | 环节4a wf_sub_03 | 入参 `req_id/chat_id`，后端按 req_id 查需求与配置信息**生成稽核 Excel** 后上传→`file_id`/`sales_id{}`；mock 返回预置清单 |
| B2 | 异步报告生成并通知 `...912` | `/api/ah/cpcp/async_report_notify_direct` | **新增** `audit_async_notify` | 环节4a wf_sub_03 | GET 传 `sales_id/location/sheet_name/file_id`→`request_id` |
| B3 | 插入 AI 稽核结果 `...840` | `/api/ahhui/cpcp/insertAiAuditResult` | **新增** `audit_insert_result` | 环节4a wf_sub_03 | `records[]`→`resultCode`；**紧跟 B2 调用**（AI 回调前置落库） |
| B4 | 通用 SQL 查询 `...032` | `/api/ah/slzx/invokeInfo/executeSqlService` | **新增** `audit_sql_query` | 环节4b 页面巡检（后端查询承载） | `sqlId=20260626001`（轮询完成 N/Y）/`20260626002`（取结果）；`params=requestIds:i1,i2,...`；由后端短窗口轮询（页面自动巡检触发） |
| C1 | 销售品测试发起 `...080` | `/api/anhui/cpcp/offerTest` | 工具3 `offer_test` | 环节6a wf_sub_04 | 入参一致；出参 `resultObject.globalId`→既有 `globalId` |
| C2 | 查询测试场景 `...880` | `/api/anhui/cpcp/getTestScenes` | 工具4 `get_test_scenes` | 环节6a wf_sub_04 | 出参 `resultObject.testScenes[]`→既有 `testScenes` |
| C3 | 查询测试进度 `...576` | `/api/anhui/cpcp/getTestProgress` | 工具5 `get_test_progress` | 环节6b 页面巡检（后端查询承载） | 出参 `resultObject.*`→既有 done/failed/failIndex；后端短窗口轮询（页面自动巡检触发） |
| C4 | 查询测试结果 `...856` | `/api/anhui/cpcp/getTestResult` | 工具6 `get_test_result` | 环节6b 页面巡检（后端查询承载） | 出参 `resultObject.testScenes/orderId/offerInstId/offerName`（逐场景受理凭证）→既有字段 |

> **未变更接口**（规范未覆盖，保持自研）：工具2 `realtime_spec_audit` 退出环节4 主链路（见 4.5.3，保留供单测/兜底）、工具7 `save_product_config`、工具8 `check_billing_rule`、工具9 `submit_release_approval`、工具10/11/13、节点结果存储。现场若另有对应接口，按同款模式增补映射即可。

#### 4.5.3 三场景链路（接口如何串起来）

**场景A · 相似度分析**（无状态，单次调用）
```
wf_sub_01 节点105 → A1 相似度分析（信封：{businessDesc}）
   → similarOfferList[]（仅 similarOfferId/Name/Score/Desc 四字段）
   → 取相似度最高1个 → 用 similarOfferId 从 K4/K5 本地资产补齐 24 字段 offerInfo 做 AI补全
        （real 模式 A1 不返回 offerInfo，见 4.5.5 第 6 点；mock 模式工具1 直接返回含 offerInfo 的种子）
落点：环节2 需求分析《加载方案》生成（取值链：原始需求→相似产品→本体默认值→待补充）
```

**场景B · 智能稽核**（环节4 规格稽核，拆为发起流 `wf_sub_03`（智能体直调）+ 轮询段（配置工作台页面自动巡检））

> **动机**：接口④为异步轮询（最长 30 分钟），若与前置链路同子流会长时间阻塞一次调用；故拆为"发起"与"轮询取结果"两段——**发**由智能体直调 `wf_sub_03`（秒级返回），**轮询取结果由配置工作台页面自动巡检**（前端轮询 `result/query` + 后端 `/audit/sql/query`），轮询单次窗口缩短（默认 5 分钟/60 次），未完成则页面续轮询，**智能体不再直调 `wf_sub_12`**。

```
【阶段4a · 稽核发起 wf_sub_03】（秒级返回，智能体直调）
用户触发稽核（传入 req_id + chat_id）
        │
        ▼  B1 智能稽核文件上传（后端按 req_id 查需求与配置信息生成稽核 Excel 后上传）
file_id + sales_id{ 销售品名:{offerId, location, sheet_name} }
        │
        ▼  遍历 sales_id 每个销售品（逐一）：
   B2 异步报告生成并通知(sales_id,location,sheet_name,file_id) → request_id
   B3 插入AI稽核结果(records[{batchId,offerId,offerName,requestId,...}]) → 落库状态1000
        │   （B2↔B3 成对且有序：先通知拿到 request_id，立刻落库，保证 AI 回调时 DB 已有记录）
        ▼  汇总 requestIds
   save_node_result(node_name=audit_pending, requestIds/sales_id清单)   ← 临时态
   输出"稽核已发起（requestIds=...）"止于反馈点
        ▼（配置工作台页面自动巡检：前端轮询 result/query + 后端 /audit/sql/query）
【阶段4b · 稽核轮询取结果（页面自动巡检，后端查询承载）】
   后端按 req_id 取 audit_pending 的 requestIds
        ▼
   B4 通用SQL查询(sqlId=20260626001, params=requestIds:id1,id2,...) → isAllFinished  ← 短窗口轮询
        │   isAllFinished=N → 单次窗口内间隔轮询；窗口耗尽 → 页面续轮询（不写 spec，非中断）
        ▼   isAllFinished=Y
   B4 通用SQL查询(sqlId=20260626002, params=requestIds:...) → 最终稽核结果
        ▼  映射 error_list[]（含 group 类目）/audit_summary
   pass=1 → save_node_result(node_name=spec) + 页面翻转显示结果（允许推进环节5）
   pass=0 → 中断引导（E8，不写 spec）
        ▼
用户确认后进入环节5 资费校准（wf_sub_05 不变）
```
- **门禁**：`spec` 仅在阶段4b 巡检完成且 pass==1 时写入（由后端/页面落库）；环节5 自查与审批四环节硬校验以 `spec` 为准，故稽核未完成不允许推进。
- **轮询纪律**：B4 单次窗口间隔 5s、上限 60 次（5 分钟）；连续 5 次失败终止（E38c）。窗口耗尽非错误，页面续轮询。
- **多品顺序**：POC 顺序处理 sales_id 清单（每品 B2+B3），请求集合以逗号拼接，①②③ 均在发起流一次完成。

**场景C · 智能测试**（环节6 自动测试，四接口信封适配；拆为发起流 `wf_sub_04`（智能体直调）+ 轮询段（配置工作台页面自动巡检））

> **动机**：与智能稽核同构——测试平台异步执行（原轮询上限 30 分钟），若同子流会长时间阻塞一次调用；**发**由智能体直调 `wf_sub_04`（秒级返回），**轮询取结果由页面自动巡检**（前端轮询 `result/query` + 后端 `/test/offer/progress`、`/test/offer/result`），轮询单次窗口缩短（默认 5 分钟/60 次），未完成则页面续轮询，未完成不写 `test` 键、不允许推进上线审批，**智能体不再直调 `wf_sub_13`**。

```
【阶段6a · 测试发起 wf_sub_04】（秒级返回，智能体直调）
wf_sub_04 自查 config → C1 测试发起{offerId} → globalId
   → C2 查询测试场景{globalId} → testScenes[]（含各场景 orderId/offerInstId 归属）
   → save(node_name=test_pending：globalId/testScenes)   ← 临时态
   输出"测试已发起（globalId=...，场景N个）"止于反馈点
        ▼（配置工作台页面自动巡检：前端轮询 result/query + 后端 /test/offer/progress、/test/offer/result）
【阶段6b · 测试轮询取结果（页面自动巡检，后端查询承载）】
   后端按 req_id 取 test_pending 的 globalId
        ▼
   C3 查询测试进度{globalId} → done/failed/failIndex  ← 短窗口轮询（间隔5s/上限60次=5分钟）
        │   未完成 → 页面续轮询（不写 test，非中断）
        ▼   done=true
   C4 查询测试结果{globalId} → testScenes[]/orderId/offerInstId/offerName（逐场景受理凭证）
        ▼
   CODE_MAP_FIXED_CASES(31条固定用例) → 九章节报告 → save(node_name=test)（test_passed==通过 才写）
```
- **门禁**：`test` 仅在阶段6b 巡检完成且 test_passed==通过 时写入（由后端/页面落库）；上线审批 `submit_release_approval` 四环节硬校验以 `test` 为准，故测试未完成不允许推进。
- **轮询纪律**：C3 单次窗口间隔 5s、上限 60 次（5 分钟）；连续 5 次失败终止（E40c）。窗口耗尽非错误，页面续轮询。

#### 4.5.4 配置化切换设计（开发 mock ↔ 现场 real，基于 application.yml）

> **落地方式**：在 Spring Boot 配置文件（`application.yml` 主配置 + `application-dev.yml`/`application-prod.yml` 分环境覆盖）新增 `prodai.appstore.interface.*` 配置组，由 `@ConfigurationProperties` 绑定。**开发环境返回模拟报文，现场环境仅改配置即直连真实接口**，工作流/插件/后端代码零改动。
>
> ⚠️ **实现状态（V3.11）**：本小节**已落地**——配置组 `prodai.appstore.interface.*`（`AppStoreInterfaceProperties`）、`InterfaceFacade` 门面（`MockInterfaceAdapter`/`EopInterfaceAdapter`/`RoutingInterfaceFacade`）、装配类 `AppStoreInterfaceConfig`，以及 **B1~B4 现场接口（`/audit/file/upload`、`/audit/async/notify`、`/audit/result/insert`、`/audit/sql-query`）** 与 **A1/C1~C4 经门面路由**均已实现并通过单测（`InterfaceAdapterTest`，dev=mock）；`/requirement/parse-xlsx` 解析逻辑后端（`RequirementDocService`）亦已实现，同名解析能力另以**智能体内嵌 skill** 形式落地（`场景设计/ah_cti_poc/skills/requirement-doc-parse/`，供无后端环境使用，见 6.6）。mock 模式返回结构须与 real 归一后结构完全一致（同构）；real 不可达时自动回退 mock 并在出参附 `note` 标注"已回退模拟"，保证演示不中断。B1~B4、A1 的**现场真实行为**仍需 EOP 联调验证（mock 可独立跑通）。

**配置项（前缀 `prodai.appstore.interface`）**：

| 配置项 | 取值 | 说明 |
| --- | --- | --- |
| `prodai.appstore.interface.mode` | `mock`（默认，开发环境）/ `real`（现场） | **总开关**：mock 走模拟报文，real 走现场真实接口 |
| `prodai.appstore.interface.eop.base-url` | `https://eop-ops.cloud.anhuitelecom.com:10008` | real 模式 EOP 基础地址 |
| `prodai.appstore.interface.eop.app-key` | `eOrder1` | tcpCont 固定头（规范 §2.2 备注 8） |
| `prodai.appstore.interface.eop.dst-sys-id` | `OrderCenter` | tcpCont 固定头（备注 9） |
| `prodai.appstore.interface.eop.svc-code` | `5012010056` | SQL 查询服务编码（备注 3） |
| `prodai.appstore.interface.eop.sign` | `123` | 签名占位（备注 10，现场若有真实签名算法在此扩展） |
| `prodai.appstore.interface.eop.upload-file-field` | `file` | 上传表单字段名（规范 B1 备注 1，默认 `file`） |
| `prodai.appstore.interface.eop.timeout-ms` | `60000` | real 接口调用超时 |
| `prodai.appstore.interface.poll.audit.interval-ms` / `max` | `5000` / `60` | 稽核轮询单次窗口（5s×60=5 分钟，页面自动巡检触发、窗口耗尽页面续轮询，非中断） |
| `prodai.appstore.interface.poll.test.interval-ms` / `max` | `5000` / `60` | 测试轮询单次窗口（5s×60=5 分钟，页面自动巡检触发、窗口耗尽页面续轮询，非中断；取代原 30 分钟单窗口口径） |

**application.yml（主配置，公共默认值）**：
```yaml
prodai:
  appstore:
    interface:
      mode: mock                                   # 默认模拟；现场改 real
      eop:
        base-url: https://eop-ops.cloud.anhuitelecom.com:10008
        app-key: eOrder1
        dst-sys-id: OrderCenter
        svc-code: "5012010056"
        sign: "123"
        upload-file-field: file
        timeout-ms: 60000
      poll:
        audit: { interval-ms: 5000, max: 60 }
        test:  { interval-ms: 5000, max: 60 }
```

**application-dev.yml（开发环境，显式模拟报文）**：
```yaml
prodai:
  appstore:
    interface:
      mode: mock
```

**application-prod.yml（客户现场，切真实接口）**：
```yaml
prodai:
  appstore:
    interface:
      # 现场仅改此开关与 base-url 即直连 EOP，其余保持默认
      mode: real
      eop:
        base-url: https://eop-ops.cloud.anhuitelecom.com:10008   # 现场按需覆盖
        app-key: ${EOP_APP_KEY:eOrder1}                          # 现场下发值可用环境变量覆盖
        dst-sys-id: ${EOP_DST_SYS_ID:OrderCenter}
        sign: ${EOP_SIGN:123}
```

**切换机制（策略模式 + 工厂，Spring 装配）**：
```
InterfaceFacade (单例门面, 注入 AppStoreV16Controller)
   ├── mode=mock → MockInterfaceAdapter   （包装现 OfferSimV16Service，18 销售品种子，返回模拟报文）
   └── mode=real → EopInterfaceAdapter    （RestClient/HttpClient 调 EOP，组装/解包信封）
由 @Configuration + @ConditionalOnProperty(name="prodai.appstore.interface.mode", havingValue="mock"/"real")
分别装配实现 Bean；对外统一门面 AppStoreV16Controller（/api/v1/appstore/*）签名与出参不变。
```
- **开发环境**：`mode=mock`，全部走模拟报文（现有 OfferSimV16Service 能力，18 销售品种子），工作流与插件零改动。
- **现场环境**：`mode=real` + 改 `base-url`（必要时用环境变量覆盖 app-key/sign），工作流/插件/Controller 均不变；EOP 出参经 Adapter 归一为既有裸出参，下游 type=6 代码节点与 LLM 无感知。
- **混合模式**（可选，逐接口联调）：新增 `prodai.appstore.interface.real-list=similar,audit,test` 白名单，命中名单的接口走 real、其余走 mock，便于现场按接口逐个验证。
- **传参覆盖（V3.11）**：9 个接口请求体顶层可选 `mode` 字段（`real`/`mock`/空或 `auto`），**传参最优先**——传 `real`/`mock` 覆盖 yml 的 `mode` 与 `real-list`；不传/`auto` 回落 yml（real-list 命中或 mode=real → real，否则默认 mock，无配置默认模拟）。B1 为 multipart，`mode` 走表单字段；其余在 JSON 体顶层。real 不可达时仍自动回退 mock 并附 `note` 标注。

#### 4.5.5 契约兼容要点（信封与字段）

1. **requestObject 信封差异**：A1/B4 为 `contractRoot.tcpCont + svcCont.requestObject`；B1 为 multipart；B2 为 GET query；B3 为 `{records[]}` 裸体；C1~C4 为 `{offerId}`/`{globalId}` 裸体（与现场一致）。适配层按接口分别组装，工作流侧统一裸传业务参数。
2. **出参解包**：A1/B4 解 `contractRoot.svcCont.resultObject`；C1~C4 解 `resultObject`（裸结构）；B3 解 `resultCode/resultMsg`。统一转既有内部出参名，**不改动下游字段消费**。
3. **字段命名**：接口原始出参 camelCase（`similarOfferList`/`testSceneId`/`resultCode`）按规范原样保留于 Adapter 内部；归一后既有 snake_case 契约不变（`offerId` 为唯一 camelCase 特例沿革不变）。
4. **mock 一致性**：mock 模式返回结构须与 real 归一后结构**完全一致**（同一 Adapter 接口），确保"开发用模拟、现场切真实"行为同构。
5. **测试场景响应增强（V3.9）**：C2/C4 的 `objTestSceneRel` 携带 `resultMsg/summaryDesc/suggestion` 三字段，由 CODE_MAP_FIXED_CASES 并入九章节报告相应章节（AI 总结/摘要/建议），不新增独立报告章节；各测试场景的 `orderId/offerInstId` 为**逐场景归属**（规范口径），报告 4.1 受理验证小节按场景逐条输出，取代原全局单一 orderId/offerInstId 口径。
6. **相似度 A1 字段缺口与补齐（V3.9 关键）**：现场 A1 出参 `similarOfferList[]` **每项仅 4 字段**（`similarOfferId`/`similarOfferName`/`similarityScore`/`similarityDesc`），**不含既有工具1契约的 `offerInfo.fields` 24 字段**。适配与补齐口径：
   - **real 模式**：Adapter 只归一 4 字段，`offerInfo` 置空/缺省；`wf_sub_01` 节点105 后的 AI 补全取值链**不依赖 A1 返回 offerInfo**，改为——命中 `similarOfferId` 后用其 ID 从 **K4 存量资料库/K5 存量报文**（本地结构化资产，按销售品 ID 精确定位）取 24 字段作为 AI 补全参照（取值链：原始需求 → 相似产品（K4/K5 补齐 offerInfo）→ 本体默认值 → 待补充）。
   - **mock 模式**：沿用现有能力（工具1 直接返回含 `offerInfo.fields` 的 18 销售品种子），保持内测补全链路可独立跑通。
   - **契约影响**：下游消费 `similarOffer.similarOfferId/Name/Score/Desc` 不变，`offerInfo` 由「工具返回」改为「命中 ID 后本地补齐」；两者产出结构一致，故 mock/real 同构（4.5.5 第 4 点）成立。若现场后续 A1 增补 offerInfo 字段，Adapter 直接解包即可，工作流无感。

---

## 5. 工作流设计

### 5.1 智能体调度模式与执行主干时序

> **核心口径**：无独立意图调度主流程，智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别直调 14 个子工作流。确定性纪律（req_id/落库/成败判定/续跑/四环节硬校验）由子流自查链路 + type=6 代码节点 + 后端硬校验兜底，**不依赖 LLM 自觉**。需求文档解析由智能体挂载 skill 承担（工作流层不接收文件）。

```
前置：wf_sub_00 提报单就绪止于确认点 → 用户回复【发起需求审批】→ wf_sub_11 发起需求工单审批
  → wf_sub_01 生成《加载方案》止于确认点（未回复确认前不调度 wf_sub_02）
用户回复"确认配置"（携带上轮 req_id）
  ▼
环节3/8 直调 wf_sub_02 → status==SUCCESS/PARTIAL 打印结果+下一步建议 → **止于反馈点**
  ▼（用户回复【下一步】/【继续】）
环节4/8 直调 wf_sub_03（4a 稽核发起：上传Excel→逐品异步通知+落库，秒级返回）→ 打印"稽核已发起"+下一步建议 → **止于反馈点**
  ▼（页面自动巡检：前端轮询 result/query + 后端 /audit/sql/query；完成 pass==1 后端落 spec）
环节4/8 页面巡检完成落 spec → 打印结果+下一步建议 → **止于反馈点**（未完成不写 spec，不允许推进）
  ▼
环节5/8 直调 wf_sub_05 → pass==1 打印结果+下一步建议 → **止于反馈点**
  ▼
环节6/8 直调 wf_sub_04（6a 测试发起：offer_test→get_test_scenes，秒级返回）→ 打印"测试已发起（场景N个）"+下一步建议 → **止于反馈点**
  ▼（页面自动巡检：前端轮询 result/query + 后端 /test/offer/progress、/test/offer/result；完成 test_passed==通过 后端落 test）
环节6/8 页面巡检完成落 test → test_passed==通过 打印结果（含受理验证小节）→ 止于汇总点（未完成不写 test，不允许推进）
  ▼
⑦ 智能体汇总打印成功详情 + 提示"是否发起上线审批"
用户回复"发起审批" → wf_sub_06（串行自查 5 类环节结果 → 报告 → submit_release_approval 推送
  → 推送即结束，返回 approval_id/status；不轮询/不分流，审批状态由页面自动巡检查询）
```

**调度要点（确定性契约权威描述）**：
1. **确认门禁**：`wf_sub_01` 输出方案即止于确认点，未命中确认语义前不调度 `wf_sub_02`；后端 `save_product_config` 不再校验 confirmed/CONFIRMED 标记。`submit_release_approval` 仍硬校验四环节结果齐全（跳步必被拒）。
2. **逐环节步进推进纪律**：02→03→05→04 严格按序，严禁并行/跳过；每环节必须得到用户反馈才调度下一环节，仅失败/异常中断；环节成败由子流/代码节点按出参字段裁决（status/pass/test_passed）。
3. **结果存储**：wf_sub_02~05 各内置 save_node_result（req_id=入参，node_name=config/spec/fee/test），成功即自动落库。
4. **续跑**：`重新执行`按 fail_node 从失败环节续调（STAGE1_CONFIG→wf_sub_02、STAGE2_AUDIT→wf_sub_03（发起，结果由页面巡检续查）、STAGE3_FEE→wf_sub_05、STAGE4_TEST→wf_sub_04（发起，结果由页面巡检续查）），已成功环节按存储回放不重复调用写接口。
5. **结果获取（V3.13 页面自动巡检）**：环节4 稽核结果、环节6 测试结果、审批进度由**配置工作台页面自动巡检**（前端轮询 `result/query` + 按需调后端查询端点），智能体**不直调** `wf_sub_12`/`wf_sub_13`/`wf_sub_08`；监控结果由智能体直调 `query_product_monitor`（`wf_sub_07`）。
6. **两个二次确认点**：需求工单审批（提报单就绪止于确认点，用户回复【发起需求审批】后 `wf_sub_11` 发起）、上线审批（执行主干全成后用户确认发起）。

**结果打印/异常处置/成功汇总三套模板（LLM 输出统一格式）**：
> **模板裁决纪律**：仅当子流出参明确返回失败项（`error_list` 非空 / `pass==0` / `test_passed==未通过`）时方可使用【❌ 执行异常】模板；出参为空或全部通过时**必须**使用【✅ 执行成功】模板，且不得凭空补出任何失败项或统计表。
```
【环节N/8·{名称}】✅ 执行成功
- 关键数据：{该环节关键输出（offer_id/save_result、audit_summary、compare_list、测试统计+受理验证
  orderId/offerInstId）}
- 下一步建议：确认后可继续进入【下一环节】——请回复【下一步】/【继续】推进（末环节改为输入"发起审批"）

【环节4/8·规格稽核】✅ 执行成功（pass==1 固定格式，禁止出现失败项；由页面自动巡检取回结果）
- 稽核结论：{逐字引用页面巡检已落库的 audit_summary，如"全部销售品稽核通过（mock）"}
- 稽核明细：{逐字引用页面巡检已落库的 audit_suggest 七项清单，全部 ✅；禁止自造总数/通过数/失败数}
- 下一步建议：请回复【下一步】/【继续】进入【环节5/8·资费校准】

【环节N/8·{名称}】❌ 执行异常（仅在出参确有失败项时使用）
- 异常原因：{resultCode/resultMsg 或 pass=0 摘要}
- 关键明细：{error_list/risk_list/失败测点/超时信息，逐字引用出参}
- 整改建议：{依据出参生成的建议}
请选择下一步：① 回复【重新执行】从失败环节继续（已成功不重复）；② 回复【修改执行方案】重新生成并确认

【执行主干全部完成】✅ 共4个环节执行成功：
1. 智能配置：offer_id={...} 全部写入成功； 2. 规格稽核：通过 {audit_summary}；
3. 资费校准：通过，未发现叠加/互斥冲突； 4. 自动测试：场景N个测点M个全部一致；
   受理验证：orderId={...}，offerInstId={...}，各受理场景均通过。
是否发起上线审批？回复【发起审批】将提交审批流；回复【暂不】可稍后继续。
```

**工作台页面自动巡检层（配置工作台前端，免发查询词，V3.13 起为结果获取主通道）**：

> **口径变更（V3.13）**：环节4 稽核结果、环节6 测试结果、审批进度的获取**由 LLM 触发查询子流改为前端页面自动巡检**——对话主链路不再由 LLM 直调 `wf_sub_12`/`wf_sub_13`/`wf_sub_08`，用户也无需回复查询类关键词；页面打开相应页签即自动轮询取结果并翻转。

- **页签自动翻转**：配置工作台打开相应页签时，前端按 `stage` 推断该页签对应的 pending 节点（审批类见 `report`、稽核见 `audit_pending`、测试见 `test_pending`），并轮询 `GET /api/v1/appstore/result/query?req_id=&latest_only=1`；同时按需调用后端查询端点（稽核 `/audit/sql/query`、测试 `/test/offer/progress`+`/test/offer/result`、审批查询端点）完成异步轮询；拉取到最终结果（`spec`/`test`）或 `report` 节点即把当前页签由「进行中」翻转为「完成」，**只翻转当前页签、不自动跳转**。
- **落库键门禁**：页面巡检到稽核 `pass==1`/测试 `test_passed==通过` 时，后端按 `req_id` 落 `spec`/`test` 键；环节5/环节7 才允许推进（门禁口径不变，仅"取结果"由后端+页面完成，不再经 LLM 查询子流）。
- **兜底**：5s 内无新产出则按兜底判定一次，避免长轮询空转；翻转后停止轮询。
- **口径**：前端与后端仅做呈现层翻转与结果落库（模拟），不改工作流结构与契约；`approval_nodes` 仍来自 URL，`result/query` 只更新 `nodeResults` 不更新 `__WB_QUERY`。

### 5.2 14 个子工作流清单（当前实现基线）

> 位于 `场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/`，由 `gen_workflows.py` 确定性生成（禁止手改 JSON），共 **14 个 JSON**（`wf_sub_00`~`wf_sub_13`）。各子流统一 **req_id 单必填入参**，内部 query_node_result 自查上游 + 结束前 save_node_result 落库；确定性逻辑内嵌 type=6 代码节点。**V3.10 智能稽核拆为发起流 `wf_sub_03`（4a）+ 结果取回（4b，页面自动巡检，后端查询承载）；自动测试拆为发起流 `wf_sub_04`（6a）+ 结果取回（6b，页面自动巡检，后端查询承载）。V3.13 起环节4/6 结果与审批进度由配置工作台页面自动巡检获取，智能体不直调 `wf_sub_12`/`wf_sub_13`/`wf_sub_08`（其仍保留为后端查询承载脚本）。V3.11 需求文档解析**不由工作流承担**（工作流 start 节点无法接收文件/附件），改由智能体挂载 skill 解析为结构化文本后作为 `requirement_text` 传入 `wf_sub_00`。**

| 子工作流 | 编码 | 入参 | 关键链路 / 代码节点 |
| --- | --- | --- | --- |
| 需求提报（环节1/8，止于确认点） | `wf_sub_00` | requirement_text | LLM 要素提取（上传 .xlsx 时其结构化文本由智能体 skill 前置解析后作为 `requirement_text` 传入，可含多品）→ CODE_RENDER_REQ 渲染提报单+req_id 生成（**一品多品时生成父 req_id + 子 offer_seq 清单**）→ save(node=requirement_report) → **止于确认点**（"是否发起【需求工单审批】？"，不直接发起审批） |
| 发起需求审批（环节1/8） | `wf_sub_11` | req_id | 自查 requirement_report → CODE_EXTRACT_RECORD 取原文 → submit_release_approval(approval-type=requirement) → LLM 回执渲染 |
| 需求分析（环节2/8，模板轨，止于确认点） | `wf_sub_01` | req_id | CODE_EXTRACT_RECORD → 相似查询(节点105) → CODE_GET_TEMPLATE(节点106) → 要素提取 LLM → CODE_VALIDATE_ELEMENTS(节点108 质量闸) → CODE_MERGE_NESTED(节点109) → CODE_OP_VALIDATE_NESTED(节点110 本体校验闸 /validate-nested) → CODE_RENDER_TABLE(节点111) → 保存 requirement → **输出方案止于确认点** |
| 智能配置（环节3/8） | `wf_sub_02` | req_id | 自查 requirement → CODE_EXTRACT_RECORD → save_product_config → CODE_FUSION_GROUP_ECHO 融合成员回显 → save(node=config) |
| 稽核发起（环节4a/8） | `wf_sub_03` | req_id, chat_id | 自查 config → CODE_EXTRACT_RECORD 取 offer_id → **`audit_file_upload`（B1：后端按 req_id 查需求与配置信息生成稽核 Excel 后上传，解析 sales_id）→ 逐品 `audit_async_notify`（B2）+ `audit_insert_result`（B3）** → 汇总 requestIds → save(node=**audit_pending**：requestIds/sales_id 清单) → **秒级返回"稽核已发起"止于反馈点**（不轮询） |
| 稽核轮询取结果（环节4b/8，页面自动巡检 · 后端查询承载） | `wf_sub_12` | req_id | 后端按 req_id 取 **audit_pending** 的 requestIds → **短窗口轮询 `audit_sql_query`（sqlId=20260626001，读 `isAllFinished`）** → 完成则 `audit_sql_query`（sqlId=20260626002 取结果）→ 归一 error_list（含 group 类目）→ 整改建议 → **pass==1 才 save(node=spec)**；未完成页面续轮询（不写 spec）。**由配置工作台页面自动巡检触发，智能体不直调** |
| 资费校准（环节5/8，成员分组） | `wf_sub_05` | req_id | 自查 config → CODE_EXTRACT_RECORD → check_billing_rule(check_scene=all, member_role 分组) → 风险解读 → save(node=fee) |
| 自动测试（环节6/8，含受理验证） | `wf_sub_04` | req_id | 自查 config → **C1 `offer_test` 发起（取 globalId）→ C2 `get_test_scenes` 取场景集合** → save(node=**test_pending**：globalId/testScenes) → **秒级返回"测试已发起"+场景清单止于反馈点**（不轮询） |
| 测试轮询取结果（环节6/8，页面自动巡检 · 后端查询承载） | `wf_sub_13` | req_id | 后端按 req_id 取 **test_pending** 的 globalId → **短窗口轮询 C3 `get_test_progress`，读 done/failed/failIndex** → 完成则 C4 `get_test_result` → CODE_MAP_FIXED_CASES(节点315：31 条固定用例/九章节报告，受理验证逐场景) → CODE_DOWNLOAD_TEST_REPORT → **test_passed==通过 才 save(node=test)**；未完成页面续轮询（不写 test）。**由配置工作台页面自动巡检触发，智能体不直调** |
| 上线审批（环节7/8，双轨+推送即结束） | `wf_sub_06` | req_id | 串行自查 5 类结果 → CODE_SUMMARY_APPROVAL 汇总 → LLM 报告（node=report）→ submit_release_approval(approval-type=launch) → **推送即结束**（不轮询/不分流），返回 approval_id/status；审批状态由页面自动巡检呈现 | **返回 `approval_id` 为工单级单值（顶层），工单内多商品体现在 `offers[]`**
| 监控运维（环节8/8，异常分支） | `wf_sub_07` | offer_id / date_range | query_product_monitor → 异常 → send_alert → CODE_OP_ROOT_CAUSE(/ops/root-cause) → CODE_OP_CREATE_WO(/ops/work-orders) |
| 审批进度查询（辅助，双轨，页面自动巡检） | `wf_sub_08` | approval_id / offer_id | query_approval_status → LLM 状态摘要（**由页面自动巡检触发，智能体不直调**） | **查询按 `req_id` 返回该需求全量商品（一张审批单内含多商品），`offers[]` 各元素冗余携带同一工单级 `approval_id`；进度字段（status/current_node/approver/opinion）为工单级、各元素一致**
| 存量产品查询（辅助，只读） | `wf_sub_09` | 产品名称/ID | CODE_OP_QUERY_OFFER（内嵌 knowledge/存量产品目录_清洗后.json） |
| 存量合规扫描（辅助） | `wf_sub_10` | 存量范围 | CODE_OP_SHELF_COMPLIANCE(/shelf-compliance) |

> **稽核/测试结果落库门禁（V3.13 页面自动巡检）**：环节5 `wf_sub_05` 自查、`submit_release_approval` 四环节硬校验均以 `node_name=spec`/`node_name=test` 为准；`spec` 只在环节4b **页面自动巡检取回且 pass==1** 后写入、`test` 只在环节6b **页面自动巡检取回且 test_passed==通过** 后写入，故"稽核/测试未完成不允许推进下一环节"由落库键天然保证（`audit_pending`/`test_pending` 为临时态，不替代 `spec`/`test`）。

### 5.3 关键算法与提示词要点

1. **req_id 生成规则（统一键）**：由 `wf_sub_00` 内嵌代码节点 CODE_RENDER_REQ 以系统时钟生成 `PLAN` + yyyyMMddHHmmss + 3 位随机数（如 `PLAN20260913143025087`），LLM 不参与；会话内沿用最近值（上传 .xlsx 时 skill 只产出文本、不生成 req_id，req_id 仍由工作流生成）。后端硬校验：格式 `PLAN\d{17}`（非法 5002）；requirement 环节同键覆盖写（删除旧记录后重插）。**V3.10 一品多品父子键**：父 `req_id` 仍严格满足 `PLAN\d{17}`（后端校验不变，零改动）；同一需求文档的 N 个新增销售品以**子序号 `offer_seq`（1..N）** 区分，子键以 `req_id` + `offer_seq` 复合寻址（落库时以 `req_id` 为主键、`offer_seq` 内嵌于 `config_json`/`plan_json` 的 `offers[]` 元素），**不新增 req_id、不扩展后端校验**。详见 6.6 章 G2。
2. **字段体系与补全策略（3 模块/9 分类/24 字段）**：字段来源仅"原始需求/AI补全"两态；取值链——**原始需求 → 相似产品（最高相似度，标 AI补全）→ 本体默认值（引擎补全，标 AI补全）→ 待补充**，禁止跳级虚构。**仅套餐档位（价格）未提取到时维持"待补充"**（禁推理/禁照搬）；套餐编码恒填"系统待生成"（不计入待补充）；引擎对"待补充"字段按本体默认值推理补全（三类资源→"无"、适用地区→全国、计费周期→自然月等）。pending_fields 判定唯一事实源=引擎反查（value="待补充"），LLM 不自判。
3. **门禁体系**：① 方案确认门禁（智能体语义识别；后端 save_product_config 已移除 CONFIRMED 标记校验）；② 需求工单审批二次确认（`wf_sub_11` 承接）；③ 上线审批四环节硬校验（后端 latestRecord(req_id,"config"/"spec"/"fee"/"test") 全非空）；④ 待补充字段时**不保存执行方案、不产出 req_id**（从源头禁止进入智能配置）。
4. **测试进度轮询（环节6b 页面自动巡检 · 后端查询承载）**：由配置工作台页面自动巡检触发后端短窗口轮询，间隔 5s、单次窗口最多 60 次（5 分钟）；窗口耗尽非中断，页面续轮询；连续 5 次查询失败终止（E40c）；done=true 通过、failed=true 取完整结果供报告定位失败原因（E12）；整体超时（E13）；分支出参 `done/failed/failIndex/fail_reason`。
5. **31 条固定用例（CODE_MAP_FIXED_CASES 节点315）**：确定性构建 ACC-001~012 / BILL-001~010 / CUST-001~009（维度 ACC|BILL|CUST、等级 P0 拦截/P1 警告/P2 提示），+ 维度汇总 + 整体结论 + 缺陷清单 + 场景覆盖核对 + 被测一致性核对（E26）；LLM 按 K3 模板渲染九章节正式版报告（4.1 受理验证独立成节（**逐场景 orderId/offerInstId 结论**），4.2 计费验证，4.3 客服验证，等；`objTestSceneRel` 的 resultMsg/summaryDesc/suggestion 并入相应章节）；31 条清单禁止增删改，结果仅依出参判定。
6. **模型纪律**：LLM 节点固定 `model=qwen3-30b-a3b`、temperature=0.2、top_p=0.5、max_tokens=2048；提示词末尾"仅输出对应出参字段"；生成/汇总/解读场景"逐字引用输入数据，不新增结论"。

### 5.4 配置规范符合性要点（对齐《SitechAI开发平台配置规范.md》）

| 规范条款 | 本方案落点 |
| --- | --- |
| 源码驱动纪律 | 14 子工作流 JSON 全部由 `gen_workflows.py` 生成，禁止手改；改逻辑→改生成器→重新生成+全量校验 |
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
- **V3.10 需求文档复用先例**：新增四档 5G畅享融合套餐（900117029~032）与副卡功能费（7320110001600005）的**可选依赖关系**，与种子组 `GP900113046` 结构**完全同构**（主卡套餐 + 宽带 + 天翼高清 + 副卡功能费 `dependency=OPTIONAL_DEPEND`），故直接复用本机制，无需扩展融合品契约（6.6 章 G3/G4）。
- **plan_json 组结构**：`{offer_type ∈ {单品,融合}, main_offer{role,fields}, member_offers[{role,fields}], group_rules, pending_fields[{role,field}]}`；成员 fields 复用 24 字段注册表；**兼容性铁律**——入参不传组结构=单商品模式，行为与旧版完全一致，所有新增字段可缺席。
- **取值链（逐成员独立）**：原始需求（成员级）→ 相似融合品 offer_group → 本体默认值 → 待补充（仅各成员价格类）；价格禁止跨成员照搬。
- **引擎两轮推理**：① 成员内推理（复用 action=reason 逐成员）；② 组级校验（action=group_check：互斥/依赖/退订联动，输出 group_violations[]）。
- **工作流落点**：wf_sub_02 CODE_FUSION_GROUP_ECHO（六列表格/主成员加粗）；环节4 稽核结果（页面巡检取回）error_list 新增 `group` 类目；wf_sub_05 compare_list 每项新增 `member_role` 键；环节6 测试结果（页面巡检取回）组场景 S_GROUP_BIND/S_ADDON_SUB + E26 组核对。
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

### 6.6 需求文档（Excel）驱动与一品多品加载（V3.10 新增解析能力，V3.11 改为 skill 承载）

> **背景**：客户提供《新增四档5G畅享融合套餐销售品.xlsx》作为需求提报输入，要求产销品加载智能体支持该需求文档的加载流程（全流程 8 环节）。文档含三个 sheet：**可选包**（4 个新增销售品宽表，38 列）、**关联关系**（A/Z 端关系表）、Sheet3（空）。本节将该能力**最小化**并入现有方案，**业务逻辑/接口契约/8 环节口径零改动**。**V3.11 变更**：**工作流 start 节点无法接收文件/附件**，故文档解析**不由任何工作流承担**——改由**智能体挂载 skill** 解析 .xlsx 为**结构化文本**（多品清单 + 品间关联关系），再作为 `requirement_text` 直调 `wf_sub_00`（文本轨 LLM 抽取多品）。曾试行的"内嵌解析节点于 `wf_sub_00`"与"独立子流 `wf_sub_14`"两种工作流内解析方案**均已废弃**。

#### 6.6.1 需求文档结构（输入契约）

| Sheet | 结构 | 关键列 |
| --- | --- | --- |
| 可选包 | 4 行新增销售品（宽表，38 列） | `OFR_ID` / `OFFER_NBR` / 省级市级 / 付费模式 / 套餐客户化名称 / 套餐名称 / 基础包可选包加装包 / 是否主套餐 / 内容描述 / 一单清三栏 / 首月次月计费 / 销售停售时间 / … / 账单展示套餐内外（语音/流量/短信/彩信/接听免费范围/其他） |
| 关联关系 | A/Z 端关系行 | `A_OFR_ID` / `A_OFR_NAME` / 关系 / `Z_OFR_ID` / `Z_OFR_NAME`；关系枚举：`100000` 可选依赖 / `110000` 强制依赖 / `120000` 默认依赖 / `130000` 赠送 / `200000` 互斥 / `410000` 不可迁转 / `100100` 自主版 |
| Sheet3 | 空（占位，忽略） | — |

**四档数据**（同一需求文档，均对 `7320110001600005` 形成 1 条**可选依赖**；销售时间 2025-12-10，停售 2027-12-31；付费模式 HB；账目类型 60667 天翼套餐月使用费）：

| 序号 offer_seq | OFR_ID | 套餐名称 | 流量 | 语音 | 宽带 | 套外流量 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 900117029 | 5G畅享融合79元套餐202512 | 20GB | 100 分钟 | 300M | 0.05 元/MB |
| 2 | 900117030 | 5G畅享融合99元套餐202512 | 40GB | 150 分钟 | 500M | 0.03 元/MB |
| 3 | 900117031 | 5G畅享融合139元套餐202512 | 60GB | 200 分钟 | 1000M | 0.03 元/MB |
| 4 | 900117032 | 5G畅享融合169元套餐202512 | 80GB | 300 分钟 | 1000M+FTTR | 0.03 元/MB |

#### 6.6.2 差异分析与设计决策（4 项已确认）

| # | 差异（gap） | 设计决策 |
| --- | --- | --- |
| G1 🔴 | `wf_sub_00` 仅接收 `requirement_text`（生成器 498 行），LLM 翻译为**平面字段**，**无 Excel/表格解析**；且**工作流 start 节点无法接收文件/附件**（`requirement_file` 无法作为工作流入参落地） | **后端提供解析端点** `/api/v1/appstore/requirement/parse-xlsx`（2e 工具，与 B1 稽核上传同模式：multipart 上传 → 解析返回结构化需求，已实现）；**V3.11 由智能体挂载 skill（`ah_cti_poc/skills/requirement-doc-parse/`，内嵌解析）** 将文档解析为结构化文本后作为 `requirement_text` 直调 `wf_sub_00`；**工作流层不接收文件、不含解析节点**，失败由智能体按 E41 引导 |
| G2 🔴 | 现方案 req_id **单品粒度**（单键 → 单一 plan_json/config_json/spec/fee/test），无法承载"1 份需求 → 4 个销售品" | **父/子键**：1 份需求 = 1 个父 `req_id`（严格满足 `PLAN\d{17}`，后端校验零改动）+ N 个子序号 `offer_seq`（1..N）；`plan_json`/`config_json` 承载 `offers[{offer_seq,...}]` 数组，代码节点内**循环**逐品处理，各环节结果按 req_id 单键落库（组结构内嵌） |
| G3 🟠 | 现仅有**组内成员关系**，品间关联关系（A/Z 端）无承载机制 | **复用 `group_rules` 组内依赖**：关联关系表的"可选依赖"↔ `dependency=OPTIONAL_DEPEND`（先例 `GP900113046` 副卡功能费）；"互斥/强制依赖/默认依赖"分别映射 `group_rules.互斥/依赖`，不新增契约 |
| G4 🟢 | 融合结构是否需扩展 | **无需扩展**：`GP900113046` 与本次需求（主卡套餐 + 宽带 + 天翼高清 + 副卡功能费可选依赖）**结构完全同构**，四档直接复用现有融合机制（6.1 章）；`seed_offer_groups.json` 新增 4 组 `GP900117029`~`GP900117032`（可选） |

**字段宽度归属（G5）**：文档 38 列多数**不在 24 字段注册表**内，按三类归属处理——① **映射入 24 字段**（套餐名称/价格档位/套内流量语音/付费模式等，走模板轨 6.2）；② **落模板 schema**（账单展示套餐内外/一单清三栏/首月次月计费等模板叶子路径，经 CODE_MERGE_NESTED 对位）；③ **冗余入报告**（销售停售时间/省级市级/内容描述等展示类，随 `plan_md`/报告输出，不入 24 字段）。

**稽核 Excel 独立生成（G6）**：环节4 稽核清单 Excel **不再复用需求输入文档**——B1 `audit_file_upload` 改为后端按 `req_id` 查需求与配置信息**自行生成稽核 Excel** 后上传（入参仅 `req_id`+`chat_id`，无前端文件）。

**知识资产补充（G7，可选）**：4 个新销售品（900117029~032）**不在任何知识资产/种子**中；`900117020~027` 已被权益随心选占用，029~032 不冲突。建议同步补 `存量产品目录`/K4/K5 可选条（不强制，不影响主链路）。

#### 6.6.3 全流程 8 环节的适配落点

| 环节 | 适配落点 | 说明 |
| --- | --- | --- |
| 1 需求提报 | 智能体 skill（解析）+ `wf_sub_00`（提报）+ 端点 2e | 上传 .xlsx → **智能体挂载 skill** 解析为 `offers[]`（4 品）+ `relations[]` 的结构化文本（工作流不接收文件）；skill 产物作为 `requirement_text` 直调 `wf_sub_00`：LLM 抽取 → CODE_RENDER_REQ 渲染提报单为**四档清单表格**（含关联关系），父 req_id + offer_seq 清单；止于确认点 |
| 1' 需求工单审批 | `wf_sub_11` | 无改动（requirement 轨，父 req_id） |
| 2 需求分析 | `wf_sub_01` 模板轨 | 逐品（offer_seq 循环）跑模板轨六步，融合组场景走 6.1 机制；生成含 4 品的 `plan_json{offers[],group_rules}`；止于确认点 |
| 3 智能配置 | `wf_sub_02` + CODE_FUSION_GROUP_ECHO | 循环逐品 `save_product_config`，`CODE_FUSION_GROUP_ECHO` 回显融合成员（四档主卡 + 副卡 7320110001600005） |
| 4 规格稽核 | `wf_sub_03`（发起）+ 页面自动巡检（后端查询承载） | 需求文档可**直接作为稽核 Excel**（B1 上传），逐品异步通知；结果由**配置工作台页面自动巡检**取回（前端轮询 `result/query` + 后端 `/audit/sql/query`）；`error_list` 含 group 类目 |
| 5 资费校准 | `wf_sub_05` | 成员分组（member_role）逐品校验，`compare_list` 含 member_role |
| 6 自动测试 | `wf_sub_04`（发起）+ 页面自动巡检（后端查询承载） | 逐品发起 → 结果由**配置工作台页面自动巡检**取回（前端轮询 `result/query` + 后端 `/test/offer/progress`、`/test/offer/result`）；组场景 S_GROUP_BIND/S_ADDON_SUB + E26 组核对（副卡可选依赖） |
| 7 上线审批 | `wf_sub_06` | 父 req_id 汇总四品结果，四环节硬校验（config/spec/fee/test 齐全）后推送 |
| 8 监控运维 | `wf_sub_07` | 逐品（offer_id）监控，无结构改动 |

#### 6.6.4 影响面与纪律边界

- **新增**：后端 1 个解析端点（`/requirement/parse-xlsx`，2e，**已实现；智能体侧另有内嵌解析 skill 可脱端点运行**）；**智能体已挂载 1 个文档解析 skill**（`ah_cti_poc/skills/requirement-doc-parse/`，.xlsx → 多品结构化文本，工作流外）；`wf_sub_01/02` 内"逐品循环"逻辑（type=6 节点内嵌循环，平台无循环节点约束不破）；异常 E41（需求文档解析失败，由智能体引导）。
- **零改动（工作流层）**：**无任何工作流 JSON 改动**（已回退试行的 wf_sub_14 与 wf_sub_00 双轨改造，恢复 14 子流基线）；req_id 后端校验（`PLAN\d{17}`）、接口契约、24 字段注册表、8 环节编号、审批四环节硬校验、步进式人机协同、两个二次确认点、落库键语义（`spec`/`test` 仅完成后写入）全部不变。
- **可选补充**：`seed_offer_groups.json` 新增 `GP900117029`~`GP900117032` 四组（复用 `GP900113046` 结构）；`存量产品目录`/K4/K5 补 4 品资料。
- **落点**：① **智能体层**——**已挂载文档解析 skill**（`ah_cti_poc/skills/requirement-doc-parse/`，内嵌解析，可选调 2e 端点），提示词补充"上传 .xlsx 时先经 skill 解析，再作为 `requirement_text` 调 `wf_sub_00`"；② **工作流层**——`wf_sub_00` 保持单轨（`requirement_text` + LLM 抽取 + CODE_RENDER_REQ）；③ 后续若需多品循环，由 `CODE_FUSION_GROUP_ECHO`（1231 行附近）与 `wf_sub_02` 融合扩展段（1423 行附近）支持 `offers[]` 循环。**（V3.11：工作流层不承载文档解析，无需改生成器；试行的 wf_sub_14 方案已废弃并回退。）**

#### 6.6.5 多商品支撑方案（V3.11 补充）

> 本节回答："14 个子工作流**是否**支持多商品、**如何**支撑"。结论：**在现有 14 子流基线上，通过"父/子键 + 内嵌循环"即可支撑一品多商品，无需新增子流、不改契约。**

##### 6.6.5.1 架构约束（为什么不能靠子流粒度解决）

14 个子工作流均为**单商品粒度**，源于两点硬约束：

1. **落库寻址单值槽位**：节点结果存储以 `req_id + node_name` 复合主键（`pd_ai_node_results` 表，见 7.1 部署项7），每个 `node_name`（`plan_json`/`config_json`/`spec`/`fee`/`test`）对同一 `req_id` 仅存**一份**，天然只能装"1 份需求"的结果。
2. **`offer_id` 为单标量**：各环节接口以单个 `offerId` 为业务主键（如测试发起 `{offerId}`、监控 `product/monitor`），工作流节点内 `offer_id` 变量为单值。

因此多商品**不能**通过"一个商品一条子流实例"承载（会导致 `req_id` 分叉、审批无法汇总、契约改动大）。**统一解法**：把"多商品"下沉到**数据与代码节点循环**两层，子流粒度不变。

##### 6.6.5.2 数据模型：父 `req_id` + 子 `offer_seq`

| 层级 | 键 | 说明 |
| --- | --- | --- |
| 需求层（父） | `req_id` | 1 份需求文档 = 1 个父 `req_id`，严格满足 `PLAN\d{17}`（后端校验零改动） |
| 商品层（子） | `offer_seq` | 需求内商品序号，1..N（与解析产物 `offers[].offer_seq` 一致），显式编号不依赖数组序 |
| 复合载体 | `offers[]` | `plan_json`/`config_json` 内嵌 `offers[{offer_seq, ofr_id, offer_name, fields{}}]` 数组；组关系入 `group_rules` |
| 逐品落库 | `req_id + node_name` | 每阶段仍单键落库，**组结构内嵌**于该键内（即"父键承载 N 品结果数组"） |

即：**父键管"这份需求"，子序号管"第几品"**；所有下游接口按品调用时，从 `offers[]` 取当前 `offer_seq` 对应子对象展开为单品参数。

##### 6.6.5.3 逐品循环实现纪律（type=6 内嵌 for）

平台**无独立循环节点**（仅 `type=6` 代码节点可在节点内写 `for`），故所有"逐品"均须在代码节点内以**内嵌循环**实现：

- **循环输入**：上游 `offers[]` 数组（来自 6.6 skill 解析产物或 `plan_json`/`config_json`）。
- **循环体**：对每品执行该环节的调用/渲染，产出结果写入 `results[offer_seq]`。
- **循环输出**：聚合为单键结果（如 `spec_results[]`、`fee_results[]`），仍以父 `req_id` 落 `node_name`。
- **失败隔离**：单品失败记录该 `offer_seq` 明细，不影响其余品；整体通过性 = 全部品通过（如 `spec` 仅当 N 品全 pass 才写入）。
- **节点 type 合规**：仅使用 0/1/2/3/6/9/13；循环不引入新 type。**生成器确定性生成**（`gen_workflows.py`），禁手改 JSON。

##### 6.6.5.4 逐子流支撑矩阵（14 子流）

| 子流 | 环节 | 多品支撑方式 | 改造量 |
| --- | --- | --- | --- |
| `wf_sub_00` | 需求提报 | LLM 抽取 + CODE_RENDER_REQ 改为渲染 `offers[]`（四档清单 + 关联关系） | 逻辑扩展 |
| `wf_sub_01` | 需求分析 | 模板轨六步内嵌 `offers[]` 循环逐品跑；生成含 N 品的 `plan_json{offers[],group_rules}` | 内嵌循环 |
| `wf_sub_02` | 智能配置 | 内嵌循环逐品 `save_product_config`；**风险**：`CODE_GEN_OFFER_ID`（节点111）单值，需扩展为逐品生成唯一 offerId | 内嵌循环 |
| `wf_sub_03` | 规格稽核发起 | 逐品组装 `sales_id` 集合并一次性发起（请求集合逗号拼接） | 内嵌循环 |
| `wf_sub_04` | 测试发起 | 逐品发起后汇总 `globalId`（多品 → 多个 globalId） | 内嵌循环 |
| `wf_sub_05` | 资费校准 | 逐品校验，`compare_list` 含 `member_role` 分组 | 内嵌循环 |
| `wf_sub_06` | 上线审批 | 按品校验，父 `req_id` 汇总 N 品四环节结果（config/spec/fee/test 齐全）后推送 | 逻辑扩展 |
| `wf_sub_07` | 监控运维 | 逐品（`offer_id`）监控聚合 | 聚合/可选 |
| `wf_sub_08` | 审批进度查询 | 旁路只读（页面自动巡检触发），天然支持 | 零改造 |
| `wf_sub_09` | 存量查询 | 旁路只读 | 零改造 |
| `wf_sub_10` | 存量合规辅助 | 旁路只读 | 零改造 |
| `wf_sub_11` | 需求工单审批 | 父 `req_id` 天然支持 | 零改造 |
| `wf_sub_12` | 规格稽核结果取回 | 父 `req_id` + 子 `offer_seq`（结果按 `requestIds` 归集）；页面自动巡检触发 | 内嵌循环 |
| `wf_sub_13` | 测试结果取回 | 父 `req_id` + 子 `offer_seq`（结果按 `globalId` 归集）；页面自动巡检触发 | 内嵌循环 |

##### 6.6.5.5 分环节推进顺序（多品的时序纪律）

多品不改变 8 环节步进式人机协同，仅将每环节内部由"单品一步"变为"逐品 N 步后聚合"：

1. **提报（环节1）**：skill 解析出 `offers[]`（序号 1..N）→ `wf_sub_00` 渲染 N 档清单 → **止于确认点**（一次确认覆盖 N 品）。
2. **需求审批（1'）**：`wf_sub_11` 按父 `req_id` 发起（N 品共用一张工单）。
3. **分析（环节2）**：`wf_sub_01` 逐品跑模板轨 → 合并为单份 `plan_json{offers[]}` → 止于确认点。
4. **配置（环节3）**：`wf_sub_02` 逐品 `save_product_config`。
5. **稽核（环节4）**：`wf_sub_03` 逐品组装 `sales_id` → 一次性发起 → 页面自动巡检取回逐品结果；`error_list` 含 group 类目。
6. **资费（环节5）**：`wf_sub_05` 逐品校验（member_role 分组）。
7. **测试（环节6）**：`wf_sub_04` 逐品发起 + 页面自动巡检取回逐品结果；组场景 S_GROUP_BIND/S_ADDON_SUB + E26 组核对。
8. **审批（环节7）**：`wf_sub_06` 汇总 N 品四环节齐全后推送。

##### 6.6.5.6 关键风险与对策

| 风险 | 说明 | 对策 |
| --- | --- | --- |
| `CODE_GEN_OFFER_ID` 单值碰撞 | `wf_sub_02` 节点111 单值生成，多品易碰撞 | 扩展为逐品生成，以 `offer_seq` 参与种子保证唯一 |
| 无循环节点 | 平台仅 type=6 内嵌 for | 循环全部内嵌代码节点，禁引入新 type；`gen_workflows.py` 确定性生成 |
| 单品失败污染整体 | 任一品失败不应误判其余品 | 逐品结果隔离 + 失败明细带 `offer_seq`；写入性键（spec/fee/test）以"全品通过"为门禁 |
| 出参键越界 | 聚合数组键须 ⊆ 代码节点返回键 | 聚合键（如 `spec_results[]`）显式纳入节点返回；出参 ⊆ 代码节点返回键纪律不破 |
| 多 globalId/requestIds 归集 | 稽核/测试异步结果需按品对齐 | 以 `offer_seq`/`sales_id`/`requestId` 为关联键归集，落库仍父键单键 |
| 后端幂等 | 多品重跑可能重复写 | 各环节自查 `req_id + node_name` 已存成功记录→回放跳过（E19），逐品幂等 |

##### 6.6.5.7 验证口径

以《新增四档5G畅享融合套餐销售品.xlsx》（4 品 + 副卡 `7320110001600005` 可选依赖）为端到端样本：skill 解析 → `wf_sub_00` 渲染四档提报单 → 四档逐品分析/配置/稽核/资费/测试 → 副卡可选依赖组核对（E26）→ 上线审批汇总。**验收点**：① 4 品 `offer_seq` 均落库于同一父 `req_id`；② 各写入键仅当 4 品全通过才写入；③ 单品故障时明细可定位且不阻断其余品。

### 6.7 监控运维输出格式规范（wf_sub_07）

> **背景**：实跑发现环节8（`wf_sub_07` 监控运维）两类输出偏差——① **正常分支**运营摘要为裸串（LLM 将入参按其出现顺序原样斜杠拼接，如 `5G-A套餐199元/287/0/0.0020/[]`），非自然语言简报；② **异常分支**结束节点直接内嵌 `{offers}` 原始 JSON，且根因报告段落出现**模板占位符原样泄漏**（`{anomalies 摘要…}`、`{swrl_fired / applied_rules…}`、`{action_list 逐条引用…}`）、"为空显示\"无\""等**括注指令泄漏**。根因为设计层**未对监控输出给出格式与纪律约束**，提示词无格式规范，故本节点为**设计补缺**（非实现缺陷）。

**输出纪律（三条铁律）**：

1. **自然语言简报，禁止裸串/裸 JSON**：无论正常或异常分支，面向用户的正文一律为**中文自然语言**。禁止将入参以斜杠/逗号原样拼接成"值串"，禁止在正文内直接内嵌原始 JSON 数组（`offers` 等仅供 LLM 阅读理解，不得原样回显）。
2. **占位符与括注零泄漏**：提示词中形如 `{...}` 的字段引用说明、`（为空显示"无"）`一类渲染指令，**必须在生成前被实际数据替换**；输出正文不得残留任何 `{` `}` 占位或"为空显示…"括注。空列表一律渲染为"无"，空字段一律渲染为"未登记"。
3. **不编造**：根因/优化方案段落一律依据出参逐字引用；后端占位（`backend_pending=1`）时以占位说明原样呈现，禁止自造根因/证据/建议。

**正常分支（节点710 → 结束711）格式**：一段运营摘要，含——产品名称、订单量（含趋势）、异常量（含趋势）、计费差错率（含趋势）、告警情况；**多商品时逐品各一行**；结尾给出"运行正常，无需处理"一类的运营结论。示例：

```
5G-A套餐199元：订单量 287（上升），异常量 0（持平），计费差错率 0.20%（持平），无告警。整体运行正常，无需人工干预。
```

**异常分支（节点709）格式**：依次为——① 环节标题头；② 逐品运行监控明细（**以列表逐行呈现各商品指标，禁止内嵌 JSON**）；③ 告警结论（含告警单号）；④ 根因推理链+优化方案正文（依据出参逐字引用/占位说明，**不得含占位符**）；⑤ 处置工单（工单号 + 占位说明）；⑥ 建议处理；⑦ 页面面板。示例：

```
【环节9/9·监控运维】✅ 执行成功

【销售品运行监控】900102306（统计周期：最近7天）
- 产品名称：5G-A融合套餐199元
- 订单量：289（下降）　异常量：3（上升）　计费差错率：1.20%（上升）
- 告警：无

已推送告警，告警单号 AL202609272347400001

【逐品运行监控明细】
- 5G-A融合套餐199元：订单量 289（下降），异常量 3（上升），计费差错率 1.20%（上升），无告警

【异动根因推理链路】
- 异动确认：error_count 由基线上行 3，fee_error_rate 0.0120 超阈值
- 归因路径：已确认异动但未命中归因规则（依据出参 message 逐字引用）
- 命中规则：无
- 证据三元组：无

【优化方案】
根因本体推理（ops_root_cause）端点暂不可达或未命中归因规则；当前仅告警，未归因

**处置工单已建立（持续闭环）**：工单号 WO1790524061823

> **建议处理：** 已推送告警并建立处置工单，建议按优化方案执行后回复【查询监控】回检工单状态

<xsbot-panel …>
```

**落点**：`gen_workflows.py` 中 `wf_sub_07` 节点710 prompt 补格式规范；节点707 prompt 明确"禁止输出 `{…}` 占位符，字段为空时省略该行或写"无"；异常分支另设**汇总渲染**（不再由结束节点直嵌 `{offers}` 原始 JSON，改由逐品列表文本承载）。异常节点709 结束模板同步去除括注指令、`{offers}` 裸 JSON。

#### 6.7.1 业务编码 ↔ 图谱 OfferingId 对齐设计

> **背景**：监控与根因链路存在**两套编码体系**——① appstore 种子/监控侧以**业务编码**（`900xxx`，如演示异常品 `900102306=5G-A融合套餐199元`）标识商品；② 本体异动图谱 `ops_graph_export.json` 以 **offeringId**（`OF-*`）为 opsGraph 节点键。根因推理 `POST /api/v1/appstore/ops/root-cause` 仅按图谱 `opsGraph` 键归因，故传入 `900xxx` 时 `resolveOfferingId` 无法解析，返回 `success:false`（`backend_pending=1`），第五幕"根因"段落无法输出真实归因。

**对齐设计（在架编码即图谱键）**：

1. **统一节点键**：根因所涉商品，其在 appstore 种子/监控侧的**业务编码**即作为 `ops_graph_export.json` 中 `opsGraph` 与 `shelfOfferings` 的**节点键**（offeringId）。因 `resolveOfferingId` 首要按**精确编码**匹配（`shelf`/`opsGraph` 任含即命中），业务编码与图谱键一致即天然对齐，无需额外别名桥接。
2. **节点完整性**：对齐商品须在 `opsGraph`（提供异动事实 metrics/channels/promotions/competitors/behaviors/market）与 `shelfOfferings`（提供 `offeringId`+`offeringName` 回显）中**均存在**；缺一则 `analyzeRootCause` 返回"未找到商品异动图谱节点"。
3. **不复用 OF-* 覆盖**：不使用"业务编码→现有 OF-* 节点"的别名映射——否则回显商品名（如 `家庭融合畅享128`）与演示品名不符；直接以业务编码建节点，产品名以 `shelfOfferings.offeringName` 为准，与监控侧一致。

**演示映射（POC 基线）**：新增 `900102306`（`5G-A融合套餐199元`）作为 `opsGraph`+`shelfOfferings` 节点，携带异常事实（收入/留存异动、渠道/促销/竞品/行为佐证），供第五幕异常归因演示；正常品 `900102308` 由监控服务（`OfferSimV16Service`）按种子数据直接输出正常摘要，**无需**图谱节点。

**落点**：`ops_graph_export.json`（新增 `900102306` 的 `opsGraph` 节点与 `shelfOfferings` 记录）。`OntologyGraphManager.resolveOfferingId` / `OpsRiskAuditEngine.resolveOfferingId` 既有精确编码匹配逻辑**无需改动**。

### 6.8 新增四档 5G 畅享主套餐（销售品全量加载，V3.12）

> **背景**：在 6.6（融合口径，`5G畅享融合79/99/139/169元套餐202512`）已验证的基础上，客户进一步明确**本轮销售品按"主套餐"产品类型加载**，四档须作为**独立主套餐销售品**全量完成加载配置（提报→分析→配置→稽核→资费→测试→审批→监控全 8 环节销售品全部完成加载）。本节**新增记录**四档主套餐规格与加载口径，**6.6 现有融合表述保留不变**，两者互为"融合套装"与"主套餐单品"口径。

#### 6.8.1 四档规格（销售品加载基线）

| 序号 offer_seq | 档位 | 产品类型 | 套内流量 | 套内语音 | 套外流量 | 套外语音 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 79 元档 | 主套餐（个人主套餐） | 20GB | 100 分钟 | 3 元/GB | 0.15 元/分钟 |
| 2 | 99 元档 | 主套餐（个人主套餐） | 40GB | 150 分钟 | 3 元/GB | 0.15 元/分钟 |
| 3 | 139 元档 | 主套餐（个人主套餐） | 60GB | 200 分钟 | 3 元/GB | 0.15 元/分钟 |
| 4 | 169 元档 | 主套餐（个人主套餐） | 80GB | 300 分钟 | 3 元/GB | 0.15 元/分钟 |

**统一规则（四档一致）**：

- **计费周期**：按自然月计费；**生效方式**：次月 1 日生效；**有效期**：12 个月。
- **套外资费**：流量 3 元/GB、语音 0.15 元/分钟（**不再按 6.6 的元/MB 表述计**，以本节元/GB、元/分钟为准）。
- **办理渠道**：营业厅、APP 两渠道均可办理。
- **档位差异**：仅套内流量/语音与月费档位不同，套外与规则四档一致。

#### 6.8.2 加载口径与落点

- **产品类型判定**：`wf_sub_01` 产品识别（6.2 模板轨①）→ `product_type=主套餐` → 路由 `personMainPrc` 模板；四档**逐品独立**走模板轨，不入融合组（与 6.6 融合组口径区分）。
- **多品载体**：复用 6.6.5 **父 `req_id` + 子 `offer_seq`（1..4）**，`plan_json{offers[]}` 承载四档；逐品循环、逐品落库、失败隔离口径不变。
- **首调守卫（V3.12）**：会话**尚无 `req_id`** 时，首条需求消息**必须先调 `wf_sub_00`（需求提报）**，严禁越级直调 `wf_sub_01`（需求分析）；拿到提报单确认点后再调 `wf_sub_01`。见 §2.2【限制】第 12 条。
- **全量加载确认**：四档均须完成 8 环节，**销售品全部加载配置**（`config`/`spec`/`fee`/`test` 四环节硬校验齐全，缺一不推送审批）。
- **冒烟语料**：`我要上新四档 5G 畅享主套餐（79/99/139/169 元），请先提报需求再生成加载方案` → 首调 `wf_sub_00`。

#### 6.8.3 与 6.6 的口径区分

| 维度 | 6.6（融合口径） | 6.8（主套餐口径，本节） |
| --- | --- | --- |
| 套餐名称 | 5G畅享融合79/99/139/169元套餐202512 | 5G 畅享主套餐 79/99/139/169 元档 |
| 产品类型 | 融合（主卡套餐 + 宽带 + 高清 + 副卡可选依赖） | 主套餐（个人主套餐，单品） |
| 组结构 | 入融合组 `GP900113046` 同构 | 逐品独立，不组组 |
| 套外流量 | 元/MB（0.05/0.03） | 3 元/GB |
| 套外语音 | — | 0.15 元/分钟 |

---

## 7. 部署、命名约定与异常处理

### 7.1 部署清单（逐项核对）

| # | 部署项 | 配置值 |
| --- | --- | --- |
| 1 | 工作流 JSON | 14 个子工作流（`工作流配置/智能体工作流集V1.6/`，含稽核发起 wf_sub_03 + 结果取回 wf_sub_12、测试发起 wf_sub_04 + 结果取回 wf_sub_13，后二者由页面自动巡检触发），`gen_workflows.py` 生成，ALL_OK |
| 2 | 智能体提示词 | 3.2 意图→工作流映射表（**结果获取由页面自动巡检，不直调 wf_sub_12/13/08**）+ 确认语义识别 + 步进式纪律 + 超范围拒答；**另需单独配置「产销品数字员工」提示词（2.4.1），数字员工独立配置并关联产销品加载智能体** |
| 3 | 子工作流节点 | 代码节点（CODE_*）与插件端点逐条核对（wf_sub_01 节点106~113、wf_sub_04/13 测试发起/取回、wf_sub_03/12 稽核发起/取回，节点315） |
| 4 | knowledge/ 知识库 | K1~K5 + K5存量报文 + ontology-fields + seed_offer_groups + 存量产品目录_清洗后 |
| 5 | 生成器 | `gen_workflows.py`（代码节点内嵌 CODE_* 逻辑，固定用例与九章节模板内嵌一致） |
| 6 | 网关地址 | `BASE_URL=http://10.86.13.201:31281`（替换真实实现仅改此值） |
| 7 | 后端依赖 | 13 工具模拟服务 + 8 适配端点（AppStoreV16Controller）+ NodeResultService（pd_ai_node_results 表）；**V3.9 新增** InterfaceFacade（mock/real 策略）+ 智能稽核四接口适配（`/audit/file/upload`、`/audit/async/notify`、`/audit/result/insert`、`/audit/sql/query`）；**V3.10 新增** 需求文档解析端点 `/requirement/parse-xlsx` |
| 8 | 接口模式配置 | `application.yml` 新增 `prodai.appstore.interface.*`：`mode=mock`（开发，返回模拟报文）/`real`（现场，直连真实接口）+ `eop.*`（base-url/app-key/dst-sys-id/svc-code/sign/upload-file-field/timeout）+ `poll.audit.*`/`poll.test.*`（见 4.5.4；分环境由 application-dev.yml / application-prod.yml 覆盖） |
| 9 | 模型纪律 | 温度 0.2；出参逐字引用不加工 |
| 10 | 环境 | 平台/Agent 运行时加载 14 JSON + knowledge/ + 后端服务 + 挂载文档解析 skill |

### 7.2 变量命名约定

| 风格 | 适用 | 示例 |
| --- | --- | --- |
| 小写下划线 | 节点变量/入出参键/JSON 键 | `req_id`（统一键）、`plan_json`、`config_json`、`pending_fields`、`fail_node`、`result_json`、`test_report` |
| 驼峰 | 接口原始出参字段（不改名；特例 `offerId`） | `globalId`、`testScenes`、`orderId`、`offerInstId`、`presetValue`、`testValue` |
| req_id + node_name | 节点结果存储寻址 | req_id=`PLAN20260913143025087`、node_name=`spec` |
| 枚举 | 字段来源（原始需求/AI补全）；稽核场景（spec/fee/all）；告警级别（high/middle/low）；测试场景编码（S_O_TC/S_ADD_CARD/S_U_TC/S_GROUP_BIND/S_ADDON_SUB）；测点编码（P_EFF_DATE/P_EXP_DATE/P_STATUS/P_MAIN_PROD/P_RELY_REL/P_MUTEX_REL/P_ORD_CNT/P_OFFER_NAME/P_OFFER_TYPE/P_PAY_MODE）；**需求文档关联关系（100000 可选依赖 / 110000 强制依赖 / 120000 默认依赖 / 130000 赠送 / 200000 互斥 / 410000 不可迁转 / 100100 自主版，V3.10）** | — |
| 失败环节编码 | STAGE1_CONFIG→wf_sub_02 / STAGE2_AUDIT→wf_sub_03（发起，结果续由页面巡检） / STAGE3_FEE→wf_sub_05 / STAGE4_TEST→wf_sub_04（发起，结果续由页面巡检） | — |

### 7.3 异常处理矩阵（E1~E31，E33~E40）

| # | 异常场景 | 触发点 | 系统行为 / 恢复方式 |
| --- | --- | --- | --- |
| E1 | 相似度分析失败 | wf_sub_01（节点105） | 跳过相似产品，仅用 K4 补全（自动降级） |
| E2 | 输出来源枚举违规 | wf_sub_01 要素提取 | 程序化校验失败→重新生成（最多2次），后转人工 |
| E3 | 未确认即触发配置 | 智能体语义识别 | 拒绝进入执行主干，引导回复"确认配置" |
| E5 | 自查无执行方案 | wf_sub_02 自查 | CODE_EXTRACT_RECORD total==0 终止，引导重走需求分析 |
| E6 | 配置落地 FAIL | wf_sub_02 | 主干中断→异常模板④，引导重新执行/修改执行方案 |
| E7 | 实时稽核超时 | ~~wf_sub_03~~（实时稽核已退出主链路，规格稽核改异步：发起 wf_sub_03 + 结果页面巡检） | 重试 1 次仍超时→中断，保留请求报文 |
| E8 | 稽核 pass=0 | 环节4b 页面巡检（后端查询承载） | 中断（可联动 send_alert high），页面打印 error_list 明细（不写 spec） |
| E9 | 资费校验 pass=0 | wf_sub_05 | 中断，打印风险清单，引导修改执行方案 |
| E10 | 测试发起失败 resultCode=1 | wf_sub_04（6a 发起） | 中断 |
| E11 | 测试场景为空 | wf_sub_04（6a 发起） | 中断，引导修改执行方案后重测 |
| E12 | 轮询连续 5 次失败 / failed=true | 环节6b 页面巡检（后端查询承载） | 中断，保留 globalId 人工续查 |
| E13 | 测试整体超时 | 环节6b 页面巡检（后端查询承载） | 中断，凭 globalId 人工续查 |
| E14 | orderId/offerInstId 为空 | 环节6b 页面巡检报告生成 | 报告标注"未获取到受理凭证，需人工核实"（不中断） |
| E15 | 未经确认发起审批 | wf_sub_06 触发门禁/后端硬校验 | 四环节结果不齐拒绝推送 |
| E16 | 审批推送失败 | wf_sub_06 | 重试 1 次后终止，修复后重推（幂等） |
| E17 | 监控接口失败 | wf_sub_07 | 终止本轮，下周期自动重试 |
| E18 | 续跑参数非法 | fail_node 非法/req_id 查无 | 按首次执行处理 |
| E19 | 重新执行写接口重复防护 | 各环节自查前置 | 已存成功记录→跳过写接口直接回放 |
| E20 | 主干中段接口网络错误 | wf_sub_03/05/04 | 中断→引导稍后重新执行 |
| E21 | 审批进度查询无审批单 | 页面自动巡检（后端查询承载） | 提示先发起审批 |
| E22 | 查询缺少必填参数 | wf_sub_07/（页面巡检按 req_id） | 先追问补齐，不发起调用 |
| E23 | 环节结果存储写入失败 | 各环节 save_node_result | 打印结果不受影响，续跑退化为全量重跑 |
| E24 | 审批未通过即要求确认上线 | wf_sub_06/07 | 拒绝生成监控运维方案 |
| E26 | 被测一致性核对不一致（e26=0） | 环节6b 页面巡检报告节点315 | 八章结论从严标注并提示人工核实，不输出通过性明细 |
| E30 | 模板路由失败 | wf_sub_01 产品识别 | product_type ∉ 枚举→中断询问 |
| E31 | 要素提取质量闸 FAIL | wf_sub_01 节点108 | quality_gate=FAIL 输出待补路径，禁止进入合并/组装 |
| E32 | schema 文件缺失/损坏 | wf_sub_01 取模板 | 中断 |
| E33 | 本体校验 high 违规 | wf_sub_01 节点110 | violations severity=high→中断引导 |
| E34 | explain/provenance 取数失败 | explain 端点 | 提示型，不中断 |
| E35 | 稽核文件上传失败/解析无销售品 | wf_sub_03 `audit_file_upload`（B1） | 中断，提示检查 Excel 格式与内容；返回 sales_id 空视为 E35 |
| E36 | 异步稽核通知失败（无 request_id） | wf_sub_03 `audit_async_notify`（B2） | 重试 1 次，仍失败→中断，保留 sales_id 供人工续查 |
| E37 | AI 稽核结果落库失败 | wf_sub_03 `audit_insert_result`（B3） | 中断（回调前置记录缺失会导致回调丢弃），修复后重跑该品 B2+B3（B3 幂等由 batchId 保证） |
| E38a | 稽核发起无 requestIds | wf_sub_03（4a 发起流） | 中断，提示重新上传/发起 |
| E38b | 稽核轮询单次窗口未完成 | 环节4b 页面巡检（后端查询承载） | **非中断**：页面显示"稽核处理中"，自动续轮询（不写 spec，不允许推进） |
| E38c | 稽核轮询连续失败/整体超时 | 环节4b 页面巡检（后端查询承载） | 连续 5 次失败或整体超时→中断，页面提示，保留 requestIds 人工续查 |
| E39 | 稽核 SQL 结果为空/异常 | 环节4b 页面巡检 `audit_sql_query`（B4 取结果） | 中断，打印 resultCode/resultMsg，引导重新执行 |
| E40a | 测试发起无 globalId | wf_sub_04（6a 发起流） | 中断，提示重新发起测试 |
| E40b | 测试轮询单次窗口未完成 | 环节6b 页面巡检（后端查询承载） | **非中断**：页面显示"测试处理中"，自动续轮询（不写 test，不允许推进） |
| E40c | 测试轮询连续失败 | 环节6b 页面巡检（后端查询承载） | 连续 5 次失败→中断，页面提示，保留 globalId 人工续查 |
| E41 | 需求文档（.xlsx）解析失败/无销售品/关联关系非法 | **智能体挂载 skill**（调 `/requirement/parse-xlsx`） | 中断，提示检查 Excel 格式（可选包/关联关系 sheet）与内容；`offers[]` 为空或关系枚举越界（∉ 7 类）视为 E41（V3.10 新增解析能力，V3.11 由智能体 skill 承担、工作流外） |

### 7.4 知识库文档清单与维护（详见《细化设计方案》第 4 章）

- 文档清单：K1（管理办法/配置规范/命名规则）、K2（资费模板手册/叠加优惠约束说明）、K3（测试用例设计规范/测试报告模板）、K4（产品信息.txt 拆分 18 单文件）、K5（FAQ）。
- 维护机制：新规范发布/新资费政策由知识运营按命名规范替换；同名先放新后删旧；更新后执行 3 组固定问答回归；下线销售品资料保留 6 个月后归档。

---

## 8. 开发实施计划（约 26 个工作日）

| 阶段 | 工作项 | 建议工期 |
| --- | --- | --- |
| 1 基础搭建 | 创建助手/知识分类；确认 knowledge/ 知识资产、`/api/v1/appstore/*` 适配端点与网关 BASE_URL；确认节点结果存储查询插件可用 | 3天 |
| 2 后端适配端点开发 | 既有 13 工具 + 8 新适配端点（含 V3.10 需求文档解析 `/requirement/parse-xlsx`）逐一联调（裸报文契约、异步测试轮询、实时稽核同步返回） | 5天 |
| 2.5 现场接口适配（V3.9） | `application.yml` 新增 `prodai.appstore.interface.*` 配置组 + InterfaceFacade + Spring 策略工厂；9 现场接口封装（A1/C1~C4 信封归一、B1~B4 智能稽核四接口）；dev/prod 分环境配置；mock/real 同构校验 | 4天 |
| 3 知识库建设 | 规范文档采编、《产品信息.txt》切片入库（knowledge/）、FAQ 编制 | 3天 |
| 4 工作流编排 | `gen_workflows.py` 生成 14 个子工作流 JSON 并导入调试；代码节点、req_id 自查链路联调 | 6天 |
| 5 智能体集成 | 提示词、插件、工作流、知识库装配；模型参数调优 | 2天 |
| 6 验证与优化 | 全流程验证：方案确认门禁、需求审批二次确认、步进式推进、异常引导与续跑、审批门禁、消息查询、受理验证结论完整性；提示词迭代 | 4天 |

---

## 9. 测试与验收

### 9.1 分层测试

1. **插件级**：逐工具连通性/入参提取/出参归纳（裸报文、实时稽核同步、存储读写一致、globalId 传递）。
2. **工作流级**：正向用例（提报→审批→方案→确认→逐环节推进→审批）；反向用例（**未确认不配置、提报单未确认不发起审批、执行方案未确认不调度 wf_sub_02、跳步审批被拒**）；步进式推进用例（每环节止于反馈点）；异常中断与续跑用例；硬校验用例（四环节不全拒审批）；结果获取用例（**页面自动巡检取回 spec/test 并翻转页签**）；分支用例；**链路验证**（`wf_runner.py --chain --chain-skip wf_sub_12,wf_sub_13,wf_sub_08`，验证不调用结果取回子流时主干串链仍不报错、无中止）。
3. **智能体级**：多轮对话体验、确认交互、引导问题命中、FAQ 准确率。
4. **专项回归**（见第 6 章）：融合 F1~F8、模板轨六步、本体校验闸 E33、存量报文三道闸、清洗 C1~C15 可重复执行；**需求文档驱动回归（V3.10 解析 / V3.11 skill 承载）**：.xlsx 上传→**智能体 skill 解析**（4 品 + 关联关系）为结构化文本→作为 `requirement_text` 调 `wf_sub_00` 渲染提报单、父/子键一品多品逐品加载、品间可选依赖复用融合组、E41 非法文档中断。

### 9.2 验收标准（节选）

| 指标 | 目标 |
| --- | --- |
| 端到端流程贯通率 | 100% |
| 确认门禁有效性 | 未确认时配置落地/审批推送触发率 = 0%；需求审批/执行方案未确认时发起率、调度率 = 0%；**执行主干未获用户反馈确认不推进下一环节率 = 0%** |
| 步进式推进纪律 | 每环节 100% 输出结果 + 下一步建议并止于反馈点；无跳步/并行/擅自推进 |
| 异常处置完整性 | 100% 输出异常环节/原因/建议 + 重新执行/修改执行方案引导 |
| 续跑正确性 | 已成功环节不重复执行，续跑起点与失败环节一致 |
| 消息查询可用性 | 监控结果查询命中率 100%；稽核/测试/审批进度由页面自动巡检呈现成功率 100% |
| 需求要素映射准确率 | ≥95%（字段名与 24 字段注册表一致） |
| AI补全字段标记正确率 | 100%（备注仅两态） |
| 待补充规则符合率 | 100%（仅价格类待补充；套餐编码恒"系统待生成"） |
| 受理验证结论完整性 | 测试报告 100% 含 orderId/offerInstId 及逐场景结论 |
| 需求文档一品多品支持（V3.10） | .xlsx 解析成功率 100%；1 份需求 N 品（父/子键）逐品加载完整率 100%；品间关联关系 100% 映射入 group_rules 组内依赖 |
| 全流程耗时 | 较人工缩短 ≥60% |

### 9.3 端到端演示剧本

- 文件：《产销品加载AI应用-端到端演示剧本.md》；按幕走通 8 环节（幕1 提报止于确认点 / 幕1-2 审批前置【用户确认后触发】/ 幕2 确认门禁 / 幕3 执行主干步进推进 / 幕3A 异常中断与引导 / 幕3B 成功详情与审批确认 / 幕4 稽核+资费 / 幕5 自动测试（含受理验证）/ 幕6-7 上线审批 / 幕8 监控运维）。
- 附"反向分支速查表"，与验收指标直接对应（含"未确认不配置"、"需求审批未确认不发起"等）。

---

## 10. 上线与运维

1. **发布**：智能体发布到小思页面；工作流/插件保持版本可回退。
2. **监控**：每日定时（或运维对话触发）运行 `wf_sub_07`；用户可随时通过工作台页面自动巡检查看结果。
3. **审批跟踪**：需求工单审批（wf_sub_11 用户确认后发起）与上线审批（launch 轨）推送后由**配置工作台页面自动巡检**呈现进度；驳回时同步驳回原因并引导修改后重新发起。
4. **迭代机制**：知识库持续更新（新规范/新资费）；提示词与阈值按误判情况调优；后端端点与工作流代码节点按系统升级同步维护。
5. **运维报表**：每周汇总执行量、拦截量、测试通过率，评估产销品加载智能体效能。

---

## 11. 风险与对策

| 风险 | 对策 |
| --- | --- |
| 接口不稳定/字段变更 | 插件层隔离；出参加必填校验；替换真实实现契约不变 |
| 实时稽核慢/超时 | 60s 超时重试 1 次；超时转人工并保留请求报文 |
| 稽核/测试异步长时间未完成 | 页面自动巡检短窗口轮询（单次 5s×60=5 分钟，窗口耗尽非错误、页面自动续轮询）；连续 5 次失败或整体超时→中断，保留 requestIds/globalId 人工续查 |
| 大模型解析幻觉 | 知识库增强 + 温度 0.2 + 结构化输出 + 来源标记 + 实时稽核兜底 |
| 配置落地与方案不一致 | 智能配置直读存储 JSON 原样透传，禁二次加工 |
| 用户未确认即触发生产写入 | 方案就绪止于确认点；确认语义识别命中才进入执行主干（后端确认门禁已移除） |
| 续跑重复执行写操作 | 各子流以 req_id+node_name 落库，续跑自查回放已成功环节 |
| 未经确认发起审批 | 需求工单审批二次确认（提报单止于确认点，经 wf_sub_11 发起）；上线审批后端四环节硬校验 |
| 流程环节被跳过 | 智能体步进式推进纪律 + submit_release_approval 四环节硬校验双保险 |
| 资费漏洞漏检 | 知识库持续运营 + check_scene=all 全量校验 |
| 融合组成员关系错误 | 成员构成以 offer_group/组种子为准，禁止模型推理增删（E26 纪律） |
| 需求文档解析歧义/一品多品串键（V3.10） | 解析端点归一为 `offers[]`（offer_seq 显式编号）+ 父/子键复合寻址；关系枚举越界/无销售品判 E41 中断；父 req_id 仍守 `PLAN\d{17}` 校验 |
| 存量报文/清洗质量 | 三道闸校验 + C1~C15 规则可审计 + 人工抽检 |

---

## 附：快速实施 Checklist（当前基线 V3.13）

### A. 工作流 JSON 路线（当前基线，已就绪项以 [x] 标注）
- [x] 知识资产迁至 `knowledge/`：K1~K5、ontology-fields、seed_offer_groups、templates 注册表、存量产品目录_清洗后、K5 存量报文
- [x] 后端 `/api/v1/appstore/*` 适配端点就绪（13 工具 + 8 新端点；BASE_URL=http://10.86.13.201:31281）
- [x] `gen_workflows.py` 生成 14 个子工作流 JSON（`wf_sub_00`~`wf_sub_13`）并导入
- [x] 确定性逻辑内嵌 type=6 代码节点（CODE_*）取代技能包脚本子命令
- [x] 智能体按 3.2 提示词语义识别直调（职责分层：只做语义识别/业务理解/结果表达）
- [ ] **单独配置「产销品数字员工」提示词（2.4.1）并关联「产销品加载智能体」**：数字员工独立配置（人设/话术/开场白/引导问题），通过关联复用加载智能体全部业务能力
- [x] 环节覆盖：需求提报（止于确认点）+ wf_sub_11 发起需求审批、需求分析（模板轨）、智能配置（融合回显）、规格稽核（组维度）、资费校准（成员分组）、自动测试（31 条固定用例+九章节报告）、上线审批（双轨+推送即结束，审批状态由页面自动巡检呈现）、监控运维（根因闭环）、存量查询/存量合规辅助子流
- [x] 主链路端点实跑验证（2026-09-19）：result/save、result/query、audit/realtime、product/config/save、billing/rules/verify、test/offer/start、test/offer/result、approval/submit、product/monitor、similar/offer/query live 生效
- [ ] 14 个既有契约端点 + 8 个适配端点逐一连通（含错误码验证）；仍占位：report/download、script/download（backend_pending=1 回退）、/ops/root-cause、/ops/work-orders、/shelf-compliance、/validate-nested、/explain 待逐项确认
- [ ] 端到端联调：正向全流程 + 反向用例（未确认不配置、需求审批未确认不发起、跳步审批被拒、逐环节推进止于反馈点、稽核驳回中断引导、续跑不重复写）+ 18 销售品兼容回归
- [ ] 全流程各子流处理完成后均输出【下一步建议】引导（V3.6）
- [ ] 按《端到端演示剧本》完成全流程彩排（含反向分支）
- [ ] 发布上线并接入监控运维

### A2. 现场接口适配与配置化封装（V3.9 新增，详见 4.5）
- [ ] **`application.yml` 新增 `prodai.appstore.interface.*` 配置组** + `@ConfigurationProperties` 绑定；`application-dev.yml`（mode=mock，返回模拟报文）/`application-prod.yml`（mode=real，直连真实接口）分环境覆盖
- [ ] 后端 InterfaceFacade + Spring 策略工厂（`mode=mock|real`，`@ConditionalOnProperty` 装配）落地，对外 `/api/v1/appstore/*` 契约不变
- [ ] 相似度分析映射 A1（信封组装/`similarOfferList[]`→`similarOffer` 取 Top1 归一；**real 无 offerInfo，按 similarOfferId 从 K4/K5 补齐 24 字段**，4.5.5 第 6 点）
- [ ] 智能稽核四接口适配 B1~B4（上传 multipart / 异步通知 GET / 插入结果裸体 / SQL 查询信封）+ **稽核"发起 + 页面自动巡检取结果"**（wf_sub_03 发起 + 页面轮询 result/query + 后端 B4 短窗口轮询 + audit_pending/spec 落库键；智能体不直调 wf_sub_12）
- [ ] 测试四接口映射 C1~C4（`resultObject.*` 解包归一）+ **测试"发起 + 页面自动巡检取结果"**（wf_sub_04 发起 + 页面轮询 result/query + 后端 C3/C4 短窗口轮询 + test_pending/test 落库键；智能体不直调 wf_sub_13）
- [ ] 配置项 `prodai.appstore.interface.eop.*` 注入（base-url/app-key/dst-sys-id/svc-code/sign/upload-file-field/timeout）+ `poll.audit.*`/`poll.test.*` 轮询参数
- [ ] mock 与 real 出参归一结构同构校验（同一 Adapter 接口一致性）
- [ ] 现场联调：`mode=real` 指向 EOP，9 接口逐一连通（含 B2↔B3 顺序、B4/C3 轮询、异常码 E35~E40）

### A3. 需求文档（Excel）驱动与一品多品（V3.10 新增解析，V3.11 改为智能体 skill 承载，详见 6.6）
- [x] **后端解析端点 `/requirement/parse-xlsx`（2e 工具）已实现**：multipart 上传 + 解析（`RequirementDocService`，复用零依赖 `ConfigDocumentParser`），返回 `offers[]` + `relations[]` + `source_sheets[]`（与 B1 同模式）
- [x] **智能体已挂载文档解析 skill**：`场景设计/ah_cti_poc/skills/requirement-doc-parse/`（`SKILL.md` + `references/output-contract.md` + `scripts/parse_requirement_xlsx.py`，内嵌 openpyxl 纯 Python 解析，语义对齐 `RequirementDocService`）——上传 .xlsx → 解析为多品结构化文本（多品清单 + 品间关联关系 + 渲染 `requirement_text`）；退出码 0=成功 / 2=E41，失败按 E41 引导
- [ ] **智能体提示词补充**：上传 .xlsx 时先经 skill 解析，再将结构化文本作为 `requirement_text` 直调 `wf_sub_00`；**严禁再调用任何"需求文档解析"子流或向工作流传 `requirement_file`**
- [x] **工作流层不承载文档解析**：已回退试行的 `wf_sub_14` 独立子流与 `wf_sub_00` 双轨改造，恢复 14 子流基线（`wf_sub_00` 单轨：`requirement_text` → LLM 抽取 → CODE_RENDER_REQ）
- [ ] 父/子键一品多品：父 `req_id` 守 `PLAN\d{17}` 校验（后端零改动）+ 子 `offer_seq`（1..N）；`plan_json`/`config_json` 承载 `offers[]`，代码节点内嵌循环逐品处理
- [ ] 品间关联关系映射 group_rules 组内依赖（可选依赖↔OPTIONAL_DEPEND，先例 GP900113046）；`wf_sub_01/02` 融合组场景复用 6.1 机制
- [ ] 24 字段归属三分（映射入 24 字段 / 落模板 schema / 冗余入报告）；稽核 Excel 由后端按 req_id 独立生成（G6，不复用需求输入文档）
- [ ] 可选：`seed_offer_groups.json` 补 `GP900117029`~`GP900117032` 四组；`存量产品目录`/K4/K5 补 4 品资料
- [ ] 端到端回归：上传《新增四档5G畅享融合套餐销售品.xlsx》→ skill 解析 → `wf_sub_00` 渲染提报单 → 四档逐品加载 → 副卡可选依赖组核对（E26）→ 上线审批

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
