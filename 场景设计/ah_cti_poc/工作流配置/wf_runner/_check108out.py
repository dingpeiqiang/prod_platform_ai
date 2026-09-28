# -*- coding: utf-8 -*-
import io, sys, json
sys.stdout.reconfigure(encoding="utf-8")
t = io.open("logs/scene1_01.log.md", encoding="utf-8").read()
FENCE = "`" * 3 + "text"


def seg(a, b):
    return t[t.find(a):t.find(b)]


def blk(seg_text, label):
    k = seg_text.find("**" + label + "**")
    a = seg_text.find(FENCE, k) + len(FENCE)
    b = seg_text.find("`" * 3, a)
    return seg_text[a:b].strip()


s108 = seg("## [108]", "## [109]")
payload = json.loads(blk(s108, "payload"))
meta = json.loads(blk(s108, "meta"))
print("108 payload carryFlag:", payload["optionalInfo"]["billGprsCfg"]["carryFlag"])
print("108 meta carryFlag:", meta["optionalInfo.billGprsCfg.carryFlag"])
s109 = seg("## [109]", "## [110]") if "## [110]" in t else seg("## [109]", "\n## ")
print("---- node109 output snippet ----")
print(s109[:1500])
