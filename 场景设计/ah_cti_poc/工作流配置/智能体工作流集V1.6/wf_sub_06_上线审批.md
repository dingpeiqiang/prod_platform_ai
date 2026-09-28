# wf_sub_06 上线审批

> 来源：`wf_sub_06_上线审批.json`（`flowId=wf_sub_06`，`version=1.6`）
> 职责：单入参 `req_id` → 串行自查 5 类环节结果（config/spec/fee/test/requirement）→ 合成结构化汇总 → LLM 生成《上线审批建议》→ 存储 report → 批量推送审批（`approval-type=launch`）→ 结束（不轮询/不分流）。
> 节点类型：`0`开始 / `1`LLM / `2`分支 / `3`插件 / `6`代码 / `9`结束。
> 约定：节点 id 简写为尾 12 位，如 `...000000000601`；`<- B.O` 表示引用节点 `B` 的出参 `O`；`{name}` 为占位符。

---

## 1. 概览

| 项 | 值 |
|---|---|
| flowName | 产销品-上线审批 |
| flowId / version | wf_sub_06 / 1.6 |
| 入参 | `req_id`(必填)、`chat_id`(必填) |
| 节点数 | 17（开始1 + 查询5 + 代码6 + 分支1 + LLM1 + 插件1 + 结束1 + 插件[推送]1 + …） |
| 分支 | 1 个（节点617 报告写入门禁，条件=`all_pass==1`） |
| 插件端点 | `result/query`、`result/save`、`approval/submit`（`http://10.86.13.201:31281/api/v1/appstore/…`） |
| LLM | `qwen3-30b-a3b`（temperature=0.2，top_p=0.5，max_tokens=2048） |
| 审批轨 | 双轨，此处 `approval-type=launch`（上线审批） |

---

## 2. 链路

```
开始(601)
  └─► 自查配置结果(602) ──► 提取配置原文(603)
       └─► 自查稽核结果(604) ──► 提取稽核原文(605)
            └─► 自查资费结果(606) ──► 提取资费原文(607)
                 └─► 自查测试结果(608) ──► 提取测试原文(609)
                      └─► 自查执行方案(610) ──► 提取执行方案原文(611)
                           └─► 合成结构化汇总(612)
                                └─► LLM上线审批建议生成(613)
                                     └─► 报告写入门禁(617) ──0(all_pass==1)──► 报告存储(614) ──► 审批推送(615) ──► 结束·审批已提交(616)
                                                                   └──-1──► （无出边）
```

> V6.0：去除轮询与分流，审批推送后即结束；审批状态由【查询审批进度】事后查询。

---

## 3. 节点清单

| 节点 | 类型 | 标题 | 说明 |
|---|---|---|---|
| 601 | 0 开始 | 开始节点 | 入参 `req_id`、`chat_id` |
| 602 | 3 插件 | 自查配置结果 | `query_node_result`，`node_name=config` |
| 603 | 6 代码 | 提取配置原文 | 解析 `list[0].result_json` 与逐品 `offers[]` |
| 604 | 3 插件 | 自查稽核结果 | `query_node_result`，`node_name=spec` |
| 605 | 6 代码 | 提取稽核原文 | 同 603 |
| 606 | 3 插件 | 自查资费结果 | `query_node_result`，`node_name=fee` |
| 607 | 6 代码 | 提取资费原文 | 同 603 |
| 608 | 3 插件 | 自查测试结果 | `query_node_result`，`node_name=test` |
| 609 | 6 代码 | 提取测试原文 | 同 603 |
| 610 | 3 插件 | 自查执行方案 | `query_node_result`，`node_name=requirement` |
| 611 | 6 代码 | 提取执行方案原文 | 同 603 |
| 612 | 6 代码 | 合成结构化汇总 | 五轨合成 + 逐品展开 |
| 613 | 1 LLM | 上线审批建议生成 | 生成《上线审批建议》正文 |
| 617 | 2 分支 | 报告写入门禁 | 条件 `all_pass == "1"` |
| 614 | 3 插件 | 报告存储 | `save_node_result`，`node_name=report` |
| 615 | 3 插件 | 审批推送 | `submit_release_approval`，`approval-type=launch` |
| 616 | 9 结束 | 结束(审批已提交) | 展示审批单号/状态 |

---

## 4. 关键节点断言

| 节点 | 类型 | 关键入参 | 出参 | 断言 |
|---|---|---|---|---|
| 601 开始 | 0 | `req_id`(必填)、`chat_id`(必填) | — | `req_id` 取真实时刻生成，禁照抄示例/历史值 |
| 602/604/606/608/610 自查环节 | 3 | `req_id <- 601.req_id`；`node_name`=config/spec/fee/test/requirement；`latest_only=1` | `list` | `submit_way=get`，端点 `result/query` |
| 603/605/607/609/611 取原文 | 6 | `query_list <- 上游.list` | `record_json, req_id, offer_id, offers` | 出参 `relName="{自身id},{出参名}"`；`_offers_of` 优先 `offers[]`，旧单品包装为 1 品（`offer_seq=1`） |
| 612 合成结构化汇总 | 6 | `config_result <- 603.record_json`；`spec_result <- 605`；`fee_result <- 607`；`test_result <- 609`；`requirement_result <- 611` | `summary_json, offer_id, all_pass, fusion_verification, offers` | 见 §5 字段契约 |
| 613 审批建议 | 1 | `summary_json, offer_id, fusion_verification <- 612` | `approval_suggest` | prompt 模板 flow-C；看板四检查项与四类自查一一对应 |
| 617 报告写入门禁 | 2 | 左值 `612.all_pass` | — | 条件=**all_pass 等于 "1"**（op1）；`sourcePort=0` 走 true 分支 |
| 614 报告存储 | 3 | `req_id <- 601`；`node_name=report`；`result_json <- 613.approval_suggest`；`status=ok` | `record_id` | `submit_way=post`；仅 `all_pass==1` 时写入（由 617 门禁） |
| 615 审批推送 | 3 | `req_id <- 601`；`offers <- 612.offers`；`report_url <- 613.approval_suggest`；`approve_confirmed=true`；`approval_flow=standard`；`approval_type=launch` | `approval_id, status, approval_type, offers` | 见 §6 契约；硬门禁 `approve_confirmed=true` + 四环节齐全 |
| 616 结束 | 9 | `approval_id, status, approval_type, offers <- 615` | — | `terminatePlan=useAnswerContent` |

