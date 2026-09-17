# 需求提报 —— 程序 A0（环节1：解读需求 → 生成需求提报文档 → 发起需求工单审批）

> 对应 SKILL.md 九环节总表**环节1 需求提报**。当用户确认意图为【配置】后触发。
> 本程序只在会话层完成"需求解读 + 需求提报文档生成 + 需求工单审批门禁"，**不调用配置落库接口、不产出执行方案**；产出为《销售品需求提报单》文本 + 需求工单审批单号（若发起）。
> 下一流程【需求分析】（flow-A）触发前置条件：**需求工单审批已通过**（或用户明确跳过审批并获授权）。

## 四层架构定位
- 第0层 意图解析：dispatcher.py 出参 `intent=ASK_INTENT`（产品描述未表明意图）时由模型先澄清；
- 用户明确【配置】→ 再次运行 dispatcher → `intent=REQ_REPORT`（route=A）→ 加载本程序；
- **LLM 仅承担"自然语言→结构化"翻译**：按 `references/requirement-report-template.md` 模板解读需求，逐字引用用户描述中的字段值；需求理解（need_summary）不超过 2 句。

## 触发条件
- dispatcher 出参 `intent=REQ_REPORT`，且会话具备"已确认配置意图"上下文（用户对意图澄清问题回复了【配置】，或消息本身含明确配置意图词）。
- 入口第一动作固定运行 dispatcher（禁止模型自行判定意图）：
```bash
python scripts/dispatcher.py --message "<用户最新消息>" [--session-file "<会话上下文 JSON>"]
```

## 执行程序

### 步骤0：意图澄清（当 dispatcher 出参 intent=ASK_INTENT 时）
- dispatcher 出参 `intent=ASK_INTENT`（matched_rule=product-desc-ask / product-doc-ask）时，**不执行任何业务**，先向用户澄清：
```
您提供的是一段产品描述，请确认您的意图：
① 查询 —— 查询该产品/销售品信息（存量、资费、测试等）
② 配置 —— 对该产品发起需求提报与配置流程
请回复【查询】或【配置】。
```
- 用户回复【查询】→ 重新运行 dispatcher，按查询类意图（QNA/QUERY_MONITOR）加载对应流程；
- 用户回复【配置】/【我要配置】→ 重新运行 dispatcher → `intent=REQ_REPORT` → 进入步骤1。

### 步骤1：解读用户需求
- LLM 仅做"自然语言→结构化需求字段"翻译：从用户需求描述/文档中**抽取并归一**为 snake_case 平面 JSON
  （字段名与 `scripts/render_requirement_report.py` 的字段集一致：name/product_type/series/members/
  price/resources/out_price/billing_cycle/effective_way/validity/change_rule/cancel_rule）；
- **业务模板分支（单/融合商品模板不同，禁止混用）**：按 `product_type` 区分——**融合品类型**
  （家庭基础套餐，schema 顶层含成员块）时抽取"融合成员 members"（有值渲染，缺失从宽不催补）；**单商品类型**
  （个人主套餐/个人附加资费/宽带主套餐/宽带附加资费/家庭附加资费）时**不抽 members**（渲染脚本
  不含该字段，既不渲染、也不判其待补充）。product_type 未提及/未知时按单商品字段集渲染，禁止臆造成员；
- **待补充判定口径（从宽，仅必要字段）**：LLM 抽取后由渲染脚本判定——仅**必要的资费价格（套餐档位 price）+ 资费免费资源（套内资源 resources）**缺失才判待补充；其余字段（套外资费/计费周期/生效方式/有效期/变更规则/退订规则等）缺失不判待补充，按用户原话引用（缺则对应行不渲染）；
- 逐字引用用户表述，**未提及的字段留空**（空/占位标记），交渲染脚本判定待补充；**禁止从相似产品照搬或臆造值**（价格纪律延用），**禁止 LLM 自行渲染成中文文档**；
- 需求原文为整篇文档时，先归纳 need_summary（1~2 句）作为 `--need-summary` 注入。

### 步骤2：生成《销售品需求提报单》（确定性脚本判定 + render_a2ui 展示，禁止纯文本拼凑）
- 待补充判定与落盘由确定性脚本完成（禁止 LLM 手工判定/渲染）：
```bash
python -X utf8 "scripts\render_requirement_report.py" \
  --req-id "<req_id>" --elements-json-file "<LLM抽取的需求字段JSON>" \
  --reporter "<提报人，可空>" --need-summary "<需求概述1~2句>" --workdir "<会话可写目录>"
```
- 出参 JSON（逐字引用）：`req_id/report_date/pending_fields/pending_count/report_text/artifact`；
   成败**仅以 resultCode=0 与否判定**，禁止语义猜测；`pending_count>0` → 出参 pending_fields 即【待补充字段】清单（**仅必要字段=资费价格+资费免费资源 缺失进入**；含价格字段"待补充"则整体待补充）；**单/融合品分支与待补充口径由渲染脚本按 product_type 与 REQUIRED_FIELDS 确定**，模型禁止手工增删待补充字段；
