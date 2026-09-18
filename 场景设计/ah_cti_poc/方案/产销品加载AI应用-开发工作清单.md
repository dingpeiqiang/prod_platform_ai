# 产销品加载 AI 应用 · 开发工作清单

> 平台：12 个工作流 JSON（`智能体工作流集V1.6/`，gen_workflows_v2.py 生成）+ 后端 /api/v1/appstore/* 适配端点
> 版本：V2.0　日期：2026-09-18
> 依据：《产销品加载AI应用开发方案.md》V3.0、《产销品加载AI应用-细化设计方案.md》V2.4、《场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/gen_workflows_v2.py》
> 用途：需要**代码开发**的接口/服务/数据/工作流工作清单（平台工作流导入与联调见《平台配置清单》，本清单不含）
>
> V2.0 变更（2026-09-18）：**工作流重塑（V2.0）**——① 实现载体由「Skills 技能包 + 脚本子命令」整体废弃，改为 **12 个工作流 JSON**（wf_main_intent_意图路由 + wf_sub_00~wf_sub_10，由 gen_workflows_v2.py 在 `工作流配置/智能体工作流集V1.6/` 生成）；② 原脚本内嵌逻辑（build_plan/extract_record/poll_test_progress/审批轮询/下载等）改以 **type=6 代码节点**内嵌于工作流（CODE_MERGE_NESTED/CODE_RENDER_TABLE/CODE_VALIDATE_ELEMENTS/CODE_GET_TEMPLATE/CODE_RENDER_REQ/CODE_MAP_FIXED_CASES/CODE_EXTRACT_RECORD/CODE_POLL_PROGRESS/CODE_DISPATCHER/CODE_SUMMARY_APPROVAL/CODE_FUSION_GROUP_ECHO/CODE_APPROVAL_POLL/CODE_DOWNLOAD_LAUNCH_SCRIPT/CODE_DOWNLOAD_TEST_REPORT/CODE_OP_* 等）；③ 后端新增 **7 个适配端点**（AppStoreV16Controller，POST /api/v1/appstore/* 的 ops/root-cause、ops/work-orders、shelf-compliance、validate-nested、explain、report/download、script/download），均已实现、编译通过、git 提交（commit ee6f5a8），待后端重新部署使 6174 生效；④ 网关 BASE_URL=http://10.86.13.201:31281/api/v1/appstore/*（需确认网关代理到 6174）；⑤ 知识库迁至 `knowledge/`（原 references/ 废弃）。
> V1.10 变更（2026-09-14）：**配置上线脚本下载链接 + 受理验证归并为自动测试子集**——① 接口3 save_product_config 落地成功时后端生成 CRM/billing 落库 SQL 上线脚本（模拟，两段式 /*run@crm*/+/*run@billing*/）并存脚本档案，出参新增 `script_url`；② 新增附带下载路由 GET `/api/v1/appstore/product/config/script`（text/plain，未落地 404）；③ flow-B 环节1 输出模板新增"配置上线脚本下载链接"行；④ 执行主干改回四环节（智能配置→稽核→资费校准→自动测试），受理验证=环节4 测试报告内子集小节，不设独立环节5 与触发词；flow-C 看板"受理验证"行并入"自动测试（含受理验证）"（4 项 ✅）。
> V1.9 变更（2026-09-14）：字段体系全量重构对齐 V3.0 口径——① 接口1 出参 offerInfo fields 由四类18字段改 3 模块/9 分类 24 字段（toFields18 重写）；② 后端 FieldOntologyService 字段注册表重构（套餐档位唯一待补充项、套餐编码默认"系统待生成"、来源两态【原始需求】/【AI补全】）；③ cpcp_api.py build_plan 改五列模块表格输出（CATEGORY_MODULE/SOURCE_LABEL 常量）；④ 工具3 确认门禁描述按 V2.2 修订（实际已移除，本版同步清理残留描述）；⑤ 触发词"确认配置/上线审批/确认上线"对齐。

---

## 0. 开发范围总述（V2.0 工作流重塑版口径）

