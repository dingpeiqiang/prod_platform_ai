# FORMS.md - 表单系统规范

> 本文档定义表单系统的字段推断规则、Schema 结构和表单类型定义。

## AI 主导的字段推断规范

### 核心原则

**所有字段值提取必须由 LLM 完成，不在代码中硬编码任何业务规则。**

```python
# ❌ 错误：硬编码业务规则
if field_code == 'bossid':
    tariff_pattern = r'(P\d{6,})'
    matches = re.findall(tariff_pattern, user_input)
    if matches:
        return matches[0]

# ✅ 正确：由 LLM 根据本体定义推断
# 1. 在 smart_intent_recognition.txt 中定义推断规则
# 2. LLM 读取本体定义（ontologies/tariff_filing_publicity.json）
# 3. LLM 根据规则提取字段值
# 4. 推荐引擎从 extractedFields 获取 LLM 提取的值
```

### 字段推断规则（在意图识别提示词中定义）

#### 1. 编码类字段（fieldCode 包含 code/id/no/number/bossid）

- **从用户输入中提取符合格式的编码**
- 常见编码格式：
  - 套餐编码：数字或字母+数字组合（如 P000111、P123456789、TC2024001、2024001）
  - 订单号：ORD开头 + 数字（如 ORD20240001）
  - 手机号：11位数字（如 13812345678）
  - 邮箱：标准邮箱格式（如 user@example.com）
- **示例**：
  - 用户说"备案套餐P000111" → bossid="P000111"
  - 用户说"订单号是ORD20240001" → order_id="ORD20240001"

#### 2. 枚举/选择类字段（fieldType 为 select/enum/radio）

- **根据用户输入的关键词匹配枚举值**
- 优先使用本体中定义的枚举值（options 列表）
- **示例**：
  - 用户说"新增备案" → action_type="新增"
  - 用户说"修改信息" → action_type="修改"
  - 用户说"删除记录" → action_type="删除"

#### 3. 数值类字段（fieldType 为 number/integer/float）

- **从用户输入中提取数字，注意单位转换**
- 常见单位转换：
  - "5万" → 50000
  - "3千" → 3000
  - "1.5G" → 1.5
  - "100M" → 100
- **示例**：
  - 用户说"费用5万元" → fees=50000
  - 用户说"带宽100M" → bandwidth=100

#### 4. 日期时间字段（fieldType 为 date/datetime）

- **必须转换为标准格式**：
  - date: "YYYY-MM-DD"（如 2026-05-12）
  - datetime: ISO 8601（如 2026-05-12T10:30:00）
- **支持自然语言转换**：
  - "今天" → 当前日期
  - "明天" → 明天的日期
  - "下周一" → 下周一的日期
  - "2026年5月12日" → "2026-05-12"
- **重要**：不要留空，必须提取具体日期值

#### 5. 关联字段推断（基于已提取字段）

- **如果字段的值依赖于其他字段，进行关联推断**
- **示例**：
  - 用户提到"畅享套餐" → name="畅享套餐", type1="基础通信服务"
  - 用户说"请假一天"且 start_date="2026-05-12" → end_date="2026-05-12"

#### 6. 必填字段优先处理

- **对于 required=true 的字段，尽量从用户输入或上下文中推断**
- 如果无法推断，在 missingInfo 中说明需要用户提供
- **不要跳过必填字段**，即使值为空也要在 extractedFields 中包含该字段（值为 ""）

#### 7. 文本类字段（fieldType 为 string/input/textarea）

- **直接提取用户描述的文本内容**
- 保持原始语义，不做过多转换
- **示例**：
  - 用户说"原因是家里有事" → reason="家里有事"
  - 用户说"备注：请尽快处理" → remark="请尽快处理"

### 推荐引擎集成

推荐引擎的 `ContextAwareStrategy` 只负责从 LLM 提取的结果中获取值：

```python
# ✅ 正确：从 LLM 提取的字段中获取推荐值
def recommend(self, user_input, form_code, field_code, context):
    extracted_fields = context.get('extractedFields', {})
    
    # LLM 已经在意图识别阶段提取了字段值
    if field_code in extracted_fields:
        value = extracted_fields[field_code]
        if value:
            return [RecommendationItem(
                value=str(value),
                source="llm_extraction",
                confidence=0.9
            )]
    
    return []
```

