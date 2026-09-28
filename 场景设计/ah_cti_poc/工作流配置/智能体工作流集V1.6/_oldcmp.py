import subprocess, os, io, glob

root = r"D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai"
rel = "场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/"
tmp = os.path.join(root, "_oldgen")
os.makedirs(tmp, exist_ok=True)

for f in ["gen_workflows.py", "wf_common.py"]:
    out = subprocess.run(["git", "show", "HEAD~1:" + rel + f], capture_output=True, cwd=root).stdout
    io.open(os.path.join(tmp, f), "wb").write(out)

r = subprocess.run(["python", "gen_workflows.py"], capture_output=True, cwd=tmp)
print("gen rc", r.returncode)

for f in sorted(glob.glob(os.path.join(tmp, "wf_sub_*.json"))):
    name = os.path.basename(f)
    tgt = os.path.join(root, rel + name)
    if not os.path.exists(tgt):
        print(name, "NO TARGET")
        continue
    a = io.open(f, encoding="utf-8").read()
    b = io.open(tgt, encoding="utf-8").read()
    print(name, "SAME" if a == b else "DIFF")
