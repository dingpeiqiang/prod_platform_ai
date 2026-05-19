---
name: 任务完成后自动提交
description: 任务完成后自动检查并提交代码变更
alwaysApply: true
---

## 任务完成后操作

任务完成后，请执行以下操作：

1. **检查工作区**：查看是否有未提交的代码变更
2. **自动提交**：如果有变更，执行 `git add -A` → `git commit` → `git push`
3. **提交信息**：使用 Conventional Commits 格式，包含任务内容摘要
