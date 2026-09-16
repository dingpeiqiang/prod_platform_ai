# 需求分析（执行方案生成）—— 程序 A

> 对应原子工作流 wf_sub_01（7 环节链路）。产物：《销售品加载方案》（五列/六列模块表格 + plan_json）+ 存储键 req_id。
> V4.0 融合组扩展：融合套餐 = 1 个主商品 + N 个成员商品（宽带/天翼高清/副卡功能费/权益包），一次提报、逐成员分析；成员构成以 `similar_offer` 出参 `offer_group` 为唯一数据源（模型禁止自行推理成员关系——SKILL.md 纪律9）；单商品链路行为零变化。

## 触发条件
- 用户首次提报销售品需求（口述或上传文档）；
- 用户对已生成执行方案提出修改意见（重新分析，覆盖写同 req_id）。

## 前置检查
- `requirement_text`（必填）：需求原文或文档内容摘要。缺失时追问："请提供销售品需求描述或上传需求文档。"

## 字段口径（3 模块 / 9 分类 / 24 字段，全程一致）
- 基础信息：产品属性（套餐名称/套餐编码/套餐档位/套餐属性/计费周期）、生命周期（套餐有效期/到期处理方式）、销售属性（适用用户/销售渠道）
- 资源配置：套餐内基础资源（国内通用流量/本地语音/短信）、套餐内权益配置（是否允许办理副卡）、套外资费标准（套外流量-计费标准/套外语音-国内通话/套外短彩信-短/彩信）
- 业务规则：订购与生效（新入网生效方式/老用户生效方式/过渡期资费规则）、变更/退订/拆机（套餐变更范围/变更生效方式/退订规则）、计费/支付/风控（付费方式/支付方式/流量结转规则/断网授权）

枚举/默认值/同义映射见 `references/ontology-fields.md`；输出模板见文末。

## 执行程序（严格按顺序）

### 步骤1：需求要素提取（仅提取，不补全）
对 requirement_text 按 `references/ontology-fields.md` 同义映射表逐条比对提取：
- 仅原文可找到（含同义改写）的字段才填值，source="原始需求"；
- 未提及字段 value 填空字符串 ""（后续步骤补全），禁止臆造；
- 自检：原文出现"套餐固定费/月费/月租+金额"而套餐档位为空 = 提取失败，必须回填；
- 套餐名称按用户命名原样保留（"校园青春卡"等自命名不强制归一 K1 模板）；
- **融合判定与商品拆分（V4.0，并入步骤1 首动作）**：需求原文识别成员商品表述（宽带/天翼高清/副卡/权益包等成员关键词，同义词口径见 ontology-fields.md 成员角色枚举）：
  - 仅主套餐资费，无任何成员表述 → offer_type=单品（后续步骤走单商品链路，行为与 V2.7 完全一致）；
  - 含成员表述 → offer_type=融合，**逐成员独立提取**（各成员自己的字段/价格），成员 fields 复用同一 24 字段注册表；需求未提尽的成员属性**不在此步补全**（等步骤2 offer_group 下发，source=AI补全）；
  - 拆分结果内嵌于 elements 组结构：`{offer_type, main_offer: {role: "主卡套餐", fields}, member_offers: [{role, fields}], group_rules: {}}`（group_rules 留空，等步骤2 下发回填）。

产出：`elements_json`（单商品=24 项 fields 数组；融合=上述组结构，每项 field/category/value/source）+ `need_summary`（中文要素摘要，≤5000 字符）。

### 步骤2：相似产品查询
```bash
python scripts/cpcp_api.py similar_offer --desc "<need_summary>"
```
- resultCode=="0" 且 similarOffer 非空（含 offerInfo） → 取 `similarOffer.offerInfo`（同构 fields 24 字段数组）；
- **V4.0 组下发**：出参含 `offer_group`（命中融合品）时，成员构成/角色/required/group_rules **逐字引用该出参**（数据源=seed_offer_groups.json）：
  - 与需求拆分结果对位：需求提及且组定义含有的成员 → 进入 member_offers；
  - 需求提及但组定义不含的成员 → 组级 violation（步骤4 组级校验输出，按 violation 流程引导修改需求，不走 E 中断）；
  - 组定义 required=false 的可选成员需求未提及时 → 不进组结构、不补全；
  - **模型禁止对 offer_group 增删成员或改写 group_rules（纪律9）**；
