# 需求分析（执行方案生成）—— 程序 A（V7.0 逻辑模型模板驱动）

> 对应原子工作流 wf_sub_01。产物：《销售品加载方案》（分节多表 plan_md_v2 + 嵌套 plan_json_v2 + flat24 派生 plan_json）+ 存储键 req_id + 规范配置原文工件 `plan_json_<req_id>.json`（供 flow-B 环节3 起读）。
> V7.0 模板轨重构（评审结论#1 直接切换）：配置报文唯一骨架 = 6 个产品配置逻辑模型模板 schema（`scripts/templates/`，源自《产品配置结构化映射逻辑模型规范.xlsx》）。
> **四层架构铁律**：LLM 仅承担"自然语言→结构化翻译"（①产品识别、④要素提取两处）；合并/校验/渲染/路由等确定性逻辑一律由脚本完成，输出逐字节可回归（幂等）。
> 旧轨（24 字段本体驱动）已整体退役：V6.1 原文存档于仓库 `场景设计/ah_cti_poc/方案/_deprecated/flow-A-v61-备份.md`（**不在 skill 包内**，仅作历史参照）；`merge_fields`/`ontology_reason`/`build_plan` 标 @deprecated（过渡期保留）。

## 触发条件
- dispatcher 出参 `intent=REQ_REPORT`（用户对销售品发起配置流程），**且需求提报（flow-A0）已完成、需求工单审批已通过**（或经授权跳过审批）；
- **门禁（V9.1）**：需求分析是配置流程的第一个交互点，须前置满足——①用户意图确认为【配置】；②《销售品需求提报单》已生成（flow-A0 产出）；③需求工单审批 status=通过（或已授权跳过）；否则不进入本程序，回到需求提报环节提示"需求工单审批未通过，暂不能进入需求分析"；
- 模型不得自行判断是否进入本程序，一律以 dispatcher 出参为准（四层架构第0层）；
- 修改场景重新分析，覆盖写同 req_id。

## 编排收敛（V10.0）：run_requirement.py 三阶段状态机

> **定位**：flow-A 确定性步骤（②③⑤⑤.5⑥⑦ + 双源相似检索降级 + 文件切分 + 校验判定 + 保存渲染）
> 已收敛为单一脚本 `scripts/run_requirement.py`（对齐 run_pipeline 对 flow-B 的收敛模式），**模型不再逐步调用单环节脚本手工编排**。
> 模型只承担两处 LLM 翻译（①产品识别、④要素提取）与读最终渲染结果，往返由 ~12 次降至 ~3 次。
> **六步逻辑口径（下文①②③④⑤⑤.5⑥⑦）仍是唯一事实源**；run_requirement 三阶段只是把确定性子步骤代码化封装的执行载体，出参与六步手编排逐字节一致。
>
> **V2.0（本轮）增量收敛**：
> - **纯本地确定性步骤改直调库、不再子进程派生**（消除 5 次子进程启动与 argv/解析失败面）：`merge_nested`/`validate_elements`/`render_table` 直接 import，`identify_products`/`derive_flat24` 内联纯逻辑（出参与原子命令逐字节一致，已回归验证）；仅后端 HTTP 步骤（`validate_nested`/`explain_nested`/`save_node_result`/`query_offer`/`similar_offer`）保留子进程 `_api`。
> - **stdout 紧凑化**：渲染表格落盘 `plan_md_<req_id>.md`、推理依据落盘 `explain_<req_id>.json`、全量结果落盘 `requirement_result_<req_id>.json`；`--stage merge-save` 的 stdout 只回【紧凑摘要】（exit/channel/pending/产物路径），**不再内联巨大的分节多表与推理长篇** → 大幅降低模型往返 token。模型需读渲染结果时读 `requirement_result_<req_id>.json` 的 `tables` 字段或 `plan_md_<req_id>.md`。**注意：'紧凑'仅限脚本 stdout（模型内部交接收底），绝不改变环节2 交付正文的完整性——用户可见出口必须全量展示 `tables` 分节多表（禁止用省略号截断，见下"输出纪律"纠偏）**。
> - **阶段状态持久化 + 前置校验**：`requirement_state_<req_id>.json` 记录 `done_stages`（products→prepare→merge-save），跨进程防错舞台调用/重复执行；跳过前置阶段直接调用目标阶段 → E30 前置缺失中断（rc=2）。
> - **E31 增量补提（方案B 增强，`--elements-supplement`）**：质量门禁 FAIL 时状态机已落盘 `missing_required_<req_id>_<n>.json`（精确列出未命中可提取必填路径+label+枚举+类型+hint），LLM 只补缺项写成 supplement 文件，`--elements-supplement` 与既有 elements **叠层深度合并** 后重跑 `--stage merge-save`，免整段重提。

