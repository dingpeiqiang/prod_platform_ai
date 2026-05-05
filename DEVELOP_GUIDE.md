# AI prod_platform_ai 开发规范

## 1. 项目定位

**AI 驱动的智能表单平台** —— 用户通过自然语言对话生成和填写表单，系统自动识别意图、提取字段、提供智能推荐。

```
用户: "帮我填一个请假申请"  →  系统自动生成请假表单
用户: "导出一月份的销售订单"  →  系统查询并导出数据
```

## 2. 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                    前端 (Vue 3)                         │
│  App.vue → ChatAssistant → ChatWindow → FormPanel      │
│  intent-panels/  (意图面板组件)                         │
└───────────────────────┬─────────────────────────────────┘
                        │ SSE 流式通信
┌───────────────────────▼─────────────────────────────────┐
│                    后端 (FastAPI)                       │
│  api/chat.py → IntentRegistry → IntentHandler          │
│  services/ (业务逻辑)                                   │
│  intent/handlers/ (意图处理器)                          │
└─────────────────────────────────────────────────────────┘
```

## 3. 核心概念

### 3.1 Intent（意图）

用户消息被 LLM 分类为不同意图类型：

| Intent Type | Handler | 功能 |
|-------------|---------|------|
| `form` | FormHandler | 生成表单 |
| `form_update` | FormUpdateHandler | 更新表单字段 |
| `delete_form` | DeleteFormHandler | 删除表单 |
| `manage_history` | ManageHistoryHandler | 历史数据维护 |
| `chat` | ChatHandler | 纯聊天 |
| `configure` | ConfigureHandler | 新业务配置 |

### 3.2 IntentContext（上下文数据袋）

替代原来的十余个局部变量，统一传递数据：

```python
@dataclass
class IntentContext:
    intent_data: Dict[str, Any]   # LLM 解析的意图数据
    intent_type: str              # 意图类型
    confidence: float             # 置信度
    ontologies: Dict              # 本体定义
    request: ChatRequest          # 请求对象
    db: Session                   # 数据库会话
    # ... 其他字段
```

### 3.3 SSE 事件

后端通过 SSE 流式返回，前端根据事件类型分发处理：

```python
yield thinking("处理中...")           # 显示思考过程
yield sse({"type": "result", ...})   # 业务数据
yield sse({"type": "done", ...})     # 流结束标记
```

## 4. 开发流程

### 4.1 新增表单类型

**步骤 1**: 创建本体定义
```bash
# backend/config/ontologies/{form_code}.json
{
  "formCode": "expense",
  "formName": "费用报销",
  "entities": [{
    "entityName": "报销信息",
    "fields": [
      {"fieldCode": "amount", "fieldName": "金额", "fieldType": "number", "required": true},
      {"fieldCode": "type", "fieldName": "类型", "fieldType": "select", "options": [...]}
    ]
  }]
}
```

**步骤 2**: 注册场景关键词
```json
// backend/config/scenes/scene_mapping.json
{
  "sceneCode": "expense",
  "keywords": ["报销", "费用"],
  "formCode": "expense"
}
```

**步骤 3**: 重启后端（配置热重载）

### 4.2 新增 Intent Handler

**步骤 1**: 创建 Handler 类
```python
# backend/app/intent/handlers/my_handler.py
from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse

class MyHandler(BaseIntentHandler):
    intent_type = "my_intent"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        yield thinking("开始处理...")
        # 业务逻辑
        yield sse({"type": "result", "content": {...}})
        yield sse({"type": "done", "isForm": False, "intentType": "my_intent"})
```

**步骤 2**: 注册 Handler
```python
# backend/app/intent/handlers/__init__.py
from .my_handler import MyHandler
__all__ = [..., "MyHandler"]

# backend/app/intent/__init__.py (在适当位置)
from .handlers import MyHandler
```

**步骤 3**: 前端注册事件处理（可选，需要 UI 响应时）
```javascript
// frontend/src/components/intent-panels/intent-registry.js
import MyPanel from './MyPanel.vue'

registerEventHandler('my_intent', (data, msg) => {
  // 处理逻辑
}, { panel: MyPanel })
```

### 4.3 新增前端 Intent Panel

当特定意图需要在对话区展示专用 UI 时：

**步骤 1**: 创建 Panel 组件
```vue
<!-- frontend/src/components/intent-panels/MyPanel.vue -->
<template>
  <div class="my-panel">
    <!-- 展示 intentData 数据 -->
  </div>
</template>
```

**步骤 2**: 注册到 intent-registry.js
```javascript
import MyPanel from './MyPanel.vue'
registerEventHandler('my_intent', handler, { panel: MyPanel })
```

## 5. 目录结构

```
backend/
├── app/
│   ├── api/              # API 路由
│   │   ├── chat.py       # 核心对话 API (SSE 流)
│   │   ├── form.py       # 表单 API
│   │   └── config.py     # 配置 API
│   │
│   ├── intent/           # 意图识别系统
│   │   ├── base.py       # BaseIntentHandler + IntentContext
│   │   ├── registry.py   # IntentHandlerRegistry (单例)
│   │   ├── utils.py      # thinking(), sse() 等工具
│   │   └── handlers/     # 意图处理器
│   │       ├── form_handler.py
│   │       ├── manage_history_handler.py
│   │       └── ...
│   │
│   ├── services/         # 业务服务
│   │   ├── llm_service.py
│   │   ├── history_service.py
│   │   └── ...
│   │
│   ├── models/           # 数据模型
│   ├── core/             # 核心工具
│   └── mcp_tools/        # MCP 工具中心
│
└── config/
    ├── ontologies/       # 表单本体定义 (JSON)
    ├── scenes/           # 场景关键词映射
    └── prompts/          # LLM Prompt 模板

