# Ollama 集成问题修复说明

## 问题描述

在日志中看到以下错误：
```
Ollama call failed: 404 Client Error: Not Found for url: http://localhost:11434/api/chat
```

## 问题原因

经过排查，发现两个问题：

1. **环境变量优先级问题**：当 `.env` 文件中 `LLM_BASE_URL` 为空字符串时，代码逻辑没有正确处理，导致使用了空值而不是配置文件中的值。

2. **调试日志不足**：原来的错误日志不够详细，难以定位具体问题。

## 已完成的修复

### 1. 优化环境变量处理逻辑

修改了 `backend/app/services/llm_service.py` 中的 `_call_ollama` 方法：

```python
# 优先使用环境变量（如果非空），否则使用配置文件
base_url = settings.LLM_BASE_URL.strip() if settings.LLM_BASE_URL else None
if not base_url:
    base_url = self.llm_config.get('baseUrl', 'http://localhost:11434')

model = settings.LLM_MODEL.strip() if settings.LLM_MODEL else None
if not model:
    model = self.llm_config.get('model', 'qwen2.5:14b-instruct-q4_K_M')
```

### 2. 增强错误日志

添加了详细的调试信息：
- 请求 URL
- 使用的模型名称
- 消息数量
- 响应状态码和内容
- 详细的异常堆栈跟踪

### 3. 创建测试脚本

创建了 `test_ollama_direct.py` 用于直接测试 Ollama API，验证服务和模型是否正常。

## 下一步操作

### 重启后端服务

由于代码已修改，需要重启后端服务以应用更改：

**方法 1：使用批处理文件**
```bash
# 停止当前服务（按 Ctrl+C）
# 然后重新启动
start-backend.bat
```

**方法 2：手动重启**
```bash
cd backend
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### 验证修复

重启后，观察日志输出，应该看到类似以下内容：

```
Ollama client initialized
Calling Ollama: http://localhost:11434/api/chat
Model: qwen2.5:14b-instruct-q4_K_M
Messages count: 2
Ollama response received, length: 156
```

如果仍然看到错误，请检查：

1. **Ollama 服务是否运行**
   ```bash
   Invoke-WebRequest -Uri http://localhost:11434/api/tags -UseBasicParsing
   ```

2. **模型是否已加载**
   ```bash
   ollama list
   ```

3. **运行直接测试脚本**
   ```bash
   cd backend
   python ../test_ollama_direct.py
   ```

## 配置检查清单

确保以下配置正确：

### backend/config/app_config.json
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

### backend/.env（可选，如果不使用则留空或删除）
```env
LLM_API_KEY=
LLM_BASE_URL=
LLM_MODEL=
```

**注意**：如果不想使用环境变量覆盖，可以将 `.env` 文件中的 LLM 相关配置留空或删除该行。

## 常见问题

### Q1: 为什么会有 404 错误？

A: 可能的原因：
- Ollama 服务未启动
- 端口配置错误
- API 端点路径错误（应该是 `/api/chat` 而不是其他路径）

### Q2: 如何确认 Ollama 正常运行？

A: 运行以下命令：
```bash
Invoke-WebRequest -Uri http://localhost:11434/api/tags -UseBasicParsing
```
应该返回包含模型列表的 JSON。

### Q3: 环境变量和配置文件哪个优先级高？

A: 环境变量优先级更高，但如果环境变量为空字符串，系统会自动回退到配置文件中的值。

## 技术支持

如果问题仍然存在，请提供：
1. 完整的错误日志
2. `test_ollama_direct.py` 的运行结果
3. Ollama 服务状态（`ollama list` 的输出）
