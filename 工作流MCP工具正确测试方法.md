# 工作流 MCP 工具正确测试方法

## ⚠️ 重要前提

**当前架构规则**：
1. ✅ **所有聊天都必须基于场景** - 没有匹配到场景就不会回答
2. ✅ **第一阶段意图识别只返回 `sceneCode`** - 不直接返回工具调用
3. ✅ **场景提示词决定后续动作** - 包括是否调用工具

---

## 🎯 正确的测试流程

### ❌ 错误的测试方式（不会工作）

```
用户: "列出所有可用的工作流"
```

**为什么不行？**
- LLM 在第一阶段意图识别时，找不到对应的场景
- 没有匹配到 sceneCode
- 系统会降级为普通 chat 或返回错误
- **不会调用 list_workflows 工具**

---

### ✅ 正确的测试方式

#### 步骤 1: 先确认有可用的场景

在数据库中查询有哪些场景：

```sql
SELECT scene_code, scene_name, prompt_code FROM scenes WHERE is_active = 1;
```

假设有一个场景：
```json
{
  "sceneCode": "workflow_management",
  "sceneName": "工作流管理",
  "promptCode": "workflow_management_prompt"
}
```

#### 步骤 2: 创建对应的场景提示词

提示词内容示例 (`workflow_management_prompt`):

```
你是一个工作流管理助手。根据用户的请求，执行相应的操作。

可用工具：
- list_workflows(): 列出所有工作流
- get_workflow_detail(workflow_code): 获取工作流详情
- execute_workflow(workflow_code, inputs): 执行工作流
- get_workflow_execution_history(workflow_code, limit): 查询执行历史

请根据用户输入，选择合适的工具调用。

输出格式（JSON）：
{
  "action": "call_tool",
  "toolCalls": [
    {
      "name": "工具名称",
      "arguments": {...}
    }
  ],
  "message": "给用户的回复"
}
```

#### 步骤 3: 在场景中测试

**测试用例 1: 列出工作流**

用户输入：
```
帮我查看所有可用的工作流
```

**预期流程**：
1. 第一阶段意图识别 → 匹配到 `sceneCode: "workflow_management"`
2. 第二阶段场景处理 → 使用 `workflow_management_prompt` 调用 LLM
3. LLM 返回：
   ```json
   {
     "action": "call_tool",
     "toolCalls": [
       {
         "name": "list_workflows",
         "arguments": {"active_only": true}
       }
     ]
   }
   ```
4. 执行 `list_workflows` MCP 工具
5. 返回工作流列表给用户

---

**测试用例 2: 获取工作流详情**

用户输入：
```
查看订单处理工作流的详细信息
```

**预期流程**：
1. 意图识别 → `sceneCode: "workflow_management"`
2. 场景提示词调用 LLM → 返回：
   ```json
   {
     "action": "call_tool",
     "toolCalls": [
       {
         "name": "get_workflow_detail",
         "arguments": {"workflow_code": "order_processing"}
       }
     ]
   }
   ```
3. 执行工具并返回详情

---

**测试用例 3: 执行工作流**

用户输入：
```
执行订单处理工作流，订单号是 ORD-12345
```

**预期流程**：
1. 意图识别 → `sceneCode: "workflow_management"`
2. 场景提示词调用 LLM → 返回：
   ```json
   {
     "action": "call_tool",
     "toolCalls": [
       {
         "name": "execute_workflow",
         "arguments": {
           "workflow_code": "order_processing",
           "inputs": {"order_id": "ORD-12345"}
         }
       }
     ]
   }
   ```
3. 执行 `execute_workflow` 工具
4. WorkflowExecutor 执行工作流
5. 返回执行结果

---

## 🔧 快速设置测试环境

### 方案 A: 使用现有场景

如果已经有场景配置，直接使用：

1. 查询现有场景：
   ```bash
   curl http://localhost:8000/api/v1/scenes | jq
   ```

2. 找到包含"工作流"或"工具"相关的场景

3. 检查该场景的提示词是否支持工具调用

4. 在该场景下发送消息测试

---

### 方案 B: 创建测试场景

如果没有合适的场景，创建一个：

#### 1. 创建场景

```sql
INSERT INTO scenes (
    scene_code, 
    scene_name, 
    prompt_code, 
    category,
    is_active
) VALUES (
    'test_workflow_tools',
    '工作流工具测试',
    'test_workflow_tools_prompt',
    'general',
    1
);
```

#### 2. 创建提示词模板

