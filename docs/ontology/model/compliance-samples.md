# 配置合规体检 —— 验证样例（R-C03 ~ R-C09）

> 适用接口：`POST /api/v1/product-ontology/config/compliance`
> 服务地址：`http://localhost:6174`
> 推理引擎：Openllet SWRL（`product.ttl`），响应 `engine` 字段应为 `openllet-swrl`
>
> 本文所有「预期结果」均由 `ComplianceRuleEngine.checkCompliance` 实跑得出，非推测。

---

## 一、如何验证

### 1. 启动服务

```powershell
cd backend-app
& "D:\APP\WORK\apache-maven-3.6.3\bin\mvn.cmd" -o spring-boot:run
```

### 2. 通用调用模板

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft": { ...草稿字段... }}'
```

> `draft` 是唯一需要的入参。`offeringId` / `text` 可留空——留空时走「内存草稿」路径，
> 直接把 `draft` 交给推理器，不查库。

### 3. 判定口径

响应关键字段：

| 字段 | 含义 |
| --- | --- |
| `compliancePass` | 是否通过。判据：无 `HIGH` 级 issue 且无 `R-C06` |
| `canSubmit` | 同 `compliancePass` |
| `engine` | `openllet-swrl` 表示本体推理生效；`java-rules` 表示已回退 Java |
| `issues` | 违规清单 |
| `appliedRules` | 通过时为 `["R-C08"]`；不通过时为命中的规则号 |
| `firedRules` | SWRL 实际点火的规则 |
| `reasoning_chain` | 逐规则推理链（规则→条件→结论） |

### 4. 基线草稿（所有样例的公共基础）

```json
{
  "offeringId": "OF-SAMPLE-BASE",
  "offeringName": "样例资费",
  "messageRootKey": "broadBandMainPrc",
  "fixedFeeAmount": 128,
  "oneTimeFee": 0,
  "channelScope": "全渠道"
}
```

以下每个样例只列出**相对基线的增量字段**，实际请求需把两段 JSON 合并。

---

## 二、直接可用的完整样例（按顺序复制即可）

为方便验证，下面每条都是**完整请求体**，不依赖基线拼接。

### 样例 0：合规通过（对照组）

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft":{"offeringId":"OF-SAMPLE-BASE","offeringName":"样例资费","messageRootKey":"broadBandMainPrc","fixedFeeAmount":128,"oneTimeFee":0,"channelScope":"全渠道"}}'
```

**预期**

```
compliancePass = true
engine         = openllet-swrl
appliedRules   = ["R-C08"]
issues         = []
```

---

### 样例 1：R-C03 折扣与赠费并存（R-CONF-002）

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft":{"offeringId":"OF-SAMPLE-C03A","offeringName":"样例资费","messageRootKey":"broadBandMainPrc","fixedFeeAmount":128,"oneTimeFee":0,"channelScope":"全渠道","prefDiscount":80,"prefFee":20}}'
```

**预期**

```
compliancePass = false
engine         = openllet-swrl
appliedRules   = ["R-C03"]
firedRules     = ["R-C03"]
issues[0] = { ruleId: R-C03, proposalAlias: R-CONF-002, issueLevel: HIGH,
              field: prefDiscount }
```

**推理依据**：`hasPreferredDiscount = (prefDiscount > 0)` 与 `hasGiftFee = (prefFee > 0)`
同时成立，SWRL 规则 `R-C03.discountGift`（`product.ttl:2057`）推出 `C03MutualExclusiveConflict`。

---

### 样例 2：R-C03 在架互斥组冲突

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft":{"offeringId":"OF-SAMPLE-C03B","offeringName":"样例资费","messageRootKey":"broadBandMainPrc","fixedFeeAmount":128,"oneTimeFee":0,"channelScope":"全渠道","bindExistingMainPkg":"99898653","mutexGroup":"MAIN_PKG"}}'
```

**预期**

```
compliancePass = false
appliedRules   = ["R-C03"]
issues[0] = { ruleId: R-C03, issueLevel: HIGH, field: mutexGroup }
```

