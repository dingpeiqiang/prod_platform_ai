"""同步 智能体工作流集V1.6 -> 智能体工作流集V1.6-prod

过程：
1. 删除 V1.6-prod 目录下所有 .json 文件；
2. 从 V1.6 复制全部 .json 文件到 V1.6-prod；
3. 批量替换环境地址（测试环境 -> 生产环境）。

用法：python sync_v16_to_prod.py
"""
import shutil
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
SRC_DIR = BASE_DIR / "智能体工作流集V1.6"
DST_DIR = BASE_DIR / "智能体工作流集V1.6-prod"

# 环境地址替换规则（顺序敏感：先替换更长的具体地址）
REPLACEMENTS = [
    ("http://10.86.13.201:31281", "http://172.30.8.52:6174"),
    ("http://10.86.13.201:31280", "http://szyg-stq-test.anhuitelecom.com:10001/prod-ai"),
]


def replace_env(text: str) -> str:
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)
    return text


def main() -> None:
    if not SRC_DIR.is_dir():
        raise SystemExit(f"源目录不存在: {SRC_DIR}")
    DST_DIR.mkdir(parents=True, exist_ok=True)

    for old_file in DST_DIR.glob("*.json"):
        old_file.unlink()
        print(f"[删除] {old_file.name}")

    for src_file in SRC_DIR.glob("*.json"):
        with open(src_file, "r", encoding="utf-8", newline="") as fp:
            content = fp.read()
        content = replace_env(content)
        with open(DST_DIR / src_file.name, "w", encoding="utf-8", newline="") as fp:
            fp.write(content)
        print(f"[复制+替换] {src_file.name}")

    print("完成。")


if __name__ == "__main__":
    main()
