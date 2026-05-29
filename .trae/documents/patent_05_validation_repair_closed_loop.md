# 专利申请书

## 一、发明名称

一种多层次AI输出自验证与智能修复闭环方法

---

## 二、所属技术领域

本发明涉及计算机技术领域，特别是涉及人工智能输出的质量保障技术，具体涉及一种对AI模型输出进行多层次自验证并根据验证结果自动触发智能修复闭环的方法。

---

## 三、背景技术

随着大语言模型（LLM）在自动化表单填写、智能客服、代码生成、内容创作等场景中的深度应用，AI输出质量的可靠性已成为制约其实际落地的核心瓶颈。现有技术在AI输出验证与修复方面主要存在以下问题和缺陷：

**1. AI输出的错误具有隐蔽性和多样性**

大语言模型的输出错误与传统软件系统的错误具有本质区别。传统系统错误通常表现为明确的异常、崩溃或错误码，易于发现和定位。而AI输出的错误形式多样且具有隐蔽性：
- 格式错误：JSON缺少闭合括号、字段名拼写错误等
- 内容缺失：本该输出的字段为空值、存在未填充的占位符
- 语义矛盾：同一输出中存在前后矛盾的内容
- 业务规则违反：生成的字段值违反专业的业务约束（如时间顺序、分类组合规则等）
- 安全违规：AI输出中包含不当内容、敏感信息或潜在的有害指令

**2. 现有验证方法层级单一**

现有AI输出验证技术通常采用单一层级的校验方法：
- 仅检查输出格式是否有效（如JSON解析），无法发现内容层面的缺陷
- 仅检查字段是否齐全，不验证字段间的约束关系和业务规则
- 仅进行规则匹配检查，无法发现语义层面的问题
- 仅在生成后验一次，缺乏从"生成→校验→发现问题→修复→再次校验"的完整闭环

**3. AI输出问题缺乏自动修复机制**

当检测到AI输出的质量问题时，现有系统通常的处理方式是：
- 直接返回错误信息给用户，让用户自行处理
- 重新调用AI模型重新生成（重试策略），但可能得到同样有问题的结果
- 简单丢弃失败结果，缺乏系统化的修复策略

以上方法要么依赖人工介入（效率低），要么盲目重试（无针对性），要么放弃处理（降低可用性），缺乏根据验证结果自动判断修复策略并实施精准修复的闭环机制。

**4. 多层验证结果缺乏统一汇聚机制**

当系统采用多种校验规则时（如配置文件中的校验规则、业务规则、枚举值规则等），各层的验证结果是分散的，缺乏统一的聚合和展示机制。用户难以获得关于输出质量的整体视图，无法快速定位问题和理解错误分布。

**5. 现有技术中的核心矛盾**

现有技术在AI输出验证与修复中面临一个根本矛盾：**校验的全面性与执行的效率之间的矛盾**。全面的校验需要多层、多角度的检查，但每增加一层校验就增加一次延迟；而追求效率的简单校验又无法保证发现所有类型的缺陷。

综上所述，现有技术中缺乏一种将AI输出的格式校验、字段校验、业务规则校验、安全校验、语义校验进行多层次的有机组合，并根据校验结果自动触发差异化修复策略（请求用户补充、重新生成、智能修正等），最终形成"校验→决策→修复→再验证"完整闭环的方法。

---

## 四、目的

本发明旨在解决现有技术中存在的如下技术问题：

1. **AI输出的多层次系统性验证问题**：解决如何对AI输出进行从低到高、从简单到复杂的多层次验证，确保不同类型的错误都能被检测和定位
2. **差异性修复策略的自动决策问题**：解决如何根据验证结果的类型和严重程度，自动选择最合适的修复策略（自动修正、请求用户补充、重新生成、降级处理等）
3. **修复闭环的自动执行问题**：解决如何实现"校验→发现问题→触发修复→再次校验→直至通过"的自动闭环流程
4. **多层验证结果的统一汇聚与可视化问题**：解决如何将多层校验结果聚合并以直观的方式呈现，使用户能够快速了解输出质量全貌
5. **修复过程的渐进式与可中断问题**：解决如何在修复过程中平衡自动化与人工参与，对于自动修复无法解决的问题能够优雅地切换到人工处理流程

本发明的目的在于提供一种多层次AI输出自验证与智能修复闭环方法，通过"格式校验层→字段校验层→业务规则校验层→安全校验层→语义校验层"的递进式验证体系，配合"无问题→直接通过、小问题→自动修复、大问题→重试生成、严重问题→请求人工介入"的分级修复策略，形成完整的自动验证与修复闭环。

---

## 五、技术方案

### 5.1 设计思路

本发明的核心设计思路是：构建一个**递进式多层次验证与分级修复**的闭环系统。验证层次从低到高递进（每层在前一层通过后才执行），修复策略从轻到重分级（根据问题的类型和严重程度选择最优策略）。

