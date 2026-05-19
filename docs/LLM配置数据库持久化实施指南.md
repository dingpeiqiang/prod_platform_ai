# LLM 配置数据库持久化实施指南

## 📋 概述

本功能将用户的 LLM 配置从浏览器 localStorage 迁移到数据库存储，实现：
- ✅ 配置永久保存，清除浏览器缓存不丢失
- ✅ 多设备同步（使用相同用户标识）
- ✅ 支持多个配置方案切换
- ✅ 配置历史记录

## 🗄️ 数据库表结构

### `llm_user_configs` 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT | 主键 |
| user_identifier | VARCHAR(100) | 用户标识（session_id 或 user_id） |
| provider | VARCHAR(50) | Provider 类型 |
| model | VARCHAR(100) | 模型名称 |
| api_key | TEXT | API Key |
| base_url | TEXT | Base URL |
| temperature | FLOAT | 温度参数 (0-2) |
| max_tokens | INT | 最大 token 数 |
| thinking | BOOLEAN | 是否启用思考模式 |
| max_input_tokens | INT | 最大输入 token 数 |
| is_active | BOOLEAN | 是否为激活配置 |
| config_name | VARCHAR(100) | 配置名称（可选） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| last_used_at | DATETIME | 最后使用时间 |

## 🚀 部署步骤

### 第 1 步：创建数据库表

#### 方式 A：使用 SQL 脚本（推荐）

1. 连接到 MySQL 数据库
2. 执行 SQL 脚本：

```bash
mysql -u prodplatformai -p prodplatformai < backend/create_llm_config_table.sql
```

或者在 MySQL 客户端中手动执行 `backend/create_llm_config_table.sql` 文件中的 SQL。

#### 方式 B：使用 Alembic 迁移

如果 Alembic 迁移正常工作：

```bash
cd backend
alembic upgrade head
```

### 第 2 步：验证表创建成功

```sql
USE prodplatformai;
SHOW TABLES LIKE 'llm_user_configs';
DESCRIBE llm_user_configs;
```

应该看到表结构和 16 个字段。

### 第 3 步：重启后端服务

```bash
cd backend
python -m uvicorn app.main:app --reload
```

### 第 4 步：测试 API

#### 保存配置

```bash
curl -X POST "http://localhost:8000/api/v1/llm-config/save" \
  -H "Content-Type: application/json" \
  -d '{
    "user_identifier": "test-user-001",
    "provider": "custom",
    "model": "qwen-plus",
    "api_key": "sk-test-key",
    "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "temperature": 0.3,
    "max_tokens": 2048,
    "thinking": false
  }'
```

预期响应：
```json
{
  "success": true,
  "message": "配置保存成功",
  "config": {
    "id": 1,
    "user_identifier": "test-user-001",
    "provider": "custom",
    "model": "qwen-plus",
    ...
  }
}
```

#### 获取激活配置

```bash
curl "http://localhost:8000/api/v1/llm-config/active/test-user-001"
```

#### 列出所有配置

```bash
curl "http://localhost:8000/api/v1/llm-config/list/test-user-001?limit=10"
```

## 📡 API 接口说明

### 1. 保存配置

**端点**: `POST /api/v1/llm-config/save`

**请求体**:
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

**响应**:
```json
{
  "success": true,
  "message": "配置保存成功",
  "config": {...}
}
```

### 2. 获取激活配置

**端点**: `GET /api/v1/llm-config/active/{user_identifier}`

**响应**:
```json
{
  "success": true,
  "message": "获取成功",
  "config": {
    "id": 1,
    "provider": "custom",
    "model": "qwen-plus",
    "api_key": "sk-...",  // 包含 API Key
    ...
  }
}
```

### 3. 列出配置

**端点**: `GET /api/v1/llm-config/list/{user_identifier}?limit=10`

**响应**:
```json
{
  "success": true,
  "message": "获取到 3 个配置",
  "configs": [
    {
      "id": 1,
      "model": "qwen-plus",
      "is_active": true,
      ...
    },
    ...
  ]
}
```

### 4. 删除配置

**端点**: `DELETE /api/v1/llm-config/{config_id}`

### 5. 激活配置

**端点**: `POST /api/v1/llm-config/activate/{config_id}`

## 🔧 前端集成（待实施）

### 修改 ModelSelector.vue

需要添加以下功能：

1. **加载时从数据库获取配置**
   ```javascript
   const loadConfigFromDB = async () => {
     const userId = getUserId() // 获取用户标识
     const response = await fetch(`/api/v1/llm-config/active/${userId}`)
     const result = await response.json()
     
     if (result.success && result.config) {
       // 填充表单
       provider.value = result.config.provider
       modelName.value = result.config.model
       apiKey.value = result.config.api_key
       baseUrl.value = result.config.base_url
       // ...
     }
   }
   ```

2. **保存时写入数据库**
   ```javascript
   const saveConfigToDB = async (config) => {
     const userId = getUserId()
     await fetch('/api/v1/llm-config/save', {
       method: 'POST',
       headers: { 'Content-Type': 'application/json' },
       body: JSON.stringify({
         user_identifier: userId,
         ...config
       })
     })
   }
   ```

3. **同时保存到 localStorage（作为缓存）**
   ```javascript
   // 双写策略：数据库 + localStorage
   await saveConfigToDB(config)
   saveConfigToStorage(config)
   ```

## 🔒 安全考虑

### API Key 存储

当前实现中，API Key 以明文存储在数据库中。建议：

1. **短期方案**：
   - 限制数据库访问权限
   - 定期备份
   - 不在日志中输出 API Key

2. **长期方案**：
   - 实现加密存储
   - 使用密钥管理系统（KMS）
   - API Key 脱敏显示

### 用户标识

当前使用 `user_identifier` 字段，可以是：
- Session ID（临时用户）
- User ID（注册用户）
- Device ID（设备标识）

建议根据实际业务场景选择合适的标识方式。

## 📊 数据迁移

如果需要从 localStorage 迁移现有配置到数据库：

### 前端迁移脚本

```javascript
const migrateConfigs = async () => {
  const savedConfig = localStorage.getItem('chat_model_config')
  if (!savedConfig) return
  
  const config = JSON.parse(savedConfig)
  const userId = getUserId()
  
  // 保存到数据库
  await fetch('/api/v1/llm-config/save', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      user_identifier: userId,
      ...config,
      config_name: '从本地迁移'
    })
  })
  
  console.log('配置已迁移到数据库')
}
```

## 🐛 故障排查

### 问题 1：表创建失败

**症状**: SQL 执行报错

**解决**:
1. 检查数据库连接
2. 确认数据库名称正确
3. 检查用户权限

### 问题 2：API 返回 500 错误

**症状**: 调用 API 时返回服务器错误

**解决**:
1. 查看后端日志
2. 确认数据库表已创建
3. 检查数据库连接配置

### 问题 3：配置无法保存

**症状**: 保存配置后查询不到

**解决**:
1. 检查 `user_identifier` 是否正确
2. 确认事务已提交
3. 查看数据库中的数据

## 📝 后续优化建议

1. **配置版本管理**
   - 记录配置变更历史
   - 支持配置回滚

2. **配置模板**
   - 提供常用模型的预设配置
   - 一键应用模板

3. **配置分享**
   - 生成配置分享链接
   - 导入他人配置

4. **智能推荐**
   - 根据使用场景推荐配置
   - 自动调优参数

5. **使用统计**
   - 记录每个配置的使用次数
   - 统计 Token 消耗

---

**实施完成时间**: 2026-05-19  
**状态**: ✅ 后端已完成，待前端集成
