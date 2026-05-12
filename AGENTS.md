# AGENTS.md - AI Agent 开发规范

> 本文档为 AI Agent 提供静态上下文，定义项目规范、架构约束和行为准则。

## 项目概述

**项目名称**：work-ai（AI驱动动态表单底层框架）  
**版本**：v2.0  
**核心能力**：智能表单识别、字段提取、表单验证、历史数据推荐

## 核心原则

### 1. 安全优先 🛡️

```
优先级别：最高
```

- **输入过滤**：所有用户输入必须经过护栏系统检查
- **输出校验**：所有 AI 输出必须符合 Schema 定义
- **权限控制**：敏感操作需要相应权限级别
- **审计追踪**：所有操作必须记录日志

### 2. 准确性 ✅

```
优先级别：高
```

- **字段提取**：必须基于 Schema 定义，不臆造数据
- **类型约束**：数值类型必须符合范围限制
- **必填检查**：必填字段必须全部填充
- **格式校验**：日期、邮箱等必须符合标准格式

### 3. 可解释性 💡

```
优先级别：中
```

- **决策透明**：每个决策都要有依据
- **拒绝说明**：拒绝请求时要说明原因
- **建议选项**：建议要提供多个选项

### 4. 高效性 ⚡

```
优先级别：中
```

- **工具复用**：优先使用已有工具
- **上下文精简**：只注入必要的上下文
- **缓存结果**：重复查询使用缓存

### 5. AI 主导 🤖

```
优先级别：最高（架构级原则）
```

- **禁止硬编码业务规则**：所有字段提取、推断逻辑必须由 LLM 完成
- **配置驱动**：本体定义、场景提示词、推荐配置全部外部化
- **LLM 负责智能推断**：代码只负责流程编排和结果展示
- **新增表单零代码**：通过配置本体和提示词即可支持新表单类型

---

## AI 主导的字段推断规范

### 核心原则

**所有字段值提取必须由 LLM 完成，不在代码中硬编码任何业务规则。**

```python
# ❌ 错误：硬编码业务规则
if field_code == 'bossid':
    tariff_pattern = r'(P\d{6,})'
    matches = re.findall(tariff_pattern, user_input)
    if matches:
        return matches[0]

# ✅ 正确：由 LLM 根据本体定义推断
# 1. 在 smart_intent_recognition.txt 中定义推断规则
# 2. LLM 读取本体定义（ontologies/tariff_filing_publicity.json）
# 3. LLM 根据规则提取字段值
# 4. 推荐引擎从 extractedFields 获取 LLM 提取的值
```

### 字段推断规则（在意图识别提示词中定义）

#### 1. 编码类字段（fieldCode 包含 code/id/no/number/bossid）
- **从用户输入中提取符合格式的编码**
- 常见编码格式：
  - 套餐编码：P开头 + 6位或更多数字（如 P000111、P123456789）
  - 订单号：ORD开头 + 数字（如 ORD20240001）
  - 手机号：11位数字（如 13812345678）
  - 邮箱：标准邮箱格式（如 user@example.com）
- **示例**：
  - 用户说"备案套餐P000111" → bossid="P000111"
  - 用户说"订单号是ORD20240001" → order_id="ORD20240001"

#### 2. 枚举/选择类字段（fieldType 为 select/enum/radio）
- **根据用户输入的关键词匹配枚举值**
- 优先使用本体中定义的枚举值（options 列表）
- **示例**：
  - 用户说"新增备案" → action_type="新增"
  - 用户说"修改信息" → action_type="修改"
  - 用户说"删除记录" → action_type="删除"

#### 3. 数值类字段（fieldType 为 number/integer/float）
- **从用户输入中提取数字，注意单位转换**
- 常见单位转换：
  - "5万" → 50000
  - "3千" → 3000
  - "1.5G" → 1.5
  - "100M" → 100
- **示例**：
  - 用户说"费用5万元" → fees=50000
  - 用户说"带宽100M" → bandwidth=100