**三阶段调用形态（每阶段一次脚本调用，模型在阶段间做 LLM 翻译）：**

```bash
# 阶段一：步骤① 后置闸（identify_products 内联）。入参=LLM 步骤① 识别的 products JSON。
python -X utf8 "scripts\run_requirement.py" --stage products --req-id <req_id> --workdir <会话可写目录> --products-file <products.json>
#   → 出参 valid_products；invalid 非空 → E30 中断；products_valid.json 落盘

# 阶段二：步骤② 相似检索（本地 query_offer 优先 + 远端 similar_offer 兜底，脚本自动降级，模型不判异常矩阵）
#        + 步骤③ 取模板 + 组装步骤④ 提取提示词资产（叶子清单+需求原文+相似品）
python -X utf8 "scripts\run_requirement.py" --stage prepare --req-id <req_id> --workdir <会话可写目录> --requirement-file <需求原文.txt>
#   → 双源均未命中 → E1 中断；命中 → extract_prompt_<req_id>_<n>.json 落盘（供步骤④ LLM 翻译）

# [LLM 步骤④ 翻译]：按 extract-prompt-template.md，读 extract_prompt 资产，产出 elements JSON

# 阶段三：步骤④ 校验闸 + ⑤ 合并 + ⑤.5 校验 + ⑦ 保存 + ⑥ 渲染（全脚本内置）
python -X utf8 "scripts\run_requirement.py" --stage merge-save --req-id <req_id> --workdir <会话可写目录> --elements-file <elements.json>
#   → stdout 只回紧凑摘要；渲染表/推理/全量结果落盘 requirement_result_<req_id>.json 等工件
#   E31 增量补提：python -X utf8 "scripts\run_requirement.py" --stage merge-save --req-id <req_id> \
#       --workdir <会话可写目录> --elements-file <原elements> --elements-supplement <补提文件>
#   退出码：0=出口B(已保存) / 1=出口A(待补充未保存) / 2=参数/前置缺失 / 3=E30 / 4=E31 / 5=E32 / 6=E33 / 7=E1
```

**阶段内自动完成（模型不再干预）**：
- **双源相似检索自动降级**：本地 `query_offer`（逐关键词候选）命中→取 top1 相似品 + 其 K5 报文；未命中→自动切远端 `similar_offer`；双源均空→E1 中断（异常矩阵判定收敛脚本化，模型不再读 exception-matrix 逐字判断）。
- **模板路由**：相似品 `template` 字段或 K5 报文包裹层决定，模型不猜 templateId。
- **三工件落盘（方案C）**：`payload_<req_id>_<n>.json` / `meta_<req_id>_<n>.json` / `similar_offer_meta_<req_id>_<n>.json`（及相似品报文工件），render_table/validate_nested 直接引用，**消除模型手工拆分 merge 出参**。
- **校验判定**：validate_elements 质量门禁、validate_nested 本体校验（E33 中断）、explain_nested 推理依据、derive_flat24 派生、save_node_result 保存（pending 空时）、render_table 渲染，全部脚本内置。

## 输出标题头（九环节总表，演示防误判为未实现）
- **环节1 需求提报已独立为 flow-A0 承担**（解读需求→生成需求提报文档→需求工单审批门禁），本程序仅覆盖全局**环节2需求分析**，输出时按下列标题头显性成节（序号/全名取 SKILL.md 九环节总表，禁止缩写、禁止漏标题头）：
  - **需求分析**：`【环节2/9·需求分析】✅ 执行成功`——六步流程完成后，在出口A/出口B 文案前输出，标题头下空一行再接 render_table 分节多表；
  - 若需求提报（环节1）与需求分析（环节2）在同一会话连续进行，两标题头连续输出（中间以 `---` 分隔），保证演示一眼可辨"需求提报→需求分析"两步均已实现；出口A（待补充未保存）同样输出两标题头，结论按出口A 文案。

## 前置检查
- `requirement_text`（必填）：需求原文或文档内容摘要。缺失时追问："请提供销售品需求描述或上传需求文档。"

## 取值链（逐级降级，禁止跳级虚构）
```
原始需求（LLM 第④步提取，source=原始需求）
  → 相似产品模板报文同路径补全（source=AI补全；merge_nested 第⑤步）
  → schema default 兜底（source=默认值）
  → 待补充（价格类字段 + 必填缺失；merge_nested pending_required 输出）
```

## 执行程序（六步，严格按顺序）

