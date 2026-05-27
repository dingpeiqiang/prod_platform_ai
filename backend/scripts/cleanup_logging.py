"""
清理脚本 - 删除重复的 logging.getLogger() 调用
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
    
    for i, line in enumerate(lines):
        # 查找重复的 logging.getLogger() 调用（在已经有 get_logger 的情况下）
        if re.match(r'^\s*logger\s*=\s*logging\.getLogger\s*\(', line):
            # 检查前面是否已经有 logger = get_logger(__name__)
            has_new_logger = False
            for j in range(max(0, i-10), i):
                if 'logger = get_logger(__name__)' in lines[j]:
                    has_new_logger = True
                    break
            
            if has_new_logger:
                # 删除重复的 logging.getLogger() 行
                modified = True
                print(f"  [删除] 第{i+1}行: {line.strip()}")
                continue
        
        new_lines.append(line)
    
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
    print("清理重复的 logging.getLogger() 调用")
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
                continue
            
            filepath = os.path.join(dir_path, filename)
            
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