---

## 5. 节点 612 汇总契约

`summary_json` 字段结构：

| 字段 | 类型 | 来源 / 说明 |
|---|---|---|
| `offer_id` | string | `cfg.offer_id`（首品） |
| `offer_name` | string | `cfg.offer_name` |
| `fusion_verification` | string | plan 含 `group.members` 或 `member_offers` 时为 `＋融合成员组合验证`，否则空串 |
| `requirement_summary` | string | 优先需求原文（≤500）；否则由 `plan_json` 提 `prodPrcName/月费/含资源` 拼可读摘要 |
| `config` | object | `{offer_id, plan_json}` |
| `spec` | object | `{pass, audit_summary}`；`pass` 缺失时按含"通过"且不含"驳回"回退判定 |
| `fee` | object | `{pass, risk_summary}`；`pass` 缺失时按含"未通过/风险"回退判定 |
| `test` | object | `{passed, order_id, offer_inst_id, test_report}`；正则提取受理凭证 |
| `all_pass` | bool | `spec_pass==1 且 fee_pass==1 且 test_passed==1` |
| `offers` | array | V3.12 逐品展开，每元素 `{offer_seq, offer_id, offer_name, config_status, plan_json, spec, fee, test, all_pass}` |

节点出参（供下游引用）：

| 出参 | 说明 |
|---|---|
| `summary_json` | 上述结构序列化字符串 |
| `offer_id` | 首品销售品 ID |
| `all_pass` | `'1'`/`'0'`（供 617 门禁） |
| `fusion_verification` | 融合成员组合验证标记 |
| `offers` | 逐品数组 JSON（供 615 批量推送） |

---

## 6. 节点 615 审批推送契约

**入参**

| 参数 | 来源 | 说明 |
|---|---|---|
| `req_id` | `601.req_id` | 四环节结果门禁校验依据 |
| `offers` | `612.offers` | 逐品数组（每元素含 `offer_seq/offer_id`） |
| `report_url` | `613.approval_suggest` | 上线审批建议正文（逐品共用） |
| `approve_confirmed` | 常量 `true` | 审批发起确认标志 |
| `approval_flow` | 常量 `standard` | 审批流 |
| `approval_type` | 常量 `launch` | 审批类型（上线审批） |

**出参**

| 出参 | 说明 |
|---|---|
| `approval_id` | 工单级审批单号（顶层单值，覆盖整批商品） |
| `status` | 整批提交状态 SUCCESS/PARTIAL/FAIL |
| `approval_type` | 审批类型 |
| `offers` | 逐品推送结果数组 JSON，元素 `{offer_seq, approval_type, status, approval_matrix}`（商品级状态；`approval_id` 已提升至顶层） |

**约束**
- 一个需求单对应一张审批单（工单级 `approval_id`），工单内含多商品。
- 插件层硬门禁：`approve_confirmed==true` 且存储中存在该 `req_id` 的四环节结果（config/spec/fee/test）。
- 幂等：同 `offer_id` 返回原 `approval_id`。
- 审批提交后不自动上线/不轮询，模拟审批约 10s 后自动流转为通过。

---

## 7. 节点 616 结束文案

```
《上线审批建议》已提交审批：
- 工单级审批单号：{approval_id}，整批提交状态：{status}，审批类型：{approval_type}
逐品审批状态：
{offers}
审批已提交（不自动上线/不轮询，状态由【查询审批进度】事后查询）。

> **建议处理：** 可回复【查询审批进度】随时查询当前审批状态（模拟审批约10秒后自动通过）
```

---

## 8. 分支走向用例

| 用例 | 前置 | 预期走向 | 结束节点 |
|---|---|---|---|
| WF06-01 | `all_pass==1`（spec/fee/test 全品通过）且 `approve_confirmed=true`、四环节齐全 | 617(0)→614→615→616 | 结束(审批已提交) |
| WF06-02 | `all_pass!=1`（任一环节未通过） | 617(-1) →（无出边，不写报告/不推送） | — |
| WF06-03 | 提交后回复【查询审批进度】 | 由 wf_sub_08 查询 | 可查到 `approval_id`，模拟 10s 后自动通过 |

---

## 9. 关键边界

- `req_id` 须为当前真实时刻生成（`PLAN+yyyyMMddHHmmss+3位随机`），严禁沿用历史值。
- 环节结果缺失/为空 → `_parse` 回退 `{raw: …}`，`*_pass` 走启发式判定，多半导致 `all_pass=0`。
- 多商品：`cfg.offers[]` 非空时逐品展开；旧版单品平面对象包装为 1 品（`offer_seq=1`）。
- 测试环节仅 `test` 原文按原样归档，不在本流程改写；正式版测试报告为 9 章节。
