# 本体推理接入 CPCP 模板轨——设计（R2·对接 backend-app Java 推理平台）

> 版本：R2（v1.0 草稿）
> 日期：2026-09-17
> 范围：只出设计，不改代码
> 决策前提（经评审确认）：
> 1. **不新造本体、不自拍 TTL**——`backend-app` Java 工程已实装完整的 CPCP 本体推理平台（TTL 本体 + OWLAPI/Openllet/RDF4J 推理机 + SWRL + SHACL + explain/provenance 可见性），本设计是**对接与接线**。
> 2. Java 推理能力优先接入 **flow-A 模板轨步骤⑤ merge_nested 之后 → 步骤⑥ 之前**新增确定性校验闸。
> 3. 推理过程可见性**复用 Java 已有 `config/explain` + `config/provenance/{field}`**（PROV-O），不在 Python 侧重造。

---

## 1. 背景与结论摘要

### 1.1 现状（实测核实，非假设）
- CPCP 技能（`cpcp-product-worker`）V7.0 已切换模板轨：产物是 **6 个逻辑模型模板 schema 驱动的嵌套报文**（`merge_nested` 输出 `payload`）。
- `backend-app` Java 工程**已具备** CPCP 产商品本体推理平台：
  - 本体：`src/main/resources/ontology/product-config.ttl`（v2.2，含 `ConfigScheme/PricingProduct/ChargePlan/PreferentialPlan/ResourceEntitlement/ComplianceRule` 等全套业务类与对象属性）。
  - 推理机：pom.xml 已声明 RDF4J（Sail+SHACL）、OWLAPI、Openllet。
  - 服务：`OpsSwrlReasoner`（SWRL）、`ShaclValidationDelegate`（SHACL）、`Rdf4jOntologyStore`、`SparqlConfigDiscoverer`、`TemplateDeriveEngine`、`TemplateComplianceService`。
  - 控制器：`ProductOntologyController`（`/api/v1/product-ontology`）已暴露 `config/infer`、`config/compliance`、`config/explain`、`config/trace`、`config/provenance/{field}`、`config/discover`、`ops/*`、`ops/hypothetical` 等端点。
  - **`FieldOntologyService` 就在此工程**——CPCP 旧轨 `ontology_reason` 现状的后端即它。**管道已通，缺的是把 v7.0 模板轨嵌套报文接进 Java 推理**。

### 1.2 设计结论
"本体推理放哪里最合适"的答案：**不在 LLM、不回旧 24 字段扁平，而是在 flow-A 模板轨确定性环节（⑤→⑥）新增一个调用 Java 推理平台的校验闸**，并复用其 explain/provenance 承载可见性。这样：
- 守住 SKILL.md 四层架构铁律（确定性逻辑脚本化，LLM 只在 ①④ 翻译）；
- 复用既有 TTL 本体 + Openllet/SWRL/SHACL，不重复造轮子；
- 补上模板轨目前缺失的**嵌套跨字段一致性 / 合规 / 归一**判定维度（`validate_elements` 只做④提取质量门禁，`merge_nested` 只做骨架对位）。

---

## 2. 落点：flow-A 步骤⑤.5 新增「嵌套本体校验闸」

### 2.1 位置
```
步骤④ 模板化提取 → validate_elements（质量闸）→ 步骤⑤ merge_nested（合并）→ 【步骤⑤.5 新增】
    → 步骤⑥ render_table → 步骤⑦ flat24 派生 + 保存
```

### 2.2 新增 REST 端点（Java 侧，复用既有服务）
建议在 `ProductOntologyController` 新增（或复用 `config/infer` + `config/compliance` 组合，二选一，见 §2.4）：

```
POST /api/v1/product-ontology/config/validate-nested
入参：{ template, payload, similar_offer }
  template     模板 id（personMainPrc/broadBandMainPrc/...）
  payload      merge_nested 出参 payload 本体（嵌套报文）
  similar_offer 相似品报文（可选，提供则参与同源/合规对照）
出参：{ success, violations[], defaulted[], rule_ids[], trace_id, explain_endpoint }
  violations[]: { path, label, severity, rule_id, desc, suggest }
  defaulted[]:  { path, label, from, value }   // from ∈ 相似品|schema_default
  rule_ids[]:   命中的本体规则清单（供 explain 追溯）
```

内部复用（Java 侧零新推理逻辑，仅编排）：
1. `ShaclValidationDelegate.validate(draft, graph)` → 结构/数据类型校验（SHACL）。
2. `TemplateDeriveEngine.derive(slots, draft, loadGraph())` → 缺口补全/默认推导。
3. `TemplateComplianceService.checkComplianceByTemplate(draft, graph)` → 模板合规 + 跨字段约束。
4. 融合组：对成员 payload 重复 1-3，再走组级互斥/依赖/共享判定（对应旧 `group_check`）。

### 2.3 Python 侧工具（`cpcp_api.py` 新增命令，复用 `_http`/`_unwrap`/`--xxx-file` 模式）
```
python -X utf8 "scripts\cpcp_api.py" validate_nested \
    --template <templateId> \
    --payload-json-file <merge 出参 payload 工件> \
    [--similar-offer-file <相似品报文>]
```
- 出参 `violations` 非空且含 `severity=high` → 按 **E33** 中断引导（异常矩阵新增）。
- `severity=warn` → 不阻断，随第⑥步输出放到【风险提示】小节。
- `trace_id` 落盘工件，供 `explain_nested` 引用。

