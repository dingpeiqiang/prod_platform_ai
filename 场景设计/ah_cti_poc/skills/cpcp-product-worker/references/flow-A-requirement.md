# 需求分析（执行方案生成）—— 程序 A

> 对应原子工作流 wf_sub_01（7 环节链路）。产物：《产销品加载执行方案》（四列表格 + plan_json）+ 存储键 req_id。

## 触发条件
- 用户首次提报销售品需求（口述或上传文档）；
- 用户对已生成执行方案提出修改意见（重新分析，覆盖写同 req_id）。

## 前置检查
- `requirement_text`（必填）：需求原文或文档内容摘要。缺失时追问："请提供销售品需求描述或上传需求文档。"

## 字段口径（四类 18 字段，全程一致）
- A.基础信息：产品名称/产品属性/产品编码/生效日期/退订规则
- B.资源配置：流量资源/语音资源/短信资源
- C.营销资源：套餐固定费/收费方式/优惠条件/优惠期
- D.销售规则：渠道类型/适用地区/订购限制/副卡规则/计费周期/销售品状态

枚举/默认值/同义映射见 `references/ontology-fields.md`；输出模板见文末。

## 执行程序（严格按顺序）

### 步骤1：需求要素提取（仅提取，不补全）
对 requirement_text 按 `references/ontology-fields.md` 同义映射表逐条比对提取：
- 仅原文可找到（含同义改写）的字段才填值，source="原始需求"；
- 未提及字段 value 填空字符串 ""（后续步骤补全），禁止臆造；
- 自检：原文出现"月费/月租/套餐费+金额"而套餐固定费为空 = 提取失败，必须回填。

产出：`elements_json`（18 项 fields 数组，每项 field/category/value/source）+ `need_summary`（中文要素摘要，≤5000 字符）。

### 步骤2：相似产品查询
```bash
python scripts/cpcp_api.py similar_offer --desc "<need_summary>"
```
- resultCode=="0" 且 similarOffer 非空（含 offerInfo） → 取 `similarOffer.offerInfo`（同构 fields 四类18字段数组）；
- **resultCode=="0" 但 similarOffer 为空对象/缺 offerInfo（未命中）**、或 resultCode=="1"、或脚本报错 → 走"无相似产品"分支：仅依据需求要素 + 引擎默认值补全（可读 K4 单文件辅助），**不中断**（E1）。

### 步骤3：同构键值合并
将 elements_json 与 offerInfo 按 field 名逐字段对齐：
- 需求有值 → 采用需求值，source="原始需求"；
- 无值 → 取 offerInfo 同名字段值，source="AI推理"；
- 皆缺失（含"无相似产品"分支全部无 offerInfo 的情形） → 留空（步骤4 引擎补全）；
- 来源仅"原始需求/AI推理"两种（"本体推理"由引擎自动标注，禁止自行标注）；
- **产品名称前置归一（强制）**：口语化名称（"5G套餐"等）不得直接作为合并值，先按 `ontology-fields.md` 产品名称合并规则归一为 K1 模板形态（如"5G套餐599元"→"5G-A套餐599元"），否则引擎判 violations 且无法自动修正；
- **销售品状态强制"待上线"**：新需求一律填"待上线"（source="原始需求"），禁止从相似产品 offerInfo 取"在售"（`ontology-fields.md` D 销售规则行）；
- 可辅助核对：读取 `references/K4存量/K4存量_产品信息{similarOfferId}_V1.0.md` 单文件（禁止作为字段来源覆盖用户原始需求）。

产出：`fields_output`（18 项字段数组）。

