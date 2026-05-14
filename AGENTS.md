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
  - 套餐编码：数字或字母+数字组合（如 P000111、P123456789、TC2024001、2024001）
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

## 编码质量规范

### 1. 代码风格规范

#### Python 代码风格

- **遵循 PEP 8**：所有 Python 代码必须符合 PEP 8 规范
- **缩进**：使用 4 个空格，禁止使用 Tab
- **行长度**：单行不超过 120 字符
- **空白行**：函数/类之间用 2 个空行分隔，方法之间用 1 个空行
- **导入顺序**：标准库 → 第三方库 → 项目内部模块，每组之间空一行

```python
# ✅ 正确示例
import os
import re
from typing import Optional, List

import requests
from fastapi import FastAPI

from app.utils.logger import get_logger
```

#### JavaScript/TypeScript 代码风格

- **遵循 Airbnb 规范**：所有 JS/TS 代码必须符合 Airbnb 风格指南
- **缩进**：使用 2 个空格
- **分号**：语句末尾必须加分号
- **引号**：使用单引号，JSX 中使用双引号
- **箭头函数**：单行函数体可以省略大括号

```javascript
// ✅ 正确示例
const handleClick = (e: React.MouseEvent) => {
  dispatch({ type: 'CLICK', payload: e.target.value });
};

const getValue = () => defaultValue;
```

### 2. 命名规范

| 类型 | 规则 | 示例 |
|------|------|------|
| **变量** | 小驼峰 | `userInput`, `formData` |
| **函数/方法** | 小驼峰 | `extractFields`, `validateForm` |
| **类** | 大驼峰 | `BaseIntentHandler`, `InputGuard` |
| **常量** | 全大写下划线分隔 | `MAX_RETRY`, `DANGEROUS_PATTERNS` |
| **文件** | 小写连字符 | `intent-handler.js`, `input-guard.py` |
| **目录** | 小写连字符 | `intent-handlers/`, `config/` |

### 3. 代码审查规范

#### PR 提交要求

1. **单一职责**：每个 PR 只解决一个问题或实现一个功能
2. **测试覆盖**：新增代码必须有对应的单元测试，覆盖率 ≥ 80%
3. **文档更新**：修改影响使用方式的代码时，必须更新相关文档
4. **无警告**：代码通过所有 lint 和类型检查

#### 审查要点

| 检查项 | 说明 |
|--------|------|
| **正确性** | 代码是否正确实现需求 |
| **可读性** | 代码是否易于理解 |
| **可维护性** | 是否符合设计模式和架构原则 |
| **安全性** | 是否存在安全隐患 |
| **性能** | 是否有性能优化空间 |
| **测试** | 测试用例是否完整 |

### 4. 测试规范

#### 测试类型

| 类型 | 说明 | 覆盖率要求 |
|------|------|------------|
| **单元测试** | 测试单个函数/方法 | ≥ 80% |
| **集成测试** | 测试模块间交互 | 关键路径覆盖 |
| **端到端测试** | 测试完整业务流程 | 核心场景覆盖 |

#### 测试命名约定

```python
# 测试文件：test_{module_name}.py
# 测试类：Test{ClassName}
# 测试方法：test_{scenario}_{expected_behavior}

class TestInputGuard:
    def test_should_block_xss_script(self):
        # 测试逻辑
        pass
    
    def test_should_allow_valid_input(self):
        # 测试逻辑
        pass
```

### 5. 文档规范

#### 代码注释

- **模块级**：每个模块顶部必须有文档字符串，说明功能、输入输出
- **函数级**：公共函数必须有文档字符串
- **复杂逻辑**：非直观的业务逻辑必须添加注释说明
- **禁止冗余**：代码自解释时无需注释

```python
def extract_fields(user_input: str, schema: dict) -> dict:
    """从用户输入中提取字段值
    
    Args:
        user_input: 用户输入文本
        schema: 表单 Schema 定义
        
    Returns:
        提取的字段字典，key 为字段编码，value 为字段值
        
    Raises:
        ValidationError: Schema 格式不正确时抛出
    """
    # 核心提取逻辑...
```