- 实现方式（V2.0 起基线）：**12 个工作流 JSON**——原「Skills 技能包 + 脚本子命令」承载层整体废弃，改为 **12 个工作流 JSON**（`wf_main_intent_意图路由` + `wf_sub_00~wf_sub_10`，由 `gen_workflows_v2.py` 在 `工作流配置/智能体工作流集V1.6/` 生成）；确定性逻辑内嵌为 **type=6 代码节点**（见第 4 节清单）；后端 14 条能力接口模拟实现**原样保留**（契约不变），并新增 7 个适配端点；
- 原脚本子命令开发项（build_plan/extract_record/poll_test_progress/cpcp_api.py 各子命令）**不再作为独立开发项**——已改为工作流内嵌代码节点与后端端点承载（对应 CODE_GET_TEMPLATE/CODE_RENDER_REQ/CODE_MAP_FIXED_CASES/CODE_EXTRACT_RECORD/CODE_POLL_PROGRESS/CODE_DOWNLOAD_* 等）；
- 13 个工具对应的 HTTP 能力接口**全部自研实现并采用模拟结果输出**，不再对接外部 ApiID；
- 模拟服务统一部署于 `http://10.86.13.201:31281/api/v1/appstore/*`（网关，需确认代理到后端端口 **6174**；已有 Mock 服务框架，其上补齐/改造 14 条路由 + 新增 7 条适配端点）；
- 模拟种子数据 = 《产品信息.txt》全部 **18 个销售品**（5G-A 系列 10 个 + 权益随心选系列 8 个），任一套餐输入均可返回与该销售品资费规则一致的结构化结果；知识源迁至 **`knowledge/`**（原 references/ 废弃，K1~K5 目录对应 `knowledge/K1~K5`）；
- 测试预期值 `presetValue` 取自该销售品在《产品信息.txt》中的规则值；
- 接口契约（路径/入参/出参）以《产销品场景部分能力接口清单.xlsx》为参考基线，后续替换真实实现时契约不变（仅改网关 BASE_URL）。

---

## 1. 接口开发清单（自研模拟实现，14 条既有路由 + 7 条 V2.0 新增适配端点）

### 1.1 需求分析与稽核类

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 工期 |
| --- | --- | --- | --- | --- | --- |
| 1 | 相似度分析 `query_similar_offer` | POST /api/v1/appstore/similar/offer/query | 以 18 销售品构建相似度匹配模拟服务（关键词+资费结构加权打分），**返回相似度最高的1个产品 similarOffer（含 offerInfo 完整产品配置信息——toFields18 同构转换为与需求要素一致的 fields 3 模块/9 分类 24 字段数组，V1.9 重写）** | 任一 18 销售品相关需求均可命中对应销售品（取 score 最高）；businessDesc>5000 字符由上游摘要，接口只校验非空 | 0.5d |
| 2 | 实时规格稽核 `realtime_spec_audit` | POST /api/v1/appstore/audit/realtime | 规则引擎：按配置规范校验必填属性/命名/生效期/销售范围，对照《产品信息.txt》该销售品规则；**同步返回** | pass/error_list/audit_summary 结构完整；支持构造缺陷用例（互斥叠加）返回 pass=0；60s 超时返回 TIMEOUT | 1d |
| 3 | 配置落地 `save_product_config` | POST /api/v1/appstore/product/config/save | 模拟 CRM 写入：内存产品档案（种子 18 销售品）；解析 plan_json 各模块字段；**确认门禁已按 V2.2 移除**（不校验 CONFIRMED 标记，确认语义由智能体识别；保留幂等与 plan_json 合法性校验），方案key由后端从 plan_json 的 req_id 键提取；幂等（同 plan_json 返回已存在 offer_id）；**V2.5 落地成功时按落地配置生成 CRM/billing 落库 SQL 上线脚本（模拟，两段式 /*run@crm*/+/*run@billing*/）并存脚本档案，出参新增 `script_url` 下载链接** | 未确认不触发由智能体保证（后端无 NOT_CONFIRMED 返回）；save_result 各模块分类明细；product_id/offer_id 生成规则稳定；script_url 可下载（GET /api/v1/appstore/product/config/script?product_id=Pxxx，text/plain，未落地 404） | 1d |

