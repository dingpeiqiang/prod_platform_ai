# -*- coding: utf-8 -*-
import io, sys
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


s107 = seg("## [107]", "## [108]")
s108 = seg("## [108]", "## [109]")
o107 = blk(s107, "elements_json")
i108 = blk(s108, "elements_json")
print("node107 output == node108 input ?", o107 == i108)
print("len", len(o107), len(i108))
if o107 != i108:
    for idx, (x, y) in enumerate(zip(o107, i108)):
        if x != y:
            print("first diff at", idx)
            print("107:", repr(o107[max(0, idx - 60):idx + 60]))
            print("108:", repr(i108[max(0, idx - 60):idx + 60]))
            break
