# 上线审批 —— 程序 C

> 对应原子工作流 wf_sub_06。执行主干四环节全部成功且用户明确确认后，串行自查五类结果、生成上线校验看板并推送审批流（受理验证为自动测试子集，随 test 结果自查，不单列）。

## 触发条件
- 四环节（config/spec/fee/test）全部成功，**且用户明确回复"上线审批"/"发起审批"**；
- 用户仅说"帮我上线"但未确认发起时，先提示："执行结果已保留，回复【上线审批】后才能提交审批流。"

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
从五类结果提取关键字段：套餐名称/套餐档位（需求）、product_id/offer_id/save_result、audit_summary、risk_list、场景数/用例数统计、orderId/offerInstId；
- **V4.0 融合组**：plan_json 为组结构时，额外提取组结构成员清单（plan_json member_offers 角色 + 环节1 出参 group 成员 offer_id），供看板"自动测试"行与报告基础信息引用（逐字引用，禁止推理）。

### 步骤3：生成上线审批建议（原 LLM 节点501g，按以下模板输出，只基于输入数据，不得新增结论）
```
已自动汇总前序环节结果，生成《{{套餐名称}}上线审批建议》。

**上线校验看板：**

| 检查项 | 结果 |
| :--- | :--- |
| 需求完整性 | ✅ |
| 配置规格稽核 | ✅ |
| 资费校准 | ✅ |
| 自动测试（三大验证：受理/计费/客服{{融合品追加："+成员组合验证"}}） | ✅ |

**风险检查：** {{逐字引用：配置完整性/资费风险/计费风险/受理风险结论；P1 警告级问题引用正式版报告第六章风险汇总}}

**整体上线结论：** {{引用正式版报告第三/八章三选一结论}}。

**AI审批建议：{{全部通过→"建议上线"；任一环节未通过→"暂缓上线"}}。**
```
- 看板各检查项与四类自查结果一一对应：需求完整性=requirement 存在且无待补充；配置规格稽核=spec pass；资费校准=fee pass；自动测试（三大验证）=test 通过且受理子集通过（orderId/offerInstId 非空 + 受理场景通过）；
- **V4.0 融合组**：自动测试检查项结论须引用含成员组合验证结果（出参 offer_group_check 全员 ✅ 才算通过；任一成员 ❌ → 本检查项 ❌ 并附成员定位）；报告归档口径不变（正式版 9 章节模板"基础信息"项含融合成员构成，逐字引用组结构，出参无组结构时省略）；
- **自动测试检查项须与正式版报告整体上线结论一致**（✅ 建议上线 / ⚠️ 评估风险后上线 / ❌ 禁止上线；任一 P0 ❌ 时本程序早已被环节4 中断拦截，不应走到本步骤）；
- 任一检查项未通过 → 该行 ❌ 并附原因（引用出参原文），AI审批建议固定"暂缓上线"；
- 禁止补 ✅ 凑数、禁止虚构风险结论。

### 步骤4：报告存储（原节点501r）
- 报告全文按环节4 已生成的正式版《销售品自动化测试报告》（9 章节结构，模板=`references/K3测试/K3测试_销售品自动化测试报告模板_V2.0.md`）原样归档，禁止在本程序中重新生成或改写章节；
```bash
python scripts/cpcp_api.py save_node_result --req-id "<req_id>" --node report --result-json "<report>"
```

### 步骤5：审批推送（原节点0502）
```bash
python scripts/cpcp_api.py submit_approval --req-id "<req_id>" --product-id "<product_id>" --report-url "<report>" --approval-flow standard
```
- 后端硬校验：approve_confirmed（脚本内置 true，以"用户明确回复上线审批"为前提）+ req_id 四环节（config/spec/fee/test）结果齐全才放行，缺失即拒绝（跳步无法推送）；
- 判定：返回 approval_id → 步骤6；`status=NOT_CONFIRMED` 且无 approval_id → 按 E16 中断（不应出现，出现即脚本/文档缺陷）；推送失败（含网络异常）→ 传输层重试由脚本内置（共尝试 3 次），耗尽后按 E16/E29 **直接中断询问**（禁止智能体自行叠加业务重试；幂等：同 product_id 重复提交返回原 approval_id）。

### 步骤6：输出
```
《{{套餐名称}}上线审批建议》已提交审批：
- 审批单号：{{approval_id}}，状态：{{status}}
- **建议处理：可输入"查询审批进度"查看当前审批环节与意见。**
```

## 禁止事项
- 严禁在四环节结果不齐时调用审批推送（后端会拒绝，且属调度违规）；
- 严禁未经用户明确确认自动发起审批；
- 看板检查项结果必须与自查出参一一对应，禁止全 ✅ 敷衍（任一环节异常时应早已中断，不应走到本程序）。
