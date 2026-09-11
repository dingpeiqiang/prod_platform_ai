# 产销品加载 AI 应用 · 接口说明书（插件对接规范）

> 版本：v1.0
> 更新日期：2026-09-10
> 适用范围：平台插件录入（工具类型 url）联调对接，与《产销品加载AI应用-开发工作清单.md》11 接口一一对应。
> 实现位置：`backend-app/src/main/java/com/sitech/prodai/controller/AppStoreController.java`（Spring Boot，端口 6174）

---

## 1. 全局约定

### 1.1 基础信息

| 项 | 值 |
| --- | --- |
| Base URL（本地） | `http://localhost:6174/api/v1/appstore` |
| 协议 | HTTP/HTTPS |
| 报文格式 | `application/json; charset=UTF-8` |
| 认证 | 网关统一 JWT（现工程 `JwtAuthFilter` 自动拦截 `/api/**`，登录 `/api/v1/auth/login` 获取 Token 后以 `Authorization: Bearer <token>` 携带）；上生产后切换为网关 Token/AppKey |
| 环境隔离 | sit / uat / pre 三套独立部署；写入类接口按环境隔离，生产禁止直连 |

### 1.2 统一响应结构

所有接口出参含统一状态字段，业务字段命名与插件出参定义一致（snake_case）：

```json
// 成功
{ "code": 0, "msg": "success", ...业务字段 }

// 业务失败（HTTP 200）
{ "code": <非0错误码>, "msg": "<失败原因>" }
```

框架级异常（参数缺失/系统错误）由全局异常处理器兜底，返回 HTTP 4xx/5xx：

```json
{ "success": false, "error_code": "bad_request", "message": "...", "request_id": "..." }
```

插件侧判定规则：优先看 `code == 0`；HTTP 非 200 或缺 `code` 字段按系统异常重试。

### 1.3 幂等约定

写入类接口（接口2/3/9）支持幂等：请求体携带 `idempotency_key`（string，选填，建议 UUID）。
- 首次请求正常执行并缓存响应快照；
- 相同 key 重复请求直接回放首次响应，不重复生成数据。

### 1.4 通用错误码

| code | 含义 | 处理建议 |
| --- | --- | --- |
| 0 | 成功 | — |
| 1xxx/2xxx/... | 业务错误（各接口定义见下） | 按 `msg` 提示或由大模型归纳 |
| 4002/9003 等 | 枚举参数非法 | 修正入参 |
| HTTP 400 | 参数校验失败（bad_request/validation_error） | 检查必填字段 |
| HTTP 401 | 未认证 | 重新获取 Token |
| HTTP 500 | 系统异常（internal_error/db_error） | 携带 request_id 排障 |

---

## 2. 接口明细

### 接口1：产销品配置查询 `query_product_config`

- **地址**：`GET {base}/products/config/query`
- **用途**：按名称/编码模糊查询产销品配置（含规格与资费快照），供比对与复用。
- **入参**（Query String）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| keyword | string | 否 | 名称/编码/规格模糊匹配；匹配范围为 `product_name`、`product_id`、`spec_json`（包含匹配） |
| product_id | string | 否 | 产品编码包含匹配（`contains`，非全等），如传 `P2026` 可命中 `P20260001` |
| status | string | 否 | 枚举 `online`/`offline`/`all`，默认 `all`；按产品状态精确过滤（忽略大小写），不校验非法值 |

- **出参**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | int | 统一状态码，0 成功，非0 业务失败 |
| msg | string | 状态描述，成功为 `success`，失败为失败原因 |
| total | int | 命中条数，即 list 数组长度 |
| list | array | 产品列表，按匹配度排序 |
| list[].product_id | string | 产品编码，全局唯一，如 `P20260001`；作为后续各接口的关联主键 |
| list[].product_name | string | 产品名称，如 `畅享流量包` |
| list[].spec_json | string | 结构化规格快照（JSON 字符串），含 `spec_desc` 规格/流量/速率等描述 |
| list[].fee_json | string | 结构化资费快照（JSON 字符串），含 `fee_desc` 资费/计费周期等描述 |
| list[].sale_scope | string | 销售范围，如 `anhui-all`（安徽全省）/`anhui-hefei`（地市）/`nationwide`（全国） |
| list[].status | string | 产品状态：`online` 在架 / `offline` 下架 / `draft` 草稿（CRM 配置生成后初始态） |
| list[].created_at | string | 创建时间，ISO-8601 格式，如 `2026-09-10T18:30:00` |

