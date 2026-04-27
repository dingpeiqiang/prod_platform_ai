# AI驱动动态表单底层框架

基于方案文档实现的AI驱动动态表单底层框架，聚焦于聊天窗口场景，实现自然语言表单生成、双向可控通信、本体约束校验等核心能力。

## 项目结构

```
work-ai/
├── backend/                 # 后端FastAPI服务
│   ├── app/
│   │   ├── api/            # API路由
│   │   ├── core/           # 核心配置（数据库、配置）
│   │   ├── models/         # 数据模型
│   │   ├── schemas/        # Pydantic模型
│   │   ├── services/       # 业务服务（表单、本体、历史）
│   │   ├── skills/         # Skills插件（场景识别、字段提取）
│   │   └── websocket/      # WebSocket管理器
│   ├── requirements.txt    # Python依赖
│   └── app/main.py        # 应用入口
├── frontend/               # 前端Vue3应用
│   ├── src/
│   │   ├── components/     # Vue组件
│   │   ├── App.vue         # 根组件
│   │   └── main.js         # 入口文件
│   ├── package.json        # Node依赖
│   └── vite.config.js      # Vite配置
└── README.md
```

## 核心功能

### 1. AI表单生成
- 自然语言输入识别场景
- Skills插件结构化解析
- 本体约束校验（无AI幻觉）
- 标准Schema生成

### 2. 聊天窗口适配
- 动态表单组件渲染
- 推荐值智能提示
- 实时校验反馈
- 多载体适配

### 3. 双向可控
- WebSocket实时双向通信
- 后端绝对控制表单属性
- 版本号冲突解决
- 多端同步

### 4. 业务本体
- 内置销售订单、请假申请、费用报销等场景
- 字段约束、规则定义
- Schema合规校验

## 快速开始

### 环境要求

- Python 3.8+
- Node.js 16+

### 后端启动

```bash
cd backend

# 安装依赖
pip install -r requirements.txt

# 启动服务
python -m app.main
```

后端服务将在 http://localhost:8000 启动

API文档：http://localhost:8000/docs

### 前端启动

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务
npm run dev
```

前端服务将在 http://localhost:5173 启动

## 使用示例

1. 打开前端页面 http://localhost:5173
2. 在聊天窗口输入需求，例如：
   - "帮我填一个销售订单，客户姓名张三"
   - "我要请假"
   - "报销费用"
3. AI会自动识别场景并生成对应表单
4. 填写表单（可点击推荐值快速选择）
5. 点击提交按钮完成表单提交

## 核心接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/form/generate` | POST | 生成表单 |
| `/api/v1/form/submit` | POST | 提交表单 |
| `/api/v1/ontology/getFormConstraint` | POST | 获取本体约束 |
| `/api/v1/ontology/validateSchema` | POST | 校验Schema |
| `/api/v1/history/getRecommendValues` | POST | 获取推荐值 |
| `/api/v1/ws/form/{formId}` | WS | WebSocket连接 |

## 技术栈

### 后端
- FastAPI - Web框架
- SQLAlchemy - ORM
- Pydantic - 数据验证
- WebSocket - 实时通信
- SQLite - 数据库（可替换为MySQL）

### 前端
- Vue 3 - 前端框架
- Element Plus - UI组件库
- Vite - 构建工具
- Axios - HTTP客户端

## 扩展说明

### 本体扩展
在 `backend/app/services/ontology_service.py` 的 `ONTOLOGY_DATA` 中添加新的场景定义。

### Skills插件扩展
在 `backend/app/skills/` 目录下添加新的Skill插件类。

### 表单组件扩展
在 `frontend/src/components/DynamicForm.vue` 中添加新的表单类型支持。
