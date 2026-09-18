# 查询运维（审批进度 + 运行监控 + 告警 + 监控运维方案）—— 程序 D

> 合并对应原子工作流 wf_sub_07（监控运维）与 wf_sub_08（审批进度查询）。轻量支线，按用户意图分流执行；运维问答依据：`references/K5FAQ/`。

## 意图判别（D-1 / D-2 / D-3 / D-4 分流）
> V6.0：支线选取由 **`dispatcher.py` 出参 `intent`** 决定（QUERY_APPROVAL→D-1、QUERY_MONITOR→D-2、QUERY_OFFER→D-4），模型不再按表述自行判断；下表为 dispatcher 触发词对应关系（规则已内置，仅作追溯）。**V13.0：原支线D-3（CONFIRM_ONLINE 确认上线）已移除，上线审批通过即自动上线衔接监控运维。**
| dispatcher intent | 用户表述 | 支线 |
| --- | --- | --- |
| QUERY_APPROVAL | 查询审批进度/审批单状态/审批到哪了/审批结果/审批意见 | D-1 |
| QUERY_MONITOR | 查询监控/查询运营/查询运行监控/运营情况/销售品运行情况/上线后表现 | D-2 |
| QUERY_OFFER | 查询存量/在架/现有产品信息（按产品名称/描述或产品ID） | D-4 |
| （dispatcher 未命中则 needs_llm） | 表述含两诉求（如"查一下审批和运营情况"） | 规则命中其一，先输出命中的支线 |

## 输出标题头（九环节总表，演示防误判为未实现）
- 本程序覆盖全局**环节9监控运维**（支线D-2 运行监控与闭环），其正式输出必须以 `【环节9/9·监控运维】✅ 执行成功` 标题头开头（序号/全名取 SKILL.md 九环节总表；监控异常告警或取数失败等场景用 `【环节9/9·监控运维】❌ 执行失败`或按异常矩阵引导）；
- 支线D-1（审批进度查询）是**环节8 上线审批**的状态查询入口，非独立环节，输出文字摘要即可（如需凸显可为该查询回复冠 `【环节8/9·上线审批】` 查询回显，属可选）；**上线审批通过即自动上线并输出监控运维方案（环节9），不再单独设置"确认上线"支线**；
- **支线D-4（存量产品信息查询）是只读信息查询，非独立九环节**，按"查询回显"输出结构化产品信息摘要即可，不强制冠环节标题头（如需统一风格可冠 `【环节0/查询】` 类回显，不作硬性要求）。

## 支线D-1：审批进度查询（wf_sub_08，兼容需求工单审批与上线审批）

### 触发条件
- dispatcher 出参 `intent=QUERY_APPROVAL`；
- **V9.1 审批双轨**：本支线可查询**需求工单审批单**（approval-type=requirement，环节1 需求提报后）与**上线审批单**（approval-type=launch，环节8）；审批单号以 approval_id 语义通用，按出参 `approval_type` 区分两类审批。

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

**审批矩阵（逐字引用出参 approval_matrix[]，禁止编造）：**
| 序号 | 审批节点 | 审批角色 | 状态 | 审批意见 | 更新时间 |
| :---: | :--- | :--- | :--- | :--- | :--- |
| {{node_seq}} | {{node_name}} | {{approver}} | {{status}} | {{opinion，无则留空}} | {{update_time，无则留空}} |
（逐行展开全部节点；出参无 approval_matrix 时省略本表，不得补造）
```
- status=驳回 时附驳回原因，并提示"可修改执行方案后重新发起"；
- **D-1 收尾固定块（SKILL.md 纪律5.1）**：审批矩阵之后（整条回复末尾）须输出 `> **建议处理：** {{按下方审批类型衔接建议}}（{{触发词说明}}）`；禁止以审批矩阵表格收尾；
- **status=通过 时按审批类型区分衔接（V9.1；V13.0 上线审批通过即自动上线）**：
  - **需求工单审批**（出参 approval_type=requirement）：输出"需求工单审批已通过 ✅"，收尾块固定 `> **建议处理：** 需求工单审批已通过，建议进入【需求分析】（可回复【开始配置】）`（衔接 flow-A，配置流程第一个交互点）；
  - **上线审批**（出参 approval_type=launch）：输出"审批已通过，销售品上架完成 ✅"，**审批通过即视为销售品已自动上线，不再需要用户回复【确认上线】**，自动衔接下方「V13.0 审批通过自动上线·监控运维方案」并输出运营可视化看板（环节9）；
- **V13.0 审批通过自动上线·监控运维方案（上线审批 status=通过 时自动执行，替代原支线D-3）**：审批通过即自动输出《监控运维方案》+ 单品运营可视化看板（环节9 标题头下），**禁止要求用户再回复【确认上线】**：
```
【环节9/9·监控运维】✅ 执行成功