- **示例**：

```json
// GET /api/v1/appstore/products/config/query?keyword=流量&status=online
{ "code": 0, "msg": "success", "total": 1,
  "list": [ { "product_id": "P20260001", "product_name": "畅享流量包",
              "spec_json": "{\"spec_desc\":\"10GB国内流量/月，超出5元/GB\"}",
              "fee_json": "{\"fee_desc\":\"monthly_fee:29元\"}",
              "sale_scope": "anhui-all", "status": "online",
              "created_at": "2026-09-10T18:30:00" } ] }
```

---

### 接口2：CRM 配置数据生成 `gen_crm_config`

- **地址**：`POST {base}/crm/config/generate`
- **用途**：按 CRM 导入格式生成配置数据（产品目录、属性、资费绑定、销售范围），生成前做重复性校验；成功后同步登记产销品档案（接口1 可查，状态 `draft`）。
- **幂等**：支持 `idempotency_key`。
- **入参**（JSON Body）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| product_name | string | 是 | 产品名称，2-20 位中英文/数字（接口5 命名稽核规则）；同名产品若已存在 CRM 配置则返回 1002 |
| fee_json | string/object | 是 | 资费定义（JSON 字符串或对象），如 `{"monthly_fee":39}`；原样写入 CRM 配置，与计费侧共用 |
| sale_scope | string | 是 | 销售范围，枚举 `anhui-all`（安徽全省）/`anhui-hefei`/`anhui-wuhu`/`anhui-bengbu`（地市）/`nationwide`（全国）；接口5 校验合法性 |
| product_id | string | 否 | 产品编码，全局唯一；不传自动生成（`P+8位流水` 格式）。传入后 CRM 配置与产销品档案共用该编码，跨系统稽核以此为对齐依据 |
| product_desc | string | 否 | 产品描述，用于稽核必填校验与审批展示，缺省取产品名称 |
| spec_json | string | 否 | 规格快照（JSON 字符串或对象），含规格/流量/速率描述；仅写入产销品档案供接口1 回查，不参与 CRM 配置校验 |
| effect_date | string | 否 | 生效日期 `yyyy-MM-dd`；写入 CRM 配置，接口5 稽核时校验 ≤ expire_date 且与计费侧一致 |
| expire_date | string | 否 | 失效日期 `yyyy-MM-dd`，须晚于生效日期，否则接口5 稽核报错 |
| attrs | array | 否 | 产品属性列表，元素为属性名/值对象，原样写入 CRM 配置 `attrs` 字段，随 `crm_config_json` 透传 |
| idempotency_key | string | 否 | 幂等键，建议 UUID；相同 key 重复请求回放首次响应 |

- **出参**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | int | 统一状态码，0 成功 |
| msg | string | 状态描述 |
| crm_config_id | string | CRM 配置 ID，如 `CRM1001`；后续接口引用 |
| crm_config_json | string | 完整 CRM 配置 JSON 字符串（含目录、属性、资费绑定、销售范围），接口5/6 直接透传 |
| status | string | 恒为 `generated`，表示配置已生成 |

- **错误码**：

| code | 含义 |
| --- | --- |
| 1001 | product_name/fee_json/sale_scope 必填 |
| 1002 | 同名产品已存在 CRM 配置（重复性校验拒绝） |

- **示例**：

```json
// POST /api/v1/appstore/crm/config/generate
// 请求
{ "product_name": "畅享流量包Pro", "fee_json": "{\"monthly_fee\":39}",
  "sale_scope": "anhui-all", "effect_date": "2026-10-01", "idempotency_key": "uuid-001" }
// 响应
{ "code": 0, "msg": "success", "crm_config_id": "CRM1001",
  "crm_config_json": "{\"crm_config_id\":\"CRM1001\",\"product_name\":\"畅享流量包Pro\",\"catalog\":\"产品目录/增值业务/畅享流量包Pro\",...}",
  "status": "generated" }
```

---

### 接口3：计费配置数据生成 `gen_billing_config`

