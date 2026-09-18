# 产销品加载 AI 应用 · 融合商品（多成员）加载落地实现方案
> 场景：安徽电信 CPCP 产销品域 · 数字员工 · 融合套餐 = 1 个主商品 + N 个成员商品
> 版本：V2.0　日期：2026-09-16（最新口径对齐 V2.0 工作流重塑）
> 依据：《产销品加载AI应用-细化设计方案.md》V2.7、《产销品加载AI应用开发方案.md》V3.0、K4 存量融合品文档（900102306/900102307/900102313/900113046）、K2 叠加优惠约束说明 V1.0
> 实现原则：**分组式 plan_json 最小侵入**——成员商品复用现有 24 字段注册表，单商品链路行为零变化（向后兼容）；接口路径/入出参契约零改动，仅出参内嵌 `offer_group` 扩展结构；模型只引用数据源、禁止自行推理成员关系（E26 同款纪律）。
>
> **V2.0 履历（工作流重塑）**：本方案功能已倒灌进 V2.0 工作流——
> - 智能配置融合成员回显 → `wf_sub_02` `CODE_FUSION_GROUP_ECHO`（环节1 offer_id 回显融合成员清单）；
> - 规格稽核新组维度 → `wf_sub_03`（error_list `group` 类目）；
> - 资费校准按成员分组 → `wf_sub_05`（compare_list `member_role` 键）；
> - 自动测试融合组场景 S_GROUP_BIND/S_ADDON_SUB + E26 组核对 → `wf_sub_04` `CODE_MAP_FIXED_CASES`（31 条固定用例口径正确）。
> - 确定性逻辑由 `skills/cpcp-product-worker` 技能包脚本内嵌为 `type=6` 代码节点（`CODE_*`）；知识库迁至 `knowledge/`（原 `references/`、`skills/` 废弃）。文档沿用 K4/K2/K3 数据源口径与 E26/E27、31 条固定用例、`seed_offer_groups` 既定设定。

---

## 0. 成员关系数据源（先定口径）

| 层级 | 来源 | 说明 |
| --- | --- | --- |
| 生产正源 | 产品域商品目录 + 融合品关系表（CRM 产品中心） | 成员商品主数据 + 主商品→成员清单/角色/必选性/依赖编码 |
| POC 数据源 | **K4 融合品文档结构化抽取** → `seed_offer_groups.json` | 4 个融合品（900102306/900102307/900102313/900113046），成员构成/依赖编码取自 K4 原文（如 900113046"宽带（省内自行配置）：1000M起；天翼高清：1路"、副卡功能费 7320110001600005 可选依赖） |
| 运行时下发 | `similar_offer` 出参内嵌 `offer_group` | 成员构成跟种子数据走，模型不自行推断 |
| 用户显式指定 | 需求原文中的成员表述 | 与组定义求交集校验，越界成员在组级稽核拦截 |

---

## 1. 总体结构（改动总览 · V2.0 工作流）

```
场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/     # 12 个工作流 JSON（gen_workflows_v2.py 生成）
  ├── wf_main_intent.json               # 总流程编排
  ├── wf_sub_00 ~ wf_sub_10.json        # 各子流程
  │   ├── wf_sub_02 CODE_FUSION_GROUP_ECHO   # 智能配置融合成员回显（环节1 offer_id 回显成员清单）
  │   ├── wf_sub_03                       # 规格稽核组维度（error_list group 类目）
  │   ├── wf_sub_04 CODE_MAP_FIXED_CASES  # 自动测试融合组场景 + E26 组核对（节点315，31 条固定用例）
  │   └── wf_sub_05                       # 资费校准成员分组（compare_list member_role 键）
  └── 确定性逻辑内嵌为 type=6 代码节点（CODE_*）：融合组种子/组级稽核/组场景编码等逻辑代码化

knowledge/                                # 知识库（原 references/、skills/ 废弃）
  ├── ontology-fields.json                # 组件结构注册表（成员角色枚举 + group_rules，迁自 ontology-fields.md）
  └── seed_offer_groups.json              # 融合组种子（4 组）

后端适配端点
  ├── POST /api/v1/appstore/ops/root-cause
  ├── POST /api/v1/appstore/ops/work-orders
  ├── POST /api/v1/appstore/shelf-compliance
  ├── POST /api/v1/appstore/validate-nested
  ├── POST /api/v1/appstore/explain
  ├── POST /api/v1/appstore/report/download
  └── POST /api/v1/appstore/script/download
  （网关 BASE_URL=http://10.86.13.201:31281，AppStoreV16Controller 提供）
```

