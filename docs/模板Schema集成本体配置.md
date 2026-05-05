# ✅ 模板Schema集成本体配置 - 编码存储方案完善

## 🎯 问题发现

**用户反馈**: "模板未按照枚举存储编码的方案来实现"

### 问题分析

之前的实现存在以下问题：

1. **导入时使用的schema**来自`config/import_data/{formCode}.schema.json`
2. 这个schema文件可能**没有包含字段的options配置**，或者options是字符串数组格式
3. 而**本体定义**在`config/ontologies/{formCode}.json`中，已经配置了完整的`{value, label}`格式
4. **FormTemplate存储的schema**没有包含本体的字段配置
5. 导致推荐引擎无法从模板中获取正确的标签映射

---

## 🔧 修复方案

### 核心思路

**在创建/更新FormTemplate时，自动从本体加载完整的字段配置（包括options的{value, label}格式）**

```
导入流程:
  1. 加载 schema.json (基础配置)
  2. 加载 ontology.json (完整字段配置)
  3. 合并两者 → enriched_schema
  4. 保存到 FormTemplate
```

---

### 修改前

```python
def _ensure_template(db, form_code: str, form_name: str, schema: Optional[Dict] = None) -> int:
    from app.models.form import FormTemplate

    template = db.query(FormTemplate).filter(
        FormTemplate.form_code == form_code
    ).first()

    if template:
        if schema:
            # ❌ 只使用传入的schema，没有本体配置
            for key, value in schema.items():
                ...
        
        return template.id

    # 创建新模板
    template_schema = schema or {"formCode": form_code, "formName": form_name}
    template = FormTemplate(
        form_code=form_code,
        form_name=form_name,
        schema=template_schema,  # ❌ 缺少本体配置
        ...
    )
```

**问题**:
- FormTemplate的schema中没有entities配置
- 推荐引擎无法从模板获取字段的options映射
- 无法正确显示中文标签

---

### 修改后

```python
def _ensure_template(db, form_code: str, form_name: str, schema: Optional[Dict] = None) -> int:
    """
    确保 FormTemplate 存在，不存在则创建，存在则更新
    ⚠️ 重要：会合并本体定义中的字段配置（包括 options 的 {value, label} 格式）
    """
    from app.models.form import FormTemplate
    from app.services.ontology_service import OntologyService

    template = db.query(FormTemplate).filter(
        FormTemplate.form_code == form_code
    ).first()

    # ⚠️ 重要：从本体获取完整的字段配置
    enriched_schema = schema.copy() if schema else {}
    try:
        ontology_result = OntologyService.get_form_constraint(form_code)
        if ontology_result.get('success'):
            ontology = ontology_result.get('constraints', {})
            
            # 合并本体中的entities到schema
            if 'entities' in ontology and ontology['entities']:
                enriched_schema['entities'] = ontology['entities']
                logger.info("从本体加载字段配置: form_code=%s", form_code)
    except Exception as e:
        logger.warning("从本体加载字段配置失败: %s", e)

    if template:
        # ⚠️ 重要：如果提供了schema，更新模板
        if enriched_schema:
            updated = False
            
            # 更新form_name
            if form_name and template.form_name != form_name:
                template.form_name = form_name
                updated = True
            
            # 更新schema（合并现有schema和新schema）
            current_schema = template.schema or {}
            # 保留原有字段，但用新schema覆盖
            for key, value in enriched_schema.items():
                if key not in current_schema or current_schema[key] != value:
                    current_schema[key] = value
                    updated = True
            
            if updated:
                template.schema = current_schema
                template.updated_at = datetime.now()
                db.commit()
                logger.info("更新 FormTemplate: form_code=%s (包含本体配置)", form_code)
        
        return template.id

    # 创建新模板
    template_schema = enriched_schema or {"formCode": form_code, "formName": form_name}
    template = FormTemplate(
        form_code=form_code,
        form_name=form_name,
        schema=template_schema,  # ✅ 包含本体配置
        version=1,
        is_active=True,
        created_at=datetime.now(),
        updated_at=datetime.now()
    )
    db.add(template)
    db.commit()
    db.refresh(template)
    logger.info("创建 FormTemplate: form_code=%s id=%d (包含本体配置)", form_code, template.id)
    return template.id
```