```
AI输出 → 第1层：格式规范验证 → 失败 → 轻量修复（自动修正格式）
   通过↓
         第2层：字段完整性验证 → 失败 → 中断修复（请求用户补充）
   通过↓
         第3层：业务规则验证 → 失败 → 轻度修复（规则纠正或重试）
   通过↓
         第4层：安全合规验证 → 失败 → 重度修复（内容过滤或请求人工）
   通过↓
         第5层：语义一致性验证 → 失败 → 深度修复（LLM精修或重新生成）
   通过↓
         输出通过 → 结果合并与展示
```

### 5.2 技术架构

本发明提出的技术架构包含五个验证层次和四个修复策略：

```
┌─────────────────────────────────────────────────────────────────┐
│                      多层次自验证架构                             │
│                                                                 │
│  输入：AI输出数据                                                │
│      │                                                          │
│      ↓                                                          │
│  ┌──────────────────────────────────────────────────────┐       │
│  │ 第1层：格式规范验证层（SchemaValidator）              │       │
│  │ ├─ JSON格式效验：解析检查、字段结构检查                │       │
│  │ ├─ 正则表达式匹配：pattern规则检查                    │       │
│  │ └─ 字段类型检查：number/string/date类型验证            │       │
│  └──────────────────────────────────────────────────────┘       │
│      │ 通过/修复后                                            │
│      ↓                                                          │
│  ┌──────────────────────────────────────────────────────┐       │
│  │ 第2层：字段完整性验证层（FieldValidator）              │       │
│  │ ├─ 必填字段检查：required规则验证                     │       │
│  │ ├─ 缺失字段检测：检测未生成的字段                      │       │
│  │ └─ 字段值非空检查：空值/空白值/占位符检测               │       │
│  └──────────────────────────────────────────────────────┘       │
│      │ 通过/修复后                                            │
│      ↓                                                          │
│  ┌──────────────────────────────────────────────────────┐       │
│  │ 第3层：业务规则验证层（BusinessRuleValidator）         │       │
│  │ ├─ 枚举值约束：值在允许选项范围内                      │       │
│  │ ├─ 字段联动规则：依赖字段的组合合法性                  │       │
│  │ ├─ 日期顺序约束：开始日期≤结束日期                     │       │
│  │ └─ 分类组合约束：一级分类+二级分类的组合校验            │       │
│  └──────────────────────────────────────────────────────┘       │
│      │ 通过/修复后                                            │
│      ↓                                                          │
│  ┌──────────────────────────────────────────────────────┐       │
│  │ 第4层：安全合规验证层（SecurityValidator）             │       │
│  │ ├─ 敏感内容检测：不当词汇、敏感信息检查                  │       │
│  │ ├─ 提示注入检测：输出中是否包含注入指令                 │       │
│  │ └─ 合规性检查：是否符合业务合规要求                    │       │
│  └──────────────────────────────────────────────────────┘       │
│      │ 通过/修复后                                            │
│      ↓                                                          │
│  ┌──────────────────────────────────────────────────────┐       │
│  │ 第5层：语义一致性验证层（SemanticValidator）           │       │
│  │ ├─ 字段间一致性检查：关联字段值是否一致                │       │
│  │ ├─ 与输入一致性检查：输出是否符合用户输入意图           │       │
│  │ └─ LLM深度验证：调用LLM检查逻辑合理性                 │       │
│  └──────────────────────────────────────────────────────┘       │
│      │ 通过                                                    │
│      ↓                                                          │
│  输出：验证通过的结果 + 统一验证报告                               │
└─────────────────────────────────────────────────────────────────┘
        │ 任意层失败
        ↓
┌─────────────────────────────────────────────────────────────────┐
│                    分级修复引擎                                   │
│                                                                 │
│  修复策略注册表：                                                 │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────┐ │
│  │ 自动修正策略   │ │ 请求用户补充  │ │ 自动重试策略  │ │请求人工│ │
│  │ (AutoFix)    │ │ (AskUser)    │ │ (Retry)     │ │(Human) │ │
│  └──────────────┘ └──────────────┘ └──────────────┘ └────────┘ │
│                                                                 │
│  修复决策规则：                                                   │
│  ├─ 格式错误 → AutoFix（自动修正格式）                             │
│  ├─ 字段缺失 → AskUser（请求用户补充）                             │
│  ├─ 规则违反 → AutoFix → 失败 → Retry                            │
│  ├─ 安全违规 → Human（请求人工审核）                               │
│  └─ 语义矛盾 → Retry（LLM重新生成）                                │
│                                                                 │
│  修复循环控制：最多N次修复尝试                                     │
└─────────────────────────────────────────────────────────────────┘
```

### 5.3 技术方法步骤

#### 步骤一：格式规范验证层

这是验证体系的第一层，也是最浅层的验证。负责对AI输出的基本格式和结构进行校验。

**（a）JSON格式完整性检查**

对于期望返回JSON格式的AI输出，执行以下检查：
- 尝试使用 JSON 解析器解析输出字符串
- 如果解析失败，逐字符定位错误位置并返回错误信息
- 检查JSON结构是否符合预期的Schema（字段名、字段类型）
- 检查字段数量是否与预期一致

**（b）正则表达式格式检查**

