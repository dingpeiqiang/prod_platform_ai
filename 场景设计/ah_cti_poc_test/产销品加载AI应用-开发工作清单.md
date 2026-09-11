# 产销品加载 AI 应用 · 开发工作清单（需要开发的部分）
> 说明：以下为平台配置之外、需要代码/工程开发完成的工作。平台侧配置见《产销品加载AI应用-平台配置清单.md》。
> 交付形态：11 个 REST 接口（插件对接目标）+ 需求文档解析辅助（可选）+ 部署与环境准备。

---

## 1. 接口开发总览

所有接口统一要求：
- 协议：HTTP/HTTPS，JSON 报文（平台工具类型为 url）。
- 认证：网关统一鉴权（Token/AppKey），禁止明文凭据。
- 出参：必须含统一状态字段（如 `code`/`msg`），业务结果字段命名与插件出参定义一致。
- 幂等与超时：写入类接口需幂等；同步接口建议 ≤30s，长任务（用例执行）用 `task_id` 异步模式。
- 环境：sit / uat / pre 三套，生产写入类接口必须可按环境隔离。

| # | 接口 | 方法 | 所属系统 | 难度 | 工期估算 |
| --- | --- | --- | --- | --- | --- |
| 1 | 产销品配置查询 | GET | CPCP | 低 | 2d |
| 2 | CRM 配置数据生成 | POST | CPCP/CRM | 中 | 3d |
| 3 | 计费配置数据生成 | POST | CPCP/计费 | 中 | 3d |
| 4 | 计费规则校验 | POST | 计费 | 中 | 3d |
| 5 | 配置规格稽核 | POST | CPCP | 中 | 3d |
| 6 | 测试用例生成 | POST | 测试平台 | 高 | 5d |
| 7 | 测试用例执行 | POST | 测试平台 | 高 | 5d |
| 8 | 受理验证 | POST | CRM/订单 | 中 | 3d |
| 9 | 上线审批推送 | POST | OA | 低 | 2d |
| 10 | 产销品监控查询 | GET | 监控平台 | 低 | 2d |
| 11 | 异常告警推送 | POST | 监控平台 | 低 | 1d |

---

## 2. 各接口详细定义

### 接口1：产销品配置查询 `query_product_config`
- `GET {cpcp-gateway}/api/v1/products/config/query`
- 入参：`keyword`(string,选填)、`product_id`(string,选填)、`status`(string,枚举 online/offline/all)
- 出参：
```json
{ "code": 0, "total": 12,
  "list": [ { "product_id": "P20260001", "product_name": "畅享流量包", "spec_json": "{}", "fee_json": "{}", "sale_scope": "anhui-all", "status": "online" } ] }
```
- 开发要点：支持名称/编码模糊查询；spec_json/fee_json 为结构化规格与资费快照，供比对与复用。

### 接口2：CRM 配置数据生成 `gen_crm_config`
- `POST {cpcp-gateway}/api/v1/crm/config/generate`
- 入参：`product_name`(必填)、`fee_json`(必填)、`sale_scope`(必填)、`effect_date`(date)、`expire_date`(date)
- 出参：`crm_config_id`、`crm_config_json`、`status`
- 开发要点：按 CRM 导入格式生成配置数据（产品目录、属性、资费绑定、销售范围）；生成前做重复性校验。

### 接口3：计费配置数据生成 `gen_billing_config`
- `POST {cpcp-gateway}/api/v1/billing/config/generate`
- 入参：`product_id`(必填)、`fee_json`(必填)、`discount_rules`(选填)
- 出参：`billing_config_id`、`billing_config_json`、`status`
- 开发要点：生成计费事件、优惠叠加、账期等配置；与 CRM 侧 product_id 关联。

### 接口4：计费规则校验 `check_billing_rule`
- `POST {billing-check}/api/v1/rules/verify`
- 入参：`billing_config_json`(必填)、`check_scene`(枚举 fee/overlay/superposition/all)
- 出参：
```json
{ "code": 0, "pass": 0,
  "risk_list": [ { "risk_type": "overlap_conflict", "risk_desc": "与X优惠互斥", "suggest": "移除叠加规则R3" } ] }
```
- 开发要点：内置规则引擎（资费互斥、叠加上限、负资费、边界价差）；规则可配置化，与知识库规则同步。

### 接口5：配置规格稽核 `check_product_spec`
- `POST {cpcp-gateway}/api/v1/spec/audit`
- 入参：`crm_config_json`(必填)、`billing_config_json`(必填)、`audit_template`(选填，默认规范模板)
- 出参：`pass`、`error_list[{item, level, desc, suggest}]`
- 开发要点：按业务规范做完整性/合规性校验（必填属性、命名规则、生效期逻辑、销售范围合法性）；模板可配置。

