import subprocess, json
p = "场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_01_需求分析.json"
out = subprocess.run(["git","show","7e1375db:"+p],capture_output=True).stdout
d = json.loads(out.decode("utf-8-sig"))
for e in d["workFlowSchemaJSON"]["edges"]:
    print(json.dumps(e, ensure_ascii=False))