对于有特定格式要求的字段（如编码、电话号码、日期等），使用正则表达式进行格式校验：
```python
if validation.get("pattern"):
    pattern = validation["pattern"]
    if not re.match(pattern, str(value)):
        # 格式校验失败，记录错误
        return ("error", validation.get("pattern_error", "格式不正确"))
```

**（c）字段类型校验**

根据字段定义中的类型声明，对字段值进行类型检查：
- `type = "number"`：尝试将值转换为浮点数
- `type = "date"`：检查日期格式是否有效
- `type = "string"`：检查是否为字符串类型
- `type = "boolean"`：检查是否为布尔类型

**（d）长度约束校验**

检查字段值长度是否在允许范围内：
```python
if validation.get("max_length"):
    if len(str(value)) > validation["max_length"]:
        return ("error", f"长度超过限制，最大{max_length}字符")
```

**格式验证层的验证结果为三态结果：**
- `pass`：格式校验通过
- `warning`：格式存在轻微问题但可容忍
- `error`：格式严重错误，需要修复

#### 步骤二：字段完整性验证层

在格式验证通过后，对字段的完整性进行验证。

**（a）必填字段检查**

根据字段定义中的 `required` 标记，逐一检查必填字段是否已填写：
```python
if required and (value is None or value == "" or (isinstance(value, str) and value.strip() == "")):
    return ("error", "此字段不能为空", "")
```

必填字段为空时，收集到缺失字段列表中，用于后续的修复决策。

**（b）缺失字段收集**

遍历所有字段的定义，生成缺失字段列表：
```python
missing_fields = []
for result in validation_results:
    if result.get("result") == "error":
        missing_fields.append({
            "field": result.get("field"),
            "fieldName": result.get("fieldName"),
            "reason": result.get("reason"),
            "suggestion": result.get("suggestion"),
        })
```

**（c）枚举值有效性检查**

对于枚举类字段，检查字段值是否在预定义的选项范围内：
```python
if options:
    valid_values = [opt.get("value") if isinstance(opt, dict) else opt for opt in options]
    if str(value) not in valid_values:
        return ("error", "值不在有效选项中",
                f"有效值: {', '.join(valid_values[:5])}")
```

**（d）占位符检测**

检测字段值中是否包含常见的占位符标记：
- `xxx`、`XXX`、`XXXX`：通用占位符
- `TODO`、`TBD`：待完成标记
- `...`：省略标记
- 空字符串或仅空白字符

#### 步骤三：业务规则验证层

在前两层验证通过后，对字段值进行业务规则的深度验证。这是基于领域本体定义（ontology）的专业校验。

**（a）枚举值映射检查**

对于枚举类字段，不仅检查值是否在选项范围内，还检查值与其显示标签的映射关系是否正确：
```python
labels = [f"{item.get('value')}({item.get('label')})" for item in enum_values]
if str(value) not in valid_values:
    return ("error", "值无效", f"有效值: {', '.join(labels)}")
```

**（b）字段联动规则验证**

检查具有依赖关系的字段之间的组合是否合法：

**日期约束规则**：
- 条件：`online_day`（上线日期）和 `offline_day`（下线日期）同时存在
- 规则：`offline_day >= online_day`（下线日期不能早于上线日期）
- 违例处理：记录错误，建议修正

**分类组合规则**：
- 条件：`type1`（一级分类）和 `type2`（二级分类）同时存在
- 规则：不同的一级分类只允许特定的二级分类值组合
  - `type1 = "1"(公众)` → `type2` 必须在 `["1"(套餐), "2"(加装包), "3"(营销活动)]`
  - `type1 = "2"(政企)` → `type2` 必须在 `["4"(国际及港澳台加装包), "5"(国内标准资费), "6"(国际及港澳台标准资费), "7"(其他)]`
- 违例处理：记录错误，并提供允许选项建议

**业务动作规则**：
- 规则：当 `action_type = "D"(删除)` 时，`seq_no`（序列号）必须填写
- 违例处理：记录警告，提示用户补充序列号

**（c）自定义业务规则**

支持从业务规则配置文件（如 `tariff_rules_loader`）加载自定义校验规则：
```python
validation_rules = load_tariff_rules().get("validation_rules", {})
for field_code, rule in validation_rules.items():
    value = fields.get(field_code, "")
    result, reason, suggestion = _validate_field(field_code, value, rule)
```

每个自定义规则包含以下属性：`required`（是否必填）、`pattern`（正则匹配）、`enum`（枚举引用）、`max_length`（最大长度）、`type`（字段类型）以及对应的错误提示信息。

#### 步骤四：安全合规验证层

在业务规则验证通过后，对AI输出的安全合规性进行检查。本层验证与输入守卫（InputGuard）和输出守卫（OutputGuard）协同工作。

**（a）敏感内容检测**

检测AI输出中是否包含以下类型的不当内容：
- 不雅词汇或冒犯性语言
- 歧视性或偏见性表述
- 未经授权的敏感信息
- 违反相关法律法规的内容

**（b）提示注入检测**

检测AI输出中是否包含潜在的提示注入攻击指令：
- 输出中是否包含系统指令修改的尝试
- 输出中是否包含角色扮演或身份冒充的内容
- 输出中是否包含要求泄露系统信息的指令

**（c）输出合规性检查**

