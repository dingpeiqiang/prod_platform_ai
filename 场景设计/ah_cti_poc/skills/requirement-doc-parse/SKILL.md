---
name: "requirement-doc-parse"
description: "解析需求文档 .xlsx 为结构化需求（多品清单 + 品间关联关系），渲染为可直调 wf_sub_00 的 requirement_text。当用户上传需求文档 .xlsx 或提到需求文档/一品多品/解析 Excel 需求时，于需求提报环节调用。"
---

# Requirement Doc Parse Skill（需求文档解析）

## Purpose

产销品加载 AI 应用（统一方案 V3.11，见 `场景设计/ah_cti_poc/方案/产销品加载AI应用-统一方案.md` 6.6 章）
的**需求提报环节前置能力**：用户上传需求文档 `.xlsx` 后，由本 skill 解析为**结构化文本**，
再作为 `requirement_text` 直调工作流 `wf_sub_00`（文本轨 LLM 抽取多品）。

**为什么由 skill（而非工作流）解析**：工作流 `start` 节点**无法接收文件/附件**，
文档解析**不能由任何工作流承担**。故本 skill 承担「文档 → 结构化文本」的确定性归一，
工作流层保持文本驱动、不接收文件、不含解析节点、零改动。

## When to Invoke

当出现以下情形时调用：

- 用户上传需求文档 `.xlsx`（如《新增四档5G畅享融合套餐销售品.xlsx》）
- 用户提到「需求文档 / 一品多品 / 多商品同时配置 / 解析 Excel 需求 / 加载需求文档」
- 进入《产销品加载 AI 应用》**环节 1 需求提报**，且输入形态为文件而非纯文本

## 输入契约

| Sheet | 结构 | 说明 |
| --- | --- | --- |
| 可选包 | 每行一个新增销售品（宽表） | 关键列 `OFR_ID` / 套餐客户化名称 / 套餐名称 等；本 skill 保留**全量原始列**不裁剪 |
| 关联关系 | A/Z 端关系行 | `A_OFR_ID` / `A_OFR_NAME` / 关系 / `Z_OFR_ID` / `Z_OFR_NAME`；关系枚举见下 |
| 其它 sheet | 忽略（如空占位 Sheet3） | — |

关系枚举：`100000` 可选依赖 / `110000` 强制依赖 / `120000` 默认依赖 / `130000` 赠送 /
`200000` 互斥 / `410000` 不可迁转 / `100100` 自主版。（中文标签自动归一为数字枚举）

## How It Works

1. **执行解析脚本**（本 skill 内嵌纯 Python 实现，依赖 `openpyxl`）：

   ```bash
   python scripts/parse_requirement_xlsx.py "<需求文档.xlsx>" --pretty
   ```

   脚本只做**确定性结构归一**（识别 sheet / 定位表头 / 取值 / 关系枚举归一），
   不推理、不补全、不改写原始值。等价于后端端点 2e `/api/v1/appstore/requirement/parse-xlsx`
   （`backend-app` 的 `RequirementDocService`），供无后端环境由智能体直接解析。

2. **取 `requirement_text`**：脚本输出的 `requirement_text` 字段即渲染好的结构化文本
   （多品清单 + 品间关联关系），可直接作为 `wf_sub_00` 的入参。若只要文本：

   ```bash
   python scripts/parse_requirement_xlsx.py "<需求文档.xlsx>" --text-only
   ```

3. **调用工作流**：将 `requirement_text` 作为 `requirement_text` 入参直调 `wf_sub_00`
   （文本轨 LLM 抽取 → CODE_RENDER_REQ 渲染提报单，父 `req_id` + 各品 `offer_seq`）。

## 纪律边界（务必遵守）

- **严禁**调用任何「需求文档解析」子工作流；**严禁**向工作流传 `requirement_file` 或文件/附件。
- **严禁**改写原始单元格值；未提及字段保持空字符串，不做三归属裁剪
  （映射入 24 字段 / 落模板 schema / 冗余入报告由下游 `wf_sub_01` 模板轨完成）。
- 解析失败 / 无销售品 / 关系枚举越界 → 返回 `resultCode=E41`，由智能体引导用户检查文档。

## Output

stdout 输出 UTF-8 JSON（`--pretty` 缩进）：

```json
{
  "resultCode": "0",
  "resultMsg": "success",
  "count": 4,
  "offers": [
    {"offer_seq": 1, "ofr_id": "900117029", "offer_name": "5G畅享融合79元套餐202512",
     "sheet_name": "可选包", "fields": {"OFR_ID": "900117029", "...": "..."}}
  ],
  "relations": [
    {"a_ofr_id": "900117029", "a_name": "5G畅享融合79元套餐202512",
     "relation": "100000", "relation_label": "可选依赖",
     "z_ofr_id": "7320110001600005", "z_name": "副卡功能费"}
  ],
  "source_sheets": ["可选包", "关联关系", "Sheet3"],
  "requirement_text": "【需求文档解析结果】..."
}
```

字段规格与 E41 判定细则见 `references/output-contract.md`。

## Exit Codes

- `0`：解析成功（`resultCode=0`）
- `2`：解析失败（`resultCode=E41`，仍输出含 `resultMsg` 的结构体）

## Dependencies

- Python 3.8+
- `openpyxl`（缺失时脚本报错提示 `pip install openpyxl`）
