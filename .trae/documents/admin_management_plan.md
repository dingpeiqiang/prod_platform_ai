# 产商品研发助手 - 后台运营管理系统规划设计

## 一、项目概述

### 1.1 项目背景

本项目是基于 AI 驱动的动态表单底层框架，当前已具备智能表单识别、字段提取、表单验证、历史数据推荐等核心能力。现需构建**后台运营管理系统**，提供完善的运营管理功能，包括会话管理、表单配置、数据管理、系统监控等能力。

### 1.2 设计目标

| 目标 | 描述 |
|------|------|
| 统一管理入口 | 提供集中的后台管理界面，统一管理表单、会话、用户等资源 |
| 可视化监控 | 提供系统运行状态、会话统计、表单使用情况等可视化仪表盘 |
| 高效配置 | 支持表单 Schema 的可视化配置和版本管理 |
| 数据管理 | 支持历史数据导入、分析和推荐引擎配置 |
| 权限控制 | 支持管理员角色和操作权限管理 |

### 1.3 核心价值

- **运营效率提升**：通过可视化管理界面，减少人工操作成本
- **系统可观测性**：实时监控系统运行状态，快速定位问题
- **配置灵活性**：支持表单动态配置，无需代码修改即可扩展业务

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                      前端层 (Vue 3)                            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │
│  │ 仪表盘Dashboard│ │ 会话管理     │ │ 表单管理     │            │
│  └──────────────┘ └──────────────┘ └──────────────┘            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │
│  │ 数据管理     │ │ 系统配置     │ │ 用户管理     │            │
│  └──────────────┘ └──────────────┘ └──────────────┘            │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP/REST
┌───────────────────────────▼─────────────────────────────────────┐
│                      后端层 (FastAPI)                           │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              Admin Router (/api/v1/admin)               │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │    │
│  │  │ Session  │ │  Form    │ │  Data    │ │  System  │   │    │
│  │  │ Manager  │ │ Manager  │ │ Manager  │ │ Manager  │   │    │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │    │
│  └─────────────────────────────────────────────────────────┘    │
└───────────────────────────┬─────────────────────────────────────┘
                            │ SQLAlchemy
┌───────────────────────────▼─────────────────────────────────────┐
│                       数据层 (SQLite/PostgreSQL)               │
│  sessions | messages | forms | users | system_config            │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 模块划分

| 模块 | 职责 | 状态 |
|------|------|------|
| Dashboard | 系统概览、实时监控、统计图表 | 新增 |
| SessionManager | 会话列表、详情查看、批量删除 | 新增 |
| FormManager | 表单 Schema 管理、版本控制、部署 | 增强现有 |
| DataManager | 历史数据导入、分析、推荐配置 | 增强现有 |
| SystemConfig | 系统参数配置、日志查看 | 新增 |
| UserManager | 用户列表、角色权限管理 | 新增 |

### 2.3 关键技术栈

| 分类 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 前端框架 | Vue | 3.x | 主框架 |
| UI组件 | Element Plus | 2.x | UI组件库 |
| 图表库 | ECharts | 5.x | 数据可视化 |
| 后端框架 | FastAPI | 0.100+ | API框架 |
| 数据库 | SQLite/PostgreSQL | - | 数据存储 |
| 状态管理 | Pinia | 2.x | 前端状态管理 |

---

## 三、功能设计

### 3.1 仪表盘 (Dashboard)

#### 3.1.1 功能概述

提供系统运行状态概览，包括会话统计、表单使用情况、系统健康度等核心指标。

#### 3.1.2 核心指标

| 指标 | 说明 | 数据来源 |
|------|------|----------|
| 今日会话数 | 今日新增会话数量 | sessions 表 |
| 活跃用户数 | 今日活跃用户数 | users 表 |
| 表单填写量 | 今日表单提交数量 | messages 表 |
| 系统健康度 | API 响应状态、数据库连接状态 | 系统监控 |
| 推荐命中率 | 历史数据推荐命中率 | recommendation 日志 |

#### 3.1.3 页面布局

