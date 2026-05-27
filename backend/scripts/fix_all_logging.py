"""
全面修复脚本 - 修复所有文件中遗漏的 logging 问题
"""

import os
import re

# 需要处理的目录
TARGET_DIRS = [
    "app/services",
    "app/api",
    "app/engine",
    "app/langchain",
    "app/mcp_tools",
    "app/intent",
    "app/harness",
    "app/core",
]

# 排除的文件
EXCLUDE_FILES = [
    "logger.py",
    "__init__.py",
    "error_handler.py",
]


def process_file(filepath):
    """处理单个文件"""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 检查是否已经使用了新的日志框架
    if 'from app.core.logger import get_logger' not in content:
        return False
    
    lines = content.split('\n')
    new_lines = []
    modified = False
    has_new_logger = False
    
    for i, line in enumerate(lines):
        # 检查是否已经有 logger = get_logger(__name__)
        if 'logger = get_logger(__name__)' in line:
            has_new_logger = True
        
        # 查找 logging.getLogger 调用（不在注释中）
        if re.match(r'^\s*logger\s*=\s*logging\.getLogger\s*\(', line):
            # 如果已经有新的 logger，删除旧的
            if has_new_logger:
                modified = True
                print(f"  [删除] {filepath}:{i+1} - {line.strip()}")
                continue
        
        new_lines.append(line)
    
    # 如果有修改，写回文件
    new_content = '\n'.join(new_lines)
    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    
    return False


def find_problem_files():
    """查找有问题的文件"""
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    problem_files = []
    
    for target_dir in TARGET_DIRS:
        dir_path = os.path.join(base_dir, target_dir)
        if not os.path.exists(dir_path):
            continue
        
        for filename in os.listdir(dir_path):
            if not filename.endswith('.py'):
                continue
            
            if filename in EXCLUDE_FILES:
                continue
            
            filepath = os.path.join(dir_path, filename)
            
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # 有 get_logger 但仍有 logging.getLogger
            if 'from app.core.logger import get_logger' in content and 'logging.getLogger' in content:
                problem_files.append(filepath)
    
    return problem_files


def main():
    """主函数"""
    print("=" * 60)
    print("全面修复日志框架问题")
    print("=" * 60)
    
    problem_files = find_problem_files()
    print(f"发现 {len(problem_files)} 个需要修复的文件:")
    
    for filepath in problem_files:
        print(f"  {filepath}")
    
    print("\n[开始修复...]")
    modified_count = 0
    
    for filepath in problem_files:
        print(f"\n[处理] {os.path.basename(filepath)}")
        if process_file(filepath):
            modified_count += 1
    
    print("\n" + "=" * 60)
    print(f"修复完成!")
    print(f"处理文件数: {len(problem_files)}")
    print(f"修改文件数: {modified_count}")
    print("=" * 60)


if __name__ == "__main__":
    main()
