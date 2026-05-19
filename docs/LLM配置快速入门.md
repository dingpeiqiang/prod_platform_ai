# LLM 配置快速入门

## 🚀 5 分钟完成 LLM 配置

### 第 1 步：启动服务

```bash
# 启动后端
cd backend
python -m uvicorn app.main:app --reload

# 启动前端（新终端）
cd frontend
npm run dev
```

### 第 2 步：访问前端

打开浏览器，访问：
```
http://localhost:5173
```

### 第 3 步：配置模型

1. 在侧边栏找到 **"模型配置"** 面板
2. 填写以下信息：

   **以阿里云通义千问为例：**
   - Provider 类型：`自定义 (OpenAI 兼容)`
   - 模型名称：`qwen-plus`
   - API Key：`sk-你的密钥`
   - Base URL：`https://dashscope.aliyuncs.com/compatible-mode/v1`

3. 点击 **"测试连接"**
4. 看到 "✓ 模型连接测试成功" 后，点击 **"应用配置"**

### 第 4 步：开始使用

现在可以开始聊天了！🎉

---

## 📝 常用模型配置速查

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

---

## ❓ 遇到问题？

### 测试失败？
- 检查 API Key 是否正确
- 确认模型名称拼写无误
- 验证 Base URL 是否准确
- 查看后端日志获取详细错误信息

### 看不到配置面板？
- 刷新浏览器（Ctrl+F5）
- 检查前端是否正常启动
- 查看浏览器控制台错误

### 需要帮助？
查看详细文档：
- [LLM 配置迁移说明](./LLM配置迁移说明.md)
- [自定义模型配置功能说明](./自定义模型配置功能说明.md)

---

**祝使用愉快！** 🎊
