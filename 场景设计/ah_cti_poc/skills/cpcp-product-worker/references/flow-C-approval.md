# 上线审批 —— 程序 C

> 对应原子工作流 wf_sub_06。执行主干四环节全部成功且用户明确确认后，串行自查五类结果、生成七章节上线报告并推送审批流。

## 触发条件
- 四环节（config/spec/fee/test）全部成功，**且用户明确回复"发起审批"**；
- 用户仅说"帮我上线"但未确认发起时，先提示："执行结果已保留，回复【发起审批】后才能提交审批流。"

## 前置检查
- `req_id`（必填）：以会话最近一次值为准。缺失时中断："缺少执行方案key，请先完成执行主干。"

## 执行程序

### 步骤1：串行自查五类环节结果（原节点501q1~q5，链式顺序，非并行）
依次执行（任何一步 total==0 → 按异常矩阵 E15/E18 中断，提示先完成执行主干）：
```bash
python scripts/cpcp_api.py query_node_result --req-id "<req_id>" --node config
python scripts/cpcp_api.py query_node_result --req-id "<req_id>" --node spec
python scripts/cpcp_api.py query_node_result --req-id "<req_id>" --node fee
python scripts/cpcp_api.py query_node_result --req-id "<req_id>" --node test
python scripts/cpcp_api.py query_node_result --req-id "<req_id>" --node requirement
```
每步取 `list[0].result_json`（可先用 `extract_record` 子命令提取）。

### 步骤2：合成结构化汇总（原代码节点501s）
从五类结果提取关键字段：需求摘要关键字段、product_id/offer_id/save_result、audit_summary、risk_list、场景数/测点数统计、orderId/offerInstId。

### 步骤3：生成七章节上线报告（原 LLM 节点501g）
按以下模板生成《销售品上线测试与稽核报告》（只基于输入数据，不得新增结论）：
```
1. 需求摘要（引用 requirement 存储结果，仅列关键字段）；
2. 配置落地结果（config：save_result 各分类写入情况、product_id/offer_id）；
3. 稽核结论（spec：audit_summary，通过/驳回）；
4. 资费结论（fee：risk_list 是否为空）；
5. 测试统计（test：场景数/测点数/成功/失败统计，失败测点逐条列出）；
6. 受理验证结论（强制章节，不得省略）：引用测试结果中的 orderId、offerInstId，
   并逐受理场景（套餐新装/副卡加装/套餐退订）给出通过/失败结论；
7. 上线建议：全部通过 → "建议上线"；任一环节未通过 → "暂缓上线"。
```

### 步骤4：报告存储（原节点501r）
```bash
python scripts/cpcp_api.py save_node_result --req-id "<req_id>" --node report --result-json "<report>"
```

### 步骤5：审批推送（原节点0502）
```bash
python scripts/cpcp_api.py submit_approval --req-id "<req_id>" --product-id "<product_id>" --report-url "<report>" --approval-flow standard
```
- 后端硬校验：approve_confirmed（脚本内置 true，以"用户明确回复发起审批"为前提）+ req_id 四环节（config/spec/fee/test）结果齐全才放行，缺失即拒绝（跳步无法推送）；
- 判定：返回 approval_id → 步骤6；`status=NOT_CONFIRMED` 且无 approval_id → 按 E16 中断（不应出现，出现即脚本/文档缺陷）；推送失败 → 重试 1 次后按 E16 中断（幂等：同 product_id 重复提交返回原 approval_id）。

### 步骤6：输出
```
《销售品上线测试与稽核报告》已提交审批：
- 审批单号：{{approval_id}}，状态：{{status}}
- 可随时发送"查询审批进度"查看当前审批环节与意见。
```

## 禁止事项
- 严禁在四环节结果不齐时调用审批推送（后端会拒绝，且属调度违规）；
- 严禁未经用户明确确认自动发起审批。
