# 产销品加载 AI 应用 · 交付与运维手册

> 本手册为《产销品加载 AI 应用》的**交付与运维**配套文档，由原两份文档合并而成：
> - **第 1 章 开发工作清单**（原《产销品加载AI应用-开发工作清单.md》）——需要**代码开发**的接口/服务/数据/工作流工作清单。
> - **第 2 章 平台配置清单**（原《产销品加载AI应用-平台配置清单.md》）——数字员工创建、工作流 JSON 导入、知识库挂载与部署后自测逐项核对。
>
> 版本：V2.0 工作流重塑　日期：2026-09-18
> 相关：《产销品加载AI应用开发方案.md》V3.0、《产销品加载AI应用-细化设计方案.md》V2.4、《智能体工作流集V1.6》（11 个子工作流 JSON）

---

# 第 1 章 开发工作清单

> 平台：11 个子工作流 JSON（`智能体工作流集V1.6/`，gen_workflows_v2.py 生成）+ 后端 /api/v1/appstore/* 适配端点
> 版本：V2.0　日期：2026-09-18
> 依据：《产销品加载AI应用开发方案.md》V3.0、《产销品加载AI应用-细化设计方案.md》V2.4、《场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/gen_workflows_v2.py》
> 用途：需要**代码开发**的接口/服务/数据/工作流工作清单（平台工作流导入与联调见第 2 章，本清单不含）
>
> V2.0 变更（2026-09-18）：**工作流重塑（V2.0）**——① 实现载体由「Skills 技能包 + 脚本子命令」整体废弃，改为 **11 个子工作流 JSON**（wf_sub_00~wf_sub_10，由 gen_workflows_v2.py 在 `工作流配置/智能体工作流集V1.6/` 生成，无意图调度主流程，由智能体按提示词【意图→工作流映射表】语义识别直调）；② 原脚本内嵌逻辑（build_plan/extract_record/poll_test_progress/审批轮询/下载等）改以 **type=6 代码节点**内嵌于工作流（CODE_MERGE_NESTED/CODE_RENDER_TABLE/CODE_VALIDATE_ELEMENTS/CODE_GET_TEMPLATE/CODE_RENDER_REQ/CODE_MAP_FIXED_CASES/CODE_EXTRACT_RECORD/CODE_POLL_PROGRESS/CODE_SUMMARY_APPROVAL/CODE_FUSION_GROUP_ECHO/CODE_APPROVAL_POLL/CODE_DOWNLOAD_LAUNCH_SCRIPT/CODE_DOWNLOAD_TEST_REPORT/CODE_OP_* 等）；③ 后端新增 **7 个适配端点**（AppStoreV16Controller，POST /api/v1/appstore/* 的 ops/root-cause、ops/work-orders、shelf-compliance、validate-nested、explain、report/download、script/download），均已实现、编译通过、git 提交（commit ee6f5a8），待后端重新部署使 6174 生效；④ 网关 BASE_URL=http://10.86.13.201:31281/api/v1/appstore/*（需确认网关代理到 6174）；⑤ 知识库迁至 `knowledge/`（原 references/ 废弃）。
> V1.10 变更（2026-09-14）：**配置上线脚本下载链接 + 受理验证归并为自动测试子集**——① 接口3 save_product_config 落地成功时后端生成 CRM/billing 落库 SQL 上线脚本（模拟，两段式 /*run@crm*/+/*run@billing*/）并存脚本档案，出参新增 `script_url`；② 新增附带下载路由 GET `/api/v1/appstore/product/config/script`（text/plain，未落地 404）；③ flow-B 环节1 输出模板新增"配置上线脚本下载链接"行；④ 执行主干改回四环节（智能配置→稽核→资费校准→自动测试），受理验证=环节4 测试报告内子集小节，不设独立环节5 与触发词；flow-C 看板"受理验证"行并入"自动测试（含受理验证）"（4 项 ✅）。
> V1.9 变更（2026-09-14）：字段体系全量重构对齐 V3.0 口径——① 接口1 出参 offerInfo fields 由四类18字段改 3 模块/9 分类 24 字段（toFields18 重写）；② 后端 FieldOntologyService 字段注册表重构（套餐档位唯一待补充项、套餐编码默认"系统待生成"、来源两态【原始需求】/【AI补全】）；③ cpcp_api.py build_plan 改五列模块表格输出（CATEGORY_MODULE/SOURCE_LABEL 常量）；④ 工具3 确认门禁描述按 V2.2 修订（实际已移除，本版同步清理残留描述）；⑤ 触发词"确认配置/上线审批/确认上线"对齐。

---

## 1.0 开发范围总述（V2.0 工作流重塑版口径）

- 实现方式（V2.0 起基线）：**11 个子工作流 JSON**——原「Skills 技能包 + 脚本子命令」承载层整体废弃，改为 **11 个子工作流 JSON**（`wf_sub_00~wf_sub_10`，由 `gen_workflows_v2.py` 在 `工作流配置/智能体工作流集V1.6/` 生成）；无意图调度主流程，由**智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别直调**各子工作流；确定性逻辑内嵌为 **type=6 代码节点**（见 1.4 节清单）；后端 14 条能力接口模拟实现**原样保留**（契约不变），并新增 7 个适配端点；
- 原脚本子命令开发项（build_plan/extract_record/poll_test_progress/cpcp_api.py 各子命令）**不再作为独立开发项**——已改为工作流内嵌代码节点与后端端点承载（对应 CODE_GET_TEMPLATE/CODE_RENDER_REQ/CODE_MAP_FIXED_CASES/CODE_EXTRACT_RECORD/CODE_POLL_PROGRESS/CODE_DOWNLOAD_* 等）；
- 13 个工具对应的 HTTP 能力接口**全部自研实现并采用模拟结果输出**，不再对接外部 ApiID；
- 模拟服务统一部署于 `http://10.86.13.201:31281/api/v1/appstore/*`（网关，需确认代理到后端端口 **6174**；已有 Mock 服务框架，其上补齐/改造 14 条路由 + 新增 7 条适配端点）；
- 模拟种子数据 = 《产品信息.txt》全部 **18 个销售品**（5G-A 系列 10 个 + 权益随心选系列 8 个），任一套餐输入均可返回与该销售品资费规则一致的结构化结果；知识源迁至 **`knowledge/`**（原 references/ 废弃，K1~K5 目录对应 `knowledge/K1~K5`）；
- 测试预期值 `presetValue` 取自该销售品在《产品信息.txt》中的规则值；
- 接口契约（路径/入参/出参）以《产销品场景部分能力接口清单.xlsx》为参考基线，后续替换真实实现时契约不变（仅改网关 BASE_URL）。

