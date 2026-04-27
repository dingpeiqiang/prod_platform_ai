# LLM聊天和意图识别功能说明

## 概述

聊天意图**完全由通用大模型接管**，同时支持规则降级（LLM不可用时）。

---

## 架构

```
用户输入
    ↓
POST /api/v1/chat
    ↓
LLM意图识别（优先）
    ↓
    ├─→ intentType: "form" → 生成表单
    │
    └─→ intentType: "chat" → LLM聊天回复
    ↓
规则降级（LLM不可用/失败）
    ↓
关键词识别 → 表单或规则回复
```

---

## API接口

### POST /api/v1/chat

**请求：**
```json
{
  "messages": [
    {"role": "user", "content": "你好"},
    {"role": "assistant", "content": "你好！有什么可以帮你？"},
    {"role": "user", "content": "帮我填一个销售订单"}
  ]
}
```

**响应（form意图）：**
```json
{
  "success": true,
  "intentType": "form",
  "formCode": "sales_order",
  "extractedFields": {
    "customer_name": "张三"
  }
}
```

**响应（chat意图）：**
```json
{
  "success": true,
  "intentType": "chat",
  "reply": "我是AI智能助手，我可以帮你...",
  "formCode": null,
  "extractedFields": null
}
```

---

## 降级机制

| LLM状态 | 处理方式 |
|---------|---------|
| ✅ 可用 | 完全由LLM识别和回复 |
| ❌ 不可用 | 关键词识别 + 规则回复 |
| ❌ 调用失败 | 关键词识别 + 规则回复 |

---

## 配置LLM

在 `config/app_config.json` 中：

```json
{
  "llm": {
    "enabled": true,
    "apiKey": "your-api-key",
    "baseUrl": "https://api.openai.com/v1",
    "model": "gpt-4",
    "temperature": 0.3,
    "maxTokens": 2048,
    "fallbackToRules": true
  }
}
```

或者在 `.env` 中配置：

```env
LLM_API_KEY=sk-xxxxx
LLM_BASE_URL=https://api.openai.com/v1
LLM_MODEL=gpt-4
```

---

## 使用示例

### 示例1：聊天对话

```
用户：你好
助手：你好！我是AI智能助手...

用户：你能做什么？
助手：我可以帮你：1. 生成和填写表单...
```

### 示例2：生成表单

```
用户：帮我填一个请假申请
助手：（意图识别为form）好的，我来帮你生成表单...
（显示请假表单）
```

### 示例3：带字段提取

```
用户：帮我填一个销售订单，客户姓名张三
助手：（提取到customer_name: "张三"）好的，我来帮你生成表单...
（显示销售订单表单，客户姓名字段已自动填充）
```

---

## 修改的文件

| 文件 | 说明 |
|------|------|
| `app/api/chat.py` | 聊天API接口（新建） |
| `app/main.py` | 注册chat路由 |
| `app/services/llm_service.py` | 添加 `_call_llm_sync` 方法 |
| `frontend/src/components/ChatAssistant.vue` | 更新调用新接口 |

---

## 验证结果

✅ 后端服务成功启动运行在 http://0.0.0.0:8000  
✅ LLM降级机制正常工作（无API Key时自动使用规则）  
✅ API接口完整可用
