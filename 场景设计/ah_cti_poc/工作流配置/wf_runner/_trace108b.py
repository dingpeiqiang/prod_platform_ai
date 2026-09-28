# -*- coding: utf-8 -*-
import io, json, os, sys, asyncio
sys.stdout.reconfigure(encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
t = io.open(os.path.join(HERE, "logs", "scene1_01.log.md"), encoding="utf-8").read()
FENCE = "`" * 3 + "text"


def seg(a, b):
    return t[t.find(a):t.find(b)]


def blk(seg_text, label):
    k = seg_text.find("**" + label + "**")
    a = seg_text.find(FENCE, k) + len(FENCE)
    b = seg_text.find("`" * 3, a)
    return seg_text[a:b].strip()


s107 = seg("## [107]", "## [108]")
s108 = seg("## [108]", "## [109]")
elements_json = blk(s107, "elements_json")
schema_json = blk(s108, "schema_json")
record_json = blk(s108, "record_json")
offer_json = blk(s108, "offer_json")
threshold = blk(s108, "threshold")
print("threshold", repr(threshold))
print("schema len", len(schema_json), "record len", len(record_json))

dump = io.open(os.path.join(HERE, "_dump_out.txt"), encoding="utf-8").read()
code = dump.split("TYPE: 6\n", 1)[1]
ns = {"__name__": "__t__"}
exec(compile(code, "<n108>", "exec"), ns)

# trace merge for carryFlag
orig_merge = ns["merge_body"]


def traced(schema, elements_map, offer_map, meta, *a, **kw):
    print("  merge elements_map carryFlag:",
          elements_map.get("optionalInfo.billGprsCfg.carryFlag"))
    r = orig_merge(schema, elements_map, offer_map, meta, *a, **kw)
    print("  merge out carryFlag:", r.get("optionalInfo", {}).get("billGprsCfg", {}).get("carryFlag"))
    return r


ns["merge_body"] = traced


class A:
    params = {"schema_json": schema_json, "record_json": record_json,
              "offer_json": offer_json, "elements_json": elements_json,
              "threshold": threshold}


out = asyncio.new_event_loop().run_until_complete(ns["main"](A()))
payload = json.loads(out["payload"])
print("FINAL carryFlag:", payload["optionalInfo"]["billGprsCfg"]["carryFlag"])
print("ve_stats:", out["ve_stats"])
