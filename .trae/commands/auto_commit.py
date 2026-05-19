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
    status_map = {}
    
    if stdout:
        for line in stdout.split('\n'):
            if line.strip():
                parts = line.split()
                if len(parts) >= 2:
                    status = parts[0]
                    filename = ' '.join(parts[1:])
                    changed_files.append(filename)
                    status_map[filename] = status
    
    return changed_files, status_map, None


def get_current_branch():
    """获取当前分支名"""
    stdout, stderr, code = run_command("git rev-parse --abbrev-ref HEAD")
    if code == 0 and stdout:
        return stdout.strip()
    return "main"


def get_git_diff(filename):
    """获取单个文件的 diff 信息"""
    stdout, _, _ = run_command(f"git diff --stat {filename}")
    return stdout


def analyze_changes(changed_files, status_map):
    """分析变更类型和内容"""
    file_types = {
        '.py': {'category': 'backend', 'label': 'Python后端代码'},
        '.js': {'category': 'frontend', 'label': 'JavaScript代码'},
        '.ts': {'category': 'frontend', 'label': 'TypeScript代码'},
        '.vue': {'category': 'frontend', 'label': 'Vue组件'},
        '.md': {'category': 'docs', 'label': '文档'},
        '.json': {'category': 'config', 'label': '配置文件'},
        '.yml': {'category': 'config', 'label': 'YAML配置'},
        '.sql': {'category': 'database', 'label': 'SQL脚本'},
        '.html': {'category': 'frontend', 'label': 'HTML模板'},
        '.css': {'category': 'frontend', 'label': 'CSS样式'},
        '.txt': {'category': 'docs', 'label': '文本文件'},
    }
    
    stats = {
        'added': 0,
        'modified': 0,
        'deleted': 0,
        'renamed': 0,
        'categories': {},
        'details': []
    }
    
    for filename in changed_files:
        status = status_map.get(filename, 'M')
        
        if status.startswith('A'):
            stats['added'] += 1
            change_type = '新增'
        elif status.startswith('D'):
            stats['deleted'] += 1
            change_type = '删除'
        elif status.startswith('R'):
            stats['renamed'] += 1
            change_type = '重命名'
        else:
            stats['modified'] += 1
            change_type = '修改'
        
        ext = os.path.splitext(filename)[1]
        file_info = file_types.get(ext, {'category': 'other', 'label': '其他文件'})
        category = file_info['category']
        
        if category not in stats['categories']:
            stats['categories'][category] = 0
        stats['categories'][category] += 1
        
        stats['details'].append({
            'filename': filename,
            'status': status,
            'change_type': change_type,
            'category': category,
            'label': file_info['label']
        })
    
    return stats


def generate_commit_message(changed_files, status_map):
    """根据变更文件生成详细的提交信息"""
    stats = analyze_changes(changed_files, status_map)
    
    commit_type = 'chore'
    type_emoji = '🔧'
    
    has_backend = stats['categories'].get('backend', 0) > 0
    has_frontend = stats['categories'].get('frontend', 0) > 0
    has_docs = stats['categories'].get('docs', 0) > 0
    has_config = stats['categories'].get('config', 0) > 0
    
    if stats['added'] > stats['modified'] and stats['added'] > 0:
        commit_type = 'feat'
        type_emoji = '✨'
    elif has_backend and stats['modified'] > 0:
        commit_type = 'refactor'
        type_emoji = '🔄'
    elif has_frontend and stats['modified'] > 0:
        commit_type = 'refactor'
        type_emoji = '🔄'
    elif has_docs:
        commit_type = 'docs'
        type_emoji = '📝'
    elif has_config:
        commit_type = 'chore'
        type_emoji = '🔧'
    
    summary_parts = []
    if stats['added'] > 0:
        summary_parts.append(f"新增{stats['added']}个文件")
    if stats['modified'] > 0:
        summary_parts.append(f"修改{stats['modified']}个文件")
    if stats['deleted'] > 0:
        summary_parts.append(f"删除{stats['deleted']}个文件")
    if stats['renamed'] > 0:
        summary_parts.append(f"重命名{stats['renamed']}个文件")
    
    summary = '、'.join(summary_parts)
    
    subject = f"{type_emoji} {commit_type}: {summary}"
    
    body_lines = []
    
    if stats['categories']:
        body_lines.append("## 变更分类")
        category_labels = {
            'backend': '后端代码',
            'frontend': '前端代码',
            'docs': '文档',
            'config': '配置',
            'database': '数据库',
            'other': '其他'
        }
        for cat, count in stats['categories'].items():
            body_lines.append(f"- {category_labels.get(cat, cat)}: {count}个文件")
    
    body_lines.append("\n## 变更详情")
    for detail in stats['details'][:10]:
        body_lines.append(f"- {detail['change_type']} {detail['label']}: {detail['filename']}")
    
    if len(stats['details']) > 10:
        body_lines.append(f"- ... 还有 {len(stats['details']) - 10} 个文件")
    
    body = '\n'.join(body_lines)
    
    full_message = f"{subject}\n\n{body}"
    
    return full_message


def auto_commit():
    """执行自动提交流程"""
    print("🔍 检查 Git 工作区状态...")
    
    _, stderr, code = run_command("git rev-parse --is-inside-work-tree")
    if code != 0:
        print(f"❌ 错误：当前目录不是 Git 仓库")
        if stderr:
            print(f"   {stderr}")
        return False
    
    changed_files, status_map, error = get_git_status()
    if error:
        print(f"❌ 检查状态失败: {error}")
        return False
    
    if not changed_files:
        print("ℹ️ 工作区无变更，无需提交")
        return True
    
    print(f"📝 检测到 {len(changed_files)} 个文件变更")
    for filename in changed_files[:5]:
        status = status_map.get(filename, 'M')
        if status.startswith('A'):
            print(f"   + {filename}")
        elif status.startswith('D'):
            print(f"   - {filename}")
        else:
            print(f"   ~ {filename}")
    if len(changed_files) > 5:
        print(f"   ... 还有 {len(changed_files) - 5} 个文件")
    
    print("\n📦 执行 git add -A...")
    _, stderr, code = run_command("git add -A")
    if code != 0:
        print(f"❌ git add 失败")
        if stderr:
            print(f"   {stderr}")
        return False
    
    message = generate_commit_message(changed_files, status_map)
    print("\n✏️ 生成提交信息:")
    print("-" * 50)
    print(message)
    print("-" * 50)
    
    print("\n🚀 执行 git commit...")
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
    print(f"\n📤 推送到 origin/{branch}...")
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