#### API 文档

- **OpenAPI 规范**：所有 API 必须有完整的 OpenAPI 文档
- **参数说明**：每个参数必须说明类型、是否必填、含义
- **返回值说明**：说明返回结构和字段含义

### 6. 代码复杂度控制

| 指标 | 阈值 | 说明 |
|------|------|------|
| **圈复杂度** | ≤ 10 | 单个函数/方法的分支数量 |
| **函数长度** | ≤ 50 行 | 超出应拆分为多个函数 |
| **类方法数** | ≤ 20 | 超出应考虑拆分类 |
| **嵌套深度** | ≤ 4 | 超出应重构 |

### 7. 静态分析工具

#### 必用工具

| 工具 | 用途 | 配置文件 |
|------|------|----------|
| **flake8** | Python 代码检查 | `.flake8` |
| **mypy** | Python 类型检查 | `mypy.ini` |
| **eslint** | JavaScript/TypeScript 检查 | `.eslintrc.json` |
| **prettier** | 代码格式化 | `.prettierrc` |

#### 配置要求

- **CI 集成**：所有静态分析工具必须集成到 CI 流程
- **预提交钩子**：使用 `pre-commit` 确保提交代码符合规范

```yaml
# .pre-commit-config.yaml 示例
repos:
  - repo: https://github.com/PyCQA/flake8
    rev: 6.0.0
    hooks:
      - id: flake8
  - repo: https://github.com/python/mypy
    rev: v1.5.1
    hooks:
      - id: mypy
```

### 8. 依赖管理

- **版本锁定**：使用 `requirements.txt` 或 `pyproject.toml` 锁定依赖版本
- **定期更新**：每季度检查并更新依赖版本
- **安全扫描**：使用 `safety` 或 `pip-audit` 扫描已知安全漏洞

---

## 设计模式规范

### 1. 创建型模式

#### 工厂模式 (Factory Pattern)

**适用场景**：对象创建逻辑复杂，需要统一管理创建过程

**应用示例**：推荐引擎策略创建

```python
class StrategyFactory:
    _strategies = {}

    @classmethod
    def register(cls, strategy_type: str, strategy_class):
        cls._strategies[strategy_type] = strategy_class

    @classmethod
    def create(cls, strategy_type: str, **kwargs):
        if strategy_type not in cls._strategies:
            raise ValueError(f"Unknown strategy: {strategy_type}")
        return cls._strategies[strategy_type](**kwargs)

StrategyFactory.register('frequency', FrequencyStrategy)
StrategyFactory.register('time_decay', TimeDecayStrategy)
```

**使用位置**：`backend/app/recommendation/strategy_factory.py`

#### 单例模式 (Singleton Pattern)

**适用场景**：全局唯一的资源管理器、配置管理器

**应用示例**：日志管理器、配置中心

```python
class SingletonMeta(type):
    _instances = {}

    def __call__(cls, *args, **kwargs):
        if cls not in cls._instances:
            cls._instances[cls] = super().__call__(*args, **kwargs)
        return cls._instances[cls]

class ConfigManager(metaclass=SingletonMeta):
    def __init__(self):
        self._config = self._load_config()
```

**使用位置**：`backend/app/utils/config_manager.py`

### 2. 结构型模式

#### 策略模式 (Strategy Pattern)

**适用场景**：算法需要灵活切换，避免大量条件判断

**应用示例**：推荐引擎多策略融合

```python
from abc import ABC, abstractmethod

class RecommendationStrategy(ABC):
    @abstractmethod
    def recommend(self, user_input: str, context: dict) -> list:
        pass

class FrequencyStrategy(RecommendationStrategy):
    def recommend(self, user_input: str, context: dict) -> list:
        # 基于频率的推荐逻辑
        pass

class TimeDecayStrategy(RecommendationStrategy):
    def recommend(self, user_input: str, context: dict) -> list:
        # 基于时间衰减的推荐逻辑
        pass
```

