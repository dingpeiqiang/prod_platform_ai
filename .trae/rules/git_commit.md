---
name: 会话完成后自动提交 Git
description: 当任务完成时，如果工作区有代码变更，自动执行 git add、git commit 和 git push
alwaysApply: true
---

## 规则要求

1. **任务完成检查**：在每次对话结束时，检查工作区是否有未提交的代码变更
2. **自动提交**：如果有变更，自动执行以下操作：
   - `git add -A`：添加所有变更
   - `git commit -m "chore: 会话完成自动提交 - [描述任务内容]"`：生成规范的提交信息
   - `git push origin <当前分支>`：推送到远程仓库
3. **提交信息**：使用 Conventional Commits 规范，包含本次会话的任务描述

## 执行时机

- 当用户明确表示任务完成时（如"完成"、"结束"、"搞定"等）
- 当对话自然结束且有代码变更时