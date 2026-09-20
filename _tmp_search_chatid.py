# -*- coding: utf-8 -*-
import io, sys, glob, re
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

files = (
    sorted(glob.glob(r"场景设计/ah_cti_poc/工作流配置/**/*.py", recursive=True))
    + sorted(glob.glob(r"场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/wf_sub_*.json"))
)
pat = re.compile(r"chat_id\s*=")
for p in files:
    try:
        txt = open(p, encoding="utf-8").read()
    except Exception as e:
        print("ERR", p, e)
        continue
    for m in pat.finditer(txt):
        seg = txt[m.start() : m.start() + 90].replace("\n", " ")
        print(p.split("工作流配置")[-1], "|", seg)
