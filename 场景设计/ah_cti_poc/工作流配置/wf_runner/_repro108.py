# -*- coding: utf-8 -*-
import io, json, re, sys, os

HERE = os.path.dirname(os.path.abspath(__file__))
LOG = os.path.join(HERE, "logs", "scene1_01.log.md")
t = io.open(LOG, encoding="utf-8").read()


def seg_between(a, b):
    i = t.find(a)
    j = t.find(b)
    return t[i:j]


def block(text, marker):
    k = text.find(marker)
    s = text.find("```text", k) + len("```text")
    e = text.find("```", s)
    return text[s:e].strip()


s107 = seg_between("## [107]", "## [108]")
elements_json = block(s107, "**elements_json**")

s108 = seg_between("## [108]", "## [109]")
schema_json = block(s108, "**schema_json**")
record_json = block(s108, "**record_json**")
offer_json = block(s108, "**offer_json**")

print("=== schema_json first 60:", schema_json[:60])
print("=== record_json:", record_json[:200])
print("=== offer_json first 60:", offer_json[:60])
print("=== elements_json:", elements_json)

# exec node 108 code
dump = io.open(os.path.join(HERE, "_dump_out.txt"), encoding="utf-8").read()
code = dump.split("TYPE: 6\n", 1)[1]
ns = {"__name__": "__t__"}
exec(compile(code, "<n108>", "exec"), ns)

import asyncio


class A:
    params = {"schema_json": schema_json, "record_json": record_json,
              "offer_json": offer_json, "elements_json": elements_json,
              "threshold": "0.30"}


out = asyncio.get_event_loop().run_until_complete(ns["main"](A()))
payload = json.loads(out["payload"])
print("=== carryFlag in merged:", payload["optionalInfo"]["billGprsCfg"].get("carryFlag"))
meta = json.loads(out["meta"])
print("=== meta carryFlag:", meta.get("optionalInfo.billGprsCfg.carryFlag"))
print("=== pending:", out["pending_required"])
