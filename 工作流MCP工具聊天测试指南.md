# 工作流 MCP 工具聊天测试指南

## 📋 测试准备

### 1. 确认服务状态

✅ **后端服务**：已启动在 `http://localhost:8000`  
✅ **前端服务**：已启动（通常是 `http://localhost:5173`）

### 2. 准备工作流数据

在测试之前，确保数据库中至少有一个工作流。如果没有，需要先创建工作流。

---

## 🧪 测试场景

### 场景 1：列出所有工作流

**测试目的**：验证 LLM 能否调用 `list_workflows` 工具

**用户输入**：
```
列出所有可用的工作流
```

**预期行为**：
1. LLM 识别需要查询工作流列表
2. 调用 `list_workflows` MCP 工具
3. 返回工作流列表给用户

**预期输出示例**：
```
系统中可用以下工作流：

1. 订单处理流程 (order_processing)
   - 分类: order
   - 描述: 自动处理订单，包括库存检查、价格计算等

2. 请假审批流程 (leave_approval)
   - 分类: approval
   - 描述: 员工请假申请审批流程

共找到 X 个工作流。
```

---

### 场景 2：获取工作流详情

**测试目的**：验证 LLM 能否调用 `get_workflow_detail` 工具

**用户输入**：
```
订单处理工作流需要哪些输入参数？
```

**预期行为**：
1. LLM 识别需要查询工作流详情
2. 调用 `get_workflow_detail` 工具，传入 workflow_code
3. 解析返回的输入参数信息
4. 以友好的方式展示给用户

**预期输出示例**：
```
订单处理工作流 (order_processing) 需要以下输入参数：

必填参数：
- order_id (string): 订单编号
- customer_id (string): 客户ID

可选参数：
- priority (string): 优先级，默认为 normal

该工作流共有 X 个节点，Y 条连接线。
```

---

### 场景 3：执行工作流

**测试目的**：验证 LLM 能否调用 `execute_workflow` 工具

**前提条件**：
- 数据库中必须有对应的工作流
- 工作流处于激活状态
- 准备好正确的输入参数

**用户输入**：
```
帮我执行订单处理工作流，订单号是 ORD-12345，客户ID是 CUST-67890
```

**预期行为**：
1. LLM 识别需要执行工作流
2. 提取参数：workflow_code, order_id, customer_id
3. 调用 `execute_workflow` 工具
4. 等待工作流执行完成
5. 返回执行结果

**预期输出示例**：
```
正在执行订单处理工作流...

✅ 工作流执行成功！

执行ID: exec_20260518123456
状态: completed

输出结果：
- 订单状态: processed
- 总金额: 299.99
- 处理时间: 5秒

工作流已成功完成。
```

---

### 场景 4：查询执行历史

**测试目的**：验证 LLM 能否调用 `get_workflow_execution_history` 工具

**用户输入**：
```
查看订单处理工作流的最近执行记录
```

**预期行为**：
1. LLM 识别需要查询执行历史
2. 调用 `get_workflow_execution_history` 工具
3. 格式化展示历史记录

**预期输出示例**：
```
订单处理工作流的最近执行记录：

1. 执行ID: exec_20260518123456
   - 状态: ✅ completed
   - 开始时间: 2026-05-18 10:30:00
   - 结束时间: 2026-05-18 10:30:05
   - 耗时: 5秒
   - 触发方式: manual

2. 执行ID: exec_20260518120000
   - 状态: ✅ completed
   - 开始时间: 2026-05-18 09:15:00
   - 耗时: 3秒

共找到 10 条执行记录。
```

---

### 场景 5：智能选择工作流

**测试目的**：验证 LLM 能否先查询再选择合适的工作流

**用户输入**：
```
我需要一个处理订单的流程
```

**预期行为**：
1. LLM 先调用 `list_workflows` 查询所有工作流
2. 分析返回结果，找到与"订单"相关的工作流
3. 可能调用 `get_workflow_detail` 获取更多信息
4. 推荐最合适的工作流给用户

**预期输出示例**：
```
我找到了以下与订单相关的工作流：

🔍 **订单处理流程** (order_processing)
- 分类: order
- 描述: 自动处理订单，包括库存检查、价格计算等
- 标签: ["order", "automation"]

这个工作流可以帮您自动处理订单，包括：
- 检查库存
- 计算价格
- 创建订单记录

您想要执行这个工作流吗？如果是，请提供订单号和客户ID。
```