**禁止**在推荐策略中硬编码任何表单特定的推断逻辑。

### 新增表单类型的流程

1. **定义本体**：在 `backend/config/ontologies/{form_code}.json` 中定义表单 Schema
2. **配置场景**：在 `backend/config/scenes/scene_mapping.json` 中添加场景映射
3. **编写场景提示词**：在 `backend/config/prompts/scenes/{scene_code}_prompt.txt` 中定义业务流程
4. **更新意图识别提示词**：在 `smart_intent_recognition.txt` 中添加关键词和推断规则（如果需要）
5. **配置静态推荐**（可选）：在 `backend/config/templates/recommendations.json` 中添加默认值

**无需修改任何 Python 代码！**

### 架构优势

```
用户输入："资费备案申请 P000111"
    ↓
LLM 意图识别（基于本体规则）
    ├─ 识别场景：tariff_filing_apply
    ├─ 加载本体定义：tariff_filing_publicity.json
    ├─ 根据本体规则推断：
    │   ├─ bossid: "P000111"（编码类字段，P+数字格式）
    │   └─ 其他字段：根据上下文推断或留空
    └─ 返回 extractedFields
    ↓
推荐引擎
    ├─ ContextAwareStrategy: 从 extractedFields 获取 LLM 提取的值
    ├─ FrequencyStrategy: 查询历史数据
    ├─ TimeDecayStrategy: 查询近期数据
    └─ StaticStrategy: 加载配置文件中的静态推荐
    ↓
合并推荐结果并返回
```

## 表单系统

### 表单类型编码

| 编码                        | 类型      | 必填字段                 | 可选字段         |
| ------------------------- | ------- | -------------------- | ------------ |
| `tariff_filing_publicity` | 资费备案公示  | bossid, tariff_code | （根据Schema定义） |
| `external_api_demo`       | 外部API演示 | -                    | -            |
| `validation_demo`         | 校验演示    | -                    | -            |
| `survey`                  | 调查问卷    | (根据问卷定义)             | (根据问卷定义)     |
| `general`                 | 通用表单    | (无)                  | (无)          |

### 字段类型定义

| 类型         | 格式         | 示例                  |
| ---------- | ---------- | ------------------- |
| `string`   | 任意文本       | "张三"                |
| `input`    | 文本输入       | "张三"                |
| `textarea` | 多行文本       | "详细描述..."           |
| `integer`  | 整数         | 3                   |
| `number`   | 数值         | 123.45              |
| `boolean`  | true/false | true                |
| `date`     | YYYY-MM-DD | 2026-04-17          |
| `datetime` | ISO 8601   | 2026-04-17T10:30:00 |
| `email`    | 邮箱格式       | user@example.com  |
| `phone`    | 手机号        | 13812345678         |
| `enum`     | 枚举值        | ["年假", "病假", "事假"] |

### Schema 文件结构

表单 Schema 采用**实体-字段**层级结构：

```json
{
  "formCode": "string",           // 表单唯一编码（英文小写）
  "formName": "string",           // 表单显示名称（中文）
  "description": "string",        // 表单描述
  "entities": [                   // 实体数组
    {
      "entityCode": "string",     // 实体编码
      "entityName": "string",     // 实体名称
      "fields": [                 // 字段数组
        {
          "fieldCode": "string",  // 字段编码
          "fieldName": "string",  // 字段显示名称
          "fieldType": "string",  // 字段类型
          "required": true/false, // 是否必填
          "ruleDescription": "string"  // 验证规则描述
        }
      ]
    }
  ]
}
```

**Schema 文件位置**：`backend/config/ontologies/{form_code}.json`

---

**相关文档**：
- [架构设计规范](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/ARCHITECTURE.md)
- [编码质量规范](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/AGENTS.md)
- [工具使用规范](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/TOOLS.md)
- [开发指南](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/DEVELOPMENT.md)