# 工作流 MCP 工具实施说明

## 📋 概述

本次实施将工作流执行能力封装为标准化 MCP 工具，使 LLM 能够自主调用工作流，实现真正的分层智能架构。

---

## ✅ 已完成的工作

### 1. 创建工作流 MCP 工具文件

**文件位置**: `backend/app/mcp_tools/workflow_tools.py`

**实现的工具**:

| 工具名称 | 功能 | 使用场景 |
|---------|------|---------|
| `execute_workflow` | 执行指定的工作流 | LLM 决定需要运行某个业务流程时 |
| `list_workflows` | 列出所有可用的工作流 | 查询系统中有哪些工作流可用 |
| `get_workflow_detail` | 获取工作流详细信息 | 了解工作流的输入输出参数 |
| `get_workflow_execution_history` | 查询执行历史 | 审计和调试工作流执行情况 |

### 2. 注册工作流工具

**修改文件**: `backend/app/mcp_tools/__init__.py`

添加了工作流工具的导入：
```python
# 工作流工具
from . import workflow_tools
```

### 3. 更新文档

**修改文件**: `docs/MCP_GUIDE.md`

- 添加了工作流工具分类说明
- 提供了完整的使用示例
- 包含 API 调用格式和响应示例

---

## 🎯 架构变化

### **之前的架构**

```
LLM (AgentExecutor)
  ↓
MCP ToolHub
  ├─→ form_generate
  ├─→ kb_qa
  ├─→ llm_chat
  └─→ ... (其他工具)

❌ LLM 无法调用工作流
```

### **现在的架构**

```
LLM (AgentExecutor)
  ↓
MCP ToolHub
  ├─→ form_generate
  ├─→ kb_qa
  ├─→ llm_chat
  ├─→ execute_workflow ← 新增！
  ├─→ list_workflows   ← 新增！
  ├─→ get_workflow_detail ← 新增！
  └─→ ... (其他工具)
       ↓
  WorkflowExecutor
       ↓
  执行可视化工作流
```

---

## 🔧 技术实现细节

### 1. execute_workflow 工具

**核心逻辑**:
```python
@mcptool(
    name="execute_workflow",
    description="执行指定的工作流。当需要运行预定义的业务流程时使用...",
    category="workflow"
)
def execute_workflow(workflow_code: str, inputs: Dict[str, Any] = None):
    # 1. 从数据库获取工作流定义
    workflow_result = WorkflowService.get_workflow(workflow_code, db)
    
    # 2. 检查工作流是否激活
    if not workflow_data.get("isActive", True):
        return {"success": False, "error": "工作流已被禁用"}
    
    # 3. 执行工作流
    executor = WorkflowExecutor(workflow_def)
    context = asyncio.run(executor.execute(inputs))
    
    # 4. 返回结果
    return {
        "success": context.status.value == "completed",
        "result": {
            "execution_id": context.workflow_id,
            "status": context.status.value,
            "outputs": context.outputs
        }
    }
```

**特点**:
- ✅ 自动检查工作流是否存在和激活
- ✅ 支持异步执行（使用 asyncio）
- ✅ 完整的错误处理
- ✅ 返回详细的执行结果

### 2. list_workflows 工具

**功能**: 列出所有可用的工作流，支持按分类过滤

**返回数据**:
```json
{
  "success": true,
  "result": {
    "total": 5,
    "workflows": [
      {
        "workflowCode": "order_processing",
        "workflowName": "订单处理流程",
        "description": "...",
        "category": "order",
        "tags": ["order", "automation"]
      }
    ]
  }
}
```

### 3. get_workflow_detail 工具

**功能**: 获取工作流的详细信息，包括输入输出参数

**智能分析**:
- 自动解析开始节点的输入参数
- 自动解析结束节点的输出参数
- 统计节点数量和连接数量

### 4. get_workflow_execution_history 工具

**功能**: 查询工作流的执行历史记录

**返回数据**:
```json
{
  "success": true,
  "result": {
    "total": 10,
    "executions": [
      {
        "executionId": "exec_20260518123456",
        "status": "completed",
        "startTime": "2026-05-18T10:30:00",
        "endTime": "2026-05-18T10:30:05",
        "durationSeconds": 5,
        "triggerType": "manual",
        "triggeredBy": "user123"
      }
    ]
  }
}
```

---

## 🚀 使用场景

### 场景 1: LLM 自主调用工作流

**用户输入**: "帮我处理订单 ORD-12345"

**LLM 决策**:
```json
{
  "reasoning": "用户需要处理订单，应该执行订单处理工作流",
  "tool_name": "execute_workflow",
  "arguments": {
    "workflow_code": "order_processing",
    "inputs": {
      "order_id": "ORD-12345"
    }
  }
}
```

**执行流程**:
```
AgentExecutor → execute_workflow → WorkflowExecutor → 返回结果
```

### 场景 2: 动态选择工作流

**用户输入**: "我需要一个审批流程"

**LLM 决策**:
```json
{
  "reasoning": "用户需要审批流程，先查询可用的工作流",
  "tool_name": "list_workflows",
  "arguments": {
    "category": "approval"
  }
}
```