### 1.2 自动测试类（含受理验证）

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 工期 |
| --- | --- | --- | --- | --- | --- |
| 4 | 测试发起 `offer_test` | POST /api/v1/appstore/test/offer/start | 校验 offerId ∈ 18 销售品；创建模拟测试任务（内存状态机），返回 globalId=50+yyyyMMddHHmmss+10位随机 | 未收录 offerId 返回 4001；globalId 格式校验；防重复发起 | 0.5d |
| 5 | 测试场景 `get_test_scenes` | POST /api/v1/appstore/test/offer/scenes | 按 globalId 返回受理类场景集合（S_O_TC/S_ADD_CARD/S_U_TC），融合/单品/权益套餐按《产品信息.txt》推导场景集合 | 5G-A 融合套餐含副卡加装场景；权益随心选类返回新装+退订 | 0.5d |
| 6 | 测试进度 `get_test_progress` | POST /api/v1/appstore/test/offer/progress | 进度状态机：totalSteps=场景数+2（前 2 步智能匹配场景&用例/资源）；按真实时间推进（默认 60~90s 跑完，可配置加速/卡死用于演示） | done/failed/failIndex 与场景状态映射（0成功/1失败/2中止/NULL进行中）一致；轮询幂等 | 1d |
| 7 | 测试结果 `get_test_result` | POST /api/v1/appstore/test/offer/result | 生成逐场景测点明细：**presetValue 取自《产品信息.txt》该销售品规则值**；testValue 默认与预期一致，支持按配置注入不一致（演示失败分支）；生成受理凭证 orderId/offerInstId | 900102308 与 900117022 两类套餐 presetValue 与源文件逐项一致；orderId/offerInstId 非空；objTestSceneRel AI 总结字段完整 | 1.5d |

### 1.3 资费与审批运维类

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 工期 |
| --- | --- | --- | --- | --- | --- |
| 8 | 计费规则校验 `check_billing_rule` | POST /api/v1/appstore/billing/rules/verify | 内置规则引擎（负资费/边界价差/叠加上限/互斥/自定义规则），对照 18 销售品资费结构 | check_scene 四种枚举生效；构造冲突用例 pass=0 且 risk_list 完整 | 1d |
| 9 | 审批推送 `submit_release_approval` | POST /api/v1/appstore/approval/submit | 写入模拟审批状态库（状态机：审批中→产品经理审核→部门主管审批→通过/驳回）；**V1.7 后端硬校验：req_id 入参必填 + 遍历查询 `NodeResultService.latestRecord(req_id,"config"/"spec"/"fee"/"test")` 四条记录全部非空（V1.7 统一键，原 execution_id 参数合并为 req_id）**；幂等（同 product_id 返回原 approval_id） | 四环节结果缺失时拒绝推送（LLM 跳步发起也被拦截）；状态可被接口13 查询 | 0.5d |
| 10 | 审批进度 `query_approval_status` | GET /api/v1/appstore/approval/status | 从模拟审批状态库按 approval_id（优先）/product_id 查询最新审批单 | 返回 status/current_node/approver/opinion/update_time；查无单返回明确提示 | 0.5d |
| 11 | 监控查询 `query_product_monitor` | GET /api/v1/appstore/product/monitor | 按 product_id+日期确定性生成指标（订单量/异常量/差错率/告警列表）；支持 error_count>0 预置演示 | date_range/metric 参数生效；告警列表与接口12 写入记录回显一致 | 0.5d |
| 12 | 异常告警 `send_alert` | POST /api/v1/appstore/alert/send | 生成 alert_id 写入模拟告警库（供监控查询回显闭环） | alarm_level 三级枚举；content 落库 | 0.25d |

### 1.4 节点结果存储查询对齐（后端通用 API，改动量小）

