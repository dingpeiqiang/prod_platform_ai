# Vite 开发环境优化指南

## 📋 概述

本项目已完成 Vite 开发环境的全面优化，提供快速的开发体验和高效的生产构建。

## ✨ 优化内容

### 1. **Vite 配置优化** (`frontend/vite.config.js`)

#### 路径别名
```javascript
resolve: {
  alias: {
    '@': resolve(__dirname, 'src')  // 使用 @ 代替 src
  }
}
```

**使用示例**：
```javascript
// 之前
import Component from '../components/Component.vue'
import utils from '../../utils/helper.js'

// 现在
import Component from '@/components/Component.vue'
import utils from '@/utils/helper.js'
```

#### 开发服务器增强
- ✅ **自动打开浏览器**: `open: true`
- ✅ **外部访问支持**: `host: '0.0.0.0'`
- ✅ **智能端口处理**: `strictPort: false`（端口占用时自动尝试下一个）
- ✅ **热更新优化**: 启用错误覆盖层

#### 代理配置
```javascript
proxy: {
  '/api': {
    target: 'http://localhost:8000',
    changeOrigin: true,
    rewrite: (path) => path
  },
  '/ws': {
    target: 'ws://localhost:8000',
    ws: true,
    changeOrigin: true
  }
}
```

#### 生产构建优化
- **代码压缩**: 使用 terser 进行高级压缩
- **移除调试代码**: 自动删除 `console.log` 和 `debugger`
- **智能分包**: 
  - `vue-vendor`: Vue 核心库
  - `element-plus`: UI 组件库
  - `axios`: HTTP 客户端
- **Sourcemap**: 生产环境禁用，减小包体积

#### 依赖预构建
```javascript
optimizeDeps: {
  include: ['vue', 'element-plus', '@element-plus/icons-vue', 'axios']
}
```
加速首次启动和冷启动速度。

### 2. **启动脚本优化**

#### start-backend.bat
- ✅ UTF-8 编码支持（中文显示正常）
- ✅ 虚拟环境自动创建和激活
- ✅ 依赖智能检查（避免重复安装）
- ✅ 详细的服务信息展示
- ✅ 使用 Uvicorn 重载模式（代码修改自动重启）

#### start-frontend.bat
- ✅ UTF-8 编码支持
- ✅ Node.js 环境检查
- ✅ 依赖智能检查
- ✅ 自动打开浏览器
- ✅ 清晰的服务信息提示

#### start-all.bat（新增）
- 🎉 **一键启动前后端**
- 在独立窗口中运行各个服务
- 统一的服务信息展示
- 美观的界面设计

### 3. **依赖管理**

新增开发依赖：
```json
{
  "devDependencies": {
    "terser": "^5.27.0"  // 高级代码压缩工具
  }
}
```

## 🚀 快速开始

### 方式一：一键启动（推荐）

双击运行 `start-all.bat`，将自动启动前后端服务。

### 方式二：分别启动

**后端**：
```bash
start-backend.bat
```

**前端**：
```bash
start-frontend.bat
```

### 方式三：命令行启动

**后端**：
```bash
cd backend
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

**前端**：
```bash
cd frontend
npm run dev
```

## 📊 服务地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端界面 | http://localhost:5173 | Vue 3 + Vite 开发服务器 |
| 后端 API | http://localhost:8000 | FastAPI 服务 |
| API 文档 | http://localhost:8000/docs | Swagger UI |
| 交互文档 | http://localhost:8000/redoc | ReDoc 文档 |

## 🔧 开发特性

### 1. **热模块替换 (HMR)**

修改 Vue 组件后，页面会自动更新，无需刷新浏览器。

**示例**：
1. 修改 `src/components/DynamicForm.vue`
2. 保存文件
3. 浏览器自动更新，保持应用状态

### 2. **快速冷启动**

Vite 利用原生 ES 模块，首次启动仅需几秒钟。

### 3. **按需编译**

只编译请求的模块，大幅提升开发速度。

### 4. **TypeScript 支持**

如需使用 TypeScript，只需：
```bash
npm install typescript @vue/tsconfig
```

## 📦 生产构建

### 构建命令

```bash
cd frontend
npm run build
```

### 构建输出

- 输出目录: `frontend/dist`
- 静态资源: `frontend/dist/assets`
- 包含优化的 HTML、CSS、JavaScript

### 预览构建结果

```bash
npm run preview
```

这会在本地启动一个生产环境的预览服务器。

## 🎯 性能优化建议

### 1. **组件懒加载**

对于大型组件，使用动态导入：

```javascript
const HeavyComponent = () => import('@/components/HeavyComponent.vue')
```

### 2. **路由懒加载**

如果使用 Vue Router：

```javascript
const routes = [
  {
    path: '/form',
    component: () => import('@/views/FormView.vue')
  }
]
```

### 3. **图片优化**

- 使用 WebP 格式
- 压缩图片大小
- 使用 CDN 或对象存储

### 4. **依赖优化**

定期检查和更新依赖：
```bash
npm outdated
npm update
```

## 🐛 故障排查

### 问题 1: 端口被占用

**症状**: `Port 5173 is already in use`

**解决**: Vite 会自动尝试下一个可用端口（5174, 5175...）

或者手动指定端口：
```javascript
server: {
  port: 3000  // 修改为你想要的端口
}
```

### 问题 2: 代理失败

**症状**: API 请求返回 404 或 CORS 错误

**解决**: 
1. 确认后端服务正在运行
2. 检查 `vite.config.js` 中的代理配置
3. 清除浏览器缓存

### 问题 3: 热更新不工作

**解决**:
1. 重启开发服务器
2. 清除 `.vite` 缓存目录：
   ```bash
   rm -rf node_modules/.vite
   npm run dev
   ```

### 问题 4: 构建失败

**症状**: `npm run build` 报错

**解决**:
1. 检查代码语法错误
2. 更新依赖：
   ```bash
   npm install
   ```
3. 查看详细错误信息

## 📝 最佳实践

### 1. **使用路径别名**

```javascript
// ✅ 推荐
import api from '@/api/index.js'
import utils from '@/utils/helper.js'

// ❌ 不推荐
import api from '../../api/index.js'
```

### 2. **环境变量**

创建 `.env.development` 和 `.env.production`：

```env
# .env.development
VITE_API_BASE_URL=http://localhost:8000

# .env.production
VITE_API_BASE_URL=https://api.example.com
```

在代码中使用：
```javascript
const apiUrl = import.meta.env.VITE_API_BASE_URL
```

### 3. **代码分割**

大型应用应该按路由或功能分割代码，减少首屏加载时间。

### 4. **性能监控**

使用浏览器开发者工具的 Performance 面板监控应用性能。

## 🔗 相关资源

- [Vite 官方文档](https://vitejs.dev/)
- [Vue 3 文档](https://vuejs.org/)
- [Element Plus 文档](https://element-plus.org/)
- [FastAPI 文档](https://fastapi.tiangolo.com/)

## 📄 许可证

本项目遵循项目根目录中的许可证条款。