---

## 1.1 接口开发清单（自研模拟实现，14 条既有路由 + 7 条 V2.0 新增适配端点）

### 1.1.1 需求分析与稽核类

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 工期 |
| --- | --- | --- | --- | --- | --- |
| 1 | 相似度分析 `query_similar_offer` | POST /api/v1/appstore/similar/offer/query | 以 18 销售品构建相似度匹配模拟服务（关键词+资费结构加权打分），**返回相似度最高的1个产品 similarOffer（含 offerInfo 完整产品配置信息——toFields18 同构转换为与需求要素一致的 fields 3 模块/9 分类 24 字段数组，V1.9 重写）** | 任一 18 销售品相关需求均可命中对应销售品（取 score 最高）；businessDesc>5000 字符由上游摘要，接口只校验非空 | 0.5d |
| 2 | 实时规格稽核 `realtime_spec_audit` | POST /api/v1/appstore/audit/realtime | 规则引擎：按配置规范校验必填属性/命名/生效期/销售范围，对照《产品信息.txt》该销售品规则；**同步返回** | pass/error_list/audit_summary 结构完整；支持构造缺陷用例（互斥叠加）返回 pass=0；60s 超时返回 TIMEOUT | 1d |
| 3 | 配置落地 `save_product_config` | POST /api/v1/appstore/product/config/save | 模拟 CRM 写入：内存产品档案（种子 18 销售品）；解析 plan_json 各模块字段；**确认门禁已按 V2.2 移除**（不校验 CONFIRMED 标记，确认语义由智能体识别；保留幂等与 plan_json 合法性校验），方案key由后端从 plan_json 的 req_id 键提取；幂等（同 plan_json 返回已存在 offer_id）；**V2.5 落地成功时按落地配置生成 CRM/billing 落库 SQL 上线脚本（模拟，两段式 /*run@crm*/+/*run@billing*/）并存脚本档案，出参新增 `script_url` 下载链接** | 未确认不触发由智能体保证（后端无 NOT_CONFIRMED 返回）；save_result 各模块分类明细；offer_id 生成规则稳定；script_url 可下载（GET /api/v1/appstore/product/config/script?offer_id={offer_id}，text/plain，未落地 404） | 1d |

### 1.1.2 自动测试类（含受理验证）

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 工期 |
| --- | --- | --- | --- | --- | --- |
| 4 | 测试发起 `offer_test` | POST /api/v1/appstore/test/offer/start | 校验 offerId ∈ 18 销售品；创建模拟测试任务（内存状态机），返回 globalId=50+yyyyMMddHHmmss+10位随机 | 未收录 offerId 返回 4001；globalId 格式校验；防重复发起 | 0.5d |
| 5 | 测试场景 `get_test_scenes` | POST /api/v1/appstore/test/offer/scenes | 按 globalId 返回受理类场景集合（S_O_TC/S_ADD_CARD/S_U_TC），融合/单品/权益套餐按《产品信息.txt》推导场景集合 | 5G-A 融合套餐含副卡加装场景；权益随心选类返回新装+退订 | 0.5d |
| 6 | 测试进度 `get_test_progress` | POST /api/v1/appstore/test/offer/progress | 进度状态机：totalSteps=场景数+2（前 2 步智能匹配场景&用例/资源）；按真实时间推进（默认 60~90s 跑完，可配置加速/卡死用于演示） | done/failed/failIndex 与场景状态映射（0成功/1失败/2中止/NULL进行中）一致；轮询幂等 | 1d |
| 7 | 测试结果 `get_test_result` | POST /api/v1/appstore/test/offer/result | 生成逐场景测点明细：**presetValue 取自《产品信息.txt》该销售品规则值**；testValue 默认与预期一致，支持按配置注入不一致（演示失败分支）；生成受理凭证 orderId/offerInstId | 900102308 与 900117022 两类套餐 presetValue 与源文件逐项一致；orderId/offerInstId 非空；objTestSceneRel AI 总结字段完整 | 1.5d |

### 1.1.3 资费与审批运维类

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 工期 |
| --- | --- | --- | --- | --- | --- |
| 8 | 计费规则校验 `check_billing_rule` | POST /api/v1/appstore/billing/rules/verify | 内置规则引擎（负资费/边界价差/叠加上限/互斥/自定义规则），对照 18 销售品资费结构 | check_scene 四种枚举生效；构造冲突用例 pass=0 且 risk_list 完整 | 1d |
| 9 | 审批推送 `submit_release_approval` | POST /api/v1/appstore/approval/submit | 写入模拟审批状态库（状态机：审批中→产品经理审核→部门主管审批→通过/驳回）；**V1.7 后端硬校验：req_id 入参必填 + 遍历查询 `NodeResultService.latestRecord(req_id,"config"/"spec"/"fee"/"test")` 四条记录全部非空（V1.7 统一键，原 execution_id 参数合并为 req_id）**；幂等（同 offer_id 返回原 approval_id） | 四环节结果缺失时拒绝推送（LLM 跳步发起也被拦截）；状态可被接口13 查询 | 0.5d |
| 10 | 审批进度 `query_approval_status` | GET /api/v1/appstore/approval/status | 从模拟审批状态库按 approval_id（优先）/offer_id 查询最新审批单 | 返回 status/current_node/approver/opinion/update_time；查无单返回明确提示 | 0.5d |
| 11 | 监控查询 `query_product_monitor` | GET /api/v1/appstore/product/monitor | 按 offer_id+日期确定性生成指标（订单量/异常量/差错率/告警列表）；支持 error_count>0 预置演示 | date_range/metric 参数生效；告警列表与接口12 写入记录回显一致 | 0.5d |
| 12 | 异常告警 `send_alert` | POST /api/v1/appstore/alert/send | 生成 alert_id 写入模拟告警库（供监控查询回显闭环） | alarm_level 三级枚举；content 落库 | 0.25d |

### 1.1.4 节点结果存储查询对齐（后端通用 API，改动量小）