```
┌─────────────────────────────────────────────────────┐
│  仪表盘 Dashboard                                   │
├─────────────────────────────────────────────────────┤
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐     │
│  │ 会话数│ │用户数│ │表单量│ │健康度│ │命中率│     │
│  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘     │
├─────────────────────────────────────────────────────┤
│  ┌──────────────────────┐  ┌─────────────────────┐ │
│  │ 会话趋势图           │  │ 表单使用排行        │ │
│  │ (近7日)              │  │                     │ │
│  └──────────────────────┘  └─────────────────────┘ │
├─────────────────────────────────────────────────────┤
│  ┌──────────────────────┐  ┌─────────────────────┐ │
│  │ 系统告警             │  │ 最近活跃会话        │ │
│  │                     │  │                     │ │
│  └──────────────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 3.2 会话管理 (Session Management)

#### 3.2.1 功能概述

管理用户会话，支持会话列表查看、详情查看、批量删除等操作。

#### 3.2.2 功能列表

| 功能 | 说明 | 接口 |
|------|------|------|
| 会话列表 | 分页展示所有会话，支持按用户、时间筛选 | `GET /api/v2/chat/sessions` |
| 会话详情 | 查看单会话完整消息历史 | `GET /api/v2/chat/sessions/{id}/messages` |
| 删除会话 | 删除指定会话及关联消息 | `DELETE /api/v2/chat/sessions/{id}` |
| 批量删除 | 批量删除选中会话 | `DELETE /api/v2/chat/sessions/batch` |
| 会话统计 | 单会话统计信息 | `GET /api/v2/chat/sessions/{id}/stats` |

#### 3.2.3 页面布局

```
┌─────────────────────────────────────────────────────┐
│  会话管理                                            │
├─────────────────────────────────────────────────────┤
│  搜索框 [用户ID] [时间范围] [搜索按钮]               │
├─────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────┐  │
│  │ 会话列表表格                                   │  │
│  │ ID | 用户 | 标题 | 创建时间 | 消息数 | 操作    │  │
│  └───────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│  分页控件                                           │
└─────────────────────────────────────────────────────┘
```

### 3.3 表单管理 (Form Management)

#### 3.3.1 功能概述

管理表单 Schema 配置，支持创建、编辑、部署、版本管理等操作。

#### 3.3.2 功能列表

| 功能 | 说明 | 接口 |
|------|------|------|
| 表单列表 | 展示所有已部署表单 | `GET /api/v1/chat/history/list` |
| 表单详情 | 查看表单 Schema 详情 | `GET /api/v1/form/schema/{form_code}` |
| 创建表单 | 新建表单 Schema | `POST /api/v1/chat/deploy-config` |
| 编辑表单 | 修改表单配置 | `POST /api/v1/chat/deploy-config` |
| 删除表单 | 删除表单及版本备份 | `POST /api/v1/chat/delete-form` |
| 版本列表 | 查看表单历史版本 | `GET /api/v1/chat/form-versions/{form_code}` |
| 版本回退 | 回退到指定版本 | `POST /api/v1/chat/rollback-form` |

#### 3.3.3 本体编辑器设计

> **说明**：在本项目中，**Schema 编辑器即是本体编辑器**。表单配置文件存储在 `backend/config/ontologies/{form_code}.json`，采用"实体-字段"层级结构。

本体编辑器采用 JSON 可视化编辑，支持：
- **实体(Entity)管理**：增删改实体，设置实体编码和名称
- **字段(Field)管理**：增删改字段、拖拽排序
- **字段类型选择**：string、input、textarea、integer、number、boolean、date、datetime、email、phone、enum 等
- **验证规则配置**：必填设置、规则描述
- **实时预览**：即时查看表单效果
- **历史版本对比**：与历史版本进行差异对比

### 3.4 数据管理 (Data Management)

#### 3.4.1 功能概述

管理历史数据导入和推荐引擎配置。

#### 3.4.2 功能列表

| 功能 | 说明 | 接口 |
|------|------|------|
| 数据文件列表 | 展示可导入的数据文件 | `GET /api/v1/chat/history/list` |
| 数据分析 | 分析数据质量和字段分布 | `POST /api/v1/chat/history/analyze` |
| 数据导入 | 将历史数据导入推荐引擎 | `POST /api/v1/chat/history/import` |
| 推荐配置 | 配置推荐引擎参数 | 新增接口 |
| 数据预览 | 预览历史数据内容 | 新增接口 |

#### 3.4.3 推荐引擎配置项

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| recommendationLimit | 推荐数量上限 | 5 |
| historyQueryLimit | 查询历史数量 | 1000 |
| countScoreWeight | 频率权重 | 0.4 |
| userScoreWeight | 用户个性化权重 | 0.4 |
| timeScoreWeight | 时间衰减权重 | 0.2 |
| timeDecayDays | 时间衰减天数 | 30 |

### 3.5 系统配置 (System Config)

#### 3.5.1 功能概述

管理系统级配置参数和日志查看。

#### 3.5.2 功能列表

| 功能 | 说明 |
|------|------|
| 系统参数 | 配置系统运行参数（LLM 超时、并发限制等） |
| LLM 配置 | 配置 LLM 提供商、API Key、模型选择 |
| 护栏配置 | 配置输入输出护栏规则 |
| 日志查看 | 查看系统运行日志 |
| 缓存管理 | 手动清理缓存 |

### 3.6 用户管理 (User Management)

#### 3.6.1 功能概述

管理系统用户和角色权限。

#### 3.6.2 功能列表

| 功能 | 说明 |
|------|------|
| 用户列表 | 展示所有用户 |
| 用户详情 | 查看用户信息和会话记录 |
| 创建用户 | 新增系统用户 |
| 修改密码 | 重置用户密码 |
| 角色管理 | 定义角色和权限 |

---

## 四、数据库设计

### 4.1 现有数据表

根据项目实际数据库迁移文件，现有数据表结构如下：

| 表名 | 说明 | 状态 |
|------|------|------|
| `chat_sessions` | 会话表（session_id、user_id、title、status） | 已存在 |
| `chat_messages` | 消息表（message_id、session_id、role、content、content_type） | 已存在 |
| `chat_message_metadata` | 消息元数据表（KV扩展，与业务解耦） | 已存在 |
| `form_templates` | 表单模板表（form_code、form_name、schema、version） | 已存在 |
| `form_instances` | 表单实例表（form_id、template_id、data、status） | 已存在 |
| `form_history` | 表单历史表（form_instance_id、field_code、field_value） | 已存在 |
| `ontologies` | 本体表（ontology_code、ontology_name、entities、version） | 已存在 |
| `system_logs` | 系统日志表（log_type、module、message、log_metadata） | 已存在 |

### 4.2 MySQL 数据库配置

#### 4.2.1 环境变量配置

```env
# MySQL 数据库配置
DATABASE_URL=mysql+pymysql://username:password@localhost:3306/db_name?charset=utf8mb4

