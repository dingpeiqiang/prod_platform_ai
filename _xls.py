# -*- coding: utf-8 -*-
import sys, os, json
sys.stdout.reconfigure(encoding='utf-8')
from openpyxl import load_workbook
p = '场景设计/ah_cti_poc/方案/新增四档5G畅享融合套餐销售品.xlsx'
wb = load_workbook(p, data_only=True)
print('sheets:', wb.sheetnames)
for ws in wb.worksheets:
    print('=== sheet', ws.title, ws.max_row, ws.max_column)
    for i, row in enumerate(ws.iter_rows(values_only=True)):
        if i > 4:
            break
        print([str(c)[:24] if c is not None else '' for c in row][:12])
