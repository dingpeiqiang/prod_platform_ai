# -*- coding: utf-8 -*-
"""在生成器 gen_workflows.py（工作流 JSON 唯一真源）中修复节点108价格补全逻辑。

问题：CODE_EXTRACT_MERGE 的 merge_body 用 `not is_price(label,key)` 一刀切禁止价格叶子
走相似产品补全，导致 phoneMbrInfo.phoneMbrFee 等成员费用即便相似产品有值也留空。
修复：仅保护「套餐月费/套餐固定费」两条主价格路径，其余价格叶子放开补全。
"""
import io, os, sys
sys.stdout.reconfigure(encoding="utf-8")

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "智能体工作流集V1.6", "gen_workflows.py")

raw = io.open(SRC, encoding="utf-8").read()

# 生成器源码中：换行为 \\n 字面量、单引号为 \\' 转义
OLD_CONST = ("SAME_PARAMETER_PAIRS = (\\n"
             "    (\\'optionalInfo.printContent.prcMonthFee\\', \\'optionalInfo.acctMonth.fixFee\\', \\'套餐月费\\', \\'套餐固定费\\'),\\n"
             ")")
NEW_CONST = (OLD_CONST + "\\n"
             "# 主套餐价格叶子：不从相似产品继承月费/固定费（避免旧品资费串值）；\\n"
             "# 其余价格叶子（如成员费用 phoneMbrFee）允许用相似产品实例参数补全。\\n"
             "PACKAGE_FEE_PATHS = (\\'optionalInfo.printContent.prcMonthFee\\', \\'optionalInfo.acctMonth.fixFee\\')")

OLD_GUARD = ("if val is None and not is_price(label, key):\\n"
             "            oval = offer_map.get(cur)\\n"
             "            if oval is not None:\\n"
             "                val, source = oval, \\'AI补全\\'")
NEW_GUARD = ("if val is None and (not is_price(label, key) or cur not in PACKAGE_FEE_PATHS):\\n"
             "            oval = offer_map.get(cur)\\n"
             "            if oval is not None:\\n"
             "                val, source = oval, \\'AI补全\\'")

assert raw.count(OLD_CONST) == 1, ("const", raw.count(OLD_CONST))
assert raw.count(OLD_GUARD) == 1, ("guard", raw.count(OLD_GUARD))

raw = raw.replace(OLD_CONST, NEW_CONST, 1).replace(OLD_GUARD, NEW_GUARD, 1)
io.open(SRC, "w", encoding="utf-8", newline="").write(raw)

code = io.open(SRC, encoding="utf-8").read()
print("PACKAGE_FEE_PATHS added:", "PACKAGE_FEE_PATHS = (" in code)
print("guard updated:", "or cur not in PACKAGE_FEE_PATHS" in code)
print("patched", os.path.basename(SRC))