**改进**:
- ✅ 自动从本体加载entities配置
- ✅ 合并到enriched_schema
- ✅ 保存到FormTemplate
- ✅ 推荐引擎可以获取完整的字段配置

---

## 📊 数据流对比

### 修复前

```
导入流程:
  schema.json: {formCode: "leave", formName: "请假申请"}
  ontology.json: {entities: [{fields: [{fieldCode: "leave_type", options: [{value: "annual", label: "年假"}]}]}]}
  
  ↓
  
_ensure_template调用:
  schema = {formCode: "leave", formName: "请假申请"}  # ❌ 没有entities
  
  ↓
  
FormTemplate存储:
  {
    "formCode": "leave",
    "formName": "请假申请"
  }  # ❌ 缺少entities配置
  
  ↓
  
推荐引擎:
  从FormTemplate获取schema
  → 没有entities
  → 无法获取字段的options
  → 无法显示中文标签 ❌
```

---

### 修复后

```
导入流程:
  schema.json: {formCode: "leave", formName: "请假申请"}
  ontology.json: {entities: [{fields: [{fieldCode: "leave_type", options: [{value: "annual", label: "年假"}]}]}]}
  
  ↓
  
_ensure_template调用:
  1. 加载schema: {formCode: "leave", formName: "请假申请"}
  2. 加载ontology: {entities: [...]}
  3. 合并: enriched_schema = {
       formCode: "leave",
       formName: "请假申请",
       entities: [...]  # ✅ 包含本体配置
     }
  
  ↓
  
FormTemplate存储:
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
              {"value": "annual", "label": "年假"},  # ✅ {value, label}格式
              {"value": "sick", "label": "病假"}
            ]
          }
        ]
      }
    ]
  }
  
  ↓
  
推荐引擎:
  从FormTemplate获取schema
  → 有entities配置
  → 可以获取字段的options
  → 正确显示中文标签 ✅
```

---

## ✅ 验证方法

### 1. 检查数据库

```sql
-- 查看模板的schema
SELECT id, form_code, schema 
FROM form_templates 
WHERE form_code = 'leave';
```

**预期结果**:
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
  ]
}
```

✅ schema中包含entities配置  
✅ options使用{value, label}格式

---

### 2. 测试导入

```bash
cd backend
python -m app.scripts.import_history --form-code leave
```

**预期日志**:
```
加载 schema: form_code=leave
从本体加载字段配置: form_code=leave
创建 FormTemplate: form_code=leave id=1 (包含本体配置)
```

或（如果模板已存在）:
```
加载 schema: form_code=leave
从本体加载字段配置: form_code=leave
更新 FormTemplate: form_code=leave (包含本体配置)
```

---

### 3. 验证推荐功能

```bash
curl "http://localhost:8000/api/v1/history/recommend?formCode=leave&fieldCode=leave_type"
```

**预期响应**:
```json
{
  "success": true,
  "recommendations": [
    {
      "value": "annual",
      "label": "年假",  # ✅ 正确显示中文
      "score": 0.8,
      "source": "database"
    },
    {
      "value": "sick",
      "label": "病假",  # ✅ 正确显示中文
      "score": 0.6,
      "source": "database"
    }
  ]
}
```

---

## 🎯 关键特性

### 1. 自动合并本体配置

```python
# 从本体获取完整的字段配置
enriched_schema = schema.copy() if schema else {}
try:
    ontology_result = OntologyService.get_form_constraint(form_code)
    if ontology_result.get('success'):
        ontology = ontology_result.get('constraints', {})
        
        # 合并本体中的entities到schema
        if 'entities' in ontology and ontology['entities']:
            enriched_schema['entities'] = ontology['entities']
            logger.info("从本体加载字段配置: form_code=%s", form_code)
except Exception as e:
    logger.warning("从本体加载字段配置失败: %s", e)