**使用位置**：`backend/app/recommendation/strategies/`

#### 适配器模式 (Adapter Pattern)

**适用场景**：接口不兼容的组件需要协同工作

**应用示例**：外部 API 数据格式转换

```python
class ExternalApiAdapter:
    def __init__(self, external_api):
        self._external_api = external_api

    def get_form_data(self, form_code: str) -> dict:
        raw_data = self._external_api.fetch(form_code)
        return self._transform(raw_data)

    def _transform(self, raw_data: dict) -> dict:
        return {
            'formCode': raw_data.get('form_code'),
            'fields': self._transform_fields(raw_data.get('fields', []))
        }
```

**使用位置**：`backend/app/external/adapters/`

#### 装饰器模式 (Decorator Pattern)

**适用场景**：需要动态添加功能，且不修改原有代码

**应用示例**：日志装饰器、性能监控装饰器

```python
def log_execution(func):
    async def wrapper(*args, **kwargs):
        logger.info(f"Executing {func.__name__}")
        start_time = time.time()
        try:
            result = await func(*args, **kwargs)
            return result
        finally:
            elapsed = time.time() - start_time
            logger.info(f"{func.__name__} completed in {elapsed:.2f}s")
    return wrapper

@log_execution
async def extract_fields(user_input: str, schema: dict) -> dict:
    # 字段提取逻辑
    pass
```

**使用位置**：`backend/app/utils/decorators.py`

### 3. 行为型模式

#### 观察者模式 (Observer Pattern)

**适用场景**：一个对象状态变化需要通知多个观察者

**应用示例**：SSE 事件推送、状态变更通知

```python
class Subject:
    def __init__(self):
        self._observers = []

    def attach(self, observer):
        if observer not in self._observers:
            self._observers.append(observer)

    def detach(self, observer):
        self._observers.remove(observer)

    def notify(self, event: dict):
        for observer in self._observers:
            observer.update(event)

class SseObserver:
    def update(self, event: dict):
        self.send_event(event)
```

**使用位置**：`backend/app/sse/event_manager.py`

#### 模板方法模式 (Template Method Pattern)

**适用场景**：算法骨架固定，细节步骤可定制

**应用示例**：意图处理器基类

```python
from abc import ABC, abstractmethod

class BaseIntentHandler(ABC):
    async def handle(self, ctx: IntentContext):
        # Phase 1: 识别
        await self._identify(ctx)
        
        # Phase 2: 执行
        await self._execute(ctx)
        
        # Phase 3: 输出
        await self._output(ctx)

    @abstractmethod
    def _identify(self, ctx: IntentContext):
        pass

    @abstractmethod
    def _execute(self, ctx: IntentContext):
        pass

    @abstractmethod
    def _output(self, ctx: IntentContext):
        pass
```

**使用位置**：`backend/app/intent/handlers/base_handler.py`

#### 责任链模式 (Chain of Responsibility Pattern)

**适用场景**：请求需要经过多个处理器依次处理

**应用示例**：输入护栏校验链

```python
from abc import ABC, abstractmethod

class GuardHandler(ABC):
    def __init__(self, next_handler=None):
        self._next_handler = next_handler

    @abstractmethod
    def handle(self, user_input: str) -> bool:
        pass

class XssGuard(GuardHandler):
    def handle(self, user_input: str) -> bool:
        if self._contains_xss(user_input):
            raise ValueError("XSS attack detected")
        if self._next_handler:
            return self._next_handler.handle(user_input)
        return True

class SqlInjectionGuard(GuardHandler):
    def handle(self, user_input: str) -> bool:
        if self._contains_sql_injection(user_input):
            raise ValueError("SQL injection detected")
        if self._next_handler:
            return self._next_handler.handle(user_input)
        return True
```

**使用位置**：`backend/app/guard/input_guard.py`