### 步骤①：产品列表识别（LLM，翻译环节）
对 requirement_text 做产品识别，输出**封闭 schema**（禁止自由文本）：
```json
{"products": [{"prodName": "5G-A融合套餐199元",
               "prodType": "个人主套餐|宽带主套餐|个人附加资费|宽带附加资费|家庭基础套餐|家庭附加资费",
               "members": ["宽带", "天翼高清", "副卡功能费"]}],
 "need_summary": "≤5000字符需求摘要"}
```
- **产品类型枚举以 templates/ 下各 schema 顶层 `x-product-type` 集合为准（低代码化，动态漂移）**——新增配置场景投 schema 声明 `x-product-type` 即自动成为合法类型；下方括号内仅为当前 6 模板的取值，非封闭写死。LLM **不直接猜 templateId**（模板路由由代码做，防模板名幻觉）；多产品并存时逐项列出；
- **第①步必须走 LLM**（评审结论#2），不做规则优先；融合需求（宽带/天翼高清/副卡/权益包等成员表述）在 members 中体现，模型禁止自行推理成员间关系（成员构成仅作检索参照，非下发数据源）；
- 识别结果必须经确定性后置闸校验：
```bash
python -X utf8 "scripts\cpcp_api.py" identify_products --products-json "<①输出>"
```
  出参 `invalid_products` 非空 → 产品类型无法归类，按 E30 中断询问（禁止默认路由，禁止自动重试）；
- 校验通过 → 以 `valid_products` + `need_summary` 进入步骤②。

### 步骤②：相似产品查询（工具，双源：本地主源 + 远端兜底）
> **V10.0 收敛**：本步双源检索与自动降级判定已由 `run_requirement.py --stage prepare` 脚本内置（本地 query_offer 逐关键词命中→远端 similar_offer→双源均空 E1），模型不再手动判异常矩阵。
> 本地主源一律经确定性脚本检索，禁止 LLM 手工找路径/手读 JSON/自行判定"目录缺失"（四层架构：路由与检索确定性化；`query_offer` 已内置正确路径解析）。

**主源（本地，确定性，优先）**——逐产品执行确定性检索：
- **检索脚本（唯一入口，禁止手工定位数据文件）**：
```bash
python -X utf8 "scripts\cpcp_api.py" query_offer --keyword "<套餐名/关键词（用紧凑词，勿带空格，如'单品'/'5G-A套餐'）>"
```
  - `query_offer` 内部读 skill 包内置存量目录 `references/存量产品目录_清洗后.json`（**随 skill 打包内置在 `references/` 下，脚本已按 skill 相对路径正确解析，模型不得自行按其他目录重寻**），出参 `matched[]` 逐条含 offer_id/product_type/tier/template/members；
- 按步骤① 的 prodType 对齐 `product_type`（单品套餐/融合套餐/权益包等）+ tier 金额最接近 + members 关键词，从 `matched[]` 取相似度 top1 的 `offer_id`（tier 无同档时取档位最接近者；同档多品取首个）；
- 命中 → 直接取该品**实例化报文** `references/K5存量报文/<offer_id>.json`（嵌套逻辑模型报文，含 templateId 包裹层）作为该产品的相似品报文；`status=dup` 的条目 `query_offer` 已自动透传到 `duplicate_of` 指向的 active 品（模型直接使用其返回即可）；
- **价格纪律已内置于合并器**：相似品报文中的价格字段不会被照搬（第⑤步 merge_nested normal 模式强制），本步骤无需人工剔除。

**兜底源（远端）**——本地 `matched=[]`（未命中）时逐产品执行：
```bash
python -X utf8 "scripts\cpcp_api.py" similar_offer --desc "<need_summary>"
```
- resultCode=="0" 且 similarOffer 非空 → 取出参（24 字段 offerInfo；**后端 POC 改造后含 `offerTemplate` 嵌套报文（评审结论#3），可直接作相似品报文**；offerTemplate 缺席时暂以本地主源为准——此处本地已未命中则按 E1 中断询问，**禁止模型手工把 24 字段逆投影为嵌套报文**）；
- **其余任何情形（本地未命中 + 远端失败/未命中/脚本报错）一律按 E1 中断询问用户**：resultCode=="0" 但 similarOffer 为空、resultCode=="1"、或 resultCode 为其他任意值（如 PARAM_MISSING/HTTP_xxx/NET_ERROR/TIMEOUT，须原样引用 resultMsg 供日志定位，禁止臆造原因）、或脚本报错 → **终止流程并询问**："相似产品服务暂不可用/未命中，回复【继续】跳过相似产品仅用需求原文补全，或回复【修改需求】调整后重提"；禁止自动降级继续。

