import os
import shutil
import opencc

SRC_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "src")
BACKUP_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "backup_src")

converter = opencc.OpenCC("s2hk")

ALLOWED_EXT = (".vue", ".js", ".css")

# Clean previous backup
if os.path.exists(BACKUP_DIR):
    shutil.rmtree(BACKUP_DIR)
os.makedirs(BACKUP_DIR)

converted_files = []

for root, dirs, files in os.walk(SRC_DIR):
    for fname in files:
        if not fname.endswith(ALLOWED_EXT):
            continue
        src_path = os.path.join(root, fname)
        rel_path = os.path.relpath(src_path, SRC_DIR)

        # Backup
        backup_path = os.path.join(BACKUP_DIR, rel_path)
        os.makedirs(os.path.dirname(backup_path), exist_ok=True)
        shutil.copy2(src_path, backup_path)

        # Read original
        with open(src_path, "r", encoding="utf-8") as f:
            content = f.read()

        # Convert
        converted = converter.convert(content)

        if converted != content:
            with open(src_path, "w", encoding="utf-8") as f:
                f.write(converted)
            converted_files.append(rel_path)

print("Total files converted:", len(converted_files))
for cf in converted_files:
    print("  ", cf)