> 说明：节点结果存储/查询为后端已有通用 API（不自研），后端契约以 `/api/v1/appstore/result/save`（POST，入参 req_id/node_name/result_json/status）与 `/api/v1/appstore/result/query`（**GET**，入参 req_id/node_name/latest_only，出参 total/list）为准。**存储已落库持久化**：后端由 `NodeResultService`（MyBatis-Plus）写入 `pd_ai_node_results` 表（H2 DDL：`backend-app/src/main/resources/sql/h2/schema-h2.sql`；MySQL DDL：`sql/01_full_schema_ddl.sql` L504 起），服务重启后结果不丢失。存储寻址口径（V1.7 统一键）：全链路唯一批次标识 = req_id（PLAN+yyyyMMddHHmmss+3位随机数，原 plan_id/execution_id 双键合并）；执行方案环节 node_name=requirement，执行主干各环节 node_name=config/spec/fee/test/**report**（上线报告，程序C 存储），同键覆盖写。V2.0 起由工作流内 **type=6 代码节点**（对应 CODE_RENDER_REQ 生成的 req_id/plan_json + 直连后端 result/save、result/query，见第 4 节）承载，原 cpcp_api.py 子命令层已废弃。

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 工期 |
| --- | --- | --- | --- | --- | --- |
| 13 | 节点结果存储 `save_node_result` | POST /api/v1/appstore/result/save | 后端已有 API 直接复用；入参 req_id/node_name/result_json（status 默认 ok）；同键（req_id+node_name）覆盖；非法 req_id 返回 5002、node_name 为空返回 5003、result_json 超 64KB 返回 5004 | 程序入参与后端契约逐项一致（各程序步骤结束前保存本环节结果：程序A 保存执行方案、程序B 各环节保存 config/spec/fee/test、程序C 保存 report）；保存→按 req_id+node_name 查询 result_json 逐字节一致；服务重启后可查询（持久化） | 0.25d |
| 14 | 节点结果查询 `query_node_result` | GET /api/v1/appstore/result/query | 后端已有 API 直接复用；入参 req_id（必填）/node_name（可选）/latest_only（默认1）；出参 code/msg/total/list（取 list[0].result_json 为结果原文） | 各程序环节 req_id 自查（V1.7 统一键）：req_id=程序入参、node_name=上游环节名；非法 req_id 返回 5002；total=0 时按 E5 处理（"未找到执行方案"） | 0.25d |

### 1.5 V2.0 新增适配端点（AppStoreV16Controller，/api/v1/appstore/*）

> 说明：V2.0 工作流重塑新增 **7 个适配端点**，供工作流内 type=6 代码节点（CODE_OP_*、CODE_DOWNLOAD_*）以 HTTP 调用（urllib，模式同 CODE_APPROVAL_POLL）。均已实现、编译通过、git 提交（commit ee6f5a8），**待后端重新部署使 6174 生效**；网关 BASE_URL=http://10.86.13.201:31281/api/v1/appstore/* 需确认代理到 6174。

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 状态 |
| --- | --- | --- | --- | --- | --- |
| 15 | 根因分析 `root_cause` | POST /api/v1/appstore/ops/root-cause | 承接 CODE_OP_ROOT_CAUSE：按网关请求透传根因分析查询（监控指标/告警定界），返回结构化根因结论与建议 | 返回与工作流节点入参一致的结构化结果；无伪造数据；接通网关可调用 | ✅ 已编码 |
| 16 | 工单创建 `work-orders` | POST /api/v1/appstore/ops/work-orders | 承接 CODE_OP_CREATE_WO：创建模拟运维工单并写内存工单库 | work_order 返回字段完整；幂等（同入参不重复建单） | ✅ 已编码 |
| 17 | 上架合规校验 `shelf_compliance` | POST /api/v1/appstore/shelf-compliance | 承接 CODE_OP_SHELF_COMPLIANCE：按上架规范校验套餐字段返回 pass/error_list | 构造缺陷用例 pass=0 且 error_list 完整 | ✅ 已编码 |
| 18 | 嵌套结构校验 `validate_nested` | POST /api/v1/appstore/validate-nested | 承接 CODE_OP_VALIDATE_NESTED：校验知识库嵌套目录（K4 目录）结构 JSON 合法性，valid=0 时 error_list 非空 | valid/error_list/note 结构完整；非法 JSON 明确报错 | ✅ 已编码 |
| 19 | 说理说明 `explain` | POST /api/v1/appstore/explain | 承接工作流内 explain 字段透传/说理聚合，返回环节结论说明文本 | 文本与固化话术对齐；无伪造 | ✅ 已编码 |
| 20 | 测试报告下载 `report_download` | POST /api/v1/appstore/report/download | 承接 CODE_DOWNLOAD_TEST_REPORT：按 globalId 生成/返回测试报告下载（text/plain，未生成 404） | 报告内容与测试结果一致；未生成返回明确提示 | ✅ 已编码 |
| 21 | 配置上线脚本下载 `script_download` | POST /api/v1/appstore/script/download | 承接 CODE_DOWNLOAD_LAUNCH_SCRIPT：返回配置上线脚本下载链接内容（text/plain，未落地 404）；与接口3 的 `script_url` 呼应 | 内容为两段式 /*run@crm*/+/*run@billing*/ SQL；未落地 404 | ✅ 已编码 |

