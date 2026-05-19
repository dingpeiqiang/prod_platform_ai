# MCP 工具管理平台 - 快速测试指南

## ✅ 已完成功能

### 后端 API（Phase 1）
- ✅ `GET /api/v1/mcp-management/tools` - 获取工具列表（含统计信息）
- ✅ `GET /api/v1/mcp-management/stats` - 获取整体统计
- ✅ `POST /api/v1/mcp-management/tools/{tool_name}/test` - 测试工具执行
- ✅ `GET /api/v1/mcp-management/logs` - 获取执行日志
- ✅ `GET /api/v1/mcp-management/categories` - 获取分类列表

### 前端组件（Phase 2-4）
- ✅ `MCPToolDashboard.vue` - 主控制面板
- ✅ `ToolList.vue` - 工具列表（支持搜索、过滤）
- ✅ `ToolTester.vue` - 在线测试工具
- ✅ `CallStats.vue` - 调用统计图表
- ✅ `ExecutionLogs.vue` - 执行日志查询
- ✅ 集成到 DashboardHome 快捷入口

## 🚀 测试步骤

### 1. 启动服务

**后端：**
```bash
cd backend
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

**前端：**
```bash
cd frontend
npm run dev
```

### 2. 访问管理界面

1. 打开浏览器访问：http://localhost:5173/
2. 登录系统（如果未登录）
3. 在首页点击 **"MCP 管理"** 快捷入口
   - 或者直接在地址栏输入：http://localhost:5173/ （登录后点击 MCP 管理按钮）

### 3. 功能测试

#### Tab 1: 工具列表
- ✅ 查看所有已注册的 MCP 工具
- ✅ 按分类过滤工具
- ✅ 搜索工具名称或描述
- ✅ 查看每个工具的调用统计（总调用次数、成功率、平均响应时间）
- ✅ 点击"测试"按钮跳转到在线测试
- ✅ 点击"日志"按钮查看该工具的专属日志

#### Tab 2: 在线测试
- ✅ 选择要测试的工具
- ✅ 查看工具的参数 Schema
- ✅ 编辑 JSON 格式的参数
- ✅ 点击"执行测试"运行工具
- ✅ 查看执行结果（成功/失败、耗时、返回数据）

#### Tab 3: 调用统计
- ✅ 查看 Top 10 最常用工具排行
- ✅ 查看 Top 10 响应最慢工具
- ✅ 按分类查看工具数量和总调用次数

#### Tab 4: 执行日志
- ✅ 查看所有工具的执行日志
- ✅ 按工具名称过滤日志
- ✅ 查看每次执行的详细信息（时间、状态、耗时、错误信息）
- ✅ 刷新日志数据

## 📊 数据结构说明

### 工具统计信息
```json
{
  "total_calls": 100,
  "success_calls": 95,
  "failed_calls": 5,
  "total_response_time_ms": 2500,
  "avg_response_time_ms": 25.0
}
```

### 执行日志条目
```json
{
  "timestamp": 1716048000.123,
  "tool_name": "query_form_template",
  "success": true,
  "execution_time_ms": 45.2,
  "error": null
}
```

## 🔧 注意事项

### 当前实现限制
1. **数据存储**：调用统计和日志使用内存存储，重启后端会清空
   - 后续可迁移到 SQLite 或 Redis 实现持久化
   
2. **最大日志数**：默认保留最近 1000 条日志
   - 可在 `mcp_management.py` 中修改 `MAX_LOG_ENTRIES` 常量

3. **无权限控制**：当前所有登录用户都可访问
   - 后续可添加角色权限控制（如仅管理员可见）

### 性能优化建议
1. 如果工具数量超过 100，建议添加分页功能
2. 如果日志量很大，建议使用数据库 + 索引
3. 统计数据可以添加定时聚合任务（每小时/每天汇总）

## 🐛 常见问题

### Q1: 看不到任何工具？
**A:** 检查是否已注册 MCP 工具。查看 `backend/app/mcp_tools/` 目录下的工具文件，确保使用了 `@mcptool` 装饰器。

### Q2: 测试工具时返回错误？
**A:** 
- 检查参数格式是否符合 Schema 要求
- 查看后端控制台日志了解详细错误信息
- 确认工具依赖的服务正常运行

### Q3: 统计数据不更新？
**A:** 
- 点击顶部的"刷新"按钮手动刷新
- 统计数据在每次工具调用后自动更新

## 📝 下一步优化方向

根据计划文档，后续可以实施：

1. **数据持久化**
   - 创建 SQLite 表存储调用历史和统计
   - 实现定期清理旧日志的功能

2. **高级监控**
   - 添加实时调用图表（使用 ECharts）
   - 实现异常告警机制

3. **工具管理 CRUD**
   - 启用/禁用工具
   - 配置工具参数默认值
   - 设置工具访问权限

4. **性能优化**
   - 添加缓存层（Redis）
   - 实现异步日志写入
   - 优化大数据量查询

## ✨ 总结

MCP 工具管理平台已成功实现核心功能，包括：
- ✅ 完整的后端 API（5个接口）
- ✅ 美观的前端 Dashboard（4个Tab）
- ✅ 实时监控和诊断能力
- ✅ 在线测试和调试功能
- ✅ 调用统计和日志查询

现在可以方便地管理和监控所有 MCP 工具的使用情况！