**兼容性铁律**：入参不传组结构 = 单商品模式，行为与 V2.7 完全一致；所有新增字段均为"可缺席"（`offer_group`/`members[]`/`member_role` 缺失时按单商品回退），后端与脚本层零破坏升级。

---

## 2. 数据层：融合组种子 `seed_offer_groups.json`

从 K4 文档结构化抽取（抽取时逐字保留 K4 原文口径，禁止改写资费值）：

```json
{
  "groups": [
    {
      "group_id": "GP900113046",
      "main_offer_id": "900113046",
      "main_offer_name": "5G-A融合套餐199元",
      "members": [
        {"role": "主卡套餐",   "offer_id": "900113046",       "required": true,  "fields_ref": "K4存量_产品信息900113046"},
        {"role": "宽带",       "offer_id": "省内自定",         "required": true,  "preset": {"宽带速率": "1000M起", "计费周期": "自然月"}},
        {"role": "天翼高清",   "offer_id": "省内自定",         "required": true,  "preset": {"路数": "1路"}},
        {"role": "副卡功能费", "offer_id": "7320110001600005", "required": false, "dependency": "OPTIONAL_DEPEND", "preset": {"月功能费": "10元", "张数": "省内自定"}}
      ],
      "group_rules": {
        "共享规则": "副卡共享主卡语音/流量/网速/卫星权益；应用权益不共享，副卡可单独办理",
        "互斥": [],
        "依赖": [{"member": "副卡功能费", "type": "OPTIONAL_DEPEND", "target": "主卡套餐"}],
        "退订联动": "主套餐退订→套餐中涉及权益功能同步退订（K4 900113046 退订章节原文）"
      }
    }
    // 900102306 / 900102307 / 900102313 同构
  ]
}
```

- 成员角色枚举（V4.0 新增，封闭集合）：`主卡套餐 / 宽带 / 天翼高清 / 副卡功能费 / 权益包 / 其他`；
- `required=false` 的成员为可选成员（如副卡功能费），需求未提及时不补全、不进 pending_fields；
- 需求提及但组定义不含的成员 → 组级稽核 violation（不走 E 中断，按 violation 流程引导修改需求）。

---

## 3. 本体层：`knowledge/ontology-fields.json`，组件结构注册表（原 `references/ontology-fields.md`，V4.0 组结构注册表已迁 knowledge/）

### 3.1 plan_json 组结构（V4.0）

```json
{
  "req_id": "PLAN20260916100000000",
  "offer_type": "融合",
  "main_offer": {
    "role": "主卡套餐",
    "fields": [ /* 原 24 字段数组，套餐属性=主资费 */ ]
  },
  "member_offers": [
    {"role": "宽带",     "fields": [ /* 24 字段子集（按成员适用性） */ ]},
    {"role": "天翼高清", "fields": [ /* ... */ ]},
    {"role": "副卡功能费", "fields": [ /* ... */ ]}
  ],
  "group_rules": { "共享规则": "...", "互斥": [], "依赖": [], "退订联动": "..." },
  "pending_fields": [ {"role": "宽带", "field": "宽带月功能费"} ]
}
```

- 成员 fields 复用同一 24 字段注册表（category/枚举/默认值/同义词映射全部沿用），未涉及字段填 ""（引擎兜底）；
- `offer_type ∈ {单品, 融合}`：需求含多成员（宽带/高清/副卡/权益包任一非主资费成员）→ 融合；否则单品（结构退化为现 V3.0 扁平 plan_json，字段布局不变）。

### 3.2 取值链（逐成员独立）

```
原始需求（成员级提取） → 相似融合品 offer_group（组级下发，成员逐一对位）
  → 本体默认值（引擎逐成员补全） → 待补充（仅各成员价格类字段）
```