### 步骤③：模板获取（工具，纯路由）
逐产品执行（templateId 由步骤② 相似品的 `template` 字段或 K5 报文包裹层决定，模型不猜）：
```bash
python -X utf8 "scripts\cpcp_api.py" get_template --template "<templateId>"
```
- 出参 `schema` = 模板 schema 全文（含 x-template/x-label/properties/enum/x-show-when/x-required）；
- 6 模板注册（当前 6 模板：personMainPrc 个人主资费 85 叶子 / broadBandMainPrc 宽带主资费 43 / personAddPrc 个人附加资费 99 / broadBandOptSpeedPrc 宽带加速包 45 / familyBasePrc 家庭基础套餐 93 / familyAddPrc 家庭附加业务 75；合法产品类型枚举=各 schema 顶层 `x-product-type` 集合，新模板投放后自动纳入，无需改代码）；
- 模板不存在/文件损坏 → E32 中断（脚本报 PARAM_MISSING，原样引用 resultMsg）；
- **提取提示词组装**：将出参 schema 的**叶子清单**（path | x-label | type | 枚举前6项 | x-required | x-show-when）按 `references/extract-prompt-template.md` 模板注入（叶子 43~99 行约 1~3K token，可一次注入；禁止 LLM 自行读模板文件）。

### 步骤④：模板化提取（LLM，翻译环节）+ 校验闸
按 `references/extract-prompt-template.md` 提示词（规则 8 条逐条强制）逐产品提取配置要素：
- 只提取原文可找到（含同义改写）的路径；未提及路径**不输出**（合并器按 schema 补骨架，无需空值）；
- 输出嵌套 JSON（与模板同构，仅命中路径），**枚举字段全放开为自由文本（V9.2）：用原文措辞、不强行归一、不做枚举命中校验**（评审结论#4 精神延续——校验不改写，现彻底放开枚举）；
- 修饰语剥离（"30GB（可结转）"→ 资源值 + 结转路径分置）；价格值必须取原文数字（"199元/月"→199）；
- 展示条件字段（清单标"条件:"）仅当原文明确满足条件时提取；成员表述归入对应成员容器，禁止混入基础信息；
- **价格字段正常提取**（主套餐档位/宽带月功能费/副卡月功能费各自独立判定，禁止互相推导）。

**校验闸（强制，禁止跳过）**：
```bash
python -X utf8 "scripts\cpcp_api.py" validate_elements --schema-file "scripts\templates\<templateId>.schema.json" --elements-json "<④输出>" --mode normal --threshold 0.30
```
- 出参 `quality_gate=FAIL`（可提取必填命中率 < 阈值）→ **E31**：打回重跑第④步 LLM **增量补提**一次（脚本已落盘 `missing_required` 清单，精确列出未命中的可提取必填路径 + x-label + 枚举 + 类型，LLM 只补缺项、不整段重来），仍低则中断转人工；
- `removed`（非法路径/非法数值）→ 已自动剔除，重跑时以 `valid_paths` 口径提示 LLM；
- `yes_norm_hints`（是否类建议归一）→ 仅提示，不改写；
- **枚举不再参与校验（V9.2）**：`validate_elements` 已移除 enum_violation，枚举字段接受任意原文；
- 校验通过（PASS / PASS_WITH_WARNINGS）→ 进入第⑤步。
- **V10.0 编排收敛**：本闸与第⑤⑤.5⑥⑦步合并为 `run_requirement.py --stage merge-save` 一次调用；FAIL 时状态机直接回喂 `missing_required` 明细（方案B 增量补提），无需模型整段重跑。

### 步骤⑤：嵌套报文合并（工具，确定性）
逐产品执行（normal 模式 = 新需求链路）：
```bash
python -X utf8 "scripts\cpcp_api.py" merge_nested --schema-file "scripts\templates\<templateId>.schema.json" --elements-json "<④校验后要素>" --offer-json "<相似品报文>" --template "<templateId>" --mode normal
```
- 合并规则（脚本内置，模型不得自行合并）：schema 为骨架逐路径对位（JSONPath，禁止模糊匹配）→ 需求要素有值用需求值(source=原始需求) → 无值且非价格取相似品同路径(source=AI补全) → schema default 兜底(source=默认值) → 仍缺留空、必填进 `pending_required`；
- **价格禁照搬（normal 模式强制）**：档位/月费/月租/固定费类字段需求未提供时一律留空进待补充，禁止从相似品取值；主套餐档位 ≠ 宽带月功能费 ≠ 副卡月功能费，各自独立判定；
- **枚举全放开为自由文本（V9.2）**：schema 枚举仅作展示/参考，不做命中校验、不产 enum_violation，提取到的任意原文原样入库（含 5G-A 阶梯计费 3元/1GB 等非模板枚举的合法值）；必填字段仅当**真正为空**才进待补充；
- **必填字段说明覆盖兜底（V9.2）**：必填枚举字段（如套外计费标准 outChargeMode）若 LLM 把计费原文写入了同体系"计费说明型"字段（超套收费标准 chargeStandard）而非本字段，判为**已覆盖**、不进待补充（原文见说明字段）；
- 出参：`payload`（嵌套实例化报文）+ `_meta`（逐叶子 source 溯源）+ `pending_required`。

