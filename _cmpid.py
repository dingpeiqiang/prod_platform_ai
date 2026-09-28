import json, subprocess, io

REL = "场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_01_需求分析.json"


def nodes_at(rev):
    out = subprocess.check_output(["git", "show", f"{rev}:{REL}"])
    d = json.loads(out.decode("utf-8"))
    return {n["id"]: n for n in d["workFlowSchemaJSON"]["nodes"]}


old = nodes_at("068336f")
new = nodes_at("HEAD")
lines = []
for nid in sorted(set(old) | set(new)):
    a = old.get(nid, {})
    b = new.get(nid, {})
    ta = (a.get("nodeMeta") or {}).get("title", "?")
    tb = (b.get("nodeMeta") or {}).get("title", "?")
    ca = a.get("code") or ""
    cb = b.get("code") or ""
    if ca != cb:
        tag = "SHRINK" if len(cb) < len(ca) else ("GROW" if len(cb) > len(ca) else "SAME")
        lines.append(f"{tag} id=...{nid[-3:]} title {ta!r}->{tb!r} code {len(ca)}->{len(cb)}")
    # other structural diffs
    for key in ("type", "outputs", "inputs", "nodeMeta", "url", "submit_way"):
        if json.dumps(a.get(key), sort_keys=True, ensure_ascii=False) != json.dumps(b.get(key), sort_keys=True, ensure_ascii=False):
            lines.append(f"  FIELD DIFF id=...{nid[-3:]} key={key} ({ta!r})")
io.open("_cmpid.txt", "w", encoding="utf-8").write("\n".join(lines))
print("done", len(lines))
