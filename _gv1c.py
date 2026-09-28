import subprocess, re
out=subprocess.run(["git","show","7e1375db:场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/gen_workflows.py"],capture_output=True).stdout
t=out.decode("utf-8-sig")
i=t.find("def apply_layout")
j=t.find("\ndef ", i+10)
body=t[i:j]
# find constants COL_GAP ROW_GAP MAIN_Y
for c in ("COL_GAP","ROW_GAP","MAIN_Y"):
    m=re.search(c+r"\s*=\s*[^\n]+", t)
    print(c, m.group(0) if m else "NA")
open("_old_layout.txt","w",encoding="utf-8").write(body)
print("bodylen",len(body))
