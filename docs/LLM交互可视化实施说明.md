# LLM 交互输入输出可视化 - 实施说明

## 📋 概述

本次优化将大模型（LLM）交互的**输入（Prompt）**和**输出（Response）**纳入到前端的"模型思考"展示中，让用户能够看到完整的 LLM 调用过程，提升系统透明度和可解释性。

## 🎯 目标

1. **透明化**：用户可以看到发送给 LLM 的完整 prompt
2. **可追溯**：用户可以看到 LLM 返回的完整 response
3. **可调试**：开发者可以通过前端直接查看 LLM 交互内容，无需查看后端日志

## 🔧 修改内容

### 1. 意图识别阶段（chat.py）

**文件**: `backend/app/api/chat.py`

#### 修改点 1: 意图识别 Prompt/Response 展示

```python
# 在调用 LLM 进行意图识别时
yield thinking("🧠 调用 LLM 进行意图识别...", result={...})

# 【新增】发送 prompt 内容到前端（可展开查看）
yield reasoning(f"📥 Prompt 输入（{len(intent_prompt)} 字符）:\n\n{intent_prompt[:2000]}{'...' if len(intent_prompt) > 2000 else ''}")

# 调用 LLM
intent_result, intent_reasoning = await loop.run_in_executor(...)

# 【新增】发送 response 内容到前端（可展开查看）
if intent_result:
    response_preview = intent_result[:2000] + ('...' if len(intent_result) > 2000 else '')
    yield reasoning(f"📤 Response 输出（{len(intent_result)} 字符）:\n\n{response_preview}")
```

#### 修改点 2: 场景提示词调用 Prompt/Response 展示

```python
# 在调用场景提示词时
yield thinking(f"🧠 使用场景提示词调用大模型...")

# 【新增】发送场景 prompt 到前端
yield reasoning(f"📥 场景 Prompt 输入（{len(scene_prompt_content) + len(last_user_message)} 字符）:\n\n系统提示词：{scene_prompt_content[:1000]}...\n\n用户消息：{last_user_message[:500]}...")

# 调用 LLM
scene_response = llm_service._call_llm_sync(last_user_message, system_prompt=scene_prompt_content)

# 【新增】发送场景 response 到前端
response_preview = scene_response[:2000] + ('...' if len(scene_response) > 2000 else '')
yield reasoning(f"📤 场景 Response 输出（{len(scene_response)} 字符）:\n\n{response_preview}")
```

### 2. AI 字段推断阶段（form_handler.py）

**文件**: `backend/app/intent/handlers/form_handler.py`

#### 修改点: AI 推断 Prompt/Response 展示

```python
# 在调用 AI 进行字段推断时
yield thinking("🧠 调用 AI 进行字段推断...")

# 【新增】获取 prompt 内容用于展示
ontology = config_loader.get_ontology(form_code)
if ontology:
    inference_prompt = ai_service._build_inference_prompt(ontology, user_input, context)
    # 发送 prompt 到前端
    yield reasoning_event(f"📥 AI 推断 Prompt 输入（{len(inference_prompt)} 字符）:\n\n{inference_prompt[:2000]}...")

# 调用 AI 推断
inference_result = ai_service.infer_fields(...)

# 【新增】发送 response 到前端
if inference_result:
    response_preview = str(inference_result)[:2000] + ('...' if len(str(inference_result)) > 2000 else '')
    yield reasoning_event(f"📤 AI 推断 Response 输出（{len(str(inference_result))} 字符）:\n\n{response_preview}")
```

## 📊 前端展示效果

### 展示结构

```
🔄 处理步骤 (X 步)
  ├─ 🧠 调用 LLM 进行意图识别...
  │   └─ 📥 Prompt 输入（XXXX 字符） [点击展开]
  │       └─ [显示前 2000 字符的 prompt 内容]
  │   └─ 📤 Response 输出（XXX 字符） [点击展开]
  │       └─ [显示前 2000 字符的 response 内容]
  │   └─ 🧠 分析用户意图...
  │       └─ [模型推理过程]
  ├─ 🔍 查询场景提示词...
  ├─ 🧠 使用场景提示词调用大模型...
  │   └─ 📥 场景 Prompt 输入（XXXX 字符） [点击展开]
  │   └─ 📤 场景 Response 输出（XXX 字符） [点击展开]
  └─ ...
```

### 交互方式

1. **自动折叠**：Prompt 和 Response 默认折叠，只显示标题和字符数
2. **点击展开**：用户点击箭头图标可以展开查看详细内容
3. **长度限制**：为避免过长内容影响性能，只显示前 2000 字符，超出部分用 "..." 表示
4. **格式化显示**：使用 `<pre>` 标签保持原始格式

## 🎨 视觉设计

### 图标规范

