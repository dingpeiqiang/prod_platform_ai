import json, io, glob

ALLOWED = {0, 1, 2, 3, 6, 9, 13}
for f in sorted(glob.glob("场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_01_*.json")):
    d = json.load(io.open(f, encoding="utf-8"))
    nodes = d["workFlowSchemaJSON"]["nodes"]
    for n in nodes:
        ttl = (n.get("nodeMeta") or {}).get("title", "?")
        t = n.get("type")
        if n.get("checkErr") is True:
            print("checkErr TRUE:", ttl)
        if t not in ALLOWED:
            print("BAD TYPE", ttl, t)
        if t in (1, 2, 3, 6, 9):
            if "outputs" not in n:
                print("NO OUTPUTS", ttl, t)
        if t == 9:
            c = (n.get("outputs") or {}).get("content", "")
            if "{" not in c:
                print("END no placeholder", ttl)
        # start node chat_id required
        if t == 0:
            for i in n.get("inputs", []):
                if i.get("name") == "chat_id" and i.get("required"):
                    print("start chat_id required=True (doc G-8 says false)")
    print("scanned", f, "nodes", len(nodes))
