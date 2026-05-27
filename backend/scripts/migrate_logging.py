"""
批量迁移脚本 - 将项目中所有Python文件的日志导入统一替换为新的日志框架
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
    "logger.py",  # 新的日志框架本身
    "__init__.py",  # 空文件或简单文件
    "error_handler.py",  # 已有错误处理机制
]

# 旧的logging导入模式
OLD_IMPORT_PATTERNS = [
    r"^import logging$",
    r"^logger = logging\.getLogger\([\'\"](.+?)[\'\"]\)$",
    r"^logger = logging\.getLogger\(__name__\)$",
]

# 新的导入模板
NEW_IMPORTS = """from app.core.logger import get_logger

logger = get_logger(__name__)"""


def process_file(filepath):
    """处理单个文件"""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 检查是否已经使用了新的日志框架
    if 'from app.core.logger import' in content:
        print(f"  [跳过] 已使用新框架: {filepath}")
        return False
    
    # 查找旧的logging导入和logger定义
    lines = content.split('\n')
    new_lines = []
    i = 0
    while i < len(lines):
        line = lines[i]
        
        # 检查是否是 import logging 行
        if re.match(r'^\s*import logging\s*$', line):
            # 找到 import logging，需要替换
            found_import = True
            i += 1
            
            # 继续查找 logger = logging.getLogger(...) 行
            while i < len(lines):
                next_line = lines[i]
                if re.match(r'^\s*logger\s*=\s*logging\.getLogger\s*\(', next_line):
                    i += 1
                    continue
                break
            
            # 添加新的导入
            new_lines.append("from app.core.logger import get_logger")
            new_lines.append("")
            new_lines.append("logger = get_logger(__name__)")
            print(f"  [替换] 已替换日志导入")
            
        else:
            new_lines.append(line)
            i += 1
    
    # 如果有修改，写回文件
    new_content = '\n'.join(new_lines)
    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        return True
    
    return False


def main():
    """主函数"""
    print("=" * 60)
    print("批量迁移日志框架脚本")
    print("=" * 60)
    
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    print(f"基础目录: {base_dir}")
    
    total_files = 0
    modified_files = 0
    
    for target_dir in TARGET_DIRS:
        dir_path = os.path.join(base_dir, target_dir)
        if not os.path.exists(dir_path):
            print(f"\n[跳过] 目录不存在: {target_dir}")
            continue
        
        print(f"\n[处理目录] {target_dir}")
        
        for filename in os.listdir(dir_path):
            if not filename.endswith('.py'):
                continue
            
            if filename in EXCLUDE_FILES:
                print(f"  [跳过] 排除文件: {filename}")
                continue
            
            filepath = os.path.join(dir_path, filename)
            
            print(f"  [处理] {filename}")
            total_files += 1
            
            if process_file(filepath):
                modified_files += 1
    
    print("\n" + "=" * 60)
    print(f"处理完成!")
    print(f"总文件数: {total_files}")
    print(f"修改文件数: {modified_files}")
    print("=" * 60)


if __name__ == "__main__":
    main()
