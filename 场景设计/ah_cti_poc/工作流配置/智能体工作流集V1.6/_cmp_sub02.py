import json, io, subprocess, os

gen = {}


def run_gen():
    subprocess.run(["python", "gen_workflows.py"], capture_output=True)


def load(path):
    with io.open(path, encoding="utf-8") as f:
        return json.load(f)


# snapshot target before regeneration
target = load("wf_sub_02_智能配置.json")
run_gen()
after = load("wf_sub_02_智能配置.json")


def norm(o):
    if isinstance(o, dict):
        return {k: norm(v) for k, v in o.items()}
    if isinstance(o, list):
        return [norm(v) for v in o]
    return o


print("TARGET == REGEN:", norm(target) == norm(after))
print("whole equal:", norm(target) == norm(after))
te = target["workFlowSchemaJSON"]["edges"]
ae = after["workFlowSchemaJSON"]["edges"]
print("edges equal:", norm(te) == norm(ae), len(te), len(ae))
for k in target:
    if target.get(k) != after.get(k):
        print("TOPKEY DIFF", k, repr(target.get(k))[:200], "||", repr(after.get(k))[:200])

tn = {n["id"]: n for n in target["workFlowSchemaJSON"]["nodes"]}
an = {n["id"]: n for n in after["workFlowSchemaJSON"]["nodes"]}
print("node id sets equal:", set(tn) == set(an))
for nid in sorted(set(tn) | set(an)):
    t = tn.get(nid)
    a = an.get(nid)
    if t != a:
        tk = set(t or {})
        ak = set(a or {})
        print("NODE DIFF", nid, "keys:", tk ^ ak)
        for k in tk & ak:
            if t[k] != a[k]:
                ts = json.dumps(t[k], ensure_ascii=False)
                as_ = json.dumps(a[k], ensure_ascii=False)
                print("  field", k)
                print("   T:", ts[:400])
                print("   A:", as_[:400])
