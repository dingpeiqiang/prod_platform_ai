# MCP 外部 API 工具配置指南

## 📋 概述

MCP 平台支持两种工具注册方式：

1. **内部工具**：通过 Python 代码和 `@mcptool` 装饰器注册
2. **外部 API 工具**：通过数据库配置，无需修改代码

本文档重点介绍**外部 API 工具**的配置和使用。

---

## 🔧 外部 API 工具配置结构

### 基本字段

```json
{
  "tool_name": "unique_tool_name",
  "tool_code": "optional_code",
  "description": "工具描述",
  "category": "external",
  "is_enabled": true,
  "is_public": true,
  "input_schema": {...},
  "config": {...},
  "output_mapping": {...}
}
```

### config 字段详解

```json
{
  "method": "GET",                    // HTTP 方法：GET/POST/PUT/DELETE/PATCH
  "url": "https://api.example.com/v1/endpoint",
  "headers": {
    "Authorization": "Bearer {{API_KEY}}",
    "Content-Type": "application/json"
  },
  "params": {
    "page": "{{page}}",
    "limit": "{{limit}}"
  },
  "body": {                           // POST/PUT 时的请求体（可选）
    "query": "{{search_query}}"
  },
  "timeout_seconds": 30,              // 超时时间（秒）
  "retry_count": 3                    // 重试次数
}
```

### output_mapping 字段

用于映射 API 响应到标准输出格式：

```json
{
  "temperature": "$.current.temp",
  "humidity": "$.current.humidity",
  "city_name": "$.location.city"
}
```

**支持的语法：**
- `$.field` - 顶层字段
- `$.nested.field` - 嵌套字段
- 如果未配置 mapping，返回原始响应

---

## 📝 配置示例

### 示例 1: 天气查询 API

```json
{
  "tool_name": "get_weather",
  "description": "查询指定城市的当前天气",
  "category": "external",
  "input_schema": {
    "type": "object",
    "properties": {
      "city": {
        "type": "string",
        "description": "城市名称，如：北京、上海"
      }
    },
    "required": ["city"]
  },
  "config": {
    "method": "GET",
    "url": "https://api.weatherapi.com/v1/current.json",
    "params": {
      "key": "{{WEATHER_API_KEY}}",
      "q": "{{city}}",
      "aqi": "no"
    },
    "timeout_seconds": 10,
    "retry_count": 2
  },
  "output_mapping": {
    "temperature_c": "$.current.temp_c",
    "condition": "$.current.condition.text",
    "humidity": "$.current.humidity",
    "wind_kph": "$.current.wind_kph"
  }
}
```

**使用方式：**
```python
result = hub.execute_sync("get_weather", {"city": "北京"})
# 返回: {"temperature_c": 25, "condition": "晴", "humidity": 60, ...}
```

---

### 示例 2: POST 请求 - 用户创建

```json
{
  "tool_name": "create_user",
  "description": "创建新用户",
  "category": "external",
  "input_schema": {
    "type": "object",
    "properties": {
      "username": {"type": "string"},
      "email": {"type": "string", "format": "email"},
      "password": {"type": "string"}
    },
    "required": ["username", "email", "password"]
  },
  "config": {
    "method": "POST",
    "url": "https://api.example.com/users",
    "headers": {
      "Authorization": "Bearer {{ADMIN_TOKEN}}",
      "Content-Type": "application/json"
    },
    "body": {
      "username": "{{username}}",
      "email": "{{email}}",
      "password": "{{password}}"
    },
    "timeout_seconds": 15,
    "retry_count": 1
  },
  "output_mapping": {
    "user_id": "$.id",
    "created_at": "$.created_at"
  }
}
```

---

### 示例 3: 带认证的 GraphQL API

```json
{
  "tool_name": "query_graphql",
  "description": "执行 GraphQL 查询",
  "category": "external",
  "input_schema": {
    "type": "object",
    "properties": {
      "query": {"type": "string"},
      "variables": {"type": "object"}
    },
    "required": ["query"]
  },
  "config": {
    "method": "POST",
    "url": "https://api.graphql.example.com/graphql",
    "headers": {
      "Authorization": "Bearer {{GRAPHQL_TOKEN}}",
      "Content-Type": "application/json"
    },
    "body": {
      "query": "{{query}}",
      "variables": "{{variables}}"
    },
    "timeout_seconds": 20,
    "retry_count": 0
  }
}
```

---

## 🚀 如何添加工具

### 方法 1: 直接插入数据库

```sql
INSERT INTO mcp_tool_definitions (
    tool_name, description, category, is_enabled, is_public,
    input_schema, config, output_mapping
) VALUES (
    'get_weather',
    '查询天气信息',
    'external',
    TRUE,
    TRUE,
    '{"type": "object", "properties": {"city": {"type": "string"}}, "required": ["city"]}',
    '{"method": "GET", "url": "https://api.weatherapi.com/v1/current.json", "params": {"key": "YOUR_KEY", "q": "{{city}}"}, "timeout_seconds": 10}',
    '{"temperature": "$.current.temp_c", "condition": "$.current.condition.text"}'
);
```

### 方法 2: 使用管理 API（推荐）

