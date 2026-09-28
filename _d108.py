import json, subprocess, io, difflib

REL = "场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_01_需求分析.json"
TITLE = "要素校验与方案合并"


def code_at(rev):
    out = subprocess.check_output(["git", "show", f"{rev}:{REL}"])
    d = json.loads(out.decode("utf-8"))
    for n in d["workFlowSchemaJSON"]["nodes"]:
        if (n.get("nodeMeta") or {}).get("title") == TITLE:
            return n.get("code", "")
    return ""


a = code_at("7d62829").splitlines()
b = code_at("HEAD").splitlines()
diff = list(difflib.unified_diff(a, b, "7d62829", "HEAD", lineterm="", n=2))
io.open("_d108.txt", "w", encoding="utf-8").write("\n".join(diff))
print("diff lines:", len(diff))