### 步骤⑤.5：嵌套本体校验闸（工具，确定性，V7.0+推理接入）
逐产品执行（**入参必须是 merge_nested 出参 payload 本体，与第⑥步同源**）：
```bash
python -X utf8 "scripts\cpcp_api.py" validate_nested --template "<templateId>" --payload-json-file "<merge 出参 payload 工件路径>" [--similar-offer-file "<相似品报文路径（可选，供冲突比对）>"]
```
- 转发 Java `POST /api/v1/product-ontology/config/validate-nested`（backend-app 端口 6174）；技术链路=normalizeNested 归一层 → ConfigMessageProjector.fromMessage 反投影为扁平 draft → TemplateDeriveEngine.derive 补全 → TemplateComplianceService.checkCompliance（含 SHACL delegate R-C06/R-C03/R-C05），**复用现有 CPCP 本体/platform，禁止新造本体或自拍 TTL**；
- 出参：`pass`（bool，门禁判定）、`violations[]`（item 含 ruleId/issueType/issueLevel=HIGH|WARN/field/message/engine）、`defaulted[]`、`rule_ids[]`（命中的校验规则）、`trace_id`（本次校验审计号）、`can_submit`（==pass）、`explain_hint`；**R-C04 自愈扩展（V9.2）**：当附加资费缺依赖主资费（dependOn/sourceOfferRef 均缺）且触发 R-C04 时，后端自动**复用相似产品（`similar_offer`）的依赖关系**——相似品明示依赖 → 补入 `dependOn/sourceOfferRef` 后重跑合规，出参附 `repaired`（field/value/source/source=similar_offer）；相似品**同样未声明依赖** → 保留 R-C04 违规并附 `instance_gaps[]`（提示用户补充依赖主资费或确认可独立订购）；
- **处置（四层架构铁律，结果逐字节引用不加工）**：
  - `pass=false`（存在 `issueLevel=HIGH` 或 `ruleId=R-C06` 违反）→ **按 E33 中断**，附违规项与建议，禁止强行产出执行方案；禁止 LLM 自行"改数据绕过"；
  - **R-C04 项（依赖缺失）已按自愈口径处理**：出参含 `repaired` 时该违规已因复用相似产品依赖而通过；出参含 `instance_gaps[]` 时按 E33 中断并**逐字引用 instance_gaps 建议**（引导用户回复补充依赖主资费或确认独立订购口径），禁止 LLM 自行补依赖或放行；
  - 仅 `level=WARN`/low 或校验通过 → **不阻断**，继续第⑥步；WARN 项随第⑧步输出附【风险提示】小节（非必填字段冲突，供用户知悉，不强制）；
  - **价格字段（档位/月费/月租/固定费）违反/待禁推仍走已有 pending_required 纪律**（第⑤步已强制留空，⑤.5 不另补价，禁止从相似品照搬或跨成员推导）；
