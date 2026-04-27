# Ollama 本地部署集成说明

## 概述

本项目已支持 Ollama 本地部署的大语言模型，默认配置为 `qwen2.5:14b-instruct-q4_K_M` 模型。

## 前置要求

### 1. 安装 Ollama

从 [Ollama 官网](https://ollama.com/) 下载并安装 Ollama。

### 2. 拉取模型

在终端中执行以下命令拉取模型：

```bash
ollama pull qwen2.5:14b-instruct-q4_K_M
```

### 3. 启动 Ollama 服务

Ollama 安装后会自动运行后台服务，默认监听 `http://localhost:11434`。

验证服务是否正常运行：

```bash
curl http://localhost:11434/api/tags
```

## 配置说明

### 方式一：通过 app_config.json 配置（推荐）

编辑 `backend/config/app_config.json`：

```json
{
  "llm": {
    "enabled": true,
    "provider": "ollama",
    "model": "qwen2.5:14b-instruct-q4_K_M",
    "temperature": 0.3,
    "maxTokens": 2048,
    "fallbackToRules": true,
    "baseUrl": "http://localhost:11434"
  }
}
```

### 方式二：通过环境变量配置

复制 `.env.example` 为 `.env`：

```bash
cd backend
copy .env.example .env
```

编辑 `.env` 文件：

```env
# LLM配置
LLM_API_KEY=
LLM_BASE_URL=http://localhost:11434
LLM_MODEL=qwen2.5:14b-instruct-q4_K_M
```

**注意：** 环境变量的优先级高于配置文件。

## 安装依赖

确保安装了 `requests` 库：

```bash
cd backend
pip install -r requirements.txt
```

## 测试集成

运行测试脚本验证 Ollama 集成：

```bash
python test_ollama.py
```

预期输出：

```
============================================================
测试 Ollama 集成
============================================================

LLM 服务启用状态: True
LLM 提供商: ollama
模型名称: qwen2.5:14b-instruct-q4_K_M
Base URL: http://localhost:11434

============================================================
测试 1: 意图识别
============================================================

用户输入: 我想申请请假，从明天开始请3天假
表单类型: ['leave', 'expense', 'sales_order']

✅ 意图识别成功:
   结果: {...}

============================================================
测试完成
============================================================
```

## 启动应用

### 后端

```bash
cd backend
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

或使用批处理文件：

```bash
start-backend.bat
```

### 前端

```bash
cd frontend
npm run dev
```

或使用批处理文件：

```bash
start-frontend.bat
```

## 切换其他模型

如果需要切换到其他 Ollama 模型，只需修改配置：

1. 拉取新模型：
   ```bash
   ollama pull llama3.1:8b
   ```

2. 修改配置：
   ```json
   {
     "llm": {
       "model": "llama3.1:8b",
       ...
     }
   }
   ```

3. 重启后端服务

## 故障排查

### 问题 1：连接失败

**错误信息：** `Connection refused` 或 `Ollama call failed`

**解决方案：**
- 确认 Ollama 服务正在运行
- 检查 `baseUrl` 配置是否正确
- 验证防火墙是否阻止了 11434 端口

### 问题 2：模型未找到

**错误信息：** `model not found`

**解决方案：**
- 确认模型已正确拉取：`ollama list`
- 检查模型名称拼写是否正确

### 问题 3：响应超时

**解决方案：**
- 增加超时时间（在 `llm_service.py` 中修改 `timeout` 参数）
- 使用更小的模型（如 `qwen2.5:7b`）
- 减少 `maxTokens` 配置

### 问题 4：内存不足

**解决方案：**
- 使用量化版本模型（如 `q4_K_M`、`q8_0`）
- 关闭其他占用内存的应用
- 使用更小的模型

## 性能优化建议

1. **选择合适的模型大小**：根据硬件配置选择模型
   - 8GB RAM: 7B 模型
   - 16GB RAM: 14B 模型
   - 32GB+ RAM: 32B+ 模型

2. **调整温度参数**：
   - 任务型场景：0.2-0.4（更确定性）
   - 创意型场景：0.6-0.8（更多样性）

3. **限制最大 Token 数**：根据实际需求设置 `maxTokens`，避免过长响应

## 技术实现细节

### API 调用流程

1. `LLMService` 检测 `provider` 配置
2. 如果为 `ollama`，使用 HTTP POST 请求调用 Ollama API
3. 构建符合 Ollama 格式的请求体
4. 解析响应并返回内容

### 支持的 Ollama API 端点

- `/api/chat` - 聊天完成（本项目使用）
- `/api/generate` - 文本生成
- `/api/embeddings` - 嵌入向量

### 兼容性

本实现保持了与原有 OpenAI 兼容 API 的向后兼容：
- 可以无缝切换不同的 LLM 提供商
- 上层业务代码无需修改
- 支持 fallback 到规则引擎

## 相关文档

- [Ollama 官方文档](https://ollama.com/docs)
- [Qwen2.5 模型介绍](https://github.com/QwenLM/Qwen2.5)
- [项目架构改进总结](../docs/架构改进总结.md)
