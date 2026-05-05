---
name: startup_scripts
description: 前后端启动脚本路径
type: reference
---

# 启动脚本

- **后端启动**: `start-backend.bat` (位于项目根目录)
- **前端启动**: `start-frontend.bat` (位于项目根目录)
- **一键启动**: `start-all.bat` (同时启动前后端)

# 为什么记住

用户明确指定使用这两个脚本启动服务，避免后续操作被拒绝或需要反复确认。

# 如何应用

- 启动服务时直接调用这两个 .bat 脚本
- 不需要手动执行 npm run dev 或 uvicorn 等命令
- 用户偏好使用脚本方式启动