---
name: startup_method
description: 前后端启动方式
type: reference
---

# 启动方式

## 后端启动（使用 bat 脚本）
```bash
start-backend.bat
```

## 前端启动（使用 bat 脚本）
```bash
start-frontend.bat
```

## 不使用 start-all.bat（有问题）

# 为什么记住

- 用户明确要求使用 bat 脚本分开启动
- 不使用 start-all.bat（有问题）

# 如何应用

1. 用户说"启动前后端" → 分开执行两个 bat 脚本
2. 用户说"启动后端" → 执行 start-backend.bat
3. 用户说"启动前端" → 执行 start-frontend.bat