#### 命令模式 (Command Pattern)

**适用场景**：需要将操作封装为对象，支持撤销/重做

**应用示例**：表单操作日志、事务管理

```python
from abc import ABC, abstractmethod

class Command(ABC):
    @abstractmethod
    def execute(self):
        pass

    @abstractmethod
    def undo(self):
        pass

class FormSubmitCommand(Command):
    def __init__(self, form_service, form_data):
        self._form_service = form_service
        self._form_data = form_data
        self._previous_state = None

    def execute(self):
        self._previous_state = self._form_service.get_current_state()
        self._form_service.submit(self._form_data)

    def undo(self):
        self._form_service.restore_state(self._previous_state)
```

**使用位置**：`backend/app/commands/`

### 4. 架构模式

#### MVC 模式 (Model-View-Controller)

**分层结构**：

| 层级 | 职责 | 示例文件 |
|------|------|----------|
| **Model** | 数据模型和业务逻辑 | `backend/app/models/` |
| **View** | 数据展示（前端） | `frontend/components/` |
| **Controller** | 请求处理和协调 | `backend/app/api/` |

#### 依赖注入模式 (Dependency Injection)

**应用示例**：FastAPI 依赖注入

```python
from fastapi import Depends

async def get_recommendation_service(
    db: Session = Depends(get_db),
    config: ConfigManager = Depends()
) -> RecommendationService:
    return RecommendationService(db=db, config=config)

@app.post("/recommend")
async def recommend(
    request: RecommendRequest,
    service: RecommendationService = Depends(get_recommendation_service)
):
    return await service.recommend(request)
```

**使用位置**：`backend/app/api/dependencies.py`

### 5. 设计模式使用指南

| 场景 | 推荐模式 | 理由 |
|------|----------|------|
| 对象创建 | 工厂模式 | 解耦创建逻辑与使用方 |
| 算法切换 | 策略模式 | 运行时动态选择算法 |
| 功能扩展 | 装饰器模式 | 无侵入式添加功能 |
| 状态通知 | 观察者模式 | 解耦发布者与订阅者 |
| 请求处理 | 责任链模式 | 灵活组合处理器 |
| 代码复用 | 模板方法模式 | 定义算法骨架 |
| 接口适配 | 适配器模式 | 兼容不同接口 |

---

## 编码原则

### 1. SOLID 原则

#### 单一职责原则 (SRP - Single Responsibility Principle)

**定义**：一个类应该只有一个引起它变化的原因

**应用示例**：

```python
# ❌ 错误：一个类负责多个职责
class FormService:
    def validate_form(self, form_data):
        # 验证逻辑
        pass
    
    def save_to_database(self, form_data):
        # 数据库操作
        pass
    
    def send_notification(self, form_data):
        # 通知逻辑
        pass

# ✅ 正确：职责分离
class FormValidator:
    def validate(self, form_data):
        pass

class FormRepository:
    def save(self, form_data):
        pass

class NotificationService:
    def send(self, form_data):
        pass
```

#### 开闭原则 (OCP - Open/Closed Principle)

**定义**：软件实体应该对扩展开放，对修改关闭

**应用示例**：

```python
# ❌ 错误：需要修改现有代码添加新策略
class RecommendationService:
    def recommend(self, strategy_type, user_input):
        if strategy_type == 'frequency':
            # 频率策略逻辑
            pass
        elif strategy_type == 'time_decay':
            # 时间衰减策略逻辑
            pass
        # 添加新策略需要修改此方法

# ✅ 正确：通过扩展实现新功能
class RecommendationService:
    def __init__(self, strategies):
        self._strategies = strategies
    
    def recommend(self, strategy_type, user_input):
        strategy = self._strategies.get(strategy_type)
        if strategy:
            return strategy.recommend(user_input)
```

#### 里氏替换原则 (LSP - Liskov Substitution Principle)

**定义**：子类对象应该能够替换父类对象而不影响程序的正确性

**应用示例**：

