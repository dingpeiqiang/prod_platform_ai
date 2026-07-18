# 快速启动指南

## 一键启动（推荐）

```powershell
.\start-all.ps1
```

同时启动 **Spring Boot 后端（6174）** 与前端（5173），各自独立窗口。

---

## 分别启动

### Spring Boot 后端
```powershell
.\start-backend-app.ps1
```
- API: http://localhost:6174
- Health: http://localhost:6174/api/v1/health
- Chat v2: http://localhost:6174/api/v2/chat/sessions

### 前端
```powershell
.\start-frontend.ps1
```
- 界面: http://localhost:5173
- 代理目标: Spring Boot 6174

> Python FastAPI 后端已退出商用主路径（`start-backend.ps1` 已标记 deprecated）。本体配置、会话等能力统一由 Spring Boot 提供；缺失外部依赖以可替换 Mock 契约承接。

---

## 常用命令

### 前端
```bash
cd frontend
npm run dev      # 开发模式
npm run build    # 生产构建
npm run preview  # 预览构建结果
```

### 后端
```powershell
.\start-backend-app.ps1          # Spring Boot 6174
```

可选环境变量：
```powershell
$env:LLM_ENABLED="true"
$env:LLM_API_KEY="..."
$env:LLM_BASE_URL="..."
$env:LLM_MODEL="..."
```

---

## 服务地址

| 服务 | 地址 |
|------|------|
| 前端界面 | http://localhost:5173 |
| Spring Boot API | http://localhost:6174 |
| Health | http://localhost:6174/api/v1/health |

## 本体平台 / 推理引擎 API

前缀：`/api/v1/ontology-mvp`

| 能力 | 方法 | 路径 |
|------|------|------|
| 图谱摘要 | GET | `/graph` |
| 本体元数据（类/场景/模板） | GET | `/meta` |
| 字段推理 | POST | `/config/infer` |
| 合规校验 | POST | `/config/compliance` |
| 智聊配置 | POST | `/config/chat` |
| 智读批量 | POST | `/config/batch` |
| 运营看板 | GET | `/ops/dashboard` |
| 根因分析 | POST | `/ops/root-cause` |
| 风险稽核 | POST | `/ops/risk-audit` |
| 风险规则 | GET/POST | `/ops/risk-rules` |

## 会话持久化 API（可替换 Mock）

前缀：`/api/v2/chat`

| 能力 | 方法 | 路径 |
|------|------|------|
| 会话列表 | GET | `/sessions` |
| 创建会话 | POST | `/sessions` |
| 消息列表 | GET | `/sessions/{id}/messages` |
| 保存消息 | POST | `/sessions/{id}/messages` |
| 批量保存 | POST | `/sessions/{id}/messages/batch` |
| 文件上传 | POST | `/upload` |

当前实现为内存 Mock，接口契约与前端 `chatApi.js` 对齐，后续可无感替换为数据库/外部会话服务。

---

## 开发特性

✅ **热更新** - 修改代码自动刷新  
✅ **智能代理** - 前端请求自动转发到 Spring Boot  
✅ **路径别名** - 使用 `@/` 代替相对路径  
✅ **可替换 Mock** - 会话/本体 MVP 等缺失依赖先走契约稳定的 Mock  

---

## 常见问题

**Q: 端口被占用怎么办？**  
A: 启动脚本会尝试清理占用进程；Vite 也会尝试下一个可用端口。

**Q: 如何停止服务？**  
A: 在对应窗口按 `Ctrl+C`

**Q: 依赖安装失败？**  
A: 删除 `node_modules` 和 `package-lock.json`，重新运行 `npm install`

---

## 更多信息

查看完整文档：[Vite开发环境优化指南.md](./Vite开发环境优化指南.md)