- 未命中融合组但需求为融合 → 按 E1 中断询问（口径不变）；
- **其余任何情形（相似服务失败/未命中/脚本报错）一律按 E1 中断询问用户**： resultCode=="0" 但 similarOffer 为空对象/缺 offerInfo（未命中）、或 resultCode=="1"、或 resultCode 为其他任意值（如 PARAM_MISSING/HTTP_xxx/NET_ERROR/TIMEOUT，须原样引用 resultMsg 供日志定位，禁止臆造原因）、或脚本报错 → **终止流程并询问**："相似产品服务暂不可用/未命中，回复【继续】跳过相似产品仅用知识库 K4 补全，或回复【修改需求】调整后重提"；禁止自动降级继续。

### 步骤3：同构键值合并
将 elements_json 与 offerInfo 按 field 名逐字段对齐：
- 需求有值 → 采用需求值，source="原始需求"；
- 无值 → 取 offerInfo 同名字段值，source="AI推理"（引擎归一后显示为"AI补全"）；
- 皆缺失（含"无相似产品"分支全部无 offerInfo 的情形） → 留空（步骤4 引擎补全）；
- 来源仅"原始需求/AI推理"两种（"AI补全"由引擎/脚本自动归一标注，禁止自行标注"AI补全"）；
- **价格字段（套餐档位）禁止从相似产品照搬**：需求未提供时留空交引擎兜底（引擎维持"待补充"）；
- 可辅助核对：读取 `references/K4存量/K4存量_产品信息{similarOfferId}_V1.0.md` 单文件（禁止作为字段来源覆盖用户原始需求）；
- **V4.0 融合组：合并逐成员执行**——主套餐与各成员各自对位 offerInfo/offer_group（成员 preset 值取自 offer_group.members[].preset，source=AI补全）；**价格字段禁止跨成员照搬**（主套餐档位 ≠ 宽带月功能费 ≠ 副卡月功能费，各自独立判定）。

产出：`fields_output`（单商品=24 项字段数组；融合=组结构，成员 fields 为推理前数组）。

### 步骤4：字段本体推理（引擎=单一事实源）
```bash
python scripts/cpcp_api.py ontology_reason --fields-json "<fields_output>"
```
- action=reason 一体推理：本体校验 + 非法值修正回写 + 缺失/待补充字段默认值补全（**仅套餐档位等价格字段维持"待补充"**）；
- 引擎补全/修正的字段 source 自动改标"AI补全"（脚本 build_plan 输出时统一归一为【AI补全】）；
- **空返回防护（脚本内置）**：出参 fields_json 为空数组/空串时脚本报 `ONTOLOGY_EMPTY`（exit 2）——此时禁止跳过本步骤或用合并前字段直接组装方案，按 E2b 异常处置：**首次空返回即中断询问**，提示"字段本体推理引擎返回空结果，请检查后端 FieldOntologyService；排查后回复【重新生成】"（禁止自动重试）；
- **violations 处置**：出参 violations 非空时逐条核对——
  - 仅"套餐名称"命名格式类 violation：不中断，但**步骤8 输出必须附 violations 原文**，提示用户确认命名（用户确认后按"修改需求"重新走程序A）；
  - 其余字段 violation：按异常中断，引导修改需求；
- **V4.0 融合组两轮推理**：入参为组结构时脚本自动逐成员推理 + 组级校验（action=group_check），出参 `group.group_violations[]`（item/level/desc/suggest）：
  - 处置规则与 violations 相同：仅名称类不中断（步骤8 附原文），其余中断引导修改需求；
  - 成员越界（需求成员 ∉ 组定义）也在 group_violations 中输出；
- 产出：推理后 `fields_json`（单商品）/组结构推理结果（融合，main_offer+member_offers 各自 fields_json）、`fixed`、`violations`、`group_violations`。

### 步骤5：拆分方案字段
```bash
python scripts/cpcp_api.py build_plan --fields-json "<推理后 fields_json 或组结构>"
```
脚本自动完成（模型不得自行生成 req_id/plan_md/pending_fields）：
- 入参防护：fields 为空数组时脚本报错拒绝组装（防"空方案"入库）；
- 来源标注两态归一：原始需求/AI推理/本体推理 → 【原始需求】/【AI补全】；
- req_id = PLAN + yyyyMMddHHmmss + 3位随机（系统时钟，每次唯一）；
- 单商品：plan_json = {req_id, fields, pending_fields}（反查 value=待补充）；plan_md = 五列模块表格；
- **V4.0 融合组**：组结构入参 → plan_json = {req_id, offer_type, main_offer, member_offers, group_rules, pending_fields}（待补充逐成员定位携带 role）；plan_md = **六列表格**（商品/模块/分类/字段名称/字段值/备注，主商品行加粗）。

