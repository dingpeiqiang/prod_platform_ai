# MCP 数据库表完整说明

## 📊 数据库表总览

MCP 管理系统使用 **3 张核心表**来持久化存储工具定义、调用日志和统计数据：

| 表名 | 用途 | 记录数预估 |
|------|------|-----------|
| `mcp_tool_definitions` | 工具定义和配置 | ~50-200 条（工具数量） |
| `mcp_call_logs` | 每次调用的详细日志 | ~10万-100万条/月 |
| `mcp_tool_stats` | 聚合统计数据 | ~几千条（按小时/天汇总） |

---

## 1️⃣ mcp_tool_definitions - 工具定义表

### 用途
存储 MCP 工具的元数据、配置信息和启用状态。这是**工具的管理中心**。

### 表结构

| 字段 | 类型 | 说明 | 索引 |
|------|------|------|------|
| **id** | INTEGER | 主键，自增 | PK |
| **tool_name** | VARCHAR(100) | 工具名称（唯一） | UNIQUE, INDEX |
| **tool_code** | VARCHAR(100) | 工具编码（可选，唯一） | UNIQUE, INDEX |
| **description** | TEXT | 工具描述 | - |
| **category** | VARCHAR(50) | 工具分类 | INDEX |
| **is_enabled** | BOOLEAN | 是否启用 | INDEX |
| **is_public** | BOOLEAN | 是否公开（对工作流可见） | - |
| **input_schema** | JSON | 输入参数 Schema | - |
| **output_schema** | JSON | 输出结果 Schema | - |
| **config** | JSON | 工具配置（超时、重试等） | - |
| **metadata** | JSON | 扩展元数据 | - |
| **total_calls** | INTEGER | 总调用次数（冗余） | - |
| **last_called_at** | DATETIME | 最后调用时间 | - |
| **created_at** | DATETIME | 创建时间 | - |
| **updated_at** | DATETIME | 更新时间 | - |
| **created_by** | VARCHAR(100) | 创建者 | - |
| **updated_by** | VARCHAR(100) | 更新者 | - |

### 索引
- `idx_tool_name`: 按工具名称查询
- `idx_tool_code`: 按工具编码查询
- `idx_tool_category`: 按分类过滤
- `idx_tool_enabled`: 快速筛选启用的工具
- `tool_name`: 唯一约束
- `tool_code`: 唯一约束

### 使用场景

#### 1. 查询所有启用的工具
```python
enabled_tools = db.query(MCPToolDefinition).filter(
    MCPToolDefinition.is_enabled == True
).all()
```

#### 2. 按分类查询工具
```python
form_tools = db.query(MCPToolDefinition).filter(
    MCPToolDefinition.category == "form",
    MCPToolDefinition.is_enabled == True
).all()
```

#### 3. 禁用/启用工具
```python
tool = db.query(MCPToolDefinition).filter(
    MCPToolDefinition.tool_name == "query_form_template"
).first()

tool.is_enabled = False  # 禁用
db.commit()
```

#### 4. 更新工具配置
```python
tool.config = {
    "timeout_seconds": 30,
    "max_retries": 3,
    "cache_ttl": 300
}
db.commit()
```

---

## 2️⃣ mcp_call_logs - 调用日志表

### 用途
记录**每次**工具调用的详细信息，用于审计、调试和性能分析。

### 表结构

| 字段 | 类型 | 说明 | 索引 |
|------|------|------|------|
| **id** | INTEGER | 主键，自增 | PK |
| **tool_name** | VARCHAR(100) | 工具名称 | INDEX |
| **tool_category** | VARCHAR(50) | 工具分类 | INDEX |
| **success** | BOOLEAN | 是否成功 | - |
| **execution_time_ms** | FLOAT | 执行耗时（毫秒） | - |
| **error_message** | TEXT | 错误信息 | - |
| **timestamp** | DATETIME | 调用时间 | INDEX |
| **request_args** | TEXT | 请求参数 JSON | - |
| **response_data** | TEXT | 响应数据 JSON | - |