frontend/
└── src/
    ├── App.vue           # 根组件 (会话管理)
    └── components/
        ├── ChatWindow.vue    # 对话核心 (SSE + Intent 分发)
        ├── FormPanel.vue     # 表单容器
        ├── DynamicForm.vue   # 动态表单渲染
        └── intent-panels/    # 意图专用面板
            ├── intent-registry.js  # 事件处理器注册器
            ├── HistoryPanel.vue
            └── DeleteResultPanel.vue
```

## 6. API 设计规范

### 6.1 流式对话 API

```bash
POST /api/v1/chat/stream
Content-Type: application/json

{"messages": [{"role": "user", "content": "帮我填请假申请"}]}
```

返回 SSE 流，包含：
- `thinking` - 处理步骤
- `result` - 业务结果
- `done` - 流结束

### 6.2 REST API

```bash
POST /api/v1/form/generate   # 生成表单
POST /api/v1/form/submit     # 提交表单
GET  /api/v1/config/ontologies  # 获取本体列表
```

### 6.3 API 响应格式

```python
class SuccessResponse(BaseModel):
    success: bool = True
    data: Any = None

class ErrorResponse(BaseModel):
    success: bool = False
    message: str
    code: Optional[str] = None
```

## 7. 前端组件规范

### 7.1 状态管理

使用 **ref + localStorage**，无 Vuex/Pinia：

```javascript
// 状态定义
const messages = ref([])
const currentFormId = ref('')

// 持久化
const saveMessages = () => {
  localStorage.setItem(`chat_session_${sessionId}`, JSON.stringify(messages.value))
}

// 加载
const loadMessages = () => {
  const raw = localStorage.getItem(`chat_session_${sessionId}`)
  if (raw) messages.value = JSON.parse(raw)
}
```

### 7.2 组件通信

- **父子通信**: Props + Emits
- **跨组件通信**: 状态提升到父组件
- **Intent 事件**: 通过 intent-registry.js 分发

### 7.3 样式规范

```css
/* 组件样式使用 scoped */
<style scoped>
.my-component { ... }
</style>

/* 通用样式放 App.vue 或单独 CSS 文件 */
```

## 8. 后端开发规范

### 8.1 Handler 规范

```python
class MyHandler(BaseIntentHandler):
    intent_type = "my_intent"  # 必须设置

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        # 1. 提取参数
        param = ctx.intent_data.get("param")

        # 2. 发送处理步骤
        yield thinking("正在处理...")

        # 3. 执行业务逻辑（同步用 run_in_executor）
        loop = asyncio.get_event_loop()
        result = await loop.run_in_executor(None, lambda: do_sync_work())

        # 4. 返回结果
        yield sse({"type": "result", "content": result})
        yield sse({"type": "done", "isForm": False, "intentType": "my_intent"})
```

### 8.2 Service 规范

```python
class MyService:
    @classmethod
    def do_something(cls, param: str, db: Session = None) -> Dict[str, Any]:
        try:
            # 业务逻辑
            return {"success": True, "data": ...}
        except Exception as e:
            logger.exception("[MyService] 操作失败: %s", e)
            return {"success": False, "message": str(e)}
        finally:
            if db:
                db.close()
```

### 8.3 日志规范

```python
logger = logging.getLogger("module.name")

logger.info("[操作] 描述 context", extra={"key": "value"})
logger.warning("[操作] 警告信息")
logger.exception("[操作] 异常信息")
```

## 9. 配置驱动开发

### 9.1 本体配置

```json
// backend/config/ontologies/{form_code}.json
{
  "formCode": "string",
  "formName": "string",
  "entities": [{
    "entityName": "string",
    "fields": [{
      "fieldCode": "string",
      "fieldName": "string",
      "fieldType": "input|select|date|...",
      "required": boolean,
      "options": [],
      "defaultValue": "any"
    }]
  }]
}
```

### 9.2 Prompt 配置

```bash
# backend/config/prompts/{prompt_name}.txt
# 支持变量占位符 {{variable}}
```

### 9.3 场景映射

```json
// backend/config/scenes/scene_mapping.json
{
  "sceneCode": "expense",
  "keywords": ["报销", "费用报销"],
  "formCode": "expense",
  "priority": 10
}
```

## 10. 测试规范

### 10.1 后端测试

```bash
# 运行所有测试
pytest backend/

# 运行特定测试
pytest backend/app/services/test_recommendation.py -v
```

### 10.2 API 测试

```bash
# 健康检查
curl http://localhost:6173/health

# 流式对话
curl -X POST http://localhost:6173/api/v1/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"role": "user", "content": "hello"}]}'
```

## 11. 启动开发

```bash
# 后端
cd backend
start-backend.bat
# 或: python -m uvicorn app.main:app --reload

# 前端
cd frontend
start-frontend.bat
# 或: npm run dev

# 访问
# 前端: http://localhost:5173
# 后端: http://localhost:6173
# API文档: http://localhost:6173/docs
```

## 12. 注意事项

1. **Intent 是核心** —— 所有功能通过 Intent 机制触发
2. **配置优于代码** —— 新增表单只需配置 JSON，无需写代码
3. **SSE 通信** —— 前端后端通过 SSE 事件流通信
4. **Handler 注册** —— 新 Handler 需要在 `__init__.py` 中导出
5. **IntentContext** —— 所有数据通过 IntentContext 传递，不要用全局变量
6. **数据库会话** —— 操作完成后记得关闭 db.close()