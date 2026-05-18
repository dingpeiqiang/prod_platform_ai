# MCP 调用日志数据持久化实施说明

## ✅ 已完成功能

### 1. 数据库模型设计

创建了两个核心表用于持久化存储 MCP 工具调用数据：

#### **mcp_call_logs** - 调用日志表
记录每次工具调用的详细信息：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER | 主键，自增 |
| tool_name | VARCHAR(100) | 工具名称（索引） |
| tool_category | VARCHAR(50) | 工具分类（索引） |
| success | BOOLEAN | 是否成功 |
| execution_time_ms | FLOAT | 执行耗时（毫秒） |
| error_message | TEXT | 错误信息 |
| timestamp | DATETIME | 调用时间（索引） |
| request_args | TEXT | 请求参数 JSON |
| response_data | TEXT | 响应数据 JSON |

**索引优化：**
- `idx_tool_name`: 按工具名称查询
- `idx_timestamp`: 按时间范围查询
- `idx_tool_timestamp`: 组合索引，优化工具+时间查询

#### **mcp_tool_stats** - 聚合统计表
存储每小时/每天的汇总统计数据（预留，暂未启用自动聚合）：

| 字段 | 类型 | 说明 |
|------|------|------|
| tool_name | VARCHAR(100) | 工具名称 |
| stat_date | VARCHAR(20) | 统计日期 YYYY-MM-DD |
| stat_hour | INTEGER | 统计小时 0-23 |
| total_calls | INTEGER | 总调用次数 |
| success_calls | INTEGER | 成功调用次数 |
| failed_calls | INTEGER | 失败调用次数 |
| avg_response_time_ms | FLOAT | 平均响应时间 |

---

### 2. 后端 API 改造

#### 修改文件：`backend/app/api/mcp_management.py`

**主要变更：**

1. **移除内存存储**
   ```python
   # 删除了这些变量
   _call_stats: Dict[str, Dict[str, Any]] = {}
   _execution_logs: List[Dict[str, Any]] = []
   MAX_LOG_ENTRIES = 1000
   ```

2. **添加数据库依赖**
   ```python
   from sqlalchemy.orm import Session
   from app.core.database import get_db
   from app.models.mcp_call_log import MCPCallLog, MCPToolStats
   ```

3. **新增数据库记录函数**
   ```python
   def _record_call_to_db(
       db: Session,
       tool_name: str,
       success: bool,
       elapsed_ms: float,
       arguments: Dict[str, Any] = None,
       result: Dict[str, Any] = None,
       error: str = None
   ):
       """记录工具调用到数据库"""
       # 创建 MCPCallLog 记录并保存到 MySQL
   ```

4. **API 端点改造**
   - `GET /tools`: 从数据库查询最近 7 天的统计
   - `GET /stats`: 从数据库计算总体统计
   - `POST /tools/{tool_name}/test`: 测试后记录到数据库
   - `GET /logs`: 从数据库查询日志（支持分页和过滤）

---

### 3. 数据库迁移

#### 迁移文件：`backend/migrations/versions/add_mcp_call_logs.py`

**执行迁移：**
```bash
cd backend
alembic upgrade head
```

**验证表创建：**
```bash
python verify_mcp_tables.py
```

输出示例：
```
============================================================
mcp_call_logs 表结构:
============================================================
  - id                             INTEGER              nullable=False
  - tool_name                      VARCHAR(100)         nullable=False
  - tool_category                  VARCHAR(50)          nullable=True
  - success                        TINYINT              nullable=False
  - execution_time_ms              FLOAT                nullable=True
  - error_message                  TEXT                 nullable=True
  - timestamp                      DATETIME             nullable=True
  - request_args                   TEXT                 nullable=True
  - response_data                  TEXT                 nullable=True

索引 (4 个):
  - idx_timestamp: ['timestamp']
  - idx_tool_category: ['tool_category']
  - idx_tool_name: ['tool_name']
  - idx_tool_timestamp: ['tool_name', 'timestamp']
```

---

### 4. 功能测试

#### 测试脚本：`backend/test_mcp_persistence.py`

**运行测试：**
```bash
cd backend
python test_mcp_persistence.py
```

**测试结果：**
```
============================================================
测试结果总结
============================================================
插入日志                 ✓ 通过
查询统计                 ✓ 通过
真实调用                 ✓ 通过

============================================================
✓ 所有测试通过！数据持久化功能正常
============================================================
```

---

## 📊 数据查询示例

### 1. 查询某工具的最近调用

```python
from datetime import datetime, timedelta
from app.models.mcp_call_log import MCPCallLog

seven_days_ago = datetime.now() - timedelta(days=7)

logs = db.query(MCPCallLog).filter(
    MCPCallLog.tool_name == "query_form_template",
    MCPCallLog.timestamp >= seven_days_ago
).order_by(MCPCallLog.timestamp.desc()).limit(100).all()
```

