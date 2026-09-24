# 需求文档解析输出契约（requirement-doc-parse）

本文档定义本 skill 的输出结构、字段口径与 E41 判定，与后端
`RequirementDocService`（端点 2e `/api/v1/appstore/requirement/parse-xlsx`）**一一对应**，
是《产销品加载 AI 应用-统一方案》6.6 章的具体落地。

## 1. 顶层结构

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `resultCode` | string | `"0"` 成功；`"E41"` 失败（需求文档解析失败/无销售品/关系越界） |
| `resultMsg` | string | `"success"` 或失败原因 |
| `count` | number | `offers[]` 条数（新增销售品个数） |
| `offers` | array | 多品清单，见 §2 |
| `relations` | array | 品间关联关系，见 §3 |
| `source_sheets` | string[] | 文档全部 sheet 名（按顺序），用于诊断 |
| `requirement_text` | string | 渲染文案，作为 `wf_sub_00` 的 `requirement_text` 入参，见 §4 |

## 2. offers[]（可选包 → 多品清单）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `offer_seq` | number | 子序号，**从 1 起**；与父 `req_id` 构成一品多品复合寻址 |
| `ofr_id` | string | 销售品 ID（`OFR_ID` / `销售品ID` / `销售品编码` / `商品编码` 列） |
| `offer_name` | string | 销售品名称（`套餐客户化名称` / `套餐名称` / `销售品名称` / `商品名称` 列） |
| `sheet_name` | string | 来源 sheet 名 |
| `fields` | object | **整行**原始列 → 值（列名空白时以 `col{n}` 占位，保证不丢列） |

口径：
- 跳过空行、重复表头行；`ofr_id` 与 `offer_name` 均空则跳过。
- `fields` 保留全量原始列，**不做**三归属裁剪（下游 `wf_sub_01` 模板轨处理）。
- 数字去除无意义的 `.0`（如 `79.0` → `79`）。

## 3. relations[]（关联关系 → 品间关联）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `a_ofr_id` | string | A 端销售品 ID |
| `a_name` | string | A 端名称 |
| `relation` | string | 关系数字枚举（中文标签已归一），见下表 |
| `relation_label` | string | 关系原始标签（保留原文，便于核对） |
| `z_ofr_id` | string | Z 端销售品 ID |
| `z_name` | string | Z 端名称 |

关系枚举：

| 枚举 | 标签 | 枚举 | 标签 |
| --- | --- | --- | --- |
| `100000` | 可选依赖 | `200000` | 互斥 |
| `110000` | 强制依赖 | `410000` | 不可迁转 |
| `120000` | 默认依赖 | `100100` | 自主版 |
| `130000` | 赠送 | | |

口径：
- 跳过空行、表头行、无 A/Z 端仅有说明文字的行。
- 去重键 `a_ofr_id|relation|z_ofr_id`（合并单元格展开会产生重复行）。
- 归一后映射入 `group_rules` 组内依赖（可选依赖 ↔ `OPTIONAL_DEPEND`，先例 `GP900113046`）。

## 4. requirement_text 渲染规格

渲染为自然语言文本，供 `wf_sub_00` 的 LLM 字段抽取（其 prompt 片段见
`wf_sub_00_需求提报.json`，抽取为 snake_case 平面 JSON `{name, product_type, series, members,
price, resources, out_price, billing_cycle, effective_way, validity, change_rule, cancel_rule}`）。

格式（示例）：

```
【需求文档解析结果】本次需求文档共包含 4 个新增销售品。

■ 销售品 5G畅享融合79元套餐202512（offer_seq=1）
  销售品ID（OFR_ID）：900117029
  字段明细：
    OFR_ID：900117029
    套餐名称：5G畅享融合79元套餐202512
    ...

■ 销售品 ...（offer_seq=2）
  ...

【品间关联关系】
  5G畅享融合79元套餐202512（900117029） --可选依赖(100000)--> 副卡功能费（7320110001600005）
```

要点：
- 逐品列出全部非空 `fields`（`列名：值`），确保 LLM 抽取不漏信息。
- 关联关系一行一条，形如 `A名称（A_ID） --标签(枚举)--> Z名称（Z_ID）`。
- 无多品时 `count=1`、`requirement_text` 退化为单品描述（文本轨仍可用）。

## 5. E41 失败判定

以下任一情形返回 `resultCode="E41"`（`count=0`、`offers/relations/source_sheets` 空、
`requirement_text` 空），由智能体引导用户检查文档：

1. 文档为空或未上传；
2. 解析器失败 / 未识别到表格；
3. 未识别到【可选包】sheet（需含 `OFR_ID` / 套餐名称 列）；
4. 【可选包】sheet 无有效销售品行（`offers` 为空）；
5. 关联关系枚举越界（`relation` 既非数字枚举、也无法由中文标签归一）。

命中 E41 时脚本退出码为 `2`（仍输出含 `resultMsg` 的结构体）。

## 6. 与工作流的对接

```
用户上传 .xlsx
  └─ 智能体调用本 skill（scripts/parse_requirement_xlsx.py）
       └─ 取 requirement_text
            └─ 直调 wf_sub_00（文本轨 LLM 抽取 → CODE_RENDER_REQ 渲染提报单）
                 └─ 父 req_id + 各品 offer_seq（一品多品）
```

**注意**：不得向工作流传 `requirement_file`；不得调用任何「需求文档解析」子工作流。