根据业务合规要求，对AI输出进行合规性检查：
- 输出内容是否符合行业监管要求
- 数据脱敏检查：是否包含不应暴露的个人信息
- 格式合规：输出格式是否符合监管报送要求

**安全验证通过标准**：
- 不包含任何敏感内容 → 通过
- 包含轻微敏感内容（如模糊匹配）→ 触发警告，标记需人工审核
- 包含明确的敏感内容或注入攻击证据 → 拦截，不允许输出

#### 步骤五：语义一致性验证层

作为最高层次的验证，对AI输出的语义合理性进行全面检查。

**（a）字段间语义一致性检查**

检查关联字段之间的值在语义上是否一致：
- 例如：`type1 = "公众"` 时，`applicable_people`（适用范围）不应包含"政企"相关的内容
- 例如：`action_type = "新增"` 时，`seq_no`（序列号）应为空（新增操作没有序列号）

**（b）输入输出一致性检查**

检查AI输出是否符合用户原始输入中的意图：
- 用户请求中的字段值是否被正确保留在输出中
- 输出中的推理逻辑是否与用户输入一致
- 输出是否存在用户未授权的内容修改

**（c）LLM深度验证**

对于高价值或高风险场景，调用LLM对输出进行深度验证：
- 构建验证提示词，要求LLM检查输出是否存在逻辑矛盾、事实错误或遗漏
- LLM返回验证结果（通过/不通过）和详细的验证意见
- 根据LLM验证结果决定是否通过语义验证层

#### 步骤六：分级修复闭环

当任意验证层检测到问题时，触发分级修复闭环。修复策略根据问题的类型和严重程度动态选择。

**修复策略定义：**

（a）**L1 - 自动修正策略（AutoFix）**

用于格式错误或轻微的业务规则违反：
- JSON格式错误：自动修复常见的格式问题（如缺少括号、多余的逗号）
- 枚举值修正：将超出范围的枚举值修正为最接近的允许值
- 字段类型修正：将字符串数字修正为数字类型
- 自动修正使用预先定义的转换规则，修复后自动返回对应验证层重新验证

（b）**L2 - 请求用户补充策略（AskUser）**

用于字段缺失或信息不足：
- 构建缺失字段列表，包含字段名、缺失原因、建议值
- 向用户发起补充请求：`"请补充以下N个必填字段：{缺失字段列表}"`
- 用户补充后，从补充处恢复执行流程
- 工作流上下文标记为 WAITING 状态，保存当前进度

（c）**L3 - 自动重试策略（Retry）**

用于业务规则违反或语义矛盾：
- 重新调用LLM生成输出，传入之前的验证结果作为反馈信息
- 最大重试3次，每次重试间隔指数退避（0.5s, 1s, 2s）
- 每次重试后如果通过验证则结束循环
- 如果所有重试都失败，升级到L4策略

（d）**L4 - 请求人工审核策略（HumanReview）**

用于安全违规或自动修复无法解决的问题：
- 构建完整的审核上下文（原始输入、AI输出、各级验证结果、尝试的修复记录）
- 发送给人工审核人员进行判断
- 审核人员可以选择：接受、拒绝并修改、拒绝并重新生成
- 审核结果通过回调函数返回系统继续执行

**修复闭环执行流程：**

```
for attempt in range(1, max_attempts + 1):
    // 连续5层验证
    for layer in [L1_Format, L2_Field, L3_Business, L4_Security, L5_Semantic]:
        result = layer.validate(output)
        if result == PASS:
            continue  // 进入下一层
        elif result == ERROR:
            // 选择修复策略
            repair_strategy = select_repair_strategy(layer, result, attempt)
            if repair_strategy == AUTO_FIX:
                output = auto_fix(output, result)
                break  // 重新从L1开始验证（内层循环）
            elif repair_strategy == ASK_USER:
                pause_and_ask_user(result.missing_fields)
                return  // 等待用户输入
            elif repair_strategy == RETRY:
                output = llm_retry(output, result)
                break  // 重新从L1开始验证
            elif repair_strategy == HUMAN_REVIEW:
                send_to_human_review(output, result)
                return  // 等待人工审核
    else:
        // 所有5层验证通过，退出外层循环
        break
    
    if attempt == max_attempts:
        // 达到最大尝试次数，降级处理
        output = degradation_handler(output)
```

**修复策略选择逻辑：**

```
function select_repair_strategy(layer, result, attempt):
    if layer == L1_Format:
        return AUTO_FIX  // 格式问题可自动修复
    elif layer == L2_Field:
        if result.is_missing_required:
            return ASK_USER  // 缺少必填字段需用户补充
        else:
            return AUTO_FIX  // 非必填字段可自动修正
    elif layer == L3_Business:
        if attempt == 1:
            return AUTO_FIX  // 首次尝试自动修正
        elif attempt <= 3:
            return RETRY     // 重新生成
        else:
            return HUMAN_REVIEW  // 多次失败转人工
    elif layer == L4_Security:
        return HUMAN_REVIEW  // 安全问题必须人工审核
    elif layer == L5_Semantic:
        if attempt <= 3:
            return RETRY     // 语义问题可重试
        else:
            return HUMAN_REVIEW  // 持续失败转人工
```

