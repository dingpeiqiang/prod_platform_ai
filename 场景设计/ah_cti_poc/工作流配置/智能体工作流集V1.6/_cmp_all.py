import glob, io, json, os, subprocess

REL = "."
for f in sorted(glob.glob(os.path.join(REL, "wf_sub_*.json"))):
    name = os.path.basename(f)
    rel = f.replace("\\", "/").lstrip("./")
    rel = "场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/" + name
    out = subprocess.run(["git", "show", "HEAD:" + rel], capture_output=True).stdout
    if not out:
        print(name, "NEW")
        continue
    h = out.decode("utf-8")
    g = io.open(f, encoding="utf-8").read()
    print(name, "IDENTICAL" if h == g else "DIFF")
