import os
import opencc

BASE = os.path.dirname(os.path.abspath(__file__))
SRC_DIR = os.path.join(BASE, "src")
ALLOWED_EXT = (".vue", ".js", ".css")

converter = opencc.OpenCC("t2s")

converted_files = []

for root, dirs, files in os.walk(SRC_DIR):
    for fname in files:
        if not fname.endswith(ALLOWED_EXT):
            continue
        path = os.path.join(root, fname)
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        converted = converter.convert(content)
        if converted != content:
            with open(path, "w", encoding="utf-8") as f:
                f.write(converted)
            converted_files.append(os.path.relpath(path, SRC_DIR))

print("Total files converted:", len(converted_files))
for cf in converted_files:
    print("  ", cf)
