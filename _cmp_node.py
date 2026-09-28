import json, subprocess, sys

REL = "场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_01_需求分析.json"


def load_at(rev):
    try:
        out = subprocess.check_output(["git", "show", f"{rev}:{REL}"], stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as e:
        print("ERR", rev, e.output.decode("utf-8", "ignore")[:200])
        return None
    d = json.loads(out.decode("utf-8"))
    return d["workFlowSchemaJSON"]["nodes"]


def nodes_map(rev):
    ns = load_at(rev)
    if ns is None:
        return {}
    return {(n.get("nodeMeta", {}) or {}).get("title", "?"): n for n in ns}


revs = sys.argv[1:]
if not revs:
    revs = ["7d62829", "447dd35", "9f62132", "3a455bf", "HEAD"]
maps = [nodes_map(r) for r in revs]
titles = set()
for m in maps:
    titles |= set(m.keys())

for t in sorted(titles):
    codes = [json.dumps(m.get(t), ensure_ascii=False, sort_keys=True) for m in maps]
    if len(set(codes)) == 1:
        continue
    print("DIFF node:", t, "across", revs)
    for r, c in zip(revs, codes):
        print("   ", r, "len", len(c))
