---
name: 会话完成后自动提交 Git
description: 当任务完成或对话结束时，如果工作区有代码变更，自动执行 git 提交
alwaysApply: true
---

## 规则要求

1. **任务完成检测**：在每次对话结束时，检查工作区是否有未提交的代码变更
2. **自动提交**：如果有变更，自动执行以下操作：
   - `git add -A`：添加所有变更
   - `git commit -m "chore: 会话完成自动提交"`：生成规范的提交信息
   - `git push origin <当前分支>`：推送到远程仓库
3. **提交信息**：使用 Conventional Commits 规范

## 执行时机

- 当用户明确表示任务完成时（如"完成"、"结束"、"搞定"、"done"等）
- 当对话自然结束且有代码变更时
- 在回答用户前，先检查是否需要提交

## 操作指令

当检测到任务完成信号时，立即执行：

```bash
# 检查状态
git status --porcelain

# 如果有变更则提交
if [ -n "$(git status --porcelain)" ]; then
    git add -A
    git commit -m "chore: 会话完成自动提交"
    git push origin $(git rev-parse --abbrev-ref HEAD)
fi
```

## 可用命令

- `/auto_commit` - 手动触发自动提交
- `/commit [message]` - 手动提交并自定义提交信息

## 注意事项

1. 确保项目已配置 Git 用户信息：
   ```bash
   git config user.name "Your Name"
   git config user.email "your.email@example.com"
   ```

2. 确保已配置远程仓库：
   ```bash
   git remote add origin <仓库地址>
   ```
