# MCP Tools 使用指南

## 概述

MCP（Model Context Protocol）工具集为项目提供标准化的工具注册、发现和调用机制。

## 目录结构

```
backend/app/mcp_tools/
├── __init__.py          # 模块入口，自动注册所有工具
├── tool_def.py          # MCP Tool 基类定义
├── tool_hub.py          # 工具注册与调度中心
├── form_tools.py        # 表单相关工具
├── kb_tools.py          # 知识库工具
├── llm_tools.py         # LLM 工具
└── system_tools.py      # 系统工具
```

## 快速开始

### 1. 工具自动加载

应用启动时，所有 MCP 工具会自动注册：

```python
from app.mcp_tools import register_all_tools
register_all_tools()  # 自动调用各子模块的 @mcptool 装饰器
```

### 2. 装饰器注册

使用 `@mcptool` 装饰器注册工具：

```python
from app.mcp_tools import mcptool

@mcptool(
    name="my_tool",
    description="这是我的工具",
    category="custom"
)
def my_tool(param1: str, param2: int):
    """工具描述（会作为 description）"""
    return {"result": f"{param1}: {param2}"}
```

### 3. 查询工具

```bash
# 查看所有工具
GET /api/v1/mcp/tools

# 查看特定工具详情
GET /api/v1/mcp/tools/form_generate

# 获取工具 schema（用于 LLM）
GET /api/v1/mcp/tools/schemas
```

### 4. 调用工具

```bash
POST /api/v1/mcp/tools/call
{
    "tool_name": "form_generate",
    "arguments": {
        "user_input": "帮我填一个请假申请"
    }
}
```

## 可用工具

### 表单工具 (category: form)

| 工具名称 | 说明 | 参数 |
|---------|------|------|
| `form_generate` | 生成表单 | user_input, form_code, extracted_fields |
| `scene_recognize` | 识别场景 | user_input |
| `field_extract` | 提取字段 | user_input, form_code, schema |
| `form_validate` | 校验表单 | form_code, form_data |
| `form_submit` | 提交表单 | form_instance_id, form_data |
| `form_list_templates` | 获取表单列表 | 无 |

### 知识库工具 (category: kb)

| 工具名称 | 说明 | 参数 |
|---------|------|------|
| `kb_qa` | 知识库问答 | question, top_k |
| `kb_search` | 知识库检索 | query, top_k |
| `kb_status` | 检查知识库状态 | 无 |

### LLM 工具 (category: llm)

| 工具名称 | 说明 | 参数 |
|---------|------|------|
| `llm_chat` | 通用对话 | prompt, system_prompt, temperature |
| `llm_json` | 生成 JSON | prompt, json_schema, temperature |
| `llm_batch` | 批量处理 | items, prompt_template, temperature |

### 系统工具 (category: system)

| 工具名称 | 说明 | 参数 |
|---------|------|------|
| `list_mcp_tools` | 列出所有工具 | 无 |
| `execute_tool` | 执行指定工具 | tool_name, arguments |
| `system_status` | 系统状态 | 无 |
| `configure_knowledge_base` | 配置知识库 | api_url, api_key, model |
| `get_help` | 获取帮助 | topic |

## AgentExecutor 集成

AgentExecutor 自动使用 MCP ToolHub：

```python
from app.services.agent_executor import AgentExecutor

# AgentExecutor.execute 会自动发现并使用 MCP 工具
result = AgentExecutor.execute("帮我填一个请假申请")
```

## MCP API

### 列表工具

```bash
GET /api/v1/mcp/tools
```

响应示例：
```json
{
    "success": true,
    "tools": [
        {
            "name": "form_generate",
            "description": "根据用户需求生成表单",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "user_input": {"type": "string"}
                },
                "required": ["user_input"]
            }
        }
    ],
    "categories": ["form", "kb", "llm", "system"]
}
```

### 调用工具

```bash
POST /api/v1/mcp/tools/call
{
    "tool_name": "kb_qa",
    "arguments": {
        "question": "公司的年假政策是什么？"
    }
}
```

## 配置知识库

```bash
POST /api/v1/mcp/tools/call
{
    "tool_name": "configure_knowledge_base",
    "arguments": {
        "api_url": "https://your-kb-api.com",
        "api_key": "your-api-key"
    }
}
```

## 添加新工具

1. 创建工具文件或添加到现有文件：

```python
# backend/app/mcp_tools/my_tools.py
from .tool_hub import mcptool

@mcptool(
    name="my_custom_tool",
    description="自定义工具描述",
    category="custom"
)
def my_custom_tool(param1: str) -> dict:
    """工具实现"""
    return {"result": param1}
```

2. 在 `__init__.py` 中导入：

```python
# backend/app/mcp_tools/__init__.py
def register_all_tools():
    from . import form_tools
    from . import kb_tools
    from . import llm_tools
    from . import system_tools
    from . import my_tools  # 添加这行
    ...
```

工具会自动注册，无需手动调用。

## 最佳实践

1. **工具命名**：使用小写下划线，如 `form_generate`
2. **描述清晰**：description 应让 LLM 理解何时调用
3. **参数校验**：使用 type hint 帮助生成 schema
4. **错误处理**：工具应返回 `{"success": bool, "result"/"error": any}`
5. **分类组织**：按功能分类（form/kb/llm/system）

## 故障排查

### 工具未注册

检查：
1. 文件是否被 `__init__.py` 导入
2. 装饰器是否正确使用
3. 启动日志是否有注册信息

### 调用失败

```bash
# 检查工具是否存在
GET /api/v1/mcp/tools/{tool_name}

# 检查系统状态
GET /api/v1/mcp/status
```

## 更新日志

- 2026-04-23: 初始版本，实现基础 MCP 框架