- **待补充判定按成员独立**：任一成员价格字段待补充 → 整体出口A；`pending_fields` 每项携带 `role` 定位；
- 主套餐档位与各成员月功能费互相独立，禁止跨成员照搬价格（价格禁止推理纪律延伸到组级）。

### 3.3 引擎两轮推理

1. **成员内推理**（复用现有 action=reason）：逐成员跑 24 字段校验/修正/补全；
2. **组级校验**（新增 action=group_check）：互斥/依赖/退订联动对照 `group_rules` + K2 约束表，输出 `group_violations[]`（格式与 violations 一致：item/level/desc/suggest）。

---

## 4. 工作流层：确定性逻辑落为 `type=6` 代码节点（原技能脚本 `cpcp_api.py` V3.1 / `validate_output.py` V1.1 已代码化）

> 原技能包脚本（`build_plan` 组结构识别、`spec_audit` 组类目、`billing_verify` 成员分组、`map_fixed_cases` 组结论透出、`validate_output` 成员行/E26 组核对）均已内嵌为工作流 `type=6` 代码节点（`CODE_*`）。

### 4.1 融合成员回显代码节点（`CODE_FUSION_GROUP_ECHO`，wf_sub_02）

```
入参 offer_group / plan_json 组结构：
├─ 扁平 fields（单商品）           → 单商品：行为与 V2.7 一致
└─ 组结构 {offer_type, main_offer, member_offers}
     → plan_md 增"商品"列（六列：商品/模块/分类/字段名称/字段值/备注）
     → 主商品行 role="主卡套餐" 加粗展示；成员分组展示，成员名独立成块
     → pending_fields 由 {role, field} 组成
     → req_id 生成规则不变（PLAN+14时间戳+3随机）
```

### 4.2 各子流程代码节点改动

| 子流程/节点 | 改动 |
| --- | --- |
| wf_sub_02（融合成员回显） | 识别组结构入参 → 逐成员推理 + 组级校验；出参新增 `group_violations[]`；`remark_excluded` 剔除逻辑按成员内生效 |
| wf_sub_03（规格稽核） | `config_json` 组结构原文透传（不变）；出参 `error_list[]` 新增 `group` 类目（item=`group:<role>`） |
| wf_sub_05（资费校准） | 出参 `compare_list[]` 每项新增 `member_role` 键（单商品时缺省="主卡套餐"） |
| wf_sub_04 `CODE_MAP_FIXED_CASES` | `testScenes[]` 组场景照列（见 §6）；`offer_group_check`（组一致性结果）出参透出；后端 `testCases[]` 优先透出不变；组维度结论由后端生成，节点不自行聚合（纪律不变） |

### 4.3 输出校验（`validate_output` 逻辑代码化）