---

## 2. 模拟数据工程（V1.6 核心，V1.9 字段键兼容）

| # | 工作项 | 说明 | 工期 |
| --- | --- | --- | --- |
| 1 | 种子数据集 `seed_offers.json` | 从《产品信息.txt》结构化 18 条销售品全量规则（ID/名称/系列/套内资费/套外资费/过渡期资费/副卡/流量结转/断网授权/停机规则/计费周期与付费方式/销售渠道/订购/变更/退订拆机携出）；**V1.9：OfferSeedService.toFields18 重写为 24 字段同构输出（in_fee/out_fee/sub_card/sale_channels/flow_carry_over/net_cutoff_limit/validity/change_rule/transition_fee 等键映射新字段），种子 JSON 键名兼容零改动** | 1d |
| 2 | 规则值映射表 `preset_map.json` | 18 销售品 × 10 测点（P_EFF_DATE/P_EXP_DATE/P_STATUS/P_MAIN_PROD/P_RELY_REL/P_MUTEX_REL/P_ORD_CNT/P_OFFER_NAME/P_OFFER_TYPE/P_PAY_MODE）的预期值映射，供接口7 生成 presetValue | 0.5d |
| 3 | 演示场景开关 | 每销售品支持注入：稽核驳回用例 / 资费冲突用例 / 测试测点不一致用例 / 监控 error_count>0 用例（通过请求参数或配置文件控制，正向演示默认全通过） | 0.5d |
| 4 | 一致性自测脚本 | 遍历 18 销售品逐一调用接口 1/2/4/7/8/11，断言返回结构与规则值一致（对应细化设计 2.4 #11/#12、3.5 #17） | 0.5d |

---

## 3. 工作流塑造与内嵌代码节点（V2.0：废弃 skills/cpcp-product-worker 与 scripts/cpcp_api.py 子命令层）

> 原「Skills 技能包 + 脚本子命令」承载层整体废弃，确定性逻辑改由 **12 个工作流 JSON（gen_workflows_v2.py 在 `工作流配置/智能体工作流集V1.6/` 生成）内嵌 type=6 代码节点**承载。12 个工作流：`wf_main_intent_意图路由` + `wf_sub_00~wf_sub_10`（上报总结/需求分析/稽核/资费校准/自动测试/审批/监控运维/上线单查询/产品查询/嵌套校验等）。

| # | 工作项 | 说明 | 状态 |
| --- | --- | --- | --- |
| 1 | `gen_workflows_v2.py` 工作流生成器 | 由 Python 脚本统一生成 12 个工作流 JSON；BASE_URL=网关 `http://10.86.13.201:31281/api/v1/appstore/*`（需确认代理到 6174）；代码节点以 urllib 直连后端适配端点 | ✅ 已生成（`工作流配置/智能体工作流集V1.6/` 12 个 JSON） |
| 2 | 代码节点·方案/字段组装 | CODE_GET_TEMPLATE（模板获取）、CODE_RENDER_REQ（req_id 生成 PLAN+时间戳+3位随机 & plan_json 组装）、CODE_MAP_FIXED_CASES（固定用例映射）、CODE_MERGE_NESTED（嵌套结果合并）、CODE_RENDER_TABLE（五列模块表格渲染） | ✅ 已内嵌 |
| 3 | 代码节点·校验/提取 | CODE_VALIDATE_ELEMENTS（字段要素校验）、CODE_EXTRACT_RECORD（取 list[0].result_json，空报 E5）；对应原 extract_record | ✅ 已内嵌 |
| 4 | 代码节点·轮询/汇总 | CODE_POLL_PROGRESS（测试进度轮询，对应原 poll_test_progress）、CODE_APPROVAL_POLL（审批轮询）、CODE_SUMMARY_APPROVAL（审批汇总）、CODE_FUSION_GROUP_ECHO（融合/单品分组回显） | ✅ 已内嵌 |
| 5 | 代码节点·运维/下载（OP_*） | CODE_DISPATCHER（意图分发）、CODE_OP_ROOT_CAUSE（根因分析）、CODE_OP_CREATE_WO（工单创建）、CODE_OP_SHELF_COMPLIANCE（上架合规）、CODE_OP_QUERY_OFFER（产品查询）、CODE_OP_VALIDATE_NESTED（嵌套校验）、CODE_DOWNLOAD_TEST_REPORT（测试报告下载）、CODE_DOWNLOAD_LAUNCH_SCRIPT（上线脚本下载） | ✅ 已内嵌 |
| 6 | 网关联调 | 网关 BASE_URL 代理至后端 6174 确认；12 工作流内代码节点调用 7 个新增适配端点与 14 条既有路由逐一连通 | ⏳ 待后端重启与联调 |

