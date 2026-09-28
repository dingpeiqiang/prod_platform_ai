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

dump = io.open(os.path.join(HERE, "_dump_out.txt"), encoding="utf-8").read()
code = dump.split("TYPE: 6\n", 1)[1]
ns = {"__name__": "__t__"}
exec(compile(code, "<n108>", "exec"), ns)

out = asyncio.new_event_loop().run_until_complete(
    ns["main"](type("A", (), {"params": {
        "schema_json": schema_json, "record_json": record_json,
        "offer_json": offer_json, "elements_json": elements_json,
        "threshold": "0.30"}})()))
payload = json.loads(out["payload"])
meta = json.loads(out["meta"])
print("carryFlag value:", payload["optionalInfo"]["billGprsCfg"]["carryFlag"])
print("carryFlag source:", meta["optionalInfo.billGprsCfg.carryFlag"]["source"])
for k in ["optionalInfo.billGprsCfg.theshold", "optionalInfo.billGprsCfg.outChargeMode",
          "optionalInfo.billVoiceCfg.theshold", "optionalInfo.printContent.prcMonthFee"]:
    print(k, "->", meta.get(k, {}).get("value"), "/", meta.get(k, {}).get("source"))