```sql
INSERT INTO prompt_templates (
    prompt_code,
    prompt_name,
    content,
    category,
    is_active
) VALUES (
    'test_workflow_tools_prompt',
    '工作流工具测试提示词',
    '
你是一个工作流管理助手。根据用户的请求，执行相应的操作。

可用工具：
- list_workflows(category, active_only): 列出所有工作流
- get_workflow_detail(workflow_code): 获取工作流详情  
- execute_workflow(workflow_code, inputs): 执行工作流
- get_workflow_execution_history(workflow_code, limit): 查询执行历史

请根据用户输入，选择合适的工具调用。

输出格式（JSON）：
{
  "action": "call_tool",
  "toolCalls": [
    {
      "name": "工具名称",
      "arguments": {...}
    }
  ],
  "message": "给用户的回复（可选）"
}

如果用户的问题不清楚，可以询问更多信息。
',
    'workflow',
    1
);
```

#### 3. 测试

在聊天界面发送：
```
帮我列出所有的工作流
```

---

## 📊 调试技巧

### 1. 查看意图识别结果

在后端日志中搜索：
```
[chat/stream] 意图识别结果
```

应该看到类似：
```json
{
  "sceneCode": "test_workflow_tools",
  "confidence": 0.95,
  "reasoning": "用户想要管理工作流"
}
```

### 2. 查看场景提示词调用

搜索日志：
```
[chat/stream] 场景提示词调用成功
```

### 3. 查看工具调用

搜索日志：
```
[MCPToolHub] 执行工具: list_workflows
[MCPToolHub] 工具执行成功
```

### 4. 前端观察

前端应该显示：
- 🧠 思考过程
- 📥 场景 Prompt 输入
- 📤 场景 Response 输出
- ⚙️ 工具调用信息
- 📊 最终结果

---

## ⚠️ 常见问题

### 问题 1: 没有匹配到场景

**现象**：
- 日志显示 `sceneCode` 为空
- 降级为普通 chat 或返回错误

**原因**：
- 用户输入与场景关键词不匹配
- 场景未激活
- 提示词配置错误

**解决**：
- 检查场景的关键词配置
- 确保 `is_active = 1`
- 调整用户输入使其更明确

---

### 问题 2: 场景提示词不支持工具调用

**现象**：
- 匹配到了场景
- 但场景响应中没有 `action: "call_tool"`

**原因**：
- 场景提示词没有定义工具调用逻辑
- 提示词的 output format 不正确

**解决**：
- 修改场景提示词，添加工具调用说明
- 确保提示词明确要求返回 JSON 格式

---

### 问题 3: 工具执行失败

**现象**：
- 调用了工具但返回错误

**原因**：
- 参数不正确
- 工作流不存在
- 权限问题

**解决**：
- 检查工作流是否存在且激活
- 验证参数格式
- 查看详细错误日志

---

## 🎯 推荐的测试顺序

1. **先测试简单的场景匹配**
   ```
   用户: "我想管理工作流"
   预期: 匹配到 workflow_management 场景
   ```

2. **再测试工具调用**
   ```
   用户: "列出所有工作流"
   预期: 调用 list_workflows 工具
   ```

3. **最后测试复杂流程**
   ```
   用户: "执行订单处理工作流，订单号 ORD-123"
   预期: 调用 execute_workflow 并执行
   ```

---

## 📝 测试检查清单

- [ ] 数据库中有可用的场景
- [ ] 场景关联了正确的提示词
- [ ] 提示词支持工具调用
- [ ] MCP 工具已注册（18个工具）
- [ ] 工作流数据存在（至少一个）
- [ ] 后端服务正常运行
- [ ] 前端服务正常运行
- [ ] 意图识别能匹配到场景
- [ ] 场景提示词能正确调用 LLM
- [ ] 工具能成功执行
- [ ] 前端能正确展示结果

---

## 💡 最佳实践

### 1. 场景设计原则

- **一个场景负责一类业务** - 不要把所有功能放在一个场景
- **场景提示词要明确** - 清楚说明可用的工具和调用方式
- **提供足够的上下文** - 让 LLM 知道何时调用哪个工具

### 2. 提示词编写技巧

```
✅ 好的提示词：
"当用户想查看工作流列表时，调用 list_workflows 工具"

❌ 不好的提示词：
"帮助用户管理工作流"（太模糊）
```

### 3. 工具调用规范

- **参数要完整** - 确保传入所有必需参数
- **错误要处理** - 工具失败时要有友好的提示
- **结果要格式化** - 将工具结果转换为易读的格式

---

**更新日期**: 2026-05-18  
**版本**: v1.1 (修正版)