> 说明：节点结果存储/查询为后端已有通用 API（不自研），后端契约以 `/api/v1/appstore/result/save`（POST，入参 req_id/node_name/result_json/status）与 `/api/v1/appstore/result/query`（**GET**，入参 req_id/node_name/latest_only，出参 total/list）为准。**存储已落库持久化**：后端由 `NodeResultService`（MyBatis-Plus）写入 `pd_ai_node_results` 表（H2 DDL：`backend-app/src/main/resources/sql/h2/schema-h2.sql`；MySQL DDL：`sql/01_full_schema_ddl.sql` L504 起），服务重启后结果不丢失。存储寻址口径（V1.7 统一键）：全链路唯一批次标识 = req_id（PLAN+yyyyMMddHHmmss+3位随机数，原 plan_id/execution_id 双键合并）；执行方案环节 node_name=requirement，执行主干各环节 node_name=config/spec/fee/test/**report**（上线报告，程序C 存储），同键覆盖写。V2.0 起由工作流内 **type=6 代码节点**（对应 CODE_RENDER_REQ 生成的 req_id/plan_json + 直连后端 result/save、result/query，见 1.4 节）承载，原 cpcp_api.py 子命令层已废弃。

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 工期 |
| --- | --- | --- | --- | --- | --- |
| 13 | 节点结果存储 `save_node_result` | POST /api/v1/appstore/result/save | 后端已有 API 直接复用；入参 req_id/node_name/result_json（status 默认 ok）；同键（req_id+node_name）覆盖；非法 req_id 返回 5002、node_name 为空返回 5003、result_json 超 64KB 返回 5004 | 程序入参与后端契约逐项一致（各程序步骤结束前保存本环节结果：程序A 保存执行方案、程序B 各环节保存 config/spec/fee/test、程序C 保存 report）；保存→按 req_id+node_name 查询 result_json 逐字节一致；服务重启后可查询（持久化） | 0.25d |
| 14 | 节点结果查询 `query_node_result` | GET /api/v1/appstore/result/query | 后端已有 API 直接复用；入参 req_id（必填）/node_name（可选）/latest_only（默认1）；出参 code/msg/total/list（取 list[0].result_json 为结果原文） | 各程序环节 req_id 自查（V1.7 统一键）：req_id=程序入参、node_name=上游环节名；非法 req_id 返回 5002；total=0 时按 E5 处理（"未找到执行方案"） | 0.25d |

### 1.1.5 V2.0 新增适配端点（AppStoreV16Controller，/api/v1/appstore/*）

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

## 1.2 模拟数据工程（V1.6 核心，V1.9 字段键兼容）

| # | 工作项 | 说明 | 工期 |
| --- | --- | --- | --- |
| 1 | 种子数据集 `seed_offers.json` | 从《产品信息.txt》结构化 18 条销售品全量规则（ID/名称/系列/套内资费/套外资费/过渡期资费/副卡/流量结转/断网授权/停机规则/计费周期与付费方式/销售渠道/订购/变更/退订拆机携出）；**V1.9：OfferSeedService.toFields18 重写为 24 字段同构输出（in_fee/out_fee/sub_card/sale_channels/flow_carry_over/net_cutoff_limit/validity/change_rule/transition_fee 等键映射新字段），种子 JSON 键名兼容零改动** | 1d |
| 2 | 规则值映射表 `preset_map.json` | 18 销售品 × 10 测点（P_EFF_DATE/P_EXP_DATE/P_STATUS/P_MAIN_PROD/P_RELY_REL/P_MUTEX_REL/P_ORD_CNT/P_OFFER_NAME/P_OFFER_TYPE/P_PAY_MODE）的预期值映射，供接口7 生成 presetValue | 0.5d |
| 3 | 演示场景开关 | 每销售品支持注入：稽核驳回用例 / 资费冲突用例 / 测试测点不一致用例 / 监控 error_count>0 用例（通过请求参数或配置文件控制，正向演示默认全通过） | 0.5d |
| 4 | 一致性自测脚本 | 遍历 18 销售品逐一调用接口 1/2/4/7/8/11，断言返回结构与规则值一致（对应细化设计 2.4 #11/#12、3.5 #17） | 0.5d |

---

## 1.3 工作流塑造与内嵌代码节点（V2.0：废弃 skills/cpcp-product-worker 与 scripts/cpcp_api.py 子命令层）

> 原「Skills 技能包 + 脚本子命令」承载层整体废弃，确定性逻辑改由 **11 个子工作流 JSON（gen_workflows_v2.py 在 `工作流配置/智能体工作流集V1.6/` 生成）内嵌 type=6 代码节点**承载。11 个子工作流：`wf_sub_00~wf_sub_10`（上报总结/需求分析/稽核/资费校准/自动测试/审批/监控运维/上线单查询/产品查询/嵌套校验等）。无意图调度主流程，由智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别直调。

| # | 工作项 | 说明 | 状态 |
| --- | --- | --- | --- |
| 1 | `gen_workflows_v2.py` 工作流生成器 | 由 Python 脚本统一生成 11 个子工作流 JSON；BASE_URL=网关 `http://10.86.13.201:31281/api/v1/appstore/*`（需确认代理到 6174）；代码节点以 urllib 直连后端适配端点 | ✅ 已生成（`工作流配置/智能体工作流集V1.6/` 11 个 JSON） |
| 2 | 代码节点·方案/字段组装 | CODE_GET_TEMPLATE（模板获取）、CODE_RENDER_REQ（req_id 生成 PLAN+时间戳+3位随机 & plan_json 组装）、CODE_MAP_FIXED_CASES（固定用例映射）、CODE_MERGE_NESTED（嵌套结果合并）、CODE_RENDER_TABLE（五列模块表格渲染） | ✅ 已内嵌 |
| 3 | 代码节点·校验/提取 | CODE_VALIDATE_ELEMENTS（字段要素校验）、CODE_EXTRACT_RECORD（取 list[0].result_json，空报 E5）；对应原 extract_record | ✅ 已内嵌 |
| 4 | 代码节点·轮询/汇总 | CODE_POLL_PROGRESS（测试进度轮询，对应原 poll_test_progress）、CODE_APPROVAL_POLL（审批轮询）、CODE_SUMMARY_APPROVAL（审批汇总）、CODE_FUSION_GROUP_ECHO（融合/单品分组回显） | ✅ 已内嵌 |
| 5 | 代码节点·运维/下载（OP_*） | CODE_OP_ROOT_CAUSE（根因分析）、CODE_OP_CREATE_WO（工单创建）、CODE_OP_SHELF_COMPLIANCE（上架合规）、CODE_OP_QUERY_OFFER（产品查询）、CODE_OP_VALIDATE_NESTED（嵌套校验）、CODE_DOWNLOAD_TEST_REPORT（测试报告下载）、CODE_DOWNLOAD_LAUNCH_SCRIPT（上线脚本下载） | ✅ 已内嵌 |
| 6 | 网关联调 | 网关 BASE_URL 代理至后端 6174 确认；11 个子工作流内代码节点调用 7 个新增适配端点与 14 条既有路由逐一连通 | ⏳ 待后端重启与联调 |

---

## 1.4 工期汇总（与主方案第 7 章对齐，V2.0 工作流重塑版）

| 阶段 | 本清单对应工作项 | 工期 |
| --- | --- | --- |
| 阶段1 基础搭建 | 种子数据集 #1 + 接口骨架 | 3d |
| 阶段2 接口开发 | 接口 1~14 开发与自测 + 数据工程 #2~#4 | 5d |
| 阶段3 知识库建设 | knowledge/K1~K5 目录文档就位（文件复制级，见第 2 章；原 references/ 废弃） | 1d |
| 阶段4 工作流塑造 | 1.3 节 11 个子工作流 JSON 生成（gen_workflows_v2.py）+ type=6 代码节点内嵌 + 后端 7 个适配端点 | 4d |
| 阶段5 部署与联调 | 11 个子工作流导入 + 后端重启（使 6174 生效）+ 网关代理确认 + 智能体语义识别直调/链路联调（见第 2 章相关节） | 4d |
| 阶段6 验证与优化 | 18 套餐一致性自测跑通 + 正反向用例 | 4d |
| **合计** | | **约 21 个工作日**（其中代码开发约 12d；工作流已生成、后端 7 端点已编码，待重启联调） |

---

## 1.5 开发自测 Checklist（V2.0 工作流重塑版）

- [ ] 14 条既有路由 + 7 条新增适配端点单元/契约测试通过（curl/Postman/JUnit，参照《产销品加载AI应用-接口说明书.md》5 节方法）
- [ ] 后端 7 个新增适配端点已编译通过、git 提交（commit ee6f5a8）确认
- [ ] **后端重新部署完成，端口 6174 生效**；网关 BASE_URL=http://10.86.13.201:31281/api/v1/appstore/* 确认代理到 6174
- [ ] 18 销售品一致性自测全绿（任一套餐返回结构化结果、资费规则值一致、无写死单一样例回退）
- [ ] presetValue 抽查：900102308（5G-A）与 900117022（权益随心选）两类套餐与《产品信息.txt》逐项一致
- [ ] 未收录销售品 ID 输入：接口 4 返回 4001，接口 1 返回空列表或明确降级提示，不返回伪造数据
- [ ] 11 个子工作流 JSON 均由 gen_workflows_v2.py 稳定生成（不手工改 JSON），type=6 代码节点内嵌完整
- [ ] 代码节点与后端契约一致：CODE_OP_* / CODE_DOWNLOAD_* / CODE_POLL_PROGRESS 等逐一连通对应端点，路径/入参/出参与细化设计映射表逐条比对（含 PARAM_MISSING/5002/5004 错误码验证）
- [ ] 工具9 四环节门禁（工具层硬校验）：四环节结果不全调 submit_approval 一律拒绝；跳步调用均有拦截记录（工具7 确认门禁已按 V2.2 移除，仅验证幂等与 plan_json 合法性校验）
- [ ] V1.9 字段重构验证：ontology/fields 接口返回 24 字段注册表（3 模块/9 分类）；similar_offer offerInfo 为 24 字段数组；来源仅【原始需求】/【AI补全】两态；仅套餐档位可"待补充"；套餐编码默认"系统待生成"
- [ ] 幂等：工具7 同 plan_json、工具9 同 offer_id 重复提交不产生重复记录
- [ ] 异常注入开关：稽核驳回/资费冲突/测点不一致/监控异常 四类反向用例可复现
- [ ] knowledge/ 知识源就位（K1~K5，原 references/ 废弃）；CODE_OP_QUERY_OFFER/CODE_OP_VALIDATE_NESTED 读取 knowledge/ 目录数据正常

---

# 第 2 章 平台配置清单

> 平台：工作流 JSON 导入（11 个子工作流）+ knowledge/ 知识库挂载 + 后端适配端点就绪
> 版本：V2.0 工作流重塑　日期：2026-09-18
> 依据：《产销品加载AI应用开发方案.md》V3.0、《产销品加载AI应用-细化设计方案.md》V2.4（第 5 章部署清单）、《智能体工作流集V1.6》（11 个子工作流 JSON）
> 用途：数字员工创建表单填写 + 工作流 JSON 导入 + 知识库挂载 + 后端新端点就绪逐项核对。

> **V2.0 工作流重塑履历（本次更新）**：实现载体由「Skills 技能包（skills/cpcp-product-worker，含 SKILL.md/scripts/references + CPCP_BASE_URL 环境变量）」重塑为「11 个子工作流 JSON + knowledge/ 知识库 + 后端适配端点」。
> - **废弃**：skills/cpcp-product-worker 技能包目录、SKILL.md 部署项、scripts/ 自检脚本（test_cpcp_api_local.py、cpcp_api.py）、references/ 知识库、`CPCP_BASE_URL` 环境变量、`wf_main_intent` 意图调度工作流及其 `CODE_DISPATCHER` 确定性意图路由。
> - **新增**：`场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/` 下 11 个子工作流 JSON 导入（wf_sub_00~wf_sub_10）；无意图调度主流程，由智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别直调各 wf_sub_*。
> - **知识库迁至** `knowledge/`（原 references/ 废弃）：K1~K5 五类知识库 + K5 存量切片、templates 模板注册表、ontology-fields.json（本体字段，以 FieldOntologyService 接口为唯一实源）、seed_offer_groups.json（融合组规则）、存量产品目录。
> - **后端新增适配端点**（AppStoreV16Controller 已实现并提交，需后端重启生效）：`/api/v1/appstore/ops/root-cause`、`/ops/work-orders`、`/shelf-compliance`、`/validate-nested`、`/explain`、`/report/download`、`/script/download`。
> - **网关**：`BASE_URL=http://10.86.13.201:31281/api/v1/appstore/*`（需确认代理到 6174）。

---

## 2.1 数字员工创建表单（平台创建助手界面逐字段填写值）

> 本节为在平台上"创建数字员工/助手"时表单各字段的填写值，取值来源见"来源"列（业务口径权威定义见开发方案 3.1/3.2/3.3 节，运行时承载见 3.2 提示词【意图→工作流映射表】直调下各 wf_sub_* 子工作流）。

### 2.1.1 基本信息

| 表单字段 | 填写值 | 来源 |
| --- | --- | --- |
| 助手 ID（标识） | `cpcp_product_worker`（对应智能体按 3.2 提示词【意图→工作流映射表】语义识别直调 11 个子工作流） | 开发方案 3.1 节 |
| 助手名称 | 产销品数字员工 | 开发方案 3.1 节 |
| 助手描述/功能介绍 | 面向产销品域的数字员工，支持从需求提报、需求分析（五列模块表格《加载方案》）、用户确认、智能配置（配置落地）、配置规格稽核、资费校准、自动测试（含受理验证子集）到上线审批（上线校验看板）、监控运维方案、存量合规扫描的全流程自动化操作。 | 开发方案 3.1 节（意图触发描述：当用户提出销售品需求提报、执行方案确认、执行主干触发、上线审批发起、审批进度/运行监控查询、存量合规扫描或产销品业务问答时，由智能体按 3.2 提示词【意图→工作流映射表】语义识别直调对应 wf_sub_*） |
| 发布范围 | 所有人可见 | 开发方案 3.1 节 |
| 模型配置 | 温度 0.2（严谨输出）、多轮对话 20 轮、top_p 适度调小 | 开发方案 3.3 节；细化设计 3.4.6 节 |

### 2.1.2 角色与能力（系统提示词，角色+技能+限制模式）

完整提示词以开发方案 3.2 节为权威（智能体按提示词【意图→工作流映射表】语义识别直调各 wf_sub_*），创建表单按以下三段填写：

**① 角色（填入"角色/人设"栏）：**

```
你是安徽电信产销品域数字员工，负责销售品从需求到上线的端到端自动化加载。你不直接操作 CRM、不代用户做业务决策；全部工作通过工作流 API 编排 + 按需读取知识库完成，你只做意图识别、流程编排与结果解读。意图识别由你按提示词【意图→工作流映射表】语义识别后直调对应 wf_sub_* 子工作流，不猜测业务结论。
```

**② 能力/技能（填入"技能/能力"栏，按意图路由填写，对应 wf_sub_* 子工作流）：**

| 能力项 | 说明 | 子工作流 | 触发意图 |
| --- | --- | --- | --- |
| 需求提报与分析 | 需求要件提取→相似产品→同构合并→本体推理（validate-nested / explain）→build_plan→保存方案，输出《加载方案》五列模块表格（24 字段，来源标【原始需求】/【AI补全】）并等待确认 | wf_sub_01 | 提报需求 / 修改需求 |
| 智能配置与配置落地 | 确认后经本体推理/融合组校验落配置明细，生成配置/上线脚本（script/download）、出配置解释 | wf_sub_02 / wf_sub_03 / wf_sub_06 | 确认配置 / 确认执行 |
| 配置规格稽核 | 实时规格稽核（audit/realtime），输出误差明细与整改建议 | wf_sub_03 | 执行稽核 |
| 资费校准 | 计费规则校验（billing/rules/verify），资费冲突拦截 | wf_sub_05 | 资费校准 |
| 自动测试 | 测试发起/场景/进度/结果轮询（含受理验证子集随测试结果输出），报告生成与下载（report/download） | wf_sub_04 | 自动测试 |
| 上线审批 | 自查四环节结果→上线校验看板（4 项 ✅ 表）→submit_approval 推送→输出审批单号（approval/submit、approval/status） | wf_sub_06 | 上线审批 / 发起审批 |
| 监控运维 | 运行监控查询（product/monitor）、异动根因本体推理（ops/root-cause）、创建处置工单（ops/work-orders） | wf_sub_07 / wf_sub_08 | 运行监控 / 确认上线 |
| 存量合规扫描 | 在架存量产品批量合规扫描（shelf-compliance，R-C* 规则） | wf_sub_10 | 存量合规扫描 |
| 存量销售品查询 | 按存量产品目录（存量产品目录_清洗后.json / K5 存量切片）检索在售销售品 | wf_sub_09 | 存量销售品查询 |
| 业务问答 | 按类别直读知识库：业务规范→K1、资费→K2、测试→K3、存量销售品→K4、高频问答→K5 | 知识库 K1~K5 | 业务规范/资费/测试/存量/FAQ 问答 |

**③ 限制（填入"限制/约束"栏，5 条核心纪律）：**

```
1. 一切系统交互只经工作流 API（BASE_URL=http://10.86.13.201:31281/api/v1/appstore/*）；出参 JSON 逐字引用不加工，成败仅依据出参字段（status / pass / valid / test_passed），严禁语义猜测；输出模板中的 ✅/统计值必须与出参一一对应，禁止补 ✅ 凑数或虚构出参不存在的数据；
2. 执行主干 智能配置→稽核→资费校准→自动测试（含受理验证小节） 必须串行：严禁并行、跳过环节、重复调用已成功环节；失败立即中断引导，不得自行重试或跳过；
3. 未识别到确认类回复（确认配置/确认执行/同意/可以/执行吧等）不得执行智能配置；req_id 以会话最近一次值为准，禁止重新生成；
4. 审批必须在四环节全部成功且用户明确确认后发起（后端四环节硬校验兜底，跳步必被拒）；审批通过后回复"确认上线"生成监控运维方案，严禁在审批通过前生成；
5. 敏感资费与配置明细仅展示摘要；输出遵循：环节名称、执行结果、关键数据、下一步建议（"建议处理：可输入"××"进入【××】。"）。
超范围兜底：抱歉，我仅支持产销品加载相关业务，请更换问题或联系管理员。
```

### 2.1.3 工作流与知识库挂载

| 表单字段 | 填写值 | 来源 |
| --- | --- | --- |
| 工作流集 | 导入 `场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/` 下 11 个子工作流 JSON：`wf_sub_00_~wf_sub_10_`（无意图调度主流程） | 智能体工作流集V1.6（gen_workflows.py / gen_workflows_v2.py 生成） |
| 意图调度入口 | 无（已移除 `wf_main_intent` 意图调度）；由智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别后直调各 `wf_sub_*` 子工作流 | 开发方案 3.2 节提示词 |
| 子工作流 | wf_sub_00~wf_sub_10（需求提报/需求分析/智能配置/稽核/自动测试/资费校准/上线审批/监控运维/进度查询/存量查询/存量合规扫描） | 智能体工作流集V1.6 |
| 知识库挂载 | 挂载 `knowledge/`（原 references/ 废弃）：K1规范(3)/K2资费(2)/K3测试(2)/K4存量(18单文件)/K5FAQ(1) + K5存量切片 + templates 注册表 + ontology-fields.json + seed_offer_groups.json + 存量产品目录_清洗后.json | knowledge/README_知识库挂载说明.md |
| 模板注册表 | `方案/templates/`：_registry.json / _index.json + 6 份 schema + 样例/测试元素 | templates 目录 |
| 后端网关 | `BASE_URL=http://10.86.13.201:31281/api/v1/appstore/*`（需确认代理到 6174） | 细化设计 2.6 节、后端部署说明 |

### 2.1.4 开场白与引导问题

| 表单字段 | 填写值 | 来源 |
| --- | --- | --- |
| 开场白 | 您好，我是产销品数字员工，可以帮您完成销售品从需求提报、加载方案生成、智能配置、规格稽核、资费校准、自动测试（含受理验证）到上线审批的全流程操作。您可以直接描述需求，或发送：- 查询审批进度（需审批单号或销售品ID）- 查询销售品监控结果（需销售品ID）- 重新执行上次失败环节 | 开发方案 3.3 节 |
| 引导问题（3 条） | 1) 查询审批进度（需审批单号或销售品ID）　2) 查询销售品监控结果（需销售品ID）　3) 重新执行上次失败环节 | 开发方案 3.3 节 |

### 2.1.5 常见问题（FAQ，12 条）

> 直接返回=否（需大模型归纳后回复）；来源：`knowledge/K5FAQ/K5FAQ_产销品加载FAQ_V1.0.md`（V1.1，已同步 24 字段口径）。平台 FAQ 表单按 Q/A 逐条录入。

| # | 问题 | 答案要点（完整答案以 K5FAQ 文档为准） |
| --- | --- | --- |
| Q1 | 实时稽核不通过怎么办？ | 执行主干中断，打印异常环节/error_list/整改建议；【修改执行方案】修正后重新执行（推荐）或【重新执行】从失败环节续跑；high 级告警必须整改 |
| Q2 | 如何查询存量产品/销售品信息？ | 提供销售品 ID 或名称即可（例：查一下 900102308 的套外资费），从 K4/K5 存量切片检索回复并标注命中文档名；覆盖 18 个在售销售品 |
| Q3 | 为什么有的字段是"待补充"？ | 仅价格类字段（套餐档位）未提供且知识库无参照时填"待补充"（禁止推理价格，避免资费风险）；其余缺失字段基于相似销售品推理补全标"AI补全" |
| Q4 | 执行到一半失败了，需要从头再来吗？ | 不需要。按环节保存结果，【重新执行】从失败环节续跑，已成功环节直接回放；【修改执行方案】先修订再继续 |
| Q5 | 测试报告显示"未获取到受理凭证，需人工核实"是失败吗？ | 不是。测试已通过，仅无法完成受理单据核对；按测试流水号（globalId）人工核实，不影响测试结论与审批 |
| Q6 | 上线审批为什么被拒绝发起？ | 审批发起有双重门禁：①须四环节（智能配置/稽核/资费校准/自动测试）全部成功（后端硬校验兜底）；②须用户明确确认后提交；未经确认返回 NOT_CONFIRMED，回复【上线审批】确认后再提交 |
| Q7 | 如何查询审批进度？ | 提问"查一下 {销售品ID} 的审批进度"（或提供审批单号），返回审批状态（审批中/通过/驳回）与节点信息；未找到审批单请先确认是否已发起 |
| Q8 | 权益随心选可以同时订两个版本吗？ | 不可以。同一号码仅可订购 1 个权益随心选（娱乐/生活/出行/商超互斥）；融合成员不共享包内流量，但可单独订购 |
| Q9 | 5G-A 套餐订购当月怎么计费？ | 新入网立即生效，当月过渡期资费：月基本费按日计扣四舍五入到分，语音/流量按天折算向上取整；老用户订购次月 1 日生效 |
| Q10 | 套外流量怎么收费？会不会一直扣？ | 阶梯模式：前 100MB 0.03元/MB，达 3 元赠送 924MB（3元/1GB），超出按 3元/1GB 计收；600 元断网授权保护，次月初自动开通（申请继续使用除外） |
| Q11 | 哪些销售品支持副卡？ | 5G-A 系列允许副卡，可共享语音/流量/网速/卫星权益（应用权益不共享）；副卡功能费与张数由省公司配置；权益随心选不涉及副卡 |
| Q12 | 数字员工支持什么，不支持什么？ | 支持：需求分析与加载方案生成（五列模块表格）、确认配置后自动执行（智能配置→稽核→资费校准→自动测试，受理验证为自动测试子集随测试结果输出）、上线校验看板与审批发起、监控运维方案与运行监控查询、存量合规扫描、存量问答。不支持范围外问题（如普通客服咨询），返回拒答话术 |

### 2.1.6 表单填写核对要点

- [ ] 助手 ID/名称/描述与 2.1.1 一致；发布范围"所有人可见"；
- [ ] 角色与限制逐字引用 2.1.2（核心纪律 5 条 + 拒答话术不得删改）；
- [ ] 能力项与意图路由与 wf_sub_* 子工作流一一对应（执行主干为四环节，受理验证为自动测试子集；超范围问题命中拒答话术）；
- [ ] 11 个子工作流 JSON 已导入，无意图调度主流程，由智能体按 3.2 提示词【意图→工作流映射表】语义识别直调各 wf_sub_*；
- [ ] 知识库 `knowledge/` 已挂载（含 templates 注册表、ontology-fields.json、seed_offer_groups.json、存量产品目录）；
- [ ] 开场白与 3 条引导问题与 2.1.4 逐字一致；
- [ ] FAQ 12 条录入完成，直接返回=否；抽查 Q2/Q10 回答命中 K4/K5 对应描述；Q3/Q6/Q12 为 V3.0 口径（套餐档位/AI补全、双重门禁、四环节含受理验证子集+监控运维方案）。

---

## 2.2 后端服务就绪（含 V2.0 新增适配端点，先于工作流部署）

后端模拟服务统一部署于网关 `http://10.86.13.201:31281/api/v1/appstore/*`（需确认代理到 6174）。

| # | 就绪项 | 核对要点 |
| --- | --- | --- |
| 1 | 14 工具模拟路由（自研模拟实现，V1.6 口径） | similar/offer/query、audit/realtime、test/offer/start\|scenes\|progress\|result、product/config/save、billing/rules/verify、approval/submit\|status、product/monitor、alert/send、ontology/fields 共 14 条路由连通；模拟种子数据兼容《产品信息.txt》18 个销售品 |
| 2 | 节点结果存储查询（后端通用 API） | POST `/result/save`、GET `/result/query`；NodeResultService（MyBatis-Plus）落库 `pd_ai_node_results` 表（H2/MySQL 双 DDL 已执行），服务重启结果不丢失 |
| 3 | 后端硬校验 | req_id 格式校验（PLAN\d{17}，非法返回 5002）；requirement 环节同键重写一律删除旧记录后重新插入（同键覆盖，原 5006 冲突拦截已移除）；工具7 确认门禁已移除（confirmed 任意值可落地，保留幂等与 plan_json 合法性校验，NOT_CONFIRMED 不再出现）；工具9 校验 req_id 四环节（config/spec/fee/test）结果齐全，缺失拒绝推送 |
| 4 | **V2.0 新增适配端点**（AppStoreV16Controller 已实现并提交，**需后端重启生效**） | `/ops/root-cause`（异动根因本体推理，wf_sub_07 节点706）；`/ops/work-orders`（创建处置工单，wf_sub_07 节点708）；`/shelf-compliance`（存量合规扫描，wf_sub_10 节点1002）；`/validate-nested`（嵌套报文本体校验，wf_sub_01 节点109）；`/explain`（配置业务解释，flow-A）；`/report/download`（测试报告下载，wf_sub_04 节点316）；`/script/download`（配置/上线脚本下载，wf_sub_06 节点621） |
| 5 | 模拟数据工程 | 种子数据集 seed_offers.json（18 销售品全量规则）、preset_map.json（18×10 测点预期值）、演示场景开关（稽核驳回/资费冲突/测点不一致/监控异常可注入）、18 套餐一致性自测脚本全绿；seed_offer_groups.json（融合组规则）供 wf_sub_02/03/05 融合组校验引用 |

**后端连通自测（V2.0 新端点需模拟服务在线并已重启生效）：**
```bash
# V1.6 既有端点
curl -X POST ${BASE_URL}/similar/offer/query -d '{"desc":"5G-A 单品套餐 月费199元 30G流量"}'
curl ${BASE_URL}/product/monitor?offer_id=900102308
curl ${BASE_URL}/result/query?req_id=PLAN20260913143025087

# V2.0 新增适配端点（需后端重启生效）
curl -X POST ${BASE_URL}/ops/root-cause        -d '{"offer_id":"900102308"}'
curl -X POST ${BASE_URL}/ops/work-orders       -d '{"offer_id":"900102308"}'
curl -X POST ${BASE_URL}/shelf-compliance      -d '{"offering_ids":["900102308","900113043"]}'
curl -X POST ${BASE_URL}/validate-nested       -d '{"template_id":"personMainPrc","payload":{}}'
curl -X POST ${BASE_URL}/explain                -d '{"trace_id":"xxx"}'
curl -X POST ${BASE_URL}/report/download       -d '{"record_id":"<globalId>"}'
curl -X POST ${BASE_URL}/script/download       -d '{"offer_id":"900102308"}'
```
- [ ] 14 工具路由 + 7 个 V2.0 适配端点逐一连通后端（含 backend_pending=0 校验、error_list / download_url 出参验证）
- [ ] 后端已重启，新端点在网，代理到 6174 已确认

---

## 2.3 工作流与知识库挂载

### 2.3.1 工作流 JSON 导入（`场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/`）

| # | 部署项 | 配置值 | 核对要点 |
| --- | --- | --- | --- |
| 1 | 意图调度主流程 | 无（不导入 `wf_main_intent`，意图调度主流程已移除） | 由智能体（LLM）按 3.2 提示词【意图→工作流映射表】语义识别直调各 wf_sub_*；无唯一入口主流程 |
| 2 | 子工作流 | 导入 `wf_sub_00_~wf_sub_10_` 共 11 个（需求提报/需求分析/智能配置/稽核/自动测试/资费校准/上线审批/监控运维/进度查询/存量查询/存量合规扫描） | 每份子工作流"步骤=原节点"与细化设计 3.1 映射索引一致；由智能体按 3.2 提示词【意图→工作流映射表】语义识别直调 |
| 3 | 工作流一致性 | `gen_workflows.py` / `gen_workflows_v2.py` 生成的 11 份 JSON 与后端端点契约（AppStoreV16Controller）逐字段对齐；**源码驱动纪律**：JSON 为生成器产物，禁止手改 `智能体工作流集V1.6/*.json`，改业务→改生成器→重新生成→全量校验 ALL_OK | 出参 snake_case（契约明文），业务字段 camelCase 与既有导出契约保持一致 |

### 2.3.2 知识库挂载（`knowledge/`，原 references/ 废弃）

| # | 挂载项 | 核对要点 |
| --- | --- | --- |
| 1 | K1 业务规范库 | 3 份（销售品管理办法/销售品配置规范/销售品测试规范），wf_sub_01 节点 2/4 引用 |
| 2 | K2 资费规则库 | 2 份（资费模型速查/资费与优惠约束说明），wf_sub_05 节点 3 引用 |
| 3 | K3 测试规范库 | 2 份 V2.0（自动化测试点设计规范/自动化测试报告模板），wf_sub_04 节点 6 引用 |
| 4 | K4 存量销售品资料库 | 18 份单文件（按销售品 ID 精确定位，禁止全量读取）；K4 用途边界：仅为 AI 补全字段参考，不作为字段实源 |
| 5 | K5 FAQ | 1 份（K5FAQ），通用直接问答 |
| 6 | K5 存量切片 | 18 个销售品 {ID}.json（接口携带原文）+ {ID}.md（可读摘要）+ 报告；由 skill references/flow-D 迁移而来 |
| 7 | 模板注册表 | `方案/templates/`：_registry.json / _index.json + 6 份 schema + 样例 + 测试元素（供 wf_sub_01 validate-nested 引用） |
| 8 | 本体字段 | ontology-fields.json/.md（18 字段定义：枚举/格式/默认值/兜底口径）；V2.1 起以 FieldOntologyService（/ontology/fields、/validate-nested、/explain）为唯一实源，知识库与文档为历史参考 |
| 9 | 融合组规则 | seed_offer_groups.json（融合组级校验，wf_sub_02/03/05 引用）；存量产品目录_清洗后.json（18 个存量目录，COD_OP_QUERY_OFFER 内嵌实源，wf_sub_09 使用） |
| 10 | 知识库制作 | 修改源头数据后执行 `python knowledge\gen_k4_slices.py`（源：《产品信息.txt》）；K4 上传 18 份并确认切片完成（每份 13 或 2 个切片） |

### 2.3.3 网关与模型纪律

| # | 部署项 | 配置值 | 核对要点 |
| --- | --- | --- | --- |
| 1 | 后端网关 | `BASE_URL=http://10.86.13.201:31281/api/v1/appstore/*` | 指向后端模拟服务（代理到 6174 需确认）；替换真实实现仅改此值，工作流零改动 |
| 2 | 模型纪律 | 温度 0.2（严谨输出）；**LLM 节点默认模型 `qwen3-30b-a3b`**（由 `llm_node` 生成器统一写入各工作流 JSON 的 `model` 字段，平台按此模型执行） | 出参逐字引用不加工；仅依据出参字段（status/pass/valid/test_passed/backend_pending）判成败；LLM 节点 `model` 字段值与明示默认模型一致 |

---

## 2.4 部署后自测

### 2.4.1 工作流导入与后端就绪自测
- [ ] 11 个子工作流 JSON 全部导入成功，无意图调度主流程，由智能体按 3.2 提示词【意图→工作流映射表】语义识别直调；
- [ ] 7 个 V2.0 适配端点自测通过（2.2 节 curl 命令全部 backend_pending=0）；
- [ ] 知识库 5 类（K1~K5）+ K5 存量切片 + templates + ontology-fields.json + seed_offer_groups.json 挂载完整。

### 2.4.2 按需加载验证
- [ ] 智能体按 3.2 提示词【意图→工作流映射表】语义识别直调，常驻不膨胀；命中意图仅直调对应单份 wf_sub_* 子工作流；
- [ ] K4 仅按 offer_id 读取单文件（禁止全量读取 18 份）；
- [ ] 大报文（plan_json/config_json/fields/report）一律走后端 save/query 或 `--file` 类端点文件传参，不经模型上下文中转。

### 2.4.3 意图路由联调（对齐 3.2 提示词【意图→工作流映射表】智能体语义识别直调）
- [ ] 提报/修改需求 → 直调 wf_sub_01（需求分析，含 validate-nested / explain，出口A 不保存不产出 req_id）；
- [ ] 确认执行 / 重新执行 → 直调 wf_sub_02/03/05/04（四环节串行环节1→2→3→4，每环节打印结果）；
- [ ] 发起审批 → 直调 wf_sub_06（仅四环节全成且用户明确确认后）；
- [ ] 查询审批进度 / 运行监控 → 直调 wf_sub_08 / wf_sub_07（轻量支线，缺失参数先追问不编造）；
- [ ] 存量合规扫描 → 直调 wf_sub_10（shelf-compliance）；存量销售品查询 → 直调 wf_sub_09；
- [ ] 业务问答 → 按类别直读 K1~K5；超范围 → 拒答话术。

### 2.4.4 关键链路联调（对齐细化设计 3.5 程序级用例）
- [ ] 正向全流程：需求分析→确认→四环节串行（不停顿）→成功详情+审批提示→审批单生成；
- [ ] 续跑专项：稽核驳回中断→【重新执行】从失败环节续跑（已成功环节凭存储回放，不重复调用写接口）；
- [ ] 硬校验专项：四环节不全调 `approval/submit` 被后端拒绝；
- [ ] 监控运维专项：product/monitor → ops/root-cause → ops/work-orders 链路（wf_sub_07）输出归因/工单；
- [ ] 细化设计 3.5 节程序级用例 #1~#21 全部通过（含四环节硬校验、十八套餐兼容）。

### 2.4.5 配置规范符合性自测（对齐《SitechAI开发平台配置规范.md》）
- [ ] **源码驱动**：11 个子工作流 JSON 均由 `gen_workflows_v2.py` 生成；改业务逻辑后重新生成并对 11 份 JSON 做全量结构校验，全部 ALL_OK；无一例手改 JSON；
- [ ] **节点契约**：全库仅使用 type 0/1/2/3/6/9/13，无未枚举类型、无平台循环节点（轮询仅 CODE_POLL_PROGRESS asyncio.sleep 5s×360/30min）；
- [ ] **入参两态/引用三层一致**：抽查跨节点引用，blockID、nameValue[0]、currValue 前缀=上游 id，`nameValue[1]`=上游 id+出参名（snake_case）；array 出参均配 item 树（ARRAY_ITEM_FIELDS 白名单）；
- [ ] **条件分支**：全部 2 条件分支 sourcePort=-1（否则）/0（如果）正确；
- [ ] **nid**：满足 `a1b2c3d4-0000-4000-8000-{12位seq}` 形态且 seq 按子流分段；
- [ ] **代码节点**：type=6 均 `async def main(args)`/`args.params`，urllib.request 用 `BASE_URL` 占位符经网关访问 `/api/v1/appstore/*`，JSON 出参名带 `_json` 存 JSON 字符串，后端不可达一律 `backend_pending=1` 优雅回退（离线 Demo 可跑）；
- [ ] **LLM 节点纪律**：`model=qwen3-30b-a3b`、温度 0.2、top_p 0.5、max_tokens 2048，提示词末尾"仅输出对应出参"；
- [ ] **req_id**：统一 PLAN+yyyyMMddHHmmss+3 位随机，由代码节点系统时钟生成、LLM 不参与。

---

## 2.5 部署完成 Checklist（按顺序勾选）

**阶段A 后端就绪（代码开发见第 1 章）**
- [ ] 数字员工创建表单已按 2.1 节逐字段填写（2.1.6 核对要点全部勾选）
- [ ] 14 条路由 + 节点结果存储（save/query）连通自测通过
- [ ] **7 个 V2.0 适配端点已重启生效并连通自测通过（2.2 节）**
- [ ] 后端硬校验就绪（5002/四环节门禁/幂等；requirement 同键重写覆盖；工具7 确认门禁已按 V2.2 移除并验证）
- [ ] 18 套餐一致性自测脚本全绿；presetValue 抽查（900102308/900117022）与《产品信息.txt》一致

**阶段B 工作流与知识库挂载**
- [ ] 数字员工创建完成（表单 2.1 节），11 个子工作流 JSON 导入完成（2.3.1 节逐项核对）
- [ ] 知识库 5 类 + K5 存量切片 + templates + ontology-fields.json + seed_offer_groups.json 挂载完成（2.3.2 节逐项核对）
- [ ] K4 18 个销售品单文件齐全；固定问答回归 3 组通过（例：查 900102308 套外资费 → 命中阶梯计费描述）
- [ ] 配置规范符合性自测 2.4.5 节逐项勾选（源码驱动 / 节点契约 / 引用三层一致 / sourcePort / nid / 代码节点 / LLM 纪律 / req_id 均由生成器与配置对齐保证）

**阶段C 联调**
- [ ] 智能体按 3.2 提示词【意图→工作流映射表】语义识别直调全部联调通过（2.4.3 节逐项勾选）
- [ ] 各 wf_sub_* 子工作流链路验证通过（与后端 V1.6+V2.0 端点契约一致，映射见细化设计 3.1 节）
- [ ] 续跑/硬校验/监控运维专项通过（2.4.4 节）

**阶段D 验收**
- [ ] 细化设计 3.5 节用例 #1~#21 全部通过
- [ ] 按《产销品加载AI应用-端到端演示剧本》完成全流程彩排（含反向分支速查表 10 项）
- [ ] 上线：11 个子工作流 JSON 与 knowledge/ 纳入 git 版本管理；后续替换真实实现仅改 `BASE_URL`，工作流 JSON/知识库零改动
