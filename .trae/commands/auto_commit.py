#!/usr/bin/env python3
"""
自动提交脚本 - 在会话结束时自动检测代码变更并提交

使用方法：
1. 在 Trae 对话中输入: /auto_commit
2. 或作为会话结束钩子自动执行

功能：
- 检测工作区是否有未提交的变更
- 如果有变更，自动执行 git add → git commit → git push
- 自动生成规范的 Conventional Commits 格式提交信息
"""

import subprocess
import os
import sys


def run_command(cmd, cwd=None):
    """执行命令并返回结果"""
    try:
        result = subprocess.run(
            cmd,
            shell=True,
            capture_output=True,
            text=True,
            cwd=cwd or os.getcwd(),
            encoding='utf-8',
            errors='ignore'
        )
        return result.stdout.strip(), result.stderr.strip(), result.returncode
    except Exception as e:
        return "", str(e), -1


def get_git_status():
    """获取 Git 状态"""
    stdout, stderr, code = run_command("git status --porcelain")
    if code != 0:
        return None, stderr
    changed_files = []
    if stdout:
        for line in stdout.split('\n'):
            if line.strip():
                parts = line.split()
                if len(parts) >= 2:
                    changed_files.append(' '.join(parts[1:]))
    return changed_files, None


def get_current_branch():
    """获取当前分支名"""
    stdout, stderr, code = run_command("git rev-parse --abbrev-ref HEAD")
    if code == 0 and stdout:
        return stdout.strip()
    return "main"


def generate_commit_message(changed_files):
    """根据变更文件生成提交信息"""
    file_types = {
        '.py': 'Python代码',
        '.js': 'JavaScript代码',
        '.ts': 'TypeScript代码',
        '.vue': 'Vue组件',
        '.md': '文档',
        '.json': '配置文件',
        '.yml': 'YAML配置',
        '.sql': 'SQL脚本',
        '.html': 'HTML文件',
        '.css': 'CSS样式',
    }
    
    summary = []
    for f in changed_files[:5]:
        ext = os.path.splitext(f)[1]
        summary.append(f"- {file_types.get(ext, '文件')}: {f}")
    
    if len(changed_files) > 5:
        summary.append(f"- 还有 {len(changed_files) - 5} 个文件...")
    
    message = f"chore: 会话完成自动提交\n\n变更文件:\n{chr(10).join(summary)}"
    return message


def auto_commit():
    """执行自动提交流程"""
    print("🔍 检查 Git 工作区状态...")
    
    _, stderr, code = run_command("git rev-parse --is-inside-work-tree")
    if code != 0:
        print(f"❌ 错误：当前目录不是 Git 仓库")
        if stderr:
            print(f"   {stderr}")
        return False
    
    changed_files, error = get_git_status()
    if error:
        print(f"❌ 检查状态失败: {error}")
        return False
    
    if not changed_files:
        print("ℹ️ 工作区无变更，无需提交")
        return True
    
    print(f"📝 检测到 {len(changed_files)} 个文件变更")
    
    print("📦 执行 git add -A...")
    _, stderr, code = run_command("git add -A")
    if code != 0:
        print(f"❌ git add 失败")
        if stderr:
            print(f"   {stderr}")
        return False
    
    message = generate_commit_message(changed_files)
    print(f"✏️ 生成提交信息: {message.splitlines()[0]}")
    
    print("🚀 执行 git commit...")
    cmd = f'git commit -m "{message}"'
    _, stderr, code = run_command(cmd)
    if code != 0:
        print(f"❌ git commit 失败")
        if stderr:
            print(f"   {stderr}")
        return False
    
    stdout, _, _ = run_command("git rev-parse HEAD")
    commit_hash = stdout[:7] if stdout else "unknown"
    print(f"✅ 提交成功! 哈希: {commit_hash}")
    
    branch = get_current_branch()
    print(f"📤 推送到 origin/{branch}...")
    _, stderr, code = run_command(f"git push origin {branch}")
    if code != 0:
        print(f"⚠️ git push 失败（可能需要手动推送）")
        if stderr:
            print(f"   {stderr}")
        return True
    
    print("✅ 推送成功!")
    return True


if __name__ == "__main__":
    custom_message = None
    if len(sys.argv) > 1:
        custom_message = " ".join(sys.argv[1:])
    
    success = auto_commit()
    sys.exit(0 if success else 1)
