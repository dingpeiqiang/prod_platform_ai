# 📁 历史数据导入目录

## ⚠️ 重要说明

**此目录用于存放用户自己准备的历史业务数据文件。**

系统**不会预置任何示例数据**，所有数据都需要用户从自己的业务系统中导出并放入此目录。

---

## 📋 目录结构

```
import_data/
├── {formCode}.data.jsonl      # 数据文件（必需）
└── {formCode}.schema.json     # 元数据声明（可选）
```

---

## 📝 如何准备数据

### 步骤1: 创建数据文件

**文件名格式：** `{formCode}.data.jsonl`

**内容格式：** 每行一个完整的JSON对象

**示例（my_leave.data.jsonl）：**
```json
{"applicant_name":"张三","department":"技术部","leave_type":"年假","start_date":"2026-03-15","end_date":"2026-03-20","reason":"回老家探亲","days":5}
{"applicant_name":"李四","department":"销售部","leave_type":"事假","start_date":"2026-03-22","end_date":"2026-03-24","reason":"家里有事","days":3}
{"applicant_name":"王五","department":"技术部","leave_type":"病假","start_date":"2026-03-25","end_date":"2026-03-27","reason":"感冒发烧","days":3}
```

**注意事项：**
- ✅ 每行必须是有效的JSON对象
- ✅ 字段名必须与本体定义中的fieldCode一致
- ✅ 支持嵌套结构和数组
- ✅ 以 `#` 开头的行为注释，会被忽略
- ❌ 不要包含空行或无效的JSON

---

### 步骤2: （可选）创建Schema文件

**文件名格式：** `{formCode}.schema.json`

**用途：**
- 定义表单元数据（名称、描述）
- 配置值转换规则
- 指定用户字段

**示例（my_leave.schema.json）：**
```json
{
  "formCode": "my_leave",
  "formName": "我的请假申请",
  "description": "公司OA系统导出的请假记录",
  "valueTransform": {
    "leave_type": {
      "annual": "年假",
      "sick": "病假",
      "personal": "事假"
    }
  },
  "userField": "applicant_name"
}
```

**字段说明：**
- `formCode`: 表单编码（与文件名对应）
- `formName`: 表单显示名称
- `description`: 数据来源描述
- `valueTransform`: 值转换规则（可选）
- `userField`: 用户标识字段（可选）

---

## 📂 参考示例

我们在 `import_data_examples/` 目录提供了一些示例文件供参考：

```
import_data_examples/
├── leave.data.jsonl              # 请假数据示例
├── leave.schema.json
├── sales_order.data.jsonl        # 销售订单示例
├── sales_order.schema.json
├── tariff_filing_publicity.data.jsonl  # 资费备案示例
└── tariff_filing_publicity.schema.json
```

**⚠️ 注意：** 这些只是格式参考，**不会被系统自动导入**。你需要：
1. 查看示例了解格式
2. 准备你自己的真实业务数据
3. 将你的数据文件放入 `import_data/` 目录

---

## 🚀 导入流程

### 1. 准备数据文件

从你的业务系统导出数据，按照上述格式保存到 `import_data/` 目录。

例如：
```
import_data/
├── my_leave.data.jsonl          # 你的请假数据
├── my_leave.schema.json         # 你的元数据
├── my_orders.data.jsonl         # 你的订单数据
└── my_orders.schema.json
```

### 2. 启动服务

```bash
# 后端
cd backend
python -m uvicorn app.main:app --reload --port 8000

# 前端
cd frontend
npm run dev
```

### 3. 执行导入

**方式一：Web界面（推荐）**
1. 打开浏览器访问前端应用
2. 点击左侧边栏底部的"📊 数据导入"
3. 选择你的表单类型
4. 确认配置并导入

**方式二：命令行**
```bash
cd backend

# 列出可导入的表单
python -m app.scripts.import_history --list

# 预览导入（不写入）
python -m app.scripts.import_history my_leave --dry-run

# 执行导入
python -m app.scripts.import_history my_leave
```

---

## 🔍 验证数据格式

### 方法1: 使用命令行预览

```bash
python -m app.scripts.import_history my_form --dry-run --limit 5
```

这会显示：
- 总记录数
- 字段值分布
- 首条记录样例

### 方法2: JSON验证工具

使用在线JSON验证工具检查每一行是否是有效的JSON。

### 方法3: Python脚本验证

```python
import json

with open('import_data/my_form.data.jsonl', 'r', encoding='utf-8') as f:
    for i, line in enumerate(f, 1):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        try:
            obj = json.loads(line)
            print(f"第{i}行: 有效")
        except json.JSONDecodeError as e:
            print(f"第{i}行: 无效 - {e}")
```

---

## ❓ 常见问题

### Q1: 从哪里获取数据？

**A:** 从你的业务系统导出：
- OA系统：请假记录、审批记录
- ERP系统：订单数据、库存数据
- CRM系统：客户信息、销售记录
- 其他系统：任何结构化的业务数据

导出格式可以是CSV、Excel等，然后转换为JSONL格式。

---

### Q2: 如何转换CSV为JSONL？

**A:** 使用Python脚本：

```python
import csv
import json

csv_file = 'your_data.csv'
jsonl_file = 'import_data/your_form.data.jsonl'

with open(csv_file, 'r', encoding='utf-8-sig') as csvfile:
    reader = csv.DictReader(csvfile)
    with open(jsonl_file, 'w', encoding='utf-8') as jsonl:
        for row in reader:
            # 清理空值
            clean_row = {k: v for k, v in row.items() if v and v.strip()}
            if clean_row:
                jsonl.write(json.dumps(clean_row, ensure_ascii=False) + '\n')

print(f"转换完成: {jsonl_file}")
```

---

### Q3: 字段名必须完全匹配吗？

**A:** 是的，字段名必须与本体定义中的`fieldCode`完全一致（区分大小写）。

如果不一致，有两种方案：
1. 在导出时重命名字段
2. 使用schema的valueTransform进行映射

---

### Q4: 可以导入多少条数据？

**A:** 没有硬性限制，但建议：
- 首次测试：10-50条
- 正式导入：根据实际情况，几千到几万条都可以
- 超大数据：建议分批导入

---

### Q5: 导入后数据在哪里？

**A:** 数据会存储在数据库的以下表中：
- `form_instance`: 表单实例（完整数据）
- `form_history`: 字段历史（用于推荐引擎）

---

## 📞 需要帮助？

- 查看示例文件：`import_data_examples/`
- 详细文档：`docs/历史数据导入功能说明.md`
- 快速指南：`QUICK_START_IMPORT.md`
- 调试指南：`docs/如何查看真实导入数据.md`

---

**记住：所有数据都应该是你自己的真实业务数据！** 🎯