### 索引
- `idx_tool_name`: 按工具查询
- `idx_timestamp`: 按时间范围查询
- `idx_tool_category`: 按分类统计
- `idx_tool_timestamp`: 组合索引（工具+时间）

### 使用场景

#### 1. 查询某工具的最近 100 次调用
```python
from datetime import datetime, timedelta

seven_days_ago = datetime.now() - timedelta(days=7)

logs = db.query(MCPCallLog).filter(
    MCPCallLog.tool_name == "query_form_template",
    MCPCallLog.timestamp >= seven_days_ago
).order_by(
    MCPCallLog.timestamp.desc()
).limit(100).all()
```

#### 2. 计算成功率
```python
total = db.query(MCPCallLog).filter(
    MCPCallLog.tool_name == tool_name,
    MCPCallLog.timestamp >= seven_days_ago
).count()

success = db.query(MCPCallLog).filter(
    MCPCallLog.tool_name == tool_name,
    MCPCallLog.timestamp >= seven_days_ago,
    MCPCallLog.success == True
).count()

success_rate = (success / total * 100) if total > 0 else 0
```

#### 3. 查询失败的调用
```python
failed_logs = db.query(MCPCallLog).filter(
    MCPCallLog.success == False,
    MCPCallLog.timestamp >= datetime.now() - timedelta(hours=24)
).order_by(MCPCallLog.timestamp.desc()).all()
```

#### 4. 性能分析 - 平均响应时间
```python
from sqlalchemy import func

avg_time = db.query(func.avg(MCPCallLog.execution_time_ms)).filter(
    MCPCallLog.tool_name == tool_name,
    MCPCallLog.timestamp >= seven_days_ago
).scalar()
```

---

## 3️⃣ mcp_tool_stats - 聚合统计表

### 用途
存储**每小时/每天**的汇总统计数据，用于快速展示统计图表，避免每次都从日志表聚合。

### 表结构

| 字段 | 类型 | 说明 | 索引 |
|------|------|------|------|
| **id** | INTEGER | 主键，自增 | PK |
| **tool_name** | VARCHAR(100) | 工具名称 | INDEX |
| **stat_date** | VARCHAR(20) | 统计日期 YYYY-MM-DD | INDEX |
| **stat_hour** | INTEGER | 统计小时 0-23（NULL=日统计） | - |
| **total_calls** | INTEGER | 总调用次数 | - |
| **success_calls** | INTEGER | 成功调用次数 | - |
| **failed_calls** | INTEGER | 失败调用次数 | - |
| **total_response_time_ms** | FLOAT | 总响应时间 | - |
| **avg_response_time_ms** | FLOAT | 平均响应时间 | - |
| **created_at** | DATETIME | 创建时间 | - |
| **updated_at** | DATETIME | 更新时间 | - |

### 索引
- `idx_stats_tool_name`: 按工具查询
- `idx_stats_date`: 按日期查询
- `idx_stats_tool_date_hour`: 组合唯一索引
- `uq_tool_date_hour`: 唯一约束（防止重复）

### 使用场景

#### 1. 查询某工具每天的调用趋势
```python
daily_stats = db.query(MCPToolStats).filter(
    MCPToolStats.tool_name == tool_name,
    MCPToolStats.stat_hour == None,  # 日统计
    MCPToolStats.stat_date >= '2026-05-01'
).order_by(MCPToolStats.stat_date).all()
```

#### 2. 查询某小时的详细统计
```python
hourly_stats = db.query(MCPToolStats).filter(
    MCPToolStats.tool_name == tool_name,
    MCPToolStats.stat_date == '2026-05-18',
    MCPToolStats.stat_hour == 14  # 14:00-15:00
).first()
```

---

## 🔗 表关系图

