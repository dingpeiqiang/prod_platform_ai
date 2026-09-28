# -*- coding: utf-8 -*-
import io, sys, os, glob
sys.stdout.reconfigure(encoding="utf-8")

WF_DIR = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                       "..", "智能体工作流集V1.6"))
SRC = glob.glob(os.path.join(WF_DIR, "wf_sub_01_需求分析.json"))[0]

OLD = (r"        kind, sv = matched\n"
       r"        if kind in ('default', 'hint_default'):\n"
       r"            elements_map[path] = sv\n"
       r"            source_map[path] = DEFAULT_SOURCE\n"
       r"        else:\n"
       r"            source_map[path] = TEMPLATE_SOURCE\n"
       r"    return source_map")

NEW = (r"        kind, sv = matched\n"
       r"        if kind in ('default', 'hint_default'):\n"
       r"            # 该要素已由 LLM 抽取器产出于 elements_map：即便取值与 schema 默认一致，\n"
       r"            # 亦属模型/需求驱动，不降级为「默认值」以免掩盖真实溯源\n"
       r"            # （如「是否结转到次月」= 是 恰与默认值相同，但源自需求「可结转至次月」）。\n"
       r"            source_map[path] = MODEL_GUESS_SOURCE\n"
       r"        else:\n"
       r"            source_map[path] = TEMPLATE_SOURCE\n"
       r"    return source_map")

raw = io.open(SRC, encoding="utf-8").read()
cnt = raw.count(OLD)
if cnt != 1:
    print("!! expected exactly 1 occurrence, got", cnt)
    sys.exit(1)
raw = raw.replace(OLD, NEW, 1)
io.open(SRC, "w", encoding="utf-8", newline="").write(raw)
print("patched", os.path.basename(SRC), "len", len(raw))

# verify it still parses and the code changed
import json
obj = json.loads(io.open(SRC, encoding="utf-8").read())
s = obj["workFlowSchemaJSON"]
schema = json.loads(s) if isinstance(s, str) else s
for n in schema["nodes"]:
    if str(n.get("id", "")).endswith("108"):
        c = n["code"]
        print("DEFAULT_SOURCE still present:", "source_map[path] = DEFAULT_SOURCE" in c)
        print("MODEL_GUESS applied at default branch:",
              "亦属模型/需求驱动，不降级为「默认值」" in c)
        print("elements_map[path] = sv removed:", "elements_map[path] = sv" not in c)
