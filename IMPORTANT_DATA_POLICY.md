# ⚠️ 重要说明：关于历史数据导入

## 🎯 核心原则

**系统不会预置任何示例数据或测试数据！**

所有历史数据都必须是用户从自己的业务系统中导出的**真实业务数据**。

---

## ❌ 我们不提供

- ❌ 虚拟/模拟的测试数据
- ❌ 自动生成的示例记录
- ❌ 预设的请假、订单等业务数据
- ❌ 任何形式的"默认数据"

---

## ✅ 你需要做什么

### 1. 从你的业务系统导出数据

例如：
- **OA系统** → 导出请假记录、审批记录
- **ERP系统** → 导出订单数据、库存数据
- **CRM系统** → 导出客户信息、销售记录
- **HR系统** → 导出员工信息、考勤记录
- **其他系统** → 任何结构化的业务数据

### 2. 转换为JSONL格式

将导出的数据（CSV、Excel等）转换为JSONL格式：

```json
{"field1":"value1","field2":"value2"}
{"field1":"value3","field2":"value4"}
```

### 3. 放入指定目录

将你的数据文件放入：
```
backend/config/import_data/
```

### 4. 执行导入

通过Web界面或命令行导入你的真实数据。

---

## 📂 参考示例（仅供参考格式）

我们在 `backend/config/import_data_examples/` 目录提供了一些**格式参考文件**：

```
import_data_examples/
├── leave.data.jsonl              # 请假数据格式示例
├── leave.schema.json
├── sales_order.data.jsonl        # 销售订单格式示例
├── sales_order.schema.json
└── ...
```

**⚠️ 重要：**
- 这些文件**只是格式参考**
- 它们**不会被系统自动导入**
- 你**不应该直接使用**这些数据
- 你需要准备**你自己的真实业务数据**

---

## 🔍 如何查看示例格式

如果你想了解数据格式，可以查看示例文件：

```bash
# Windows
type backend\config\import_data_examples\leave.data.jsonl

# Linux/Mac
cat backend/config/import_data_examples/leave.data.jsonl
```

你会看到类似这样的格式：
```json
{"applicant_name":"张三","department":"技术部","leave_type":"年假",...}
{"applicant_name":"李四","department":"销售部","leave_type":"事假",...}
```

**但这只是告诉你格式应该是什么样的，不是让你使用这些数据！**

---

## 💡 正确的使用流程

```
你的业务系统
    ↓
导出数据（CSV/Excel/数据库）
    ↓
转换为JSONL格式
    ↓
保存到 import_data/ 目录
    ↓
通过系统导入
    ↓
推荐引擎基于你的真实数据工作
```

---

## 📝 数据准备示例

假设你有一个OA系统，里面有请假记录：

### 步骤1: 从OA系统导出

导出为CSV文件 `leave_export.csv`：
```csv
申请人,部门,请假类型,开始日期,结束日期,天数
张三,技术部,年假,2026-03-15,2026-03-20,5
李四,销售部,事假,2026-03-22,2026-03-24,3
...
```

### 步骤2: 转换为JSONL

使用Python脚本转换：
```python
import csv
import json

with open('leave_export.csv', 'r', encoding='utf-8-sig') as csvfile:
    reader = csv.DictReader(csvfile)
    with open('backend/config/import_data/my_leave.data.jsonl', 'w', encoding='utf-8') as jsonl:
        for row in reader:
            # 映射字段名到本体定义
            record = {
                "applicant_name": row["申请人"],
                "department": row["部门"],
                "leave_type": row["请假类型"],
                "start_date": row["开始日期"],
                "end_date": row["结束日期"],
                "days": int(row["天数"])
            }
            jsonl.write(json.dumps(record, ensure_ascii=False) + '\n')
```

### 步骤3: 创建Schema（可选）

创建 `my_leave.schema.json`：
```json
{
  "formCode": "my_leave",
  "formName": "我的请假申请",
  "description": "公司OA系统导出的请假记录",
  "userField": "applicant_name"
}
```

### 步骤4: 导入数据

```bash
cd backend
python -m app.scripts.import_history my_leave
```

或通过Web界面导入。

---

## 🎯 为什么这样做？

### 1. 数据真实性
- 只有真实的业务数据才能提供有价值的推荐
- 虚拟数据无法反映实际业务场景

### 2. 数据隐私
- 你的业务数据可能包含敏感信息
- 我们不应该预置或接触你的真实数据

### 3. 业务多样性
- 每个公司的业务流程不同
- 每个系统的数据结构不同
- 预置数据无法满足所有需求

### 4. 灵活性
- 你可以随时导入新的数据
- 你可以导入任意类型的数据
- 完全由你控制

---

## ❓ 常见问题

### Q: 我没有真实数据怎么办？

**A:** 
1. 从你的业务系统导出历史数据
2. 如果没有系统，手动创建一些测试数据
3. 参考示例文件的格式

### Q: 可以使用示例数据吗？

**A:** 
- 技术上可以，但**不建议**
- 示例数据只是为了展示格式
- 你应该使用自己的真实数据

### Q: 数据格式不对怎么办？

**A:**
1. 查看示例文件了解正确格式
2. 使用转换脚本处理数据
3. 查看文档：`docs/历史数据导入功能说明.md`

### Q: 可以导入多少数据？

**A:**
- 没有硬性限制
- 建议首次测试用10-50条
- 正式导入可以根据需要导入几千到几万条

---

## 📞 需要帮助？

- 查看格式示例：`import_data_examples/`
- 详细文档：`docs/历史数据导入功能说明.md`
- 快速指南：`QUICK_START_IMPORT.md`
- 调试指南：`docs/如何查看真实导入数据.md`

---

**记住：所有数据都应该是你自己的真实业务数据！** 

**我们只提供工具，不提供数据。** 🎯
