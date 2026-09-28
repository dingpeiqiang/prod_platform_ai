import json, subprocess, io

REL = "场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_01_需求分析.json"


def dump(rev):
    out = subprocess.check_output(["git", "show", f"{rev}:{REL}"])
    d = json.loads(out.decode("utf-8"))
    lines = []
    for n in d["workFlowSchemaJSON"]["nodes"]:
        lines.append(f"id=...{n['id'][-3:]} type={n.get('type')} title={(n.get('nodeMeta') or {}).get('title')}")
    lines.append("EDGES:")
    for e in d["workFlowSchemaJSON"]["edges"]:
        lines.append(f"  {e['startId'][-3:]}({e.get('sourcePort')}) -> {e['endId'][-3:]}")
    return "\n".join(lines)


txt = []
for rev in ("068336f", "HEAD"):
    txt.append("=" * 30 + " " + rev + " " + "=" * 30)
    txt.append(dump(rev))
io.open("_dump2.txt", "w", encoding="utf-8").write("\n".join(txt))
print("ok")
