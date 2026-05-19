---
name: commit
description: 自动检测代码变更并提交到 Git
usage: /commit [message]
parameters:
  - name: message
    type: string
    optional: true
    description: 提交信息，不提供则自动生成
---

## 命令功能

自动执行以下操作：
1. 检查工作区状态
2. 如果有变更，执行 `git add -A`
3. 生成规范的提交信息
4. 执行 `git commit`
5. 推送到远程仓库

## 使用示例

```
/commit                    # 自动生成提交信息
/commit "修复登录功能"       # 使用自定义提交信息
```

## 实现逻辑

```bash
# 检查状态
git status --porcelain

# 如果有变更
if [ -n "$(git status --porcelain)" ]; then
    git add -A
    
    # 生成提交信息
    if [ -z "$1" ]; then
        # 自动生成基于变更内容的提交信息
        CHANGED_FILES=$(git status --porcelain | awk '{print $2}')
        MESSAGE="chore: 会话完成自动提交\n\n变更文件:\n$CHANGED_FILES"
    else
        MESSAGE="$1"
    fi
    
    git commit -m "$MESSAGE"
    git push origin $(git rev-parse --abbrev-ref HEAD)
    echo "✅ 提交成功!"
else
    echo "ℹ️ 工作区无变更"
fi
```
