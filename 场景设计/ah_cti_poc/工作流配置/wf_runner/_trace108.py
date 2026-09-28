# -*- coding: utf-8 -*-
import io, json, os, sys, asyncio
sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
LOG = os.path.join(HERE, "logs", "scene1_01.log.md")
t = io.open(LOG, encoding="utf-8").read()


def seg_between(a, b):
    i = t.find(a)
    j = t.find(b)
    return t[i:j]


def last_block(text, label):
    # take the LAST ```text block after the label occurrence
    k = text.rfind(label)
    s = text.find("```text", k) + len("```text")
    e = text.find("```", s)
    return text[s:e].strip()


s107 = seg_between("## [107]", "## [108]")
s108 = seg_between("## [108]", "## [109]")
elements_json = last_block(s107, "**elements_json**")
schema_json = last_block(s108, "**schema_json**")
record_json = last_block(s108, "**record_json**")
offer_json = last_block(s108, "**offer_json**")

dump = io.open(os.path.join(HERE, "_dump_out.txt"), encoding="utf-8").read()
code = dump.split("TYPE: 6\n", 1)[1]
ns = {"__name__": "__t__"}
exec(compile(code, "<n108>", "exec"), ns)

leaves = ns["_collect_leaves"](ns["_load"](schema_json))
elems_list = ns["_load_offers"](elements_json)
print("== elems_list len", len(elems_list))
elems = elems_list[0]
for w in ("elements", "elements_json", "payload", "fields"):
    if isinstance(elems, dict) and w in elems:
        print("== unwrap via", w)
        elems = ns["_load"](elems[w]) if isinstance(elems[w], str) else elems[w]
        break
flat = ns["_flatten"](elems)
print("== flat keys:", sorted(flat.keys()))
p = "optionalInfo.billGprsCfg.carryFlag"
print("== flat carryFlag:", flat.get(p))
print("== leaf exists:", p in leaves, leaves.get(p, {}).get("default"))
em = ns["flatten_elements"](elems)
print("== elements_map carryFlag:", em.get(p))
sm = ns["relabel_source_map"](em, leaves, record_json)
print("== after relabel elements_map carryFlag:", em.get(p), "source:", sm.get(p))
offer = ns["unwrap_offer"](ns["_load"](offer_json or ""))
tid = ns["_load"](schema_json).get("x-template", "")
if isinstance(offer, dict) and tid in offer:
    offer = offer[tid]
om = ns["flatten_offer"](offer)
print("== offer_map carryFlag:", om.get(p))

out = asyncio.new_event_loop().run_until_complete(
    ns["main"](type("A", (), {"params": {"schema_json": schema_json, "record_json": record_json,
                                         "offer_json": offer_json, "elements_json": elements_json,
                                         "threshold": "0.30"}})()))
payload = json.loads(out["payload"])
meta = json.loads(out["meta"])
print("== FINAL carryFlag:", payload["optionalInfo"]["billGprsCfg"]["carryFlag"])
print("== FINAL meta carryFlag:", meta.get(p))
print("== ve_stats:", out["ve_stats"])
print("== ve_result:", out["ve_result"], "pending:", out["pending_required"])