- **推理依据（自动显示，V9.2）**：校验无阻断项（pass=true 或仅 WARN）时，**自动**用出参 `trace_id` 调 `explain_nested --trace-id <trace_id> [--audience sales]`（POST /config/explain，不含 `--field`）渲染【推理依据】小节随环节2 输出——**无需用户另行追问"为什么/依据"**；用户再追问"某字段来源/为什么这么判"时，可叠加 `--field <字段路径>`（GET /config/provenance/{field}，PROV-O 溯源）补充字段级依据；`--field` 仅作追问时的增强，不改变自动显示行为。**复用 Java 已有 explain/provenance，Python 侧重造 reason_trace**（禁止）；explain 取数失败按 E34 提示型处置（不中断主干，输出"推理依据暂不可用"，其余输出照常）；
- 一次性校验失败/后端未启动（HTTP 连接失败）→ **不阻断主干**，按 E34 提示"本体校验暂不可用，已跳过（配置仍可生成，建议后续补校验）"，继续第⑥步（防校验服务抖动卡死需求分析）。
- **R-C06 固费金额派生（实测口径，勿踩坑）**：后端 R-C06 的 `fixedFeeAmount` 由 `optionalInfo.printContent.prcMonthFee` 与 `optionalInfo.acctMonth.fixFee` **按数值派生**。**这两者必须为数字**（199 而非字符串 "199"），否则即使提取质量门禁通过（PASS），`validate_nested` 仍会以 R-C06 中断（E33）。故步骤④ 提取价格时须以**数值型**输出 `prcMonthFee`/`fixFee`（"199元/月"→ `prcMonthFee: 199`、`fixFee: 199`），且两处月费须一致；字符串价格值需先转数值再入 payload（merge_nested 对 type=number 叶子保留数值型）。
  - **V10.1 同源派生（同一业务参数不同表达，勿再向用户重复确认）**：`prcMonthFee`（免填单/宣传展示）与 `fixFee`（月租计费固定费）本质是**同一业务参数、仅表达形式不同**，不是两个待独立确认的字段。`merge_nested` 已在 merge 后**确定性双向回填**（`reconcile_same_parameter`）：一侧有值、另一侧空 → 用有值侧派生出空侧（source=同源派生(权威侧标签)）并移出 pending_required；两侧有值但不一致 → 以 source=原始需求/存量提取 侧为权威，弱侧对齐同一金额（不外呼重复确认，也不静默掩盖：同参数本就应同一金额）。因此**模型不得再以"套餐固定费待补充"为由让用户重复确认/填写**。

### 步骤⑥：分节表格渲染（工具，确定性）
逐产品执行（**入参必须是 merge_nested 出参的 payload 本体（嵌套报文），不是 merge 全出参**）：
```bash
python -X utf8 "scripts\cpcp_api.py" render_table --schema-file "scripts\templates\<templateId>.schema.json" --json-file "<merge 出参 payload 工件路径>" --meta-file "<merge 出参 _meta 工件路径>" [--similar-offer-file "<similar_offer 出参工件路径（含 similarOfferId/similarOfferName，可选）>"] --title "<套餐名称>"
```
- 渲染形态（V2.1，业务人员可读）：概览卡片置顶（资费名称/套餐月费/包含资源）+ "1. 基础信息 / 2. 发布信息 / 3. 免填单 / 4. 月租…" 独立小节（可选配置下组件与 发布信息 同级）+ 更深容器为小节内二级分组加粗子标题，**四列表格（字段名称|字段值|取值来源|备注），纯 x-label 中文，无技术键名、无层级标记列**；
- **取值来源列（V3.0+复用相似产品拼接）**：`--meta-file` 传入 merge_nested 出参 `_meta`（逐叶子溯源，path→{source}），render_table 确定性映射为业务标签——`原始需求`→**原始需求提取**、`AI补全`→**复用相似产品**、`本体推理`→**本体推理**、`默认值`→**默认值**；`本体推理`仅标注来源（validate_nested defaulted 补全的口径），**不回写 payload 值**；
- **复用相似产品拼接（V3.x）**：选用 `--similar-offer-file` 传入 similar_offer 出参（含 `similarOfferId`/`similarOfferName`）时，凡 `AI补全` 来源列渲染为 **`参考相似产品: {similarOfferId} {similarOfferName}`**（如 `参考相似产品: 12121212 5G-A轻享单品129元`），替代固定标签"复用相似产品"；未传相似品信息时回退固定标签"复用相似产品"；
- 仅渲染有值段；必填缺失进文末【待补充字段】（与 merge `pending_required` 同源）；
- 同输入输出逐字节稳定（幂等），渲染失败/输出为空 → E32 中断（先核对入参是否误传 merge 全出参）；
- **输出形态（消除疑虑）**：`render_table` 出参即**分节 Markdown 表格正文**（stdout，非 JSON），作为环节2 出口正文**直接展示**——**无需落盘为 JSON 再读、不存在"输出是 JSON 还是文本"的二义**；若后续需留档，仅作展示副本由模型原样拼接，不改造内容。

### 步骤⑦：flat24 派生 + 保存执行方案
**7a. 下游兼容派生（评审结论#1/#5）**——模板轨产物单向投影为 24 字段（环节2/3 后端仍按 24 字段校验）：
```bash
python -X utf8 "scripts\cpcp_api.py" derive_flat24 --template "<templateId>" --payload-json "<merge 出参 payload>"
```
- 映射表 = `references/ontology-fields.json`（path_to_field，确定性）；**`--mapping-file` 缺省即用该内置映射，通常不传**（脚本内置兜底，`derive_flat24` 无 `--mapping-file` 参数仅当确有自定义映射表时才需显式指定，一般场景省略即可）；模板路径无 flat24 对应的自动丢弃（如 roleMax）；方向仅 v2→flat，禁止反向；
- 派生 `fields`（field/value/source=模板轨派生）组装 flat plan_json（沿用旧轨 build_plan 口径，@deprecated 过渡保留）；
- **待补充判定（V7.0 口径）**：merge_nested 出参 `pending_required` 为空 → 可保存；非空 → **直接跳出口A**（不保存、不产出 req_id，从源头禁止进入智能配置）。判定唯一事实源 = merge_nested 出参（价格类字段待补充即整体出口A）。