- 组场景名（套餐新装/副卡加装/套餐退订/**成员加装/成员退订**等出参实际返回）逐行核对；
- 按 `member_role` 分组核对行存在性与空值省略；E27 阈值改为**逐成员内**计算（防多成员稀释误判）；
- E26 组核对扩展：核对"主 offerName + 成员角色清单"与 plan_json 组结构一致；
- 融合 plan_md 六列表头 `| 商品 | 模块 |` 纳入校验白名单。

### 4.4 回归验证

- 融合组场景断言（组结构入参 → 六列表格 + pending_fields 携带 role + 主成员加粗）；
- 单商品回归：扁平入参 → 输出与 V2.7 逐字段一致（防回归）。

---

## 5. 流程层：融合口径接入 V2.0 工作流（原 flow-A/B/C/D 文档故有改动点）

### 5.1 需求分析轨（原程序A，`wf_sub_01` 模板轨）

| 环节 | 改动 |
| --- | --- |
| 商品拆分（产品识别） | 需求原文识别成员商品（宽带/高清/副卡/权益包关键词同义词表）；未明确成员 → 按相似融合品 offer_group 补全（source=AI补全）；拆分结果内嵌于 elements 组结构 |
| 相似查询 | `similar_offer` 出参含 `offer_group` → 直接作为成员补全与组规则来源（禁止模型重推）；未命中融合组 → 按 E1 中断询问（口径不变） |
| 合并（merge 节点） | 同构合并逐**成员**执行（主+N 成员各自对齐 offerInfo/offer_group），价格字段禁止跨成员照搬 |
| validate_nested 本体闸 | 两轮推理：逐成员 reason → 组级校验；`group_violations` 非空时与 violations 同流程处置（仅名称类不中断，其余中断引导） |
| 待补充判定 | pending_fields 判定按成员独立；任一成员有待补充 → 出口A（不保存不产 req_id） |
| 出口 | 出口A/B 文案增加"融合成员构成"行（主+N 成员名清单）；出口B 表格为六列 |

### 5.2 执行轨（原程序B，`wf_sub_02` 融合成员回显 + `wf_sub_03` 规格稽核 + `wf_sub_05` 资费校准）

| 环节 | 改动 |
| --- | --- |
| 智能配置（wf_sub_02 CODE_FUSION_GROUP_ECHO） | 出参 `group`（主 offer_id + members[]）随 offer_id 显性回显纪律同步展示：模板新增"融合成员：宽带（offer_id xxx）/天翼高清（xxx）/副卡功能费（xxx）"行，逐字引用出参，禁止省略成员行；**组内任一成员 PARTIAL → 整体按 PARTIAL 口径处置（失败分类明细含成员定位）** |
| 规格稽核（wf_sub_03） | 稽核对象行增加"组维度：主 offer_id + N 成员"；error_list 含 group 类目 → 对应成员行标 ❌ 并附明细；七项检查项结构不变 |
| 资费校准（wf_sub_05） | 比对表按成员分组输出（组间空行分隔或独立小表），行数=Σ各成员有值行；空值行省略规则与 E27 判定**逐成员内**计算；8 项比对项目名不变 |
| 自动测试（wf_sub_04 CODE_MAP_FIXED_CASES） | 场景表输出组场景（出参实际返回为准）；受理验证新增"成员组合验证"（数据源=出参 offer_group_check，逐字引用）；E26 预校验扩展为组核对（主 offerName 一致 + 出参成员角色与 plan_json member_offers 角色集合一致；不一致 → E26 中断于 wf_sub_04 节点 315，铁律不变：禁止输出任何通过性明细） |
| 汇总块 | 关键数据列增加"融合成员 N 个全部成功/部分失败"；其余结构不变 |

### 5.3 审批/监控轨

- 审批轨（原 flow-C）：看板"自动测试（三大验证）"行结论引用含成员组合验证结果；报告归档口径不变（正式版 9 章节模板增加成员构成基础信息项）；
- 监控轨（原 flow-D)：D-2 监控 `product_id` 可传主 offer_id（组维度指标）或成员 offer_id（成员维度），前置检查不强制区分（出参为准）；D-3 方案模板不变。

---

## 6. 自动测试规范：融合组场景（原 K3 增补，V2.1 增补版 → 落 wf_sub_04 `CODE_MAP_FIXED_CASES`）

### 6.1 组场景编码（新增 2 个，与现有 3 个并存）

| 场景编码 | 场景名称 | 必选性 |
| --- | --- | --- |
| S_GROUP_BIND | 融合成员绑定 | 融合品必选（有 required 成员时） |
| S_ADDON_SUB | 可选成员加装/退订 | 有 required=false 成员时必选 |

- S_O_TC / S_U_TC / S_ADD_CARD 口径不变（主卡号码维度）；
- 新测点：`P_SHARE`（共享权益校验）/ `P_GROUP_MUTEX`（组互斥）/ `P_MEMBER_STATUS`（成员实例状态），预期值取自 seed_offer_groups preset。

### 6.2 固定用例映射（31 条不变，组级结论由后端出参）

- 31 条固定用例清单**不增删**（判定依据出参映射表在组场景下自然覆盖：如 ACC-004 引用 S_O_TC 场景不变）；31 条口径由 `wf_sub_04 CODE_MAP_FIXED_CASES` 承载并核对正确；
- 组维度通过性：后端按组场景结果生成 `overallConclusion`，代码节点/模型只透出，禁止聚合改判；
- 受理凭证核验扩展：主订单 orderId/offerInstId + 成员实例清单（offer_group_check.members[].inst_id）。

---

## 7. 后端适配端点（AppStoreV16Controller · 网关 BASE_URL=http://10.86.13.201:31281）

融合加载相关能力经 V2.0 后端统一适配端点暴露于 `/api/v1/appstore/*`：

| 端点 | 融合组相关语义 |
| --- | --- |
| `POST /api/v1/appstore/ops/root-cause` | 组合故障/失败根因定位（根因审计） |
| `POST /api/v1/appstore/ops/work-orders` | 操作工单（融合配置落地执行） |
| `POST /api/v1/appstore/shelf-compliance` | 货架/合规类判定 |
| `POST /api/v1/appstore/validate-nested` | 嵌套校验（组级一致性，见本体推理接入方案） |
| `POST /api/v1/appstore/explain` | 可解释性/溯源（offer_group_check 结论解释） |
| `POST /api/v1/appstore/report/download` | 报告下载 |
| `POST /api/v1/appstore/script/download` | 脚本下载 |

融合组成员构成以 `seed_offer_groups.json`（knowledge/）种子为准，**仅主推荐命中融合组才下发 offer_group**，副推荐命中不影响单品模式；单品入参行为不变。

---

## 8. 知识库同步（原 SKILL.md / 知识库 → knowledge/）

- `${GLOBAL}knowledge/ontology-fields.json`：纪律1 判定字段补充"组场景"（offer_group_check）；纪律8 offer_id 回显扩展为"主 offer_id + 成员清单"；**成员关系纪律：成员构成以 offer_group/plan_json 组结构为准，禁止模型增删成员或自行推理组规则**（码入 wf_sub_02 CODE_FUSION_GROUP_ECHO）；
- `${GLOBAL}knowledge/`：K2 补"组级约束判定口径"小节（互斥/依赖/共享/退订联动的校验动作与告警级别映射，引用现有第 3 节级别表）；
- K4：不新增文档（融合成员字段参照=各成员对应 K4 单文件 + seed preset）；
- K5：Q11（副卡）答案补一句组结构口径；新增 Q14"融合套餐需求怎么提报"。

---

## 9. 验收用例（增量 8 条，对齐细化设计 3.5 口径）

| # | 用例 | 通过标准 |
| --- | --- | --- |
| F1 | 融合需求提报（主套餐+宽带+副卡） | 出口B 六列表格 + 成员构成齐全 + req_id 生成 |
| F2 | 融合需求缺成员价格（宽带功能费未提） | 出口A，pending_fields 定位到 role=宽带；不保存不产 req_id |
| F3 | 需求成员越界（组定义外的成员商品） | 组级 violation 引导修改需求（不进入执行） |
| F4 | 融合确认配置 → 四环节串行 | 环节1 出参含 group 且 offer_id 回显；环节2/3/4 均带成员维度输出 |
| F5 | 成员落地 PARTIAL | PARTIAL 明细含成员定位；引导行按 PARTIAL 口径 |
| F6 | 组级互斥构造（两个权益包） | 环节2 error_list group 类目 ❌ + 中断引导 |
| F7 | 资费比对逐成员输出 | 行数=Σ各成员有值行；E27 按成员内判定 |
| F8 | 单商品全流程回归 | 21 条既有用例全绿，输出与 V2.7 无差异 |

---

## 10. 实施顺序与工作量估算

| 阶段 | 内容 | 交付物 | 估量 |
| --- | --- | --- | --- |
| ① 数据 | K4 融合品抽取种子 | knowledge/seed_offer_groups.json | 0.5d |
| ② 工作流 | 融合组逻辑落为 wf_sub_02/03/04/05 `type=6` 代码节点（gen_workflows_v2.py） | 智能体工作流集V1.6 | 2d |
| ③ 后端适配 | AppStoreV16Controller 融合相关端点适配 | /api/v1/appstore/* | 1.5d |
| ④ 知识库 | ontology-fields.json 组结构注册表 + K2/K3/K5 组口径 | knowledge/ | 1d |
| ⑤ 联调 | F1~F8 用例 + 单商品回归 + 演示剧本融合幕更新 | 验收报告 | 1d |

**总估量约 6~7 人日**。风险点：① K4 融合品描述非结构化，抽取需人工核对资费值逐字一致；② E26 组核对（wf_sub_04 节点 315）依赖后端 offer_group_check 出参质量（先出①再联调⑤）；③ 六列表格与既有 validate 白名单需同步，防校验误报。