- **《销售品需求提报单》展示用 render_a2ui（不是把 report_text 当纯文本粘贴）**：调用 `render_a2ui`，组件取自官方 `references/a2ui_forms.md`（信息展示→Card+List【模板5】、表格→DataGrid【模板16 #22】），填充出参/需求字段值（req_id/need_summary/product_name/product_type/price/resources/out_price/billing_cycle/effective_way/validity/change_rule/cancel_rule/待补充清单 pending_summary）；禁止用纯文本拼凑替代表单；
- 输出以 `【环节1/9·需求提报】✅ 执行成功` 标题头开头（九环节总表，禁止缩写）；
- 脚本自动落盘工件 `requirement_report_<req_id>.json`（会话工作区，供需求工单审批与后续流程引用），req_id 沿用 flow-A 生成规则（PLAN + 时间戳 + 随机；缺省时由脚本生成）。

### 步骤3：提示发起需求工单审批（门禁）
- 需求提报单生成后，**必须输出提示**：
```
是否发起【需求工单审批】？可回复"发起需求审批"提交审批；或回复【跳过审批】直接进入需求分析（需授权）。
```
- 用户明确回复"发起需求审批"/"提交需求审批" → 步骤4；
- 用户回复【跳过审批】且会话授权允许 → 记录跳过，直接允许进入需求分析（作为演示/授权旁路）；
- 用户未表态 → 停留在本步骤等待，不擅自发起审批、不进入需求分析。

### 步骤4：发起需求工单审批（复用 submit_approval，approval-type=requirement）
```bash
python scripts/cpcp_api.py submit_approval --req-id "<req_id>" --product-id "<req_id>" \
  --report-url "<需求提报单内容或存储路径>" --approval-flow requirement --approval-type requirement
```
- 审批对象 product_id 在需求阶段尚未落地为销售品，POC 以 req_id（需求单号）作为审批对象标识（后端按 approval-type=requirement 路由为需求工单审批流）；`--report-url` 引用步骤2 落盘的需求提报单工件 `requirement_report_<req_id>.json`（或其路径）；
- 返回 `approval_id` → 记录为**需求工单审批单号**，输出：
```
【环节1/9·需求提报】✅ 执行成功

《销售品需求提报单》已生成，需求工单审批已发起：
- 需求单号：{{req_id}}
- 需求工单审批单号：{{approval_id}}，状态：{{status}}

建议处理：可输入"查询审批进度"查看需求工单审批状态；审批通过后回复"开始配置"进入【需求分析】。
```
- 推送失败/网络异常 → 按异常矩阵引导（传输层重试由 cpcp_api 内置共 3 次，仍失败按 E16/E29 中断询问），需求提报单保留。

### 步骤5：需求工单审批通过门禁
- 用户后续查询需求工单审批状态（flow-D 支线D-1）得知 status=通过；
- **需求工单审批通过后**，用户回复【开始配置】/【需求分析】→ 触发 flow-A（需求分析六步）；
- 审批未通过/未发起/用户未确认 → 禁止进入需求分析（flow-A 前置检查硬性校验需求工单审批状态）。

## 输出头（九环节总表，演示防误判）
- 本程序覆盖全局**环节1 需求提报**，正式输出必须以 `【环节1/9·需求提报】✅ 执行成功` 标题头开头（序号/全名取 SKILL.md 九环节总表，禁止缩写、禁止漏标题头）；
- 需求提报单渲染在标题头下；需求工单审批发起信息也在本标题头下输出；
- 需求工单审批**通过后**进入的需求分析走环节2 标题头（`【环节2/9·需求分析】`，见 flow-A）。

## 禁止事项
- 禁止未经用户明确意图（配置）就进入需求提报（先澄清）；
- 禁止擅自发起需求工单审批（须用户明确"发起需求审批"）；
- 禁止在需求提报单中臆造/照搬价格与规则值（价格纪律延用）；
- **禁止 LLM 手工渲染《销售品需求提报单》中文文档或改写模板结构**（待补充判定/落盘一律由 `render_requirement_report.py` 确定性完成，LLM 只输出结构化需求字段 JSON）；
- **《销售品需求提报单》展示必须走 `render_a2ui`（组件取官方 `references/a2ui_forms.md`：Card+List/DataGrid），禁止纯文本拼凑替代表单**；
- 禁止生成执行方案/调配置落库接口（需求提报只在会话层，不落地）；
- 禁止需求工单审批未通过时进入需求分析（门禁硬校验）。
