# 已发布本体应用服务接口文档 V2

## 1. 文档说明

本文档描述当前可对外开放的**已发布本体应用服务**接口。接口以 `projectId` 定位项目，服务端自动解析该项目当前的“已发布且已物化”版本；调用方**不传 `versionId`**。

当前共开放 10 个接口：本体列表、模型加载、图查询能力、图查询执行、自然语言生成查询、SWRL 规则查询、推理任务提交、取消和工作台查询。

> 当前 `/ontology/model/published/application/load` 仍会按 `instanceLimit` 返回实例及实例关系；本文档如实描述现有实现。若后续需要“仅本体定义（类、属性、关系、OWL、SWRL，不含实例）”的一次性聚合接口，应单独新增接口并发布新的契约版本。

### 1.1 通用访问地址

```text
http://10.88.212.165:9999/data-onto-server
```

### 1.2 通用请求头

| Header | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `Authorization` | 二选一 | `Bearer YOUR_ACCESS_TOKEN` | 用户登录后获取的访问令牌，由 gateway 校验。 |
| `x-api-key` | 二选一 | `sk-onto-<UUID>` | 作为 `Authorization` 的替代认证方式。API Key 由管理端为用户创建，gateway 将其解析为该创建用户的 JWT，因此沿用该用户的 JWT 权限与接口鉴权范围。仅可通过请求头传递，**不得**放入 Query 参数。 |
| `Content-Type` | 是 | `application/json` | 请求体格式 |

### 1.3 通用响应结构

```json
{
  "code": 200,
  "message": "处理成功",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | Long | 响应码，`200` 表示成功 |
| `message` | String | 响应信息 |
| `data` | Object / Array | 业务响应数据；失败时通常为 `null` |

### 1.4 已发布应用范围响应结构

除本体列表外，已发布应用接口统一在 `data` 外层返回当前解析出的版本范围：

```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "projectId": "project_001",
    "versionId": "version_001",
    "semanticMaterialized": true,
    "data": {}
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.projectId` | String | 项目 ID |
| `data.versionId` | String | 服务端解析出的当前已发布、已物化版本 ID |
| `data.semanticMaterialized` | Boolean | 当前版本是否已完成语义物化 |
| `data.data` | Object / Array | 该接口实际业务数据 |

---

## 2. 查询已发布本体列表接口

### 2.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 查询已发布本体列表 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/review/published/ontologies` |
| Controller | `OntologyReviewController` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

### 2.2 请求体

```json
{}
```

该接口无请求字段。

### 2.3 响应示例

```json
{
  "code": 200,
  "message": "处理成功",
  "data": [
    {
      "projectId": "project_001",
      "projectName": "客户本体",
      "versionId": "version_001",
      "versionNo": "v1.0",
      "versionName": "客户本体 V1",
      "commitId": "commit_001",
      "domainName": "客户域",
      "publishTime": "2026-07-22 10:00:00"
    }
  ]
}
```

### 2.4 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Array | 已发布本体项目列表 |
| `data[].projectId` | String | 本体项目 ID |
| `data[].projectName` | String | 本体项目名称 |
| `data[].versionId` | String | 当前已发布版本 ID |
| `data[].versionNo` | String | 当前已发布版本号 |
| `data[].versionName` | String | 当前已发布版本名称 |
| `data[].commitId` | String | 当前版本关联的提交 ID |
| `data[].domainName` | String | 所属领域名称 |
| `data[].publishTime` | String | 发布时间，格式为 `yyyy-MM-dd HH:mm:ss` |

---

## 3. 加载当前已发布物化本体模型接口

### 3.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 加载当前已发布物化本体模型 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/model/published/application/load` |
| Controller | `OntologyModelController` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

### 3.2 请求体

```json
{
  "projectId": "project_001",
  "instanceLimit": 100
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 本体项目 ID |
| `instanceLimit` | Integer | 否 | 返回实例数量上限；当前实现会同时返回实例和实例关系 |

### 3.3 响应示例

```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "projectId": "project_001",
    "versionId": "version_001",
    "semanticMaterialized": true,
    "data": {
      "projectId": "project_001",
      "versionId": "version_001",
      "classCount": 2,
      "propertyCount": 3,
      "relationCount": 1,
      "constraintCount": 0,
      "mappingRuleCount": 0,
      "instanceCount": 1,
      "instanceLimit": 100,
      "classes": [{ "id": "class_001", "className": "客户", "classCode": "Customer" }],
      "properties": [{ "id": "property_001", "propertyName": "客户名称", "propertyCode": "customerName", "dataType": "string" }],
      "relations": [{ "id": "relation_001", "relationName": "购买", "relationCode": "buy" }],
      "constraints": [],
      "mappingRules": [],
      "instances": [],
      "instanceRelations": []
    }
  }
}
```

### 3.4 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.data.classCount` | Integer | 类数量 |
| `data.data.propertyCount` | Integer | 属性数量 |
| `data.data.relationCount` | Integer | 关系数量 |
| `data.data.constraintCount` | Integer | 约束数量 |
| `data.data.mappingRuleCount` | Integer | 映射规则数量 |
| `data.data.instanceCount` | Integer | 本次返回的实例数量 |
| `data.data.instanceLimit` | Integer | 实例返回数量限制 |
| `data.data.classes` | Array | 本体类列表 |
| `data.data.properties` | Array | 本体属性列表 |
| `data.data.relations` | Array | 本体关系列表 |
| `data.data.constraints` | Array | 本体约束列表 |
| `data.data.mappingRules` | Array | 数据映射规则列表 |
| `data.data.instances` | Array | 实例列表 |
| `data.data.instanceRelations` | Array | 实例关系列表 |

---

## 4. 查询已发布本体图检索能力接口

### 4.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 查询已发布本体图检索能力 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/query/published/application/capability` |
| Controller | `OntologyQueryController` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

### 4.2 请求体

```json
{ "projectId": "project_001" }
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 本体项目 ID |

### 4.3 响应示例

```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "projectId": "project_001",
    "versionId": "version_001",
    "semanticMaterialized": true,
    "data": {
      "defaultLanguage": "sparql",
      "executableLanguages": ["sparql"],
      "unsupportedLanguages": ["cypher", "ngql", "gremlin", "pgql", "gql"]
    }
  }
}
```

### 4.4 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.data.defaultLanguage` | String | 默认图查询语言 |
| `data.data.executableLanguages` | Array<String> | 当前已发布物化本体可执行的查询语言列表 |
| `data.data.unsupportedLanguages` | Array<String> | 当前版本暂不支持执行的查询语言列表 |

---

## 5. 执行已发布本体图查询接口

### 5.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 执行已发布本体图查询 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/query/published/application/execute` |
| Controller | `OntologyQueryController` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

### 5.2 请求体

```json
{
  "projectId": "project_001",
  "language": "sparql",
  "query": "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 25",
  "limit": 100
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 本体项目 ID |
| `language` | String | 是 | 图查询语言；须属于能力接口返回的 `executableLanguages` |
| `query` | String | 是 | 图查询语句 |
| `limit` | Integer | 否 | 返回结果上限 |

### 5.3 响应示例

```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "projectId": "project_001",
    "versionId": "version_001",
    "semanticMaterialized": true,
    "data": {
      "rows": [{ "subject": "客户", "predicate": "购买", "object": "产品" }],
      "nodes": [{ "id": "class_001", "name": "客户", "kind": "Class" }],
      "edges": [{ "id": "relation_001", "from": "class_001", "to": "class_002", "label": "购买", "kind": "Relation" }]
    }
  }
}
```

### 5.4 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.data.rows` | Array | 表格形式查询结果 |
| `data.data.rows[].subject` | String | 主体 |
| `data.data.rows[].predicate` | String | 谓词、类型或属性 |
| `data.data.rows[].object` | String | 客体值或 IRI |
| `data.data.nodes` | Array | 图节点列表 |
| `data.data.nodes[].id` | String | 节点唯一标识 |
| `data.data.nodes[].name` | String | 节点显示名称 |
| `data.data.nodes[].kind` | String | 节点类型 |
| `data.data.edges` | Array | 图边列表 |
| `data.data.edges[].id` | String | 边唯一标识 |
| `data.data.edges[].from` | String | 起点节点 ID |
| `data.data.edges[].to` | String | 终点节点 ID |
| `data.data.edges[].label` | String | 边显示名称 |
| `data.data.edges[].kind` | String | 边类型 |

