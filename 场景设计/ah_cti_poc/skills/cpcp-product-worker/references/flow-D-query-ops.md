# 查询运维（审批进度 + 运行监控 + 告警）—— 程序 D

> 合并对应原子工作流 wf_sub_07（监控运维）与 wf_sub_08（审批进度查询）。两条轻量支线，按用户意图二选一执行；运维问答依据：`references/K5FAQ/`。

## 支线D-1：审批进度查询（wf_sub_08）

### 触发条件
- 用户消息："查询审批进度/审批单状态/审批到哪了/审批结果"等。

### 前置检查
- `approval_id`（与 product_id 至少一个，approval_id 优先）；
- `product_id`：销售品 ID（缺失 approval_id 时按其查最新审批单）；
- 两者皆缺 → 不发起调用，先追问："请提供审批单号或销售品ID，以便查询审批进度。"

### 执行程序
```bash
python scripts/cpcp_api.py approval_status --approval-id "<approval_id，可省略>" --product-id "<product_id，可省略>"
```
- 查无审批单 → 按异常矩阵 E21 提示："未找到该销售品的审批单，请确认是否已发起审批。"

按固定格式归纳输出（仅依据接口出参，禁止编造）：
```
审批单号 {{approval_id}}｜状态：{{status}}｜当前环节：{{current_node}}（审批人 {{approver}}）｜最近意见：{{opinion}}｜更新时间：{{update_time}}
```
- status=驳回 时附驳回原因，并提示"可修改执行方案后重新发起"；
- **status=通过（上架完成）时**：改为输出"审批已通过，销售品上架完成 ✅"及通过意见，并提示可发送"查询监控"查看上线后运行情况（衔接支线D-2）；
- 模拟服务行为说明：审批提交 10s 后查询即自动流转为"通过（上架完成）"，不会一直停留在"审批中"。

### 禁止事项
- 禁止编造审批状态（仅依据接口出参）。

## 支线D-2：运行监控与告警（wf_sub_07）

### 触发条件
- 用户消息："查询监控/查询运行监控/销售品运行情况"等；
- 定期巡检（由外部调度按日触发时同样加载本程序）。

### 前置检查
- `product_id`（必填）：销售品 ID。缺失时不发起调用，先追问："请提供要查询的销售品ID。"（禁止编造或使用历史值兜底）；
- `date_range`（选填，默认最近1天，如 `2026-09-11~2026-09-12`）；
- `metric`（选填，默认 all；枚举 order/error/fee/all）。

### 执行程序
1. **监控查询**（原节点2）：
```bash
python scripts/cpcp_api.py query_monitor --product-id "<product_id>" --date-range "<date_range，可省略>" --metric all
```
   - 接口失败 → 按异常矩阵 E17 处理："监控查询失败"，终止本轮。
2. **异常判定**（原节点3，程序 if 判定）：
   - `error_count > 0` 或 `fee_error_rate > 0.1` → 推送告警；
   - 否则 → 直接输出正常摘要。
3. **异常告警**（原节点4，仅异常时）：
```bash
python scripts/cpcp_api.py send_alert --product-id "<product_id>" --alarm-level "<high/middle/low，按异常程度>" --content "<告警正文：含产品、环节、异常指标摘要、建议>"
```
   - 告警文案须含产品与异常摘要，不得空泛。
4. **输出摘要**：
```
【销售品运行监控】{{product_id}}（{{date_range}}）
- 订单量：{{order_count}}　异常量：{{error_count}}　计费差错率：{{fee_error_rate}}
- 告警列表：{{alarm_list 摘要，为空显示"无"}}
{{异常时附加：已推送告警，告警单号 {{alert_id}}}}
```

### 禁止事项
- 缺 product_id 时禁止编造或使用历史值兜底调用；
- 禁止瞒报异常（error_count>0 必须推送告警并明示）。

## 通用禁止事项
- 缺失必填参数时先追问，禁止编造后直接调用。
