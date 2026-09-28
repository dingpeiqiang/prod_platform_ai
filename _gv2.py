import subprocess
out=subprocess.run(["git","show","7e1375db:场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/gen_workflows_v2.py"],capture_output=True).stdout
t=out.decode("utf-8-sig")
print("LEN",len(t))
i=t.find("apply_layout")
print("idx",i)
print(t[:200])
