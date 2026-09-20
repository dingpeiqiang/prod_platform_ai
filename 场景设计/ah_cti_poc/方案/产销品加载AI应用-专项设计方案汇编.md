# 产销品加载 AI 应用 · 专项设计方案汇编

> 本汇编汇总《产销品加载 AI 应用》各专项设计与子方案，由原 5 份独立方案文档合并而成，按主题分章节：
>
> - **第 1 章 融合商品（多成员）加载落地实现方案**（原《产销品加载AI应用-融合商品加载落地实现方案.md》）
> - **第 2 章 需求分析模板驱动重构方案**（原《需求分析模板驱动重构方案.md》）
> - **第 3 章 本体推理接入 CPCP 模板轨设计**（原《本体推理接入CPCP模板轨-设计.md》）
> - **第 4 章 存量产品实例化报文生成方案**（原《存量产品实例化报文生成方案.md》）
> - **第 5 章 存量产品数据清洗规则**（原《存量产品数据清洗规则.md》）
>
> 各章均对齐 **V2.0 工作流重塑**口径：确定性逻辑由技能包脚本内嵌为工作流 `type=6` 代码节点（CODE_*），知识库迁至 `knowledge/`（原 references/、skills/ 废弃），后端适配能力经 AppStoreV16Controller `/api/v1/appstore/*` 暴露。
>
> 版本：V2.2 面板收敛　日期：2026-09-19
> 相关：《产销品加载AI应用开发方案.md》V3.0、《产销品加载AI应用-细化设计方案.md》V2.1

> **V2.1 端到端实跑验证（2026-09-19）**：以 `wf_runner.py` strict 模式从「需求提报」串行跑通 8 个子工作流（真实 LLM + 真实网关端点，零回退零错误），覆盖本汇编第 1 章融合/成员链路（单商品链路口径）与第 2 章模板轨。实测 `req_id=PLAN20260919182810908`、`offer_id=982810908`、上线审批单 `AP202609191829100019`（通过）。各章功能口径（24 字段注册表、member_role 分组、31 条固定用例、E26 纪律）实跑未发现与本文档相悖之处；差异点（渠道 APP 未落显、串行顺序、受理场景未覆盖）汇总见《端到端演示剧本》1.5.4。

> **V2.2 结束节点页面单面板收敛（2026-09-19）**：各子工作流结束节点末尾 `{panel}` 片段由「配置工作台(view=workbench) + 环节业务页(view=stage)」双面板收敛为**单面板、单 url**——url 统一用环节业务页 `config-workbench.html?offer_id=..&name=..&chatId=<实值>&stage=<N>&view=stage`（`title` 用环节业务名），移除 `product-detail.html` 单品运营看板与 view=workbench 冗余面板；panel 输出 **JSON 紧凑无空格**（冒号/逗号后无空格，逐字对齐 `{"version":"1.0","message_id":"...","panels":[{"panel":"right","mode":"external","url":"...","title":"..."}]}`）；00/01 阶段 `offer_id` 取 `req_id` 兜底。生成器 `gen_workflows_v2.py` + 11 个 JSON 同步，详见《工作流JSON开发规范.md》§十一。

> **V2.3 面板 url 透传 `req_id` + 页面按 `stage` 动态联动（2026-09-20）**：在 V2.2 单面板基础上，面板 url 进一步追加 `&req_id=<实值>`（`gen_panel_code` 读取 `args.params.req_id`，有 req_id 的环节 00~06 绑定起始节点入参、07~10 空串），url 目标形态 `config-workbench.html?offer_id=..&name=..&chatId=..&req_id=..&stage=<N>&view=stage`；`config-workbench.html` 解析 `req_id`，并新增**导航步进进度（已完成步进勾选 ✓ / 当前步进高亮）与默认 Tab 按 `stage` 动态加载**（8 环节口径，08/09/10 辅助子流按就近展示）。生成器 + 13 个 JSON 同步，详见《工作流JSON开发规范.md》v1.6。

---

# 第 1 章 融合商品（多成员）加载落地实现方案

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

## 1.0 成员关系数据源（先定口径）

| 层级 | 来源 | 说明 |
| --- | --- | --- |
| 生产正源 | 产品域商品目录 + 融合品关系表（CRM 产品中心） | 成员商品主数据 + 主商品→成员清单/角色/必选性/依赖编码 |
| POC 数据源 | **K4 融合品文档结构化抽取** → `seed_offer_groups.json` | 4 个融合品（900102306/900102307/900102313/900113046），成员构成/依赖编码取自 K4 原文（如 900113046"宽带（省内自行配置）：1000M起；天翼高清：1路"、副卡功能费 7320110001600005 可选依赖） |
| 运行时下发 | `similar_offer` 出参内嵌 `offer_group` | 成员构成跟种子数据走，模型不自行推断 |
| 用户显式指定 | 需求原文中的成员表述 | 与组定义求交集校验，越界成员在组级稽核拦截 |

---

## 1.1 总体结构（改动总览 · V2.0 工作流）

```
场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/     # 11 个子工作流 JSON（gen_workflows_v2.py 生成）
  ├── wf_sub_00 ~ wf_sub_10.json        # 各子流程（由智能体/LLM 按 3.2 提示词【意图→工作流映射表】语义识别后直调）
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

## 1.2 数据层：融合组种子 `seed_offer_groups.json`

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

## 1.3 本体层：`knowledge/ontology-fields.json`，组件结构注册表（原 `references/ontology-fields.md`，V4.0 组结构注册表已迁 knowledge/）

### 1.3.1 plan_json 组结构（V4.0）

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

### 1.3.2 取值链（逐成员独立）

```
原始需求（成员级提取） → 相似融合品 offer_group（组级下发，成员逐一对位）
  → 本体默认值（引擎逐成员补全） → 待补充（仅各成员价格类字段）