- 📥 **蓝色下载图标**：表示输入（Prompt）
- 📤 **绿色上传图标**：表示输出（Response）
- 🧠 **大脑图标**：表示 AI 思考/推理

### 颜色规范

- **Prompt 输入**：蓝色边框/背景（与现有 thinking 样式一致）
- **Response 输出**：绿色边框/背景
- **推理过程**：紫色边框/背景

## ⚙️ 配置选项

### 字符限制

当前实现中，Prompt 和 Response 都限制显示前 **2000 字符**。如需调整，修改以下位置：

```python
# chat.py - 意图识别
intent_prompt[:2000]  # 修改为其他值
intent_result[:2000]  # 修改为其他值

# chat.py - 场景调用
scene_prompt_content[:1000]  # 修改为其他值
scene_response[:2000]  # 修改为其他值

# form_handler.py
inference_prompt[:2000]  # 修改为其他值
str(inference_result)[:2000]  # 修改为其他值
```

### 完全显示（开发环境）

如果需要在开发环境中显示完整内容，可以：

```python
# 移除截断逻辑
yield reasoning(f"📥 Prompt 输入（{len(intent_prompt)} 字符）:\n\n{intent_prompt}")
```

⚠️ **注意**：生产环境不建议完全显示，可能导致：
- 前端性能问题（大量 DOM 节点）
- 用户体验下降（滚动过长）
- 安全风险（暴露敏感信息）

## 🧪 测试建议

### 测试场景

1. **意图识别测试**
   - 发送："资费备案公示"
   - 检查是否显示意图识别的 Prompt 和 Response

2. **场景调用测试**
   - 发送："备案套餐 P000111"
   - 检查是否显示场景提示词的 Prompt 和 Response

3. **AI 推断测试**
   - 触发表单生成流程
   - 检查是否显示 AI 字段推断的 Prompt 和 Response

### 验证要点

- ✅ Prompt 内容正确显示（包含本体定义、用户输入等）
- ✅ Response 内容正确显示（JSON 格式的推断结果）
- ✅ 点击展开/折叠功能正常
- ✅ 字符计数准确
- ✅ 超长内容有 "..." 截断提示
- ✅ 不影响原有的推理过程显示

## 🔒 安全考虑

### 敏感信息过滤

在生产环境中，可能需要对以下内容进行脱敏：

1. **API Key**：确保 prompt 中不包含 API Key
2. **用户隐私数据**：如手机号、身份证号等
3. **内部配置**：如数据库连接字符串等

### 实现建议

```python
def sanitize_for_display(text: str, max_length: int = 2000) -> str:
    """清理敏感信息并截断"""
    # 替换 API Key
    text = re.sub(r'sk-[a-zA-Z0-9]{32,}', '[REDACTED]', text)
    # 替换手机号
    text = re.sub(r'1[3-9]\d{9}', '[PHONE]', text)
    # 截断
    if len(text) > max_length:
        return text[:max_length] + '...'
    return text
```

## 📈 性能影响

### 预期影响

- **网络传输**：每次 LLM 调用增加约 2-4 KB 的数据传输（2000 字符 × 2）
- **前端渲染**：每个 reasoning 事件增加一个可折叠的 DOM 节点
- **内存占用**：每条消息增加约 4-8 KB 的内存占用

### 优化建议

1. **懒加载**：只在用户点击展开时才渲染详细内容
2. **虚拟滚动**：对于大量 reasoning 步骤，使用虚拟列表
3. **缓存策略**：已展开的内容可以缓存，避免重复渲染

## 🚀 后续优化方向

1. **语法高亮**：对 JSON 格式的 response 进行语法高亮
2. **对比视图**：提供 Prompt/Response 的并排对比视图
3. **搜索功能**：在长文本中支持关键词搜索
4. **导出功能**：允许用户导出完整的 LLM 交互日志
5. **统计信息**：显示 Token 消耗、响应时间等统计信息

## 📝 相关文件清单

### 后端文件

- `backend/app/api/chat.py` - 主聊天接口（意图识别、场景调用）
- `backend/app/intent/handlers/form_handler.py` - 表单生成处理器（AI 推断）
- `backend/app/services/ai_inference_service.py` - AI 推断服务（构建 prompt）

### 前端文件

- `frontend/src/composables/useChatStream.js` - SSE 事件处理
- `frontend/src/components/ChatMessageList.vue` - 消息展示组件

## ✅ 验收标准

- [ ] 所有 LLM 调用都显示 Prompt 和 Response
- [ ] 点击展开/折叠功能正常
- [ ] 字符计数准确
- [ ] 超长内容有截断提示
- [ ] 不影响原有推理过程显示
- [ ] 前端性能无明显下降
- [ ] 无控制台错误

---

**实施日期**: 2026-05-12  
**版本**: v1.0  
**作者**: AI Assistant