### 步骤4：字段本体推理（引擎=单一事实源）
```bash
python scripts/cpcp_api.py ontology_reason --fields-json "<fields_output>"
```
- action=reason 一体推理：本体校验 + 非法值修正回写 + 缺失/待补充字段默认值补全（**仅套餐固定费维持"待补充"**）；
- 引擎补全/修正的字段 source 自动改标"本体推理"；
- **空返回防护（脚本内置）**：出参 fields_json 为空数组/空串时脚本报 `ONTOLOGY_EMPTY`（exit 2）——此时禁止跳过本步骤或用合并前字段直接组装方案，按异常处置：重试 1 次，仍为空则中断提示"字段本体推理引擎返回空结果，请检查后端 FieldOntologyService"；
- **violations 处置**：出参 violations 非空时逐条核对——
  - 仅"产品名称"命名格式类 violation：不中断，但**步骤8 输出必须附 violations 原文**，提示用户确认是否按 K1 模板改名（用户确认后按"修改需求"重新走程序A）；
  - 其余字段 violation：按异常中断，引导修改需求；
- 产出：推理后 `fields_json`、`fixed`、`violations`。

### 步骤5：拆分方案字段
```bash
python scripts/cpcp_api.py build_plan --fields-json "<推理后 fields_json>"
```
脚本自动完成（模型不得自行生成 req_id/plan_md/pending_fields）：
- 入参防护：fields 为空数组时脚本报错拒绝组装（防"空方案"入库）；
- req_id = PLAN + yyyyMMddHHmmss + 3位随机（系统时钟，每次唯一）；
- plan_json = {req_id, fields, pending_fields}（反查 value=待补充）；
- plan_md = 四列表格（字段分类/字段名称/字段值/来源）。

### 步骤6：待补充判断
- pending_fields 为空 → 步骤7；
- 非空 → **直接跳出口A**（不保存、不产出 req_id，从源头禁止进入智能配置）。判定唯一事实源=引擎反查结果。

### 步骤7：保存执行方案
```bash
python scripts/cpcp_api.py save_node_result --req-id "<req_id>" --node requirement --result-json "<plan_json>"
```
修改场景同键覆盖写。

### 步骤8：输出（二选一）
**出口A（有待补充，未保存）：**
```
{{plan_md}}

【待补充字段】{{pending_fields 逐项列出}}

执行方案暂未保存、暂不能执行（回复【确认执行】无效）：
- 请直接补充价格/资源类字段值，将更新执行方案并再次确认。
```
**出口B（无待补充，已保存）：**
```
{{plan_md}}

【待补充字段】无，所有字段均已明确

《产销品加载执行方案》已生成并保存（req_id：{{req_id}}），请核对：
- 回复【确认执行】：将自动串行执行 智能配置→稽核→资费校准→自动测试 四个环节
  （每环节执行后向您打印结果，仅异常时中断）；
- 如需调整：请直接说明修改意见（仅价格、资源类字段须由您补充，其余字段已按相似产品补全）。
```
- **步骤4 存在"产品名称"violation 时**：出口B 文案末尾追加【命名规范提示】小节，逐字引用 violations 原文，并给出 K1 模板建议名（如"5G-A套餐单品599元"），说明"回复修改意见可调整命名"；
- 禁止为"展示好看"省略 violations 或自行改写表格中的产品名称；
- **输出纪律**：只输出 plan_md 表格 + 出口文案；禁止额外生成文档文件/下载链接（除非用户明确要求导出文件）；禁止输出内部推理过程（提取细节、合并推导、任务清单等）。

来源说明：来源列可能为"原始需求 / AI推理 / 本体推理"三种。

## 禁止事项
- 禁止生成或修改 req_id（一律以 build_plan 输出为准）；
- 禁止对"待补充"的套餐固定费做任何推理或从相似产品照搬；
- 产品编码不补全：需求未提供时值填"由智能配置生成"（不计入 pending_fields）；
- 出口A 场景禁止保存执行方案或产出 req_id；
- 禁止把口语化产品名称（"5G套餐"等）未经归一直接合并进 fields（见步骤3 前置归一）；
- 禁止把相似产品的"在售"状态带入新需求方案（一律"待上线"）；
- 禁止把修饰语混入资源字段值（"160GB（可结转）"→ value 只填"160GB"）；
- 禁止未读 ontology-fields.md 同义词表就直接提取（先读后提）。