#### 4. 日期时间字段（fieldType 为 date/datetime）
- **必须转换为标准格式**：
  - date: "YYYY-MM-DD"（如 2026-05-12）
  - datetime: ISO 8601（如 2026-05-12T10:30:00）
- **支持自然语言转换**：
  - "今天" → 当前日期
  - "明天" → 明天的日期
  - "下周一" → 下周一的日期
  - "2026年5月12日" → "2026-05-12"
- **重要**：不要留空，必须提取具体日期值

#### 5. 关联字段推断（基于已提取字段）
- **如果字段的值依赖于其他字段，进行关联推断**
- **示例**：
  - 用户提到"畅享套餐" → name="畅享套餐", type1="基础通信服务"
  - 用户说"请假一天"且 start_date="2026-05-12" → end_date="2026-05-12"

#### 6. 必填字段优先处理
- **对于 required=true 的字段，尽量从用户输入或上下文中推断**
- 如果无法推断，在 missingInfo 中说明需要用户提供
- **不要跳过必填字段**，即使值为空也要在 extractedFields 中包含该字段（值为 ""）

#### 7. 文本类字段（fieldType 为 string/input/textarea）
- **直接提取用户描述的文本内容**
- 保持原始语义，不做过多转换
- **示例**：
  - 用户说"原因是家里有事" → reason="家里有事"
  - 用户说"备注：请尽快处理" → remark="请尽快处理"

### 推荐引擎集成

推荐引擎的 `ContextAwareStrategy` 只负责从 LLM 提取的结果中获取值：

```python
# ✅ 正确：从 LLM 提取的字段中获取推荐值
def recommend(self, user_input, form_code, field_code, context):
    extracted_fields = context.get('extractedFields', {})
    
    # LLM 已经在意图识别阶段提取了字段值
    if field_code in extracted_fields:
        value = extracted_fields[field_code]
        if value:
            return [RecommendationItem(
                value=str(value),
                source="llm_extraction",
                confidence=0.9
            )]
    
    return []
```

**禁止**在推荐策略中硬编码任何表单特定的推断逻辑。

### 新增表单类型的流程

1. **定义本体**：在 `backend/config/ontologies/{form_code}.json` 中定义表单 Schema
2. **配置场景**：在 `backend/config/scenes/scene_mapping.json` 中添加场景映射
3. **编写场景提示词**：在 `backend/config/prompts/scenes/{scene_code}_prompt.txt` 中定义业务流程
4. **更新意图识别提示词**：在 `smart_intent_recognition.txt` 中添加关键词和推断规则（如果需要）
5. **配置静态推荐**（可选）：在 `backend/config/templates/recommendations.json` 中添加默认值

**无需修改任何 Python 代码！**

### 架构优势

```
用户输入："资费备案申请 P000111"
    ↓
LLM 意图识别（基于本体规则）
    ├─ 识别场景：tariff_filing_apply
    ├─ 加载本体定义：tariff_filing_publicity.json
    ├─ 根据本体规则推断：
    │   ├─ bossid: "P000111"（编码类字段，P+数字格式）
    │   └─ 其他字段：根据上下文推断或留空
    └─ 返回 extractedFields
    ↓
推荐引擎
    ├─ ContextAwareStrategy: 从 extractedFields 获取 LLM 提取的值
    ├─ FrequencyStrategy: 查询历史数据
    ├─ TimeDecayStrategy: 查询近期数据
    └─ StaticStrategy: 加载配置文件中的静态推荐
    ↓
合并推荐结果并返回
```

---

## 表单系统

### 表单类型编码

| 编码 | 类型 | 必填字段 | 可选字段 |
|------|------|----------|----------|
| `tariff_filing_publicity` | 资费备案公示 | bossid, tariff_code | （根据Schema定义） |
| `external_api_demo` | 外部API演示 | - | - |
| `validation_demo` | 校验演示 | - | - |
| `survey` | 调查问卷 | (根据问卷定义) | (根据问卷定义) |
| `general` | 通用表单 | (无) | (无) |