---

## 6. 自然语言生成已发布本体图查询接口

### 6.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 自然语言生成已发布本体图查询 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/query/published/application/ai/generate` |
| Controller | `OntologyQueryController` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

### 6.2 请求体

```json
{
  "projectId": "project_001",
  "language": "sparql",
  "intent": "查询客户与产品之间的购买关系",
  "limit": 100,
  "modelId": "model_001"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 本体项目 ID |
| `language` | String | 是 | 目标查询语言，应使用能力接口声明支持的语言 |
| `intent` | String | 是 | 自然语言检索意图 |
| `limit` | Integer | 否 | 生成语句建议的结果上限 |
| `modelId` | String | 否 | 指定大模型 ID；不传时由服务端按默认模型策略处理 |

### 6.3 响应示例

```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "projectId": "project_001",
    "versionId": "version_001",
    "semanticMaterialized": true,
    "data": {
      "language": "sparql",
      "query": "SELECT ?customer ?product WHERE { ?customer <urn:buy> ?product } LIMIT 100",
      "explanation": "查询客户购买产品的关系"
    }
  }
}
```

### 6.4 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.data.language` | String | 实际生成的查询语言 |
| `data.data.query` | String | 生成的只读图查询语句 |
| `data.data.explanation` | String | 生成说明 |

> 调用方应将生成的 `query` 交由“执行已发布本体图查询接口”执行；生成接口不执行查询。

---

## 7. 查询已发布本体 SWRL 规则列表接口

### 7.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 查询已发布本体 SWRL 规则列表 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/reasoning/published/application/swrl/rule/list?pageSize=10&pageNum=1` |
| Controller | `OntologyReasoningController` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

### 7.2 Query 参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `pageSize` | Integer | 否 | `10` | 每页条数 |
| `pageNum` | Integer | 否 | `1` | 页码，从 1 开始 |

### 7.3 请求体

```json
{
  "projectId": "project_001",
  "enabled": true,
  "keyword": "客户"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 本体项目 ID |
| `ruleName` | String | 否 | 按规则名称筛选 |
| `ruleCode` | String | 否 | 按规则编码筛选 |
| `enabled` | Boolean | 否 | 是否仅查询启用规则 |
| `keyword` | String | 否 | 规则名称或编码关键字 |

### 7.4 响应示例

```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "projectId": "project_001",
    "versionId": "version_001",
    "semanticMaterialized": true,
    "data": {
      "pageNum": 1,
      "pageSize": 10,
      "total": 1,
      "totalPage": 1,
      "list": [{
        "id": "swrl_001",
        "ruleName": "高价值客户识别",
        "ruleCode": "high_value_customer",
        "enabled": true
      }]
    }
  }
}
```

### 7.5 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.data.pageNum` | Integer | 当前页码 |
| `data.data.pageSize` | Integer | 每页条数 |
| `data.data.total` | Long | 规则总数 |
| `data.data.totalPage` | Integer | 总页数 |
| `data.data.list` | Array | SWRL 规则列表 |
| `data.data.list[].id` | String | 规则 ID |
| `data.data.list[].ruleName` | String | 规则名称 |
| `data.data.list[].ruleCode` | String | 规则编码 |
| `data.data.list[].enabled` | Boolean | 是否启用 |

---

## 8. 查询已发布本体 SWRL 规则详情接口

### 8.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 查询已发布本体 SWRL 规则详情 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/reasoning/published/application/swrl/rule/get` |
| Controller | `OntologyReasoningController` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

### 8.2 请求体

```json
{
  "projectId": "project_001",
  "ruleId": "swrl_001"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 本体项目 ID |
| `ruleId` | String | 是 | SWRL 规则 ID |

### 8.3 响应示例

```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "projectId": "project_001",
    "versionId": "version_001",
    "semanticMaterialized": true,
    "data": {
      "rule": {
        "id": "swrl_001",
        "ruleName": "高价值客户识别",
        "ruleCode": "high_value_customer",
        "enabled": true
      },
      "bodyAtoms": [],
      "headAtoms": [],
      "argumentsByAtomId": {},
      "inputMetadata": { "fields": [] }
    }
  }
}
```

### 8.4 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.data.rule` | Object | SWRL 规则主体信息，包含规则 ID、名称、编码、启用状态及规则表达式等字段 |
| `data.data.rule.id` | String | SWRL 规则 ID |
| `data.data.rule.ruleName` | String | 规则名称 |
| `data.data.rule.ruleCode` | String | 规则编码 |
| `data.data.rule.enabled` | Boolean | 是否启用 |
| `data.data.bodyAtoms` | Array | 规则前件（Body）原子列表 |
| `data.data.headAtoms` | Array | 规则后件（Head）原子列表 |
| `data.data.argumentsByAtomId` | Object | 以原子 ID 为键的参数列表映射 |
| `data.data.inputMetadata` | Object | 规则运行时输入条件元数据 |
| `data.data.inputMetadata.fields` | Array | 可传入条件字段定义；无动态条件时为空数组 |

---

## 9. 提交已发布本体推理任务接口

### 9.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 提交已发布本体推理任务 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/reasoning/published/application/task/run` |
| Controller | `OntologyReasoningController` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

### 9.2 请求体

```json
{
  "projectId": "project_001",
  "taskName": "客户本体 OWL 推理",
  "executionMode": "MATERIALIZED",
  "reasonType": "OWL_LITE",
  "ruleScope": "ALL_ENABLED",
  "ruleIds": [],
  "conditions": [],
  "instanceLimit": 100,
  "includeOwl": true,
  "includeSwrl": false,
  "includeExplicitFacts": true
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 本体项目 ID |
| `taskName` | String | 否 | 推理任务名称 |
| `executionMode` | String | 是 | 当前已发布物化应用推理传 `MATERIALIZED` |
| `reasonType` | String | 是 | `OWL_LITE` 或 `SWRL` |
| `ruleScope` | String | SWRL 时建议传 | `CURRENT_RULE`（当前规则）、`DEPENDENCY_CHAIN`（当前规则及依赖链）、`ALL_ENABLED`（全部启用规则） |
| `ruleIds` | Array<String> | 否 | SWRL 指定规则范围时传入规则 ID 列表；`ALL_ENABLED` 时传空数组 |
| `conditions` | Array | 否 | SWRL 规则的动态过滤条件 |
| `conditions[].ruleId` | String | 条件存在时是 | 规则 ID |
| `conditions[].variableName` | String | 条件存在时是 | SWRL 变量名 |
| `conditions[].propertyPredicate` | String | 条件存在时是 | 条件属性谓词 |
| `conditions[].operator` | String | 条件存在时是 | 比较运算符，如 `=`、`>`、`>=` |
| `conditions[].value` | String / Number / Boolean | 条件存在时是 | 条件值 |
| `instanceLimit` | Integer | 否 | 参与本次推理的实例上限 |
| `includeOwl` | Boolean | 是 | 是否执行 OWL 推理 |
| `includeSwrl` | Boolean | 是 | 是否执行 SWRL 推理 |
| `includeExplicitFacts` | Boolean | 否 | 是否将显式事实纳入推理范围 |

### 9.3 响应示例

```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "projectId": "project_001",
    "versionId": "version_001",
    "semanticMaterialized": true,
    "data": {
      "taskId": "reason_task_001",
      "taskStatus": "PENDING"
    }
  }
}
```

### 9.4 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.data.taskId` | String | 服务端创建的异步推理任务 ID |
| `data.data.taskStatus` | String | 初始任务状态，通常为 `PENDING` |