> ⚠️ **此样例依赖在架图谱中能查到 `99898653`**。
> 走 HTTP 接口时，`ComplianceRuleEngine` 会调用 `computeSameMutexGroup` 拿在架商品做比对，
> 若查不到该商品，`sameMutexGroup` 为 `false`，**R-C03 不会命中，会误判为合规**。
> 验证前请确认 `99898653` 在 `pd_ops_shelf_offerings` 中存在且 `mutexGroup = MAIN_PKG`。
> 库中没有时，此样例的预期结果是 `compliancePass = true` —— 这不是缺陷，是缺数据。

---

### 样例 3：R-C04 附加品缺依赖

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft":{"offeringId":"OF-SAMPLE-C04","offeringName":"样例资费","messageRootKey":"broadBandMainPrc","fixedFeeAmount":128,"oneTimeFee":0,"channelScope":"全渠道","offeringType":"addon"}}'
```

**预期**

```
compliancePass = false
appliedRules   = ["R-C04"]
issues[0] = { ruleId: R-C04, issueLevel: HIGH, field: dependOn }
```

**推理依据**：`isAddonFlag = true` 且 `hasDependOn = false`（`dependOn` / `sourceOfferRef` 均空），
规则 `R-C04`（`product.ttl:2066`）推出 `C04OrphanAddon`。

---

### 样例 4：R-C05 高风险零固费

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft":{"offeringId":"OF-SAMPLE-C05","offeringName":"样例资费","messageRootKey":"broadBandMainPrc","fixedFeeAmount":0,"oneTimeFee":0,"channelScope":"全渠道"}}'
```

**预期**

```
compliancePass = false
appliedRules   = ["R-C05", "R-C09"]
issues 含 R-C05（HIGH，field=fixedFeeAmount）
issues 含 R-C09（HIGH，field=fixedFeeAmount）
```

> **会同时命中 R-C09**：固费 0 元低于下限 9 元。这是两条规则各自独立判定，
> 不是重复报错——R-C05 看「零元 + 无合约」，R-C09 看「是否落在 9~599 区间」。

---

### 样例 5：R-C05 白名单豁免（但 R-C09 仍命中）

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft":{"offeringId":"OF-SAMPLE-C05W","offeringName":"样例资费","messageRootKey":"broadBandMainPrc","fixedFeeAmount":0,"oneTimeFee":0,"channelScope":"全渠道","bizScenario":"权益赠送"}}'
```

**预期**

```
compliancePass = false
appliedRules   = ["R-C09"]
issues 仅含 R-C09，不含 R-C05
```

> **关键语义**：`bizScenario` 命中 `equityGiftWhitelist` 只豁免 **R-C05**，
> **不豁免 R-C09 固费下限**。零元资费即便白名单放行，也仍不符合 9~599 区间。
> 若要完整豁免，需同时把 `channelScope` 设为 `内部验证`，或调整 `monthlyFeeMin` 阈值。

---

### 样例 6：R-C06 必填缺失 —— 资费名称

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft":{"offeringId":"OF-SAMPLE-C06A","messageRootKey":"broadBandMainPrc","fixedFeeAmount":128,"oneTimeFee":0,"channelScope":"全渠道"}}'
```

**预期**

```
compliancePass = false
appliedRules   = ["R-C06"]
issues[0] = { ruleId: R-C06, issueLevel: MEDIUM, field: offeringName }
```

> R-C06 是 **MEDIUM** 级，但仍导致不通过——判据是「无 R-C06 命中」，
> 不受级别影响（见 `ComplianceRuleEngine.java:248`）。

---

### 样例 7：R-C06 必填缺失 —— 销售渠道

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft":{"offeringId":"OF-SAMPLE-C06B","offeringName":"样例资费","messageRootKey":"broadBandMainPrc","fixedFeeAmount":128,"oneTimeFee":0}}'
```

**预期**

```
compliancePass = false
appliedRules   = ["R-C06"]
issues[0] = { ruleId: R-C06, issueLevel: MEDIUM, field: channelScope }
```

---

### 样例 8：R-C07 异常优惠漏洞（R-CONF-003）

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft":{"offeringId":"OF-SAMPLE-C07","offeringName":"样例资费","messageRootKey":"broadBandMainPrc","fixedFeeAmount":128,"oneTimeFee":0,"channelScope":"全渠道","discountPercent":100,"repeatable":true}}'
```

**预期**

```
compliancePass = false
appliedRules   = ["R-C07"]
issues[0] = { ruleId: R-C07, proposalAlias: R-CONF-003, issueLevel: HIGH,
              field: prefDiscount }
```