- **地址**：`POST {base}/billing/config/generate`
- **用途**：生成计费事件、账期等配置；与 CRM 侧 `product_id` 关联。
- **幂等**：支持 `idempotency_key`。
- **入参**（JSON Body）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| product_id | string | 是 | 产品编码（与 CRM 侧一致），接口5 稽核时校验两侧一致，不一致报 `product_id` high 错误 |
| fee_json | string/object | 是 | 资费定义（JSON 字符串或对象），与接口2 入参一致；原样写入计费配置 `fee_json` 字段 |
| discount_rules | array | 否 | 优惠叠加规则列表，元素含 `discount_type`（如 `limited_time` 限时/`long_term` 长期，两者互斥）等字段；接口4 按此校验互斥与叠加上限（默认上限 3 条） |
| effect_date | string | 否 | 生效日期 `yyyy-MM-dd`；接口5 稽核时校验与 CRM 侧生效日期一致 |
| idempotency_key | string | 否 | 幂等键，建议 UUID；相同 key 重复请求回放首次响应 |

- **出参**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | int | 统一状态码，0 成功 |
| msg | string | 状态描述 |
| billing_config_id | string | 计费配置 ID，如 `BILL2001` |
| billing_config_json | string | 完整计费配置 JSON 字符串，含 `billing_events` 计费事件（受理/出账/退订）/`account_period` 账期，接口4/5/6 直接透传 |
| status | string | 恒为 `generated`，表示配置已生成 |

- **错误码**：

| code | 含义 |
| --- | --- |
| 2001 | product_id/fee_json 必填 |

- **示例**：

```json
// 响应
{ "code": 0, "msg": "success", "billing_config_id": "BILL2001",
  "billing_config_json": "{\"billing_config_id\":\"BILL2001\",\"product_id\":\"P20260001\",\"billing_events\":[{\"event\":\"order\",...},{\"event\":\"monthly_bill\",...},{\"event\":\"cancel\",...}],\"account_period\":\"自然月\",...}",
  "status": "generated" }
```

---

### 接口4：计费规则校验 `check_billing_rule`

- **地址**：`POST {base}/rules/verify`
- **用途**：内置规则引擎校验计费配置（资费互斥、叠加上限、负资费、边界价差、自定义规则）。
- **入参**（JSON Body）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| billing_config_json | string | 是 | 计费配置 JSON 字符串（也接受对象，字段名 `billing_config`），一般为接口3 出参透传；缺失或非法 JSON 返回 3001。校验读取 `fee_items`（含 `fee_name`/`amount`/`base_amount`）与 `discount_rules` 字段 |
| check_scene | string | 否 | 校验场景，枚举 `fee`（负资费+边界价差）/`overlay` 或 `superposition`（叠加上限+互斥）/`all`（全部+自定义规则），默认 `all`；自定义规则任意场景均校验 |

- **出参**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code / msg | — | 统一状态，code 0 成功 |
| pass | int | 0=通过，1=存在风险 |
| risk_list | array | 风险清单，pass=1 时非空 |
| risk_list[].risk_type | string | `overlap_conflict`（互斥）/`negative_fee`（负资费）/`boundary_price_gap`（价差）/`overlay_limit_exceeded`（叠加上限）/`custom_rule`（自定义） |
| risk_list[].risk_desc | string | 风险描述，含冲突/异常明细 |
| risk_list[].suggest | string | 处置建议，供大模型归纳或直接提示用户 |

- **规则可配置化**：`POST {base}/rules/config`（附加接口，非插件工具）支持更新 `overlay_limit`（叠加上限）、`boundary_price_ratio`（边界价差比例）、`mutex_pairs`（互斥对），与知识库规则同步。
- **示例**：

```json
// 请求
{ "billing_config_json": "{\"discount_rules\":[{\"discount_type\":\"limited_time\"},{\"discount_type\":\"long_term\"}]}", "check_scene": "all" }
// 响应
{ "code": 0, "msg": "success", "pass": 1,
  "risk_list": [ { "risk_type": "overlap_conflict", "risk_desc": "互斥优惠同时存在: 限时优惠与长期优惠互斥", "suggest": "移除其中一方叠加规则" } ] }
```

---

### 接口5：配置规格稽核 `check_product_spec`