**{{套餐名称}}监控运维方案已生成**

**一、销售情况监控**
- 订购量、新增量、退订量、订购成功率

**二、受理运行监控**
- 受理成功率、订购失败率、变更失败率、退订失败率

**三、推送规则**
- **定时推送：** 每日09:00推送前一日销售及受理情况；每周一09:00推送近7日趋势；每月1日09:00推送上月运营报告。
- **异常推送：** 订购成功率低于95%、受理成功率低于95%、失败率超过5%、核心指标波动超过30%时立即推送。

**当前状态：** 监控指标已配置，定时推送已配置，异常推送已配置。

**单品运营可视化：** 经 `xsbot-panel` 外链片段（输出结果必须是 JSON，且以 ` ```xsbot-panel ` 代码围栏包裹）加载外部运营页（`mode:"external"`，右侧面板）：
```xsbot-panel
{"version":"1.0","message_id":"{{chat_id}}","panels":[{"panel":"right","mode":"external","url":"http://10.88.158.111:10002/gzdg/orderForm/?busiId=SHI_MING_JI_HUO&chatId={{chat_id}}","title":"实名激活"}]}
```

{{套餐名称}}已成功上线，运营视图已开启。

> **建议处理：** 销售品已成功上线，建议查询运行监控确认上线后表现（可回复【查询监控】查看运行情况）
```
   - 监控运维方案为固定模板（阈值/推送时间为平台标准口径），禁止自行改写阈值；
   - 该可视化面板与 D-2 第5步同一 `xsbot-panel` 外链结构（输出结果必须是 JSON，且以 ` ```xsbot-panel ` 代码围栏包裹：`{"version":"1.0","message_id":"{chat_id}","panels":[{"panel":"right","mode":"external","url":"http://10.88.158.111:10002/gzdg/orderForm/?busiId=SHI_MING_JI_HUO&chatId={chat_id}","title":"实名激活"}]}`），禁止省略面板、禁止用纯文本/纯 URL/纯表格拼凑替代表单渲染、禁止手写页面 HTML/图表载荷、禁止内联监控数据；
   - 用户后续发送"查询监控" → 转支线D-2 执行真实监控查询与异常告警。
- 模拟服务行为说明：审批提交 10s 后查询即自动流转为"通过"，不会一直停留在"审批中"。

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
   - `error_count > 0` 或 `fee_error_rate > 0.1` → 推送告警，并在输出摘要/可视化 URL 后执行**第6步 异动根因本体推理与优化闭环**；
   - 否则 → 直接输出正常摘要（不进入根因推理）。
3. **异常告警**（原节点4，仅异常时）：
```bash
python scripts/cpcp_api.py send_alert --product-id "<product_id>" --alarm-level "<high/middle/low，按异常程度>" --content "<告警正文：含产品、环节、异常指标摘要、建议>"
```
   - 告警文案须含产品与异常摘要，不得空泛。
4. **输出摘要**：
```
【环节9/9·监控运维】✅ 执行成功

