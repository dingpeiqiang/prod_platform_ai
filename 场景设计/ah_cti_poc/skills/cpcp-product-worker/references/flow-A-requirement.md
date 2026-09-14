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
- 来源仅"原始需求/AI推理"两种（"本体推理"由引擎自动标注，禁止自行标注）。
- 可辅助核对：读取 `references/K4存量/K4存量_产品信息{similarOfferId}_V1.0.md` 单文件（禁止作为字段来源覆盖用户原始需求）。

产出：`fields_output`（18 项字段数组）。

### 步骤4：字段本体推理（引擎=单一事实源）
```bash
python scripts/cpcp_api.py ontology_reason --fields-json "<fields_output>"
```
- action=reason 一体推理：本体校验 + 非法值修正回写 + 缺失/待补充字段默认值补全（**仅套餐固定费维持"待补充"**）；
- 引擎补全/修正的字段 source 自动改标"本体推理"；
- **空返回防护（脚本内置）**：出参 fields_json 为空数组/空串时脚本报 `ONTOLOGY_EMPTY`（exit 2）——此时禁止跳过本步骤或用合并前字段直接组装方案，按异常处置：重试 1 次，仍为空则中断提示"字段本体推理引擎返回空结果，请检查后端 FieldOntologyService"；
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
来源说明：来源列可能为"原始需求 / AI推理 / 本体推理"三种。

## 禁止事项
- 禁止生成或修改 req_id（一律以 build_plan 输出为准）；
- 禁止对"待补充"的套餐固定费做任何推理或从相似产品照搬；
- 产品编码不补全：需求未提供时值填"由智能配置生成"（不计入 pending_fields）；
- 出口A 场景禁止保存执行方案或产出 req_id。
