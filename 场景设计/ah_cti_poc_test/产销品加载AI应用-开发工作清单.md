# 产销品加载 AI 应用 · 开发工作清单

> 平台：AI应用开发（九思大模型 · 低代码智能体平台）
> 版本：V1.6　日期：2026-09-12
> 依据：《产销品加载AI应用开发方案.md》V1.6（第 7 章开发实施计划）、《产销品加载AI应用-细化设计方案.md》V1.2
> 用途：需要**代码开发**的接口/服务/数据工作清单（平台界面配置类工作见《平台配置清单》，本清单不含）

---

## 0. 开发范围总述（V1.6 口径）

- 13 个插件工具对应的 HTTP 能力接口**全部自研实现并采用模拟结果输出**，不再对接外部 ApiID；
- 模拟服务统一部署于 `http://10.86.13.201:31281`（已有 Mock 服务框架，V1.6 在其上补齐/改造 14 条路由）；
- 模拟种子数据 = 《产品信息.txt》全部 **18 个销售品**（5G-A 系列 10 个 + 权益随心选系列 8 个），任一套餐输入均可返回与该销售品资费规则一致的结构化结果；
- 测试预期值 `presetValue` 取自该销售品在《产品信息.txt》中的规则值；
- 接口契约（路径/入参/出参）以《产销品场景部分能力接口清单.xlsx》为参考基线，后续替换真实实现时契约不变。

---

## 1. 接口开发清单（自研模拟实现，14 条路由）

### 1.1 需求分析与稽核类

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 工期 |
| --- | --- | --- | --- | --- | --- |
| 1 | 相似度分析 `query_similar_offer` | POST /api/v1/similar/offer/query | 以 18 销售品构建相似度匹配模拟服务（关键词+资费结构加权打分），返回 similarOfferList | 任一 18 销售品相关需求均可命中对应销售品（score 降序）；businessDesc>5000 字符由上游摘要，接口只校验非空 | 0.5d |
| 2 | 实时规格稽核 `realtime_spec_audit` | POST /api/v1/audit/realtime | 规则引擎：按配置规范校验必填属性/命名/生效期/销售范围，对照《产品信息.txt》该销售品规则；**同步返回** | pass/error_list/audit_summary 结构完整；支持构造缺陷用例（互斥叠加）返回 pass=0；60s 超时返回 TIMEOUT | 1d |
| 3 | 配置落地 `save_product_config` | POST /api/v1/product/config/save | 模拟 CRM 写入：内存产品档案（种子 18 销售品）；解析 plan_json 四类字段；**内部二次校验 confirmed==true**；幂等（同 plan_json 返回已存在 offer_id） | 非 confirmed 返回 NOT_CONFIRMED 且无写入；save_result 四类分类明细；product_id/offer_id 生成规则稳定 | 1d |

### 1.2 自动测试类（含受理验证）

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 工期 |
| --- | --- | --- | --- | --- | --- |
| 4 | 测试发起 `offer_test` | POST /api/v1/test/offer/start | 校验 offerId ∈ 18 销售品；创建模拟测试任务（内存状态机），返回 globalId=50+yyyyMMddHHmmss+10位随机 | 未收录 offerId 返回 4001；globalId 格式校验；防重复发起 | 0.5d |
| 5 | 测试场景 `get_test_scenes` | POST /api/v1/test/offer/scenes | 按 globalId 返回受理类场景集合（S_O_TC/S_ADD_CARD/S_U_TC），融合/单品/权益套餐按《产品信息.txt》推导场景集合 | 5G-A 融合套餐含副卡加装场景；权益随心选类返回新装+退订 | 0.5d |
| 6 | 测试进度 `get_test_progress` | POST /api/v1/test/offer/progress | 进度状态机：totalSteps=场景数+2（前 2 步智能匹配场景&用例/资源）；按真实时间推进（默认 60~90s 跑完，可配置加速/卡死用于演示） | done/failed/failIndex 与场景状态映射（0成功/1失败/2中止/NULL进行中）一致；轮询幂等 | 1d |
| 7 | 测试结果 `get_test_result` | POST /api/v1/test/offer/result | 生成逐场景测点明细：**presetValue 取自《产品信息.txt》该销售品规则值**；testValue 默认与预期一致，支持按配置注入不一致（演示失败分支）；生成受理凭证 orderId/offerInstId | 900102308 与 900117022 两类套餐 presetValue 与源文件逐项一致；orderId/offerInstId 非空；objTestSceneRel AI 总结字段完整 | 1.5d |

### 1.3 资费与审批运维类

| # | 接口 | 方法/路径 | 开发内容 | 验收要点 | 工期 |
| --- | --- | --- | --- | --- | --- |
| 8 | 计费规则校验 `check_billing_rule` | POST /api/v1/billing/rules/verify | 内置规则引擎（负资费/边界价差/叠加上限/互斥/自定义规则），对照 18 销售品资费结构 | check_scene 四种枚举生效；构造冲突用例 pass=0 且 risk_list 完整 | 1d |
| 9 | 审批推送 `submit_release_approval` | POST /api/v1/approval/submit | 写入模拟审批状态库（状态机：审批中→产品经理审核→部门主管审批→通过/驳回）；**校验 approve_confirmed==true**；幂等（同 product_id 返回原 approval_id） | 非 confirmed 返回 NOT_CONFIRMED；状态可被接口13 查询 | 0.5d |
| 10 | 审批进度 `query_approval_status` | GET /api/v1/approval/status | 从模拟审批状态库按 approval_id（优先）/product_id 查询最新审批单 | 返回 status/current_node/approver/opinion/update_time；查无单返回明确提示 | 0.5d |
| 11 | 监控查询 `query_product_monitor` | GET /api/v1/product/monitor | 按 product_id+日期确定性生成指标（订单量/异常量/差错率/告警列表）；支持 error_count>0 预置演示 | date_range/metric 参数生效；告警列表与接口12 写入记录回显一致 | 0.5d |
| 12 | 异常告警 `send_alert` | POST /api/v1/alert/send | 生成 alert_id 写入模拟告警库（供监控查询回显闭环） | alarm_level 三级枚举；content 落库 | 0.25d |