**推理依据**：`discountPercent >= 100` 且 `repeatableFlag = true`，
规则 `R-C07`（`product.ttl:2091`）推出 `C07AbnormalDiscount`。

---

### 样例 9：R-C07 负例 —— 折扣 100% 但不可重复

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft":{"offeringId":"OF-SAMPLE-C07N","offeringName":"样例资费","messageRootKey":"broadBandMainPrc","fixedFeeAmount":128,"oneTimeFee":0,"channelScope":"全渠道","discountPercent":100,"repeatable":false}}'
```

**预期**

```
compliancePass = true
appliedRules   = ["R-C08"]
issues         = []
```

> 该负例用于确认 R-C07 **不是**见到 100% 折扣就报，必须「可重复」同时成立。

---

### 样例 10：R-C09 固费低于下限（R-CONF-001）

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft":{"offeringId":"OF-SAMPLE-C09A","offeringName":"样例资费","messageRootKey":"broadBandMainPrc","fixedFeeAmount":5,"oneTimeFee":0,"channelScope":"全渠道"}}'
```

**预期**

```
compliancePass = false
appliedRules   = ["R-C09"]
issues[0] = { ruleId: R-C09, proposalAlias: R-CONF-001, issueLevel: HIGH,
              field: fixedFeeAmount }
```

---

### 样例 11：R-C09 固费超出上限

```powershell
curl -X POST "http://localhost:6174/api/v1/product-ontology/config/compliance" `
  -H "Content-Type: application/json" `
  -d '{"draft":{"offeringId":"OF-SAMPLE-C09B","offeringName":"样例资费","messageRootKey":"broadBandMainPrc","fixedFeeAmount":999,"oneTimeFee":0,"channelScope":"全渠道"}}'
```

**预期**

```
compliancePass = false
appliedRules   = ["R-C09"]
issues[0] = { ruleId: R-C09, proposalAlias: R-CONF-001, issueLevel: HIGH,
              field: fixedFeeAmount }
```

---

## 三、预期结果总表

| # | 场景 | 关键增量字段 | compliancePass | 命中规则 |
| --- | --- | --- | --- | --- |
| 0 | 合规基线 | — | ✅ true | `R-C08` |
| 1 | 折扣+赠费 | `prefDiscount=80, prefFee=20` | ❌ false | `R-C03` HIGH |
| 2 | 在架互斥冲突 | `bindExistingMainPkg=99898653, mutexGroup=MAIN_PKG` | ❌ false ⚠️ | `R-C03` HIGH |
| 3 | 附加缺依赖 | `offeringType=addon` | ❌ false | `R-C04` HIGH |
| 4 | 零固费 | `fixedFeeAmount=0, oneTimeFee=0` | ❌ false | `R-C05` + `R-C09` HIGH |
| 5 | 零固费+白名单 | 同上 + `bizScenario=权益赠送` | ❌ false | `R-C09` HIGH（R-C05 已豁免） |
| 6 | 缺资费名称 | 去掉 `offeringName` | ❌ false | `R-C06` MEDIUM |
| 7 | 缺销售渠道 | 去掉 `channelScope` | ❌ false | `R-C06` MEDIUM |
| 8 | 全折扣可重复 | `discountPercent=100, repeatable=true` | ❌ false | `R-C07` HIGH |
| 9 | 全折扣不可重复 | `discountPercent=100, repeatable=false` | ✅ true | `R-C08` |
| 10 | 固费低于下限 | `fixedFeeAmount=5` | ❌ false | `R-C09` HIGH |
| 11 | 固费超出上限 | `fixedFeeAmount=999` | ❌ false | `R-C09` HIGH |

⚠️ 样例 2 依赖在架图谱中存在 `99898653`，详见该样例说明。

---

## 四、阈值在哪里改

所有阈值都在两份配置里，改完重启即生效，无需改代码：

| 阈值 | 位置 | 默认值 |
| --- | --- | --- |
| 固费下限 / 上限 | `ops_rules.json` → `config.defaults.monthlyFeeMin` / `monthlyFeeMax` | `9` / `599` |
| R-C07 全折扣阈值 | `ops_rules.json` → `config.rules.R-C07.discountPercentGte` | `100` |
| 规则开关 | `ops_rules.json` → `config.rules.R-C0x.enabled` | 均为 `true` |
| TTL 内默认阈值 | `product.ttl` 的 `th_RC05_zeroFee` / `th_RC07_discountFull` / `th_RC09_feeMin` / `th_RC09_feeMax` | `0` / `100` / `9` / `599` |