```

**优势**:
- ✅ 自动加载本体配置
- ✅ 容错处理，失败不影响导入
- ✅ 记录日志便于调试

---

### 2. 智能更新检测

```python
# 更新schema（合并现有schema和新schema）
current_schema = template.schema or {}
for key, value in enriched_schema.items():
    if key not in current_schema or current_schema[key] != value:
        current_schema[key] = value
        updated = True

if updated:
    template.schema = current_schema
    template.updated_at = datetime.now()
    db.commit()
    logger.info("更新 FormTemplate: form_code=%s (包含本体配置)", form_code)
```

**优势**:
- ✅ 只在实际有变化时才更新
- ✅ 保留原有的自定义配置
- ✅ 记录更新时间戳

---

### 3. 向后兼容

```python
enriched_schema = schema.copy() if schema else {}
```

**优势**:
- ✅ 即使没有schema也能正常工作
- ✅ 即使本体加载失败也能继续
- ✅ 不会破坏现有功能

---

## 📝 完整工作流程

```
用户执行导入:
  python -m app.scripts.import_history --form-code leave
  
  ↓
  
1. 加载 schema.json
   {formCode: "leave", formName: "请假申请"}
   
  ↓
  
2. 加载 ontology.json
   {
     entities: [
       {
         entityCode: "leave_info",
         fields: [
           {
             fieldCode: "leave_type",
             fieldName: "请假类型",
             options: [
               {value: "annual", label: "年假"},
               {value: "sick", label: "病假"}
             ]
           }
         ]
       }
     ]
   }
   
  ↓
  
3. 合并为 enriched_schema
   {
     formCode: "leave",
     formName: "请假申请",
     entities: [...]  // 从本体获取
   }
   
  ↓
  
4. 保存至 FormTemplate
   INSERT INTO form_templates (form_code, schema, ...)
   VALUES ('leave', '{...enriched_schema...}', ...)
   
  ↓
  
5. 推荐引擎使用
   - 从 FormTemplate 获取 schema
   - 解析 entities 配置
   - 获取字段的 options 映射
   - 返回 {value: "annual", label: "年假"}
   
  ↓
  
6. 前端显示
   - 显示: [年假] [病假]
   - 提交: leave_type = "annual"
```

---

## 📁 修改的文件

1. **[import_history.py](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/backend/app/scripts/import_history.py#L403-L479)**
   - `_ensure_template` 函数 - 添加本体配置加载和合并逻辑

---

## 🎉 优化效果

| 项目 | 修复前 | 修复后 |
|------|--------|--------|
| **模板schema** | ❌ 缺少entities | ✅ 包含完整配置 |
| **字段options** | ❌ 无或字符串数组 | ✅ {value, label}格式 |
| **推荐标签** | ❌ 显示编码 | ✅ 显示中文 |
| **编码存储** | ⚠️ 部分实现 | ✅ 完全实现 |
| **本体同步** | ❌ 不同步 | ✅ 自动同步 |

---

## 💡 最佳实践

### 1. 本体配置规范

确保所有枚举字段都在本体中配置了options：

```json
{
  "formCode": "leave",
  "entities": [
    {
      "entityCode": "leave_info",
      "fields": [
        {
          "fieldCode": "leave_type",
          "fieldName": "请假类型",
          "fieldType": "select",
          "options": [
            {"value": "annual", "label": "年假"},
            {"value": "sick", "label": "病假"},
            {"value": "personal", "label": "事假"}
          ]
        }
      ]
    }
  ]
}
```

### 2. Schema文件简化

`config/import_data/{formCode}.schema.json` 可以只包含基础配置：

```json
{
  "formCode": "leave",
  "formName": "请假申请",
  "description": "OA系统历史请假记录"
}
```

详细的字段配置会从本体自动加载。

### 3. 监控日志

关注以下日志：
- `"从本体加载字段配置"` - 成功加载
- `"从本体加载字段配置失败"` - 加载失败，需要检查本体文件
- `"创建/更新 FormTemplate (包含本体配置)"` - 模板已保存

---

**最后更新**: 2026-04-29  
**版本**: v1.0  
**状态**: ✅ 修复完成