### 字段类型定义

| 类型 | 格式 | 示例 |
|------|------|------|
| `string` | 任意文本 | "张三" |
| `input` | 文本输入 | "张三" |
| `textarea` | 多行文本 | "详细描述..." |
| `integer` | 整数 | 3 |
| `number` | 数值 | 123.45 |
| `boolean` | true/false | true |
| `date` | YYYY-MM-DD | 2026-04-17 |
| `datetime` | ISO 8601 | 2026-04-17T10:30:00 |
| `email` | 邮箱格式 | user@example.com |
| `phone` | 手机号 | 13812345678 |
| `enum` | 枚举值 | ["年假", "病假", "事假"] |

### Schema 文件结构

表单 Schema 采用**实体-字段**层级结构：

```json
{
  "formCode": "string",           // 表单唯一编码（英文小写）
  "formName": "string",           // 表单显示名称（中文）
  "description": "string",        // 表单描述
  "entities": [                   // 实体数组
    {
      "entityCode": "string",     // 实体编码
      "entityName": "string",     // 实体名称
      "fields": [                 // 字段数组
        {
          "fieldCode": "string",  // 字段编码
          "fieldName": "string",  // 字段显示名称
          "fieldType": "string",  // 字段类型
          "required": true/false, // 是否必填
          "ruleDescription": "string"  // 验证规则描述
        }
      ]
    }
  ]
}
```

**Schema 文件位置**：`backend/config/ontologies/{form_code}.json`

---

## 工具使用规范

### 工具分类

| 分类 | 说明 | 示例工具 |
|------|------|----------|
| `form` | 表单相关 | recognize_scene, extract_fields |
| `validation` | 验证相关 | validate_field, validate_form |
| `system` | 系统相关 | get_status, health_check |
| `data` | 数据相关 | query_database |
| `file` | 文件相关 | upload_file |
| `external` | 外部调用 | call_api |

### 权限级别

```
PUBLIC (公开) → AUTHENTICATED (登录) → ADMIN (管理员) → RESTRICTED (受限)
```

- **PUBLIC**：无需认证，所有用户可用
- **AUTHENTICATED**：需要登录认证
- **ADMIN**：需要管理员权限
- **RESTRICTED**：受限使用，需要特殊授权

### 工具调用规则

1. **先识别意图**：确定需要调用的工具
2. **检查权限**：确认用户有权限调用
3. **验证参数**：检查参数是否符合 Schema
4. **执行并记录**：执行工具并记录日志
5. **返回结果**：返回结构化结果

---

## 错误处理

### 错误码定义

| 错误码 | 类型 | 说明 |
|--------|------|------|
| `E001` | 输入校验失败 | 输入包含不安全内容 |
| `E002` | Schema 不匹配 | 输出不符合定义 |
| `E003` | 必填字段缺失 | 缺少必填字段 |
| `E004` | 类型错误 | 字段类型不正确 |
| `E005` | 范围超限 | 数值超出允许范围 |
| `E006` | 权限不足 | 用户权限不够 |
| `E007` | 工具不存在 | 请求的工具未注册 |
| `E008` | 执行失败 | 工具执行出错 |

### 错误响应格式

```json
{
  "success": false,
  "error": {
    "code": "E003",
    "message": "必填字段缺失",
    "details": [
      {"field": "required_field", "message": "必填字段不能为空"}
    ]
  }
}
```

---

## 护栏系统

### 输入护栏检测