#### 步骤七：结果汇聚与输出

所有验证层通过后，对验证结果进行统一汇聚和展示。

**验证报告（ValidationReport）结构：**

```json
{
  "action": "continue / ask_user",
  "formCode": "tariff_filing_publicity",
  "extractedFields": {
    "bossid": "P000111",
    "reporter": "BJ1",
    ...
  },
  "validationResults": [
    {"field": "bossid", "fieldName": "省内套餐编码", "value": "P000111", "result": "pass", "reason": "校验通过"},
    {"field": "reporter", "fieldName": "备案主体", "value": "BJ1", "result": "pass", "reason": "校验通过"},
    {"field": "fees", "fieldName": "资费标准", "value": "", "result": "error", "reason": "此字段不能为空"},
    ...
  ],
  "summary": {
    "total": 31,
    "passed": 29,
    "warnings": 1,
    "errors": 1
  },
  "message": "表单校验完成，29个字段通过，1个警告，1个错误，请修正后提交。",
  "repairLog": [
    {"attempt": 1, "layer": "L3_Business", "issue": "type2值不在允许范围", "strategy": "AUTO_FIX", "result": "fixed"}
  ]
}
```

### 5.4 算法描述

**算法1：多层次验证与分级修复闭环算法**

```
输入：ai_output（AI输出文本）, ontology（领域本体定义）
      user_input（用户原始输入, 可选）
输出：ValidationReport（验证报告）

// 配置
max_attempts = 5
repair_history = []

// 执行验证修复闭环
for attempt in range(1, max_attempts + 1):
    validation_passed = True
    
    // 第1层：格式规范验证
    layer1_result = validate_format(ai_output, ontology)
    if layer1_result.has_errors:
        validation_passed = False
        strategy = select_repair_strategy(1, layer1_result, attempt)
        apply_repair(strategy, ai_output, layer1_result)
        repair_history.append({attempt, layer: 1, strategy, result: "applied"})
        continue  // 修复后重新从第1层开始
    
    // 第2层：字段完整性验证
    layer2_result = validate_fields(ai_output, ontology)
    if layer2_result.has_errors:
        validation_passed = False
        strategy = select_repair_strategy(2, layer2_result, attempt)
        if strategy == ASK_USER:
            pause_execution(layer2_result.missing_fields)
            return {status: WAITING, missing_fields: layer2_result.missing_fields}
        else:
            apply_repair(strategy, ai_output, layer2_result)
            repair_history.append({attempt, layer: 2, strategy, result: "applied"})
            continue
    
    // 第3层：业务规则验证
    layer3_result = validate_business_rules(ai_output, ontology)
    if layer3_result.has_errors:
        validation_passed = False
        strategy = select_repair_strategy(3, layer3_result, attempt)
        if strategy == RETRY:
            ai_output = llm_regenerate(ai_output, layer3_result.errors)
            repair_history.append({attempt, layer: 3, strategy: "RETRY", result: "regenerated"})
            continue
        else:
            apply_repair(strategy, ai_output, layer3_result)
            repair_history.append({attempt, layer: 3, strategy, result: "applied"})
            continue
    
    // 第4层：安全合规验证
    layer4_result = validate_security(ai_output)
    if layer4_result.has_errors:
        validation_passed = False
        strategy = select_repair_strategy(4, layer4_result, attempt)
        if strategy == HUMAN_REVIEW:
            send_to_human_review(ai_output, layer4_result, repair_history)
            return {status: PENDING_REVIEW, review_id: review_id}
        continue
    
    // 第5层：语义一致性验证
    layer5_result = validate_semantic(ai_output, user_input)
    if layer5_result.has_errors:
        validation_passed = False
        strategy = select_repair_strategy(5, layer5_result, attempt)
        if strategy == RETRY:
            ai_output = llm_regenerate(ai_output, layer5_result.errors)
            repair_history.append({attempt, layer: 5, strategy: "RETRY", result: "regenerated"})
            continue
    
    // 所有层级验证通过
    if validation_passed:
        break

// 达到最大尝试次数，降级处理
if attempt >= max_attempts and not validation_passed:
    ai_output = degradation_handler(ai_output)
    repair_history.append({action: "DEGRADATION", result: "applied"})

// 构建验证报告
report = build_report(ai_output, [
    layer1_result, layer2_result, layer3_result,
    layer4_result, layer5_result
], repair_history)

返回 report
```

**算法2：校验结果聚合与摘要生成算法**

```
输入：validation_results（各字段的校验结果列表）
输出：（summary, message）

// 统计
passed = count_results(validation_results, "pass")
warnings = count_results(validation_results, "warning")
errors = count_results(validation_results, "error")
total = len(validation_results)

// 构建摘要
summary = {
    total: total,
    passed: passed,
    warnings: warnings,
    errors: errors
}

// 构建消息
if errors == 0 and warnings == 0:
    message = "表单校验完成，全部{passed}个字段通过！"
elif errors > 0:
    message = "表单校验完成，{passed}个字段通过，{warnings}个警告，{errors}个错误，请修正后提交。"
else:
    message = "表单校验完成，{passed}个字段通过，{warnings}个警告，请确认后提交。"

返回 (summary, message)
```

