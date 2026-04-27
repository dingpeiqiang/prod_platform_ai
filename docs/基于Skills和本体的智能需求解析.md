# 基于Skills和本体的智能需求解析

## 概述

大模型现在可以**基于Skills插件能力和本体定义**进行更智能的需求解析和意图识别！

---

## 核心改进

| 改进项 | 说明 |
|--------|------|
| **本体信息注入** | 将完整的本体结构（字段、类型、约束）传给LLM |
| **Skills能力参考** | 将场景识别关键词、字段提取分隔符传给LLM |
| **增强输出格式** | 增加confidence置信度、reasoning推理说明 |
| **智能提示词工程** | 两个专业的提示词模板 |

---

## 提示词模板

### 1. 智能意图识别提示词

**文件：** `config/prompts/smart_intent_recognition.txt`

**传给LLM的信息：**
- ✅ 所有本体的完整信息（表单、字段、类型、约束）
- ✅ 场景识别关键词参考
- ✅ 字段提取分隔符参考
- ✅ 用户消息历史

**LLM任务：**
1. 意图判断（聊天/表单）
2. 表单匹配（基于本体）
3. 字段提取（基于本体字段）
4. 字段校验（基于本体约束）

**输出增强：**
```json
{
  "intentType": "form",
  "formCode": "sales_order",
  "confidence": 0.95,
  "extractedFields": {
    "customer_name": "张三"
  },
  "validation": {
    "valid": true,
    "warnings": []
  },
  "reasoning": "用户明确提到'销售订单'，匹配到sales_order本体，置信度高"
}
```

---

### 2. 智能聊天回复提示词

**文件：** `config/prompts/smart_chat_response.txt`

**传给LLM的信息：**
- ✅ 所有本体信息
- ✅ 用户消息历史

**LLM任务：**
1. 友好专业的对话
2. 智能建议使用表单
3. 基于本体理解用户需求

---

## 信息构建函数

### `_build_ontologies_info()`

构建完整的本体信息字符串，包含：
- 表单编码和名称
- 表单描述
- 实体和字段列表
- 字段类型、是否必填

**示例输出：**
```
### sales_order (销售订单)
描述：销售订单表单
客户信息字段：
  - 客户姓名 (customer_name) - input [必填]
  - 客户电话 (customer_phone) - input
订单信息字段：
  - 订单金额 (order_amount) - number [必填]
```

---

### `_build_scene_keywords()`

构建场景识别关键词参考：
```
sales_order: 销售订单, 销售, 订单, 客户
leave: 请假, 请假申请, 休假
expense: 报销, 费用报销, 报销申请
```

---

### `_build_separators()`

构建字段提取分隔符参考：
```
'是', '为', '：', ':', ' '
```

---

## API响应增强

### ChatResponse新增字段

| 字段 | 说明 |
|------|------|
| `confidence` | 置信度（0-1） |
| `reasoning` | LLM的推理说明 |

---

## 完整工作流程

```
用户输入
    ↓
构建上下文信息
    ├─→ 本体完整信息
    ├─→ Skills关键词
    └─→ 分隔符列表
    ↓
LLM智能意图识别
    ├─→ 意图判断
    ├─→ 表单匹配
    ├─→ 字段提取（基于本体字段）
    └─→ 字段校验（基于本体约束）
    ↓
返回结果（含confidence和reasoning）
    ↓
    ├─→ form意图 → 生成表单
    │
    └─→ chat意图 → LLM智能回复
```

---

## 修改的文件

| 文件 | 说明 |
|------|------|
| `config/prompts/smart_intent_recognition.txt` | 智能意图识别提示词（新建） |
| `config/prompts/smart_chat_response.txt` | 智能聊天回复提示词（新建） |
| `app/api/chat.py` | 重构，使用新提示词和信息构建 |

---

## 使用效果

### 示例1：准确的表单识别

```
用户：帮我填一个销售订单，客户姓名张三
LLM输出：
{
  "intentType": "form",
  "formCode": "sales_order",
  "confidence": 0.98,
  "extractedFields": {"customer_name": "张三"},
  "reasoning": "用户提到'销售订单'，完全匹配sales_order本体..."
}
```

### 示例2：智能聊天建议

```
用户：我要请假
LLM识别为form，生成请假表单

用户：今天天气怎么样
LLM识别为chat，友好回复并建议："如果需要请假，可以告诉我哦"
```

---

## 总结

现在LLM可以：
1. ✅ 基于完整的本体信息进行意图识别
2. ✅ 参考Skills插件的能力（关键词、分隔符）
3. ✅ 输出置信度和推理说明
4. ✅ 更准确的字段提取（知道本体有哪些字段）
5. ✅ 更智能的聊天回复（知道有哪些表单可用）
