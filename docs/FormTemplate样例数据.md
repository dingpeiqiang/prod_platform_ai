# ✅ FormTemplate样例数据 - 单条参考实现

## 🎯 需求

**用户要求**: "模板中样例数据给一条参考即可，不要多"

### 背景

在FormTemplate的schema中添加一条样例数据，用于：
- 📋 展示数据结构
- 🔍 帮助开发者理解字段格式
- 💡 作为API调用的参考示例

---

## 🔧 实现方案

### 核心思路

**从导入的第一条记录中提取样例，应用反向转换（确保使用编码），存储到schema.sample字段**

```
导入流程:
  1. 读取第一条记录: {"leave_type": "年假"}
  
  ↓
  
  2. 反向转换: {"leave_type": "annual"}  ← 编码
  
  ↓
  
  3. 添加到schema:
     {
       "formCode": "leave",
       "entities": [...],
       "sample": {                      ← 新增
         "leave_type": "annual",        ← 编码
         "department": "tech"
       }
     }
  
  ↓
  
  4. 保存到FormTemplate
```

---

### 代码实现

#### 1. 提取样例数据

```python
# ⚠️ 重要：从records中提取一条样例数据（使用编码）
sample_record = None
if records:
    # 取第一条记录，应用反向转换
    first_record = records[0]
    flat_sample = flatten_record(first_record)
    sample_with_codes = apply_reverse_transforms(flat_sample, ontology)
    sample_record = sample_with_codes
    logger.info("提取样例数据: form_code=%s", form_code)
```

**功能**:
- ✅ 只取第一条记录
- ✅ 应用反向转换（中文 → 编码）
- ✅ 确保样例使用编码格式

---

#### 2. 传递样例数据

```python
# 确保 FormTemplate 存在（传入schema以支持更新）
template_id = _ensure_template(db, form_code, form_name, schema, sample_record)
```

---

#### 3. 添加到schema

```python
def _ensure_template(db, form_code: str, form_name: str, 
                     schema: Optional[Dict] = None, 
                     sample_record: Optional[Dict] = None) -> int:
    """
    确保 FormTemplate 存在，不存在则创建，存在则更新
    ⚠️ 重要：会合并本体定义中的字段配置（包括 options 的 {value, label} 格式）
    ⚠️ 重要：会在schema中存储一条样例数据作为参考
    """
    # ... 加载本体配置 ...
    
    # ⚠️ 重要：添加样例数据到schema（只有一条）
    if sample_record:
        enriched_schema['sample'] = sample_record
        logger.info("添加样例数据到schema: form_code=%s", form_code)
    
    # ... 保存模板 ...
```

---

## 📊 数据结构

### FormTemplate.schema 完整结构

```json
{
  "formCode": "leave",
  "formName": "请假申请",
  "entities": [
    {
      "entityCode": "leave_info",
      "fields": [
        {
          "fieldCode": "leave_type",
          "fieldName": "请假类型",
          "options": [
            {"value": "annual", "label": "年假"},
            {"value": "sick", "label": "病假"}
          ]
        }
      ]
    }
  ],
  "sample": {                                    ← 新增：样例数据
    "applicant_name": "张三",
    "leave_type": "annual",                      ← 编码
    "department": "tech",                        ← 编码
    "start_date": "2026-03-15",
    "end_date": "2026-03-20",
    "reason": "回老家探亲",
    "days": 5
  }
}
```

**特点**:
- ✅ 只有一条样例
- ✅ 使用编码格式
- ✅ 包含所有字段
- ✅ 便于参考

---

## ✅ 验证方法

### 1. 检查数据库

```sql
-- 查看FormTemplate的schema
SELECT id, form_code, schema 
FROM form_templates 
WHERE form_code = 'leave';
```

**预期结果**:
```json
{
  "formCode": "leave",
  "formName": "请假申请",
  "entities": [...],
  "sample": {
    "leave_type": "annual",    // ✅ 编码
    "department": "tech"       // ✅ 编码
  }
}
```