**7b. 双份入库**（`pending_required` 为空时）：
```bash
python -X utf8 "scripts\cpcp_api.py" save_node_result --req-id "<req_id>" --node requirement --result-json "<plan_json_v2>"
```
- **`--node requirement` 为需求分析环节固定节点名（即环节2 的存储键），沿用 flow-A 既定口径，不得改用 stage2/demand_analysis/中文等其它命名**；
- `plan_json_v2` = `{req_id, template, payload, _meta, pending_required, flat_fields}`（嵌套报文 + 溯源 + flat24 派生，双份合计 ≤64KB，超限 5004 报错按 E23 处置）；
- **规范别名落盘（V10.2，环节2→环节3 无缝衔接）**：除按产品索引的 `plan_json_v2_<req_id>_<i>.json` 外，`run_requirement merge-save` 在首个已保存产品处**同时落盘规范名 `plan_json_<req_id>.json`**（内容同该产品 plan_json_v2）——这是 flow-B `run_pipeline`（环节3 起）读取配置原文的固定工件，消除 v2 按索引命名与环节3 期望载配置原文的错位；单商品即主配置原文，多商品取第一个（统一环节3 配置对象口径）；
- **req_id 唯一权威贯穿（禁止换号，V9.4）**：req_id 的唯一权威口径 = `PLAN + yyyyMMddHHmmss + 3 位随机`（脚本 `build_plan`/`cpcp_api` 内置生成，须满足后端 `PLAN\d{17}` 强校验）；req_id 自环节1 需求提报生成后**贯穿环节2~9 全程不变**——环节2 保存必须**沿用环节1 的同一 req_id**，**禁止因格式/其它原因在本环节另编新号、禁止把后缀非数字的号照搬进保存**（会触发 5002 或割裂各环节关联）；若环节1 遗留非合规号，应在环节1 源头以合规口径重新生成后再进入本环节；修改场景同键覆盖写；
- 存储失败 → E23 提示型异常（不中断，提示影响续跑回放）。

### 步骤⑧：输出（二选一）
**出口A（有待补充，未保存）：**
```
【环节2/9·需求分析】✅ 执行成功

已识别并生成《{{套餐名称}}》销售品需求单。系统根据历史相似产品自动补全缺失字段，形成完整《加载方案》如下：

{{render_table 分节多表，逐字引用}}

【待补充字段】{{merge_nested pending_required 逐项（x-label 中文）}}

【推理依据】{{explain_nested 出参 explanation/used_rules 逐字引用}}（可再回复"字段来源"查看某字段溯源）

> **建议处理：** 执行方案暂未保存、暂不能执行；请直接补充上述待补充字段值，我将更新执行方案并再次确认（可回复"补充字段：套餐固定费=199" 或直接说明补充值）
> 补齐并重新生成方案后，方可回复【确认配置】进入【销售品智能配置】。
```
> 说明：本出口模板不含环节1 标题头——**环节1 需求提报（需求提报文档+需求工单审批）已由 flow-A0 独立承担并输出**，需求分析（本程序）仅输出环节2 标题头。
**出口B（无待补充，已保存）：**
```
【环节2/9·需求分析】✅ 执行成功

已识别并生成《{{套餐名称}}》销售品需求单。系统根据历史相似产品自动补全缺失字段，形成完整《加载方案》如下：

{{render_table 分节多表，逐字引用}}

【推理依据】{{explain_nested 出参 explanation/used_rules 逐字引用}}（可再回复"字段来源"查看某字段溯源）

{{多产品时逐产品分节展示，每产品以"◆ {{name}}（{{prodType}}）"小节头分隔}}

《加载方案》已生成并保存（req_id：{{req_id}}），AI补全项已直接整合。

> **建议处理：** 《加载方案》已就绪，建议确认后进入配置（可回复【确认配置】启动【销售品智能配置】，或回复"修改意见"调整方案）
```
> 说明：需求提报（环节1）已在 flow-A0 输出；如在同一会话连续完成需求提报与需求分析，两标题头（环节1/环节2）由 flow-A0 与 flow-A 各自输出并以 `---` 分隔，保证演示一眼可辨两步均已实现。
- **枚举全放开（V9.2）**：枚举字段接受任意原文、不产 enum_violation，故出口不再附【枚举确认提示】（该小节随枚举放开移除）；是否类字段的归一建议仅在 `yes_norm_hints` 非空时并入出口提示展示（"建议归一为'是'"，不改写）；
- 如需调整方案：提示"请直接说明修改意见（仅价格、资源类字段须由您补充，其余字段已按相似产品补全）"；
- **输出纪律**：只输出 render_table 表格 + 出口文案；禁止额外生成文档文件/下载链接（除非用户明确要求导出文件）；禁止输出内部推理过程（提取细节、合并推导、_meta 全文、任务清单等）。
- **禁止用省略号截断分节多表（V10.1 纠偏）**：`stdout 紧凑化`仅指 `--stage merge-save` 脚本 stdout 面向**模型内部交接收底**；**交付给用户的环节2 出口正文必须= `requirement_result_<req_id>.json` 的 `tables` 字段全量内容**（含 1.基础信息/2.发布信息/3.免填单/4.月租/5.流量资源配置/6.语音资源配置/7.短信资源配置 全部有值小节），逐字完整展示——**禁止用"…"/"……"/省略号或"（略）"省略任何现有小节或行**。若因篇幅担心而想省略 → 违背"完整文案"纪律，应全部展示；模型不得自行删节 render_table 出参（E25 截断按异常矩阵处置）。

