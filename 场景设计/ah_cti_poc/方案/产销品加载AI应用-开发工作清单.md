# 产销品加载 AI 应用 · 开发工作清单

> 平台：Skills 技能包（`cpcp-product-worker`）+ 后端模拟服务
> 版本：V1.10　日期：2026-09-14
> 依据：《产销品加载AI应用开发方案.md》V2.9（1.4 节实现方式说明）、《产销品加载AI应用-细化设计方案.md》V2.3、《skills/Skills技能包实现方案.md》V1.3
> 用途：需要**代码开发**的接口/服务/数据/脚本工作清单（技能包部署与自测见《平台配置清单》，本清单不含）
>
> V1.10 变更（2026-09-14）：**配置上线脚本下载链接**——① 接口3 save_product_config 落地成功时后端生成 CRM/billing 落库 SQL 上线脚本（模拟，两段式 /*run@crm*/+/*run@billing*/）并存脚本档案，出参新增 `script_url`；② 新增附带下载路由 GET `/api/v1/appstore/product/config/script`（text/plain，未落地 404）；③ flow-B 环节1 输出模板新增"配置上线脚本下载链接"行。
> V1.9 变更（2026-09-14）：字段体系全量重构对齐 V3.0 口径——① 接口1 出参 offerInfo fields 由四类18字段改 3 模块/9 分类 24 字段（toFields18 重写）；② 后端 FieldOntologyService 字段注册表重构（套餐档位唯一待补充项、套餐编码默认"系统待生成"、来源两态【原始需求】/【AI补全】）；③ cpcp_api.py build_plan 改五列模块表格输出（CATEGORY_MODULE/SOURCE_LABEL 常量）；④ 工具3 确认门禁描述按 V2.2 修订（实际已移除，本版同步清理残留描述）；⑤ 触发词"确认配置/上线审批/确认上线"对齐。

---

## 0. 开发范围总述（V1.8 Skills 版口径）

- 实现方式（V2.6 起基线）：**Skills 技能包**——原"平台工作流编排 + 插件市场录入"层不再开发，改为**脚本层开发**（`skills/cpcp-product-worker/scripts/`，已完成）；后端 14 条能力接口模拟实现**原样保留**（契约不变）；
- 13 个工具对应的 HTTP 能力接口**全部自研实现并采用模拟结果输出**，不再对接外部 ApiID；
- 模拟服务统一部署于 `http://10.86.13.201:31281`（已有 Mock 服务框架，V1.6 在其上补齐/改造 14 条路由）；
- 模拟种子数据 = 《产品信息.txt》全部 **18 个销售品**（5G-A 系列 10 个 + 权益随心选系列 8 个），任一套餐输入均可返回与该销售品资费规则一致的结构化结果；
- 测试预期值 `presetValue` 取自该销售品在《产品信息.txt》中的规则值；
- 接口契约（路径/入参/出参）以《产销品场景部分能力接口清单.xlsx》为参考基线，后续替换真实实现时契约不变（仅改脚本 `CPCP_BASE_URL`）。

---

## 1. 接口开发清单（自研模拟实现，14 条路由）

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

