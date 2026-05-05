# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**AI驱动动态表单底层框架** - 基于FastAPI+Vue3的智能表单系统，支持自然语言表单生成、字段提取、双向可控通信和本体约束校验。

## 常用命令

### 后端 (Python/FastAPI)

```bash
cd backend

# 安装依赖
pip install -r requirements.txt

# 启动服务
python -m app.main

# 或使用uvicorn
uvicorn app.main:app --reload --host 0.0.0.0 --port 6173
```

### 前端 (Vue 3/Vite)

```bash
cd frontend

# 安装依赖
npm install

# 开发模式
npm run dev

# 构建生产版本
npm run build

# 预览构建结果
npm run preview
```

### 一键启动

```bash
start-all.bat    # 同时启动后端+前端
start-backend.bat # 仅后端
```

### 测试

```bash
# 后端测试
pytest backend/

# 特定测试
pytest backend/app/services/test_recommendation.py -v
```

## 架构概览

### 整体数据流

```
用户输入 → 前端ChatWindow → 后端 /api/v1/chat/stream
    ↓
意图识别 (LLM + SceneRecognition)
    ↓
┌─ 表单意图 → 字段提取 + RecommendationEngine → 返回Schema + 推荐值
└─ 聊天意图 → LLM回复
```

### 后端核心模块

| 目录 | 职责 |
|------|------|
| `app/api/` | API路由（chat, form, config, validation等） |
| `app/services/` | 业务逻辑（llm_service, form_service, recommendation_engine等） |
| `app/harness/` | AI Agent框架（guardrails护栏, entropy不确定性, multi-agent等） |
| `app/intent/` | 意图识别处理 |
| `config/ontologies/` | 表单Schema定义（JSON格式） |
| `config/prompts/` | LLM Prompt模板 |
| `config/scenes/` | 场景关键词配置 |

### 前端核心组件

| 组件 | 职责 |
|------|------|
| `ChatWindow.vue` | 聊天消息展示和输入 |
| `DynamicForm.vue` | 动态表单渲染 |
| `FormPanel.vue` | 表单面板容器 |
| `ConfigCard.vue` | 配置卡片 |

### 核心服务

- **llm_service.py**: 大模型调用封装（OpenAI兼容）
- **form_service.py**: 表单生成和提交
- **recommendation_engine.py**: 历史数据推荐（频率+用户+时间衰减）
- **history_service.py**: 历史记录管理

## 表单扩展

新增表单类型需要：

1. 创建 `config/ontologies/{form_code}.json` - 表单Schema定义
2. 更新 `config/scenes/scene_mapping.json` - 添加场景关键词
3. （可选）更新 `config/prompts/` - 更新Prompt模板
4. 重启服务

### Schema字段类型

`string` | `integer` | `number` | `boolean` | `date` | `datetime` | `email` | `phone` | `enum`

## 关键配置

| 配置 | 路径 |
|------|------|
| 系统配置 | `backend/config/system_config.json` |
| 应用配置 | `backend/config/app_config.json` |
| 场景映射 | `backend/config/scenes/scene_mapping.json` |
| LLM Prompt | `backend/config/prompts/smart_intent_recognition.txt` |

## API端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/chat/stream` | POST | 流式聊天（支持表单识别） |
| `/api/v1/form/generate` | POST | 生成表单 |
| `/api/v1/form/submit` | POST | 提交表单 |
| `/api/v1/history/getRecommendValues` | POST | 获取推荐值 |
| `/health` | GET | 健康检查 |
| `/docs` | GET | Swagger文档 |

## 数据库

- 默认使用SQLite（`form.db`）
- 可通过环境变量切换MySQL/PostgreSQL
- 表结构通过SQLAlchemy ORM定义

## 开发规范（强制）

### SSE 协议 v2.0（强制）

所有后端 Intent Handler 必须使用统一事件格式，不得混用旧格式。

```python
from app.intent.utils import intent_event, done_event

# ✅ 正确：统一 intent_event
yield intent_event("my_intent", "action_name", {...}, is_form=False)
yield done_event("my_intent", is_form=False, intent_data=ctx.intent_data)

# ❌ 错误：硬编码 sse({...})
yield sse({"type": "my_intent", "content": {...}})
```

通用事件（无需改动）：`thinking`, `reasoning`, `text_start`, `text`, `text_end`, `error`, `stats`

### Intent Handler 开发流程（强制）

1. 在 `backend/app/intent/handlers/` 创建 `xxx_handler.py`
2. 继承 `BaseIntentHandler`，实现 `intent_type` 和 `handle()` 方法
3. **必须**使用 `intent_event()` / `done_event()` 输出事件
4. 在 `intent_registry.py` 中注册

```python
# backend/app/intent/handlers/example_handler.py
from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse, intent_event, done_event
from typing import AsyncGenerator

class ExampleHandler(BaseIntentHandler):
    intent_type = "example"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        yield thinking("处理中...")
        # ... 业务逻辑 ...
        yield intent_event("example", "done", {"key": "value"}, is_form=False)
        yield done_event("example", is_form=False, intent_data=ctx.intent_data)
```

### 前端 Intent 注册（强制）

使用 `composables/useIntentRegistry.js`，不得直接操作 SSE switch/case：

```javascript
import { registerEventHandler, registerPostProcessor } from '../composables/useIntentRegistry.js'

registerEventHandler('my_intent', (data, msg) => {
  // data = intentEvent.data
  msg.myData = data
})

registerPostProcessor('my_intent', async (msg, intentData) => {
  // 流结束后处理
})
```

### 文件下载 / 大数据：必须走直接 API（强制）

文件类数据（导出、报告生成等）**禁止**通过 SSE 事件 body 传输。

正确方式：SSE 返回下载链接，前端直接请求 API 下载。

```python
# ✅ 正确：通过 intent_event 返回 downloadUrl
yield intent_event("manage_history", "export", {
    "downloadUrl": f"/api/v1/config/export/{formCode}?format=csv",
    "filename": "report.csv",
    "recordCount": 100
}, is_form=False)
```

```javascript
// ✅ 正确：前端调 API 下载
const resp = await fetch(data.downloadUrl)
const blob = await resp.blob()
// 触发下载...
```

### API 设计原则

- **文件类 API**：`GET /api/v1/xxx/export/{id}` 直接返回文件流（`Response(content=..., media_type=...)`）
- **数据类 API**：`POST /api/v1/xxx/action` 返回 JSON
- 不在 SSE body 中传输大文件内容

### 新增功能检查清单

每次实现新能力前，对照检查：
- [ ] 是否需要新增 Intent Handler？→ 放在 `app/intent/handlers/`
- [ ] 事件输出是否使用 `intent_event()` / `done_event()`？
- [ ] 文件/大数据是否走单独 API 端点？
- [ ] 前端是否通过 `useIntentRegistry.js` 注册？
- [ ] 是否需要更新 `config/scenes/scene_mapping.json` 添加场景关键词？

## 日志

- 应用日志: `backend/logs/app.log`
- 终端输出: DEBUG级别
- 第三方库: INFO级别（降低噪音）