# backend-app（Spring Boot）

商用主路径唯一后端：业务 API + LLM（Spring AI）均在 Java 内实现。  
`backend/`（Python FastAPI）已退出默认启动链路，仅作历史对照。

## 技术栈

- Java 17
- Spring Boot 3.4
- Spring AI OpenAI（兼容自定义 `base-url`）
- Maven

## 端口

| 服务 | 端口 |
|------|------|
| Spring Boot (`backend-app`) | **6174**（前端唯一代理目标） |
| 前端 Vite | 5173 |

## 启动

```powershell
# Spring Boot 后端（6174）
.\start-backend-app.ps1

# 一键：Spring Boot + 前端
.\start-all.ps1
```

启用 LLM：

```powershell
$env:LLM_ENABLED="true"
$env:LLM_API_KEY="sk-xxx"
$env:LLM_BASE_URL="https://your-openai-compatible-host"
$env:LLM_MODEL="gpt-4o-mini"
.\start-backend-app.ps1
```

手动 Maven：

```bash
cd backend-app
mvn -s .mvn/local-settings.xml spring-boot:run
```

未设置 `LLM_ENABLED=true` 时，`/api/v1/chat/completion|stream` 返回 503；产商品本体、会话 v2、表单 schema 等 Mock 接口仍可用。

## 已实现接口

### 基础
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health`、`/api/v1/health` | 健康检查 |

### 产商品本体 `/api/v1/product-ontology`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/graph` `/meta` | 图谱 / 元数据 |
| POST | `/config/infer` `/config/compliance` `/config/chat` `/config/batch` | 配置推理链路 |
| GET/POST | `/ops/*` | 运营看板 / 根因 / 风险 |

### 会话 v2（可替换 Mock）`/api/v2/chat`
| 方法 | 路径 | 说明 |
|------|------|------|
| * | `/sessions*` `/messages*` `/upload` | 内存会话，契约对齐 `chatApi.js` |

### LLM
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/chat/completion` | 同步补全 |
| POST | `/api/v1/chat/stream` | SSE 流式 |
| GET | `/api/v1/chat/model/providers` | 模型提供商列表（Mock） |
| GET | `/api/v1/chat/model/default` | 系统默认模型（Mock） |
| GET | `/api/v1/chat/model/available` | 可用模型列表（Mock） |
| POST | `/api/v1/chat/model/test` | 连通性测试（LLM 启用时真实调用） |
| POST | `/api/v1/chat/model/switch` | 切换模型（内存 Mock） |
| POST | `/api/v1/llm-config/save` | 保存用户模型配置（内存 Mock） |
| GET | `/api/v1/llm-config/active/{userId}` | 获取激活配置（内存 Mock） |

### 表单 / 配置（可替换 Mock）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/form/schema/{formCode}` | 表单 schema（classpath:ontologies） |
| POST | `/api/v1/form/generate` | 生成表单实例（内存） |
| POST | `/api/v1/form/submit` | 提交表单（内存） |
| POST | `/api/v1/validation/llm` | 智能校验 Mock（必填规则） |
| GET | `/api/v1/config/ontologies` | 本体/表单列表 |

## 前端联调

前端固定代理到 `http://localhost:6174`，不再支持 `VITE_API_TARGET=python`。

## 测试

```bash
cd backend-app
mvn -s .mvn/local-settings.xml -q test
```

## 配置来源

- `src/main/resources/ontology/mock_graph.json`
- `src/main/resources/ontologies/*.json`（如表单 `offering_config.json`）

## 可替换 Mock 约定

缺失外部依赖（会话库、配置中心、真实 Provider 注册）先以**稳定 HTTP 契约 + 内存/文件 Mock**承接，后续可无感替换实现类，不改前端。

## 本阶段不做

- 不默认启动 Python `backend`
- 不迁移完整工作流 / harness / MCP / KB 管理台
- 不做完整鉴权

### 管理台（可替换 Mock）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/scenes/tree /scenes /scenes/stats/summary | 场景树/列表/统计 |
| GET/POST/PUT/DELETE | /api/v1/prompts* | 提示词 CRUD（内存） |
| GET | /api/v1/ontologies* | 本体列表/分类/详情 |
| * | /api/workflows* | 工作流库 Mock |
| * | /api/kb* | 知识库 Mock |
| * | /api/v1/mcp-management* | MCP 管理 Mock |

前端首页快捷入口已接通：场景 / 提示词 / 本体。  
工作流 / MCP / 知识库 / LangChain / 可视化 入口已隐藏（本阶段不做完整迁移）。
