# -*- coding: utf-8 -*-
import glob, io, json, os, sys

WF_DIR = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                       "..", "智能体工作流集V1.6"))

for pat in sys.argv[1:] or ["wf_sub_*"]:
    for f in sorted(glob.glob(os.path.join(WF_DIR, pat + ".json"))):
        if f.endswith("_export.json"):
            continue
        obj = json.load(io.open(f, encoding="utf-8"))
        schema = obj.get("workFlowSchemaJSON") or obj.get("workFlowSchema") or {}
        if isinstance(schema, str):
            schema = json.loads(schema)
        start = [n for n in schema.get("nodes", []) if n.get("type") == 0]
        if not start:
            continue
        ins = start[0].get("inputs", []) or []
        print("#", os.path.basename(f), obj.get("flowName", ""))
        for ip in ins:
            print("   -", ip.get("name"), "|", ip.get("description", ""))
