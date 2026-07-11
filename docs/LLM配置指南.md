# LLM 配置指南

> 本文档整合了 LLM 配置的所有相关内容，包括快速入门、自定义模型配置、数据库持久化和 Ollama 本地部署集成。

---

## 目录

1. [快速入门](#1-快速入门)
2. [常用模型配置速查](#2-常用模型配置速查)
3. [自定义模型配置](#3-自定义模型配置)
4. [配置持久化](#4-配置持久化)
5. [Ollama 本地部署集成](#5-ollama-本地部署集成)
6. [高级配置调优](#6-高级配置调优)
7. [API 接口说明](#7-api-接口说明)
8. [故障排查](#8-故障排查)

---

## 1. 快速入门

### 1.1 启动服务

```bash
cd backend
python -m uvicorn app.main:app --reload

cd frontend
npm run dev
```

### 1.2 访问前端

打开浏览器访问：`http://localhost:5173`

### 1.3 配置模型

1. 在侧边栏找到 **"模型配置"** 面板
2. 填写以下信息：

**以阿里云通义千问为例：**
- Provider 类型：`自定义 (OpenAI 兼容)`
- 模型名称：`qwen-plus`
- API Key：`sk-你的密钥`
- Base URL：`https://dashscope.aliyuncs.com/compatible-mode/v1`

3. 点击 **"测试连接"**
4. 看到 "✓ 模型连接测试成功" 后，点击 **"应用配置"**

---

## 2. 常用模型配置速查

### 阿里云通义千问
```
模型: qwen-plus
Base URL: https://dashscope.aliyuncs.com/compatible-mode/v1
文档: https://help.aliyun.com/zh/model-studio/
```

### OpenAI GPT
```
模型: gpt-4-turbo
Base URL: https://api.openai.com/v1
文档: https://platform.openai.com/docs
```

### MiniMax
```
模型: abab6.5-chat
Base URL: https://api.minimax.chat/v1
文档: https://www.minimaxi.com/
```

### 智谱 GLM
```
模型: glm-4
Base URL: https://open.bigmodel.cn/api/paas/v4
文档: https://open.bigmodel.cn/
```

### 百度文心一言
```
模型: ernie-bot-4
Base URL: https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions
```

---

## 3. 自定义模型配置

### 3.1 Provider 类型选择

| Provider | 说明 |
|----------|------|
| **自定义 (OpenAI 兼容)** | 支持任何 OpenAI 兼容的 API |
| **OpenAI** | 预设 OpenAI 官方 API |
| **MiniMax** | 预设 MiniMax API |
| **Ollama** | 本地部署的大语言模型 |

### 3.2 配置项说明

#### 基础配置
| 配置项 | 说明 |
|--------|------|
| 模型名称 | 例如 `gpt-4`, `qwen-plus`, `claude-3` |
| API Key | 支持显示/隐藏切换 |
| Base URL | OpenAI 兼容 API 的基础 URL |

#### 高级配置
| 配置项 | 说明 | 推荐值 |
|--------|------|--------|
| Temperature (0-2) | 控制输出的随机性 | 0.3（默认） |
| Max Tokens | 最大输出 token 数 | 2048（默认） |
| Thinking Mode | 显示模型推理过程 | 关闭（默认） |

### 3.3 配置示例

#### 阿里云 DashScope
```json
{
  "provider": "custom",
  "model": "qwen-plus",
  "apiKey": "sk-...",
  "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "temperature": 0.3,
  "maxTokens": 2048,
  "thinking": false
}
```

#### OpenAI 官方 API
```json
{
  "provider": "openai",
  "model": "gpt-4-turbo",
  "apiKey": "sk-...",
  "baseUrl": "https://api.openai.com/v1",
  "temperature": 0.7,
  "maxTokens": 4096,
  "thinking": false
}
```

---

## 4. 配置持久化

### 4.1 数据库存储

配置从浏览器 localStorage 迁移到数据库存储，实现：
- ✅ 配置永久保存，清除浏览器缓存不丢失
- ✅ 多设备同步（使用相同用户标识）
- ✅ 支持多个配置方案切换
- ✅ 配置历史记录

### 4.2 数据库表结构

**llm_user_configs 表：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | INT | 主键 |
| `user_identifier` | VARCHAR(100) | 用户标识 |
| `provider` | VARCHAR(50) | Provider 类型 |
| `model` | VARCHAR(100) | 模型名称 |
| `api_key` | TEXT | API Key |
| `base_url` | TEXT | Base URL |
| `temperature` | FLOAT | 温度参数 (0-2) |
| `max_tokens` | INT | 最大 token 数 |
| `thinking` | BOOLEAN | 是否启用思考模式 |
| `is_active` | BOOLEAN | 是否为激活配置 |
| `config_name` | VARCHAR(100) | 配置名称（可选） |
| `created_at` | DATETIME | 创建时间 |
| `updated_at` | DATETIME | 更新时间 |
| `last_used_at` | DATETIME | 最后使用时间 |

### 4.3 API 接口

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/llm-config/save` | POST | 保存配置 |
| `/api/v1/llm-config/active/{user_identifier}` | GET | 获取激活配置 |
| `/api/v1/llm-config/list/{user_identifier}` | GET | 列出所有配置 |
| `/api/v1/llm-config/{config_id}` | DELETE | 删除配置 |
| `/api/v1/llm-config/activate/{config_id}` | POST | 激活配置 |

### 4.4 安全考虑

当前实现中，API Key 以明文存储在数据库中。建议：
- 限制数据库访问权限
- 定期备份
- 不在日志中输出 API Key
- 长期方案：实现加密存储，使用密钥管理系统（KMS）

---

## 5. Ollama 本地部署集成

### 5.1 前置要求

#### 1. 安装 Ollama

从 [Ollama 官网](https://ollama.com/) 下载并安装 Ollama。

#### 2. 拉取模型

```bash
ollama pull qwen2.5:14b-instruct-q4_K_M
```

#### 3. 启动 Ollama 服务

Ollama 安装后会自动运行后台服务，默认监听 `http://localhost:11434`。

验证服务：
```bash
curl http://localhost:11434/api/tags
```

### 5.2 配置说明

#### 方式一：通过 app_config.json 配置

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

#### 方式二：通过环境变量配置

```env
LLM_API_KEY=
LLM_BASE_URL=http://localhost:11434
LLM_MODEL=qwen2.5:14b-instruct-q4_K_M
```

### 5.3 切换其他模型

1. 拉取新模型：
   ```bash
   ollama pull llama3.1:8b
   ```

2. 修改配置：
   ```json
   {
     "llm": {
       "model": "llama3.1:8b"
     }
   }
   ```

3. 重启后端服务

### 5.4 性能优化建议

| 硬件配置 | 推荐模型 |
|----------|---------|
| 8GB RAM | 7B 模型 |
| 16GB RAM | 14B 模型 |
| 32GB+ RAM | 32B+ 模型 |

---

## 6. 高级配置调优

### 6.1 Temperature 调整

| 场景 | 推荐值 | 说明 |
|------|--------|------|
| 代码生成 | 0.1-0.3 | 需要准确性 |
| 创意写作 | 0.7-1.0 | 需要多样性 |
| 数据分析 | 0.0-0.2 | 需要确定性 |
| 日常对话 | 0.3-0.5 | 平衡 |

### 6.2 Max Tokens 设置

| 场景 | 推荐值 |
|------|--------|
| 简单问答 | 256-512 |
| 普通对话 | 1024-2048 |
| 长文生成 | 4096-8192 |
| 超长文本 | 16384+ |

### 6.3 Thinking Mode

| 状态 | 适用场景 |
|------|---------|
| 开启 | 调试、学习、理解模型推理 |
| 关闭 | 生产环境、节省 token、提高速度 |

---

## 7. API 接口说明

### 7.1 测试模型配置

**端点**: `POST /api/v1/chat/model/test`

**请求体**:
```json
{
  "provider": "custom",
  "model": "qwen-plus",
  "apiKey": "sk-...",
  "baseUrl": "https://...",
  "temperature": 0.3,
  "maxTokens": 2048,
  "thinking": false
}
```

**成功响应**:
```json
{
  "success": true,
  "message": "模型连接测试成功",
  "provider": "custom",
  "model": "qwen-plus",
  "response_preview": "你好！我是通义千问..."
}
```

### 7.2 应用模型配置

**端点**: `POST /api/v1/chat/model/switch`

**请求体**: 同上

### 7.3 保存配置到数据库

**端点**: `POST /api/v1/llm-config/save`

```json
{
  "user_identifier": "user-123",
  "provider": "custom",
  "model": "qwen-plus",
  "api_key": "sk-...",
  "base_url": "https://...",
  "temperature": 0.3,
  "max_tokens": 2048,
  "thinking": false,
  "config_name": "我的配置"
}
```

---

## 8. 故障排查

### 8.1 测试失败
- 检查 API Key 是否正确
- 确认模型名称拼写无误
- 验证 Base URL 是否准确
- 查看后端日志获取详细错误信息

### 8.2 连接失败（Ollama）
- 确认 Ollama 服务正在运行
- 检查 `baseUrl` 配置是否正确
- 验证防火墙是否阻止了 11434 端口

### 8.3 HTTP 401 错误
- 检查 API Key 是否有效或已过期
- 确认账户余额充足
- 重新生成 API Key

### 8.4 HTTP 404 错误
- 检查模型名称拼写
- 确认 Base URL 正确
- 查阅 API 文档确认模型可用性

### 8.5 连接超时
- 检查网络连接
- 使用更小的模型（如 `qwen2.5:7b`）
- 减少 `maxTokens` 配置

### 8.6 内存不足（Ollama）
- 使用量化版本模型（如 `q4_K_M`、`q8_0`）
- 关闭其他占用内存的应用
- 使用更小的模型

---

**最后更新**：2026-07-11  
**版本**：v1.0