> 本接口只负责提交异步任务。请使用“查询推理工作台接口”按 `taskId` 查询任务状态、结果、溯源和日志。

---

## 10. 取消已发布本体推理任务接口

### 10.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 取消已发布本体推理任务 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/reasoning/published/application/task/cancel` |
| Controller | `OntologyReasoningController` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

### 10.2 请求体

```json
{
  "projectId": "project_001",
  "taskId": "reason_task_001"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 本体项目 ID |
| `taskId` | String | 是 | 待取消的推理任务 ID |

### 10.3 响应示例

```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "projectId": "project_001",
    "versionId": "version_001",
    "semanticMaterialized": true,
    "data": {
      "id": "reason_task_001",
      "taskStatus": "CANCEL_REQUESTED"
    }
  }
}
```

### 10.4 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.data.id` | String | 推理任务 ID |
| `data.data.taskStatus` | String | 取消请求后的任务状态，通常为 `CANCEL_REQUESTED` 或终态 |

---

## 11. 查询已发布本体推理工作台接口

### 11.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 查询已发布本体推理工作台 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/reasoning/published/application/reasoning/workbench/get` |
| Controller | `OntologyReasoningController` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

### 11.2 请求体

```json
{
  "projectId": "project_001",
  "taskId": "reason_task_001"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 本体项目 ID |
| `taskId` | String | 是 | 推理任务 ID |

### 11.3 响应示例

```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "projectId": "project_001",
    "versionId": "version_001",
    "semanticMaterialized": true,
    "data": {
      "task": {
        "id": "reason_task_001",
        "taskStatus": "SUCCESS",
        "taskParams": "{\"reasonType\":\"OWL_LITE\"}"
      },
      "results": [
        {
          "id": "result_001",
          "subjectIri": "urn:customer:001",
          "predicate": "rdf:type",
          "objectIri": "urn:class:HighValueCustomer",
          "resultContent": "customer_001 rdf:type HighValueCustomer",
          "evidenceJson": "{}"
        }
      ],
      "logs": [
        {
          "id": "log_001",
          "logLevel": "INFO",
          "logContent": "推理任务执行完成"
        }
      ]
    }
  }
}
```

### 11.4 响应字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.data.task` | Object | 当前推理任务信息 |
| `data.data.task.id` | String | 推理任务 ID |
| `data.data.task.taskStatus` | String | 任务状态，如 `PENDING`、`RUNNING`、`CANCEL_REQUESTED`、`SUCCESS`、`FAILED` |
| `data.data.task.taskParams` | String | 任务参数 JSON 字符串，包含执行范围、规则统计、告警等执行摘要 |
| `data.data.results` | Array | 当前任务推理结果 |
| `data.data.results[].subjectIri` | String | 推理结论主体 IRI |
| `data.data.results[].predicate` | String | 推理结论谓词 |
| `data.data.results[].objectIri` | String | 推理结论客体 IRI；字面量结果可能为空 |
| `data.data.results[].objectValue` | String | 字面量客体值；对象关系结果可能为空 |
| `data.data.results[].resultContent` | String | 便于展示的推理结论文本 |
| `data.data.results[].evidenceJson` | String | 推理依据 JSON 字符串；客户端需按需反序列化 |
| `data.data.logs` | Array | 推理执行日志 |
| `data.data.logs[].logLevel` | String | 日志级别 |
| `data.data.logs[].logContent` | String | 日志内容 |

### 11.5 调用建议

推理任务提交成功后，使用返回的 `taskId` 调用本接口。任务状态为 `PENDING`、`RUNNING` 或 `CANCEL_REQUESTED` 时，可按业务需要轮询；当前页面实现的轮询间隔为 3 秒。

---

## 12. 同步 SWRL 推理执行接口