```
┌─────────────────────────┐
│ mcp_tool_definitions    │  ← 工具定义（静态）
│ - tool_name (PK)        │
│ - is_enabled            │
│ - input_schema          │
│ - config                │
└────────┬────────────────┘
         │ 1:N
         │
         ▼
┌─────────────────────────┐
│ mcp_call_logs           │  ← 调用日志（动态，高频写入）
│ - id (PK)               │
│ - tool_name (FK)        │
│ - success               │
│ - execution_time_ms     │
│ - timestamp             │
└────────┬────────────────┘
         │ 聚合
         │
         ▼
┌─────────────────────────┐
│ mcp_tool_stats          │  ← 聚合统计（定时任务更新）
│ - id (PK)               │
│ - tool_name (FK)        │
│ - stat_date             │
│ - stat_hour             │
│ - total_calls           │
│ - avg_response_time_ms  │
└─────────────────────────┘
```

---

## 🚀 典型业务流程

### 1. 工具调用流程

```
1. 用户/工作流调用工具
   ↓
2. 检查 mcp_tool_definitions.is_enabled
   ↓ (如果禁用则拒绝)
3. 执行工具逻辑
   ↓
4. 记录到 mcp_call_logs
   ↓
5. 更新 mcp_tool_definitions.total_calls
   ↓
6. （定时任务）聚合到 mcp_tool_stats
```

### 2. 查询工具列表流程

```
1. 从 mcp_tool_definitions 获取工具基本信息
   ↓
2. 从 mcp_call_logs 查询最近 7 天的调用统计
   ↓
3. 合并数据返回前端
```

### 3. 查看调用历史流程

```
1. 从 mcp_call_logs 查询指定工具的日志
   ↓
2. 支持按时间、成功/失败过滤
   ↓
3. 分页返回（默认 100 条）
```

---

## 📈 性能优化建议

### 1. 定期清理旧日志

```python
# 每天凌晨执行
from datetime import datetime, timedelta

thirty_days_ago = datetime.now() - timedelta(days=30)
deleted = db.query(MCPCallLog).filter(
    MCPCallLog.timestamp < thirty_days_ago
).delete()
db.commit()
print(f"Deleted {deleted} old log entries")
```

### 2. 启用聚合统计任务

```python
# 每小时运行一次
def aggregate_hourly_stats():
    last_hour = datetime.now().replace(minute=0, second=0, microsecond=0) - timedelta(hours=1)
    
    # 从 logs 聚合到 stats
    # ... (见前文代码示例)
```

### 3. 索引优化

当前索引已覆盖常见查询场景。如果数据量超过千万级：
- 考虑分区表（按月分区 `mcp_call_logs`）
- 添加覆盖索引
- 读写分离（统计查询走从库）

---

## 🔧 维护命令

### 查看表大小

```sql
SELECT 
    table_name,
    ROUND(((data_length + index_length) / 1024 / 1024), 2) AS size_mb
FROM information_schema.TABLES
WHERE table_schema = 'your_database'
AND table_name LIKE 'mcp_%';
```

### 清理测试数据

```python
# 删除测试工具的日志
db.query(MCPCallLog).filter(
    MCPCallLog.tool_name.like("test_%")
).delete()
db.commit()
```

### 重置工具统计

```python
# 重置某工具的调用计数
tool = db.query(MCPToolDefinition).filter(
    MCPToolDefinition.tool_name == "some_tool"
).first()
tool.total_calls = 0
db.commit()
```

---

## ✨ 总结

MCP 数据库设计遵循以下原则：

✅ **分离关注点**：定义、日志、统计各司其职  
✅ **性能优先**：完善的索引策略，支持高效查询  
✅ **可扩展性**：预留聚合表，支持未来优化  
✅ **易于维护**：清晰的表结构，便于理解和操作  

现在 MCP 工具管理平台拥有完整的持久化存储方案，可以支撑生产环境的大规模使用！🎉