### 接口6：测试用例生成 `gen_test_cases`
- `POST {test-platform}/api/v1/cases/generate`
- 入参：`crm_config_json`(必填)、`billing_config_json`(必填)、`case_type`(枚举 acceptance/change/cancel/billing/all)
- 出参：`case_count`、`case_ids[]`、`case_list_json`
- 开发要点：
  - 规则驱动生成：受理、变更、退订、计费四类场景模板化；
  - 结合资费边界（生效日、跨月、叠加）生成边界用例；
  - 用例格式与执行引擎对齐（可执行脚本或结构化步骤）。

### 接口7：测试用例执行 `run_test_cases`
- `POST {test-platform}/api/v1/cases/execute`
- 入参：`case_ids[]`(必填)、`env`(枚举 sit/uat/pre)、`execute_mode`(枚举 sync/async)
- 出参：`task_id`、`total`、`passed`、`failed`、`fail_detail[{case_id, step, expect, actual, reason}]`
- 开发要点：异步执行 + 任务状态查询；失败明细需定位到步骤；环境隔离，禁止打生产。

### 接口8：受理验证 `verify_acceptance`
- `POST {crm-gateway}/api/v1/order/verify`
- 入参：`product_id`(必填)、`verify_type`(枚举 new/change/cancel)
- 出参：`pass`、`order_id`、`fail_reason`
- 开发要点：在验证环境自动发起模拟受理/变更/退订订单并回读结果。

### 接口9：上线审批推送 `submit_release_approval`
- `POST {oa-gateway}/api/v1/approval/submit`
- 入参：`product_id`(必填)、`report`(或 report_url，必填)、`approval_flow`(枚举 standard/urgent)
- 出参：`approval_id`、`status`
- 开发要点：对接 OA/审批系统 API，附测试与稽核报告附件；支持回调审批结果（可选扩展）。

### 接口10：产销品监控查询 `query_product_monitor`
- `GET {monitor}/api/v1/product/monitor`
- 入参：`product_id`(必填)、`date_range`(选填)、`metric`(枚举 order/error/fee/all)
- 出参：`order_count`、`error_count`、`fee_error_rate`、`alarm_list[]`
- 开发要点：聚合订单成功率、计费差错率、投诉关联等指标；支持时间范围。

### 接口11：异常告警推送 `send_alert`
- `POST {monitor}/api/v1/alert/send`
- 入参：`product_id`(必填)、`alarm_level`(枚举 high/middle/low)、`content`(必填)
- 出参：`alert_id`、`status`
- 开发要点：对接运维群机器人/工单系统；阈值判定逻辑可放工作流选择器或本接口。

---

## 3. 可选开发项

| 项 | 说明 | 优先级 |
| --- | --- | --- |
| 需求文档解析服务 | 若需求以 word/pdf 上传，平台知识库仅作检索；如需结构化抽取（表格资费等），可开发独立的文档解析接口供大模型节点调用 | 中 |
| 执行结果回查接口 | `run_test_cases` 异步模式下，提供 `GET /tasks/{task_id}` 回查；插件可另建工具或在主接口轮询 | 中 |
| 审批结果回调 | 审批通过/驳回回传数字员工，自动通知并触发上线/打回 | 低 |
| 报告文件存储 | 测试/稽核报告生成 PDF/HTML 并返回 URL，供审批附件 | 中 |

---

## 4. 非功能性工作

| 工作项 | 内容 |
| --- | --- |
| 环境准备 | sit/uat/pre 环境接口部署、网关注册、鉴权 Token 申请 |
| 联调测试 | 与平台插件逐一联调（连通性、入参提取、出参归纳）；提供 Postman/Swagger 文档 |
| 数据安全 | 敏感资费字段脱敏；接口审计日志；生产写入二次确认机制（接口侧幂等+白名单） |
| 文档交付 | 每个接口的《接口说明书》（地址/方法/入参/出参/错误码）交平台侧照表录入插件 |
| 上线支持 | 插件调试期配合排障；接口版本变更同步通知平台侧更新插件 |

---

## 5. 排期建议（开发侧，约 30 个工作日，可并行压缩至 ~15 天）

| 周次 | 工作 |
| --- | --- |
| W1 | 接口1、9、10、11（简单接口）+ 接口定义评审、Swagger 交付 |
| W2 | 接口2、3、8（配置生成与受理验证） |
| W3 | 接口4、5（规则校验/稽核引擎） |
| W4 | 接口6、7（用例生成与执行，含测试环境打通） |
| W5 | 联调、异常场景补齐、文档与部署交付 |

> 并行建议：平台侧在 W1 结束即可开始插件配置与工作流编排，接口按"先查询类、后写入类"顺序逐步提供，两边流水线推进。