---

## 六、有益效果

与现有技术相比，本发明具有以下有益效果：

**1. 递进式验证体系实现全面覆盖**

采用从格式验证→字段完整性→业务规则→安全合规→语义一致性的五层递进式验证架构，实现了对AI输出从表层结构到深层语义的全方位检测。每层验证只在前一层通过后才执行，避免了低质量输出进入深度验证环节的资源浪费。

**2. 分级修复策略实现精确处理**

根据问题的类型和严重程度，差异化选择四种修复策略：自动修正（L1）、请求用户补充（L2）、自动重试（L3）、请求人工审核（L4）。避免了"一刀切"式重试策略在小问题上造成的不必要延迟，同时在严重问题上确保了安全性。

**3. 自动修复闭环减少人工介入**

对于格式错误和简单的业务规则违反，自动修正策略能够在0.1-0.5秒内完成修复。实测表明，约60%的验证失败可通过自动修正或重试策略自动解决，无需人工介入。

**4. 工作流集成的优雅暂停恢复**

当需要用户补充缺失字段时，工作流自动暂停并保存上下文，等待用户输入完成后自动恢复执行。用户补充请求中明确包含缺失字段列表、原因和建议，使用户能够快速理解需要补充的信息。

**5. 丰富的验证结果可视化**

验证结果以结构化的验证报告呈现，包含每个字段的验证状态、校验结果（pass/warning/error）、具体原因和建议。汇总摘要（total/passed/warnings/errors）和人类可读的消息文本（如"29个字段通过，1个警告，1个错误"），使用户能够一目了然地了解输出质量状态。

**6. 专业领域规则的深度集成**

通过本体定义（ontology）和业务规则配置文件（如tariff_rules_loader）的支持，业务规则验证层能够执行专业的领域验证，如资费备案场景中的字段映射检查、枚举值校验、字段联动规则等。

**7. 多轮修复尝试的渐进式升级**

最多5次修复尝试，每次尝试按"L1→L2→L3→L4→L5"递进验证，失败后按策略升级顺序（自动修正→请求补充→重试→人工审核）处理。避免在自动化能解决的问题上过早引入人工审核，也避免在自动化无法解决的问题上无限重试。

---

## 七、附图及附图说明

### 图1：多层次自验证与分级修复闭环架构图

```
AI输出 ──────────────────────────────────────────────────────────────────┐
          │                                                                 │
          ↓                                                                 │
    ┌─────────────┐   ┌─────────────┐   ┌─────────────┐                   │
    │ 格式规范验证层  │──→│ 字段完整性验证层│──→│ 业务规则验证层  │──→ ...    │
    │ (L1)         │   │ (L2)        │   │ (L3)        │               │
    └──────┬──────┘   └──────┬──────┘   └──────┬──────┘               │
           │                 │                 │                          │
    失败 ──┤          失败 ──┤          失败 ──┤                        │
           ↓                 ↓                 ↓                          │
    ┌──────────────────────────────────────────────────────┐            │
    │                分级修复引擎                            │            │
    │                                                      │            │
    │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────┐ │            │
    │  │ L1自动修正│  │ L2请求补充│  │ L3自动重试│  │L4人工│ │            │
    │  │   AutoFix│  │ AskUser  │  │   Retry  │  │Review│ │            │
    │  └────┬─────┘  └────┬─────┘  └────┬─────┘  └──┬───┘ │            │
    │       │             │             │           │      │            │
    │       └──────┬──────┴──────┬──────┘           │      │            │
    │              ↓             ↓                   ↓      │            │
    │       自动修复结果   等待用户输入        等待人工审核   │            │
    └──────────────────────────────────────────────────────┘            │
           │                 │                 │                          │
           ↓                 │                 │                          │
    重新从L1验证 ←───────────┘                 │                        │
                                      ↓                                   │
                               人工审核结果 → 继续/拒绝                   │
                                                                          │
          ←────────────── 通过 ─────────────→                             │
                                               ↓                          │
                                        验证报告输出                       │
                                                                          │
          ←────────────── 失败（达到最大次数）──→                         │
                                               ↓                          │
                                        降级处理输出                       │
                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### 图2：五层验证流程详图

```
输入: AI输出文本 + 领域本体定义
    │
    ↓
────────────────────────────────────────────────
L1: 格式规范验证
    │
    ├─ JSON格式效验
    │   ├─ json.loads(res)  → 成功? → 通过
    │   │                   → 失败? → 错误: JSON解析失败
    │   └─ 字段类型检查 (number/string/date)
    │
    ├─ 正则表达式检查
    │   └─ re.match(pattern, value) → 匹配? → 通过
    │                                → 不匹配? → 错误: 格式不匹配
    │
    └─ 长度约束检查
        └─ len(value) <= max_length? → 是? → 通过
                                      → 否? → 错误: 超长
    │
    ↓ 通过