### 1.4 平台复用插件对齐（改动量小）

| # | 接口 | 改动内容 | 工期 |
| --- | --- | --- | --- |
| 13 | 节点结果存储 `save_node_result` | key 规范对齐 V1.6：plan_id + `EXEC{execution_id}_STAGE{n}`；非法 key 返回 5002 | 0.25d |
| 14 | 节点结果查询 `query_node_result` | 同上；查无返回 5005 | 0.25d |

---

## 2. 模拟数据工程（V1.6 核心）

| # | 工作项 | 说明 | 工期 |
| --- | --- | --- | --- |
| 1 | 种子数据集 `seed_offers.json` | 从《产品信息.txt》结构化 18 条销售品全量规则（ID/名称/系列/套内资费/套外资费/过渡期资费/副卡/流量结转/断网授权/停机规则/计费周期与付费方式/销售渠道/订购/变更/退订拆机携出） | 1d |
| 2 | 规则值映射表 `preset_map.json` | 18 销售品 × 10 测点（P_EFF_DATE/P_EXP_DATE/P_STATUS/P_MAIN_PROD/P_RELY_REL/P_MUTEX_REL/P_ORD_CNT/P_OFFER_NAME/P_OFFER_TYPE/P_PAY_MODE）的预期值映射，供接口7 生成 presetValue | 0.5d |
| 3 | 演示场景开关 | 每销售品支持注入：稽核驳回用例 / 资费冲突用例 / 测试测点不一致用例 / 监控 error_count>0 用例（通过请求参数或配置文件控制，正向演示默认全通过） | 0.5d |
| 4 | 一致性自测脚本 | 遍历 18 销售品逐一调用接口 1/2/4/7/8/11，断言返回结构与规则值一致（对应细化设计 2.4 #11/#12、3.5 #17） | 0.5d |

---

## 3. 工作流配套开发（代码节点/循环）

| # | 工作项 | 说明 | 工期 |
| --- | --- | --- | --- |
| 1 | 测试轮询循环逻辑（wf_sub_04 节点4） | 循环节点调用接口6：间隔 5s、超时 30 分钟（360 次）、连续 5 次查询失败终止转人工（保留 globalId）；退出条件 done==true 或 failed==true | 0.5d |
| 2 | execution_id 生成与回放逻辑（主流程节点4/5） | EXE+yyyyMMddHHmmss+2位序号；续跑时按 EXEC{execution_id}_STAGE{n} 查询回放已成功环节，写接口不重复调用 | 0.5d |
| 3 | 环节结果打印节点拼装（主流程节点6/8/10/12） | 直接引用插件出参按 3.1.2 模板拼装；若平台不支持纯文本拼装，用温度 0.2 小 LLM 仅做格式化（提示词注明"逐字引用，不新增内容"） | 0.5d |

---

## 4. 工期汇总（与主方案第 7 章对齐）

| 阶段 | 本清单对应工作项 | 工期 |
| --- | --- | --- |
| 阶段1 基础搭建 | 种子数据集 #1 + 接口骨架 | 3d（含平台建项） |
| 阶段2 插件开发 | 接口 1~14 开发与自测 + 数据工程 #2~#4 | 5d |
| 阶段3 知识库建设 | （界面配置为主，见平台配置清单） | 3d |
| 阶段4 工作流编排 | 本清单第 3 节 3 项 + 工作流导入调试 | 6d |
| 阶段5 智能体集成 | （界面配置为主） | 2d |
| 阶段6 验证与优化 | 18 套餐一致性自测跑通 + 正反向用例 | 4d |
| **合计** | | **约 22 个工作日**（其中代码开发约 12d） |

---

## 5. 开发自测 Checklist

- [ ] 14 条路由单元/契约测试通过（curl/Postman/JUnit，参照《产销品加载AI应用-接口说明书.md》5 节方法）
- [ ] 18 销售品一致性自测脚本全绿（任一套餐返回结构化结果、资费规则值一致、无写死单一样例回退）
- [ ] presetValue 抽查：900102308（5G-A）与 900117022（权益随心选）两类套餐与《产品信息.txt》逐项一致
- [ ] 未收录销售品 ID 输入：接口 4 返回 4001，接口 1 返回空列表或明确降级提示，不返回伪造数据
- [ ] 工具7/9 门禁：confirmed / approve_confirmed 非 true 一律拒绝（NOT_CONFIRMED）
- [ ] 幂等：工具7 同 plan_json、工具9 同 product_id 重复提交不产生重复记录
- [ ] 异常注入开关：稽核驳回/资费冲突/测点不一致/监控异常 四类反向用例可复现
- [ ] 模拟服务与导出 JSON 契约一致（路径/入参/出参逐项比对 `插件\自研插件集V1.6\*.json`）