来源说明：取值来源列固定形态——【原始需求提取】/【复用相似产品】/【本体推理】/【默认值】（render_table 依 merge `_meta` 逐叶子确定性映射；`本体推理`仅标注不写值）；备注列保留条件提示/默认值标记（render_table 自动生成）。

## 多产品并存口径
- 步骤① 识别出 N 个产品（N≥2）时，步骤②~⑥/⑤.5 **逐产品独立执行**（各自检索相似品/取模板/提取/合并/校验/渲染），禁止跨产品混用模板或报文；
- 任一产品在任一步骤异常 → 整体中断按对应 E 码处置（不做部分输出）；
- 步骤⑦ 保存时 plan_json_v2 以产品数组组织：`{req_id, products: [{name, template, payload, _meta, pending_required}], flat_fields}`；
- 待补充判定逐产品独立，任一产品存在 pending_required → 整体出口A。

## 禁止事项
- 禁止生成或修改 req_id（以系统时钟生成结果为准，禁止臆造）；
- **禁止对"待补充"的价格字段做任何推理或从相似产品照搬**（normal 模式 merge_nested 已强制，模型不得绕过脚本手工补价）；
- 禁止 LLM 参与合并/校验/渲染/路由/本体推理判定（第②③⑤⑥⑦步与步骤⑤.5 一律工具执行，LLM 只在①④做翻译；⑤.5 校验结果逐字节引用，禁止改数据绕过违反项）；
- 禁止模型手工把 24 字段逆投影为嵌套报文（远端 offerTemplate 缺席时按 E1 降级口径走，禁止自造映射）；
- **禁止在 Python 侧重造 reason_trace/推理 trace**（⑤.5 可见性一律复用 Java 已实装的 `/config/explain` 与 `/config/provenance/{field}`，只传 trace_id 取数，禁止自行发明推理链路）；
- 禁止在提取输出中输出模板叶子清单外的路径（validate_elements 会剔除，重跑浪费）；
- 禁止未读 `extract-prompt-template.md` 提示词模板就直接提取（先读后提）；
- 出口A 场景禁止保存执行方案或产出 req_id；
- 禁止把修饰语混入资源字段值（"30GB（可结转）"→ value 只填"30GB"，结转规则入对应结转路径）；
- **禁止中途截断输出**：步骤①~⑦ 任一环节完成后必须继续执行至步骤⑧ 出口模板输出；步骤⑦ 保存成功后必须立即输出出口B 完整文案（含分节多表与 req_id），禁止仅输出内部推理/中间状态就结束回复（防"没看到结果"复发）；
- **禁止省略环节2 收尾固定块**：出口A/出口B 均须以"**建议处理：**"收尾块结束（SKILL.md 纪律5.1），**禁止以分节多表、【待补充字段】清单或空行收尾**；出口A 收尾块必须给出可执行动作（补齐字段→重新生成方案），不得只写"暂不能执行"式否定表述；出口B 收尾块触发词固定【确认配置】；
- Windows 中文路径环境：脚本调用一律使用绝对路径直调（`python -X utf8 "<绝对路径>\cpcp_api.py" ...`），禁止 `cd <中文路径> && python` 组合命令（GBK 控制台会乱码报错）；长报文一律走 `--xxx-json-file` 文件传参。