【销售品运行监控】{{product_id}}（{{date_range}}）
- 产品名称：{{offer_name，为空显示"未登记"}}
- 订单量：{{order_count}}（{{order_trend}}）　异常量：{{error_count}}（{{error_trend}}）　计费差错率：{{fee_error_rate}}（{{fee_trend}}）
- 告警列表：{{alarm_list 摘要，为空显示"无"}}
{{异常时附加：已推送告警，告警单号 {{alert_id}}}}
```
5. **输出单品运营可视化（xsbot-panel 外链，输出结果必须是 JSON，禁止纯文本/纯表格拼凑）**：文本摘要之后在同一回复中**以 `xsbot-panel` 代码围栏输出 JSON 外链片段**，由前端右面板以 `mode:"external"` 加载外部运营看板 URL（前端 `panel:"right"`），LLM **禁止手写页面 HTML/图表载荷、禁止用纯文本/纯 URL/纯表格拼凑替代表单渲染**：

`xsbot-panel` 外链 JSON——**整段逐字输出以下结构**（`version/message_id/panels[{panel,mode,url,title}]`），`message_id` 用会话 `chat_id`：
```text
```xsbot-panel
{"version":"1.0","message_id":"{chat_id}","panels":[{"panel":"right","mode":"external","url":"http://10.88.158.111:10002/gzdg/orderForm/?busiId=SHI_MING_JI_HUO&chatId={chat_id}","title":"实名激活"}]}
```
```
   - **xsbot-panel 输出结果必须是 JSON，且必须用 ` ```xsbot-panel ` 代码围栏包裹**（上述片段），禁止仅输出纯 URL/纯文本/表格拼凑、禁止改写成非 JSON 结构；`message_id` 与 `url` 中 `chatId` 均取会话消息 ID（会话未出现过时不允许臆造为 0/占位），`busiId` 固定为 `SHI_MING_JI_HUO`、`title` 固定为「实名激活」、`panel` 固定 `right`、`mode` 固定 `external`，外部页面渲染完全由前端完成，LLM 只负责将 `{chat_id}` 替换为会话消息 ID，禁止手写页面 HTML/图表载荷；
   - 数据契约：外部运营页所需数据来自 `query_monitor` 出参（offer_name/order_count/order_trend/error_count/error_trend/fee_error_rate/fee_trend/alarm_list），由前端页面自行拉取渲染，LLM 只负责拼外链 URL，不内联监控数据；
   - **环节9 收尾固定块（V11.0 强制，SKILL.md 纪律5.1）**：可视化面板之后（**整条回复的最后一个内容块**）必须输出下一步建议块，禁止以摘要或面板收尾：
     - 正常（error_count==0 且 fee_error_rate≤0.1）：
       ```
       > **建议处理：** 运行指标正常，无需人工干预；可继续观察，如需刷新运行情况可回复【查询监控】
       ```
     - 异常（已推送告警、已建处置工单）：收尾块改引用根因闭环结论——`> **建议处理：** 已推送告警并建立处置工单 {{workOrderId}}，建议按优化方案执行后回复【查询监控】回检工单状态（工单号 {{workOrderId}} 已在会话中留存）`；工单建单失败（E34-ops）时收尾块声明"工单服务暂不可用，稍后回复【查询监控】重试建单"。**注意：收尾块触发词只能使用 dispatcher 已支持的意图词（【查询监控】等），禁止自造【查询工单】/【关闭工单】等 dispatcher 未定义触发词**；工单回检/闭环所需的 workOrderId 由会话上下文携带，无需用户给出新触发词。