> 说明：节点结果存储/查询为后端已有通用 API（不自研），后端契约以 `/api/v1/appstore/result/save`（POST，入参 req_id/node_name/result_json/status）与 `/api/v1/appstore/result/query`（**GET**，入参 req_id/node_name/latest_only，出参 total/list）为准。**存储已落库持久化**：后端由 `NodeResultService`（MyBatis-Plus）写入 `pd_ai_node_results` 表（H2 DDL：`backend-app/src/main/resources/sql/h2/schema-h2.sql`；MySQL DDL：`sql/01_full_schema_ddl.sql` L504 起），服务重启后结果不丢失。存储寻址口径（V1.7 统一键）：全链路唯一批次标识 = req_id（PLAN+yyyyMMddHHmmss+3位随机数，原 plan_id/execution_id 双键合并）；执行方案环节 node_name=requirement，执行主干各环节 node_name=config/spec/fee/test/**report**（上线报告，程序C 存储），同键覆盖写。V1.8 起由脚本子命令 `cpcp_api.py save_node_result / query_node_result` 直连调用（封装见第 3 节 #1）。

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 工期 |
| --- | --- | --- | --- | --- | --- |
| 13 | 节点结果存储 `save_node_result` | POST /api/v1/appstore/result/save | 后端已有 API 直接复用；入参 req_id/node_name/result_json（status 默认 ok）；同键（req_id+node_name）覆盖；非法 req_id 返回 5002、node_name 为空返回 5003、result_json 超 64KB 返回 5004 | 程序入参与后端契约逐项一致（各程序步骤结束前保存本环节结果：程序A 保存执行方案、程序B 各环节保存 config/spec/fee/test、程序C 保存 report）；保存→按 req_id+node_name 查询 result_json 逐字节一致；服务重启后可查询（持久化） | 0.25d |
| 14 | 节点结果查询 `query_node_result` | GET /api/v1/appstore/result/query | 后端已有 API 直接复用；入参 req_id（必填）/node_name（可选）/latest_only（默认1）；出参 code/msg/total/list（取 list[0].result_json 为结果原文） | 各程序环节 req_id 自查（V1.7 统一键）：req_id=程序入参、node_name=上游环节名；非法 req_id 返回 5002；total=0 时按 E5 处理（"未找到执行方案"） | 0.25d |

---

## 2. 模拟数据工程（V1.6 核心，V1.9 字段键兼容）

| # | 工作项 | 说明 | 工期 |
| --- | --- | --- | --- |
| 1 | 种子数据集 `seed_offers.json` | 从《产品信息.txt》结构化 18 条销售品全量规则（ID/名称/系列/套内资费/套外资费/过渡期资费/副卡/流量结转/断网授权/停机规则/计费周期与付费方式/销售渠道/订购/变更/退订拆机携出）；**V1.9：OfferSeedService.toFields18 重写为 24 字段同构输出（in_fee/out_fee/sub_card/sale_channels/flow_carry_over/net_cutoff_limit/validity/change_rule/transition_fee 等键映射新字段），种子 JSON 键名兼容零改动** | 1d |
| 2 | 规则值映射表 `preset_map.json` | 18 销售品 × 10 测点（P_EFF_DATE/P_EXP_DATE/P_STATUS/P_MAIN_PROD/P_RELY_REL/P_MUTEX_REL/P_ORD_CNT/P_OFFER_NAME/P_OFFER_TYPE/P_PAY_MODE）的预期值映射，供接口7 生成 presetValue | 0.5d |
| 3 | 演示场景开关 | 每销售品支持注入：稽核驳回用例 / 资费冲突用例 / 测试测点不一致用例 / 监控 error_count>0 用例（通过请求参数或配置文件控制，正向演示默认全通过） | 0.5d |
| 4 | 一致性自测脚本 | 遍历 18 销售品逐一调用接口 1/2/4/7/8/11，断言返回结构与规则值一致（对应细化设计 2.4 #11/#12、3.5 #17） | 0.5d |

---

## 3. 技能包脚本层开发（V1.8：替代原"工作流与调度配套开发"；已全部完成）

> 原平台工作流编排/插件录入/代码节点开发随工作流方式废止，承载方式改为**技能包脚本层**（对应原 3 类代码节点 + 插件封装层 + LLM 调度约定）。以下脚本均已实现并通过本地自测。

| # | 工作项 | 说明 | 状态 |
| --- | --- | --- | --- |
| 1 | `scripts/cpcp_api.py` 统一 API 客户端（17 子命令） | 承接原 14 个插件工具封装层：请求侧**裸报文**（业务参数 JSON 置于顶层，V2.7 起 contractRoot/tcpCont 包裹整体移除）、出参侧 `_unwrap` 兼容解包、超时重试（同步 60s/异步 30s、save_product_config 不自动重试）、错误码归一（PARAM_MISSING/HTTP_xxx/NET_ERROR/TIMEOUT/PARSE_ERROR/ONTOLOGY_EMPTY）；含 `build_plan`（承接原 004a 拆分代码节点：req_id 系统生成 PLAN+时间戳+3位随机、plan_json 三键组装、**plan_md 五列模块表格代码生成（模块/分类/字段名称/字段值/备注，CATEGORY_MODULE 归并+同模块/同分类合并展示，V1.9 重写）**、pending_fields 反查、SOURCE_LABEL 来源两态【原始需求】/【AI补全】）与 `extract_record`（承接原 CODE_EXTRACT_RECORD：提取 list[0].result_json，list 空报 E5）本地逻辑；节点结果存储查询直连后端（64KB 前置校验 5004）；大报文支持 `--xxx-file` 文件传参 | ✅ 已完成（编译通过、本地自测 8 项通过） |
| 2 | `scripts/poll_test_progress.py` 测试进度轮询（承接原 wf_sub_04 代码节点 0304） | 间隔 5s、最多 360 次（超时 30 分钟）、连续 5 次查询失败终止转人工（保留 globalId）；退出码 0=done / 1=failed / 2=连续失败 / 3=超时，输出 fail_reason 供程序分支判定 | ✅ 已完成 |
| 3 | `scripts/test_cpcp_api_local.py` 本地功能自测 | 不依赖后端 8 项断言（裸报文契约 mock 回显/出参解包归一/错误码归一/build_plan 唯一 req_id/E5/枚举缺参 64KB 前置校验） | ✅ 已完成（8 项通过） |
| 4 | LLM 调度层配套（SKILL.md + flow 文档程序约束，无独立代码） | 意图路由 5 类（含单环节点播：执行稽核/资费校准/自动测试/受理验证）、确认语义识别（触发词"确认配置"，V2.2：无需写 CONFIRMED 标记）、程序B 串行纪律（严禁并行/跳步/重复调用写接口，5 环节=四环节+受理验证）、每环节结果打印（"建议处理"引导话术；✅/统计值与出参一一对应）、上线审批触发词、确认上线→监控运维方案（审批通过后）、fail_node 续跑映射（STAGE1~4→环节1~4）；全部由 SKILL.md 核心纪律 5 条与 flow-A~D 文档固化 | ✅ 已完成（文档） |

---

## 4. 工期汇总（与主方案第 7 章对齐，V1.8 Skills 版）

| 阶段 | 本清单对应工作项 | 工期 |
| --- | --- | --- |
| 阶段1 基础搭建 | 种子数据集 #1 + 接口骨架 | 3d |
| 阶段2 接口开发 | 接口 1~14 开发与自测 + 数据工程 #2~#4 | 5d |
| 阶段3 知识库建设 | references/K1~K5 目录文档就位（文件复制级，见平台配置清单） | 1d |
| 阶段4 技能包脚本层 | 本清单第 3 节 3 项脚本（已完成）+ flow-A~D 流程文档改写 | 4d |
| 阶段5 部署与联调 | 技能包注册 + 后端连通自测 + 意图路由/链路联调（见平台配置清单 3 节） | 4d |
| 阶段6 验证与优化 | 18 套餐一致性自测跑通 + 正反向用例 | 4d |
| **合计** | | **约 21 个工作日**（其中代码开发约 12d，脚本层已全部完成） |

---

## 5. 开发自测 Checklist（V1.8 Skills 版）

- [ ] 14 条路由单元/契约测试通过（curl/Postman/JUnit，参照《产销品加载AI应用-接口说明书.md》5 节方法）
- [ ] 18 销售品一致性自测脚本全绿（任一套餐返回结构化结果、资费规则值一致、无写死单一样例回退）
- [ ] presetValue 抽查：900102308（5G-A）与 900117022（权益随心选）两类套餐与《产品信息.txt》逐项一致
- [ ] 未收录销售品 ID 输入：接口 4 返回 4001，接口 1 返回空列表或明确降级提示，不返回伪造数据
- [ ] `test_cpcp_api_local.py` 7 项本地自测全绿（脚本层：三类报文解包/错误码归一/build_plan/extract_record/前置校验）
- [ ] 脚本与后端契约一致：17 子命令逐一连通后端，路径/入参/出参与细化设计 2.6 节映射表逐条比对（含 PARAM_MISSING/5002/5006/5004 错误码验证）
- [ ] 工具9 四环节门禁（工具层硬校验）：四环节结果不全调 submit_approval 一律拒绝；跳步调用均有拦截记录（工具7 确认门禁已按 V2.2 移除，仅验证幂等与 plan_json 合法性校验）
- [ ] V1.9 字段重构验证：ontology/fields 接口返回 24 字段注册表（3 模块/9 分类）；similar_offer offerInfo 为 24 字段数组；来源仅【原始需求】/【AI补全】两态；仅套餐档位可"待补充"；套餐编码默认"系统待生成"
- [ ] 幂等：工具7 同 plan_json、工具9 同 product_id 重复提交不产生重复记录
- [ ] 异常注入开关：稽核驳回/资费冲突/测点不一致/监控异常 四类反向用例可复现
- [ ] poll_test_progress.py 退出码验证：done→0 / failed→1 / 连续失败→2 / 超时→3，fail_reason 出参非空