```python
# ✅ 正确：子类可以替换父类
class Animal:
    def make_sound(self):
        pass

class Dog(Animal):
    def make_sound(self):
        return "Woof"

class Cat(Animal):
    def make_sound(self):
        return "Meow"

def play_sound(animal: Animal):
    print(animal.make_sound())

# 子类可以安全替换父类
play_sound(Dog())  # Output: Woof
play_sound(Cat())  # Output: Meow
```

#### 接口隔离原则 (ISP - Interface Segregation Principle)

**定义**：客户端不应该被迫依赖它不需要的接口

**应用示例**：

```python
# ❌ 错误：胖接口
class Worker:
    def work(self):
        pass
    
    def eat(self):
        pass
    
    def sleep(self):
        pass

# ✅ 正确：接口分离
class Workable:
    def work(self):
        pass

class Eatable:
    def eat(self):
        pass

class Sleepable:
    def sleep(self):
        pass

class HumanWorker(Workable, Eatable, Sleepable):
    pass

class RobotWorker(Workable):
    pass  # 机器人不需要 eat 和 sleep
```

#### 依赖倒置原则 (DIP - Dependency Inversion Principle)

**定义**：高层模块不应该依赖低层模块，两者都应该依赖抽象

**应用示例**：

```python
# ❌ 错误：高层依赖低层具体实现
class MySQLDatabase:
    def connect(self):
        pass

class UserService:
    def __init__(self):
        self._db = MySQLDatabase()  # 直接依赖具体类

# ✅ 正确：依赖抽象接口
class Database:
    def connect(self):
        pass

class MySQLDatabase(Database):
    def connect(self):
        pass

class UserService:
    def __init__(self, db: Database):
        self._db = db  # 依赖抽象接口
```

### 2. DRY 原则 (Don't Repeat Yourself)

**定义**：不要重复代码，相同的逻辑应该只出现一次

**应用示例**：

```python
# ❌ 错误：重复的验证逻辑
def validate_email(email):
    if not email:
        raise ValueError("Email is required")
    if '@' not in email:
        raise ValueError("Invalid email format")

def validate_username(username):
    if not username:
        raise ValueError("Username is required")
    if len(username) < 3:
        raise ValueError("Username must be at least 3 characters")

# ✅ 正确：提取公共验证逻辑
def validate_required(field, name):
    if not field:
        raise ValueError(f"{name} is required")

def validate_email(email):
    validate_required(email, "Email")
    if '@' not in email:
        raise ValueError("Invalid email format")

def validate_username(username):
    validate_required(username, "Username")
    if len(username) < 3:
        raise ValueError("Username must be at least 3 characters")
```

### 3. KISS 原则 (Keep It Simple, Stupid)

**定义**：保持代码简单易懂，避免不必要的复杂性

**应用示例**：

```python
# ❌ 错误：过度复杂的实现
def calculate_average(numbers):
    total = sum(numbers)
    count = len(numbers)
    if count == 0:
        raise ValueError("Cannot calculate average of empty list")
    return total / count

# ✅ 正确：简洁明了
def calculate_average(numbers):
    if not numbers:
        raise ValueError("Cannot calculate average of empty list")
    return sum(numbers) / len(numbers)
```

### 4. YAGNI 原则 (You Ain't Gonna Need It)

**定义**：不要实现当前不需要的功能

**实践要点**：
- 只实现当前需求明确需要的功能
- 避免过度设计和"未来可能需要"的功能
- 保持代码灵活，便于后续扩展

### 5. 高内聚低耦合

**定义**：模块内部应该高度相关，模块之间应该松散耦合

**应用示例**：

