# 产销品加载 AI 应用 · 平台配置清单
> 说明：以下工作全部在「AI应用开发」平台界面完成（助手编排、插件管理、知识管理、工作流管理），无需编码。
> 关联文档：《产销品加载AI应用-开发工作清单.md》（接口/网关等需开发部分）

---

## 1. 智能体（助手）配置

### 1.1 创建助手
| 配置项 | 值 | 操作位置 |
| --- | --- | --- |
| 助手代码 | `cpcp_product_worker` | 助手 → 创建助手 |
| 助手名称 | 产销品数字员工 | 同上 |
| 功能介绍 | 面向产销品域的数字员工，支持从需求提报、需求分析、销售品配置、规格稽核、资费校准、自动测试、受理验证到上线审批、监控运维的全流程自动化。 | 同上 |
| 发布范围 | 所有人可见 | 同上 |
| 图标 | 默认/上传 | 同上 |

### 1.2 助手编排
| 配置项 | 配置内容 |
| --- | --- |
| 提示词 | 按【角色+技能+限制】模式配置（内容见《产销品加载AI应用开发方案.md》3.2 节，复制粘贴即可） |
| 插件 | 点击插件右侧「+」，添加《产销品加载插件集》下的 11 个工具 |
| 工作流 | 点击工作流右侧「+」，添加 `wf_cpcp_main` 主工作流及各子工作流 |
| 知识库 | 添加：产销品业务规范库、资费规则库、测试规范库 |
| 模型配置 | 温度值 0.2；多轮对话 20 轮；top_p 调小；FAQ 直接返回：否 |
| 答案为空提示 | "抱歉，我仅支持产销品加载相关业务，请更换问题或联系管理员。" |
| 开场白文案 | "您好，我是产销品数字员工，可协助您完成销售品从需求提报、智能配置、稽核校准、自动测试到上线审批、监控运维的全流程。请上传需求文档或直接描述需求。" |
| 引导问题 | ① 我要上新一个流量套餐，请帮我分析需求 ② 帮我稽核已生成的产销品配置 ③ 生成销售品测试用例并执行 ④ 查询昨日新增销售品运行监控 |

### 1.3 调试与发布
- 右上角「保存」→ 右侧「预览与调试」验证对话效果 → 「发布」到小思页面。

---

## 2. 插件配置（插件管理页面）

> 前提：每个工具对应的接口已由开发侧提供（见开发工作清单），配置工作本身在平台完成。

### 2.1 创建插件
- 插件名称：`产销品加载插件集`；填写功能介绍与图标 → 创建。

### 2.2 逐个创建工具（11 个）

| # | 工具代码 | 工具名称 | 接口协议 | 接口地址 | 是否归纳总结 | 提参要点 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `query_product_config` | 产销品配置查询 | http/https | GET `{cpcp-gateway}/api/v1/products/config/query` | 是/LLM | keyword 必填提参 |
| 2 | `gen_crm_config` | CRM配置数据生成 | http/https | POST `{cpcp-gateway}/api/v1/crm/config/generate` | 否 | 复述确认后调用 |
| 3 | `gen_billing_config` | 计费配置数据生成 | http/https | POST `{cpcp-gateway}/api/v1/billing/config/generate` | 否 | fee_json 必填 |
| 4 | `check_billing_rule` | 计费规则校验 | http/https | POST `{billing-check}/api/v1/rules/verify` | 否 | check_scene 枚举 |
| 5 | `check_product_spec` | 配置规格稽核 | http/https | POST `{cpcp-gateway}/api/v1/spec/audit` | 否 | audit_template 可选 |
| 6 | `gen_test_cases` | 测试用例生成 | http/https | POST `{test-platform}/api/v1/cases/generate` | 否 | case_type 枚举 |
| 7 | `run_test_cases` | 测试用例执行 | http/https | POST `{test-platform}/api/v1/cases/execute` | 否 | env 枚举 sit/uat/pre |
| 8 | `verify_acceptance` | 受理验证 | http/https | POST `{crm-gateway}/api/v1/order/verify` | 否 | verify_type 枚举 |
| 9 | `submit_release_approval` | 上线审批推送 | http/https | POST `{oa-gateway}/api/v1/approval/submit` | 否 | approval_flow 枚举 |
| 10 | `query_product_monitor` | 产销品监控查询 | http/https | GET `{monitor}/api/v1/product/monitor` | 是/LLM | metric 枚举 |
| 11 | `send_alert` | 异常告警 | http/https | POST `{monitor}/api/v1/alert/send` | 否 | alarm_level 枚举 |

