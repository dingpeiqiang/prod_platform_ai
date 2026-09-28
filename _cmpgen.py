import json, subprocess, sys, os, glob

REL = "场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6"

def head_bytes(path):
    try:
        out = subprocess.check_output(
            ["git", "show", f"HEAD:{path}"], stderr=subprocess.STDOUT)
        return out
    except subprocess.CalledProcessError:
        return None

def norm(obj):
    return json.dumps(obj, ensure_ascii=False, indent=2, sort_keys=False)

def diff_lists(a, b, label):
    sa, sb = set(a), set(b)
    only_h = sa - sb
    only_g = sb - sa
    if only_h:
        print(f"    [{label}] only-in-HEAD: {sorted(only_h)}")
    if only_g:
        print(f"    [{label}] only-in-GEN : {sorted(only_g)}")

for f in sorted(glob.glob(os.path.join(REL, "wf_sub_*.json"))):
    name = os.path.basename(f)
    relpath = f.replace("\\", "/")
    hb = head_bytes(relpath)
    if hb is None:
        print(f"{name}: NEW (not in HEAD)")
        continue
    try:
        hj = json.loads(hb.decode("utf-8"))
    except Exception as e:
        print(f"{name}: HEAD decode err {e}")
        continue
    gj = json.load(open(f, encoding="utf-8"))
    hs, gs = norm(hj), norm(gj)
    if hs == gs:
        print(f"{name}: IDENTICAL")
        continue
    print(f"{name}: DIFF")
    hn = hj.get("workFlowSchemaJSON", {}).get("nodes", [])
    gn = gj.get("workFlowSchemaJSON", {}).get("nodes", [])
    hid = [str(n.get("id")) for n in hn]
    gid = [str(n.get("id")) for n in gn]
    if hid != gid:
        diff_lists(hid, gid, "nodes")
    else:
        for i, (h, g) in enumerate(zip(hn, gn)):
            if norm(h) != norm(g):
                print(f"    node[{i}] id={h.get('id')} differs")
    # top-level keys
    if set(hj.keys()) != set(gj.keys()):
        diff_lists(hj.keys(), gj.keys(), "topkeys")
    # edges
    he = hj.get("workFlowSchemaJSON", {}).get("edges", [])
    ge = gj.get("workFlowSchemaJSON", {}).get("edges", [])
    if norm(he) != norm(ge):
        print(f"    edges differ: HEAD={len(he)} GEN={len(ge)}")
