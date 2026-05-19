# LLM 配置迁移说明

## 📢 重要变更

**LLM 配置已从配置文件迁移至前端动态管理！**

从 v2.0 开始，所有 LLM（大语言模型）相关配置都通过前端界面进行设置和管理，不再依赖 `.env` 或 `app_config.json` 文件。

## 🎯 为什么要迁移？

### 之前的问题
- ❌ 配置分散在多个文件中（`.env`、`app_config.json`）
- ❌ 修改配置需要重启服务
- ❌ API Key 等敏感信息容易泄露到版本控制
- ❌ 不同用户无法使用不同的模型配置
- ❌ 切换模型需要手动编辑配置文件

### 现在的优势
- ✅ **集中管理**：所有配置在一个界面
- ✅ **即时生效**：修改后立即应用，无需重启
- ✅ **安全可靠**：API Key 存储在浏览器本地，不上传服务器
- ✅ **灵活切换**：可以轻松测试不同的模型和配置
- ✅ **用户友好**：图形化界面，无需编辑配置文件

## 🚀 如何配置 LLM

### 步骤 1：访问前端界面

打开浏览器，访问：
```
http://localhost:5173
```

### 步骤 2：找到模型配置面板

在聊天界面的**左侧边栏**或**右侧边栏**（取决于布局），找到 **"模型配置"** 面板。

### 步骤 3：填写配置信息

#### 基础配置（必填）

| 字段 | 说明 | 示例 |
|------|------|------|
| **Provider 类型** | 选择 API 提供商 | 自定义 (OpenAI 兼容) |
| **模型名称** | 模型标识符 | `qwen-plus`, `gpt-4`, `minimax-m2.7` |
| **API Key** | 认证密钥 | `sk-xxxxxxxxxxxxx` |
| **Base URL** | API 端点地址 | `https://dashscope.aliyuncs.com/compatible-mode/v1` |

#### 高级配置（可选）

点击 **"高级配置"** 展开更多选项：

| 字段 | 说明 | 默认值 | 范围 |
|------|------|--------|------|
| **Temperature** | 控制随机性 | 0.3 | 0-2 |
| **Max Tokens** | 最大输出长度 | 2048 | 1-32768 |
| **Thinking Mode** | 显示推理过程 | 关闭 | 开/关 |

### 步骤 4：测试连接

点击 **"测试连接"** 按钮：
- ✅ 成功：显示响应预览
- ❌ 失败：显示详细错误信息和建议

### 步骤 5：应用配置

测试成功后，点击 **"应用配置"** 保存设置。

配置会自动保存到浏览器的 localStorage，下次打开时自动恢复。

## 📋 常见 Provider 配置示例

### 阿里云 DashScope（通义千问）

```json
{
  "provider": "custom",
  "model": "qwen-plus",
  "apiKey": "sk-你的API Key",
  "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "temperature": 0.3,
  "maxTokens": 2048,
  "thinking": false
}
```

**获取 API Key**：
1. 访问 https://dashscope.console.aliyun.com/
2. 登录阿里云账号
3. 进入 API Key 管理页面
4. 创建或复制 API Key

### OpenAI 官方 API

```json
{
  "provider": "openai",
  "model": "gpt-4-turbo",
  "apiKey": "sk-你的API Key",
  "baseUrl": "https://api.openai.com/v1",
  "temperature": 0.7,
  "maxTokens": 4096,
  "thinking": false
}
```

**获取 API Key**：
1. 访问 https://platform.openai.com/
2. 注册/登录 OpenAI 账号
3. 进入 API Keys 页面
4. 创建新的 API Key

### MiniMax

```json
{
  "provider": "custom",
  "model": "abab6.5-chat",
  "apiKey": "你的API Key",
  "baseUrl": "https://api.minimax.chat/v1",
  "temperature": 0.5,
  "maxTokens": 2048,
  "thinking": false
}
```

### 智谱 AI（GLM）

```json
{
  "provider": "custom",
  "model": "glm-4",
  "apiKey": "你的API Key",
  "baseUrl": "https://open.bigmodel.cn/api/paas/v4",
  "temperature": 0.3,
  "maxTokens": 2048,
  "thinking": true
}
```

## 🔧 配置文件说明

### `.env` 文件

**LLM 相关配置已废弃**，现在只保留基础设施配置：

