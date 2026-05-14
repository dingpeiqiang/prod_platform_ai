# LangChain 集成模块

## 概述

本模块是基于 LangChain 的大模型工作流升级实现，提供以下核心能力：

### 核心组件

1. **LLM 封装** (`llm_wrapper.py`)
   - 统一的 LLM 接口封装
   - 支持 OpenAI、Anthropic 等多种 Provider
   - 同步/异步流式调用支持

2. **Chain 链** (`chains.py`)
   - `FormRecognitionChain`: 表单识别链
   - `FieldExtractionChain`: 字段提取链  
   - `FormValidationChain`: 表单验证链
   - `IntentRecognitionChain`: 意图识别链

3. **Agent 代理** (`agents.py`)
   - `FormAgent`: 表单处理 Agent
   - `TaskAgent`: 任务处理 Agent
   - `ChatAgent`: 聊天 Agent

4. **Workflow 工作流** (`workflows.py`)
   - `FormWorkflow`: 表单处理工作流
   - `ValidationWorkflow`: 表单验证工作流

## API 接口

### 基础 URL: `/api/v1/langchain`

| 接口 | 方法 | 说明 |
|------|------|------|
| `/chat` | POST | 聊天接口 |
| `/intent/recognize` | POST | 意图识别 |
| `/form/recognize` | POST | 表单识别 |
| `/form/extract` | POST | 字段提取 |
| `/form/validate` | POST | 表单验证 |
| `/agent/form` | POST | 表单 Agent |
| `/agent/task` | POST | 任务 Agent |
| `/workflow/form` | POST | 表单工作流 |
| `/health` | GET | 健康检查 |

## 依赖安装

```bash
pip install langchain langchain-openai langchain-anthropic langchain-core langchain-community
```

## 使用示例

### Python 代码示例

```python
from app.langchain import FormAgent, FormWorkflow

# 使用 Agent
agent = FormAgent()
result = await agent.process("帮我填一个销售订单")

# 使用 Workflow
workflow = FormWorkflow()
async for step in workflow.run("帮我填一个销售订单"):
    print(step)
```

### API 调用示例

```bash
# 表单识别
curl -X POST http://localhost:8000/api/v1/langchain/form/recognize \
  -H "Content-Type: application/json" \
  -d '{"user_input": "帮我填一个销售订单"}'

# 字段提取
curl -X POST http://localhost:8000/api/v1/langchain/form/extract \
  -H "Content-Type: application/json" \
  -d '{"form_code": "sales_order", "user_input": "客户名称是张三，金额5000元"}'
```

## 架构设计

```
用户输入
    ↓
┌─────────────────────────────────────┐
│          Intent Recognition         │  # 意图识别链
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│          Form Recognition           │  # 表单识别链
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│          Field Extraction           │  # 字段提取链
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│         Recommendation Engine      │  # 推荐引擎
└─────────────────────────────────────┘
    ↓
┌─────────────────────────────────────┐
│          Form Validation           │  # 表单验证链
└─────────────────────────────────────┘
    ↓
        结果输出
```

## 配置说明

在 `app_config.json` 中配置 LLM：

```json
{
  "llm": {
    "enabled": true,
    "provider": "openai",
    "model": "gpt-4o",
    "baseUrl": "https://api.openai.com/v1",
    "apiKey": "your-api-key",
    "temperature": 0.1,
    "maxTokens": 4096
  }
}
```

## 扩展指南

### 添加新的 Chain

1. 创建新的 Chain 类，继承或参考现有 Chain 实现
2. 在 `chains.py` 中注册
3. 在 `__init__.py` 中导出

### 添加新的 Agent

1. 创建新的 Agent 类
2. 在 `agents.py` 中实现
3. 在 `__init__.py` 中导出

### 添加新的 Workflow

1. 创建新的 Workflow 类
2. 实现异步生成器方法 `run()`
3. 在 `workflows.py` 中注册

## 注意事项

1. 确保 LLM 服务已正确配置
2. 建议在生产环境中使用流式调用
3. 注意 API 调用频率限制
4. 建议添加适当的错误处理和重试机制
