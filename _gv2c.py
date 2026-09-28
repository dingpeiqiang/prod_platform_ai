import subprocess
out=subprocess.run(["git","show","7e1375db:场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/gen_workflows_v2.py"],capture_output=True).stdout
t=out.decode("utf-8-sig")
lines=t.split("\n")
for n,l in enumerate(lines):
    if "position" in l or "def " in l and ("layout" in l or "pos" in l):
        print(n, l[:110])
