import subprocess, json
p = "wf_sub_01_需求分析.json"
cur = json.load(open(p, encoding="utf-8"))
out = subprocess.run(["git","show","7e1375db:"+"场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/"+p],capture_output=True).stdout
ref = json.loads(out.decode("utf-8-sig"))
def norm(d):
    d=json.loads(json.dumps(d))
    for n in d["workFlowSchemaJSON"]["nodes"]:
        n.pop("position",None)
    return d
print("equal ignoring positions:", json.dumps(norm(cur),ensure_ascii=False)==json.dumps(norm(ref),ensure_ascii=False))
# position diffs
a={n["id"]:n.get("position") for n in cur["workFlowSchemaJSON"]["nodes"]}
b={n["id"]:n.get("position") for n in ref["workFlowSchemaJSON"]["nodes"]}
for k in b:
    if a.get(k)!=b.get(k):
        print("DIFF", k[-4:], "cur",a.get(k),"ref",b.get(k))
