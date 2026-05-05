# 🎯 历史数据导入功能 - 使用指南

## 📖 快速上手（3分钟）

### ✅ 前提条件

确保你已经：
1. ✅ 安装了 Python 3.8+
2. ✅ 安装了 Node.js 16+
3. ✅ 项目依赖已安装（`pip install -r backend/requirements.txt` 和 `npm install`）

---

## 🚀 方式一：Web界面导入（推荐）

### 第1步：启动服务

```bash
# 终端1 - 启动后端
cd backend
python -m uvicorn app.main:app --reload --port 8000

# 终端2 - 启动前端
cd frontend
npm run dev
```

等待看到类似输出：
```
INFO:     Uvicorn running on http://127.0.0.1:8000
VITE v4.x.x  ready in xxx ms
➜  Local:   http://localhost:5173/
```

### 第2步：打开浏览器

访问：`http://localhost:5173`

### 第3步：导入数据

1. **找到导入按钮**
   - 在左侧边栏底部，点击 **"📊 数据导入"**

2. **选择表单类型**
   - 从列表中选择 "leave"（请假申请）
   - 查看右侧的表单详细信息

3. **确认配置**
   - 点击"下一步"
   - （可选）设置"限制条数"为 5（用于测试）
   - 点击"开始导入"

4. **查看结果**
   - 等待导入完成（通常几秒）
   - 查看统计信息和字段分布
   - 点击"完成"

### 第4步：验证效果

在聊天窗口输入："我要请假"

观察系统是否基于历史数据提供智能推荐：
- 申请人推荐：张三、李四、王五...
- 部门推荐：技术部、销售部...
- 请假类型推荐：年假、病假、事假...

---

## 💻 方式二：命令行导入

### 快速命令

```bash
cd backend

# 1. 查看可导入的表单
python -m app.scripts.import_history --list

# 2. 预览导入效果（不写入数据库）
python -m app.scripts.import_history leave --dry-run

# 3. 执行导入（限制5条用于测试）
python -m app.scripts.import_history leave --limit 5

# 4. 导入全部数据
python -m app.scripts.import_history leave

# 5. 导入所有表单类型
python -m app.scripts.import_history --all
```

### 输出示例

```
========================================================
  导入报告: leave
========================================================
  源记录数:     25
  实际导入:     25
  跳过(空数据): 0
  错误:         0

  顶层字段分布 (Top 5):
  --------------------------------------------------------
  applicant_name:
    张三: 5次
    李四: 4次
    王五: 3次
    ...
  
  department:
    技术部: 10次
    销售部: 6次
    ...

========================================================
```

---

## 🧪 方式三：API测试

### 运行测试脚本

```bash
# 确保后端服务已启动
python test_import_api.py
```

### 手动测试API

```bash
# 1. 获取可导入表单列表
curl http://localhost:8000/api/v1/config/import/list

# 2. 执行导入
curl -X POST http://localhost:8000/api/v1/config/import/execute \
  -H "Content-Type: application/json" \
  -d '{"formCode": "leave", "limit": 5}'
```

---

## 📋 可用的示例数据

系统预置了以下示例数据文件：

| 表单编码 | 表单名称 | 记录数 | 数据格式 | 说明 |
|---------|---------|-------|---------|------|
| `leave` | 请假申请 | 25条 | JSONL | OA系统历史请假记录 |
| `sales_order` | 销售订单 | 10条 | JSONL | ERP系统历史订单 |
| `tariff_filing_publicity` | 资费备案公示 | 30条 | JSONL | 电信业务资费备案 |

**数据位置：** `backend/config/import_data/`

---

## 🔍 如何准备自己的数据

### 步骤1：创建数据文件

在 `backend/config/import_data/` 目录创建文件：

**文件名格式：** `{formCode}.data.jsonl`

**内容格式：** 每行一个JSON对象

```json
{"field1":"value1","field2":"value2","field3":"value3"}
{"field1":"value4","field2":"value5","field3":"value6"}
```

**示例（my_form.data.jsonl）：**
```json
{"name":"张三","age":25,"department":"技术部"}
{"name":"李四","age":30,"department":"销售部"}
{"name":"王五","age":28,"department":"人事部"}
```

### 步骤2：（可选）创建Schema文件

**文件名格式：** `{formCode}.schema.json`

```json
{
  "formCode": "my_form",
  "formName": "我的表单",
  "description": "自定义表单数据",
  "valueTransform": {
    "department": {
      "tech": "技术部",
      "sales": "销售部"
    }
  },
  "userField": "name"
}
```

### 步骤3：导入数据

```bash
python -m app.scripts.import_history my_form
```

或通过Web界面导入。

---

## ❓ 常见问题

### Q1: 导入后没有看到推荐？

**解决方案：**
1. 确认导入成功（查看导入结果或日志）
2. 刷新页面重新生成表单
3. 检查字段名是否与本体定义一致
4. 查看后端日志：`backend/logs/app.log`

### Q2: 如何删除已导入的数据？

目前需要通过SQL手动删除：

```sql
-- 查找template_id
SELECT id FROM form_template WHERE form_code = 'leave';

-- 删除历史记录
DELETE FROM form_history 
WHERE form_instance_id IN (
  SELECT id FROM form_instance WHERE template_id = <template_id>
);

-- 删除实例
DELETE FROM form_instance WHERE template_id = <template_id>;
```

### Q3: 支持哪些数据格式？

- ✅ **JSONL**（推荐）：每行一个JSON对象，支持嵌套、数组
- ⚠️ **CSV**（兼容）：仅支持扁平结构

### Q4: 导入失败怎么办？

**排查步骤：**
1. 检查数据文件格式是否正确
2. 使用 `--dry-run` 预览而不写入
3. 查看后端日志：`backend/logs/app.log`
4. 确认数据库连接正常
5. 检查字段名是否与本体定义匹配

### Q5: 可以重复导入吗？

可以，但会导致数据重复。建议：
- 首次导入时使用 `--limit` 测试
- 确认无误后再导入全部
- 如需重新导入，先清空数据

---

## 📚 更多文档

- [快速开始指南](./QUICK_START_IMPORT.md) - 3步快速导入
- [详细功能说明](./docs/历史数据导入功能说明.md) - 完整使用指南
- [界面使用说明](./docs/历史数据导入界面说明.md) - 界面元素详解
- [实现总结](./IMPLEMENTATION_SUMMARY.md) - 技术实现细节

---

## 🎉 开始使用

现在你已经了解了如何使用历史数据导入功能，开始体验吧！

**推荐操作流程：**
1. 使用Web界面导入"请假申请"数据（25条）
2. 在聊天窗口测试智能推荐
3. 根据需要导入其他表单数据
4. 准备你自己的业务数据并导入

**祝你使用愉快！** 🚀