- **地址**：`POST {base}/spec/audit`
- **用途**：按业务规范做完整性/合规性校验（必填属性、命名规则、生效期逻辑、销售范围合法性、跨系统一致性）；模板可配置。
- **入参**（JSON Body）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| crm_config_json | string | 是 | CRM 配置 JSON 字符串（或对象 `crm_config`），一般为接口2 出参透传；缺失或非法 JSON 返回 5001 |
| billing_config_json | string | 是 | 计费配置 JSON 字符串（或对象 `billing_config`），一般为接口3 出参透传；缺失或非法 JSON 返回 5001 |
| audit_template | string/object | 否 | 自定义稽核模板，支持 `name_pattern`（命名正则，非法正则回退默认规则）、`required_attrs`（追加必填项，与默认项合并去重）；缺省用默认规范模板 |

- **默认校验项**：
  - 必填属性：`product_name`、`product_desc`、`fee_json`、`sale_scope`、`effect_date`
  - 命名规则：2-20 位中英文/数字
  - 生效期：effect_date ≤ expire_date；CRM 与计费侧生效日期一致
  - 销售范围：`anhui-all`/`anhui-hefei`/`anhui-wuhu`/`anhui-bengbu`/`nationwide`
  - 跨系统：CRM 与计费侧 `product_id` 一致

- **出参**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code / msg | — | 统一状态，code 0 成功 |
| pass | int | 0=通过，1=存在错误 |
| error_list | array | 错误清单，pass=1 时非空 |
| error_list[].item | string | 出错字段名，如 `product_name`/`effect_date` |
| error_list[].level | string | 错误级别：`high` 阻断 / `middle` 建议修正 / `low` 提示 |
| error_list[].desc | string | 错误描述，含实际值与期望规则 |
| error_list[].suggest | string | 修正建议，供大模型归纳或直接提示用户 |

- **错误码**：`5001` crm_config_json/billing_config_json 必填且须为合法 JSON。

---

### 接口6：测试用例生成 `gen_test_cases`

- **地址**：`POST {base}/cases/generate`
- **用途**：规则驱动生成四类场景用例（受理/变更/退订/计费）+ 资费边界用例（月末生效日、叠加达上限），用例为结构化步骤，与执行引擎对齐。
- **入参**（JSON Body）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| crm_config_json | string | 是 | CRM 配置（或对象 `crm_config`），一般为接口2 出参透传；缺失或非法 JSON 返回 6001 |
| billing_config_json | string | 是 | 计费配置（或对象 `billing_config`），一般为接口3 出参透传；用例内容（产品编码/名称、计费场景）取自此配置 |
| case_type | string | 否 | 用例类型，枚举 `acceptance`（受理）/`change`（变更）/`cancel`（退订）/`billing`（计费）/`all`，默认 `all`；忽略大小写，非法值按 `all` 处理 |

- **出参**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code / msg | — | 统一状态，code 0 成功 |
| case_count | int | 生成用例数，与 case_ids 长度一致 |
| case_ids | array[string] | 用例 ID 列表（`TC9001` 格式），接口7 执行入参 |
| case_list_json | string | 用例列表 JSON 字符串，元素含 `case_id`/`case_type`/`case_name`/`steps`（结构化步骤，`action` 操作/`expect` 预期/`actual` 实际，执行前 actual 为空） |

- **错误码**：`6001` crm_config_json/billing_config_json 必填且须为合法 JSON。
- **说明**：`case_type=all` 时生成 4 类场景 + 2 条边界用例（共 6 条）；指定单类时 1 条场景 + 2 条边界（共 3 条）。

---

### 接口7：测试用例执行 `run_test_cases`

- **地址**：`POST {base}/cases/execute`
- **用途**：按环境执行用例，支持同步/异步两种模式；环境隔离，`prod` 直接拒绝。
- **入参**（JSON Body）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| case_ids | array[string] | 是 | 用例 ID 列表（接口6 产物，`TC9001` 格式），任一 ID 不存在返回 4001；为空返回 7001 |
| env | string | 否 | 执行环境，枚举 `sit`/`uat`/`pre`，默认 `sit`（忽略大小写）；`prod` 非法（返回 4002，生产禁止执行） |
| execute_mode | string | 否 | 执行模式，枚举 `sync`（同步返回结果，默认）/`async`（立即返回 task_id + status=running，需轮询回查） |