### 步骤6：待补充判断
- pending_fields 为空 → 步骤7；
- 非空 → **直接跳出口A**（不保存、不产出 req_id，从源头禁止进入智能配置）。判定唯一事实源=引擎反查结果；
- **V4.0 组口径：待补充判定逐成员独立**——任一成员（含主商品）存在"待补充"价格字段 → 整体出口A。

### 步骤7：保存执行方案
```bash
python scripts/cpcp_api.py save_node_result --req-id "<req_id>" --node requirement --result-json "<plan_json>"
```
修改场景同键覆盖写。

### 步骤8：输出（二选一）
**出口A（有待补充，未保存）：**
```
已识别并生成《{{套餐名称}}》销售品需求单。系统根据历史相似产品自动补全缺失字段，形成完整《加载方案》如下：

{{plan_md（五列/六列模块表格，逐字引用）}}

【待补充字段】{{pending_fields 逐项列出；融合组按成员定位："宽带-宽带月功能费"}}

执行方案暂未保存、暂不能执行（回复"确认配置"无效）：
- 请直接补充价格/资源类字段值，将更新执行方案并再次确认。
```
**出口B（无待补充，已保存）：**
```
已识别并生成《{{套餐名称}}》销售品需求单。系统根据历史相似产品自动补全缺失字段，形成完整《加载方案》如下：

{{plan_md（五列/六列模块表格，逐字引用）}}

融合成员构成：{{主卡套餐（主商品）+ 成员角色清单，逐字引用 offer_group 出参，禁止省略成员行；单商品本行整体省略}}

《加载方案》已生成并保存（req_id：{{req_id}}），AI补全项已直接整合。**建议处理：可输入"确认配置"进入【销售品智能配置】。**
```
- 如需调整方案：提示"请直接说明修改意见（仅价格、资源类字段须由您补充，其余字段已按相似产品补全）"；
- **步骤4 存在"套餐名称"violation 时**：出口B 文案末尾追加【命名规范提示】小节，逐字引用 violations 原文，说明"回复修改意见可调整命名"；
- **步骤4 group_violations 非空时**：输出附【组规则提示】小节，逐字引用 group_violations 原文（成员越界/组级冲突）；
- 禁止为"展示好看"省略 violations 或自行改写表格中的套餐名称；
- **输出纪律**：只输出 plan_md 表格 + 出口文案；禁止额外生成文档文件/下载链接（除非用户明确要求导出文件）；禁止输出内部推理过程（提取细节、合并推导、任务清单等）。

来源说明：备注列固定两态——【原始需求】/【AI补全】。

## 禁止事项
- 禁止生成或修改 req_id（一律以 build_plan 输出为准）；
- 禁止对"待补充"的套餐档位（价格）做任何推理或从相似产品照搬；**融合组禁止跨成员照搬价格**；
- **禁止自行推理/增删融合成员或改写组规则（纪律9）：成员构成唯一数据源=offer_group 出参**；
- 套餐编码不补全：需求未提供时值填"系统待生成"（不计入 pending_fields）；
- 出口A 场景禁止保存执行方案或产出 req_id；
- 禁止把修饰语混入资源字段值（"30GB（可结转）"→ value 只填"30GB"，结转规则入"流量结转规则"字段）；
- 禁止未读 ontology-fields.md 同义词表就直接提取（先读后提）；
- **禁止中途截断输出**：步骤1~7 任一环节完成后必须继续执行至步骤8 出口模板输出；步骤7 保存成功后必须立即输出出口B 完整文案（含 plan_md 表格与 req_id），禁止仅输出内部推理/中间状态就结束回复（防"没看到结果"复发）；
- Windows 中文路径环境：脚本调用一律使用绝对路径直调（`python -X utf8 "<绝对路径>\cpcp_api.py" ...`），禁止 `cd <中文路径> && python` 组合命令（GBK 控制台会乱码报错）。