**然后 LLM 根据返回结果选择合适的工怍流并执行**。

### 场景 3: 混合使用

**复杂任务**:
```
用户: "检查订单状态，如果有问题就发起退款流程"

LLM 执行步骤:
1. execute_workflow(workflow_code="order_status_check", ...)
2. 分析返回结果
3. 如果需要退款:
   execute_workflow(workflow_code="refund_process", ...)
```

---

## 📊 优势对比

| 维度 | 之前（无工作流工具） | 现在（有工作流工具） |
|------|-------------------|-------------------|
| **LLM 可见性** | 看不到工作流 | 可以看到并调用工作流 |
| **灵活性** | 工作流只能手动调用 | LLM 可以智能选择 |
| **集成度** | 对话和工作流分离 | 统一的工具调用接口 |
| **智能化** | 需要人工判断何时用工作流 | LLM 自动决策 |
| **可扩展性** | 新增工作流需单独处理 | 自动注册为 MCP 工具 |

---

## 🧪 测试方法

### 1. 运行测试脚本

```bash
cd d:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai
python test_workflow_mcp_tools.py
```

**预期输出**:
```
============================================================
测试工作流 MCP 工具
============================================================

已注册工具总数: XX
可用分类: ['form', 'kb', 'llm', 'system', 'workflow']

------------------------------------------------------------
检查工作流工具:
------------------------------------------------------------
execute_workflow                    ✅ 已注册
list_workflows                      ✅ 已注册
get_workflow_detail                 ✅ 已注册
get_workflow_execution_history      ✅ 已注册
```

### 2. 通过 API 测试

```bash
# 列出所有工作流
curl -X POST http://localhost:8000/api/v1/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"tool_name": "list_workflows", "arguments": {}}'

# 执行工作流
curl -X POST http://localhost:8000/api/v1/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{
    "tool_name": "execute_workflow",
    "arguments": {
      "workflow_code": "your_workflow_code",
      "inputs": {}
    }
  }'
```

### 3. 在 AgentExecutor 中测试

启动后端服务后，通过聊天界面测试：

```
用户: "列出所有可用的工作流"
AI: [调用 list_workflows 工具，返回工作流列表]

用户: "执行订单处理工作流，订单号是 ORD-123"
AI: [调用 execute_workflow 工具，执行工作流并返回结果]
```

---

## ⚠️ 注意事项

### 1. 工作流必须先存在

在执行工作流之前，确保：
- ✅ 工作流已在数据库中创建
- ✅ 工作流处于激活状态 (`isActive = true`)
- ✅ 工作流定义完整（有 nodes 和 edges）

### 2. 输入参数匹配

调用 `execute_workflow` 时，传入的 `inputs` 必须与工作流开始节点定义的参数匹配。

**查看参数的方法**:
```bash
# 先获取工作流详情
POST /api/v1/mcp/tools/call
{
  "tool_name": "get_workflow_detail",
  "arguments": {
    "workflow_code": "your_workflow"
  }
}
```

### 3. 异步执行

工作流执行是异步的，如果工作流中包含 LLM 节点，可能需要较长时间。

**建议**:
- 对于长时间运行的工作流，考虑使用流式执行
- 设置合理的超时时间
- 监控执行状态

### 4. 错误处理

工具会捕获所有异常并返回友好的错误信息：

```json
{
  "success": false,
  "error": "工作流 'xxx' 不存在或已被禁用"
}
```

---

## 🔄 后续优化建议

### 1. 添加工作流推荐工具

```python
@mcptool(
    name="recommend_workflow",
    description="根据用户需求推荐最合适的工作流"
)
def recommend_workflow(user_requirement: str) -> Dict[str, Any]:
    """使用 LLM 分析需求，推荐合适的工作流"""
    # 实现逻辑...
```

### 2. 支持流式执行

为长时间运行的工作流提供流式输出：

```python
@mcptool(
    name="execute_workflow_stream",
    description="流式执行工作流，实时返回进度"
)
async def execute_workflow_stream(workflow_code: str, inputs: Dict = None):
    """流式执行工作流"""
    # 实现逻辑...
```

### 3. 工作流组合工具

允许 LLM 组合多个工作流：

```python
@mcptool(
    name="compose_workflows",
    description="组合多个工作流形成新的流程"
)
def compose_workflows(workflow_codes: List[str]) -> Dict[str, Any]:
    """组合工作流"""
    # 实现逻辑...
```

### 4. 性能优化

- 缓存工作流定义，减少数据库查询
- 支持并行执行多个工作流
- 添加执行结果缓存

---

## 📝 总结

✅ **已完成**:
- 创建了 4 个工作流 MCP 工具
- 更新了工具注册机制
- 完善了文档和使用示例
- 提供了测试脚本

🎯 **实现效果**:
- LLM 现在可以自主调用工作流
- 实现了真正的分层智能架构
- 统一了工具调用接口
- 提高了系统的灵活性和智能化程度

🚀 **下一步**:
- 在实际业务场景中测试
- 根据反馈优化工具设计
- 添加更多高级功能

---

**实施日期**: 2026-05-18  
**实施人员**: AI Assistant  
**版本**: v1.0
