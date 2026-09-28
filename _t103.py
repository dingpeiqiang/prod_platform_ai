import json, asyncio, io, types, os

HERE = os.path.dirname(os.path.abspath(__file__))


def load_node(name):
    src = io.open(os.path.join(HERE, name), encoding="utf-8-sig").read()
    mod = types.ModuleType(name)
    mod.Output = dict
    exec(compile(src, name, "exec"), mod.__dict__)
    return mod


n103 = load_node("_node103.py")
rec = {"result_json": json.dumps({"req_id": "PLAN1", "offers": [{"offer_seq": 1, "name": "X", "offer_id": "OF1"}]}, ensure_ascii=False)}
a = types.SimpleNamespace(params={"query_list": json.dumps([rec], ensure_ascii=False)})
r = asyncio.run(n103.main(a))
print("103 ok:", list(r.keys()), "offers=", r["offers"][:80])

n112 = load_node("_node112.py")
a = types.SimpleNamespace(params={"chat_id": "c1", "req_id": "PLAN1", "offer_id": "OF1"})
r = asyncio.run(n112.main(a))
print("112 ok, panel len", len(r["panel"]))

n106 = load_node("_node106.py")
a = types.SimpleNamespace(params={"template_id": "personMainPrc", "template_base": os.path.join(HERE, "场景设计", "ah_cti_poc", "方案", "templates")})
r = asyncio.run(n106.main(a))
print("106 source=", r["source"], "schema len", len(r["schema_json"]))