# 连接池配置
DB_POOL_SIZE=20
DB_MAX_OVERFLOW=10
DB_POOL_RECYCLE=3600
DB_ECHO=False
```

#### 4.2.2 MySQL 数据表设计

##### admin_users（管理员用户表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| username | VARCHAR(100) | UNIQUE NOT NULL | 用户名 |
| password_hash | VARCHAR(256) | NOT NULL | 密码哈希（bcrypt） |
| email | VARCHAR(128) | NULL | 邮箱 |
| role | VARCHAR(32) | DEFAULT 'operator' | 角色（admin/operator/viewer） |
| status | VARCHAR(20) | DEFAULT 'active' | 状态（active/inactive） |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_admin_users_username`（唯一）、`idx_admin_users_role`

##### admin_permissions（权限表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| role_code | VARCHAR(32) | NOT NULL | 角色编码 |
| resource | VARCHAR(100) | NOT NULL | 资源标识 |
| action | VARCHAR(50) | NOT NULL | 操作权限（read/write/delete） |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：`idx_admin_permissions_role_resource`（复合唯一）

##### system_config（系统配置表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| config_key | VARCHAR(128) | UNIQUE NOT NULL | 配置键 |
| config_value | LONGTEXT | NOT NULL | 配置值（JSON格式） |
| description | VARCHAR(256) | NULL | 配置说明 |
| category | VARCHAR(50) | DEFAULT 'system' | 配置分类 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_system_config_key`（唯一）、`idx_system_config_category`

### 4.3 核心表结构详情（MySQL）

#### 4.3.1 chat_sessions（会话表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| session_id | VARCHAR(64) | UNIQUE NOT NULL | 会话UUID |
| user_id | VARCHAR(100) | NULL | 用户ID |
| title | VARCHAR(200) | NULL | 会话标题 |
| context_tags | JSON | NULL | 上下文标签 |
| session_metadata | JSON | NULL | 会话元数据 |
| status | VARCHAR(20) | DEFAULT 'active' | 状态 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_chat_sessions_session_id`（唯一）、`idx_chat_sessions_user_id`、`idx_chat_sessions_status`

