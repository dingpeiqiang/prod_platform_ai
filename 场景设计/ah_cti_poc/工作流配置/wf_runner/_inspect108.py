# -*- coding: utf-8 -*-
import io, sys, re, json
sys.stdout.reconfigure(encoding="utf-8")
t = io.open("logs/scene1_01.log.md", encoding="utf-8").read()
i = t.find("## [108]")
j = t.find("## [109]")
s = t[i:j]
FENCE = "`" * 3 + "text"


def blk(label):
    k = s.find("**" + label + "**")
    a = s.find(FENCE, k) + len(FENCE)
    b = s.find("`" * 3, a)
    return s[a:b].strip()


oj = blk("offer_json")
print("offer_json len", len(oj))
print("carryFlag hits:", re.findall(r".{30}carryFlag.{30}", oj)[:5])
print("head:", oj[:400])
rg = blk("record_json")
print("record_json:", rg[:300])
th = blk("threshold")
print("threshold:", repr(th))
