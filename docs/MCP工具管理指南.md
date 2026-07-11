# MCP 工具管理指南

> 本文档整合了 MCP（Model Context Protocol）工具集的使用、配置、管理和数据库设计相关内容。

---

## 目录

1. [概述](#1-概述)
2. [工具架构](#2-工具架构)
3. [工具使用指南](#3-工具使用指南)
4. [外部API工具配置](#4-外部api工具配置)
5. [外部工具参数配置](#5-外部工具参数配置)
6. [外部工具管理UI](#6-外部工具管理ui)
7. [数据库设计](#7-数据库设计)
8. [最佳实践](#8-最佳实践)

---

## 1. 概述

MCP（Model Context Protocol）工具集为项目提供标准化的工具注册、发现和调用机制。平台支持两种工具注册方式：

| 方式 | 说明 |
|------|------|
| **内部工具** | 通过 Python 代码和 `@mcptool` 装饰器注册 |
| **外部 API 工具** | 通过数据库配置，无需修改代码 |

---

## 2. 工具架构

### 目录结构

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

### 工具分类

| 分类 | 说明 | 示例工具 |
|------|------|---------|
| `form` | 表单相关 | recognize_scene, extract_fields |
| `validation` | 验证相关 | validate_field, validate_form |
| `system` | 系统相关 | get_status, health_check |
| `data` | 数据相关 | query_database |
| `file` | 文件相关 | upload_file |
| `external` | 外部调用 | call_api |

---

## 3. 工具使用指南

### 3.1 工具自动加载

应用启动时，所有 MCP 工具会自动注册：

```python
from app.mcp_tools import register_all_tools
register_all_tools()
```

### 3.2 装饰器注册

使用 `@mcptool` 装饰器注册工具：

```python
from app.mcp_tools import mcptool

@mcptool(
    name="my_tool",
    description="这是我的工具",
    category="custom"
)
def my_tool(param1: str, param2: int):
    return {"result": f"{param1}: {param2}"}
```

### 3.3 查询工具

```bash
GET /api/v1/mcp/tools
GET /api/v1/mcp/tools/form_generate
GET /api/v1/mcp/tools/schemas
```

### 3.4 调用工具

```bash
POST /api/v1/mcp/tools/call
{
    "tool_name": "form_generate",
    "arguments": {
        "user_input": "帮我填一个请假申请"
    }
}
```

### 3.5 可用工具列表

#### 表单工具 (category: form)

| 工具名称 | 说明 | 参数 |
|---------|------|------|
| `form_generate` | 生成表单 | user_input, form_code, extracted_fields |
| `scene_recognize` | 识别场景 | user_input |
| `field_extract` | 提取字段 | user_input, form_code, schema |
| `form_validate` | 校验表单 | form_code, form_data |
| `form_submit` | 提交表单 | form_instance_id, form_data |

#### 知识库工具 (category: kb)

| 工具名称 | 说明 | 参数 |
|---------|------|------|
| `kb_qa` | 知识库问答 | question, top_k |
| `kb_search` | 知识库检索 | query, top_k |
| `kb_status` | 检查知识库状态 | 无 |

#### LLM 工具 (category: llm)

| 工具名称 | 说明 | 参数 |
|---------|------|------|
| `llm_chat` | 通用对话 | prompt, system_prompt, temperature |
| `llm_json` | 生成 JSON | prompt, json_schema, temperature |

#### 工作流工具 (category: workflow)

| 工具名称 | 说明 | 参数 |
|---------|------|------|
| `execute_workflow` | 执行工作流 | workflow_code, inputs |
| `list_workflows` | 列出工作流 | category, active_only |

---

## 4. 外部 API 工具配置

### 4.1 配置结构

```json
{
  "tool_name": "unique_tool_name",
  "tool_code": "optional_code",
  "description": "工具描述",
  "category": "external",
  "is_enabled": true,
  "is_public": true,
  "input_schema": {...},
  "config": {
    "method": "GET",
    "url": "https://api.example.com/v1/endpoint",
    "headers": {"Authorization": "Bearer {{API_KEY}}"},
    "params": {"page": "{{page}}"},
    "body": {},
    "timeout_seconds": 30,
    "retry_count": 3
  },
  "output_mapping": {
    "temperature": "$.current.temp"
  }
}
```

### 4.2 配置示例

#### 天气查询 API

```json
{
  "tool_name": "get_weather",
  "description": "查询指定城市的当前天气",
  "category": "external",
  "input_schema": {
    "type": "object",
    "properties": {
      "city": {"type": "string", "description": "城市名称"}
    },
    "required": ["city"]
  },
  "config": {
    "method": "GET",
    "url": "https://api.weatherapi.com/v1/current.json",
    "params": {
      "key": "{{WEATHER_API_KEY}}",
      "q": "{{city}}"
    },
    "timeout_seconds": 10,
    "retry_count": 2
  },
  "output_mapping": {
    "temperature_c": "$.current.temp_c",
    "condition": "$.current.condition.text"
  }
}
```

### 4.3 敏感信息管理

使用环境变量引用：

```json
{
  "config": {
    "headers": {
      "Authorization": "Bearer {{API_KEY}}"
    }
  }
}
```

### 4.4 模板语法（Jinja2）

```json
{
  "params": {
    "city": "{{city}}",
    "count": "{{count | default(10)}}"
  }
}
```

---

## 5. 外部工具参数配置

### 5.1 参数类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `string` | 字符串 | 用户名 |
| `integer` | 整数 | 数量 |
| `number` | 数字 | 价格 |
| `boolean` | 布尔 | 是否启用 |
| `object` | 对象 | 复杂对象 |
| `array` | 数组 | 多个元素 |

### 5.2 数组类型配置

#### 基本数组

```json
{
  "tags": {
    "type": "array",
    "items": {"type": "string"}
  }
}
```

#### 对象数组

```json
{
  "orders": {
    "type": "array",
    "items": {
      "type": "object",
      "properties": {
        "order_id": {"type": "string"},
        "amount": {"type": "number"}
      }
    }
  }
}
```

### 5.3 配置检查清单

- [ ] 已选择正确的数据类型
- [ ] 数组参数已配置子项
- [ ] 必填属性已勾选
- [ ] 参数描述清晰明确

---

## 6. 外部工具管理 UI

### 6.1 功能特性

#### 工具列表管理
- 工具名称 + 启用/禁用标签
- 描述、分类、调用统计
- 搜索和过滤功能

#### 新建/编辑工具

**基本信息：**
- 工具名称（必填，唯一）
- 工具编码（可选）
- 描述、分类
- 是否启用

**API 配置：**
- 请求方法（GET/POST/PUT/DELETE/PATCH）
- URL、Headers、Params、Body
- 超时时间、重试次数

**输入 Schema：**
```json
{
  "type": "object",
  "properties": {
    "city": {"type": "string"}
  },
  "required": ["city"]
}
```

**输出映射：**
```json
{
  "temperature": "$.current.temp_c"
}
```

### 6.2 API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/mcp-management/external-tools` | GET | 获取列表 |
| `/api/v1/mcp-management/external-tools` | POST | 创建 |
| `/api/v1/mcp-management/external-tools/{name}` | PUT | 更新 |
| `/api/v1/mcp-management/external-tools/{name}` | DELETE | 删除 |
| `/api/v1/mcp-management/external-tools/{name}/toggle` | POST | 切换状态 |

### 6.3 数据流

```
用户操作 → 前端 API 调用 → 后端 HTTP 端点 → 数据库操作 → ToolHub 同步 → 返回结果 → 前端更新 UI
```

---

## 7. 数据库设计

### 7.1 核心表

| 表名 | 用途 | 记录数预估 |
|------|------|-----------|
| `mcp_tool_definitions` | 工具定义和配置 | ~50-200 条 |
| `mcp_call_logs` | 调用日志 | ~10万-100万条/月 |
| `mcp_tool_stats` | 聚合统计 | ~几千条 |

### 7.2 mcp_tool_definitions

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER | 主键 |
| `tool_name` | VARCHAR(100) | 工具名称（唯一） |
| `tool_code` | VARCHAR(100) | 工具编码（可选） |
| `description` | TEXT | 工具描述 |
| `category` | VARCHAR(50) | 工具分类 |
| `is_enabled` | BOOLEAN | 是否启用 |
| `input_schema` | JSON | 输入参数 Schema |
| `output_schema` | JSON | 输出结果 Schema |
| `config` | JSON | 工具配置 |
| `total_calls` | INTEGER | 总调用次数 |
| `created_at` | DATETIME | 创建时间 |

### 7.3 mcp_call_logs

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER | 主键 |
| `tool_name` | VARCHAR(100) | 工具名称 |
| `success` | BOOLEAN | 是否成功 |
| `execution_time_ms` | FLOAT | 执行耗时 |
| `error_message` | TEXT | 错误信息 |
| `timestamp` | DATETIME | 调用时间 |
| `request_args` | TEXT | 请求参数 |
| `response_data` | TEXT | 响应数据 |

### 7.4 mcp_tool_stats

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INTEGER | 主键 |
| `tool_name` | VARCHAR(100) | 工具名称 |
| `stat_date` | VARCHAR(20) | 统计日期 |
| `stat_hour` | INTEGER | 统计小时 |
| `total_calls` | INTEGER | 总调用次数 |
| `success_calls` | INTEGER | 成功次数 |
| `avg_response_time_ms` | FLOAT | 平均响应时间 |

### 7.5 表关系

```
mcp_tool_definitions (1:N) → mcp_call_logs (聚合) → mcp_tool_stats
```

---

## 8. 最佳实践

1. **工具命名**：使用小写下划线，如 `form_generate`
2. **描述清晰**：description 应让 LLM 理解何时调用
3. **参数校验**：使用 type hint 帮助生成 schema
4. **合理设置超时**：外部 API 通常 5-30 秒
5. **使用环境变量**：不要硬编码密钥
6. **监控性能**：定期检查响应时间和成功率
7. **定期清理日志**：删除30天前的旧日志

---

**最后更新**：2026-07-11  
**版本**：v1.0