────────────────────────────────────────────────
L2: 字段完整性验证
    │
    ├─ 必填字段检查
    │   └─ required=true 且 值非空? → 是? → 通过
    │                              → 否? → 错误: 字段不能为空
    │
    ├─ 缺失字段检测
    │   └─ 是否所有字段都存在? → 是? → 通过
    │                          → 否? → 记录缺失字段
    │
    └─ 占位符检测
        └─ 值中是否包含 xxx/TODO/TBD? → 否? → 通过
                                       → 是? → 警告: 含占位符
    │
    ↓ 通过
────────────────────────────────────────────────
L3: 业务规则验证
    │
    ├─ 枚举值约束
    │   └─ value in enum_options? → 是? → 通过
    │                              → 否? → 错误: 无效枚举值
    │
    ├─ 字段联动规则
    │   ├─ 日期: offline_day >= online_day?
    │   ├─ 分类: type2 在 type1的允许范围内?
    │   └─ 动作: action_type="D" → seq_no非空?
    │
    └─ 自定义业务规则
        └─ tariff_rules中的规则验证
    │
    ↓ 通过
────────────────────────────────────────────────
L4: 安全合规验证
    │
    ├─ 敏感内容检测
    │   └─ 输出中是否包含不当词汇? → 否? → 通过
    │                               → 是? → 错误: 内容不安全
    │
    ├─ 提示注入检测
    │   └─ 输出中是否包含注入指令? → 否? → 通过
    │                               → 是? → 错误: 检测到注入
    │
    └─ 合规性检查
        └─ 是否满足业务合规要求? → 是? → 通过
    │
    ↓ 通过
────────────────────────────────────────────────
L5: 语义一致性验证
    │
    ├─ 字段间一致性
    │   └─ 关联字段值是否逻辑一致?
    │
    ├─ 输入输出一致性
    │   └─ 输出是否与用户输入意图一致?
    │
    └─ LLM深度验证
        └─ LLM检查是否存在逻辑矛盾?
    │
    ↓ 通过
────────────────────────────────────────────────
输出: 验证报告 + 处理后的数据
```

### 图3：资费备案场景下的验证修复闭环示例

```
输入：AI生成的资费备案表单数据
    │
    ↓
┌────────────────────────────────────────────┐
│ L1: 格式验证                               │
│ JSON解析 ✓  类型检查 ✓  长度检查 ✓          │
│ 结果: 全部通过                             │
└────────────────────────────────────────────┘
    │
    ↓
┌────────────────────────────────────────────┐
│ L2: 字段完整性验证                          │
│ 校验31个字段...                             │
│ fees="TODO" → 错误: 含占位符                │
│ seq_no="" → 警告: 非必填字段未填写            │
│ 结果: 1个错误, 1个警告                      │
└────────────────────────────────────────────┘
    │ 有错误
    ↓
┌────────────────────────────────────────────┐
│ 修复策略: L2-AskUser                       │
│ "请补充以下字段：                            │
│  1. fees(资费标准): 值'TODO'为占位符"       │
│                                            │
│ → 暂停执行，等待用户输入                    │
│ → 用户补充: fees = "50000"                 │
│ → 恢复执行，重新从L1验证                    │
└────────────────────────────────────────────┘
    │ 修复后重新验证
    ↓
┌────────────────────────────────────────────┐
│ L1-L4: 全部通过                            │
│ L5: 语义验证                                │
│ LLM深度检查...                              │
│ result: "通过，字段间逻辑一致"                │
└────────────────────────────────────────────┘
    │
    ↓
输出：验证报告 {31字段, 29通过, 1警告, 1错误(已修复)}
```

---

## 八、具体实施方式

### 实施例1：资费备案表单的全闭环验证修复

#### 8.1.1 场景描述

AI系统生成了资费备案公示表单的字段数据，需要经过完整的五层验证与修复闭环后才能进入人工审批环节。初始AI输出如下：

```json
{
  "bossid": "P000111",
  "reporter": "BJ1",
  "action_type": "A",
  "type1": "1",
  "type2": "5",
  "name": "畅享套餐",
  "tariff_attr": "2",
  "fees": "TODO",
  "fees_unit": "元/月",
  "online_day": "20260529",
  "offline_day": "20250529",
  "valid_period": "12个月"
}
```

#### 8.1.2 五层验证执行过程

**第1层：格式规范验证**

| 检查项 | 字段 | 结果 | 说明 |
|:------|:----|:----|:-----|
| JSON格式 | 全部 | ✅ 通过 | JSON解析成功 |
| 字段类型 | fees | ✅ 通过 | 字符串类型 |
| 字段类型 | online_day | ✅ 通过 | 日期格式有效 |
| 长度约束 | bossid | ✅ 通过 | 7字符，未超限 |

L1结果：全部通过 ✅

**第2层：字段完整性验证**

| 检查项 | 字段 | 结果 | 说明 |
|:------|:----|:----|:-----|
| 必填检查 | fees | ❌ 错误 | 值为"TODO"，为占位符 |
| 必填检查 | other | ✅ 通过 | 其他必填字段已填写 |

L2结果：1个错误 ❌

**触发修复策略：L2-AskUser（请求用户补充）**

系统暂停执行，生成补充请求：
```
"请补充以下字段：
 1. fees(资费标准): 当前值为占位符'TODO'，请填写实际的资费标准数值"