> 运行时 `OpsSwrlReasoner.applyThresholds`（`:1239`）会用工信配置覆写 TTL 里的阈值个体，
> 所以以 `ops_rules.json` 为准，TTL 里的值是缺省兜底。

---

## 五、字段 → 本体属性映射在哪看

`ops_rules.json` → `config.propertyMapping.properties`（17 条）。
Java 侧没有任何逐字段硬编码，全部由这份声明驱动
（`OpsSwrlReasoner.assertMappedFacts`，`:1517`）。

常用映射：

| 草稿字段 | 本体属性 | 转换 |
| --- | --- | --- |
| `fixedFeeAmount` \| `monthlyFee` \| `chargePlan.fixedFeeAmount` | `monthlyFee` | decimal，缺省 `-1` |
| `oneTimeFee` | `oneTimeFee` | decimal，缺省 `0` |
| `hasContract` | `hasContract` | boolean |
| `repeatable` \| `repeatChargeFlag` | `repeatableFlag` | boolean |
| `discountPercent` \| `prefDiscount` | `discountPercent` | decimal + `percent-normalize` |
| `offeringName` \| `offerName` | `c06MissingName` | `all-empty` |
| `channelScope` \| `releaseScope.channelScope` | `c06MissingChannel` | `all-empty` |
| `offeringType == "addon"` 或 `isMainOffer == "false"` | `isAddonFlag` | equals-any（多分支 OR 合并） |
| `dependOn` \| `sourceOfferRef` | `hasDependOn` | `all-nonempty` |
| `bizScenario` 命中 `graph.equityGiftWhitelist` 或 `channelScope == "内部验证"` | `exemptionFlag` | `graph-contains` / equals（多分支 OR 合并） |

---

## 六、已知边界

1. **R-C03 在架互斥不是纯本体推理**
   `sameMutexGroup` 需要跨个体比较（草稿 vs 在架商品），OWL/SWRL 无法表达，
   由 Java 侧 `computeSameMutexGroup`（`ComplianceRuleEngine.java:390`）预计算后作为事实断言。
   **这是明确的建模妥协**：该规则的「比较」在 Java，「判定」在 SWRL。
   其余 R-C04~C09 均由本体独立推理。

2. **豁免标志 `exemptionFlag` 是派生布尔**
   开放世界假设下「不存在白名单审批单号」无法由 OWL 推出，
   故该布尔由声明式映射翻译后断言，非推理所得（见 `product.ttl:1333`）。

3. **合规结论来自「不属于违规族」**
   OWL 开放世界无法用补集证明「不存在其它违规」，
   因此合规判据是查询实例是否落入 `ConfigViolation`（`product.ttl:1105`）。
   该查询结果完全来自本轮 SWRL 推理，外部不注入任何判定布尔。

4. **`pd_ai_ontology_instance` 当前为 0 行**
   带 `draftId` 走库表路径时，查不到事实会回退内存草稿。
   本文件的样例均**不带 `draftId`**，走内存路径，不受空表影响。

---

## 七、若结果与预期不符

按以下顺序排查：

1. **`engine` 是不是 `java-rules`？**
   说明 SWRL 已回退。检查 `application.yml` 的 `prodai.ontology.swrl-enabled`（`:110`）
   与 `ops_rules.json` 中 `R-C03~C09` 的 `engine` 是否为 `swrl`。

2. **`issues = []` 但 `compliancePass = false`？**
   检查 `R-C08` 是否被 `enabled: false` 关掉（`ops_rules.json` → `config.rules.R-C08`）。

3. **样例 2 不命中 R-C03？**
   先确认 `pd_ops_shelf_offerings` 里有 `99898653`，且 `mutex_group` 为 `MAIN_PKG`。

4. **整体跑不起来？**
   确认数据库可连：`jdbc:mysql://10.88.154.215:8100/newpmsdb`。
   本地无网可覆盖：`$env:SPRING_DATASOURCE_URL="jdbc:h2:file:./data/prodplatformai;MODE=MySQL"`。