---

### 2. 查看日志

执行导入时应该看到：
```
提取样例数据: form_code=leave
添加样例数据到schema: form_code=leave
创建/更新 FormTemplate: form_code=leave (包含本体配置)
```

---

### 3. API测试

```bash
curl "http://localhost:8000/api/v1/config/templates/leave"
```

**预期响应**:
```json
{
  "success": true,
  "template": {
    "id": 1,
    "formCode": "leave",
    "formName": "请假申请",
    "schema": {
      "formCode": "leave",
      "entities": [...],
      "sample": {                    // ✅ 包含样例
        "leave_type": "annual",
        "department": "tech"
      }
    }
  }
}
```

---

## 🎯 关键特性

### 1. 只存储一条

```python
# 只取第一条记录
first_record = records[0]
```

**优势**:
- ✅ 节省存储空间
- ✅ 简洁明了
- ✅ 足够作为参考

---

### 2. 使用编码格式

```python
sample_with_codes = apply_reverse_transforms(flat_sample, ontology)
```

**优势**:
- ✅ 与FormInstance.data一致
- ✅ 与FormHistory.field_value一致
- ✅ 符合编码存储规范

---

### 3. 自动更新

```python
if sample_record:
    enriched_schema['sample'] = sample_record
```

**优势**:
- ✅ 每次导入都会更新样例
- ✅ 保持样例最新
- ✅ 反映当前数据结构

---

## 📝 使用场景

### 场景1: API文档示例

```javascript
// 创建请假申请的API调用示例
const requestData = {
  formCode: "leave",
  data: {
    applicant_name: "李四",
    leave_type: "sick",      // 参考schema.sample中的格式
    department: "sales",
    start_date: "2026-04-30",
    end_date: "2026-05-02",
    reason: "感冒",
    days: 3
  }
};
```

---

### 场景2: 前端表单初始化

```javascript
// 从schema.sample获取默认值
const defaultValues = template.schema.sample;
// {
//   applicant_name: "张三",
//   leave_type: "annual",
//   ...
// }

// 填充表单
Object.keys(defaultValues).forEach(field => {
  formData[field] = defaultValues[field];
});
```

---

### 场景3: 数据验证

```javascript
// 检查提交的数据格式是否正确
const sample = template.schema.sample;
const submitted = formData;

// 比较字段类型
Object.keys(sample).forEach(field => {
  if (typeof submitted[field] !== typeof sample[field]) {
    console.error(`字段 ${field} 类型不匹配`);
  }
});
```

---

## 📁 修改的文件

1. **[import_history.py](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/backend/app/scripts/import_history.py)**
   - 提取样例数据逻辑
   - `_ensure_template` 函数增加sample_record参数
   - 添加样例数据到schema

---

## 🎉 优化效果

| 项目 | 修复前 | 修复后 |
|------|--------|--------|
| **样例数据** | ❌ 无 | ✅ 一条 |
| **数据格式** | - | ✅ 编码 |
| **参考价值** | - | ✅ 高 |
| **存储空间** | - | ✅ 最小 |
| **API文档** | ⚠️ 需手动编写 | ✅ 自动生成 |

---

## 💡 最佳实践

### 1. 样例数据用途

- ✅ API调用示例
- ✅ 前端表单初始化
- ✅ 数据格式验证
- ✅ 开发调试参考

### 2. 注意事项

- ⚠️ 样例数据不包含敏感信息
- ⚠️ 样例数据使用编码格式
- ⚠️ 每次导入会更新样例

### 3. 扩展建议

如果需要更多样例，可以：
```json
{
  "sample": {...},           // 主要样例
  "examples": [              // 可选：多个示例
    {...},
    {...}
  ]
}
```

但目前保持简洁，只用一条。

---

**最后更新**: 2026-04-29  
**版本**: v1.0  
**状态**: ✅ 实现完成