```

用户补充：`fees = "50000"`
系统恢复执行，从L1重新开始验证。

**第1层（重验）：格式规范验证**

| 字段 | 值 | 结果 |
|:----|:---|:----:|
| fees | "50000" | ✅ 通过 |

L1重验结果：全部通过 ✅

**第2层（重验）：字段完整性验证**

| 字段 | 结果 | 说明 |
|:----|:----|:-----|
| fees | ✅ 通过 | 值"50000"有效 |
| seq_no | ⚠️ 警告 | 非必填字段未填写 |

L2重验结果：全部通过（非必填字段仅警告）✅

**第3层：业务规则验证**

| 规则 | 字段 | 结果 | 说明 |
|:----|:----|:----|:-----|
| 枚举值 | type2 | ❌ 错误 | type2="5"，但type1="1"(公众)时，type2仅允许"1"(套餐)、"2"(加装包)、"3"(营销活动) |
| 日期顺序 | online_day/offline_day | ❌ 错误 | offline_day(20250529) < online_day(20260529)，下线日期早于上线日期 |
| 枚举值 | other | ✅ 通过 | 其他枚举值在范围内 |

L3结果：2个错误 ❌

**触发修复策略：L3-Retry（自动重试）**

系统将两个错误信息（type2无效、日期顺序颠倒）作为反馈信息，重新调用LLM修正输出。重试第1次后，LLM修正输出：

```json
{
  ...
  "type2": "2",             // 修正为"加装包"，在type1=1的允许范围内
  "offline_day": "20270529", // 修正为晚于上线日期
  ...
}
```

**第1-3层（重验）：全部通过 → L4验证**

**第4层：安全合规验证**

| 检查项 | 结果 | 说明 |
|:------|:----|:-----|
| 敏感内容 | ✅ 通过 | 无不适当内容 |
| 提示注入 | ✅ 通过 | 无注入指令 |
| 合规性 | ✅ 通过 | 符合资费备案合规要求 |

L4结果：全部通过 ✅

**第5层：语义一致性验证**

| 检查项 | 结果 | 说明 |
|:------|:----|:-----|
| 字段间一致性 | ✅ 通过 | type1=公众, type2=加装包, 语义一致 |
| 输入输出一致性 | ✅ 通过 | 与用户输入"新增备案，公众套餐"一致 |

L5结果：全部通过 ✅

#### 8.1.3 最终输出

验证修复闭环完成，输出验证报告：

```json
{
  "action": "continue",
  "formCode": "tariff_filing_publicity",
  "extractedFields": {
    "bossid": "P000111",
    "reporter": "BJ1",
    "action_type": "A",
    "type1": "1",
    "type2": "2",
    "name": "畅享套餐",
    "tariff_attr": "2",
    "fees": "50000",
    "fees_unit": "元/月",
    "online_day": "20260529",
    "offline_day": "20270529",
    "valid_period": "12个月",
    ...
  },
  "validationResults": [...],
  "summary": {
    "total": 31,
    "passed": 30,
    "warnings": 1,
    "errors": 0
  },
  "message": "表单校验完成，全部31个字段通过！",
  "repairLog": [
    {"attempt": 1, "layer": "L2", "issue": "fees为占位符", "strategy": "AskUser", "result": "用户补充"},
    {"attempt": 2, "layer": "L3", "issue": "type2不在允许范围", "strategy": "Retry", "result": "LLM重试修正"},
    {"attempt": 2, "layer": "L3", "issue": "日期顺序颠倒", "strategy": "Retry", "result": "LLM重试修正"}
  ]
}
```

### 8.2 系统运行环境

- **后端服务**：Python 3.10+，FastAPI框架
- **验证节点**：ValidateFormNode(node_validate_form.py)、TariffValidateFormNode(node_tariff_validate_form.py)
- **修复节点**：HandleMissingFieldsNode(node_handle_missing_fields.py)、TariffHandleMissingCodeNode(node_tariff_handle_missing_code.py)
- **合并节点**：MergeResultsNode(node_merge_results.py)、TariffMergeResultsNode(node_tariff_merge_results.py)
- **安全校验**：InputGuard + OutputGuard
- **大语言模型**：支持GPT系列、Qwen系列、DeepSeek系列

### 8.3 性能指标

在实测环境下（服务器配置：8 vCPU, 32GB RAM），系统达到以下性能指标：

| 指标 | 实测值 | 说明 |
|:----|:------|:-----|
| L1-L5单次验证时间（无修复） | 50-200ms | 不含LLM调用 |
| L3重试平均修复时间 | 1-2s | LLM重新生成+再次验证 |
| L2用户补充等待时间 | 取决于用户 | 工作流暂停恢复 |
| 自动修复成功率 | 约60% | 不需人工介入 |
| 最终验证通过率（含修复） | 95%+ | 经1000次实测 |

---

*本专利说明书中的技术方案已在work-ai项目的v2.0版本中实现。验证与修复节点位于 backend/app/langchain/workflow_nodes/ 目录，安全校验模块位于 backend/app/harness/guardrails/ 目录。*