### 2. 计算工具统计

```python
total_calls = db.query(MCPCallLog).filter(
    MCPCallLog.tool_name == tool_name,
    MCPCallLog.timestamp >= seven_days_ago
).count()

success_calls = db.query(MCPCallLog).filter(
    MCPCallLog.tool_name == tool_name,
    MCPCallLog.timestamp >= seven_days_ago,
    MCPCallLog.success == True
).count()

avg_time = db.query(MCPCallLog.execution_time_ms).filter(
    MCPCallLog.tool_name == tool_name,
    MCPCallLog.timestamp >= seven_days_ago
).all()

avg_response_time = sum(t for t, in avg_time if t) / len(avg_time) if avg_time else 0
```

### 3. 按分类统计

```python
from sqlalchemy import func

category_stats = db.query(
    MCPCallLog.tool_category,
    func.count(MCPCallLog.id).label('total'),
    func.sum(func.cast(MCPCallLog.success, Integer)).label('success')
).group_by(MCPCallLog.tool_category).all()
```

---

## 🔧 性能优化建议

### 1. 定期清理旧日志

创建定时任务清理超过 30 天的日志：

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

### 2. 启用聚合统计

每小时运行一次聚合任务，将详细日志汇总到 `mcp_tool_stats` 表：

```python
from sqlalchemy import func
from datetime import datetime, date

# 聚合上一小时的数据
last_hour = datetime.now().replace(minute=0, second=0, microsecond=0) - timedelta(hours=1)
hour_str = last_hour.strftime('%Y-%m-%d')
hour_num = last_hour.hour

# 插入或更新统计
stats = db.query(
    MCPCallLog.tool_name,
    func.count().label('total'),
    func.sum(func.cast(MCPCallLog.success, Integer)).label('success'),
    func.avg(MCPCallLog.execution_time_ms).label('avg_time')
).filter(
    MCPCallLog.timestamp >= last_hour,
    MCPCallLog.timestamp < last_hour + timedelta(hours=1)
).group_by(MCPCallLog.tool_name).all()

for tool_name, total, success, avg_time in stats:
    # Upsert 逻辑
    existing = db.query(MCPToolStats).filter(
        MCPToolStats.tool_name == tool_name,
        MCPToolStats.stat_date == hour_str,
        MCPToolStats.stat_hour == hour_num
    ).first()
    
    if existing:
        existing.total_calls = total
        existing.success_calls = success
        existing.failed_calls = total - success
        existing.avg_response_time_ms = avg_time
    else:
        new_stat = MCPToolStats(
            tool_name=tool_name,
            stat_date=hour_str,
            stat_hour=hour_num,
            total_calls=total,
            success_calls=success,
            failed_calls=total - success,
            avg_response_time_ms=avg_time
        )
        db.add(new_stat)

db.commit()
```

### 3. 索引优化

当前已创建的索引足够应对常见查询场景。如果数据量超过百万级，考虑：

- 分区表：按月分区 `mcp_call_logs`
- 覆盖索引：为常用查询创建覆盖索引
- 读写分离：统计查询走从库

---

## 🚀 下一步优化方向

### Phase 6: 高级功能

1. **数据导出**
   - 导出 CSV/Excel 格式
   - 支持自定义时间范围和过滤器

2. **实时告警**
   - 成功率低于阈值时告警
   - 响应时间超过阈值时告警

3. **可视化的增强**
   - ECharts 图表展示趋势
   - 热力图显示调用高峰时段

4. **工具启用/禁用管理**
   - UI 开关控制工具可用性
   - 工作流执行时检查工具状态

---

## 📝 注意事项

### 1. 数据存储策略

- **默认保留期限**：建议保留最近 30 天的详细日志
- **聚合数据**：长期保留聚合统计数据（可按年归档）
- **存储空间**：每条日志约 1-5KB，100万次调用约 1-5GB

### 2. 性能影响

- **写入性能**：每次工具调用增加一次数据库写入（~5-10ms）
- **查询性能**：使用索引后，常见查询 < 100ms
- **建议**：高频调用场景考虑异步写入或批量插入

### 3. 兼容性

- ✅ 向后兼容：不影响现有 MCP API
- ✅ 无破坏性变更：前端无需修改
- ✅ 平滑迁移：重启后端即可生效

---

## ✨ 总结

MCP 调用日志数据持久化已成功实施：

✅ **数据库表创建**：2 张表，包含完整索引  
✅ **API 改造完成**：所有接口改用数据库查询  
✅ **测试验证通过**：插入、查询、统计功能正常  
✅ **性能优化就绪**：索引完善，支持高效查询  

现在 MCP 工具调用数据将永久保存在 MySQL 数据库中，即使重启后端也不会丢失！🎉