- **出参（sync）**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| task_id | string | 执行任务 ID（`T+时间戳+流水` 格式），可用于任务回查 |
| total | int | 用例总数 |
| passed | int | 通过条数 |
| failed | int | 失败条数，passed + failed = total |
| fail_detail | array | 失败明细：`case_id` 用例/`step` 失败步骤（首个 step 的 action）/`expect` 预期/`actual` 实际/`reason` 失败原因 |

- **出参（async）**：`task_id`、`status`（`running`），需通过任务回查接口获取结果。
- **任务回查（可选开发项）**：`GET {base}/tasks/{task_id}`
  - 出参：`task_id`（任务 ID）、`env`（执行环境）、`status`（`running` 执行中/`finished` 已完成）、`total`（用例总数）、`passed`（通过数）、`failed`（失败数）、`fail_detail`（失败明细，结构同同步模式）
  - 错误码：`4004` 任务不存在
- **错误码**：

| code | 含义 |
| --- | --- |
| 4001 | 用例不存在 |
| 4002 | 非法环境（生产环境禁止执行） |
| 7001 | case_ids 必填 |

- **Mock 行为**：每条用例约 10% 概率失败（联调演示用），失败明细定位到步骤并给出原因。

---

### 接口8：受理验证 `verify_acceptance`

- **地址**：`POST {base}/order/verify`
- **用途**：在验证环境自动发起模拟受理/变更/退订订单并回读结果。
- **入参**（JSON Body）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| product_id | string | 是 | 产品编码，须为产销品档案中已存在的产品（精确匹配），否则返回 8003 |
| verify_type | string | 否 | 验证类型，枚举 `new`（新受理）/`change`（变更）/`cancel`（退订），默认 `new`（忽略大小写）；非法值返回 8002 |

- **出参**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code / msg | — | 统一状态，code 0 成功 |
| pass | int | 0=受理验证通过，1=失败 |
| order_id | string | 模拟订单号（`ORD5001` 格式），可用于排障追溯 |

- **错误码**：

| code | 含义 |
| --- | --- |
| 8001 | product_id 必填 |
| 8002 | verify_type 非法 |
| 8003 | 产销品不存在 |

---

### 接口9：上线审批推送 `submit_release_approval`

- **地址**：`POST {base}/approval/submit`
- **用途**：对接 OA/审批系统，附测试与稽核报告附件，发起上线审批。
- **幂等**：支持 `idempotency_key`。
- **入参**（JSON Body）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| product_id | string | 是 | 产品编码，须为产销品档案中已存在的产品（精确匹配），否则返回 9002 |
| report | string | 二选一 | 报告内容文本（测试/稽核结论），与 report_url 至少传一项，否则返回 9001；两者都传时优先取 report |
| report_url | string | 二选一 | 报告文件 URL（可下载地址），与 report 至少传一项 |
| approval_flow | string | 否 | 审批流程类型，枚举 `standard`（标准流程）/`urgent`（加急流程），默认 `standard`（忽略大小写）；非法值返回 9003 |
| idempotency_key | string | 否 | 幂等键，建议 UUID；相同 key 重复请求回放首次响应，避免重复发起审批 |

- **出参**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code / msg | — | 统一状态，code 0 成功 |
| approval_id | string | 审批单号（`AP7001` 格式），可用于 OA 侧跟踪 |
| status | string | 恒为 `submitted`，表示审批已发起 |

- **错误码**：

| code | 含义 |
| --- | --- |
| 9001 | product_id 与 report/report_url 必填 |
| 9002 | 产销品不存在 |
| 9003 | approval_flow 非法 |

---

### 接口10：产销品监控查询 `query_product_monitor`

- **地址**：`GET {base}/product/monitor`
- **用途**：聚合订单量、计费差错率、告警关联等指标，支持时间范围。
- **入参**（Query String）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| product_id | string | 是 | 产品编码，须为产销品档案中已存在的产品（精确匹配），否则返回 10002 |
| date_range | string | 否 | 统计时间范围，格式 `yyyy-MM-dd~yyyy-MM-dd`，如 `2026-09-01~2026-09-10`；当前 Mock 不校验格式，仅作查询条件回显 |
| metric | string | 否 | 指标过滤，枚举 `order`（订单量）/`error`（差错）/`fee`（计费）/`all`，默认 `all`（当前实现恒全量返回，指定单值时仅在出参回显 metric 作过滤提示） |

