# -*- coding: utf-8 -*-
import glob
import io, json, os, sys

WF_DIR = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                       "..", "智能体工作流集V1.6"))


def resolve(prefix):
    if os.path.isfile(prefix):
        return prefix
    for f in glob.glob(os.path.join(WF_DIR, prefix + "*.json")):
        if not f.endswith("_export.json"):
            return f
    raise SystemExit("not found: " + prefix)


def load_schema(path):
    obj = json.load(io.open(path, encoding="utf-8"))
    s = obj.get("workFlowSchemaJSON") or obj.get("workFlowSchema")
    return json.loads(s) if isinstance(s, str) else s


if __name__ == "__main__":
    path = resolve(sys.argv[1])
    node_id = sys.argv[2]
    schema = load_schema(path)
    lines = []
    for n in schema.get("nodes", []):
        if str(n.get("id")) == str(node_id) or str(n.get("id", "")).endswith(str(node_id)):
            lines.append("TITLE: " + str((n.get("nodeMeta") or {}).get("title")))
            lines.append("TYPE: " + str(n.get("type")))
            src = n.get("code")
            lines.append(src if src else json.dumps(n, ensure_ascii=False, indent=2))
    out_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_dump_out.txt")
    io.open(out_path, "w", encoding="utf-8").write("\n".join(lines))
    print("written to", out_path, "len", sum(len(x) for x in lines))
