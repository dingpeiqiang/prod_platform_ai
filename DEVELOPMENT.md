# DEVELOPMENT.md - 开发指南

> 本文档定义开发流程、意图处理器规范和代码审查标准。

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
3. 更新 `TOOLS.md` 威胁类型表

### 新增意图处理器（Handler）

1. 在 `backend/app/intent/handlers/` 创建 `{name}_handler.py`
2. 继承 `BaseIntentHandler`，实现 `intent_type` 和 `handle()` 方法
3. 遵循 Phase 模板结构（见下文）
4. 在 `backend/app/intent/__init__.py` 注册 Handler
5. 前端在 `intent-panels/` 创建对应的 Panel 组件
6. 在 `intent-registry.js` 注册事件处理器（不改主逻辑）

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

| Phase   | 名称            | 用途             | 必需输出                                    |
| ------- | ------------- | -------------- | --------------------------------------- |
| Phase 1 | 识别 (Identify) | 分析输入，确定任务类型和参数 | `thinking()` 识别信息                       |
| Phase 2 | 执行 (Execute)  | 核心业务逻辑处理       | `thinking()` 执行进度                       |
| Phase 3 | 输出 (Output)   | SSE 事件输出       | `stats` + `intent_event` + `done_event` |

### 注释风格规范

```python
# ═══ Phase 1：识别 ══════════════════════════════════════════  # Phase 标题
# ── Step 1：子步骤 ────────────────────────────────────────    # Step 子步骤
# ── 输出结果 ────────────────────────────────────────────      # 结果输出
# ── 输出错误 ────────────────────────────────────────────      # 错误输出
```

### SSE 事件类型

| 事件类型                     | 用途        | 使用场景             |
| ------------------------ | --------- | ---------------- |
| `thinking`               | 用户可见的思考步骤 | Phase 1/2 中的状态提示 |
| `reasoning`              | AI 推理过程展示 | LLM 思考过程流式输出     |
| `sse({"type": "text"})`  | 文本内容输出    | 表单内容、配置预览        |
| `sse({"type": "stats"})` | 性能统计数据    | Phase 3 统计信息     |
| `sse({"type": "error"})` | 错误信息      | 异常情况提示           |
| `intent_event`           | 触发前端面板    | Phase 3 通知前端     |
| `done_event`             | 结束信号      | Phase 3 最后输出     |

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

**相关文档**：
- [架构设计规范](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/ARCHITECTURE.md)
- [表单系统规范](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/FORMS.md)
- [工具与安全规范](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/TOOLS.md)
- [编码质量规范](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/AGENTS.md)