6. **异动根因本体推理与优化闭环（V8.1，仅步骤2判定异常时执行）**——复用现有 CPCP 本体推理平台（backend-app Java，具 `product-ops.ttl` 产商品运营归因与风险本体 + `ops_rules.json` R-A01~A06），**禁止新造本体/自拍 TTL**；本体推理**必须输出推理链**：
   ① **根因推理**（确定性调后端，第2层执行）：
   ```bash
   python scripts/cpcp_api.py ops_root_cause --product-id "<product_id>"
   ```
   - 接口失败 → 按异常矩阵 E34-ops 提示型处理（不阻断，输出"根因推理暂不可用"）；出参 JSON **逐字引用不加工**。
   - 出参含**推理链字段**：`anomalies[]`（R-A01 异动确认）、`paths[]`（R-A02~A05 归因排名，含 rootCauseType/name/weight/ruleId/evidence[]/path[]/isPrimary）、`evidenceTriples[]`（本体 {s,p,o} 证据三元组）、`swrlFiredRules[]`（命中的 SWRL 规则）、`appliedRules[]`、`reasonEngine`（openllet-swrl / java-rules）、`actionList[]`（优化建议）。
   ② **输出根因推理链（第3层组装，逐字引用出参；模型禁止改写/补造证据）**：
   ```
   **异动根因推理链路（{{reasonEngine}}）**
   - 异动确认：{{anomalies 摘要：指标 code、delta、message，逐字引用}}
   - 归因路径（按 paths 排名）：
   | 排名 | 根因类型 | 对象 | 权重 | 规则 | 证据 |
   | :--: | :-- | :-- | :-- | :-- | :-- |
   | {{rank}} | {{rootCauseType 渠道/促销/竞品/行为}} | {{name}} | {{weight}} | {{ruleId}} | {{evidence 逐字引用}} |
   （逐行展开 topN；paths 为空 → 照实引用出参 message"已确认异动但未命中归因规则"，禁止编造根因）
   - 命中规则：{{swrlFiredRules / appliedRules，逗号分隔，空则照实省略}}
   - 证据三元组：{{evidenceTriples 逐字摘要}}
   ```
   ③ **优化方案（规则/图数据驱动，第3层组装）**并**建工单闭环**：
   ```bash
   python scripts/cpcp_api.py create_work_order --product-id "<product_id>" --source ops_assistant \
     --session-id "<会话 session_id，有则传>" --title "{{产品名}}异动根因处置工单" \
     --summary "<异动摘要+根因链路摘要>" --actions '<actionList 的优化动作 JSON 数组，逐字引用>' \
     --root-causes '<paths 的根因 JSON 数组，逐字引用>'
   ```
   - 建单返回 `workOrder.workOrderId`（WO 开头）即工单号；失败按异常矩阵 E34-ops 提示型处理（不阻断根因结论输出）。
   - 输出：
   ```
   **优化方案（规则驱动）**
   {{actionList 逐条引用；无则引用出参 message，禁止编造}}
   **处置工单已建立（持续闭环）**：工单号 {{workOrderId}}｜状态 open｜来源 {{source}}
   回检方式：后续可输入"查询工单 / 关闭工单"进行状态跟踪与闭环回检。
   ```
   - **闭环纪律**：告警→根因→方案→工单 形成持续闭环；`actionList`/`workOrder.draft` 为规则/图数据驱动（`graph.actionSuggestions`/`disposition.defaultAction`），**LLM 禁止在证据与出参之外编造优化建议**；根因/优化全部依据 `ops_root_cause` 出参逐字引用。

### 禁止事项
- 缺 product_id 且会话内无产出值时禁止调用（先追问）；
- 禁止瞒报异常（error_count>0 必须推送告警并明示）；
- 根因决策/优化建议一律以 `ops_root_cause`/工单后端出参为准，**禁止 LLM 在证据链之外自行推断根因或编造优化方案**。

## ~~支线D-3~~（已移除）

> **已废弃（V13.0）**：原"确认上线与监控运维方案（支线D-3，dispatcher `CONFIRM_ONLINE`）"已删除。**上线审批（approval-type=launch）通过即视为销售品已自动上线，直接自动衔接环节9 监控运维并输出《监控运维方案》+ 运营可视化看板**，不再需要用户二次回复"确认上线"。该《监控运维方案》模板已并入支线D-1 的「V13.0 审批通过自动上线·监控运维方案」步骤（见上）。dispatcher 已移除 `CONFIRM_ONLINE` 意图，用户回复"确认上线"不再单独路由；监控运维方案于审批通过后自动输出。

## 支线D-4：存量产品信息查询（按名称/描述 或 产品ID）

### 触发条件
- dispatcher 出参 `intent=QUERY_OFFER`；
- 用户查询**存量/在架/现有产品信息**：按产品名称/描述（如"查 5G-A融合套餐199元的存量信息"、"查询存量产品信息"）或按产品 ID（9 位编码，如"900102306 的产品资料"）。**只读查询，不触发任何配置/审批/上线动作**。