每个工具需配置：
1. **基础信息**：工具代码、名称、描述、类型（url）、协议、地址、工具提示词、是否归纳总结。
2. **输入参数**：英文名、描述、枚举值、为空提示、数据类型（string/int/date/float）、是否必填、是否提参、默认值（可用 `{默认值名称}`）。
3. **输出参数**：英文名、描述、数据类型、是否必填。
4. 保存 → 插件详情页「发布」。

> 入参/出参明细表见《产销品加载AI应用开发方案.md》4.2 节，照表录入。

---

## 3. 知识库配置（知识管理页面）

| 步骤 | 操作 | 内容 |
| --- | --- | --- |
| 1 | 新增知识分类 | ① 产销品业务规范 ② 资费规则库 ③ 测试规范库 ④ FAQ |
| 2 | 上传知识（文档模版） | 产销品管理办法、配置规范、命名规则、资费模板与叠加约束、测试用例设计规范、报告模板（word/pdf/txt） |
| 3 | 上传知识（FAQ 模版） | 稽核不通过怎么办、资费冲突处理、如何查存量产品等高频问答 |
| 4 | 校验 | 抽查切片质量、检索命中情况（在助手预览中问答验证） |

---

## 4. 工作流配置（工作流管理页面）

### 4.1 主工作流 `wf_cpcp_main`
- 创建工作流 → 填写名称/描述/图标 → 拖拉拽编排。
- 节点顺序：开始 → 需求分析(大模型) → 要素完整性(选择器) → 存量查询(插件) → 配置生成(插件×2) → 规格稽核(插件) → 稽核判定(选择器) → 资费校准(插件) → 资费判定(选择器) → 用例生成(插件) → 用例执行(插件) → 测试判定(选择器) → 受理验证(插件) → 报告汇总(大模型) → 上线审批(插件) → 结束。

### 4.2 子工作流清单（均需创建并发布）

| 子工作流编码 | 名称 | 主要节点 |
| --- | --- | --- |
| `wf_sub_01` | 需求分析 | 开始 → 大模型(结构化解析) → 选择器(完整性) → 结束 |
| `wf_sub_02` | 智能配置 | 开始 → query_product_config → 大模型(复用决策) → gen_crm_config → gen_billing_config → 结束 |
| `wf_sub_03` | 规格稽核 | 开始 → check_product_spec → 大模型(整改建议) → 结束 |
| `wf_sub_04` | 资费校准 | 开始 → check_billing_rule → 大模型(风险解读) → 结束 |
| `wf_sub_05` | 自动测试 | 开始 → gen_test_cases → run_test_cases → 选择器(全通过?) → 结束 |
| `wf_sub_06` | 受理验证 | 开始 → verify_acceptance → 选择器 → 结束 |
| `wf_sub_07` | 上线审批 | 开始 → 大模型(报告生成) → submit_release_approval → 结束 |
| `wf_sub_08` | 监控运维 | 开始 → query_product_monitor → 选择器(异常?) → send_alert → 结束 |

### 4.3 关键节点配置要点
| 节点 | 配置 |
| --- | --- |
| 开始节点 | 参数：`requirement_text`(string,必填)、`requirement_file`(string,选填)、`product_id`(string,选填)；参数名仅英文字母+下划线 |
| 大模型节点(需求分析) | 温度 0.2；提示词要求仅输出 JSON（product_name/fee_json/sale_scope/effect_date）；输出参数 `parsed_json`(string) |
| 选择器节点 | if-else 条件：如 `pass 等于 1` 且 `failed 等于 0`；支持且/或多条件 |
| 大模型节点(报告汇总) | 输入引用前序节点参数；按标准模板生成报告；输出 `report`(string) |
| 插件节点 | 入参可引用前序节点输出或自定义；入参/出参不可在画布中增改（回到插件管理修改） |
| 结束节点 | 输出：product_id、各环节结果、审批单号、下一步指引 |

### 4.4 保存/发布
- 保存为草稿（不发布）→ 调试通过后点「发布」。

---

## 5. 配置顺序建议

```
① 创建助手框架 → ② 创建插件+11个工具并发布 → ③ 建知识分类并上传知识
→ ④ 逐个编排子工作流并单环节调试 → ⑤ 编排主工作流并全流程调试
→ ⑥ 助手装配（提示词/插件/工作流/知识库/模型参数/开场白/引导问题）
→ ⑦ 预览与调试 → ⑧ 发布
```
