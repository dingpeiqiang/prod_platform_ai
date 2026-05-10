# 场景管理功能实施计划

## 一、项目研究结论

### 1.1 现有架构分析

通过研究代码库，发现以下重要基础设施已存在：

| 组件 | 位置 | 功能 | 状态 |
|------|------|------|------|
| Admin API | `app/api/admin.py` | 管理功能API入口 | ✅ 已存在 |
| AdminService | `app/services/admin_service.py` | 管理业务逻辑 | ✅ 已存在 |
| ConfigLoader | `app/core/config_loader.py` | 配置加载器 | ✅ 已存在 |
| 数据库迁移 | `migrations/` | 版本化DB变更 | ✅ 已存在 |
| 场景映射文件 | `config/scenes/scene_mapping.json` | 当前场景配置 | ✅ 已存在 |
| Ontology模型 | `app/models/ontology.py` | 数据库模型参考 | ✅ 已存在 |

### 1.2 现有功能

AdminService已具备以下场景相关功能：
- `get_scene_mappings()` - 获取场景配置
- `update_scene_mappings()` - 更新场景配置
- 场景关键词随表单部署同步添加

## 二、实施范围

本计划专注于**场景管理功能**，暂不涉及引擎优化。实施分为3个阶段：

---

## 三、第一阶段：数据模型与数据库

### 3.1 创建场景数据模型

**新文件**：`app/models/scene.py`

创建场景模型，包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| scene_code | VARCHAR(100) UNIQUE | 场景编码 |
| scene_name | VARCHAR(200) | 场景名称 |
| description | TEXT | 描述 |
| keywords | JSON | 关键词列表 |
| priority | INT | 优先级 |
| is_active | BOOLEAN | 是否启用 |
| intent_type | VARCHAR(50) | 意图类型 |
| form_code | VARCHAR(100) | 关联表单 |
| action_type | VARCHAR(50) | 动作类型 |
| action_prompt_file | VARCHAR(255) | 动作提示词文件 |
| required_tools | JSON | 必需工具列表 |
| available_tools | JSON | 可用工具列表 |
| pre_action_steps | JSON | 前置步骤 |
| post_action_steps | JSON | 后置步骤 |
| version | INT | 版本号 |
| created_by | VARCHAR(100) | 创建者 |
| updated_by | VARCHAR(100) | 更新者 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 3.2 数据库迁移

**新文件**：`migrations/versions/{timestamp}_add_scenes_table.py`

创建数据库迁移脚本，同时：
1. 创建 scenes 表
2. 从 scene_mapping.json 迁移现有数据

---

## 四、第二阶段：服务层与业务逻辑

### 4.1 扩展场景配置结构

**更新**：`config/scenes/scene_mapping.json`

完善场景配置格式（已在之前完成）

### 4.2 创建场景服务

**新文件**：`app/services/scene_service.py`

提供以下功能：

| 方法 | 功能 |
|------|------|
| list_scenes() | 获取场景列表 |
| get_scene() | 获取单个场景 |
| create_scene() | 创建场景 |
| update_scene() | 更新场景 |
| delete_scene() | 删除场景 |
| toggle_active() | 启用/禁用场景 |
| test_scene_recognition() | 测试场景识别 |
| get_scene_stats() | 获取统计信息 |

### 4.3 扩展ConfigLoader

**更新**：`app/core/config_loader.py`

添加以下方法：
- `get_scene_by_code(scene_code)` - 从数据库获取场景
- `get_all_scenes()` - 获取所有场景
- `get_scene_prompt(scene_code)` - 加载场景提示词

### 4.4 扩展AdminService

**更新**：`app/services/admin_service.py`

集成SceneService功能

---

## 五、第三阶段：API接口

### 5.1 场景管理API

**更新**：`app/api/admin.py`

添加以下接口：

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/v1/scenes` | 获取场景列表 |
| GET | `/api/v1/scenes/{scene_code}` | 获取场景详情 |
| POST | `/api/v1/scenes` | 创建场景 |
| PUT | `/api/v1/scenes/{scene_code}` | 更新场景 |
| DELETE | `/api/v1/scenes/{scene_code}` | 删除场景 |
| PATCH | `/api/v1/scenes/{scene_code}/toggle` | 启用/禁用 |
| POST | `/api/v1/scenes/test` | 测试场景识别 |
| GET | `/api/v1/scenes/stats` | 获取统计数据 |
| POST | `/api/v1/scenes/import` | 导入场景配置 |
| GET | `/api/v1/scenes/export` | 导出场景配置 |

### 5.2 API请求/响应模型

**新文件**：`app/api/schemas/scene.py`

定义Pydantic模型用于请求验证和响应序列化

---

## 六、第四阶段：提示词管理

### 6.1 创建提示词管理器

**新文件**：`app/services/scene_prompt_manager.py`

功能：
- 加载场景提示词
- 保存提示词文件
- 变量注入
- 模板系统

### 6.2 创建提示词目录结构

```
config/prompts/scenes/
├── _framework_readme.md
├── _templates/
│   ├── form_generation.txt
│   ├── form_with_mcp.txt
│   └── chat.txt
├── tariff_filing_publicity_prompt.txt
└── [其他场景提示词]
```

---

## 七、文件清单

### 7.1 新建文件

| 文件 | 说明 |
|------|------|
| `app/models/scene.py` | 场景数据模型 |
| `app/services/scene_service.py` | 场景业务逻辑 |
| `app/services/scene_prompt_manager.py` | 提示词管理器 |
| `app/api/schemas/scene.py` | API数据模型 |
| `migrations/versions/{timestamp}_add_scenes_table.py` | 数据库迁移 |
| `config/prompts/scenes/_framework_readme.md` | 提示词框架文档 |
| `config/prompts/scenes/_templates/form_generation.txt` | 模板 |
| `config/prompts/scenes/_templates/form_with_mcp.txt` | 模板 |
| `config/prompts/scenes/tariff_filing_publicity_prompt.txt` | 示例提示词 |

### 7.2 修改文件

| 文件 | 修改内容 |
|------|---------|
| `app/models/__init__.py` | 导出Scene模型 |
| `app/core/config_loader.py` | 添加场景配置方法 |
| `app/services/admin_service.py` | 集成场景功能 |
| `app/api/admin.py` | 添加场景API |
| `config/scenes/scene_mapping.json` | 扩展配置格式 |

---

## 八、实施步骤

### 步骤1：数据模型
1. 创建 `app/models/scene.py`
2. 更新 `app/models/__init__.py`
3. 创建数据库迁移

### 步骤2：服务层
1. 创建 `app/services/scene_service.py`
2. 创建 `app/services/scene_prompt_manager.py`
3. 扩展 `ConfigLoader`
4. 扩展 `AdminService`

### 步骤3：API层
1. 创建 `app/api/schemas/scene.py`
2. 更新 `app/api/admin.py`

### 步骤4：配置文件
1. 创建提示词模板目录和文件
2. 完善示例提示词

---

## 九、注意事项

1. **保持向后兼容**：继续支持现有的 scene_mapping.json 方式
2. **双存储策略**：类似 Ontology 的做法，数据库优先，同时写入文件
3. **版本管理**：场景修改时自动版本递增
4. **热重载**：配置修改后自动刷新 ConfigLoader 缓存
5. **权限控制**：后续可扩展，但当前阶段复用现有权限机制

---

## 十、后续可扩展方向

（本次暂不实施）

1. 场景版本历史与回滚
2. 场景执行日志记录
3. 场景A/B测试
4. 更复杂的工具编排
5. 前端管理界面
