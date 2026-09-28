import json, asyncio, io, types, os

HERE = os.path.dirname(os.path.abspath(__file__))
TMPL = os.path.join(HERE, "场景设计", "ah_cti_poc", "方案", "templates")


def load_node(name):
    src = io.open(os.path.join(HERE, name), encoding="utf-8-sig").read()
    mod = types.ModuleType(name)
    mod.Output = dict
    exec(compile(src, name, "exec"), mod.__dict__)
    return mod


schema = json.load(io.open(os.path.join(TMPL, "personMainPrc.schema.json"), encoding="utf-8"))
schema_json = json.dumps(schema, ensure_ascii=False)

n108 = load_node("_node108.py")
llm_out = [
    {"offer_seq": 1, "template_id": "personMainPrc",
     "elements": {"baseInfo": {"prodPrcName": "A套餐100元"},
                  "optionalInfo": {"printContent": {"prcMonthFee": 100}}}},
]
args = types.SimpleNamespace(params={
    "schema_json": schema_json,
    "elements_json": json.dumps(llm_out, ensure_ascii=False),
    "offer_json": "{}", "offer_group": "", "threshold": "0.30",
    "record_json": "{}",
})
r108 = asyncio.run(n108.main(args))
print("108 ok, offers len:", len(r108["offers"]))

n109 = load_node("_node109.py")
a109 = types.SimpleNamespace(params={
    "schema_json": schema_json,
    "offers_json": r108["offers"],
    "similar_offer": "{}",
    "title": "产销品配置方案（模板轨）",
})
r109 = asyncio.run(n109.main(a109))
print("109 ok, table_text len:", len(r109.get("table_text", "")))

n114 = load_node("_node114.py")
a114 = types.SimpleNamespace(params={
    "results": r108["offers"],
    "req_id": "PLAN20260101000000123",
    "template": "personMainPrc",
})
r114 = asyncio.run(n114.main(a114))
print("114 ok, result_json len:", len(r114["result_json"]))