| 威胁类型 | 描述 | 严重级别 |
|----------|------|----------|
| `xss_script` | XSS 脚本注入 | 高 |
| `xss_onerror` | HTML 事件注入 | 中 |
| `xss_javascript` | JavaScript 协议注入 | 高 |
| `sql_union` | SQL UNION 注入 | 高 |
| `sql_drop` | SQL DROP 注入 | 严重 |
| `sql_exec` | SQL 执行命令 | 严重 |
| `cmd_shell` | Shell 命令注入 | 严重 |
| `cmd_pipe` | 管道命令注入 | 高 |
| `cmd_semicolon` | 分号命令注入 | 高 |
| `prompt_ignore` | Prompt 忽略攻击 | 中 |
| `prompt_override` | Prompt 覆盖攻击 | 中 |
| `prompt_jailbreak` | Prompt 越狱攻击 | 高 |
| `malicious_url` | 恶意协议 URL | 中 |
| `path_traversal` | 路径遍历攻击 | 高 |

### 敏感信息检测

- API Key
- Password
- Private Key
- Bearer Token

---

## 工作流程

### 表单识别流程

```
用户输入 → 输入护栏 → 场景识别 → LLM 意图识别 → 输出校验 → 返回结果
```

### 字段提取流程

```
用户输入 + 表单类型 → 输入护栏 → Schema 加载 → LLM 提取 → 推荐引擎 → 输出校验 → 返回结果
```

### 表单验证流程

```
表单数据 + Schema → 规则引擎校验 → LLM 智能校验 → 返回验证结果
```

### 历史推荐流程

```
表单编码 + 用户输入 → 推荐引擎 → 多策略融合 → 返回推荐列表
```

---

## 推荐引擎配置

推荐引擎支持多策略融合，配置位于 `backend/config/app_config.json`：

```json
{
  "recommendation": {
    "recommendationLimit": 5,
    "historyQueryLimit": 1000,
    "countScoreWeight": 0.4,
    "userScoreWeight": 0.4,
    "timeScoreWeight": 0.2,
    "timeDecayDays": 30,
    "countScorePerUnit": 0.1,
    "userScorePerUnit": 0.2,
    "recentDaysThreshold": 90
  }
}
```

### 推荐策略

| 策略 | 权重 | 说明 |
|------|------|------|
| `frequency` | 40% | 基于历史填写频率 |
| `user_personalized` | 40% | 同一用户的历史记录优先 |
| `time_decay` | 20% | 近期数据权重更高 |
| `static` | 兜底 | 无历史数据时使用配置文件默认值 |

---

## 性能要求

| 指标 | 目标 | 备注 |
|------|------|------|
| 响应时间 | < 2s | P95 |
| 并发数 | 100+ | 支持 |
| 上下文长度 | 4K-32K | 可配置 |
| 错误率 | < 1% | 生产环境 |

---

## 监控指标

| 类别 | 指标 |
|------|------|
| 请求量 | QPS, 日活 |
| 响应时间 | P50, P95, P99 |
| 错误率 | 按错误码统计 |
| 工具调用 | 调用次数, 成功率 |
| 护栏拦截 | 拦截次数, 类型分布 |
| 推荐引擎 | 推荐命中率, 处理耗时 |

---

## 开发指南

### 新增表单类型

1. 在 `backend/config/ontologies/` 添加 `{form_code}.json`
2. 在 `backend/config/scenes/scene_mapping.json` 添加场景映射
3. 更新 `backend/config/prompts/intent_recognition.txt` 添加关键词
4. 在 `AGENTS.md` 更新表单类型表
5. （可选）在 `backend/config/templates/recommendations.json` 添加静态推荐值

### 新增工具

1. 定义工具函数
2. 使用 `register_tool` 注册
3. 指定分类和权限级别
4. 编写工具描述（供 LLM 理解）

### 新增护栏规则

1. 在 `InputGuard.DANGEROUS_PATTERNS` 添加正则
2. 设置威胁权重
3. 更新 `AGENTS.md` 威胁类型表

### 新增意图处理器（Handler）