### 前置检查
- 查询入参（二选一，均可选）：`entities.product_id`（9 位存量编码）或 `entities.offer_name`（产品名称/描述关键词）；两者皆缺 → 不发起调用，先追问："请提供要查询的产品名称或产品ID。"
- **会话上下文取参**：`dispatcher.py` 出参 `entities.*` 已按"会话已明确出现过的值"兜底，不属于编造/历史兜底；会话内从未出现过且用户未提供时才追问。

### 执行程序
查询统一走确定性本地脚本 `query_offer`（第2层执行，读取存量目录 + K4 存量销售品资料库，LLM 不参与检索判定）：
```bash
# 按产品 ID（9 位编码）
python scripts/cpcp_api.py query_offer --product-id "<product_id>"
# 按产品名称/描述关键词
python scripts/cpcp_api.py query_offer --name "<offer_name>"
python scripts/cpcp_api.py query_offer --keyword "<描述关键词>"
```
- **数据源**：`references/存量产品目录_清洗后.json`（存量在架产品目录 18 条，字段 offer_id/name_clean/product_type/tier/members/template/status/duplicate_of，随 skill 打包内置在 `references/` 下）+ `references/K4存量/`（K4 存量销售品资料库，按产品 ID 单文件）；`status=dup` 的条目按 `duplicate_of` 透传到 active 品；
- 出参 `matched[]`（逐条含 offer_id/name/product_type/tier/template/members + k4_text 档案原文 + k4_path）；
- **命中且唯一（len(matched)==1）→ 输出结构化查询回显**（仅依据出参，逐字引用，禁止编造）：
```
**存量产品信息（{{name}}）**
- 产品ID：{{offer_id}}｜类型：{{product_type}}｜产品线：{{biz_series}}｜档位：{{tier}}｜模板：{{template}}
- 成员：{{members，逗号分隔；无则省略}}
- 档案说明：{{k4_text 依档案绪言/总述摘要引用，超长时展示结构化要点，禁止改写数据}}
```
  提示下一步："如需了解该产品的监控情况，可回复【查询监控】。"
  > **收尾固定块（SKILL.md 纪律5.1）**：查询回显末尾须输出 `> **建议处理：** 存量产品信息为只读查询；如需了解该产品上线后表现可回复【查询监控】`，**禁止以产品信息条目列表收尾，禁止使用 dispatcher 未定义的【存量合规扫描】等自造触发词**。
- **命中多条（len(matched)>1，按名称模糊多款命中时）→ 先列匹配清单请用户收敛到具体产品，再按其 offer_id/名称精确查询**（禁止把所有匹配并成一团输出、禁止臆选其中一款）：
```
**存量产品信息查询，匹配到 {{count}} 款，请选择或提供产品ID：**
| 产品ID | 名称 | 类型 | 档位 |
| :--: | :-- | :-- | :-- |
   | {{offer_id}} | {{name}} | {{product_type}} | {{tier}} |
   （逐行列出全部 matched）
   ```
   > **收尾固定块（SKILL.md 纪律5.1）**：匹配清单末尾须输出 `> **建议处理：** 命中多款产品，请收敛到具体产品后精确查询（请直接回复产品ID或完整产品名称，我可据此重新查询）`，禁止以匹配清单表格收尾（此处引导用户回复的是**产品ID/名称实体**，非意图触发词，dispatcher 会据实体兜底路由到 QUERY_OFFER）；

  用户回复具体产品ID/名称后，重新执行 `query_offer --product-id/--name` 输出该款详情；
- **未命中（matched=[]）→ 追问引导**："未在存量目录中找到该产品，请核对名称/ID后重新提供关键词（我会按名称/ID重新查询）。"
- 存量产品信息查询为只读，**不生成 req_id、不进入需求分析/配置流水线**。

### 禁止事项
- 禁止把存量产品信息查询误转入需求提报/配置/审批/上线流程（只读查询）；
- 禁止编造产品字段（名称/档位/成员/资费均以目录与 K4 档案为准）；
- 禁止改写 K4 档案数据、禁止把"未收录"产品包装为"已收录"；
- 会话内从未出现且用户未提供的必填参数先追问，禁止编造后直接调用。

## 通用禁止事项
- 会话内从未出现且用户未提供的必填参数先追问，禁止编造后直接调用。
