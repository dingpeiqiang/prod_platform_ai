# -*- coding: utf-8 -*-
import io, sys
sys.stdout.reconfigure(encoding="utf-8")
t = io.open(r"../智能体工作流集V1.6/wf_sub_01_需求分析.json", encoding="utf-8").read()
needle = "kind in ('default', 'hint_default')"
i = t.find(needle)
print("raw idx", i)
print(repr(t[i - 90:i + 300]))