- **出参**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code / msg | — | 统一状态，code 0 成功 |
| order_count | int | 订单量（按 productId 确定性 Mock，100~999） |
| error_count | int | 差错单数（0~7），反映计费/受理异常单量 |
| fee_error_rate | double | 计费差错率（0~0.004），占比≤0.4% 视为正常 |
| alarm_list | array | 关联告警（接口11 推送的告警回显：`alarm_id` 告警 ID/`alarm_level` 级别/`content` 内容/`created_at` 时间） |
| metric / date_range | — | 入参回显，便于核对查询条件 |

- **错误码**：

| code | 含义 |
| --- | --- |
| 10001 | product_id 必填 |
| 10002 | 产销品不存在 |

---

### 接口11：异常告警推送 `send_alert`

- **地址**：`POST {base}/alert/send`
- **用途**：对接运维群机器人/工单系统推送告警；推送后可在接口10 的 `alarm_list` 回查。
- **入参**（JSON Body）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| product_id | string | 是 | 产品编码（如 `P20260001`），与接口10 监控查询一致；推送后按此编码在接口10 `alarm_list` 回查 |
| alarm_level | string | 否 | 告警级别，枚举 `high`（高）/`middle`（中）/`low`（低），默认 `low`（忽略大小写）；非法值返回 11002，高级别建议联动工单系统优先处理 |
| content | string | 是 | 告警内容文本，建议包含异常现象、影响范围与发生时间；推送至运维群机器人/工单系统，原样推送至接口10 告警回查 |

- **出参**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code / msg | — | 统一状态，code 0 成功 |
| alert_id | string | 告警 ID（`AL3001` 格式），可在接口10 `alarm_list` 中回查 |
| status | string | 恒为 `sent`，表示已推送 |

- **错误码**：

| code | 含义 |
| --- | --- |
| 11001 | product_id/content 必填 |
| 11002 | alarm_level 非法 |

---

## 3. 错误码总表

| code | 接口 | 含义 |
| --- | --- | --- |
| 1001 | 2 | 必填项缺失（product_name/fee_json/sale_scope） |
| 1002 | 2 | 同名产品 CRM 配置已存在 |
| 2001 | 3 | 必填项缺失（product_id/fee_json） |
| 3001 | 4 | billing_config_json 缺失或非法 JSON |
| 4001 | 7 | 用例不存在 |
| 4002 | 7 | 非法执行环境（禁止打生产） |
| 4004 | 7b | 任务不存在 |
| 5001 | 5 | crm/billing 配置缺失或非法 JSON |
| 6001 | 6 | crm/billing 配置缺失或非法 JSON |
| 7001 | 7 | case_ids 必填 |
| 8001 | 8 | product_id 必填 |
| 8002 | 8 | verify_type 非法 |
| 8003 | 8 | 产销品不存在 |
| 9001 | 9 | product_id 与报告附件必填 |
| 9002 | 9 | 产销品不存在 |
| 9003 | 9 | approval_flow 非法 |
| 10001 | 10 | product_id 必填 |
| 10002 | 10 | 产销品不存在 |
| 11001 | 11 | product_id/content 必填 |
| 11002 | 11 | alarm_level 非法 |

---

## 4. 联调说明（平台侧插件录入）

1. **连通性**：先以接口1（GET）验证网关与鉴权打通，再逐个录入写入类工具。
2. **入参提取**：大模型节点从对话上下文提取入参；`crm_config_json`/`billing_config_json` 在工作流中由接口2/3 出参直接传递（字符串透传即可）。
3. **出参归纳**：接口4/5 的 `risk_list`/`error_list` 结构固定，可模板化渲染；接口7 异步模式需插件轮询 `GET /tasks/{task_id}`（建议 2s 间隔，上限 30 次）。
4. **Postman/Swagger**：本地启动后可访问 `http://localhost:6174/v3/api-docs`（若开启）或按本说明书照表录入；每个接口的字段名与本文档严格一致。
5. **数据重置**：Mock 数据为内存态，服务重启即恢复种子数据（4 条产销品）；联调写脏数据无需清理。
6. **后续演进**：内存 Mock 替换为 MyBatis-Plus 持久化时，接口契约（路径/入参/出参）保持不变，插件无需改动。