```

- **待补充判定按成员独立**：任一成员价格字段待补充 → 整体出口A；`pending_fields` 每项携带 `role` 定位；
- 主套餐档位与各成员月功能费互相独立，禁止跨成员照搬价格（价格禁止推理纪律延伸到组级）。

### 1.3.3 引擎两轮推理

1. **成员内推理**（复用现有 action=reason）：逐成员跑 24 字段校验/修正/补全；
2. **组级校验**（新增 action=group_check）：互斥/依赖/退订联动对照 `group_rules` + K2 约束表，输出 `group_violations[]`（格式与 violations 一致：item/level/desc/suggest）。

---

## 1.4 工作流层：确定性逻辑落为 `type=6` 代码节点（原技能脚本 `cpcp_api.py` V3.1 / `validate_output.py` V1.1 已代码化）

> 原技能包脚本（`build_plan` 组结构识别、`spec_audit` 组类目、`billing_verify` 成员分组、`map_fixed_cases` 组结论透出、`validate_output` 成员行/E26 组核对）均已内嵌为工作流 `type=6` 代码节点（`CODE_*`）。

> **配置规范对齐（《SitechAI开发平台配置规范.md》代码节点规范）**：本方案确定性逻辑统一以 **type=6 代码节点**落位（`async def main(args)` + `args.params` 取入参；urllib.request 相对路径拼接 `BASE_URL` 占位符，经网关访问 `/api/v1/appstore/*`；含 `_json` 出参名（如 `plan_json`/`config_json`）存 JSON 字符串；后端不可达一律 `backend_pending=1` 优雅回退保离线 Demo）；出参中新增的 array 型（`group_violations[]`、`error_list[]`、`compare_list[]`）均配置 item 树（ARRAY_ITEM_FIELDS 白名单），键名 snake_case。生成与重新生成统一走 `gen_workflows_v2.py`，禁止手改 JSON。

### 1.4.1 融合成员回显代码节点（`CODE_FUSION_GROUP_ECHO`，wf_sub_02）

```
入参 offer_group / plan_json 组结构：
├─ 扁平 fields（单商品）           → 单商品：行为与 V2.7 一致
└─ 组结构 {offer_type, main_offer, member_offers}
     → plan_md 增"商品"列（六列：商品/模块/分类/字段名称/字段值/备注）
     → 主商品行 role="主卡套餐" 加粗展示；成员分组展示，成员名独立成块
     → pending_fields 由 {role, field} 组成
     → req_id 生成规则不变（PLAN+14时间戳+3随机）
```

### 1.4.2 各子流程代码节点改动

| 子流程/节点 | 改动 |
| --- | --- |
| wf_sub_02（融合成员回显） | 识别组结构入参 → 逐成员推理 + 组级校验；出参新增 `group_violations[]`；`remark_excluded` 剔除逻辑按成员内生效 |
| wf_sub_03（规格稽核） | `config_json` 组结构原文透传（不变）；出参 `error_list[]` 新增 `group` 类目（item=`group:<role>`） |
| wf_sub_05（资费校准） | 出参 `compare_list[]` 每项新增 `member_role` 键（单商品时缺省="主卡套餐"） |
| wf_sub_04 `CODE_MAP_FIXED_CASES` | `testScenes[]` 组场景照列（见 1.6）；`offer_group_check`（组一致性结果）出参透出；后端 `testCases[]` 优先透出不变；组维度结论由后端生成，节点不自行聚合（纪律不变） |

### 1.4.3 输出校验（`validate_output` 逻辑代码化）

- 组场景名（套餐新装/副卡加装/套餐退订/**成员加装/成员退订**等出参实际返回）逐行核对；
- 按 `member_role` 分组核对行存在性与空值省略；E27 阈值改为**逐成员内**计算（防多成员稀释误判）；
- E26 组核对扩展：核对"主 offerName + 成员角色清单"与 plan_json 组结构一致；
- 融合 plan_md 六列表头 `| 商品 | 模块 |` 纳入校验白名单。

### 1.4.4 回归验证

- 融合组场景断言（组结构入参 → 六列表格 + pending_fields 携带 role + 主成员加粗）；
- 单商品回归：扁平入参 → 输出与 V2.7 逐字段一致（防回归）。

---

## 1.5 流程层：融合口径接入 V2.0 工作流（原 flow-A/B/C/D 文档故有改动点）

### 1.5.1 需求分析轨（原程序A，`wf_sub_01` 模板轨）

| 环节 | 改动 |
| --- | --- |
| 商品拆分（产品识别） | 需求原文识别成员商品（宽带/高清/副卡/权益包关键词同义词表）；未明确成员 → 按相似融合品 offer_group 补全（source=AI补全）；拆分结果内嵌于 elements 组结构 |
| 相似查询 | `similar_offer` 出参含 `offer_group` → 直接作为成员补全与组规则来源（禁止模型重推）；未命中融合组 → 按 E1 中断询问（口径不变） |
| 合并（merge 节点） | 同构合并逐**成员**执行（主+N 成员各自对齐 offerInfo/offer_group），价格字段禁止跨成员照搬 |
| validate_nested 本体闸 | 两轮推理：逐成员 reason → 组级校验；`group_violations` 非空时与 violations 同流程处置（仅名称类不中断，其余中断引导） |
| 待补充判定 | pending_fields 判定按成员独立；任一成员有待补充 → 出口A（不保存不产 req_id） |
| 出口 | 出口A/B 文案增加"融合成员构成"行（主+N 成员名清单）；出口B 表格为六列 |

### 1.5.2 执行轨（原程序B，`wf_sub_02` 融合成员回显 + `wf_sub_03` 规格稽核 + `wf_sub_05` 资费校准）

| 环节 | 改动 |
| --- | --- |
| 智能配置（wf_sub_02 CODE_FUSION_GROUP_ECHO） | 出参 `group`（主 offer_id + members[]）随 offer_id 显性回显纪律同步展示：模板新增"融合成员：宽带（offer_id xxx）/天翼高清（xxx）/副卡功能费（xxx）"行，逐字引用出参，禁止省略成员行；**组内任一成员 PARTIAL → 整体按 PARTIAL 口径处置（失败分类明细含成员定位）** |
| 规格稽核（wf_sub_03） | 稽核对象行增加"组维度：主 offer_id + N 成员"；error_list 含 group 类目 → 对应成员行标 ❌ 并附明细；七项检查项结构不变 |
| 资费校准（wf_sub_05） | 比对表按成员分组输出（组间空行分隔或独立小表），行数=Σ各成员有值行；空值行省略规则与 E27 判定**逐成员内**计算；8 项比对项目名不变 |
| 自动测试（wf_sub_04 CODE_MAP_FIXED_CASES） | 场景表输出组场景（出参实际返回为准）；受理验证新增"成员组合验证"（数据源=出参 offer_group_check，逐字引用）；E26 预校验扩展为组核对（主 offerName 一致 + 出参成员角色与 plan_json member_offers 角色集合一致；不一致 → E26 中断于 wf_sub_04 节点 315，铁律不变：禁止输出任何通过性明细） |
| 汇总块 | 关键数据列增加"融合成员 N 个全部成功/部分失败"；其余结构不变 |

### 1.5.3 审批/监控轨

- 审批轨（原 flow-C）：看板"自动测试（三大验证）"行结论引用含成员组合验证结果；报告归档口径不变（正式版 9 章节模板增加成员构成基础信息项）；
- 监控轨（原 flow-D)：D-2 监控 `offer_id` 可传主 offer_id（组维度指标）或成员 offer_id（成员维度），前置检查不强制区分（出参为准）；D-3 方案模板不变。

---

## 1.6 自动测试规范：融合组场景（原 K3 增补，V2.1 增补版 → 落 wf_sub_04 `CODE_MAP_FIXED_CASES`）

### 1.6.1 组场景编码（新增 2 个，与现有 3 个并存）

| 场景编码 | 场景名称 | 必选性 |
| --- | --- | --- |
| S_GROUP_BIND | 融合成员绑定 | 融合品必选（有 required 成员时） |
| S_ADDON_SUB | 可选成员加装/退订 | 有 required=false 成员时必选 |

- S_O_TC / S_U_TC / S_ADD_CARD 口径不变（主卡号码维度）；
- 新测点：`P_SHARE`（共享权益校验）/ `P_GROUP_MUTEX`（组互斥）/ `P_MEMBER_STATUS`（成员实例状态），预期值取自 seed_offer_groups preset。

### 1.6.2 固定用例映射（31 条不变，组级结论由后端出参）

- 31 条固定用例清单**不增删**（判定依据出参映射表在组场景下自然覆盖：如 ACC-004 引用 S_O_TC 场景不变）；31 条口径由 `wf_sub_04 CODE_MAP_FIXED_CASES` 承载并核对正确；
- 组维度通过性：后端按组场景结果生成 `overallConclusion`，代码节点/模型只透出，禁止聚合改判；
- 受理凭证核验扩展：主订单 orderId/offerInstId + 成员实例清单（offer_group_check.members[].inst_id）。

---

## 1.7 后端适配端点（AppStoreV16Controller · 网关 BASE_URL=http://10.86.13.201:31281）

融合加载相关能力经 V2.0 后端统一适配端点暴露于 `/api/v1/appstore/*`：

| 端点 | 融合组相关语义 |
| --- | --- |
| `POST /api/v1/appstore/ops/root-cause` | 组合故障/失败根因定位（根因审计） |
| `POST /api/v1/appstore/ops/work-orders` | 操作工单（融合配置落地执行） |
| `POST /api/v1/appstore/shelf-compliance` | 货架/合规类判定 |
| `POST /api/v1/appstore/validate-nested` | 嵌套校验（组级一致性，见第 3 章本体推理） |
| `POST /api/v1/appstore/explain` | 可解释性/溯源（offer_group_check 结论解释） |
| `POST /api/v1/appstore/report/download` | 报告下载 |
| `POST /api/v1/appstore/script/download` | 脚本下载 |

融合组成员构成以 `seed_offer_groups.json`（knowledge/）种子为准，**仅主推荐命中融合组才下发 offer_group**，副推荐命中不影响单品模式；单品入参行为不变。

---

## 1.8 知识库同步（原 SKILL.md / 知识库 → knowledge/）

- `${GLOBAL}knowledge/ontology-fields.json`：纪律1 判定字段补充"组场景"（offer_group_check）；纪律8 offer_id 回显扩展为"主 offer_id + 成员清单"；**成员关系纪律：成员构成以 offer_group/plan_json 组结构为准，禁止模型增删成员或自行推理组规则**（码入 wf_sub_02 CODE_FUSION_GROUP_ECHO）；
- `${GLOBAL}knowledge/`：K2 补"组级约束判定口径"小节（互斥/依赖/共享/退订联动的校验动作与告警级别映射，引用现有第 3 节级别表）；
- K4：不新增文档（融合成员字段参照=各成员对应 K4 单文件 + seed preset）；
- K5：Q11（副卡）答案补一句组结构口径；新增 Q14"融合套餐需求怎么提报"。

---

## 1.9 验收用例（增量 8 条，对齐细化设计 3.5 口径）

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

## 1.10 实施顺序与工作量估算

| 阶段 | 内容 | 交付物 | 估量 |
| --- | --- | --- | --- |
| ① 数据 | K4 融合品抽取种子 | knowledge/seed_offer_groups.json | 0.5d |
| ② 工作流 | 融合组逻辑落为 wf_sub_02/03/04/05 `type=6` 代码节点（gen_workflows_v2.py） | 智能体工作流集V1.6 | 2d |
| ③ 后端适配 | AppStoreV16Controller 融合相关端点适配 | /api/v1/appstore/* | 1.5d |
| ④ 知识库 | ontology-fields.json 组结构注册表 + K2/K3/K5 组口径 | knowledge/ | 1d |
| ⑤ 联调 | F1~F8 用例 + 单商品回归 + 演示剧本融合幕更新 | 验收报告 | 1d |

**总估量约 6~7 人日**。风险点：① K4 融合品描述非结构化，抽取需人工核对资费值逐字一致；② E26 组核对（wf_sub_04 节点 315）依赖后端 offer_group_check 出参质量（先出①再联调⑤）；③ 六列表格与既有 validate 白名单需同步，防校验误报。

---

# 第 2 章 需求分析模板驱动重构方案（cpcp-product-worker 需求分析链路重构）

> 版本：V2.0（最新口径对齐 V2.0 工作流重塑） | 日期：2026-09-16
> 目标：把 flow-A（程序A）从「24 字段本体驱动」重构为「**逻辑模型模板驱动**」——模板 schema（来自《产品配置结构化映射逻辑模型规范.xlsx》6 个模板）成为配置报文的唯一骨架，LLM 只做自然语言→结构化翻译，确定性逻辑（合并/渲染/路由/校验）全部代码化。
> 实施口径：**直接切换**（不双轨）；`derive_flat24` 派生 24 字段 plan_json 作为下游过渡兼容层（环节2/3 后端按 24 字段校验继续可用）。
>
> **V2.0 履历（工作流重塑）**：本文档对应的模板轨六步流程已由技能包脚本转为 **`wf_sub_01` 模板轨代码节点**承载——
> - 产品识别 → LLM 提示词阶段；
> - get_template（节点 106，模板获取）；
> - 要素提取（LLM 翻译阶段）；
> - validate_elements（节点 108，提取质量门禁）；
> - merge_nested（节点 109，JSONPath 对位合并）；
> - validate_nested 本体闸（节点 110，调用网关 `POST /api/v1/appstore/validate-nested`）；
> - render_table（节点 111，层级表格渲染）。
> - 工具 20~24（identify_products/get_template/merge_nested/render_table/derive_flat24）逻辑由代码节点承载；`validate-nested`/`explain` 端点已在后端 **AppStoreV16Controller**（`/api/v1/appstore/validate-nested`、`/api/v1/appstore/explain`）实现；知识库迁至 `knowledge/`（templates 注册表、ontology-fields.json 已迁 `knowledge/`，原 `references/`、`skills/` 废弃）；`merge_fields` 废弃口径与 V2.0 一致。

---

## 2.0 背景与动机

### 2.0.1 现状（V6.1）链路

```
需求原文
 → ①LLM 按 24 字段注册表提取 elements（扁平字段数组）
 → ②similar_offer 相似查询（后端 toFields24 转成 24 字段数组）
 → ③merge_fields 同名合并（扁平对位）
 → ④ontology_reason 引擎补全（引擎=事实源）
 → ⑤build_plan 五列/六列表格（24 字段 → 模块/分类归并）
 → ⑥save_node_result（plan_json 入库）
```

### 2.0.2 现状问题（重构动因）

| 问题 | 说明 |
| :--- | :--- |
| 字段太粗 | 24 字段是"需求单"口径，不是"配置报文"口径——落地时 plan_json 与真实逻辑模型报文（baseInfo/optionalInfo/…嵌套结构）之间靠后端隐式转换，AI 无法核对 |
| 模板覆盖缺位 | 6 类产品模板（个人主资费/宽带主资费/个人附加/加速包/家庭基础/家庭附加）结构差异大，24 字段一律同仁，丢失模板级约束（枚举/show-when/默认值/必填） |
| 输出不可读 | plan_md 是 24 字段平铺表，业务人员看不懂配置语义（无层级、无分组） |
| 相似产品只能给"值" | 相似品出参是 24 字段值，无法按模板路径逐位对位补全 |

### 2.0.3 重构目标（六步流程，已与业务对齐）

| 步 | 职责 | 载体 | LLM? |
| --- | --- | --- | --- |
| ① | 识别需求中待配置的产品列表（单品/融合/多品，逐产品定模板） | LLM（封闭输出 schema）+ 模板索引路由 | 是（翻译） |
| ② | 逐产品查询相似产品 | 工具：存量目录检索（本地）→ similar_offer（远端） | 否 |
| ③ | 按产品模板取配置逻辑模型模板 | 工具：templates/_index.json + <templateId>.schema.json | 否 |
| ④ | 结合模板提取需求配置要素（嵌套形态，与模板同构） | LLM（schema 注入提示词，输出仅含命中的路径） | 是（翻译） |
| ⑤ | 相似产品报文 与 提取要素 整合 | 工具：merge_nested.py（JSONPath 对位合并，已完成 V1.0） | 否 |
| ⑥ | 生成结构化文本（业务可读层级表格，非 JSON） | 工具：render_table.py（V1.1 已完成） | 否 |

**已完成前提**（本会话前序成果）：
- 存量清洗 C1~C15：`clean_products.py` + `存量产品目录_清洗后.json`（18 条：5 familyBasePrc / 5 personMainPrc / 8 personAddPrc）
- xlsx→schema：`excel_to_schema.py` + `templates/` 6 个 schema（leafs 85/43/99/45/93/75，show-when 60，enum 210，number 84）+ `_index.json`
- `merge_nested.py` V1.0（第⑤步）：JSONPath 对位 + 价格禁照搬 + default 兜底 + pending_required + _meta 溯源，familyBasePrc/personMainPrc 双样例断言 ALL PASS，幂等回归 PASS
- `render_table.py` V1.1（第⑥步）：层级表格渲染，端到端联测通过（merge 输出→渲染）
- `存量产品实例化报文生成方案.md`（存量报文补齐子方案，P0~P3 分批，待并入本方案实施，详见本汇编第 4 章）

---

## 2.1 目标架构

### 2.1.1 总体链路（wf_sub_01 模板轨，原 flow-A' 模板驱动）

```
需求原文
 → ①LLM 产品识别（产品列表[]，每项 product_type→templateId 路由；封闭 schema）
 → ②逐产品相似查询（本地存量目录检索 offer_id + 实例化报文；远端 similar_offer 兜底）
 → ③get_template 节点106（templates/_index.json 路由 → <templateId>.schema.json）
 → ④LLM 逐产品按模板提取（提示词注入该模板 schema 骨架；输出嵌套 elements，仅命中路径）
 → ⑤merge_nested 节点109（elements + 相似品实例化报文，JSONPath 对位）
 → ⑤.5 validate_nested 节点110（调用网关 POST /api/v1/appstore/validate-nested，本体一致性闸）
 → ⑥render_table 节点111（逐产品渲染层级表格，模板树 L1~Ln 分组）
 → 输出：《销售品加载方案》层级表格版（plan_md_v2）+ 嵌套 plan_json_v2
 → save_node_result（node=requirement，plan_json_v2 入库；向后兼容：24 字段 plan_json 同步派生保留）
```

### 2.1.2 切换口径（评审结论 #1：直接切换）

| 项 | 旧轨（V6.1，退役） | 新轨（V7.0，本重构） |
| --- | --- | --- |
| 字段口径 | 24 字段扁平 | 模板 schema 嵌套（6 模板） |
| 合并工具 | merge_fields（field 名对位）→ **@deprecated** | merge_nested（JSONPath 对位） |
| 输出表格 | 五列/六列平铺 | **分节多表**（概览卡片 + 按业务模块分节，render_table V2.0） |
| 引擎依赖 | ontology_reason（后端） | schema enum/default 兜底（本地，无后端依赖） |
| 兼容 | — | derive_flat24 单向投影（v2→flat），flat plan_json 继续入库供环节2/3 |

**切换执行**：S4 起 flow-A 整体改写为模板轨；旧轨文档迁 `方案/_deprecated/` 存档；merge_fields 代码标 `@deprecated`（一个迭代周期后随 C 系列清理纪律删除）。

### 2.1.3 组件落位（V2.0：代码节点 + knowledge/）

```
场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/
  wf_sub_01.json（模板轨）
    节点106  get_template（模板获取）
    节点108  validate_elements（提取质量门禁）
    节点109  merge_nested（JSONPath 对位合并）
    节点110  CODE_OP_VALIDATE_NESTED（调用网关 /api/v1/appstore/validate-nested）
    节点111  render_table（层级表格渲染）
  确定性逻辑全部内嵌为 type=6 代码节点（gen_workflows_v2.py 生成）

knowledge/
  templates/                    6 个模板 schema + _index.json（注册表，自 方案/templates/ 迁入）
  ontology-fields.json          旧轨 + derive_flat24 映射依据（自 ontology-fields.md 迁入）
方案/                           保留：清洗器/转换器/测试样例/存量实例化方案（开发域资产）
```

---

## 2.2 各步骤细化设计

### 2.2.1 第①步 产品识别（LLM，唯一新增 LLM 环节）

- **输入**：需求原文。
- **输出 schema（封闭）**：
  ```json
  {"products":[{"name":"5G-A融合套餐199元","product_type":"融合套餐|单品套餐|权益包|宽带套餐|宽带加速包|家庭附加",
                "template":"familyBasePrc|personMainPrc|personAddPrc|broadBandMainPrc|broadBandOptSpeedPrc|familyAddPrc",
                "members":["宽带","天翼高清","副卡功能费"]}],
   "need_summary":"≤5000字符需求摘要"}
  ```
- **路由依据**：product_type → templateId 映射表固化在 `templates/_index.json`（融合→familyBasePrc、单品→personMainPrc、权益包→personAddPrc 等）；LLM 只输出 product_type，路由代码做——**LLM 不直接猜 templateId**（防止模板名幻觉）。
- **失败处置**：product_type 无法归类 → 中断询问（复用 E1 形态），禁止默认路由。

### 2.2.2 第②步 相似查询（工具化，双源）

- **主源（本地，确定性）**：在 `存量产品目录_清洗后.json` 按 product_type + tier + members 关键词检索 top1 → 直接取该品**实例化报文**（`存量报文/<offer_id>.json`，见第 4 章存量实例化子方案）。
- **兜底源（远端）**：本地未命中 → `similar_offer --desc <need_summary>`（现状接口）；其 24 字段出参经**逆向投影**（flat24 → 模板路径映射表，与 derive_flat24 共用映射表）转为模板嵌套报文。
- **纪律**：相似品报文仅作 AI补全来源；价格字段禁照搬（merge_nested 已内置）。

### 2.2.3 第③步 模板获取（纯路由）

- `get_template --template-id <id>` 读 `_index.json` 返回 schema 文件绝对路径 + 元数据（leafs 数/价格字段表）。无 LLM。

### 2.2.4 第④步 模板化提取（LLM，提示词核心）

- **提示词结构**（写入 wf_sub_01 模板轨要素提取环节，全文模板）：
  1. 角色与任务：从需求原文提取配置要素，仅填命中的模板路径；
  2. 注入：该产品模板 schema 的**叶子路径清单**（path / x-label / type / enum / x-show-when / x-required，按 baseInfo→…顺序）；6 模板 43~99 叶子，全部注入约 1~2K token，可接受；
  3. 规则：只提取原文可找到（含同义改写）的路径；未提及路径**不输出**（merge 以 schema 补骨架，无需显式空值）；价格值必须原文数字；枚举值用原文措辞（不强行归一枚举，归一在⑤按 enum 校验报警）；修饰语剥离规则沿用旧轨（"30GB（可结转）"→ 资源值 + 规则路径分置）；
  4. 输出：嵌套 JSON（与模板同构，仅命中路径）。
- **质量自检（代码化，防提取失败；wf_sub_01 节点108 validate_elements）**：路径合法性（∈ schema）/数值合法性/枚举命中率统计；命中率 < 阈值（P0 定 60%）→ 提示词重试一次，再低中断人工。
- **dup 与产品信息.txt 纪律**：需求与存量品同名的只标注；900102306→900113046 指向 active。

### 2.2.5 第⑤步 合并（wf_sub_01 节点109 merge_nested）

- 增量 1：`--mode legacy`（存量实例化：source 标签"存量提取"+ 价格豁免）——已在存量实例化方案中定义，同一逻辑落地为代码节点；
- 增量 2：出参增加 `plan_md_v2`（直接内联调用 render_table）或保持双节点分离（**建议分离**，职责单一，由工作流串联两次调用）；
- 增量 3：enum 校验（提取值 ∉ enum → 出参 `_meta[path].enum_violation=true`，不阻断，进异常清单）。

### 2.2.6 第⑥步 渲染（wf_sub_01 节点111 render_table）

- 增量 1：SKIP_KEYS 增加 pricingId（已含）；按模板 x-label 渲染组名时附加英文容器名（现状已有，保持）；
- 增量 2：出参支持 `--out-file`（plan_md_v2 工件落盘，供 validate_output/输出环节逐字引用）。

### 2.2.7 24 字段派生（derive_flat24，兼容层）

- 映射表：模板路径 → 24 字段名，固化在 `knowledge/templates/_index.json` 附属映射（已迁至 knowledge/）；如 `optionalInfo.printContent.prcMonthFee`→套餐档位+计费口径；`baseInfo.roleMax`→无对应丢弃；24 字段无对应路径的填默认值口径同 `knowledge/ontology-fields.json`）；
- 方向：**v2→flat 单向投影**（新轨产物派生旧 plan_json 入库），禁止反向（flat→v2 有损）；
- 派生后走既有 build_plan（旧轨复用）组装 flat plan_json——下游零改动。

---

## 2.3 存量数据层（并入实施）

### 2.3.1 存量实例化报文（子方案已立，此处衔接）

- 按本汇编第 4 章《存量产品实例化报文生成方案》P0~P3 分批生成 18 份 `存量报文/<offer_id>.json`；
- 落位：**knowledge 侧** `knowledge/存量报文/`（第②步本地相似检索的读取路径，与 K4 存量 md 并列；方案目录留生成工具与中间产物）；
- dup（900102306）：复制 active 版加 `x-dup-of` 标记。

### 2.3.2 模板注册表（knowledge/templates/_index.json）

- 内容：6 模板 × {templateId, name_cn, product_type, leafs, 价格字段路径表, x-show-when 计数, enum 总数, flat24 映射表}；模板注册表自原 `references/templates-registry.md` 迁至 `knowledge/templates/`；
- 生成方式：`excel_to_schema.py` 扩展 `--emit-registry`（从 schema 自动统计，禁止手写数字）。

---

## 2.4 工作流文档与节点契约改动（V2.0：原 flow-A / tools-contract / SKILL.md / exception-matrix / ontology-fields 对应件）

- `wf_sub_01.json`（模板轨，原 flow-A-requirement.md 改写为模板轨的落点）：整体承载模板轨六步（产品识别 → get_template 节点106 → 要素提取 → validate_elements 节点108 → merge_nested 节点109 → validate_nested 本体闸节点110 → render_table 节点111）；取值链改为：原始需求 → 相似品模板报文（AI补全）→ schema default（默认值）→ 待补充（价格类）。
- 工具 20~24（identify_products / get_template / merge_nested / render_table / derive_flat24）契约：逻辑全部落为 `type=6` 代码节点，由 `wf_sub_01` 引用；工具1 similar_offer 契约登记 `offerTemplate` 出参（后端 POC 改造项）；`merge_fields` 废弃口径与 V2.0 一致（标 @deprecated）。
- `wf_sub_01` 节点110 `CODE_OP_VALIDATE_NESTED`：调用网关 `POST /api/v1/appstore/validate-nested`（远端本体一致性闸），可解释性走 `POST /api/v1/appstore/explain`——两端点已由后端 **AppStoreV16Controller** 实现。
- 异常矩阵（原 exception-matrix.md）：增 E30（模板路由失败：product_type ∉ 枚举）/ E31（提取质量门禁未达标：枚举命中率/必填缺失率超阈）/ E32（schema 文件缺失/损坏）；复用 E1 中断询问形态。
- `knowledge/ontology-fields.json`：标注"flat24 派生映射依据（下游过渡兼容）"，不再作为提取注册表（自 ontology-fields.md 迁至 knowledge/）。

---

## 2.5 实施计划（批次与验收）

| 阶段 | 内容 | 产出/验收 |
| --- | --- | --- |
| S0 方案评审 | 本文档评审，6 项结论已确认（见 2.6 章） | ✅ 2026-09-16 完成 |
| S1 模板资产迁移 | 方案/templates → skill/scripts/templates；templates-registry.md 生成（excel_to_schema --emit-registry） | 6 schema + registry ALL_PASS |
| S2 存量实例化 | 按存量方案 P0~P3 生成 18 份报文入 K5（含 --mode legacy 分支） | _report.json 异常清单归零 + 抽检通过 |
| S3 工具集成 | cpcp_api 增 5 子命令（含 identify_products LLM 提示词出参约定）+ derive_flat24 + validate_elements；merge_fields 标 @deprecated | 本地自测脚本（新增 12 用例）全 PASS |
| S4 流程文档 | flow-A 模板轨整体改写 + tools-contract + SKILL.md + exception-matrix + 旧轨存档 | 文档评审 |
| S5 端到端联调 | 幕1/1B 演示剧本模板轨改写（新增幕1D：需求→分节表格全链路）+ 后端 POC by-template 出参联调 | 演示彩排 PASS；下游环节（配置/稽核/测试）以 flat 派生回归 PASS |
| S6 存量回归 | 18 存量品逐个跑模板轨全链路 | 每品分节表格人工抽检 |

**工作量估算**：S1~S3 约 2~3 天（工具已大半完成）；S4 约 1 天；S5~S6 约 1~2 天（含后端 by-template 联调）；合计 1.5 周内。

---

## 2.6 评审结论（2026-09-16 已确认）

| # | 问题 | 结论 | 方案落实 |
| --- | --- | --- | --- |
| 1 | 双轨并存 vs 直接切换 | **直接切换** | S4 起直接改写 flow-A 为模板轨（步骤 1~8 整体替换为 A1~A8），**不保留 24 字段旧轨**；`derive_flat24` 仅作下游过渡兼容层（环节2/3 后端仍按 24 字段校验，派生 plan_json 继续入库），旧轨代码（merge_fields 等）标 `@deprecated` 保留一个迭代周期后清理 |
| 2 | 第①步产品识别 LLM 依赖 | **必须使用 LLM** | 不做规则优先兜底；identify_products 固定走 LLM（封闭 schema 单选输出，禁止自由文本），与四层架构"翻译"定位一致 |
| 3 | 逆向投影 | **远端 POC 增加 by-template 出参** | similar_offer 出参增加 `offerTemplate`（按 6 模板嵌套的实例化报文），映射表与 templates-registry 固化；本地存量检索仍为主源，远端为兜底；需在细化设计方案 2.x 工具1 契约中同步登记后端改造项 |
| 4 | enum 归一时机 | **校验不改写** | LLM 输出原文措辞；merge_nested 按 schema enum 校验报警（`_meta[path].enum_violation=true`）不改写值，异常进清单人工确认 |
| 5 | plan_json_v2 入库形态 | **双份** | save_node_result requirement 节点存 `plan_json_v2`（嵌套报文 + _meta + pending）与 `plan_json`（derive_flat24 派生），双份合计 <64KB；query_node_result 下游按需取用 |
| 6 | 存量报文落位 | **K5（knowledge 内）** | `knowledge/存量报文/`（原 `references/K5存量报文/`，已迁 knowledge/）18 份实例化报文 + `_report.json`；第②步本地相似检索读取路径与此一致 |

---

# 第 3 章 本体推理接入 CPCP 模板轨——设计（R2·对接 backend-app Java 推理平台）

> 版本：R2（V2.0 对齐标高）
> 日期：2026-09-17
> 范围：只出设计，不改代码
> 决策前提（经评审确认）：
> 1. **不新造本体、不自拍 TTL**——`backend-app` Java 工程已实装完整的 CPCP 本体推理平台（TTL 本体 + OWLAPI/Openllet/RDF4J 推理机 + SWRL + SHACL + explain/provenance 可见性），本设计是**对接与接线**。
> 2. Java 推理能力优先接入 **flow-A 模板轨步骤⑤ merge_nested 之后 → 步骤⑥ 之前**新增确定性校验闸。
> 3. 推理过程可见性**复用 Java 已有 `config/explain` + `config/provenance/{field}`**（PROV-O），不在 Python 侧重造。
>
> **V2.0 履历（工作流重塑）**：本设计核心功能**已被 V2.0 采用**——后端 **AppStoreV16Controller** 已新增 `POST /api/v1/appstore/validate-nested` 与 `POST /api/v1/appstore/explain` 两端点；`wf_sub_01` 模板轨已插入 `CODE_OP_VALIDATE_NESTED`（节点110）调用网关端点（网关 BASE_URL=http://10.86.13.201:31281，由 gen_workflows_v2.py 生成）。因此本设计由"建议新增端点 / 未实现 / 技能脚本调用"口径改为"**对接已被 V2.0 采用**"，路径对齐 `/api/v1/appstore/validate-nested` + `/api/v1/appstore/explain`；原 SKILL.md / `cpcp_api.py validate_nested` 子命令表述已删（确定性逻辑落为代码节点）；知识库迁至 `knowledge/`（原 `references/` 废弃）。

---

## 3.0 背景与结论摘要

### 3.0.1 现状（实测核实，非假设）
- CPCP 技能（`cpcp-product-worker`）V7.0 已切换模板轨：产物是 **6 个逻辑模型模板 schema 驱动的嵌套报文**（`merge_nested` 输出 `payload`）。V2.0 起该模板轨由 `wf_sub_01` 代码节点承载（get_template 节点106 → validate_elements 节点108 → merge_nested 节点109 → validate_nested 节点110 → render_table 节点111）。
- `backend-app` Java 工程**已具备** CPCP 产商品本体推理平台：
  - 本体：`src/main/resources/ontology/product-config.ttl`（v2.2，含 `ConfigScheme/PricingProduct/ChargePlan/PreferentialPlan/ResourceEntitlement/ComplianceRule` 等全套业务类与对象属性）。
  - 推理机：pom.xml 已声明 RDF4J（Sail+SHACL）、OWLAPI、Openllet。
  - 服务：`OpsSwrlReasoner`（SWRL）、`ShaclValidationDelegate`（SHACL）、`Rdf4jOntologyStore`、`SparqlConfigDiscoverer`、`TemplateDeriveEngine`、`TemplateComplianceService`。
  - 控制器：`ProductOntologyController`（`/api/v1/product-ontology`）已暴露 `config/infer`、`config/compliance`、`config/explain`、`config/trace`、`config/provenance/{field}`、`config/discover`、`ops/*`、`ops/hypothetical` 等端点。
  - **`FieldOntologyService` 就在此工程**——CPCP 旧轨 `ontology_reason` 现状的后端即它。**管道已通，缺的是把 v7.0 模板轨嵌套报文接进 Java 推理**。

### 3.0.2 设计结论
"本体推理放哪里最合适"的答案：**不在 LLM、不回旧 24 字段扁平，而是在 flow-A 模板轨确定性环节（⑤→⑥）新增一个调用 Java 推理平台的校验闸**，并复用其 explain/provenance 承载可见性。这样：
- 守住 SKILL.md 四层架构铁律（确定性逻辑脚本化，LLM 只在 ①④ 翻译）；
- 复用既有 TTL 本体 + Openllet/SWRL/SHACL，不重复造轮子；
- 补上模板轨目前缺失的**嵌套跨字段一致性 / 合规 / 归一**判定维度（`validate_elements` 只做④提取质量门禁，`merge_nested` 只做骨架对位）。

> **V2.0 采用落地**：上述校验闸已由后端 **AppStoreV16Controller** 的 `POST /api/v1/appstore/validate-nested` 实现，`wf_sub_01` 节点110 `CODE_OP_VALIDATE_NESTED` 直接调用该网关端点；可见性可解释项由 `POST /api/v1/appstore/explain` 承接。本设计的端点协议与 Python 工具表述为**已被实现**的前置依据，而非待新增项。

---

## 3.1 落点：flow-A 步骤⑤.5「嵌套本体校验闸」（V2.0 已采用落为 wf_sub_01 节点110）

### 3.1.1 位置（wf_sub_01 模板轨）
```
产品识别 → get_template 节点106 → 要素提取 → validate_elements 节点108（质量闸）
  → merge_nested 节点109（合并）→ 【CODE_OP_VALIDATE_NESTED 节点110（①⑤.5 校验闸）】
  → render_table 节点111 → ⑦ flat24 派生 + 保存
```

### 3.1.2 已实现的网关端点（AppStoreV16Controller）
V2.0 已定稿：校验闸由后端 **AppStoreV16Controller** 暴露 `POST /api/v1/appstore/validate-nested`（网关 BASE_URL=http://10.86.13.201:31281），本体一致性/合规/归一判定在此承载；可解释性由 `POST /api/v1/appstore/explain` 承接。历史设计曾提出在 `ProductOntologyController` 新增 `/api/v1/product-ontology/config/validate-nested`，该路径现由 `/api/v1/appstore/validate-nested` 对齐（对应 `config/infer` + `config/compliance` + `config/explain` 组合的聚合）。

```
POST /api/v1/appstore/validate-nested
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

### 3.1.3 代码节点拓扑（wf_sub_01 节点110 CODE_OP_VALIDATE_NESTED）
```
CODE_OP_VALIDATE_NESTED（type=6 代码节点）
  --template <templateId>
  --payload-json-file <merge 节点109 出参 payload 工件>
  [--similar-offer-file <相似品报文>]
  内部调用网关 POST /api/v1/appstore/validate-nested
```
- 出参 `violations` 非空且含 `severity=high` → 按 **E33** 中断引导（异常矩阵新增）。
- `severity=warn` → 不阻断，随 render_table 节点111 输出放到【风险提示】小节。
- `trace_id` 落盘工件，供 `/api/v1/appstore/explain` 引用。

> **配置规范对齐**：节点110 为 type=6 代码节点（`async def main(args)`/`args.params`），内部经网关 `BASE_URL` 占位符调用 `/api/v1/appstore/validate-nested`；后端不可达一律 `backend_pending=1` 优雅回退（不阻断离线 Demo）；`violations` array 出参配 item 树。

### 3.1.4 端点方案取舍（评审结论已定）
历史设计曾评估"不新增端点，直接组合现有 `config/infer` + `config/compliance` + `config/explain`"以最小改动（优点零 Java 改动；缺点两次调用、trace 链割裂）。**评审已定：采用聚合单一 `validate-nested` 端点**（聚合 + 一次出 trace_id，性价比更高），V2.0 以 `/api/v1/appstore/validate-nested` 落地。

---

## 3.2 推理过程可见性：复用 Java explain/provenance

### 3.2.1 设计原则（对齐四层架构 + SKILL.md 纪律）
- **用户可见层**：只给结论 + 关键依据 + 需人工确认项（延续纪律7"中间推理收敛"，不暴露碎碎念）。
- **可追溯层**：推理每一步落 `trace_id`，Java 侧 `config/trace` 逐字节回放（可回归，纪律1）。
- **规则透明层**：每条判定带 `rule_id`，Java `config/explain` 可按 audience（business/technical）解释，`config/provenance/{field}` 用 PROV-O `derivedFrom` 回答"这个字段默认值为什么是 X"。

### 3.2.2 接线
- `validate-nested` 出参 `trace_id` 落盘（随 `plan_json_v2` 工件，不入 render_table，纪律12）。
- 需要向用户/业务解释时，走网关 `POST /api/v1/appstore/explain`（按 `--audience business|technical`/`--field <路径>` 取数；内部对应 `config/explain` + `config/provenance/{field}`）。出参**逐字引用不加工**（纪律1）。

### 3.2.3 与现有纪律的边界
| 纪律 | 影响 |
|---|---|
| 纪律7 中间推理收敛 | 顶层只给结论+依据；explain/provenance 按需取，不主动倾倒 |
| 纪律1 逐字引用不加工 | explain/provenance 出参逐字引用 |
| 纪律12 禁止手工渲染 | trace/explain 不入 render_table，只经脚本取数 |
| 价格禁推理 | 价格类字段不进 validate-nested 的"补全/推理"分支，仍走 `pending_required`（Java 侧 `isPrice` 语义对齐） |

---

## 3.3 与现有工具/契约的关系（不引入重复职责）

| 现有 | 职责 | 关系 |
|---|---|---|
| `merge_nested` | ⑤ 骨架对位合并 + pending_required | 不变，作为 ⑤.5 入参 |
| `validate_elements` | ④ 提取质量门禁（vs schema 叶子） | 不变，前置闸（先于 merge） |
| **⑤.5 validate-nested（新）** | 嵌套跨字段/合规/归一判定（Java 推理） | 补 merge 未覆盖的一致性维度 |
| `render_table` | ⑥ 渲染 | 不变 |
| `derive_flat24` | ⑦ 下游投影 | 不变 |
| 旧 `ontology_reason` | flow-A 之外旧轨工具（@deprecated） | 过渡保留，新链路不再依赖；Java 侧 `FieldOntologyService` 与新闸并存 |

---

## 3.4 需要配套修改的文档/契约

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

## 3.5 验证与回归

- Java 侧：`validate-nested` 对 6 模板各取 1 个 K5 存量报文做回归（`config/regression/run` 可参照）。
- CPCP 侧：`validate_nested` 对模板轨 `payload` 逐字节幂等（同输入同输出），`explain_nested` 出参逐字稳定。
- 断言：价格类字段路径永不进入"补全/推理"（对齐 `pending_required`），不引入违反 SKILL.md 纪律 11/12 的行为。

---

## 备注：本设计的演进脉络
- 早期曾从零提出"JSON 本体/自行设计 TTL"，经确认 Java 工程已实装成熟本体平台后**废弃造轮子路径**，改为"对接 + 新增校验闸 + 复用可见性"。这也印证评审的原则：**优先使用项目中已有最新技术栈（去旧留新）**。

---

# 第 4 章 存量产品实例化逻辑报文生成方案

> **V2.0 履历**：报文落位定为 `knowledge/K5存量报文/`（原 references/K5 及"待定"口径废弃）；
> `merge_nested` 由技能包脚本子命令调整为工作流 `type=6` 代码节点 `wf_sub_01`（CODE_MERGE_NESTED），
> 配套后端校验走 `/api/v1/appstore/validate-nested` 等适配端点（AppStoreV16Controller）。
> 知识库与数据资产已随 V2.0 迁至 `knowledge/`。数据与规则口径（V1.0）保持不变。

> 目标：为 18 个存量产品（17 active + 1 dup）各生成一份**按逻辑模型模板实例化的 JSON 报文**，
> 作为：① 相似产品库（第②步相似查询的检索语料 + 第⑤步 AI补全取值来源）；
> ② 存量数据结构化资产（替代/补充 K4存量 md 自然语言形态）；
> ③ 新需求配置时的对照基准（测试预期值 presetValue 人工核对基准）。

---

## 4.1 现状与缺口

| 资产 | 形态 | 缺口 |
| :--- | :--- | :--- |
| K4存量 18 个 md | 自然语言正文（分节） | 非结构化，机器不可对位 |
| 存量产品目录_清洗后.json | 元数据（offer_id/模板/成员/档位） | 无配置值 |
| templates/ 6 个 schema | 模板骨架（叶子+枚举+show-when） | 无存量实例 |
| _sample_*.json 2 个 | xlsx 虚构样例（"测试资费"） | 不是存量产品 |

## 4.2 生成流程（复用六步流程，反向自举）

```
K4存量 md 正文（自然语言）
   │
   ▼
④ LLM 提取（提示词：模板 schema 注入 + 正文 → 配置要素 JSON，嵌套形态）
   │    ── 输出 _extract.json（中间产物，留档审计）
   ▼
⑤ merge_nested 合并（确定性，工作流 type=6 代码节点 wf_sub_01 / CODE_MERGE_NESTED）
   │    入参：--schema-file <模板> --elements-json <④产出> --offer-json {}（无相似品，纯提取值）
   │    规则：提取有值→原始需求(来源标注改"存量提取")；价格字段正常提取（存量品价格是事实数据，
   │           不受"禁止照搬"约束——该约束只针对跨品照搬）；default 兜底；必填缺失→pending 清单
   │    后端校验走 /api/v1/appstore/validate-nested 等适配端点（AppStoreV16Controller）
   ▼
实例化报文 <offer_id>.json（+ _meta 溯源 + pending_required）
   │
   ▼
⑥ render_table.py 渲染层级表格 → 人工/规则双通道校验 → 入库
```

### 4.2.1 与第⑤步现有 merge_nested 代码节点的差异点

| 项 | 新需求流程（现状） | 存量实例化（本方案） |
| :--- | :--- | :--- |
| offer 输入 | 相似产品报文（AI补全） | **无**（传 `{}`） |
| 价格字段 | 禁止照搬，留空 | **正常提取**（事实数据，非照搬） |
| source 标注 | 原始需求/AI补全/默认值 | 存量提取/默认值（新增一种标注） |
| 骨架裁剪 | 保留全部叶子 | 保留全部叶子（值可为 ""，保持与模板同构，便于相似对位） |

实现方式：`merge_nested` 代码节点（wf_sub_01）增加 `--mode legacy`（存量实例化）参数，仅改 source 标签与价格豁免两处分支，**不新建脚本**（向后兼容：默认 mode 行为零变化）。

## 4.3 质量校验（三道闸）

1. **规则闸（代码化，自动）**：
   - 枚举字段：提取值 ∈ schema enum（不在 → 报警列异常清单，不改写）
   - number 字段：提取值可转数值（"199元/月"类文本值进异常清单，人工定格式口径）
   - 必填缺失率：单品 pending > 30% 判定"提取质量差"，该品提取结果打回重跑（只重跑 LLM，不影响其他品）
   - 价格一致性：baseInfo 无档位字段，price 落在 optionalInfo（prcMonthFee/fixFee），与目录 `tier`（如"199元"）交叉核对，不一致进异常清单
2. **渲染闸（代码化，自动）**：render_table 渲染成功 + 无渲染异常 = 结构合法性
3. **人工闸（抽样）**：每模板抽 1 个品（共 6 个，或至少覆盖 3 个在用模板），对照 K4 md 正文逐字段核对；dup 记录（900102306）不实例化，其 JSON 由 active 版（900113046）复制并加 `"x-dup-of": "900113046"` 标记

## 4.4 产出物

```
knowledge/K5存量报文/
  <offer_id>.json          18 份（17 生成 + 1 dup 复制标记）——嵌套报文，与模板同构
  <offer_id>.md            18 份渲染表格（第⑥步产物，业务可读，人工核对用）
  _extract/<offer_id>.json 18 份第④步中间产物（审计留档）
  _report.json             汇总：每品 pending 数/异常清单/校验结论
```

命名对齐 K4存量 惯例（offer_id 主键），目录落位定为 `knowledge/K5存量报文/`（与 K4存量 并列，属 `knowledge/` 知识库与数据资产；skills references 形态废弃）。

## 4.5 执行顺序（分批，先小后大）

| 批次 | 产品 | 模板 | 数量 | 目的 |
| :--- | :--- | :--- | :--- | :--- |
| P0 试产 | 900113046（融合199） | familyBasePrc | 1 | 打通全链路，校准提取提示词 |
| P1 | 900102308（单品199） | personMainPrc | 1 | 第二模板验证，price/档位口径确认 |
| P2 | 900117020（权益19.9生活版） | personAddPrc | 1 | 附加资费模板验证 |
| P3 | 其余 14 个 active | 全部 | 14 | 批量跑 + 异常清单集中处理 |

每批结束：异常清单归零（人工确认格式口径）→ 下一批。P0~P2 即可验证本方案，**评审通过后从 P0 开始**。

## 4.6 待确认问题（实现偏差风险点）

1. **提取责任**：第④步 LLM 提取提示词尚未落地——本方案 P0 需要先写提示词（模板 schema 注入 + 分节映射规则），还是先用规则/人工做 P0 提取？
2. **number 字段值格式**："199元/月"、"0.29元/MB" 这类文本值：转纯数值（口径=月），还是保留原文？schema 已把 prcMonthFee 等转 number，倾向**转数值 + 单位剥离规则代码化**，但需业务确认。
3. **正文无对应值**：K4 md 分节与模板叶子不是一一覆盖（如"信控/欠费停机"细节在模板中无叶子）——**不扩模板**，未覆盖信息只留在 md（模板是报文规范，不是资料库），确认？
4. **dup 处理**：900102306 只复制标记不单独提取，确认？
5. **目录落位**：已定为 `knowledge/K5存量报文/`（原"方案目录还是 skill references（K5）"待定已解决），供后续第②步相似查询读取。

---

# 第 5 章 存量产品数据清洗规则

> **V2.0 履历**：清洗产物与数据资产已迁至 `knowledge/`（`存量产品目录_清洗后.json` 位于 `knowledge/`）；C15 价格纪律引用随 V2.0 调整为「模板轨 merge_nested 价格纪律」。数据规则本身（V1.0）保持不变。

> 数据源：`方案/产品信息.txt`（18 条原始记录）
> 目标：把自然语言规格文本清洗成**规整目录**（`存量产品目录_清洗后.json`），
> 同时识别**疑似重复/版本差异**，为后续「每品实例化逻辑报文 + 配置模板 + AI 衍生」打底。
> 清洗规则须**可重复执行、可审计**：任何清洗动作都有明确规则依据，不靠人工拍脑袋。

## 5.1 字符/格式清洗（低级规则）

| # | 规则 | 说明 |
| --- | --- | --- |
| C1 | 按 `\t` 拆列，严格 3 列 | ID、名称、正文；列数不足视为脏行，报错不静默 |
| C2 | 去空白/全角空格 | 正文首尾、名称内部多余空格剔除 |
| C3 | 正文编码统一 UTF-8 | 源文件已 UTF-8，保留；乱码行报警 |
| C4 | 名称中的多余空格/括弧统一 | 如"5G-A 套餐"→"5G-A套餐" |

## 5.2 业务口径清洗（关键规则）

| # | 规则 | 说明 |
| --- | --- | --- |
| C5 | **命名归一**：产品类型三态 | 由正文特征判定 `product_type ∈ {单品套餐, 融合套餐, 权益包}`，`name_clean` 统一为 `{系列}{档位}元{（单品|融合）?}`——名称带"融合"→融合；名称不带但正文含成员（宽带/天翼高清/副卡独立表述）→融合；否则单品/权益包 |
| C6 | **系列归集** | `biz_series`：5G-A 主套餐 / 权益随心选（后续可按模板类别扩展） |
| C7 | **档位抽取** | `tier`：正则 `(\d+(?:\.\d+)?)元` 从名称抽取（如 199元 / 19.9元） |
| C8 | **融合成员识别**（仅融合） | 正则检测正文成员关键词：宽带/天翼高清/副卡；`members` 列出角色清单 |
| C9 | **模板映射** | 映射到 xlsx 逻辑模型模板编码：单品主套餐→`personMainPrc`(待 xlsx 主资费模板编码核对)、融合主套餐→`familyBasePrc`、权益包→`personAddPrc`；映射关系固定，`template` 字段可追溯 |
| C10 | **正文版本识别** | 检测通知文号：含"业资调度〔2025〕22号"→`desc_version=22号文`；含"业资调度〔2025〕6号"或"新增5G-A 套餐相关销售品"→`desc_version=6号文`；用于识别新旧双版本 |

## 5.3 去重/版本规则（本次发现的重点）

| # | 规则 | 说明 |
| --- | --- | --- |
| C11 | **同名去重判定** | `name_clean` 相同的记录 → 归为同一商品族；族内若正文版本（C10）与详略（正文长度差异≥10%且版本不同）不同 → 标记 `dup_group` |
| C12 | **规范记录选择** | 同一商品族内，优先选 **22号文（新、更全，含停机规则/同步属性）** 记录为 `status=active`，另一条 `status=dup`、`duplicate_of=active条ID` |
| C13 | **重复不删除、只标记** | 保留原始记录（`status=dup`），避免丢失信息；下游消费时按 `status=active` 取规范记录 |

> 本次发现：`900113046` 与 `900102306` 均为"5G-A融合套餐199元"，版本不同（22号文/6号文）→ 同一商品族，选 `900113046`(22号文,更全) 为 active，`900102306` 为 dup。

## 5.4 语义边界规则（供下游字段提取复用）

| # | 规则 | 说明 |
| --- | --- | --- |
| C14 | **卫星短信 ≠ 套餐内短信** | 正文"卫星权益：X分钟、X条短信"的"短信"是**卫星短信**，字段提取时不得并入套餐内"短信"字段（见会话已确认问题） |
| C15 | **价格类字段禁照搬** | 清洗后的目录仅作**相似匹配/参考**，套餐档位等价格字段**禁止**作为同名字段照搬到新需求（沿用模板轨 merge_nested 价格纪律） |

## 5.5 产出物

1. `knowledge/存量产品目录_清洗后.json`（本规则机器化输出，含上述全部字段）
2. 清洗报告（统计：总条数/去重数/版本数/每系列数）

## 5.6 后续（未在本任务内，属 AI 衍生阶段）

- AI 衍生：扩展档位/补齐缺失模板类别（宽带主资费/家庭附加业务/个人附加资费等）
- 每品实例化逻辑报文 + 对应配置模板 → 喂 `similar_offer` 相似匹配与模板驱动配置
