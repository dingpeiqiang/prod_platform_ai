# AI Skills（Function Calling）功能说明

## 概述

项目已改造为真正的AI Skills！现在LLM可以主动调用这些Skills（工具）来完成任务！

---

## 📋 改造前后对比

| 方面 | 改造前 | 改造后 |
|------|--------|--------|
| **Skills定位** | 轻量级业务模块，独立运行 | AI工具（Tools），可被LLM调用 |
| **LLM关系** | 独立运行，互不干扰 | LLM可以决定何时调用哪个Skill |
| **命名** | 容易混淆（不是AI生态的Skills） | 明确：Tools/Function Calling |
| **调用流程** | 先调用Skills，再调用LLM（可选） | LLM决定调用哪些Tools |

---

## 🛠️ 三个AI Skills（Tools）

| 工具名称 | 说明 |
|---------|------|
| **recognize_scene** | 识别用户输入的场景（表单类型） |
| **extract_fields** | 从用户输入中提取字段值 |
| **get_available_forms** | 获取所有可用的表单类型列表 |

---

## 📁 核心文件

| 文件 | 说明 |
|------|------|
| `app/skills/tool_registry.py` | 工具注册器（ToolRegistry） |
| `app/skills/__init__.py` | Skills初始化和工具注册 |
| `config/prompts/function_calling.txt` | Function Calling提示词 |
| `app/api/chat_with_tools.py` | 带工具调用的聊天API |

---

## 🔧 ToolRegistry - 工具注册器

### 功能
- 注册工具定义（名称、描述、参数Schema）
- 获取工具列表（OpenAI Function Calling格式）
- 调用工具执行

### 工具定义格式
```json
{
  "type": "function",
  "function": {
    "name": "recognize_scene",
    "description": "识别用户输入的场景（表单类型），返回表单编码",
    "parameters": {
      "type": "object",
      "properties": {
        "user_input": {
          "type": "string",
          "description": "用户的原始输入文本"
        }
      },
      "required": ["user_input"]
    }
  }
}
```

---

## 🌐 API接口

### POST /api/v1/chat_with_tools

带工具调用的聊天接口

**请求：**
```json
{
  "messages": [
    {"role": "user", "content": "帮我填一个请假申请，请假天数3天"}
  ]
}
```

**响应：**
```json
{
  "success": true,
  "intentType": "form",
  "formCode": "leave",
  "extractedFields": {
    "leave_days": 3
  },
  "toolCalls": [
    {
      "tool": "recognize_scene",
      "input": "帮我填一个请假申请...",
      "output": "leave"
    },
    {
      "tool": "extract_fields",
      "input": {"user_input": "...", "form_code": "leave"},
      "output": {"leave_days": 3}
    }
  ]
}
```

---

## 📊 当前实现状态

由于LLM Function Calling需要真实的LLM API，当前实现：

| 功能 | 状态 | 说明 |
|------|------|------|
| **ToolRegistry** | ✅ 完成 | 工具注册和调用框架 |
| **三个Tools** | ✅ 完成 | recognize_scene、extract_fields、get_available_forms |
| **提示词** | ✅ 完成 | function_calling.txt |
| **手动工具调用** | ✅ 完成 | 演示工具调用流程 |
| **真实LLM Function Calling** | ⏸️ 待定 | 需要LLM API Key |

---

## 🚀 下一步（可选）

如果配置了LLM API Key，可以实现：

1. **LLM主动调用工具**
   ```
   用户：帮我填一个请假
   LLM：我需要先识别场景，让我调用recognize_scene
   LLM调用recognize_scene → "leave"
   LLM：现在让我提取字段，调用extract_fields
   LLM调用extract_fields → {...}
   LLM：好的，我来帮你生成表单
   ```

2. **流式显示工具调用**
   - 实时显示LLM调用了哪个工具
   - 显示工具的输入和输出
   - 完整的Agent思维链

---

## 📝 总结

| 项 | 状态 |
|----|------|
| ToolRegistry工具注册器 | ✅ 完成 |
| 三个AI Skills注册 | ✅ 完成 |
| Function Calling提示词 | ✅ 完成 |
| 手动工具调用演示API | ✅ 完成 |
| 不再与AI Skills概念混淆 | ✅ 完成 |
| 真实LLM Function Calling | 需要LLM API Key |

**项目现在具备真正的AI Skills（Function Calling）基础架构！**