```bash
# ✅ 保留：数据库、Redis、Neo4j 等
DATABASE_URL=mysql://...
REDIS_URL=redis://...

# ❌ 已废弃：LLM 配置（请通过前端设置）
# LLM_API_KEY=
# LLM_BASE_URL=
# LLM_MODEL=
```

### `app_config.json` 文件

**移除了敏感的 LLM 配置**，仅保留默认参数：

```json
{
  "llm": {
    "enabled": true,
    "provider": "custom",
    // ❌ 已移除：apiKey, baseUrl, model
    "temperature": 0.5,      // ✅ 保留：默认参数
    "maxTokens": 4096,       // ✅ 保留：默认参数
    "thinking": true         // ✅ 保留：默认参数
  }
}
```

这些默认参数仅在**前端未提供配置时**作为 fallback 使用。

## ⚠️ 注意事项

### 1. 首次使用

如果是第一次使用系统：
1. 启动后端和前端服务
2. 打开浏览器访问前端界面
3. **必须先配置 LLM** 才能使用聊天功能
4. 如果未配置，系统会显示警告提示

### 2. 清除浏览器数据

如果清除了浏览器缓存或 localStorage：
- 配置会丢失
- 需要重新配置 LLM
- 建议记录常用配置以便快速恢复

### 3. 多设备使用

配置存储在**本地浏览器**中：
- 不同设备需要分别配置
- 不同浏览器需要分别配置
- 同一设备的不同浏览器互不影响

### 4. 安全性

- ✅ API Key 存储在浏览器 localStorage
- ✅ 不会上传到服务器
- ✅ 不会写入版本控制
- ⚠️ 注意保护本地存储安全
- ⚠️ 不要在公共电脑上保存敏感配置

## 🐛 常见问题

### Q1: 为什么看不到"模型配置"面板？

**A**: 检查以下几点：
1. 确认前端服务已启动（`npm run dev`）
2. 刷新浏览器页面（Ctrl+F5）
3. 检查浏览器控制台是否有错误
4. 确认使用的是最新版本的前端代码

### Q2: 测试连接失败怎么办？

**A**: 根据错误信息排查：
- **HTTP 401**: API Key 无效，检查是否正确
- **HTTP 404**: 模型名称错误或 Base URL 错误
- **连接超时**: 检查网络连接
- **配置不完整**: 确保填写了所有必填字段

查看详细错误信息和日志：
```bash
# 查看后端日志
cd backend
python -m uvicorn app.main:app --reload
```

### Q3: 可以恢复到配置文件方式吗？

**A**: 不建议，但技术上可行：
1. 在 `.env` 中添加 LLM 配置
2. 修改 `openai_provider.py` 的优先级逻辑
3. 但这会失去动态配置的优势

### Q4: 如何在生产环境部署？

**A**: 推荐方案：
1. 首次部署后，管理员通过前端配置 LLM
2. 配置保存在用户浏览器中
3. 可以为不同用户配置不同的模型
4. 或者使用环境变量作为全局默认值（可选）

### Q5: 配置的优先级是什么？

**A**: 从高到低：
1. **前端传入的配置**（最高优先级）
2. `.env` 文件中的配置（fallback）
3. 代码中的硬编码默认值（最后兜底）

## 📚 相关文档

- [自定义模型配置功能说明](./自定义模型配置功能说明.md)
- [模型连接测试错误日志优化说明](./模型连接测试错误日志优化说明.md)
- [用户使用手册](./用户使用手册.md)

## 🔄 迁移检查清单

如果你是从旧版本升级：

- [ ] 备份旧的 `.env` 和 `app_config.json`
- [ ] 更新代码到最新版本
- [ ] 清理 `.env` 中的 LLM 配置
- [ ] 清理 `app_config.json` 中的敏感信息
- [ ] 启动服务并访问前端
- [ ] 通过前端界面重新配置 LLM
- [ ] 测试连接确保配置正确
- [ ] 验证聊天功能正常工作

## 💡 最佳实践

1. **定期备份配置**：记录常用的模型配置参数
2. **使用环境变量管理敏感信息**：虽然 API Key 在前端，但数据库密码等仍在 `.env`
3. **测试后再应用**：每次修改配置后先测试，确认无误再应用
4. **文档化配置**：团队内部共享常用配置示例
5. **监控使用情况**：关注 API 调用量和费用

---

**如有疑问，请查阅相关文档或联系技术支持。**