```
┌─────────────────────────────────────────────────────┐
│           高内聚示例                                │
│  ┌──────────────┐    ┌──────────────┐             │
│  │  UserService │    │  FormService │             │
│  │  ──────────  │    │  ──────────  │             │
│  │  - create()  │    │  - submit()  │             │
│  │  - update()  │    │  - validate()│             │
│  │  - delete()  │    │  - query()   │             │
│  └──────────────┘    └──────────────┘             │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│           低耦合示例                                │
│                                                    │
│   UserService ───(抽象接口)───> Database           │
│                                    ▲               │
│                                    │               │
│                              MySQLDatabase         │
│                              PostgreSQLDatabase    │
└─────────────────────────────────────────────────────┘
```

### 6. 关注点分离 (SoC - Separation of Concerns)

**定义**：将不同的关注点分离到不同的模块中

**应用示例**：

| 关注点 | 模块 | 职责 |
|--------|------|------|
| 数据访问 | `repository/` | 数据库操作 |
| 业务逻辑 | `service/` | 核心业务处理 |
| API 控制 | `api/` | 请求响应处理 |
| 数据验证 | `validation/` | 数据校验逻辑 |
| 配置管理 | `config/` | 配置读取管理 |

### 7. 最小惊讶原则 (Principle of Least Surprise)

**定义**：代码行为应该符合用户的预期

**应用示例**：

```python
# ❌ 错误：行为不符合预期
def get_users(active_only=True):
    # 函数名暗示获取用户列表
    # 但默认只返回活跃用户，可能令人惊讶
    pass

# ✅ 正确：行为清晰明确
def get_users(filter_active=None):
    """
    获取用户列表
    
    Args:
        filter_active: None表示不筛选, True只返回活跃用户, False只返回非活跃用户
    """
    pass
```

### 8. 防御性编程

**定义**：假设输入可能是错误的，提前处理异常情况

**应用示例**：

```python
# ✅ 防御性编程示例
def process_form(form_data):
    # 验证输入类型
    if not isinstance(form_data, dict):
        raise TypeError("form_data must be a dictionary")
    
    # 验证必填字段
    required_fields = ['name', 'email']
    missing_fields = [f for f in required_fields if f not in form_data]
    if missing_fields:
        raise ValueError(f"Missing required fields: {', '.join(missing_fields)}")
    
    # 验证字段类型
    if not isinstance(form_data['name'], str):
        raise TypeError("name must be a string")
    
    # 安全处理
    sanitized_email = sanitize_email(form_data['email'])
    return process(sanitized_email)
```

### 9. 可测试性原则

**定义**：代码应该易于编写测试

**实践要点**：
- 使用依赖注入，便于 mock
- 保持函数单一职责
- 避免全局状态
- 返回确定的结果

**应用示例**：

```python
# ❌ 错误：难以测试
class OrderService:
    def __init__(self):
        self._repo = OrderRepository()  # 硬编码依赖
    
    def calculate_total(self, order_id):
        order = self._repo.get(order_id)
        return order.amount * 1.08  # 税率硬编码

# ✅ 正确：易于测试
class OrderService:
    def __init__(self, repo, tax_rate=0.08):
        self._repo = repo
        self._tax_rate = tax_rate
    
    def calculate_total(self, order_id):
        order = self._repo.get(order_id)
        return order.amount * (1 + self._tax_rate)

# 测试时可以注入 mock
def test_calculate_total():
    mock_repo = Mock()
    mock_repo.get.return_value = Order(amount=100)
    service = OrderService(mock_repo, tax_rate=0.08)
    assert service.calculate_total(1) == 108
```

### 10. 代码可读性原则

**实践要点**：

| 原则 | 说明 | 示例 |
|------|------|------|
| **有意义的命名** | 使用描述性名称 | `user_input` vs `u` |
| **适当的注释** | 解释为什么，而非做什么 | 注释业务规则而非代码逻辑 |
| **合理的结构** | 清晰的代码组织 | 合理的函数拆分 |
| **一致的风格** | 统一的编码风格 | 遵循项目规范 |
| **避免魔法数字** | 使用常量替代 | `MAX_RETRY = 3` vs `3` |

---

**最后更新**：2026-05-14  
**维护者**：AI Team  
**版本**：v2.0 Phase 5（编码原则完善）