1. 在 `backend/app/intent/handlers/` 创建 `{name}_handler.py`
2. 继承 `BaseIntentHandler`，实现 `intent_type` 和 `handle()` 方法
3. 遵循 Phase 模板结构（见下文）
4. 在 `backend/app/intent/__init__.py` 注册 Handler
5. 前端在 `intent-panels/` 创建对应的 Panel 组件
6. 在 `intent-registry.js` 注册事件处理器（不改主逻辑）

---

## 意图处理器处理步骤规范

### Phase 模板结构

每个 Handler 的 `handle()` 方法必须遵循以下 Phase 结构：

```python
async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
    """处理步骤规范：

    ═══ Phase 1：识别 (Identify)     —— 分析输入，确定任务
    ═══ Phase 2：执行 (Execute)      —— 核心业务逻辑
    ═══ Phase 3：输出 (Output)       —— SSE 事件输出
    """

    # ═══ Phase 1：识别 ══════════════════════════════════════════
    yield thinking("📋 Phase 1：识别到任务「XXX」", result={...})

    # ═══ Phase 2：执行 ══════════════════════════════════════════
    # ── Step 1：子步骤1 ────────────────────────────────────────
    try:
        result = await do_something()
        yield thinking("📝 Step 1/2：执行完成", result={...})
    except Exception as e:
        logger.warning(f"执行失败: {e}")
        yield thinking(f"❌ 执行失败: {e}", result={...})

    # ═══ Phase 3：输出 ══════════════════════════════════════════
    ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
    yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
    yield intent_event("xxx", "yyy", result, is_form=False)
    yield done_event("xxx", is_form=False, intent_data=ctx.intent_data)
```

### Phase 定义

| Phase | 名称 | 用途 | 必需输出 |
|-------|------|------|---------|
| Phase 1 | 识别 (Identify) | 分析输入，确定任务类型和参数 | `thinking()` 识别信息 |
| Phase 2 | 执行 (Execute) | 核心业务逻辑处理 | `thinking()` 执行进度 |
| Phase 3 | 输出 (Output) | SSE 事件输出 | `stats` + `intent_event` + `done_event` |

### 注释风格规范

```python
# ═══ Phase 1：识别 ══════════════════════════════════════════  # Phase 标题
# ── Step 1：子步骤 ────────────────────────────────────────    # Step 子步骤
# ── 输出结果 ────────────────────────────────────────────      # 结果输出
# ── 输出错误 ────────────────────────────────────────────      # 错误输出
```

### SSE 事件类型

| 事件类型 | 用途 | 使用场景 |
|---------|------|---------|
| `thinking` | 用户可见的思考步骤 | Phase 1/2 中的状态提示 |
| `reasoning` | AI 推理过程展示 | LLM 思考过程流式输出 |
| `sse({"type": "text"})` | 文本内容输出 | 表单内容、配置预览 |
| `sse({"type": "stats"})` | 性能统计数据 | Phase 3 统计信息 |
| `sse({"type": "error"})` | 错误信息 | 异常情况提示 |
| `intent_event` | 触发前端面板 | Phase 3 通知前端 |
| `done_event` | 结束信号 | Phase 3 最后输出 |

### thinking 消息格式

```
📋 Phase 1：标题 | 详情
📝 Step 1/2：状态 | 详情
✅ 执行完成：结果
❌ 执行失败：错误原因
```

### 新增意图类型流程

1. **创建 Handler 文件**：`backend/app/intent/handlers/{name}_handler.py`
2. **继承 BaseIntentHandler**：实现 `intent_type` 和 `handle()` 方法
3. **遵循 Phase 模板**：使用统一的 Phase 1/2/3 结构
4. **注册 Handler**：在 `backend/app/intent/__init__.py` 添加注册
5. **前端适配**：在 `intent-panels/` 创建 Panel 组件
6. **注册事件处理**：在 `intent-registry.js` 添加处理逻辑

---

**最后更新**：2026-05-07  
**维护者**：AI Team  
**版本**：v2.0 Phase 2（处理步骤规范统一）