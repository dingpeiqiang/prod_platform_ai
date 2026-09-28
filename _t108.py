import json, asyncio, io, types, sys, os

HERE = os.path.dirname(os.path.abspath(__file__))
CODE = os.path.join(HERE, "_node108.py")
TMPL = os.path.join(HERE, "场景设计", "ah_cti_poc", "方案", "templates")

src = io.open(CODE, encoding="utf-8-sig").read()
mod = types.ModuleType("node108")
mod.Output = dict
exec(compile(src, CODE, "exec"), mod.__dict__)

schema = json.load(io.open(os.path.join(TMPL, "personMainPrc.schema.json"), encoding="utf-8"))
print("schema template:", schema.get("x-template"))

# LLM 逐品输出（两品，第二品故意用不同价格）
llm_out = [
    {"offer_seq": 1, "template_id": "personMainPrc",
     "elements": {"baseInfo": {"prodPrcName": "A套餐100元"},
                  "optionalInfo": {"printContent": {"prcMonthFee": 100}}}},
    {"offer_seq": 2, "template_id": "personMainPrc",
     "elements": {"baseInfo": {"prodPrcName": "B套餐200元"},
                  "optionalInfo": {"printContent": {"prcMonthFee": 200}}}},
]
elements_json = json.dumps(llm_out, ensure_ascii=False)

# 相似产品兜底报文（值应为 300，用于检测是否串值/回退）
offer_json = json.dumps({"personMainPrc": {"baseInfo": {"prodPrcName": "相似品"},
                                           "optionalInfo": {"printContent": {"prcMonthFee": 300}}}},
                        ensure_ascii=False)

args = types.SimpleNamespace(params={
    "schema_json": json.dumps(schema, ensure_ascii=False),
    "elements_json": elements_json,
    "offer_json": offer_json,
    "offer_group": "",
    "threshold": "0.30",
    "record_json": json.dumps({"offers": [{"offer_seq": 1}, {"offer_seq": 2}]}, ensure_ascii=False),
})

ret = asyncio.run(mod.main(args))
offers = json.loads(ret["offers"])
for o in offers:
    body = json.loads(o["payload"])
    name = body.get("baseInfo", {}).get("prodPrcName")
    fee = body.get("optionalInfo", {}).get("printContent", {}).get("prcMonthFee")
    print("offer_seq", o["offer_seq"], "name=", name, "fee=", fee, "gate=", o["quality_gate"])