---

## 4. 工期汇总（与主方案第 7 章对齐，V2.0 工作流重塑版）

| 阶段 | 本清单对应工作项 | 工期 |
| --- | --- | --- |
| 阶段1 基础搭建 | 种子数据集 #1 + 接口骨架 | 3d |
| 阶段2 接口开发 | 接口 1~14 开发与自测 + 数据工程 #2~#4 | 5d |
| 阶段3 知识库建设 | knowledge/K1~K5 目录文档就位（文件复制级，见平台配置清单；原 references/ 废弃） | 1d |
| 阶段4 工作流塑造 | 本清单第 3 节 12 个工作流 JSON 生成（gen_workflows_v2.py）+ type=6 代码节点内嵌 + 后端 7 个适配端点 | 4d |
| 阶段5 部署与联调 | 12 工作流导入 + 后端重启（使 6174 生效）+ 网关代理确认 + 意图路由/链路联调（见平台配置清单 3 节） | 4d |
| 阶段6 验证与优化 | 18 套餐一致性自测跑通 + 正反向用例 | 4d |
| **合计** | | **约 21 个工作日**（其中代码开发约 12d；工作流已生成、后端 7 端点已编码，待重启联调） |

---

## 5. 开发自测 Checklist（V2.0 工作流重塑版）

- [ ] 14 条既有路由 + 7 条新增适配端点单元/契约测试通过（curl/Postman/JUnit，参照《产销品加载AI应用-接口说明书.md》5 节方法）
- [ ] 后端 7 个新增适配端点已编译通过、git 提交（commit ee6f5a8）确认
- [ ] **后端重新部署完成，端口 6174 生效**；网关 BASE_URL=http://10.86.13.201:31281/api/v1/appstore/* 确认代理到 6174
- [ ] 18 销售品一致性自测全绿（任一套餐返回结构化结果、资费规则值一致、无写死单一样例回退）
- [ ] presetValue 抽查：900102308（5G-A）与 900117022（权益随心选）两类套餐与《产品信息.txt》逐项一致
- [ ] 未收录销售品 ID 输入：接口 4 返回 4001，接口 1 返回空列表或明确降级提示，不返回伪造数据
- [ ] 12 个工作流 JSON 均由 gen_workflows_v2.py 稳定生成（不手工改 JSON），type=6 代码节点内嵌完整
- [ ] 代码节点与后端契约一致：CODE_OP_* / CODE_DOWNLOAD_* / CODE_POLL_PROGRESS 等逐一连通对应端点，路径/入参/出参与细化设计映射表逐条比对（含 PARAM_MISSING/5002/5006/5004 错误码验证）
- [ ] 工具9 四环节门禁（工具层硬校验）：四环节结果不全调 submit_approval 一律拒绝；跳步调用均有拦截记录（工具7 确认门禁已按 V2.2 移除，仅验证幂等与 plan_json 合法性校验）
- [ ] V1.9 字段重构验证：ontology/fields 接口返回 24 字段注册表（3 模块/9 分类）；similar_offer offerInfo 为 24 字段数组；来源仅【原始需求】/【AI补全】两态；仅套餐档位可"待补充"；套餐编码默认"系统待生成"
- [ ] 幂等：工具7 同 plan_json、工具9 同 product_id 重复提交不产生重复记录
- [ ] 异常注入开关：稽核驳回/资费冲突/测点不一致/监控异常 四类反向用例可复现
- [ ] knowledge/ 知识源就位（K1~K5，原 references/ 废弃）；CODE_OP_QUERY_OFFER/CODE_OP_VALIDATE_NESTED 读取 knowledge/ 目录数据正常
