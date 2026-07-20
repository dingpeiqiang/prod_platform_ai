# 融合推理智能体平台设计文档

> 版本：v2.0 | 更新日期：2026-07-10

---

## 目录

1. [概述](#1-概述)
2. [平台 API 能力](#2-平台-api-能力)
3. [工作流编排示例](#3-工作流编排示例)
4. [对本体平台的能力需求](#4-对本体平台的能力需求)

---

## 1. 概述

### 1.1 平台定位

融合推理智能体平台是一个基于 **LLM + 规则引擎 + 知识图谱** 的决策推理平台。它将大语言模型的生成能力、业务规则引擎的确定性校验、本体知识图谱的结构化存储统一封装为可编排的工作流节点，供上层智能体调用。

**核心价值**：
- **LLM 负责生成**：候选方案、自然语言解释、SPARQL 查询生成
- **规则引擎负责校验**：Drools 执行确定性业务规则，不可被 LLM 绕过
- **本体负责存储**：Jena/RDF4J 存储结构化实体知识，支持 SPARQL 查询

### 1.2 架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                        智能体应用层                                  │
│   营销推荐 Agent / 计费争议 Agent / 其他业务 Agent                    │
└────────────────────────────────┬────────────────────────────────────┘
                                 │ 调用 SDK
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    融合推理引擎 SDK                                   │
│                  (IntegrativeReasonEngine)                          │
│                                                                     │
│   ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────┐ │
│   │ retrieve_    │ │ evaluate_    │ │ compare_     │ │ explain  │ │
│   │ facts        │ │ policy       │ │ state        │ │          │ │
│   └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └────┬─────┘ │
└──────────┼────────────────┼────────────────┼───────────────┼───────┘
           │                │                │               │
           ▼                ▼                ▼               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     后端服务 (ontoSrv)                               │
│   ┌──────────────┐ ┌──────────────┐ ┌──────────────┐               │
│   │ Jena/RDF4J   │ │ Drools       │ │ Redis        │               │
│   │ 本体存储     │ │ 规则引擎     │ │ 快照/审计    │               │
│   └──────────────┘ └──────────────┘ └──────────────┘               │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.3 核心概念

| 概念 | 说明 |
|------|------|
| **实体 (Entity)** | 本体中的实例，如 Customer、Order，通过 URI 标识 |
| **事实 (Facts)** | 实体的属性键值对，如 `{vipLevel: "Gold", annualSpend: 80000}` |
| **快照 (Snapshot)** | 某次事实检索的结果，存入 Redis，支持假设推理 |
| **策略集 (Policy Set)** | 一组 Drools 规则，通过 `policy_set_id` 标识 |
| **裁决 (Decision)** | 规则引擎的评估结果：`allow` / `deny` / `review` |
| **追踪 (Trace)** | 一次完整推理的审计日志，贯穿所有 SDK 调用 |

### 1.4 设计原则

| 原则 | 说明 |
|------|------|
| **本体不可被 LLM 说服** | LLM 只能查询本体，不能改写事实 |
| **规则可配置但不可绕过** | 业务规则有版本、可热更新，评估结果不可被 LLM 覆盖 |
| **假设只在执行上下文存在** | 假设推理不修改本体，只在临时快照或 Named Graph 上操作 |
| **一切结论可追溯** | 每次推理自动写入审计日志，支持事后追溯 |

---

## 2. 平台 API 能力

> 核心类：`IntegrativeReasonEngine`（`sdk/reasoning_engine.py`）

### 2.1 API 总览

平台对外提供以下核心能力：

| API | 方法名 | 功能 | 典型场景 |
|-----|--------|------|----------|
| **事实检索** | `retrieve_facts` | 从本体查询实体画像 | 获取用户等级、消费记录 |
| **规则评估** | `evaluate_policy` | 执行 Drools 规则校验 | 校验用户是否满足升级条件 |
| **合并评估** | `evaluate_policy_with_facts` | 一步完成事实检索+规则评估 | 快速校验场景 |
| **SWRL推理** | `evaluate_swrl` | 执行 SWRL 规则推理 | 本体规则推导新事实 |
| **SHACL验证** | `validate_shacl` | SHACL 数据形状验证 | 数据合规性检查 |
| **假设推理(内存)** | `compare_state` | 基于快照做假设推演 | 多方案对比选优 |
| **假设推理(本体)** | `hypothetical_evaluate` | 在本体中创建临时图做推演 | 深度 what-if 分析 |
| **解释生成** | `explain` | 从审计日志生成自然语言解释 | 向用户解释决策原因 |
| **审计查询** | `get_trace` | 获取完整审计日志 | 问题追溯、合规审计 |
| **自然语言查询** | `nl_query` | NL → SPARQL → NL | 自然语言查询本体数据 |
| **实体发现** | `nl_discover_and_retrieve` | NL 查询发现实体并获取画像 | "找出高价值客户" |
| **快捷评估** | `quick_evaluate` | retrieve_facts + evaluate_policy | 简化调用 |

### 2.2 事实检索 (retrieve_facts)

**功能**：从 Jena 本体库查询指定实体的事实数据，存入 Redis 快照。

**方法签名**：
```python
async def retrieve_facts(self, req: RetrieveFactsRequest) -> RetrieveFactsResponse
```

**输入参数** (`RetrieveFactsRequest`)：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `entities` | `List[EntityRef]` | ✅ | 要查询的实体引用列表，支持同时查询多个实体 |
| `entities[].id` | `str` | ✅ | 实体 ID。可以是短名称（如 `"Customer_Li"`）或完整 URI（如 `"http://example.org/Customer_Li"`）。短名称会自动补全为 `http://example.org/{id}` |
| `entities[].type` | `str` | ✅ | 实体类型，如 `"Customer"`、`"Account"`、`"Invoice"`、`"Payment"`。用于后端确定查询的本体类 |
| `entities[].source` | `str` | ❌ | 数据来源，默认 `"ontology"`。可选值：`"ontology"`（本体库）、`"crm"`（CRM系统）、`"cmdb"`（配置管理库） |
| `intent` | `dict` | ❌ | 查询意图，控制返回哪些属性。`scope` 字段可选值：`"profile"`（用户画像，默认）、`"billing_profile"`（账单画像）、`"full"`（全部属性） |
| `trace_context` | `TraceContext` | ✅ | 追踪上下文。包含 `trace_id`（可选，不传自动生成 UUID）、`tenant_id`（租户标识，默认 `"marketing_tenant"`）、`timestamp`（时间戳） |

**输出结果** (`RetrieveFactsResponse`)：

| 字段 | 类型 | 说明 |
|------|------|------|
| `snapshot_id` | `str` | Redis 快照 ID（格式：`snap_{8位hex}_{时间戳}`），用于后续 `compare_state` 假设推理。**注意：快照 TTL 为 1 小时，过期后无法使用** |
| `facts_map` | `Dict[str, FactSet]` | 实体 ID → 事实键值对的映射。key 为实体 URI（如 `"http://example.org/Customer_Li"`），value 为 `FactSet`（可通过 `.root` 获取 `dict`） |

**副作用**：
- Redis 写入快照：`snapshot:{snapshot_id}` → JSON，TTL 3600秒
- Redis 写入审计日志：`audit:{trace_id}` → JSON 数组，追加一条记录

**快照数据结构**：

快照 ID 格式：`snap_{8位uuid}_{时间戳}`，如 `snap_a1b2c3d4_1234567890`

```json
{
  "_meta": {
    "trace_id": "uuid...",
    "tenant_id": "default_tenant",
    "created_at": 1234567890.123,
    "ttl": 3600,
    "entity_refs": ["Customer_Li"]
  },
  "facts": {
    "http://example.org/Customer_Li": {
      "vipLevel": "Gold",
      "annualSpend": 80000,
      "memberYears": 3
    }
  }
}
```

快照用于 `compare_state` 假设推理，TTL 1 小时后自动过期。

**审计日志条目结构**：

```json
{
  "step": "fact.retrieve",
  "timestamp": 1234567890.123,
  "snapshot_id": "snap_a1b2c3d4_1234567890",
  "entities": ["Customer_Li"]
}
```

**调用示例**：
```python
# 示例1：营销场景 - 查询单个客户画像
req = RetrieveFactsRequest(
    entities=[EntityRef(id="Customer_Li", type="Customer")],
    intent={"scope": "profile"},
    trace_context=TraceContext(tenant_id="marketing_tenant")
)
resp = await engine.retrieve_facts(req)
# resp.snapshot_id = "snap_a1b2c3d4_1234567890"
# resp.facts_map["http://example.org/Customer_Li"].root = {"vipLevel": "Gold", "annualSpend": 80000, "memberYears": 3}

# 示例2：计费场景 - 查询多个实体（账户+发票+支付）
req = RetrieveFactsRequest(
    entities=[
        EntityRef(id="Account_001", type="Account"),
        EntityRef(id="Invoice_001", type="Invoice"),
        EntityRef(id="Payment_001", type="Payment"),
    ],
    intent={"scope": "billing_profile"},
    trace_context=TraceContext()
)
resp = await engine.retrieve_facts(req)
# resp.facts_map 包含三个实体的事实数据
```

---

### 2.3 规则评估 (evaluate_policy)

**功能**：将实体事实传入 Drools 规则引擎，按指定策略集评估，返回裁决结果。

**方法签名**：
```python
async def evaluate_policy(self, req: EvaluatePolicyRequest) -> EvaluatePolicyResponse
```

**输入参数** (`EvaluatePolicyRequest`)：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `facts` | `FactSet` | ✅ | 实体事实键值对。`FactSet` 是 Pydantic RootModel，通过 `FactSet(root={"key": "value"})` 构造，通过 `.root` 获取底层 `dict` |
| `context` | `dict` | ✅ | 评估上下文，必须包含 `policy_set_id`。可选字段：`expectation_type`（`"validation"` 表示校验用户本身，`"candidate_check"` 表示校验候选方案） |
| `context.policy_set_id` | `str` | ✅ | 策略集 ID，如 `"PS_MARKETING_RECOMMEND_V1"`、`"PS_BILLING_REFUND_V1"`。规则引擎根据此 ID 加载对应的 Drools 规则文件 |
| `context.expectation_type` | `str` | ❌ | 评估类型：`"validation"`（校验用户/账户本身是否满足基础条件）或 `"candidate_check"`（校验候选方案是否合规）。用于审计日志区分不同用途的调用 |
| `trace_context` | `TraceContext` | ✅ | 追踪上下文，同 `retrieve_facts` |

**输出结果** (`EvaluatePolicyResponse`)：

| 字段 | 类型 | 说明 |
|------|------|------|
| `decision` | `DecisionResult` | 裁决结果对象 |
| `decision.verdict` | `str` | 裁决结论：`"allow"`（通过）、`"deny"`（拒绝）、`"review"`（需人工审核）、`"rank"`（排名评分） |
| `decision.confidence` | `float` | 置信度，0.0-1.0，默认 1.0 |
| `decision.triggered_rules` | `List[str]` | 触发的规则 ID 列表，如 `["R001", "R003"]`。用于审计追溯哪些规则参与了决策 |
| `decision.reason` | `str` | 裁决理由的自然语言描述，如 `"用户等级 Gold 且年消费 >= 5万，满足升级条件"` |
| `decision.metrics` | `List[EvaluationMetric]` | 评估指标列表（可选），每项包含 `name`、`value`、`level`（pass/warn/fail） |
| `decision.candidate_index` | `int` | 候选方案索引（可选），在校验多个候选方案时用于标识是哪个方案的结果 |

**副作用**：自动写入审计日志，记录 `policy_set_id`、`verdict`、`triggered_rules`

**调用示例**：
```python
# 示例1：校验用户本身是否满足基础条件
req = EvaluatePolicyRequest(
    facts=FactSet(root={"vipLevel": "Gold", "annualSpend": 80000, "creditScore": 750}),
    context={"policy_set_id": "PS_MARKETING_RECOMMEND_V1", "expectation_type": "validation"},
    trace_context=TraceContext()
)
resp = await engine.evaluate_policy(req)
# resp.decision.verdict = "allow"
# resp.decision.triggered_rules = ["R001"]
# resp.decision.reason = "用户等级 Gold 且年消费 >= 5万"

# 示例2：校验候选方案是否合规（营销场景）
req = EvaluatePolicyRequest(
    facts=FactSet(root={
        "vipLevel": "Gold",
        "annualSpend": 80000,
        "candidateAction": "升级为铂金卡会员",      # LLM 生成的候选方案
        "candidateActionType": "premium_upgrade"    # 方案分类（规则引擎用于匹配）
    }),
    context={"policy_set_id": "PS_MARKETING_RECOMMEND_V1", "expectation_type": "candidate_check"},
    trace_context=TraceContext()
)

# 示例3：校验账单方案是否合规（计费场景）
req = EvaluatePolicyRequest(
    facts=FactSet(root={
        "accountStatus": "active",
        "outstandingBalance": 5000,
        "billingAction": "全额退款至原支付方式",     # 候选账单方案
        "billingActionType": "full_refund",          # 方案分类
        "caseType": "refund"                         # 案例类型
    }),
    context={"policy_set_id": "PS_BILLING_REFUND_V1", "expectation_type": "candidate_check"},
    trace_context=TraceContext()
)
```

---

### 2.4 合并评估 (evaluate_policy_with_facts)

**功能**：一步完成事实检索 + 规则评估，无需分步调用。将 `RetrieveFactsRequest` 直接发送到 Java 后端，由后端内部完成 Jena 查询 + Drools 评估的全流程。

**方法签名**：
```python
async def evaluate_policy_with_facts(
    self, req: RetrieveFactsRequest, policy_set_id: str
) -> EvaluatePolicyResponse
```

**输入参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `req` | `RetrieveFactsRequest` | ✅ | 同 `retrieve_facts` 的请求，包含 `entities`、`intent`、`trace_context` |
| `policy_set_id` | `str` | ✅ | 策略集 ID |

**输出结果** (`EvaluatePolicyResponse`)：同 `evaluate_policy`，包含 `decision` 对象。

**内部流程**：
1. 将 `entities` 和 `intent` 发送到 Java 后端 `/api/v1/evaluate`
2. Java 后端内部：调用 Jena 查询事实 → 调用 Drools 评估规则
3. 返回裁决结果

**与分步调用的区别**：

| 方式 | 调用 | 网络请求 | 是否生成快照 |
|------|------|----------|-------------|
| 分步调用 | `retrieve_facts` + `evaluate_policy` | 2 次 | 是（可后续用 `compare_state`） |
| 合并调用 | `evaluate_policy_with_facts` | 1 次 | 否（无法后续假设推理） |

**适用场景**：只需要判断用户是否满足条件，不需要快照做后续假设推理。

**调用示例**：
```python
req = RetrieveFactsRequest(
    entities=[EntityRef(id="Customer_Li", type="Customer")],
    intent={"scope": "profile"},
    trace_context=TraceContext(tenant_id="marketing_tenant")
)
resp = await engine.evaluate_policy_with_facts(req, "PS_MARKETING_RECOMMEND_V1")
# resp.decision.verdict = "allow"
# resp.decision.triggered_rules = ["R001", "R003"]
```

---

### 2.5 SWRL 规则推理 (evaluate_swrl)

**功能**：调用 SWRL 引擎执行语义网规则推理，从已知事实推导出新事实。

**方法签名**：
```python
async def evaluate_swrl(self, req: EvaluateSWRLRequest) -> EvaluateSWRLResponse
```

**输入参数** (`EvaluateSWRLRequest`)：

| 字段 | 类型 | 必填 | 说明                |
|------|------|------|-------------------|
| `facts` | `FactSet` | ✅ | 事实数据（键值对）         |
| `rule_refs` | `List[SWRLRuleRef]` | ❌ | 指定规则列表（模式1）       |
| `rule_refs[].rule_id` | `str` | ✅ | 规则编号/名称（使用模式1时必填） |
| `rule_refs[].module` | `str` | ❌ | 规则模块分组            |
| `rule_module` | `str` | ❌ | 指定规则模块（模式2）       |
| `trace_context` | `TraceContext` | ✅ | 追踪上下文             |

**三种调用模式**：

| 模式 | 参数 | 说明 |
|------|------|------|
| 指定规则 | `rule_refs=[...]` | 仅使用指定的具体规则 |
| 指定模块 | `rule_module="xxx"` | 平台自动选择该模块内的所有规则 |
| 默认规则 | 都不指定 | 使用平台默认规则集 |

**输出结果** (`EvaluateSWRLResponse`)：

| 字段 | 类型 | 说明 |
|------|------|------|
| `results` | `List[SWRLResult]` | 每条规则的推理结果 |
| `results[].rule_id` | `str` | 规则 ID，用于关联 `rules` 中的规则定义 |
| `results[].fired` | `bool` | 规则是否触发 |
| `results[].conclusions` | `List[dict]` | 推出的新事实，如 `[{"predicate": "individualHasPainPoint", "object": "PainPoint_Security"}]` |
| `results[].bindings` | `dict` | 变量绑定，如 `{"ind": "Customer_Li", "fam": "Family_001"}` |
| `fired_rule_ids` | `List[str]` | 触发的规则 ID 列表 |
| `rules` | `List[Object]` | 规则定义列表（包含推理链，用于解释规则逻辑） |
| `rules[].rule_id` | `str` | 规则 ID，用于关联 `results` 中的执行结果 |
| `rules[].label` | `str` | 规则标签，如 `"家庭有老人小孩无智能安防推导智家安防痛点"` |
| `rules[].comment` | `str` | 规则描述 |
| `rules[].body` | `List[Object]` | 规则体（前件/条件列表） |
| `rules[].body[].type` | `str` | 原子类型：`ClassAtom`、`IndPropAtom`、`DataPropAtom`、`BuiltinAtom` |
| `rules[].body[].predicate` | `str` | 谓词名称，如 `"个人画像"`、`"个人客户归属于家庭中"` |
| `rules[].body[].predicate_code` | `str` | 谓词编码，如 `"ind"`、`"hasFamily"` |
| `rules[].body[].text` | `str` | 人类可读描述，如 `"「个人客户归属于家庭中」: ?ind → ?fam"` |
| `rules[].head` | `List[Object]` | 规则头（后件/结论列表），结构同 `body` |

**`body`/`head` 原子类型说明**：

| type | 说明 | 额外字段 |
|------|------|----------|
| `ClassAtom` | 类断言，判断个体是否属于某类 | `variable` |
| `IndPropAtom` | 对象属性断言 | `subject`, `object` |
| `DataPropAtom` | 数据属性断言 | `subject`, `value_var` |
| `BuiltinAtom` | 内置函数（比较等） | `operator`, `operator_cn`, `left`, `right` |

**输出示例**（简化）：
```json
{
  "results": [
    {
      "ruleId": "RULE_001",
      "fired": true,
      "conclusions": [{"predicate": "individualHasPainPoint", "object": "PainPoint_Security"}],
      "bindings": {"ind": "Customer_Li", "fam": "Family_001"}
    }
  ],
  "fired_rule_ids": ["RULE_001"],
  "rules": [
    {
      "rule_id": "RULE_001",
      "label": "家庭有老人小孩无智能安防推导智家安防痛点",
      "comment": "个人客户归属的家庭有老人小孩无智能安防时，推理该个人客户隐含智家安防痛点",
      "body": [
        {"type": "ClassAtom", "predicate": "个人画像", "predicate_code": "ind", "text": "目标属于「个人画像」"},
        {"type": "IndPropAtom", "predicate": "个人客户归属于家庭中", "predicate_code": "hasFamily", "subject": "ind", "object": "fam", "text": "「个人客户归属于家庭中」: ?ind → ?fam"}
      ],
      "head": [
        {"type": "IndPropAtom", "predicate": "个人客户存在客户痛点", "predicate_code": "individualHasPainPoint", "subject": "ind", "object": "pain", "text": "「个人客户存在客户痛点」: ?ind → ?pain"}
      ]
    }
  ]
}
```

**副作用**：自动写入审计日志

**调用示例**：
```python
# 模式1：指定规则
req = EvaluateSWRLRequest(
    facts=FactSet(root={"vipLevel": "Gold", "annualSpend": 80000}),
    rule_refs=[
        SWRLRuleRef(rule_id="SWRL_001"),
        SWRLRuleRef(rule_id="SWRL_002")
    ],
    trace_context=TraceContext()
)

# 模式2：指定模块
req = EvaluateSWRLRequest(
    facts=FactSet(root={"vipLevel": "Gold", "annualSpend": 80000}),
    rule_module="marketing_rules",
    trace_context=TraceContext()
)

resp = await engine.evaluate_swrl(req)
# resp.fired_rule_ids = ["SWRL_001"]
# resp.results[0].conclusions = [{"predicate": "eligibleForUpgrade", "object": "Platinum"}]
```

**与 Drools 规则评估的区别**：

| 维度 | evaluate_policy (Drools) | evaluate_swrl (SWRL) |
|------|--------------------------|----------------------|
| 规则类型 | 业务规则（if-then 决策） | 语义网规则（本体推理） |
| 输出 | 裁决结果 (allow/deny) | 新推导的事实 |
| 典型用途 | 合规校验、权限判断 | 属性推导、分类推理 |

---

### 2.6 SHACL 数据验证 (validate_shacl)

**功能**：使用 SHACL (Shapes Constraint Language) 验证数据是否符合预定义的形状约束。

**方法签名**：
```python
async def validate_shacl(self, req: ShaclValidationRequest) -> ShaclValidationResponse
```

**输入参数** (`ShaclValidationRequest`)：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `data` | `dict` | ✅ | 要验证的数据 |
| `shapes` | `str` | ❌ | 指定 shape 名称（不指定则使用默认） |
| `tenant_id` | `str` | ✅ | 租户 ID |

**输出结果** (`ShaclValidationResponse`)：

| 字段 | 类型 | 说明 |
|------|------|------|
| `conforms` | `bool` | 数据是否符合约束 |
| `results` | `List[ShaclViolation]` | 违规列表（conforms=false 时） |
| `results[].severity` | `str` | 严重级别：`violation` / `warning` / `info` |
| `results[].path` | `str` | 违规属性路径 |
| `results[].message` | `str` | 违规描述信息 |
| `results[].source_shape` | `str` | 触发违规的 shape 名称 |

**调用示例**：
```python
req = ShaclValidationRequest(
    data={
        "id": "Customer_Li",
        "vipLevel": "Gold",
        "annualSpend": 80000,
        "email": "invalid-email"  # 假设格式不合规
    },
    shapes="CustomerShape",
    tenant_id="marketing_tenant"
)
resp = await engine.validate_shacl(req)
# resp.conforms = False
# resp.results = [ShaclViolation(severity="violation", path="email", message="邮箱格式不正确", ...)]
```

**典型用途**：
- 数据入库前的格式校验
- 实体数据完整性检查
- 多租户数据合规性验证

---

### 2.7 假设推理 - 内存版 (compare_state)

**功能**：基于已有的事实快照，在内存中应用补丁（patch），重新执行规则评估。用于多方案对比，选出最优方案。

**方法签名**：
```python
async def compare_state(self, req: CompareStateRequest) -> CompareStateResponse
```

**输入参数** (`CompareStateRequest`)：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `base_snapshot_id` | `str` | ✅ | 基准快照 ID，来自 `retrieve_facts` 返回的 `snapshot_id`。**注意：快照 TTL 1 小时，过期后会报错 "Snapshot not found or expired"** |
| `patches` | `List[StateChangePatch]` | ✅ | 假设变更列表，每个 patch 表示一种"如果改成这样会怎样"的假设场景 |
| `patches[].target_entity` | `EntityRef` | ✅ | 目标实体，必须是快照中已存在的实体 |
| `patches[].changes` | `FactSet` | ✅ | 要变更的属性键值对。只会覆盖指定的属性，其他属性保持不变 |
| `patches[].description` | `str` | ❌ | 变更描述，用于结果展示，如 `"升级为铂金卡会员"` |
| `evaluation_metrics` | `List[str]` | ❌ | 评估指标，如 `["compliance_status"]`。当前主要用于标记 |
| `policy_set_id` | `str` | ✅ | 策略集 ID，用于调用 Drools 规则评估变更后的状态 |
| `trace_context` | `TraceContext` | ✅ | 追踪上下文 |

**输出结果** (`CompareStateResponse`)：

| 字段 | 类型 | 说明 |
|------|------|------|
| `comparisons` | `List[dict]` | 每个 patch 的评估结果，顺序与输入 patches 对应 |
| `comparisons[].patch_description` | `str` | 补丁描述（来自输入的 `description`） |
| `comparisons[].resulting_state` | `dict` | 应用补丁后的完整事实状态（包含所有实体的所有属性） |
| `comparisons[].evaluation` | `dict` | Drools 规则评估结果，包含 `verdict`、`triggered_rules`、`reason` |

**副作用**：无（不修改本体，不写 Redis 快照）

**调用示例**：
```python
# 场景：有两个合规的候选方案，需要对比选出最优
req = CompareStateRequest(
    base_snapshot_id="snap_a1b2c3d4_1234567890",  # 来自 retrieve_facts
    patches=[
        StateChangePatch(
            target_entity=EntityRef(id="http://example.org/Customer_Li", type="Customer"),
            changes=FactSet(root={"recommendedAction": "升级为铂金卡会员"}),
            description="升级为铂金卡会员"
        ),
        StateChangePatch(
            target_entity=EntityRef(id="http://example.org/Customer_Li", type="Customer"),
            changes=FactSet(root={"recommendedAction": "购买年度会员套餐"}),
            description="购买年度会员套餐"
        ),
    ],
    evaluation_metrics=["compliance_status"],
    policy_set_id="PS_MARKETING_RECOMMEND_V1",
    trace_context=TraceContext()
)
resp = await engine.compare_state(req)
# resp.comparisons[0].patch_description = "升级为铂金卡会员"
# resp.comparisons[0].evaluation = {"verdict": "allow", "triggered_rules": ["R003"], ...}
# resp.comparisons[1].patch_description = "购买年度会员套餐"
# resp.comparisons[1].evaluation = {"verdict": "allow", "triggered_rules": ["R003"], ...}
```

**特点**：
- 不修改本体，只在内存中操作
- 速度快，适合批量方案对比
- 基于 Redis 快照，快照过期则无法使用
- **深拷贝机制**：每个 patch 操作前会深拷贝原始快照数据（`{eid: facts.copy() for eid, facts in base_facts.items()}`），确保不污染原始快照

---

### 2.8 假设推理 - 本体版 (hypothetical_evaluate)

**功能**：在 Jena 中创建临时 Named Graph，注入假设三元组，联合查询事实，执行规则评估，最后清理临时图。适用于需要在本体层面做深度 what-if 分析的场景。

**方法签名**：
```python
async def hypothetical_evaluate(
    self,
    entity_ids: List[str],
    triples: List[HypotheticalTriple],
    policy_set_id: str,
    tenant_id: str = "default_tenant",
) -> HypotheticalEvaluateResponse
```

**输入参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `entity_ids` | `List[str]` | ✅ | 要检索事实的实体 URI 列表。系统会先从本体中查询这些实体的完整事实，然后与假设三元组合并 |
| `triples` | `List[HypotheticalTriple]` | ✅ | 假设三元组列表，表示"如果这些属性变成这样"的假设场景 |
| `triples[].subject` | `str` | ✅ | 主体名称（**不含命名空间**，系统会自动拼接 `http://example.org/`）。如 `"Customer_Li"` |
| `triples[].predicate` | `str` | ✅ | 属性名称（不含命名空间），如 `"vipLevel"`、`"creditLimit"` |
| `triples[].object` | `str` | ✅ | 属性值，如 `"Platinum"`、`"150000"` |
| `triples[].literal` | `bool` | ❌ | `object` 是否为字面量，默认 `True`。`True` 表示字面量值（如字符串、数字），`False` 表示资源 URI（会自动拼接命名空间） |
| `policy_set_id` | `str` | ✅ | 策略集 ID，用于调用 Drools 规则评估合并后的事实 |
| `tenant_id` | `str` | ❌ | 租户 ID，默认 `"default_tenant"` |

**输出结果** (`HypotheticalEvaluateResponse`)：

| 字段 | 类型 | 说明 |
|------|------|------|
| `facts` | `dict` | 联合检索后的实体事实（原始事实 + 假设修改）。key 为实体 URI，value 为属性键值对。假设三元组会覆盖原始属性值 |
| `decision` | `DecisionResult` | 基于合并后事实的 Drools 裁决结果，结构同 `evaluate_policy` 的输出 |

**副作用**：
- Java 后端创建临时 Named Graph（推理完成后自动删除）
- 不写入 Redis 快照和审计日志（由 SDK 层决定是否记录）

**调用示例**：
```python
# 场景：假设 Customer_Li 升级为 Platinum，信用额度提升到 15 万，看是否满足高端推荐条件
resp = await engine.hypothetical_evaluate(
    entity_ids=["http://example.org/Customer_Li"],
    triples=[
        HypotheticalTriple(subject="Customer_Li", predicate="vipLevel", object="Platinum", literal=True),
        HypotheticalTriple(subject="Customer_Li", predicate="creditLimit", object="150000", literal=True),
    ],
    policy_set_id="PS_MARKETING_RECOMMEND_V1",
    tenant_id="marketing_tenant"
)
# resp.facts = {"http://example.org/Customer_Li": {"vipLevel": "Platinum", "creditLimit": "150000", "annualSpend": 80000, ...}}
# resp.decision.verdict = "allow"
# resp.decision.reason = "假设升级至 Platinum 后满足高端推荐条件"
```

**与 compare_state 的区别**：

| 维度 | compare_state (内存版) | hypothetical_evaluate (本体版) |
|------|------------------------|-------------------------------|
| 执行位置 | SDK 内存 + HTTP 调 Drools | Java 后端（Jena + Drools） |
| 数据来源 | Redis 快照 | Jena 本体库（实时查询） |
| 隔离性 | 进程级（只改内存 dict） | 存储级（临时 Named Graph） |
| 速度 | 快（无 I/O 写） | 慢（有图创建/删除） |
| 依赖快照 | 是（快照过期则不可用） | 否（直接查本体） |
| 适用场景 | Agent 快速方案对比（推荐） | 外部系统深度 what-if 分析 |

---

### 2.9 解释生成 (explain)

**功能**：根据审计追踪 ID，从 Redis 读取审计日志，生成面向不同受众的自然语言解释。

**方法签名**：
```python
async def explain(self, req: ExplainRequest) -> ExplainResponse
```

**输入参数** (`ExplainRequest`)：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `trace_id` | `str` | ✅ | 追踪 ID，来自 `retrieve_facts` 或 `TraceContext` 生成的 `trace_id`。**注意：如果该 trace_id 没有对应的审计日志，会报错 "Trace not found"** |
| `audience` | `str` | ✅ | 受众类型，决定解释的详细程度和格式。可选值见下表 |
| `tenant_id` | `str` | ✅ | 租户 ID，来自 `TraceContext.tenant_id` |

**受众类型说明**：

| audience | 输出格式 | 适用场景 |
|----------|----------|----------|
| `"end_user"` | 简单一句话，面向终端用户 | 展示给客户看的推荐理由 |
| `"business"` | 带 trace_id 和规则 ID 的业务说明 | 面向业务人员的决策依据 |
| `"audit"` | 完整 JSON 审计日志 | 合规审计、问题追溯 |

**输出结果** (`ExplainResponse`)：

| 字段 | 类型 | 说明 |
|------|------|------|
| `natural_language` | `str` | 自然语言解释文本。格式取决于 `audience` 参数 |
| `referenced_rules` | `List[str]` | 本次推理过程中引用的所有规则 ID 列表（去重） |

**规则收集逻辑**：
- 从 Redis 读取审计日志：`audit:{trace_id}`
- 遍历所有审计条目，提取 `triggered_rules` 字段
- 合并所有规则 ID，使用 `set()` 去重后返回

**副作用**：无（只读取 Redis，不写入）

**调用示例**：
```python
# 场景：向终端用户解释推荐结果
req = ExplainRequest(
    trace_id="trace_xxx",  # 来自 TraceContext.trace_id
    audience="end_user",
    tenant_id="marketing_tenant"
)
resp = await engine.explain(req)
# resp.natural_language = "根据您的用户资质，为您推荐了最合适的方案。"
# resp.referenced_rules = ["R001", "R003"]

# 场景：面向业务人员的解释
req = ExplainRequest(
    trace_id="trace_xxx",
    audience="business",
    tenant_id="marketing_tenant"
)
resp = await engine.explain(req)
# resp.natural_language = "基于追踪ID trace_xxx，系统根据业务规则做出了决策。"

# 场景：审计用途，获取完整日志
req = ExplainRequest(
    trace_id="trace_xxx",
    audience="audit",
    tenant_id="marketing_tenant"
)
resp = await engine.explain(req)
# resp.natural_language = '[{"step": "fact.retrieve", "timestamp": ..., ...}, ...]'
```

---

### 2.10 审计查询 (get_trace)

**功能**：获取指定追踪 ID 的完整审计日志，用于问题追溯和合规审计。

**方法签名**：
```python
async def get_trace(self, trace_id: str, tenant_id: str) -> Dict[str, Any]
```

**输入参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `trace_id` | `str` | ✅ | 追踪 ID，来自 `TraceContext.trace_id`。如果该 trace_id 不存在，会报错 "Trace not found" |
| `tenant_id` | `str` | ✅ | 租户 ID |

**输出结构**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `trace_id` | `str` | 追踪 ID |
| `tenant_id` | `str` | 租户 ID |
| `steps` | `List[dict]` | 审计步骤列表，按时间顺序排列 |
| `steps[].step` | `str` | 步骤类型：`"fact.retrieve"`、`"policy.evaluate"`、`"policy.evaluate_with_facts"`、`"swrl.evaluate"` 等 |
| `steps[].timestamp` | `float` | Unix 时间戳 |
| `steps[].snapshot_id` | `str` | 快照 ID（仅 `fact.retrieve` 步骤） |
| `steps[].entities` | `List[str]` | 查询的实体列表（仅 `fact.retrieve` 步骤） |
| `steps[].policy_set_id` | `str` | 策略集 ID（仅 `policy.evaluate` 步骤） |
| `steps[].verdict` | `str` | 裁决结果（仅 `policy.evaluate` 步骤） |
| `steps[].triggered_rules` | `List[str]` | 触发的规则列表（仅 `policy.evaluate` 步骤） |
| `total_steps` | `int` | 总步骤数 |

**副作用**：无（只读取 Redis）

**调用示例**：
```python
trace = await engine.get_trace("trace_xxx", "marketing_tenant")
# trace = {
#   "trace_id": "trace_xxx",
#   "tenant_id": "marketing_tenant",
#   "steps": [
#     {"step": "fact.retrieve", "timestamp": 1234567890.123, "snapshot_id": "snap_abc123", "entities": ["Customer_Li"]},
#     {"step": "policy.evaluate", "timestamp": 1234567890.456, "policy_set_id": "PS_MARKETING_V1", "verdict": "allow", "triggered_rules": ["R001"]},
#     {"step": "policy.evaluate", "timestamp": 1234567890.789, "policy_set_id": "PS_MARKETING_V1", "verdict": "allow", "triggered_rules": ["R003"]}
#   ],
#   "total_steps": 3
# }
```

---

### 2.11 自然语言查询 (nl_query)

**功能**：将自然语言问题转换为 SPARQL 查询，执行后返回自然语言回答。采用两阶段设计控制 token 消耗。

**方法签名**：
```python
async def nl_query(self, question: str) -> str
```

**输入参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `question` | `str` | ✅ | 自然语言问题，如 `"Customer_Li 的会员等级是什么？"`、`"找出年消费超过5万的客户"` |

**处理流程**：

```
问题: "Customer_Li 的会员等级是什么？"
  │
  ▼
Stage1: 加载轻量目录 (GET /schema/catalog)
  │  - 获取所有类名、属性名（不含实例数据）
  │  - LLM 判断哪些类和属性与问题相关
  │  - 输出: classes=["Customer"], props=["vipLevel"]
  │
  ▼
Stage2: 加载相关类详情 (POST /schema/detail)
  │  - 只加载 Customer 类的实例样例
  │  - LLM 生成 SPARQL 查询
  │  - 安全校验：拦截写操作（DELETE/INSERT/DROP等）
  │
  ▼
执行 SPARQL (POST /sparql/query)
  │  - 返回查询结果行
  │
  ▼
LLM 解释结果
  │  - 将 SPARQL 结果转为自然语言
  │
  ▼
输出: "Customer_Li 的会员等级是 Gold。"
```

**输出**：自然语言回答字符串

**三级缓存策略**：

| 缓存 | 内容 | 生命周期 |
|------|------|----------|
| `_schema_cache` | 全量本体 schema（含实例样例） | 类级别，永久缓存 |
| `_catalog_cache` | 轻量 T-Box 目录（不含实例） | 类级别，永久缓存 |
| `_load_detail` | 按需加载特定类的实例 | 每次调用都请求后端 |

**LLM 配置**：`temperature=0.1`（低温度，输出更确定性，适合 SPARQL 生成）

**SPARQL 生成 Prompt 规则**：
- 只输出 SPARQL 查询，不要额外解释
- 使用 PREFIX 声明命名空间
- 文本匹配使用 `FILTER(CONTAINS(...))` 或 `FILTER(STRSTARTS(...))`
- 聚合查询使用 `GROUP BY / HAVING / ORDER BY`
- 默认 `LIMIT 100`

**Stage1 LLM 输出格式**：`类名1,类名2|属性名1,属性名2`
- 示例：`Customer,Order|annualSpend,orderAmount`
- 用正则去掉 `<think>` 推理块（某些模型会输出 CoT）

**安全机制**：
- 只允许只读查询（SELECT / ASK / DESCRIBE / CONSTRUCT）
- 拦截写操作（DELETE / INSERT / DROP / CLEAR / CREATE / LOAD）
- 默认 LIMIT 100

**错误处理**：
- SPARQL 生成失败：返回 `"查询生成失败: {错误信息}"`
- SPARQL 执行失败：LLM 尝试解释错误原因
- 查询结果为空：返回 `"查询结果为空。"`

**副作用**：无（只读操作）

**调用示例**：
```python
answer = await engine.nl_query("Customer_Li 的会员等级是什么？")
# answer = "Customer_Li 的会员等级是 Gold。"

answer = await engine.nl_query("找出年消费超过5万的客户")
# answer = "年消费超过5万的客户有：Customer_Li（8万）、Customer_Wang（6万）。"

answer = await engine.nl_query("有多少个铂金会员？")
# answer = "共有 3 个铂金会员。"
```

---

### 2.12 实体发现与画像获取 (nl_discover_and_retrieve)

**功能**：自然语言查询发现目标实体，然后获取这些实体的结构化画像。组合了 `nl_query` 和 `retrieve_facts` 的能力。

**方法签名**：
```python
async def nl_discover_and_retrieve(
    self, question: str, max_entities: int = 5
) -> Dict[str, Any]
```

**输入参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `question` | `str` | ✅ | 自然语言问题，如 `"找出最近3个月消费最高的客户"` |
| `max_entities` | `int` | ❌ | 最大返回实体数，默认 5。从 SPARQL 结果中提取的实体 ID 数量上限 |

**输出结构**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `nl_answer` | `str` | 自然语言回答，基于 SPARQL 查询结果生成 |
| `entity_ids` | `List[str]` | 从 SPARQL 结果中提取的实体 URI 列表，如 `["http://example.org/Customer_Li"]` |
| `sparql` | `str` | LLM 生成的 SPARQL 查询语句 |
| `raw_results` | `List[dict]` | SPARQL 查询的原始结果行 |
| `snapshot` | `RetrieveFactsResponse` | 调用 `retrieve_facts` 获取的实体画像快照（如果实体提取成功） |
| `facts_flat` | `dict` | 第一个实体的扁平化画像，如 `{"vipLevel": "Gold", "annualSpend": 80000}`。方便直接查看 |

**处理流程**：
1. Stage1: NL → SPARQL（同 `nl_query`）
2. 执行 SPARQL 查询
3. 从结果中提取实体 ID（启发式提取）
4. 调用 `retrieve_facts` 获取实体画像
5. 生成自然语言回答

**实体 ID 提取的启发式策略**：

从 SPARQL 查询结果中提取实体 ID，按优先级匹配：

| 优先级 | 匹配方式 | 说明 |
|--------|----------|------|
| 高 | 精确字段名匹配 | 字段名为 `id`、`entity`、`customer`、`user`、`subject`、`s`、`customerId`、`userId`、`entityId`、`subjectId` |
| 低 | 第一个非数字、非重复的值 | 如果高优先级无匹配，取每行第一个符合条件的值 |

处理逻辑：
- 按优先级排序 → 去重 → 截断到 `max_entities`
- 短名称自动补全为 `http://example.org/{id}`

**副作用**：
- Redis 写入快照（来自 `retrieve_facts`）
- Redis 写入审计日志（来自 `retrieve_facts`）

**调用示例**：
```python
result = await engine.nl_discover_and_retrieve("找出年消费超过5万的客户")
# result["nl_answer"] = "年消费超过5万的客户有：Customer_Li（8万）、Customer_Wang（6万）。"
# result["entity_ids"] = ["http://example.org/Customer_Li", "http://example.org/Customer_Wang"]
# result["snapshot"].snapshot_id = "snap_xxx"
# result["facts_flat"] = {"vipLevel": "Gold", "annualSpend": 80000, ...}
```

---

### 2.13 快捷评估 (quick_evaluate)

**功能**：一步完成 retrieve_facts + evaluate_policy，返回裁决结果。内部自动创建 TraceContext，不需要手动管理追踪上下文。

**方法签名**：
```python
async def quick_evaluate(
    self,
    entities: List[EntityRef],
    policy_set_id: str,
    tenant_id: str = "default_tenant",
) -> DecisionResult
```

**输入参数**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `entities` | `List[EntityRef]` | ✅ | 实体引用列表，同 `retrieve_facts` 的 `entities` 参数 |
| `entities[].id` | `str` | ✅ | 实体 ID |
| `entities[].type` | `str` | ✅ | 实体类型 |
| `policy_set_id` | `str` | ✅ | 策略集 ID |
| `tenant_id` | `str` | ❌ | 租户 ID，默认 `"default_tenant"` |

**输出结果** (`DecisionResult`)：

| 字段 | 类型 | 说明 |
|------|------|------|
| `verdict` | `str` | 裁决结论：`"allow"` / `"deny"` / `"review"` |
| `triggered_rules` | `List[str]` | 触发的规则 ID 列表 |
| `reason` | `str` | 裁决理由 |

**内部流程**：
1. 创建 `TraceContext`（自动生成 `trace_id`）
2. 调用 `retrieve_facts` 获取第一个实体的画像
3. 调用 `evaluate_policy` 评估规则
4. 返回 `DecisionResult`

**副作用**：
- Redis 写入快照（来自 `retrieve_facts`）
- Redis 写入审计日志（来自 `retrieve_facts` + `evaluate_policy`）

**调用示例**：
```python
# 快速判断用户是否满足升级条件
decision = await engine.quick_evaluate(
    entities=[EntityRef(id="Customer_Li", type="Customer")],
    policy_set_id="PS_MARKETING_RECOMMEND_V1",
    tenant_id="marketing_tenant"
)
# decision.verdict = "allow"
# decision.triggered_rules = ["R001", "R003"]
# decision.reason = "用户等级 Gold 且年消费 >= 5万"
```

**适用场景**：只需要判断用户是否满足条件，不需要中间数据（如快照 ID、完整画像）。

---

### 2.14 API 关系图

```
                                    ┌─────────────────┐
                                    │  nl_query       │
                                    │  (NL → SPARQL)  │
                                    └────────┬────────┘
                                             │
                                             ▼
┌─────────────────┐           ┌───────────────────────────────────────────┐
│ retrieve_facts  │──────────▶│              校验与推理层                  │
│ (查画像)        │           │                                           │
└────────┬────────┘           │  ┌─────────────────┐  ┌───────────────┐  │
         │                    │  │ evaluate_policy │  │ evaluate_swrl │  │
         │                    │  │ (Drools 规则)   │  │ (SWRL 推理)   │  │
         │                    │  └────────┬────────┘  └───────┬───────┘  │
         │                    │           │                   │          │
         │                    │           ▼                   ▼          │
         │                    │  ┌─────────────────────────────────────┐ │
         │                    │  │        validate_shacl               │ │
         │                    │  │        (数据形状验证)               │ │
         │                    │  └─────────────────────────────────────┘ │
         │                    └───────────────────┬───────────────────────┘
         │                                        │
         ▼                                        ▼
┌─────────────────┐                    ┌─────────────────┐
│ compare_state   │                    │    explain      │
│ (内存假设推理)  │                    │ (生成解释)      │
└─────────────────┘                    └─────────────────┘

┌─────────────────┐
│ hypothetical_   │
│ evaluate        │
│ (本体假设推理)  │
└─────────────────┘
```

---

## 3. 工作流编排示例

> 以营销推荐场景为例，展示如何通过平台拖拉拽方式编排工作流。

### 3.1 场景描述

**业务背景**：用户咨询会员升级方案，系统需要：
1. 查询用户画像（会员等级、年消费等）
2. 生成候选推荐方案
3. 校验每个方案是否符合业务规则
4. 对比合规方案，选出最优
5. 生成用户可读的解释

### 3.2 工作流节点

| 节点名称 | 节点类型 | 调用能力 | 说明 |
|----------|---------|----------|------|
| 开始 | 内置 | - | 接收输入参数 |
| 获取用户画像 | 工具 | `retrieve_facts` | 查询本体中的用户事实 |
| 生成候选方案 | LLM | - | LLM 基于画像生成推荐 |
| 校验合规性 | 工具 | `evaluate_policy` | Drools 规则校验 |
| 对比选优 | 工具 | `compare_state` | 假设推理对比方案 |
| 生成解释 | 工具 | `explain` | 审计日志 → 自然语言 |
| 结束 | 内置 | - | 输出最终结果 |

### 3.3 工作流图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                                                                 │
│   ┌────────┐                                                                    │
│   │  开始  │                                                                    │
│   │        │ 输入参数:                                                          │
│   │        │   user_id: "Customer_Li"                                           │
│   │        │   query: "我想升级会员，有什么推荐？"                                │
│   └───┬────┘                                                                    │
│       │                                                                         │
│       ▼                                                                         │
│   ┌────────────────────┐                                                        │
│   │  获取用户画像      │ 调用: retrieve_facts                                   │
│   │                    │ 输入: user_id                                          │
│   │                    │ 输出: snapshot_id, facts_map, trace_id                 │
│   └────────┬───────────┘                                                        │
│            │                                                                    │
│            ▼                                                                    │
│   ┌────────────────────┐                                                        │
│   │  生成候选方案      │ 调用: LLM                                              │
│   │                    │ 输入: facts_map, query                                 │
│   │                    │ 输出: candidates["升级金卡", "开通铂金卡", ...]         │
│   └────────┬───────────┘                                                        │
│            │                                                                    │
│            ▼                                                                    │
│   ┌────────────────────┐                                                        │
│   │  校验合规性        │ 调用: evaluate_policy (多次)                           │
│   │                    │ 输入: snapshot_id, candidates, trace_id                │
│   │                    │ 输出: validated_decisions[]                            │
│   │                    │   - 每个候选方案的 allow/deny 裁决                      │
│   └────────┬───────────┘                                                        │
│            │                                                                    │
│            ▼                                                                    │
│   ┌────────────────────┐                                                        │
│   │  对比选优          │ 调用: compare_state                                    │
│   │                    │ 输入: snapshot_id, validated_decisions                 │
│   │                    │ 输出: final_decision                                   │
│   │                    │   - 选中方案 + 理由                                     │
│   └────────┬───────────┘                                                        │
│            │                                                                    │
│            ▼                                                                    │
│   ┌────────────────────┐                                                        │
│   │  生成解释          │ 调用: explain                                          │
│   │                    │ 输入: trace_id, audience="end_user"                    │
│   │                    │ 输出: explanation                                      │
│   └────────┬───────────┘                                                        │
│            │                                                                    │
│            ▼                                                                    │
│   ┌────────┐                                                                    │
│   │  结束  │ 输出:                                                              │
│   │        │   final_decision: "选中: 升级金卡 (理由: 年消费达标)"              │
│   │        │   explanation: "根据您的年消费记录，为您推荐了金卡升级方案。"       │
│   └────────┘                                                                    │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 3.4 节点间数据流转

| 上游节点 | 输出字段 | 下游节点 | 输入字段 |
|----------|----------|----------|----------|
| 获取用户画像 | `snapshot_id` | 校验合规性、对比选优 | `snapshot_id` |
| 获取用户画像 | `facts_map` | 生成候选方案 | `facts_map` |
| 获取用户画像 | `trace_id` | 校验合规性、生成解释 | `trace_id` |
| 生成候选方案 | `candidates` | 校验合规性 | `candidates` |
| 校验合规性 | `validated_decisions` | 对比选优 | `validated_decisions` |

### 3.5 运行时数据示例

**步骤1：获取用户画像**
```json
// 输入
{"user_id": "Customer_Li"}

// 输出
{
  "snapshot_id": "snap_a1b2c3d4_1234567890",
  "facts_map": {
    "Customer_Li": {
      "vipLevel": "Gold",
      "annualSpend": 80000,
      "memberYears": 3,
      "creditScore": 750
    }
  },
  "trace_id": "trace_xxx"
}
```

**步骤2：生成候选方案**
```json
// 输出
{
  "candidates": [
    "升级为铂金卡会员",
    "开通联名信用卡",
    "购买年度会员套餐"
  ]
}
```

**步骤3：校验合规性**
```json
// 输出
{
  "validated_decisions": [
    {"verdict": "allow", "triggered_rules": ["R001"], "reason": "用户满足基础条件"},
    {"verdict": "allow", "candidate_index": 0, "triggered_rules": ["R003"], "reason": "年消费达标"},
    {"verdict": "deny", "candidate_index": 1, "triggered_rules": ["R005"], "reason": "信用分不足"},
    {"verdict": "allow", "candidate_index": 2, "triggered_rules": ["R003"], "reason": "会员年限达标"}
  ]
}
```

**步骤4：对比选优**
```json
// 输出
{
  "final_decision": "选中: 升级为铂金卡会员 (理由: 年消费达标，且为最高价值方案)"
}
```

**步骤5：生成解释**
```json
// 输出
{
  "explanation": "根据您的年消费记录和会员等级，为您推荐了铂金卡升级方案。"
}
```

---

## 4. 对本体平台的能力需求

> 详细接口文档见：[api-reference.md](./api-reference.md)

### 4.1 依赖的后端能力

融合推理引擎 SDK 依赖本体平台（ontoSrv）提供以下能力：

| 能力 | 接口 | 说明 |
|------|------|------|
| 实体事实检索 | `POST /facts/retrieve` | 根据实体 ID 查询属性键值对 |
| 规则评估 | `POST /policy/evaluate` | 执行 Drools 规则，返回裁决 |
| 合并评估 | `POST /evaluate` | 一步完成事实检索+规则评估 |
| SWRL 推理 | `POST /swrl/evaluate` | 执行 SWRL 规则推理，推导新事实 |
| SHACL 验证 | `POST /shacl/validate` | SHACL 数据形状验证 |
| SPARQL 查询 | `POST /sparql/query` | 执行只读 SPARQL 查询 |
| Schema 获取 | `GET /schema` | 获取完整本体结构 |
| 轻量目录 | `GET /schema/catalog` | 获取类名和属性名（不含实例） |
| 实例样例 | `POST /schema/detail` | 按类名获取实例样例 |
| 假设推理 | `POST /hypothetical/evaluate` | 创建临时 Named Graph 做 what-if |

### 4.2 调用关系

```
SDK 方法                          后端接口
─────────────────────────────────────────────────────
retrieve_facts()              ──▶ POST /facts/retrieve
evaluate_policy()             ──▶ POST /policy/evaluate
evaluate_policy_with_facts()  ──▶ POST /evaluate
evaluate_swrl()               ──▶ POST /swrl/evaluate
validate_shacl()              ──▶ POST /shacl/validate
hypothetical_evaluate()       ──▶ POST /hypothetical/evaluate
nl_query()                    ──▶ GET /schema/catalog
                              ──▶ POST /schema/detail
                              ──▶ POST /sparql/query
```

### 4.3 数据存储需求

| 存储 | 用途 | 生命周期 |
|------|------|----------|
| Jena/RDF4J | 本体数据（实体、属性、关系） | 持久 |
| Drools 规则文件 | 业务规则（.drl） | 持久，可热更新 |
| Redis 快照 | 事实检索结果缓存 | TTL 1小时 |
| Redis 审计日志 | 推理过程记录 | 持久 |

---

## 附录

### A. 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `REDIS_HOST` | `localhost` | Redis 主机 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `JAVA_SERVICE_URL` | `http://localhost:8080/api/v1` | Java 后端地址 |
| `LLM_MODEL` | `gpt-4o` | LLM 模型名 |
| `LLM_BASE_URL` | - | LLM API 地址 |
| `LLM_API_KEY` | - | LLM API Key |

### B. 文件结构

```
marketing_agent_demo/
├── sdk/
│   ├── reasoning_engine.py   # 核心 SDK
│   ├── models.py             # 数据模型
│   ├── mcp_server.py         # MCP Server
│   └── mcp_client.py         # MCP Client
├── agent/
│   ├── graph.py              # LangGraph 工作流定义
│   ├── nodes.py              # 节点实现
│   └── state.py              # 状态模型
└── run.py                    # 启动入口
```
