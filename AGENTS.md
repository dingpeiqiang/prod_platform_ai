# AGENTS.md - AI Agent 开发规范

> 本文档为 AI Agent 提供静态上下文，定义项目规范、架构约束和行为准则。

## 项目概述

**项目名称**：work-ai（AI驱动动态表单底层框架）  
**版本**：v2.0  
**核心能力**：智能表单识别、字段提取、表单验证

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

---

## 表单系统

### 表单类型编码

| 编码 | 类型 | 必填字段 | 可选字段 |
|------|------|----------|----------|
| `leave` | 请假申请 | leave_type, leave_days | reason, start_date, end_date |
| `expense` | 报销申请 | amount, category | description, receipt_ids |
| `survey` | 调查问卷 | (根据问卷定义) | (根据问卷定义) |
| `general` | 通用表单 | (无) | (无) |

### 字段类型定义

| 类型 | 格式 | 示例 |
|------|------|------|
| `string` | 任意文本 | "张三" |
| `integer` | 整数 | 3 |
| `number` | 数值 | 123.45 |
| `boolean` | true/false | true |
| `date` | YYYY-MM-DD | 2026-04-17 |
| `datetime` | ISO 8601 | 2026-04-17T10:30:00 |
| `email` | 邮箱格式 | user@example.com |
| `phone` | 手机号 | 13812345678 |
| `enum` | 枚举值 | ["年假", "病假", "事假"] |

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
      {"field": "leave_days", "message": "请假天数必填"}
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
| `sql_union` | SQL UNION 注入 | 高 |
| `sql_drop` | SQL DROP 注入 | 严重 |
| `cmd_shell` | Shell 命令注入 | 严重 |
| `prompt_ignore` | Prompt 忽略攻击 | 中 |
| `prompt_jailbreak` | Prompt 越狱攻击 | 高 |

### 敏感信息检测

- API Key
- Password
- Private Key
- Bearer Token

---

## 工作流程

### 表单识别流程

```
用户输入 → 输入护栏 → 上下文注入 → LLM 识别 → 输出校验 → 返回结果
```

### 字段提取流程

```
用户输入 + 表单类型 → 输入护栏 → Schema 加载 → LLM 提取 → 输出校验 → 返回结果
```

### 表单验证流程

```
表单数据 + Schema → 字段校验 → 类型检查 → 范围检查 → 返回验证结果
```

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

---

## 开发指南

### 新增表单类型

1. 在 `config/schemas/` 添加 `{form_code}.json`
2. 在 `AGENTS.md` 更新表单类型表
3. 更新 `EnhancedToolRegistry` 添加相关工具

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
3. 在 `backend/app/intent/__init__.py` 注册 Handler
4. 前端在 `intent-panels/` 创建对应的 Panel 组件
5. 在 `intent-registry.js` 注册事件处理器（不改主逻辑）

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
    yield thinking("📋 识别到任务「XXX」", result={...})

    # ═══ Phase 2：执行 ══════════════════════════════════════════
    # ── Step 1：子步骤1 ────────────────────────────────────────
    try:
        result = await do_something()
        yield thinking(f"✅ 执行完成", result={...})
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
**维护者**：AI Team  
**版本**：v2.0 Phase 1