### 12.1 基本信息

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 同步 SWRL 推理执行 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/reasoning/swrl/execute` |
| Controller | `OntologyReasoningController` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

> 本接口同步执行 SWRL 规则，并在响应中直接返回推理结果、溯源、诊断、统计和日志，同时创建可通过任务详情接口查询的任务记录。

### 12.2 请求体

```json
{
  "projectId": "project_001",
  "taskName": "客户关系 SWRL 推理",
  "factSourceType": "ONTOLOGY_INSTANCE",
  "useAllRules": false,
  "ruleIds": ["rule_001"],
  "factConstraints": null,
  "factScope": null,
  "factGraph": null,
  "factOverlay": null
}
```

#### 12.2.1 请求字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 已发布本体项目 ID |
| `taskName` | String | 否 | 推理任务名称 |
| `factSourceType` | String | 是 | 事实来源类型。支持的码值及使用限制见下表；服务端忽略大小写并统一转为大写。 |
| `useAllRules` | Boolean | 是 | 是否执行全部启用的 SWRL 规则；为 `false` 时使用 `ruleIds` 指定规则。 |
| `ruleIds` | Array<String> | 条件必填 | 指定执行的规则 ID 列表；`useAllRules=false` 时必须传入至少一个规则 ID，`useAllRules=true` 时传空数组。 |
| `factConstraints` | Object | 否 | 规则输入条件树。按所选 SWRL 规则 Body 中的变量及其约束元数据校验，用于规则级条件限制。 |
| `factScope` | Object | 否 | 全局事实范围树。按当前已发布本体的类、数据属性和对象属性元数据校验，与所选规则的 Body 变量无关。未传、`null` 或空树时不缩小事实范围。 |
| `factGraph` | Object | 否 | 调用方直接提供的完整事实图；仅 `factSourceType=FACT_GRAPH` 时可传，且此时必须传。 |
| `factOverlay` | Object | 否 | 调用方提供的数据属性覆盖；仅非 `FACT_GRAPH` 事实源可传，不参与实例范围筛选。 |

#### 12.2.2 请求字段说明：`factSourceType` 事实来源类型与通用条件树格式

支持以下码值：

| 码值 | 事实来源 | 说明 | 请求限制 |
| --- | --- | --- | --- |
| `ONTOLOGY_INSTANCE` | 已发布本体实例 | 从当前项目当前已发布版本的本体实例、数据属性和对象关系加载事实；支持按 `factScope` 选择锚点实例。 | 可传 `factConstraints`、`factScope`、`factOverlay`；不可传 `factGraph`。 |
| `BUSINESS_DATABASE` | 业务数据库 | 从已发布映射配置关联的业务数据库读取事实；服务端按发布映射和确定性查询计划加载。 | 可传 `factConstraints`、`factScope`、`factOverlay`；不可传 `factGraph`。 |
| `GRAPH_DATABASE` | 图数据库 | 从已发布映射配置关联的图数据库读取事实。 | 可传 `factConstraints`、`factScope`、`factOverlay`；不可传 `factGraph`。 |
| `ONTOLOGY_FILE` | 本体文件 | 从当前发布版本关联的本体文件加载本体事实；文件不可用时请求失败。 | 可传 `factConstraints`、`factScope`、`factOverlay`；不可传 `factGraph`。 |
| `FACT_GRAPH` | 内联事实图 | 完全使用请求体中的 `factGraph` 作为事实来源，不读取已发布实例、业务数据库或图数据库。 | 必须传 `factGraph`；不可传 `factOverlay`；可传 `factScope`。 |

未注册或不支持的来源类型会被拒绝。来源可用性还受当前发布版本是否存在对应资源影响。

`factScope` 与 `factConstraints` 使用相同的递归条件树 JSON 结构。组合节点使用 `logic` 和 `items`，其中 `logic` 为 `AND` 或 `OR`，`items` 为非空子节点数组；子节点可以继续是组合节点或叶子节点。

```json
{
  "logic": "AND",
  "items": [
    {
      "constraintType": "DATA_PROPERTY",
      "classIri": "http://example.com/ontology#Person",
      "propertyIri": "http://example.com/ontology#age",
      "operator": "GE",
      "value": 18,
      "datatypeIri": "http://www.w3.org/2001/XMLSchema#integer"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `logic` | String | 组合节点逻辑：`AND` 或 `OR`；组合节点不传叶子字段。 |
| `items` | Array | 组合节点的非空子节点数组。 |
| `constraintType` | String | 叶子类型：`CLASS`、`DATA_PROPERTY` 或 `OBJECT_PROPERTY`。 |
| `classIri` | String | 叶子主体所属类 IRI，必填。 |
| `propertyIri` | String | `DATA_PROPERTY` 必填；不能与 `relationIri` 同时使用。 |
| `relationIri` | String | `OBJECT_PROPERTY` 必填；不能与 `propertyIri` 同时使用。 |
| `operator` | String | 比较操作符。支持 `EQ`（等于）、`NE`（不等于）、`GT`（大于）、`GE`（大于等于）、`LT`（小于）、`LE`（小于等于）、`IN`（属于集合）、`NIN`（不属于集合）、`LIKE`（包含匹配）、`SW`（前缀匹配）、`EW`（后缀匹配）、`IS_NULL`（为空）和 `NOT_NULL`（不为空）；兼容别名 `GTE`=`GE`、`LTE`=`LE`，服务端规范化为 `GE`/`LE`。具体可用范围见下表。 |
| `value` | Object | 单值条件使用，例如 `EQ`、`GE`、`LIKE`。 |
| `values` | Array | `IN`、`NIN` 使用；不能同时传 `value`。 |
| `datatypeIri` | String | 可选数据类型标识，用于数据属性值校验与归一化。 |

| `constraintType` | 必需字段 | 说明 |
| --- | --- | --- |
| `CLASS` | `classIri`、`operator` | 判断实例是否属于指定类；只支持 `EQ`、`NE`，且不传 `value`、`values`。 |
| `DATA_PROPERTY` | `classIri`、`propertyIri`、`operator`、`value` / `values` | 按指定类的数据属性值比较；数值/日期通常可使用 `GT`、`GE`、`LT`、`LE`，字符串通常可使用 `LIKE`、`SW`（前缀匹配）、`EW`（后缀匹配）。 |
| `OBJECT_PROPERTY` | `classIri`、`relationIri`、`operator`、`value` / `values` | 按指定类的对象属性值比较；值为目标实例 IRI，支持 `EQ`、`NE`、`IN`、`NIN`、`IS_NULL`、`NOT_NULL`。 |

操作符与值字段的对应关系如下：

| 操作符 | 适用类型 | 值字段 | 说明 |
| --- | --- | --- | --- |
| `EQ`、`NE`、`GT`、`GE`、`LT`、`LE`、`LIKE`、`SW`、`EW` | `DATA_PROPERTY` | `value` | 单值比较；大小比较仅适用于整数、数值、日期和日期时间；字符串匹配仅适用于字符串。 |
| `IN`、`NIN` | `DATA_PROPERTY` 或 `OBJECT_PROPERTY` | `values` | 集合比较；必须传非空数组，不能同时传 `value`。数据属性数组元素按属性数据类型校验，对象属性数组元素必须为目标实例 IRI。 |
| `EQ`、`NE` | `CLASS` 或 `OBJECT_PROPERTY` | `value` | 类断言或对象属性单值比较；`CLASS` 不传 `value`，对象属性的值为目标实例 IRI。 |
| `IS_NULL`、`NOT_NULL` | `DATA_PROPERTY` 或 `OBJECT_PROPERTY` | 无 | 为空/不为空判断；不能传 `value` 或 `values`。 |

`CLASS` 仅支持 `EQ`、`NE`，且只需 `classIri` 和 `operator`，不传 `propertyIri`、`relationIri`、`value`、`values` 或 `datatypeIri`。`DATA_PROPERTY` 必须传 `propertyIri`，且该属性必须属于 `classIri`；`OBJECT_PROPERTY` 必须传 `relationIri`，且该关系的源类别必须为 `classIri`。`IS_NULL`、`NOT_NULL` 不传 `value`、`values`。实际提交时，`factScope` 和 `factConstraints` 分别按其自身的校验规则确定字段是否可用，详见后续章节。

#### 12.2.3 请求字段说明：`factScope` 锚点实例范围

`factScope` 用于从事实源查询本次推理的锚点实例。服务端根据当前有效 SWRL 规则分析锚点实例需要的关联事实，以及无法由锚点现有关系定位的候选事实。关联实例和候选实例不需要满足锚点的 `factScope` 条件，但只加载规则实际使用的类别、属性和关系。

固定点推理使用完整的相关事实集；接口最终仅返回以 `factScope` 命中锚点实例为主体的新推理事实。未匹配到锚点实例时返回空结果，不扩大为全量事实查询。

`factScope` 的类、属性和关系标识均以**当前已发布本体**为准进行校验。对于 `ONTOLOGY_INSTANCE` 事实源，范围条件在锚点查询阶段执行，而不是对关联实例、候选实例及关系边进行全局裁剪。

例如，以下范围将“Customer”类中 `province` 为“北京”的实例选为锚点；规则需要的家庭、标签、商品等关联或候选实例可以不具备相同的 `province` 值：

```json
{
  "projectId": "project_001",
  "taskName": "北京客户 SWRL 推理",
  "factSourceType": "ONTOLOGY_INSTANCE",
  "useAllRules": false,
  "ruleIds": ["rule_001"],
  "factScope": {
    "logic": "AND",
    "items": [
      {
        "constraintType": "CLASS",
        "classIri": "http://example.com/ontology#Customer",
        "operator": "EQ"
      },
      {
        "constraintType": "DATA_PROPERTY",
        "classIri": "http://example.com/ontology#Customer",
        "propertyIri": "http://example.com/ontology#province",
        "operator": "EQ",
        "value": "北京",
        "datatypeIri": "http://www.w3.org/2001/XMLSchema#string"
      }
    ]
  }
}
```

同步执行响应的 `data.factScope` 与任务详情的 `data.factScope` 会回显服务端记录的范围结构；其中 `value`、`values` 会按现有任务审计规则脱敏为 `[REDACTED]`。

#### 12.2.4 请求字段说明：`factConstraints` 任务内规则常量覆盖

`factConstraints` 用于覆盖当前有效 SWRL 规则 Body 中 builtin 的默认常量。请求操作符必须与原规则 builtin 操作符一致；服务端只替换本次任务私有规则中的常量参数，不修改已发布规则。未提交覆盖值时继续使用规则中的默认常量。

使用步骤：

1. 按本次准备执行的规则范围调用 `POST /ontology/reasoning/swrl/constraint/metadata`；
2. 使用响应 `data.fields` 中返回的类/属性 IRI、`datatypeIri` 和原规则 builtin 对应的 `operators` 构造覆盖条件；
3. 将条件树作为 `/swrl/execute` 的 `factConstraints` 提交。

服务端会校验数据属性变量与 builtin 常量的绑定关系。目标数据属性必须在当前有效规则 Body 中绑定到变量，并且该变量必须唯一对应一个带字面量常量的 builtin；无法唯一定位时拒绝请求。`factConstraints` 不用于筛选结果主体，也不能替代 `factScope` 选择锚点实例。

例如，已发布规则 Body 包含：

```text
Person(?person) ^ age(?person, ?age) ^ swrlb:greaterThanOrEqual(?age, 18)
```

提交以下任务内覆盖：

```json
{
  "factConstraints": {
    "logic": "AND",
    "items": [
      {
        "constraintType": "DATA_PROPERTY",
        "classIri": "http://example.com/ontology#Person",
        "propertyIri": "http://example.com/ontology#age",
        "operator": "GE",
        "value": 25,
        "datatypeIri": "http://www.w3.org/2001/XMLSchema#integer"
      }
    ]
  }
}
```

本次任务执行的私有规则将变为 `swrlb:greaterThanOrEqual(?age, 25)`，即仅把默认常量从 `18` 覆盖为 `25`；已发布规则仍保留 `swrlb:greaterThanOrEqual(?age, 18)`。

操作符不能借此改变规则语义。例如原规则使用 `swrlb:greaterThanOrEqual`（`GE`），请求却提交 `"operator": "LE"` 时，服务端拒绝请求并提示操作符必须与 SWRL 规则保持一致，而不会将其改写为 `swrlb:lessThanOrEqual(?age, 25)`。

| 对比项 | `factConstraints` | `factScope` |
| --- | --- | --- |
| 校验依据 | 当前有效规则 Body 中数据属性变量与 builtin 常量的唯一绑定 | 当前已发布本体的类、数据属性和对象属性元数据 |
| 作用对象 | 本次任务私有规则中的 builtin 默认常量 | 本次推理的锚点实例 |
| 生效时机 | 规划和执行前复制规则并替换常量 | 事实加载时查询锚点实例 |
| 对支持事实的影响 | 不裁剪事实，也不筛选结果主体 | 关联实例和候选实例无需满足锚点条件，但仅加载规则所需事实 |

#### 12.2.5 请求字段说明：`factGraph` 内联事实图

当 `factSourceType` 为 `FACT_GRAPH` 时，`factGraph` 必填。调用方可直接提交参与本次推理的实例节点、类别断言、数据属性事实和对象关系事实，不读取已发布实例、业务库或图数据库数据。

```json
{
  "factSourceType": "FACT_GRAPH",
  "useAllRules": false,
  "ruleIds": ["rule_001"],
  "factGraph": {
    "nodes": [
      {
        "instanceIri": "http://example.com/instance/zhangsan",
        "classIris": ["http://example.com/ontology#Person"],
        "dataProperties": [
          {
            "propertyIri": "http://example.com/ontology#age",
            "value": 28,
            "datatypeIri": "http://www.w3.org/2001/XMLSchema#integer"
          }
        ]
      },
      {
        "instanceIri": "http://example.com/instance/lisi",
        "classIris": ["http://example.com/ontology#Person"],
        "dataProperties": []
      }
    ],
    "edges": [
      {
        "sourceIri": "http://example.com/instance/zhangsan",
        "relationIri": "http://example.com/ontology#knows",
        "targetIri": "http://example.com/instance/lisi"
      }
    ]
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `factGraph.nodes` | Array | 否 | 实例节点列表；可以为空 |
| `nodes[].instanceIri` | String | 是 | 实例绝对 IRI |
| `nodes[].classIris` | Array<String> | 否 | 该实例的类别绝对 IRI 列表，生成类别断言事实 |
| `nodes[].dataProperties` | Array | 否 | 该实例的数据属性事实列表 |
| `dataProperties[].propertyIri` | String | 是 | 数据属性绝对 IRI |
| `dataProperties[].value` | String / Number / Boolean | 是 | 标量属性值；不允许 `null`、数组或对象 |
| `dataProperties[].datatypeIri` | String | 否 | 数据类型绝对 IRI |
| `factGraph.edges` | Array | 否 | 对象关系事实列表；可以为空 |
| `edges[].sourceIri` | String | 是 | 关系主体实例绝对 IRI |
| `edges[].relationIri` | String | 是 | 对象关系绝对 IRI |
| `edges[].targetIri` | String | 是 | 关系客体实例绝对 IRI |

节点、类别、属性和关系 IRI 必须为绝对 IRI。边的端点允许只出现在 `edges` 中；但若同时传入 `factScope`，范围匹配和关系裁剪仍会在内联图转换为标准事实后执行。

#### 12.2.6 请求字段说明：`factOverlay` 客户端数据属性覆盖

`factOverlay` 用于在非 `FACT_GRAPH` 事实源中，以调用方传入的实时值替换已加载实例的数据属性。它不是独立事实源，不能新增实例、类别断言或对象关系。

```json
{
  "factOverlay": {
    "dataProperties": [
      {
        "instanceIri": "http://example.com/instance/customer_001",
        "propertyIri": "http://example.com/ontology#creditScore",
        "value": 780,
        "datatypeIri": "http://www.w3.org/2001/XMLSchema#integer"
      }
    ]
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `factOverlay.dataProperties` | Array | 否 | 数据属性覆盖列表；为空时不执行覆盖。 |
| `dataProperties[].instanceIri` | String | 是 | 已被事实范围保留的实例绝对 IRI。 |
| `dataProperties[].propertyIri` | String | 是 | 当前已发布本体中的数据属性绝对 IRI。 |
| `dataProperties[].value` | String / Number / Boolean | 是 | 非空标量覆盖值。 |
| `dataProperties[].datatypeIri` | String | 否 | 传入时必须与已发布属性的数据类型一致。 |

执行顺序固定为：**加载基础事实 → `factScope` 过滤 → `factOverlay` 替换属性 → SWRL 推理**。因此覆盖值不参与范围匹配：范围外实例不会因覆盖值重新进入推理；已保留实例即使覆盖后不再满足范围条件，也仍以覆盖后的值参与推理。

同一 `(instanceIri, propertyIri)` 的覆盖按“整属性替换”处理：服务端会移除该实例该属性的所有基础值，再写入调用方提交的最终值集。单值属性只允许一个去重后的值；多值属性可以提交多个值。同步响应和任务详情仅回显覆盖结构，所有 `value` 均脱敏为 `[REDACTED]`。

`FACT_GRAPH` 与 `factOverlay` 互斥：使用 `FACT_GRAPH` 时，调用方应直接在 `factGraph.nodes[].dataProperties` 中提交最终属性值，不能提交 `factOverlay`；其他事实源不能提交 `factGraph`。

### 12.3 响应体

#### 12.3.1 响应示例

以下示例为对象属性断言结果。`results` 中每条结论统一以 `subject`、`relation`、`object` 三个 term 表示，不再使用分散的 `subjectIri`、`objectIri`、`relationIri` 字段。

```json
{
  "code": 200,
  "message": "处理成功",
  "data": {
    "taskId": "swrl_task_001",
    "taskCode": "SWRL_202607260001",
    "taskName": "客户关系 SWRL 推理",
    "projectId": "project_001",
    "factSourceType": "ONTOLOGY_INSTANCE",
    "factScope": null,
    "status": "SUCCESS",
    "explicitRuleIds": ["rule_001"],
    "dependencyRuleIds": [],
    "effectiveRuleIds": ["rule_001"],
    "results": [
      {
        "resultId": "swrl-result-001",
        "factKey": "OBJECT_PROPERTY|...",
        "resultType": "OBJECT_PROPERTY_ASSERTION",
        "subject": {
          "iri": "http://example.com/instance/zhangsan",
          "code": "PERSON_001",
          "name": "张三",
          "classIri": "http://example.com/ontology#Person",
          "classCode": "PERSON",
          "className": "人员",
          "properties": [
            {
              "propertyIri": "http://example.com/ontology#age",
              "propertyName": "年龄",
              "value": 28,
              "datatypeIri": "http://www.w3.org/2001/XMLSchema#integer"
            }
          ]
        },
        "relation": {
          "iri": "http://example.com/ontology#knows",
          "code": "KNOWS",
          "name": "认识",
          "properties": []
        },
        "object": {
          "iri": "http://example.com/instance/lisi",
          "code": "PERSON_002",
          "name": "李四",
          "classIri": "http://example.com/ontology#Person",
          "classCode": "PERSON",
          "className": "人员",
          "properties": []
        },
        "sourceRuleIds": ["rule_001"],
        "round": 2
      }
    ],
    "evidenceGraph": {},
    "provenance": [],
    "diagnostics": [],
    "statistics": {},
    "logs": []
  }
}
```

#### 12.3.2 响应字段说明

响应统一包装为 `CommonResult<SwrlExecuteResponse>`，其中 `code` 为业务状态码，`message` 为处理消息，实际推理响应位于 `data`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | Integer | 业务状态码；成功时为 `200` |
| `message` | String | 处理消息；成功时为“处理成功” |
| `data` | Object | 同步 SWRL 推理执行结果 |
| `data.taskId` | String | 本次推理审计任务 ID；可用于调用 `/ontology/reasoning/swrl/task/get` 查询任务详情 |
| `data.taskCode` | String | 本次推理任务编码 |
| `data.taskName` | String | 本次推理任务名称；未传入时由服务端生成默认名称 |
| `data.projectId` | String | 本体项目 ID |
| `data.factSourceType` | String | 实际使用的事实来源类型：`ONTOLOGY_INSTANCE`、`BUSINESS_DATABASE`、`GRAPH_DATABASE`、`ONTOLOGY_FILE` 或 `FACT_GRAPH` |
| `data.factScope` | Object | 本次任务记录的事实范围条件树；未提交时为空。为避免在任务回放中暴露筛选值，其中 `value`、`values` 会被脱敏为 `[REDACTED]` |
| `data.factOverlay` | Object | 本次任务记录的数据属性覆盖；未提交时为空。覆盖值会被脱敏为 `[REDACTED]` |
| `data.status` | String | 任务状态：`SUCCESS`、`PARTIAL_SUCCESS` 或 `FAILED` |
| `data.explicitRuleIds` | Array<String> | 调用方显式选择的规则 ID；选择执行全部规则时为空数组 |
| `data.dependencyRuleIds` | Array<String> | 为满足显式规则依赖而自动补入的规则 ID |
| `data.effectiveRuleIds` | Array<String> | 实际参与执行的完整规则 ID 列表，按依赖顺序排列 |
| `data.results` | Array | 本次规则证明得到的推理结论列表；字段见第 12.3.3 节 |
| `data.evidenceGraph` | Object | 由返回结论及其溯源构建的证据图；字段见下文 |
| `data.provenance` | Array | 每条结论对应的规则证明与匹配事实；字段见下文 |
| `data.diagnostics` | Array | 执行警告、规则拒绝、事实加载或截断等诊断信息；字段见下文 |
| `data.statistics` | Object | 事实加载与固定点推理统计信息；字段见下文 |
| `data.logs` | Array | 本次任务的阶段日志；字段见下文 |

##### `evidenceGraph` 证据图字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.evidenceGraph.nodes` | Array | 证据图节点列表；可能因服务端治理上限被截断 |
| `data.evidenceGraph.nodes[].id` | String | 节点唯一标识 |
| `data.evidenceGraph.nodes[].type` | String | 节点类型：`FACT`（事实）、`RULE`（规则）或 `SOURCE`（事实来源）。 |
| `data.evidenceGraph.nodes[].label` | String | 节点展示名称 |
| `data.evidenceGraph.nodes[].attributes` | Object | 节点附加属性；属性内容随节点类型变化 |
| `data.evidenceGraph.edges` | Array | 证据图边列表 |
| `data.evidenceGraph.edges[].id` | String | 边唯一标识 |
| `data.evidenceGraph.edges[].source` | String | 起点节点 ID |
| `data.evidenceGraph.edges[].target` | String | 终点节点 ID |
| `data.evidenceGraph.edges[].type` | String | 边类型：`USED`（规则使用前提事实）、`PRODUCED`（规则产生结论）或 `ORIGINATED`（事实来自来源）。 |
| `data.evidenceGraph.edges[].attributes` | Object | 边附加属性；属性内容随边类型变化 |

`nodes[].type` 保持 `FACT`、`RULE`、`SOURCE` 英文机器类型；前端使用 `label` 和 `attributes` 展示。`RULE.attributes` 包含 `ruleId`、`ruleCode`、`ruleName`，`label` 按规则名称、编码、ID 回退。`FACT.attributes` 包含完整的 `subject`、`relation`、`object` Term，以及 `resultType`、`factKey`、`round`、`inferred`。`SOURCE.attributes` 包含 `sourceType`、`sourceName`、`resourceType`、`resourceName`、`sourceReference`：业务数据库和图数据库展示配置连接资源名及资源类型，不反查真实表或图库名；本体文件展示实际文件名。

边类型保持英文机器值 `USED`、`PRODUCED`、`ORIGINATED`，前端分别展示为“使用前提”“推导结论”“来源于”。

##### `provenance` 推理依据字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.provenance[].resultId` | String | 被证明的结论 `data.results[].resultId` |
| `data.provenance[].ruleId` | String | 本次证明使用的 SWRL 规则 ID |
| `data.provenance[].ruleCode` | String | 推导规则编码；历史任务或无法解析规则元数据时为空 |
| `data.provenance[].ruleName` | String | 推导规则名称；历史任务或无法解析规则元数据时为空 |
| `data.provenance[].round` | Integer | 生成该结论的固定点推理轮次 |
| `data.provenance[].variableBindings` | Object | 原始规则变量绑定，键为变量名，值为运行时绑定值；字面量可能表示为包含 `value`、`datatypeIri`、`languageTag` 的对象。该字段保持审计原始值语义。 |
| `data.provenance[].variableBindingTerms` | Object | 实体变量的可展示绑定信息，键为变量名，值为实例 Term，包含 `iri`、`code`、`name`、所属类及属性；字面量变量不在此字段中，仍读取 `variableBindings`。 |
| `data.provenance[].matchedFacts` | Array | 本条规则 Body 实际匹配到的事实，元素结构与 `data.results[]` 相同；`ONTOLOGY_INSTANCE` 事实源下会补齐实体、关系、类和字面量的展示信息。 |
| `data.provenance[].sourceReferences` | Array<Object> | 匹配事实的来源定位信息；字段随事实来源类型变化，例如内联事实图中的节点或边索引 |
| `data.provenance[].executorTrace` | Array<Object> | 执行器匹配与 builtin 计算轨迹；用于调试和可解释性展示，字段随原子类型变化 |

示例：

```json
{
  "resultId": "swrl-result-001",
  "ruleId": "rule_001",
  "ruleCode": "HIGH_VALUE_CUSTOMER",
  "ruleName": "高价值客户识别",
  "round": 1,
  "variableBindings": {
    "customer": "http://example.com/instance/customer_001",
    "score": {
      "value": 780,
      "datatypeIri": "http://www.w3.org/2001/XMLSchema#integer"
    }
  },
  "variableBindingTerms": {
    "customer": {
      "iri": "http://example.com/instance/customer_001",
      "code": "CUST_001",
      "name": "张三",
      "classIri": "http://example.com/ontology#Customer",
      "classCode": "CUSTOMER",
      "className": "客户",
      "properties": []
    }
  }
}
```

##### `diagnostics`、`statistics` 与 `logs` 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.diagnostics[].level` | String | 诊断级别：`INFO`、`WARN` 或 `ERROR` |
| `data.diagnostics[].code` | String | 稳定诊断代码，例如 `FIXED_POINT_ROUND`、`MAX_ROUNDS_REACHED` |
| `data.diagnostics[].message` | String | 面向调用方的诊断说明 |
| `data.diagnostics[].ruleId` | String | 关联规则 ID；任务级诊断时为空 |
| `data.statistics.loadedFactCount` | Integer | 进入固定点执行器的初始事实数 |
| `data.statistics.inferredFactCount` | Integer | 本次推导得到的去重后事实数 |
| `data.statistics.deduplicatedFactCount` | Integer | 事实加载或推导期间识别出的重复事实数量 |
| `data.statistics.executedRuleCount` | Integer | 成功准备并参与执行的规则数 |
| `data.statistics.roundCount` | Integer | 已执行的固定点推理轮数 |
| `data.statistics.anchorInstanceCount` | Integer | `ONTOLOGY_INSTANCE` 来源下命中 `factScope` 的锚点实例数；其他来源通常为 `0` |
| `data.statistics.linkedInstanceCount` | Integer | 为锚点规则匹配补充加载的关联实例数 |
| `data.statistics.candidateInstanceCount` | Integer | 为规则匹配补充加载的候选实例数 |
| `data.statistics.loadedClassFactCount` | Integer | 已加载的类断言事实数 |
| `data.statistics.loadedDataPropertyFactCount` | Integer | 已加载的数据属性事实数 |
| `data.statistics.loadedObjectPropertyFactCount` | Integer | 已加载的对象属性事实数 |
| `data.statistics.costMs` | Long | 本次固定点推理耗时，单位毫秒 |
| `data.logs[].phase` | String | 日志阶段，例如 `FACT_LOAD`、`FACT_OVERLAY` |
| `data.logs[].level` | String | 日志级别，例如 `INFO`、`WARN`、`ERROR` |
| `data.logs[].message` | String | 日志内容 |

#### 12.3.3 `results` 结果字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.results` | Array | 推理得到的事实结论列表 |
| `data.results[].resultId` | String | 结果记录 ID |
| `data.results[].factKey` | String | 用于唯一标识结果事实的内部键 |
| `data.results[].resultType` | String | 结果类型：`OBJECT_PROPERTY_ASSERTION`、`DATA_PROPERTY_ASSERTION` 或 `CLASS_ASSERTION` |
| `data.results[].subject` | Object | 结论主体 term，通常为实例 |
| `data.results[].relation` | Object | 结论关系 term；类断言时为 `rdf:type` |
| `data.results[].object` | Object | 结论客体 term，可为实例、字面量或类 |
| `data.results[].sourceRuleIds` | Array<String> | 推导出该结果的规则 ID 列表 |
| `data.results[].round` | Integer | 固定点推理轮次 |

所有合法的推理事实均包含语义完整的 `subject`、`relation`、`object`。字面量值 `0`、`false` 和空字符串也是有效客体，客户端不得按 JavaScript 真值判断其是否缺失。

##### Term 通用字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `iri` | String | IRI 资源标识；实例、关系和类 term 使用；字面量 object 通常为空 |
| `code` | String | 已解析到本体元数据时的业务编码 |
| `name` | String | 已解析到本体元数据时的名称；字面量 object 为便于展示的文本值 |
| `properties` | Array | 实例 term 的已存储属性列表；非实例 term 通常为空数组 |
| `properties[].propertyIri` | String | 实例属性 IRI |
| `properties[].propertyName` | String | 实例属性名称 |
| `properties[].value` | Object | 实例属性值 |
| `properties[].datatypeIri` | String | 实例属性值的数据类型 IRI |
| `value` | Object | 仅字面量 object 使用的原始值 |
| `datatypeIri` | String | 仅字面量 object 使用的数据类型 IRI |
| `languageTag` | String | 仅语言字面量 object 使用的语言标签 |

##### 实例所属类字段

当事实来源为 `ONTOLOGY_INSTANCE`，且 term 成功匹配到当前项目、当前发布版本中的实例时，实例 term 额外包含以下所属类核心标识：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `classIri` | String | 实例所属类 IRI |
| `classCode` | String | 实例所属类编码 |
| `className` | String | 实例所属类名称；类名称为空时使用类别名 |

仅返回所属类的核心身份信息，不在每个实例 term 中重复返回类描述、注释等大字段。无法解析实例所属类时，这三个字段为空且不影响推理结果返回。

#### 12.3.4 不同结果类型的 term 语义

| `resultType` | `subject` | `relation` | `object` |
| --- | --- | --- | --- |
| `OBJECT_PROPERTY_ASSERTION` | 实例 term，可包含所属类字段及 `properties` | 对象关系 term | 实例 term，可包含所属类字段及 `properties` |
| `DATA_PROPERTY_ASSERTION` | 实例 term，可包含所属类字段及 `properties` | 数据属性 term | 字面量 term，使用 `value`、`datatypeIri`、`languageTag`，不包含所属类字段 |
| `CLASS_ASSERTION` | 实例 term，可包含所属类字段及 `properties` | `rdf:type` term | 类 term，使用自身 `iri`、`code`、`name`，不重复设置 `classIri`、`classCode`、`className` |

`relation` 不属于实例，因此不会返回 `classIri`、`classCode`、`className`。非 `ONTOLOGY_INSTANCE` 事实来源不会因 IRI 偶然相同而自动映射为已发布实例，也不会补充实例属性或所属类字段。

#### 12.3.5 三种断言结果格式

以下示例均为 `data.results[]` 中的一条完整结果记录。

##### 12.3.5.1 对象属性断言：`OBJECT_PROPERTY_ASSERTION`

对象属性断言表示一个实例与另一个实例之间的关系；`subject` 和 `object` 都是实例 term。

```json
{
  "resultId": "swrl-result-001",
  "factKey": "OBJECT_PROPERTY|...",
  "resultType": "OBJECT_PROPERTY_ASSERTION",
  "subject": {
    "iri": "http://example.com/instance/zhangsan",
    "code": "PERSON_001",
    "name": "张三",
    "classIri": "http://example.com/ontology#Person",
    "classCode": "PERSON",
    "className": "人员",
    "properties": [
      {
        "propertyIri": "http://example.com/ontology#age",
        "propertyName": "年龄",
        "value": 28,
        "datatypeIri": "http://www.w3.org/2001/XMLSchema#integer"
      }
    ]
  },
  "relation": {
    "iri": "http://example.com/ontology#knows",
    "code": "KNOWS",
    "name": "认识",
    "properties": []
  },
  "object": {
    "iri": "http://example.com/instance/lisi",
    "code": "PERSON_002",
    "name": "李四",
    "classIri": "http://example.com/ontology#Person",
    "classCode": "PERSON",
    "className": "人员",
    "properties": []
  },
  "sourceRuleIds": ["rule_001"],
  "round": 2
}
```

##### 12.3.5.2 数据属性断言：`DATA_PROPERTY_ASSERTION`

数据属性断言的 `object` 是字面量，不是实例；其值由 `value`、`datatypeIri` 和可选的 `languageTag` 表示。

```json
{
  "resultId": "swrl-result-002",
  "factKey": "DATA_PROPERTY|...",
  "resultType": "DATA_PROPERTY_ASSERTION",
  "subject": {
    "iri": "http://example.com/instance/zhangsan",
    "code": "PERSON_001",
    "name": "张三",
    "classIri": "http://example.com/ontology#Person",
    "classCode": "PERSON",
    "className": "人员",
    "properties": []
  },
  "relation": {
    "iri": "http://example.com/ontology#age",
    "code": "AGE",
    "name": "年龄",
    "properties": []
  },
  "object": {
    "iri": null,
    "code": null,
    "name": "28",
    "properties": [],
    "value": 28,
    "datatypeIri": "http://www.w3.org/2001/XMLSchema#integer",
    "languageTag": null
  },
  "sourceRuleIds": ["rule_002"],
  "round": 1
}
```

##### 12.3.5.3 类断言：`CLASS_ASSERTION`

类断言表示实例属于某个类。`relation` 固定为 `rdf:type`，`object` 是断言结论中的类自身，而不是实例的所属类嵌套信息。

```json
{
  "resultId": "swrl-result-003",
  "factKey": "CLASS|...",
  "resultType": "CLASS_ASSERTION",
  "subject": {
    "iri": "http://example.com/instance/zhangsan",
    "code": "PERSON_001",
    "name": "张三",
    "classIri": "http://example.com/ontology#Person",
    "classCode": "PERSON",
    "className": "人员",
    "properties": []
  },
  "relation": {
    "iri": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
    "code": "RDF_TYPE",
    "name": "类型",
    "properties": []
  },
  "object": {
    "iri": "http://example.com/ontology#Adult",
    "code": "ADULT",
    "name": "成年人",
    "properties": []
  },
  "sourceRuleIds": ["rule_003"],
  "round": 1
}
```

### 12.4 查询同步 SWRL 任务详情

| 项目 | 内容 |
| --- | --- |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/reasoning/swrl/task/get` |

请求体：

```json
{
  "projectId": "project_001",
  "taskId": "swrl_task_001"
}
```

#### 12.4.1 请求字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 本体项目 ID。 |
| `taskId` | String | 是 | 同步 SWRL 推理接口返回的任务 ID。 |

#### 12.4.2 响应字段说明

任务详情响应为 `CommonResult<SwrlTaskDetailDto>`。除第 12.3.2 节中已定义的 `results`、`provenance`、`diagnostics`、`statistics`、`evidenceGraph`、`logs` 和 `factScope` 字段外，还包含以下任务审计字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.projectId` | String | 本体项目 ID。 |
| `data.taskId` | String | 同步 SWRL 推理任务 ID。 |
| `data.taskCode` | String | 服务端生成的任务编码。 |
| `data.taskName` | String | 任务名称；未传时为服务端默认名称。 |
| `data.taskStatus` | String | 任务状态：`RUNNING`（执行中）、`SUCCESS`（成功完成）、`PARTIAL_SUCCESS`（部分成功，结果可能受事实加载治理或其他告警影响）或 `FAILED`（失败）。 |
| `data.factSourceType` | String | 实际使用的事实来源类型，码值见第 12.2.2 节。 |
| `data.resultCount` | Integer | 本次推理返回的结论数量。 |
| `data.submitTime` | String | 任务提交时间，格式为 `yyyy-MM-dd HH:mm:ss`。 |
| `data.endTime` | String | 任务结束时间；任务未结束时为空，格式为 `yyyy-MM-dd HH:mm:ss`。 |
| `data.explicitRuleIds` | Array<String> | 调用方显式选择的规则 ID；使用全部规则时为全部启用规则 ID 列表。 |
| `data.dependencyRuleIds` | Array<String> | 因规则依赖自动补入的规则 ID 列表。 |
| `data.effectiveRuleIds` | Array<String> | 最终实际执行的完整规则 ID 列表，按依赖顺序排列。 |
| `data.factSource` | Object | 任务执行时记录的事实来源可用性信息。 |
| `data.factSource.factSourceType` | String | 事实来源类型码值，见第 12.2.2 节。 |
| `data.factSource.available` | Boolean | 执行前该事实来源是否可用。 |
| `data.factSource.resourceName` | String | 服务端识别到的来源资源名称，例如当前发布本体实例、本体文件名或请求事实图。 |
| `data.factSource.diagnostics` | Array | 事实来源可用性诊断，元素字段与 `data.diagnostics[]` 一致。 |
| `data.factConstraints` | Object | 服务端记录的规则常量覆盖条件树；其中 `value`、`values` 已脱敏为 `[REDACTED]`。未提交时为空。 |
| `data.factScope` | Object | 服务端记录的全局事实范围条件树；其中 `value`、`values` 已脱敏为 `[REDACTED]`。未提交时为空。 |
| `data.factOverlay` | Object | 服务端记录的数据属性覆盖；其中全部 `value` 已脱敏为 `[REDACTED]`。未提交时为空。 |

该接口返回的 `data.results` 与第 12.3.3 节定义的统一三元组结果格式完全一致，可用于查询历史同步 SWRL 任务的结果详情。

### 12.5 查询 SWRL 事实约束元数据接口

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 查询 SWRL 事实约束元数据 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/reasoning/swrl/constraint/metadata` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

请求体：

```json
{
  "projectId": "project_001",
  "useAllRules": false,
  "ruleIds": ["rule_001"]
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 本体项目 ID。 |
| `useAllRules` | Boolean | 是 | 是否按全部启用规则聚合元数据；为 `false` 时按 `ruleIds` 聚合。 |
| `ruleIds` | Array<String> | 条件必填 | `useAllRules=false` 时至少传一个已启用规则 ID；`useAllRules=true` 时可传空数组。 |

响应 `data` 字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data.projectId` | String | 本体项目 ID。 |
| `data.useAllRules` | Boolean | 本次元数据聚合是否使用全部启用规则。 |
| `data.explicitRuleIds` | Array<String> | 调用方显式选择的规则 ID。 |
| `data.dependencyRuleIds` | Array<String> | 因规则依赖自动补入的规则 ID。 |
| `data.effectiveRuleIds` | Array<String> | 最终参与元数据聚合的规则 ID。 |
| `data.fields` | Array | 可用于构造 `factConstraints` 的字段元数据。 |
| `data.fields[].fieldKey` | String | 字段稳定标识。 |
| `data.fields[].constraintType` | String | 字段类型：`DATA_PROPERTY` 或 `OBJECT_PROPERTY`。 |
| `data.fields[].classIri` | String | 字段所属类 IRI。 |
| `data.fields[].propertyIri` | String | 数据属性 IRI；对象属性字段为空。 |
| `data.fields[].relationIri` | String | 对象属性 IRI；数据属性字段为空。 |
| `data.fields[].label` | String | 字段展示名称。 |
| `data.fields[].datatypeIri` | String | 字段数据类型 IRI；对象属性通常为 `IRI`。 |
| `data.fields[].operators` | Array<String> | 该字段允许的操作符码值，具体值以服务端返回为准。 |
| `data.fields[].sourceRuleIds` | Array<String> | 该字段来源规则 ID 列表。 |
| `data.fields[].required` | Boolean | 是否要求调用方提交该字段；通常为 `false`。 |
| `data.diagnostics` | Array | 元数据聚合诊断；字段结构同第 12.3.2 节的 `data.diagnostics[]`。 |

调用方应优先使用该接口返回的 `operators` 和 `datatypeIri` 构造 `factConstraints`，不要自行推断规则字段约束。

### 12.6 查询 SWRL 事实来源列表接口

| 项目 | 内容 |
| --- | --- |
| 接口名称 | 查询 SWRL 事实来源列表 |
| 请求方式 | `POST` |
| 请求地址 | `http://10.88.212.165:9999/data-onto-server/ontology/reasoning/swrl/fact-source/list` |
| 请求格式 | `application/json` |
| 响应格式 | `application/json` |

请求体：

```json
{ "projectId": "project_001" }
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `projectId` | String | 是 | 本体项目 ID。 |

响应 `data` 为事实来源选项数组：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `data` | Array | 事实来源选项列表，包含全部已注册来源类型。 |
| `data[].factSourceType` | String | 事实来源类型码值，见第 12.2.2 节。 |
| `data[].available` | Boolean | 当前项目当前已发布版本是否可以使用该来源。 |
| `data[].resourceName` | String | 可展示的来源资源名称；不可用时可能为空。 |
| `data[].diagnostics` | Array | 来源可用性诊断；元素包含 `level`、`code`、`message` 和可选的 `ruleId`。 |

调用方可使用 `available=true` 的 `factSourceType` 作为 12.2 节请求的来源类型；不可用来源不要提交执行。

---

## 13. 典型调用流程

```text
1. 调用“查询已发布本体列表”，获得 projectId。
2. 调用“加载当前已发布物化本体模型”，查看类、属性、关系及当前有限实例数据。
3. 调用“查询已发布本体图检索能力”，获取允许执行的查询语言。
4. 任选以下方式生成查询语句：
   - 调用“自然语言生成已发布本体图查询”；
   - 由调用方自行构造查询语句。
5. 调用“执行已发布本体图查询”，获取表格和图结构结果。
6. 若需要规则推理：调用 SWRL 规则列表和详情接口确定规则与输入条件。
7. 调用“提交已发布本体推理任务”，获得 taskId。
8. 调用“查询已发布本体推理工作台”获取任务状态、结果、溯源及日志；必要时调用取消接口。
```
