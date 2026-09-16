# 查询运维（审批进度 + 运行监控 + 告警 + 监控运维方案）—— 程序 D

> 合并对应原子工作流 wf_sub_07（监控运维）与 wf_sub_08（审批进度查询）。轻量支线，按用户意图分流执行；运维问答依据：`references/K5FAQ/`。

## 意图判别（D-1 / D-2 / D-3 分流）
> V6.0：支线选取由 **`dispatcher.py` 出参 `intent`** 决定（QUERY_APPROVAL→D-1、QUERY_MONITOR→D-2、CONFIRM_ONLINE→D-3），模型不再按表述自行判断；下表为 dispatcher 触发词对应关系（规则已内置，仅作追溯）。
| dispatcher intent | 用户表述 | 支线 |
| --- | --- | --- |
| QUERY_APPROVAL | 查询审批进度/审批单状态/审批到哪了/审批结果/审批意见 | D-1 |
| QUERY_MONITOR | 查询监控/查询运营/查询运行监控/运营情况/销售品运行情况/上线后表现 | D-2 |
| CONFIRM_ONLINE | 确认上线（审批通过后的上线动作）/监控运维方案 | D-3 |
| （dispatcher 未命中则 needs_llm） | 表述含两诉求（如"查一下审批和运营情况"） | 规则命中其一，先输出命中的支线 |

## 支线D-1：审批进度查询（wf_sub_08）

### 触发条件
- dispatcher 出参 `intent=QUERY_APPROVAL`。

### 前置检查
- `approval_id`（与 product_id 至少一个，approval_id 优先）：取值 `dispatcher.py` 出参 `entities.approval_id`；
- `product_id`：销售品 ID（缺失 approval_id 时按其查最新审批单），取值 `dispatcher.py` 出参 `entities.product_id`；
- 两者皆缺 → 不发起调用，先追问："请提供审批单号或销售品ID，以便查询审批进度。"
- **会话上下文取参**：`dispatcher.py` 出参 `entities.*` 已按"会话已明确出现过的值"兜底（本会话执行主干或审批环节的产出值），不属于"编造/历史兜底"；仅当会话内从未出现过且用户未提供时才追问。

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
- **status=通过（上架完成）时**：改为输出"审批已通过，销售品上架完成 ✅"，并提示"**建议处理：可输入'确认上线'进入【监控运维】。**"（衔接支线D-3）；
- 模拟服务行为说明：审批提交 10s 后查询即自动流转为"通过（上架完成）"，不会一直停留在"审批中"。

### 禁止事项
- 禁止编造审批状态（仅依据接口出参）。

## 支线D-2：运行监控与告警（wf_sub_07）

### 触发条件
- dispatcher 出参 `intent=QUERY_MONITOR`；
- 定期巡检（由外部调度按日触发时同样加载本程序）。

### 前置检查
- `product_id`（必填）：销售品 ID（存量 9 位 ID 或配置落地返回的 `P+req_id` 形态产品 ID），取值 `dispatcher.py` 出参 `entities.product_id`；
- **V4.0 融合组**：product_id 可传主 offer_id（组维度指标）或成员 offer_id（成员维度），前置检查不强制区分（以出参为准，出参 offer_name 即所查对象）；
- **会话上下文取参**：`dispatcher.py` 出参 `entities.product_id` 会话兜底（本会话执行主干环节1 返回的 product_id）；会话内从未出现过且用户未提供时 → 不发起调用，先追问："请提供要查询的销售品ID。"（禁止凭空编造）；
- `date_range`（选填，默认最近1天，如 `2026-09-11~2026-09-12`）；
- `metric`（选填，默认 all；枚举 order/error/fee/all）。

### 执行程序
1. **监控查询**（原节点2）：
```bash
python scripts/cpcp_api.py query_monitor --product-id "<product_id>" --date-range "<date_range，可省略>" --metric all
```
   - 接口失败 → 按异常矩阵 E17 处理（传输层重试由脚本内置共尝试 3 次，仍失败即 E29 终止询问）："监控查询失败"，终止本轮；
   - `offer_name` 为空时照实输出"产品名称：未登记"，禁止编造名称。
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
- 产品名称：{{offer_name，为空显示"未登记"}}
- 订单量：{{order_count}}（{{order_trend}}）　异常量：{{error_count}}（{{error_trend}}）　计费差错率：{{fee_error_rate}}（{{fee_trend}}）
- 告警列表：{{alarm_list 摘要，为空显示"无"}}
{{异常时附加：已推送告警，告警单号 {{alert_id}}}}
```

### 禁止事项
- 缺 product_id 且会话内无产出值时禁止调用（先追问）；
- 禁止瞒报异常（error_count>0 必须推送告警并明示）。

## 支线D-3：确认上线与监控运维方案（审批通过后）

### 触发条件
- dispatcher 出参 `intent=CONFIRM_ONLINE`（审批通过后用户回复"**确认上线**"，或直接要求"生成监控运维方案"）。

### 前置检查
- `product_id`（必填）：审批通过的销售品 ID（`dispatcher.py` 出参 `entities.product_id`，会话上下文取参规则同 D-2）。

### 执行程序
1. 校验审批状态：先运行支线D-1 确认 status=通过（未通过 → 中断提示"审批尚未通过，暂不能上线"）；
2. **输出监控运维方案（固定模板，数据逐字引用会话上下文，不虚构指标值）**：
```
**{{套餐名称}}监控运维方案已生成**

**一、销售情况监控**
- 订购量、新增量、退订量、订购成功率

**二、受理运行监控**
- 受理成功率、订购失败率、变更失败率、退订失败率

**三、推送规则**
- **定时推送：** 每日09:00推送前一日销售及受理情况；每周一09:00推送近7日趋势；每月1日09:00推送上月运营报告。
- **异常推送：** 订购成功率低于95%、受理成功率低于95%、失败率超过5%、核心指标波动超过30%时立即推送。

**当前状态：** 监控指标已配置，定时推送已配置，异常推送已配置。

{{套餐名称}}已成功上线，运营视图已开启。可输入"查询监控"查看运行情况。
```
3. 用户后续发送"查询监控" → 转支线D-2 执行真实监控查询与异常告警。

### 禁止事项
- 审批未通过时禁止输出"已成功上线"；
- 监控运维方案为固定模板（阈值/推送时间为平台标准口径），禁止自行改写阈值。

## 通用禁止事项
- 会话内从未出现且用户未提供的必填参数先追问，禁止编造后直接调用。
