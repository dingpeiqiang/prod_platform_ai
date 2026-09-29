"""同步 智能体工作流集V1.6 -> 智能体工作流集V1.6-prod

过程：
1. 删除 V1.6-prod 目录下所有 .json 文件；
2. 从 V1.6 复制目标 .json 文件到 V1.6-prod（仅 wf_sub_*.json、import_headers.json
   及中文导出 *_export.json；跳过 _ 开头的临时脚手架文件，避免其非 UTF-8 编码导致解码失败）；
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


def is_target(path: Path) -> bool:
    name = path.name
    if name.startswith("_"):
        return False
    return name == "import_headers.json" or name.startswith("wf_sub_") or name.endswith("_export.json")


def read_text(path: Path) -> str:
    # 原始字节若带 UTF-16 BOM 则按 UTF-16 解码，否则 UTF-8
    raw = path.read_bytes()
    if raw[:2] in (b"\xff\xfe", b"\xfe\xff"):
        return raw.decode("utf-16")
    return raw.decode("utf-8")


def main() -> None:
    if not SRC_DIR.is_dir():
        raise SystemExit(f"源目录不存在: {SRC_DIR}")
    DST_DIR.mkdir(parents=True, exist_ok=True)

    for old_file in DST_DIR.glob("*.json"):
        old_file.unlink()
        print(f"[删除] {old_file.name}")

    for src_file in SRC_DIR.glob("*.json"):
        if not is_target(src_file):
            continue
        content = replace_env(read_text(src_file))
        # 命名约定：子工作流落库文件加 -prod 后缀区分环境；headers/导出件保持原名
        dst_name = src_file.name
        if src_file.name.startswith("wf_sub_"):
            dst_name = src_file.stem + "-prod" + src_file.suffix
        with open(DST_DIR / dst_name, "w", encoding="utf-8", newline="") as fp:
            fp.write(content)
        print(f"[复制+替换] {dst_name}")

    print("完成。")


if __name__ == "__main__":
    main()