---

## 🔍 调试技巧

### 1. 查看后端日志

在后端终端中可以看到详细的调用日志：

```
[AgentExecutor] 开始流式执行, user_input=列出所有可用的工作流
[AgentExecutor] Prompt 构建完成, length=XXX
[AgentExecutor] 开始调用 LLM...
[AgentExecutor] LLM 调用完成, elapsed=X.XXs
[AgentExecutor] 决定调用工具: list_workflows
[MCPToolHub] 执行工具: list_workflows
```

### 2. 检查 MCP 工具调用

搜索日志中的关键词：
- `execute_workflow` - 工作流执行
- `list_workflows` - 工作流列表
- `get_workflow_detail` - 工作流详情
- `get_workflow_execution_history` - 执行历史

### 3. 查看数据库

可以直接查询数据库验证：

```sql
-- 查看所有工作流
SELECT * FROM workflows;

-- 查看工作流执行历史
SELECT * FROM workflow_executions ORDER BY created_at DESC LIMIT 10;

-- 查看 MCP 工具调用日志
SELECT * FROM mcp_call_logs WHERE tool_name LIKE '%workflow%' ORDER BY timestamp DESC LIMIT 10;
```

---

## ⚠️ 常见问题

### 问题 1：LLM 没有调用工作流工具

**可能原因**：
- LLM 不知道有这些工具可用
- Prompt 中没有包含工作流工具的 schema

**解决方法**：
1. 检查 AgentExecutor 是否正确加载了 MCP 工具
2. 查看日志中是否有 `[MCP] 已注册 18 个工具`
3. 确认 `get_tool_schemas_for_llm()` 返回了工作流工具

### 问题 2：工作流不存在

**错误信息**：
```
工作流 'xxx' 不存在或已被禁用
```

**解决方法**：
1. 先调用 `list_workflows` 查看有哪些工作流
2. 使用正确的工作流编码（workflow_code）
3. 确保工作流处于激活状态（is_active = true）

### 问题 3：参数不匹配

**错误信息**：
```
工作流执行失败: ...
```

**解决方法**：
1. 先调用 `get_workflow_detail` 查看需要的参数
2. 确保传入的参数类型和名称正确
3. 检查工作流开始节点的参数定义

### 问题 4：工作流执行超时

**可能原因**：
- 工作流中包含 LLM 节点，响应较慢
- 工作流逻辑复杂，执行时间长

**解决方法**：
1. 检查工作流定义是否合理
2. 考虑优化工作流中的 LLM 调用
3. 设置合理的超时时间

---

## 📊 测试检查清单

使用此清单确保所有功能都正常工作：

- [ ] 后端服务正常运行
- [ ] 前端服务正常运行
- [ ] 数据库中至少有 1 个工作流
- [ ] 工作流处于激活状态
- [ ] LLM 能够看到工作流工具（查看日志）
- [ ] `list_workflows` 工具调用成功
- [ ] `get_workflow_detail` 工具调用成功
- [ ] `execute_workflow` 工具调用成功
- [ ] `get_workflow_execution_history` 工具调用成功
- [ ] 错误处理正常工作
- [ ] 用户界面友好展示结果

---

## 🎯 快速测试命令

如果你想快速测试，可以在聊天界面依次发送：

```
1. 列出所有可用的工作流
2. 告诉我订单处理工作流的详细信息
3. （如果有工作流）执行某个工作流
4. 查看某个工怍流的执行历史
```

---

## 📝 记录测试结果

测试完成后，记录以下信息：

| 测试项 | 状态 | 备注 |
|--------|------|------|
| list_workflows | ⬜ 通过 / ⬜ 失败 |  |
| get_workflow_detail | ⬜ 通过 / ⬜ 失败 |  |
| execute_workflow | ⬜ 通过 / ⬜ 失败 |  |
| get_workflow_execution_history | ⬜ 通过 / ⬜ 失败 |  |
| 智能选择工作流 | ⬜ 通过 / ⬜ 失败 |  |
| 错误处理 | ⬜ 通过 / ⬜ 失败 |  |

---

**测试日期**: 2026-05-18  
**测试人员**: ___________  
**测试结果**: ___________
