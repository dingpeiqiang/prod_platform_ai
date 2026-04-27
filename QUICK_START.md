# 🚀 快速启动指南

## 一键启动（推荐）

```bash
start-all.bat
```

这将同时启动前后端服务，在独立窗口中运行。

---

## 分别启动

### 后端
```bash
start-backend.bat
```
- API: http://localhost:8000
- 文档: http://localhost:8000/docs

### 前端
```bash
start-frontend.bat
```
- 界面: http://localhost:5173
- 自动打开浏览器

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
```bash
cd backend
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

---

## 服务地址

| 服务 | 地址 |
|------|------|
| 🌐 前端界面 | http://localhost:5173 |
| 🔧 后端 API | http://localhost:8000 |
| 📖 API 文档 | http://localhost:8000/docs |

---

## 开发特性

✅ **热更新** - 修改代码自动刷新  
✅ **自动打开浏览器** - 前端启动后自动打开  
✅ **代码重载** - 后端代码修改自动重启  
✅ **智能代理** - 前端请求自动转发到后端  
✅ **路径别名** - 使用 `@/` 代替相对路径  

---

## 常见问题

**Q: 端口被占用怎么办？**  
A: Vite 会自动尝试下一个可用端口

**Q: 如何停止服务？**  
A: 在对应窗口按 `Ctrl+C`

**Q: 依赖安装失败？**  
A: 删除 `node_modules` 和 `package-lock.json`，重新运行 `npm install`

---

## 更多信息

查看完整文档：[Vite开发环境优化指南.md](./Vite开发环境优化指南.md)
