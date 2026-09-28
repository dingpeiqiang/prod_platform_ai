import subprocess, json
# Load current apply_layout from _al_view
import importlib.util
spec=importlib.util.spec_from_file_location("alv","_al_view.py")
m=importlib.util.module_from_spec(spec)
# _al_view references undefined names (start_node etc) but apply_layout only needs ROW_GAP/COL_GAP/MAIN_Y
src=open("_al_view.py",encoding="utf-8").read()
head=src[:src.find("\ns1 = []")]
g={"ROW_GAP":260,"COL_GAP":360,"MAIN_Y":300}
exec(head,g)
apply_layout=g["apply_layout"]

p="wf_sub_01_需求分析.json"
out=subprocess.run(["git","show","7e1375db:场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/"+p],capture_output=True).stdout
d=json.loads(out.decode("utf-8-sig"))
apply_layout(d)
for n in d["workFlowSchemaJSON"]["nodes"]:
    print(n["id"][-4:], n["position"])