### 2.4 可选：不新增端点，直接组合现有端点
为最小改动，也可不新增 Java 端点，CPCP 侧依序调：
1. `POST /config/infer`（slots+payload）→ 补全结果
2. `POST /config/compliance`（draft=payload）→ 合规 violations
3. `POST /config/explain`（trace_id）→ 可见性
优点：零 Java 改动；缺点：两次调用、trace 链割裂。**推荐新增单一 `validate-nested` 端点**（聚合 + 一次出 trace_id），性价比更高。

---

## 3. 推理过程可见性：复用 Java explain/provenance

### 3.1 设计原则（对齐四层架构 + SKILL.md 纪律）
- **用户可见层**：只给结论 + 关键依据 + 需人工确认项（延续纪律7"中间推理收敛"，不暴露碎碎念）。
- **可追溯层**：推理每一步落 `trace_id`，Java 侧 `config/trace` 逐字节回放（可回归，纪律1）。
- **规则透明层**：每条判定带 `rule_id`，Java `config/explain` 可按 audience（business/technical）解释，`config/provenance/{field}` 用 PROV-O `derivedFrom` 回答"这个字段默认值为什么是 X"。

### 3.2 接线
- `validate-nested` 出参 `trace_id` 落盘（随 `plan_json_v2` 工件，不入 render_table，纪律12）。
- 需要向用户/业务解释时，CPCP 侧新增命令（复用 `_http`）：
```
python -X utf8 "scripts\cpcp_api.py" explain_nested \
    --trace-id <trace_id> [--audience business|technical] [--field <路径>]
```
内部调 Java `POST /config/explain` + `GET /config/provenance/{field}`。出参**逐字引用不加工**（纪律1）。

### 3.3 与现有纪律的边界
| 纪律 | 影响 |
|---|---|
| 纪律7 中间推理收敛 | 顶层只给结论+依据；explain/provenance 按需取，不主动倾倒 |
| 纪律1 逐字引用不加工 | explain/provenance 出参逐字引用 |
| 纪律12 禁止手工渲染 | trace/explain 不入 render_table，只经脚本取数 |
| 价格禁推理 | 价格类字段不进 validate-nested 的"补全/推理"分支，仍走 `pending_required`（Java 侧 `isPrice` 语义对齐） |

---

## 4. 与现有工具/契约的关系（不引入重复职责）

| 现有 | 职责 | 关系 |
|---|---|---|
| `merge_nested` | ⑤ 骨架对位合并 + pending_required | 不变，作为 ⑤.5 入参 |
| `validate_elements` | ④ 提取质量门禁（vs schema 叶子） | 不变，前置闸（先于 merge） |
| **⑤.5 validate-nested（新）** | 嵌套跨字段/合规/归一判定（Java 推理） | 补 merge 未覆盖的一致性维度 |
| `render_table` | ⑥ 渲染 | 不变 |
| `derive_flat24` | ⑦ 下游投影 | 不变 |
| 旧 `ontology_reason` | flow-A 之外旧轨工具（@deprecated） | 过渡保留，新链路不再依赖；Java 侧 `FieldOntologyService` 与新闸并存 |

---

## 5. 需要配套修改的文档/契约

1. **SKILL.md**
   - `目录导航`：`ontology-fields` 行追加说明——模板轨推理由 backend-app Java 平台承接（`validate-nested`/`explain-nested`）。
   - `核心纪律/脚本调用约定`：新增 `validate_nested` / `explain_nested` 命令与错误码 E33/E34。
2. **references/flow-A-requirement.md**
   - `header`：V7.0 模板轨描述补"步骤⑤.5 嵌套本体校验闸"。
   - 新增「步骤⑤.5」小节（入参/出参/E33 中断口径）。
3. **references/tools-contract.md**
   - 新增 `validate_nested`、`explain_nested` 契约与 `POST /config/validate-nested` 协议。
4. **references/exception-matrix.md**
   - 新增 E33（嵌套校验 high 中断）、E34（explain/provenance 取数失败提示型）。
5. **backend-app**
   - （若选新增端点方案）`ProductOntologyController` + `ProductOntologyService` 增加 `validate-nested` 编排。
   - `product-config.ttl` 核对：模板轨 6 schema 的价格类叶子路径是否已标 `isPrice`（供禁推理对齐）。

---

## 6. 验证与回归

- Java 侧：`validate-nested` 对 6 模板各取 1 个 K5 存量报文做回归（`config/regression/run` 可参照）。
- CPCP 侧：`validate_nested` 对模板轨 `payload` 逐字节幂等（同输入同输出），`explain_nested` 出参逐字稳定。
- 断言：价格类字段路径永不进入"补全/推理"（对齐 `pending_required`），不引入违反 SKILL.md 纪律 11/12 的行为。

---

## 备注：本设计的演进脉络
- 早期曾从零提出"JSON 本体/自行设计 TTL"，经确认 Java 工程已实装成熟本体平台后**废弃造轮子路径**，改为"对接 + 新增校验闸 + 复用可见性"。这也印证评审的原则：**优先使用项目中已有最新技术栈（去旧留新）**。