```python
# 后端 API（待实现）
POST /api/v1/mcp-management/tools
Content-Type: application/json

{
  "tool_name": "get_weather",
  "description": "查询天气",
  "category": "external",
  "input_schema": {...},
  "config": {...},
  "output_mapping": {...}
}
```

### 方法 3: 使用 ToolRegistryManager

```python
from app.core.database import SessionLocal
from app.mcp_tools.tool_registry_manager import ToolRegistryManager

db = SessionLocal()
manager = ToolRegistryManager(db)

tool_data = {
    "tool_name": "get_weather",
    "description": "查询天气",
    "category": "external",
    "input_schema": {...},
    "config": {...},
    "output_mapping": {...}
}

success = manager.create_external_tool(tool_data)
db.close()
```

---

## 🔐 敏感信息管理

### 环境变量引用

在配置中使用 `{{ENV_VAR_NAME}}` 引用环境变量：

```json
{
  "config": {
    "headers": {
      "Authorization": "Bearer {{API_KEY}}"
    }
  }
}
```

**设置环境变量：**
```bash
# .env 文件
API_KEY=your_secret_key_here
```

### 支持的变量

- `{{API_KEY}}` - API 密钥
- `{{TOKEN}}` - 访问令牌
- `{{SECRET}}` - 其他敏感信息

---

## 📊 模板语法（Jinja2）

配置中的字符串值支持 Jinja2 模板语法：

### 基本变量替换

```json
{
  "params": {
    "city": "{{city}}",
    "count": "{{count | default(10)}}"
  }
}
```

### 条件判断

```json
{
  "params": {
    "unit": "{% if use_fahrenheit %}f{% else %}c{% endif %}"
  }
}
```

### 循环（较少用）

```json
{
  "body": {
    "tags": [{% for tag in tags %}"{{tag}}"{% if not loop.last %},{% endif %}{% endfor %}]
  }
}
```

---

## 🧪 测试工具

### 1. 使用在线测试界面

访问 MCP 管理平台 → Tab 2: 在线测试

1. 选择工具
2. 输入参数
3. 点击"执行测试"
4. 查看结果

### 2. 使用 curl 测试

```bash
curl -X POST http://localhost:8000/api/v1/mcp-management/tools/get_weather/test \
  -H "Content-Type: application/json" \
  -d '{"city": "北京"}'
```

### 3. Python 脚本测试

```python
import requests

response = requests.post(
    "http://localhost:8000/api/v1/mcp-management/tools/get_weather/test",
    json={"city": "北京"}
)

print(response.json())
```

---

## ⚙️ 高级配置

### 超时和重试

```json
{
  "config": {
    "timeout_seconds": 30,    // 单次请求超时
    "retry_count": 3          // 失败后重试次数
  }
}
```

**重试策略：**
- 指数退避：第 1 次重试等待 2s，第 2 次 4s，第 3 次 8s
- 仅在网络错误时重试（HTTP 5xx 不重试）

### 自定义 Headers

```json
{
  "config": {
    "headers": {
      "X-Custom-Header": "{{custom_value}}",
      "Accept": "application/json"
    }
  }
}
```

### 复杂 Body

```json
{
  "config": {
    "body": {
      "data": {
        "items": "{{items}}",
        "metadata": {
          "source": "mcp_platform",
          "timestamp": "{{timestamp}}"
        }
      }
    }
  }
}
```

---

## 🐛 常见问题

### Q1: 工具注册后不生效？

**A:** 检查以下几点：
1. `is_enabled` 是否为 `true`
2. 重启后端服务（或调用同步 API）
3. 查看日志：`[MCP] 已注册 X 个外部 API 工具`

### Q2: 模板变量未替换？

**A:** 
- 确保变量名与输入参数一致
- 检查 Jinja2 语法是否正确
- 查看日志中的模板渲染错误

### Q3: API 返回 401/403？

**A:** 
- 检查认证 token 是否正确
- 确认环境变量已设置
- 验证 headers 配置

### Q4: 响应映射失败？

**A:** 
- 检查 JSONPath 是否正确
- 确认 API 返回结构与预期一致
- 使用 `$` 表示根节点

---

## 📈 监控和统计

外部 API 工具的调用会自动记录到：

1. **mcp_call_logs** - 每次调用的详细信息
2. **mcp_tool_stats** - 聚合统计数据
3. **MCP 管理平台** - 可视化展示

**查看指标：**
- 总调用次数
- 成功率
- 平均响应时间
- 错误分布

---

## ✨ 最佳实践

1. **合理设置超时**：外部 API 通常 5-30 秒
2. **启用重试**：网络不稳定时自动重试
3. **使用环境变量**：不要硬编码密钥
4. **完善 Schema**：帮助 LLM 理解参数
5. **添加描述**：清晰的描述提高调用准确率
6. **监控性能**：定期检查响应时间和成功率
7. **版本管理**：API 变更时更新配置并测试

---

## 🎯 总结

外部 API 工具配置化方案的优势：

✅ **无需修改代码** - 配置即可集成新 API  
✅ **动态更新** - 修改配置后立即生效  
✅ **统一管理** - 所有工具在一个平台管理  
✅ **类型安全** - Schema 保证参数正确性  
✅ **灵活扩展** - 支持任意 HTTP API  

现在您可以轻松集成任何第三方 API 到 MCP 平台！🚀
