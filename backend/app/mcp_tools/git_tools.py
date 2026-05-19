import subprocess
import os
from typing import Dict, Any, Optional
from .tool_hub import get_toolhub


@get_toolhub().register_decorator
def git_auto_commit(message: Optional[str] = None) -> Dict[str, Any]:
    """
    自动检测代码变更并提交到 Git
    
    Args:
        message: 可选的提交信息，如果不提供则自动生成
        
    Returns:
        提交结果，包含成功状态、提交信息和变更文件列表
    """
    try:
        result = subprocess.run(
            ['git', 'status', '--porcelain'],
            capture_output=True,
            text=True,
            cwd=os.getcwd()
        )
        
        if not result.stdout.strip():
            return {
                "success": True,
                "message": "工作区无变更，无需提交",
                "changed_files": [],
                "commit_hash": None
            }
        
        changed_files = [line.split()[-1] for line in result.stdout.strip().split('\n')]
        
        subprocess.run(['git', 'add', '-A'], capture_output=True)
        
        if not message:
            file_types = {
                '.py': 'Python代码',
                '.js': 'JavaScript代码',
                '.ts': 'TypeScript代码',
                '.vue': 'Vue组件',
                '.md': '文档',
                '.json': '配置文件',
                '.yml': 'YAML配置'
            }
            
            summary = []
            for f in changed_files:
                ext = os.path.splitext(f)[1]
                summary.append(f"{file_types.get(ext, '文件')}: {f}")
            
            message = f"chore: 会话完成自动提交\n\n变更文件:\n{chr(10).join(f'- {f}' for f in changed_files)}"
        
        commit_result = subprocess.run(
            ['git', 'commit', '-m', message],
            capture_output=True,
            text=True
        )
        
        if commit_result.returncode != 0:
            return {
                "success": False,
                "message": f"提交失败: {commit_result.stderr}",
                "changed_files": changed_files
            }
        
        hash_result = subprocess.run(
            ['git', 'rev-parse', 'HEAD'],
            capture_output=True,
            text=True
        )
        
        return {
            "success": True,
            "message": "自动提交成功",
            "commit_message": message,
            "commit_hash": hash_result.stdout.strip(),
            "changed_files": changed_files
        }
        
    except Exception as e:
        return {
            "success": False,
            "message": f"Git操作失败: {str(e)}",
            "changed_files": []
        }


@get_toolhub().register_decorator
def git_push(branch: str = "main") -> Dict[str, Any]:
    """
    推送到远程仓库
    
    Args:
        branch: 目标分支名称，默认为main
        
    Returns:
        推送结果
    """
    try:
        result = subprocess.run(
            ['git', 'push', 'origin', branch],
            capture_output=True,
            text=True
        )
        
        if result.returncode == 0:
            return {
                "success": True,
                "message": f"成功推送到 {branch} 分支",
                "output": result.stdout
            }
        else:
            return {
                "success": False,
                "message": f"推送失败: {result.stderr}",
                "output": result.stdout
            }
            
    except Exception as e:
        return {
            "success": False,
            "message": f"推送失败: {str(e)}"
        }


@get_toolhub().register_decorator
def git_check_status() -> Dict[str, Any]:
    """
    检查当前 Git 仓库状态
    
    Returns:
        仓库状态信息
    """
    try:
        status_result = subprocess.run(
            ['git', 'status', '--porcelain'],
            capture_output=True,
            text=True
        )
        
        branch_result = subprocess.run(
            ['git', 'rev-parse', '--abbrev-ref', 'HEAD'],
            capture_output=True,
            text=True
        )
        
        changed_files = []
        if status_result.stdout.strip():
            changed_files = [line.split()[-1] for line in status_result.stdout.strip().split('\n')]
        
        return {
            "success": True,
            "branch": branch_result.stdout.strip(),
            "has_changes": len(changed_files) > 0,
            "changed_files": changed_files
        }
        
    except Exception as e:
        return {
            "success": False,
            "message": f"检查状态失败: {str(e)}"
        }
