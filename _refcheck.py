import json, io, glob

for f in sorted(glob.glob("场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_01_*.json")):
    d = json.load(io.open(f, encoding="utf-8"))
    nodes = d["workFlowSchemaJSON"]["nodes"]
    outs = {}
    for n in nodes:
        o = n.get("outputs")
        names = set()
        if isinstance(o, list):
            for x in o:
                if isinstance(x, dict) and x.get("name"):
                    names.add(x["name"])
        outs[n["id"]] = names
    issues = []

    def walk(inp, nid, ttl, where):
        if not isinstance(inp, dict):
            return
        if inp.get("type") == "ref":
            b = inp.get("blockID")
            r = inp.get("relName")
            if b and b in outs and r and r not in outs[b]:
                issues.append(f"{ttl} [{where}] ref {b[-3:]}.{r} NOT in outputs {sorted(outs[b])}")
        # condition structure
    for n in nodes:
        ttl = (n.get("nodeMeta") or {}).get("title", "?")
        ins = n.get("inputs")
        if isinstance(ins, list):
            for i in ins:
                walk(i, n["id"], ttl, "inputs")
        elif isinstance(ins, dict):
            for c in ins.get("condition", []):
                for ci in c.get("conditions", []):
                    walk(ci.get("left"), n["id"], ttl, "condition")
        for dep in n.get("dependencyData", []):
            b = dep.get("relName")
            for ch in dep.get("children", []):
                r = ch.get("relName", "").split(",")[-1]
                if b in outs and r not in outs[b]:
                    issues.append(f"{ttl} [dependency] ref {b[-3:]}.{r} NOT in outputs {sorted(outs[b])}")
    print(f, "issues:", len(issues))
    for x in issues:
        print("   ", x)