#### 4.3.2 chat_messages（消息表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| message_id | VARCHAR(64) | UNIQUE NOT NULL | 消息UUID |
| session_id | VARCHAR(64) | NOT NULL | 所属会话ID |
| role | VARCHAR(20) | NOT NULL | 角色 |
| content | LONGTEXT | NOT NULL | 消息内容 |
| content_type | VARCHAR(20) | DEFAULT 'text' | 内容类型 |
| parent_id | VARCHAR(64) | NULL | 父消息ID |
| sort_order | INT | NOT NULL | 排序字段 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：`idx_chat_messages_message_id`（唯一）、`idx_chat_messages_session_id`、`idx_chat_messages_sort_order`

#### 4.3.3 chat_message_metadata（消息元数据表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| message_id | VARCHAR(64) | NOT NULL | 所属消息ID |
| meta_key | VARCHAR(100) | NOT NULL | 元数据键 |
| value | LONGTEXT | NULL | 元数据值 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：`idx_chat_message_metadata_message_id`、`idx_chat_message_metadata_key`、`uq_message_key`（复合唯一）

#### 4.3.4 form_templates（表单模板表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| form_code | VARCHAR(100) | UNIQUE NOT NULL | 表单编码 |
| form_name | VARCHAR(200) | NOT NULL | 表单名称 |
| schema | LONGTEXT | NOT NULL | 表单Schema（JSON） |
| version | INT | DEFAULT 1 | 版本号 |
| is_active | TINYINT(1) | DEFAULT 1 | 是否启用 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_form_templates_form_code`（唯一）、`idx_form_templates_is_active`

#### 4.3.5 ontologies（本体表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| ontology_code | VARCHAR(100) | UNIQUE NOT NULL | 本体编码 |
| ontology_name | VARCHAR(200) | NOT NULL | 本体名称 |
| entities | LONGTEXT | NOT NULL | 实体定义（JSON） |
| form_code | VARCHAR(100) | NULL | 关联表单编码 |
| form_name | VARCHAR(200) | NULL | 关联表单名称 |
| description | VARCHAR(500) | NULL | 描述 |
| version | INT | DEFAULT 1 | 版本号 |
| is_active | TINYINT(1) | DEFAULT 1 | 是否启用 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：`idx_ontologies_ontology_code`（唯一）、`idx_ontologies_is_active`

#### 4.3.6 form_instances（表单实例表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| form_id | VARCHAR(50) | UNIQUE NOT NULL | 表单实例ID |
| template_id | BIGINT | NOT NULL | 关联模板ID |
| data | LONGTEXT | NOT NULL | 表单数据（JSON） |
| version | INT | DEFAULT 1 | 版本号 |
| status | VARCHAR(50) | DEFAULT 'draft' | 状态 |
| user_id | VARCHAR(100) | NULL | 用户ID |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| submitted_at | DATETIME | NULL | 提交时间 |

**索引**：`idx_form_instances_form_id`（唯一）、`idx_form_instances_template_id`、`idx_form_instances_status`

#### 4.3.7 form_history（表单历史表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| form_instance_id | BIGINT | NOT NULL | 关联实例ID |
| field_code | VARCHAR(100) | NOT NULL | 字段编码 |
| field_value | LONGTEXT | NOT NULL | 字段值 |
| user_id | VARCHAR(100) | NULL | 用户ID |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：`idx_form_history_instance_id`、`idx_form_history_field_code`

#### 4.3.8 system_logs（系统日志表）

| 字段名 | MySQL类型 | 约束 | 说明 |
|--------|-----------|------|------|
| id | BIGINT | PRIMARY KEY AUTO_INCREMENT | 主键 |
| log_type | VARCHAR(50) | NOT NULL | 日志类型 |
| module | VARCHAR(100) | NOT NULL | 模块名 |
| message | LONGTEXT | NOT NULL | 日志消息 |
| user_id | VARCHAR(100) | NULL | 用户ID |
| log_metadata | JSON | NULL | 元数据 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**索引**：`idx_system_logs_log_type`、`idx_system_logs_module`、`idx_system_logs_created_at`

---

## 五、API 接口设计

### 5.1 新增接口列表

| API 路径 | 方法 | 说明 |
|----------|------|------|
| `/api/v1/admin/sessions` | GET | 分页获取会话列表 |
| `/api/v1/admin/sessions/batch` | DELETE | 批量删除会话 |
| `/api/v1/admin/users` | GET | 获取用户列表 |
| `/api/v1/admin/users` | POST | 创建用户 |
| `/api/v1/admin/users/{id}` | GET | 获取用户详情 |
| `/api/v1/admin/users/{id}` | PUT | 更新用户信息 |
| `/api/v1/admin/users/{id}` | DELETE | 删除用户 |
| `/api/v1/admin/system/config` | GET | 获取系统配置 |
| `/api/v1/admin/system/config` | POST | 更新系统配置 |
| `/api/v1/admin/system/logs` | GET | 分页获取日志 |
| `/api/v1/admin/dashboard/stats` | GET | 获取仪表盘统计数据 |

### 5.2 接口详细设计

#### 5.2.1 获取会话列表

**请求**：
```
GET /api/v1/admin/sessions?page=1&limit=20&user_id=&start_time=&end_time=
```

**响应**：
```json
{
  "success": true,
  "data": {
    "list": [
      {
        "session_id": "xxx",
        "user_id": "user1",
        "title": "新对话",
        "message_count": 10,
        "created_at": "2024-01-01T10:00:00",
        "updated_at": "2024-01-01T10:30:00"
      }
    ],
    "total": 100,
    "page": 1,
    "limit": 20
  }
}
```

#### 5.2.2 获取仪表盘统计

**请求**：
```
GET /api/v1/admin/dashboard/stats
```

**响应**：
```json
{
  "success": true,
  "data": {
    "today_sessions": 120,
    "active_users": 45,
    "form_submissions": 234,
    "health_status": "healthy",
    "recommendation_hit_rate": 0.78,
    "session_trend": [100, 120, 95, 130, 115, 125, 120],
    "form_usage": [
      {"form_code": "tariff_filing", "count": 80},
      {"form_code": "survey", "count": 60}
    ]
  }
}
```

---

## 六、前端页面设计

### 6.1 页面结构

```
┌─────────────────────────────────────────────────────────┐
│  顶部导航栏                                              │
│  Logo | 导航菜单 | 用户信息                               │
├──────────────┬──────────────────────────────────────────┤
│  侧边菜单    │  主内容区                                  │
│  ├─ 仪表盘   │                                          │
│  ├─ 会话管理 │                                          │
│  ├─ 表单管理 │                                          │
│  ├─ 数据管理 │                                          │
│  ├─ 系统配置 │                                          │
│  └─ 用户管理 │                                          │
└──────────────┴──────────────────────────────────────────┘
```

### 6.2 页面列表

| 页面路径 | 组件 | 功能 |
|----------|------|------|
| `/admin/dashboard` | Dashboard.vue | 仪表盘 |
| `/admin/sessions` | SessionList.vue | 会话列表 |
| `/admin/sessions/:id` | SessionDetail.vue | 会话详情 |
| `/admin/forms` | FormList.vue | 表单列表 |
| `/admin/forms/create` | FormEditor.vue | 表单创建 |
| `/admin/forms/:code/edit` | FormEditor.vue | 表单编辑 |
| `/admin/data` | DataManager.vue | 数据管理 |
| `/admin/system` | SystemConfig.vue | 系统配置 |
| `/admin/users` | UserList.vue | 用户列表 |
| `/admin/users/create` | UserEditor.vue | 用户创建 |

### 6.3 组件设计

#### 6.3.1 侧边栏组件 (AdminSidebar.vue)

- 固定左侧，包含导航菜单
- 高亮当前激活菜单项
- 响应式折叠

#### 6.3.2 统计卡片组件 (StatCard.vue)

- 展示单个统计指标
- 支持图标、数值、变化趋势
- 统一样式

#### 6.3.3 表格组件 (BaseTable.vue)

- 支持分页、排序、筛选
- 支持批量操作
- 统一表格样式

---

## 七、安全设计

### 7.1 权限控制

| 角色 | 权限 |
|------|------|
| admin | 全部权限 |
| operator | 会话管理、表单管理、数据管理 |
| viewer | 只读权限 |

### 7.2 API 安全

- 使用 JWT Token 认证
- 接口访问日志记录
- 敏感操作二次确认

---

## 八、部署与集成

### 8.1 前端部署

- 构建命令：`npm run build`
- 静态资源部署到 Nginx
- 反向代理配置

### 8.2 后端部署

- FastAPI 应用使用 Uvicorn 运行
- 支持 Docker 容器化部署
- 配置环境变量

---

## 九、开发计划

### 9.1 开发阶段划分

| 阶段 | 时间 | 任务 |
|------|------|------|
| 第一阶段 | 1-2周 | 后端 API 开发（会话管理、用户管理） |
| 第二阶段 | 2-3周 | 前端页面开发（仪表盘、会话管理） |
| 第三阶段 | 2-3周 | 前端页面开发（表单管理、数据管理） |
| 第四阶段 | 1-2周 | 系统配置、用户管理页面 |
| 第五阶段 | 1周 | 测试、Bug修复、文档完善 |

### 9.2 任务分解

#### 9.2.1 后端任务

| 任务 | 描述 | 预估工时 |
|------|------|----------|
| Admin API Router | 创建管理员路由 | 4h |
| Session Admin API | 会话列表、批量删除 | 8h |
| User Management API | 用户 CRUD、角色管理 | 8h |
| Dashboard Stats API | 统计数据聚合 | 8h |
| System Config API | 系统配置管理 | 6h |
| 数据库迁移 | 用户表、配置表 | 4h |

#### 9.2.2 前端任务

| 任务 | 描述 | 预估工时 |
|------|------|----------|
| AdminLayout 布局 | 侧边栏、导航栏 | 6h |
| Dashboard 页面 | 统计卡片、图表 | 8h |
| SessionList 页面 | 会话列表、筛选、批量删除 | 8h |
| SessionDetail 页面 | 消息历史展示 | 6h |
| FormList 页面 | 表单列表、版本管理 | 8h |
| FormEditor 页面 | Schema 可视化编辑器 | 12h |
| DataManager 页面 | 数据导入、分析 | 8h |
| SystemConfig 页面 | 系统参数配置 | 6h |
| UserManager 页面 | 用户列表、编辑 | 6h |

---

## 十、风险与应对

| 风险 | 描述 | 应对措施 |
|------|------|----------|
| Schema 编辑器复杂度 | JSON 可视化编辑较为复杂 | 采用成熟的 JSON 编辑器组件 |
| 性能问题 | 大量会话数据查询慢 | 分页查询、索引优化 |
| 权限漏洞 | 越权访问风险 | 完善权限校验、接口日志记录 |
| 数据安全 | 敏感配置信息泄露 | 配置加密存储、最小权限原则 |

---

## 附录：现有接口清单

### 已存在的 Admin API

| API 路径 | 方法 | 说明 | 文件 |
|----------|------|------|------|
| `/api/v1/chat/history/analyze` | POST | 数据分析 | admin.py |
| `/api/v1/chat/history/import` | POST | 数据导入 | admin.py |
| `/api/v1/chat/history/list` | GET | 数据文件列表 | admin.py |
| `/api/v1/chat/history/{form_code}/summary` | GET | 数据摘要 | admin.py |
| `/api/v1/chat/deploy-config` | POST | 部署表单配置 | admin.py |
| `/api/v1/chat/delete-form` | POST | 删除表单 | admin.py |
| `/api/v1/chat/form-versions/{form_code}` | GET | 表单版本列表 | admin.py |
| `/api/v1/chat/rollback-form` | POST | 版本回退 | admin.py |

### 已存在的 Chat API

| API 路径 | 方法 | 说明 | 文件 |
|----------|------|------|------|
| `/api/v2/chat/sessions` | POST | 创建会话 | chat_v2.py |
| `/api/v2/chat/sessions` | GET | 获取会话列表 | chat_v2.py |
| `/api/v2/chat/sessions/{id}` | DELETE | 删除会话 | chat_v2.py |
| `/api/v2/chat/sessions/{id}/messages` | POST | 保存消息 | chat_v2.py |
| `/api/v2/chat/sessions/{id}/messages` | GET | 获取消息列表 | chat_v2.py |

---

**文档版本**: v1.0  
**创建时间**: 2026-05-09  
**作者**: AI Team  
**审核状态**: 待审核