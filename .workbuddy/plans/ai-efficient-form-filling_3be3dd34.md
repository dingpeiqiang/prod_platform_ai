---
name: ai-efficient-form-filling
overview: 重构推荐系统为三层智能架构：①本体规则推理（LLM理解ruleDescription+options生成推荐）②静态默认值兜底 ③历史关联推断（已知字段推断未知字段），并打通数据链路到达前端
todos:
  - id: schema-and-config
    content: schemas/form.py 增加 fieldRecommendations + system_config.json 增加三层推荐开关
    status: completed
  - id: prompt-enhance
    content: smart_intent_recognition.txt fieldRecommendations 格式扩展为带 reason 的对象数组
    status: completed
  - id: chat-engine-enrich
    content: chat.py 推荐引擎输出增强 + recommendation_engine 修复去重bug/全字段推荐/条件过滤
    status: completed
    dependencies:
      - prompt-enhance
  - id: history-bugfix
    content: history_service.py 修复 _get_db_recommendations 缺少 form_code 过滤
    status: completed
  - id: formservice-three-layers
    content: FormService.generate_form() 实现三层推荐合并 + _merge_recommendations()
    status: completed
    dependencies:
      - schema-and-config
      - chat-engine-enrich
      - history-bugfix
  - id: api-pass-through
    content: form.py 传递 fieldRecommendations 给 FormService
    status: completed
    dependencies:
      - schema-and-config
  - id: frontend-pipeline
    content: ChatAssistant.vue 透传 fieldRecommendations + DynamicForm.vue 增强推荐标签展示
    status: completed
---

## 用户需求

用户认为当前推荐引擎只是频次统计，不够智能。需要实现**三层递进式智能推荐**：

1. **本体规则推理**：根据本体的 ruleDescription（填写规则）+ options（枚举选项），利用 LLM 理解规则后为未填写字段生成推荐值
2. **静态默认值**：本体配置的默认值/常用选项，优先级低，作为兜底
3. **历史关联推断**：不是简单的频次统计，而是"根据已知推断未知"——当已知部分字段值时，从历史数据中找到相似记录，推断其他字段的条件概率分布

## 产品概述

将表单推荐从"频次统计"升级为"三层智能推荐"。核心能力：

- 第一层：LLM 理解本体规则，根据已填字段和规则推理出未填字段的推荐值（如：已知 leave_type=病假 → 推断 reason 应含"身体"相关）
- 第二层：静态默认值作为低优先级备选
- 第三层：基于历史数据的关联推断——按已知字段过滤历史记录，统计目标字段的条件概率分布（如：历史中 leave_type=年假 时，start_date 集中在周一）

## 核心功能

- 后端新增三层推荐引擎，替代当前单一频次统计
- 打通推荐数据链路：chat.py → SSE → 前端 → /form/generate → FormService → DynamicForm
- 推荐结果携带来源标识和推荐原因（source + reason），前端按来源区分样式
- 推荐数据结构从扁平字符串数组升级为对象数组，向后兼容

## 技术栈

- 后端：Python + FastAPI + SQLAlchemy（复用现有）
- 前端：Vue 3 + Element Plus（复用现有）
- LLM：复用现有 LLMService 的 `_call_llm_sync_with_reasoning()` 同步调用
- 数据流：SSE → fetch API → 动态表单渲染（复用现有）

## 实现方案

### 三层推荐架构

```
用户输入 → LLM 意图识别 → extractedFields
                                    ↓
                    ┌───────────────────────────────┐
                    │  三层推荐引擎 (FormService 内)   │
                    │                               │
                    │  Layer 1: 本体规则推理 (LLM)    │  ← ruleDescription + options + 已填字段
                    │  Layer 2: 静态默认值 (配置)      │  ← options / static 推荐
                    │  Layer 3: 历史关联推断 (SQL)     │  ← 按已知字段过滤 → 条件概率
                    │                               │
                    │  合并去重 → source + reason     │
                    └───────────────────────────────┘
                                    ↓
              field.recommend[] → DynamicForm 增强展示
```

### Layer 1：本体规则推理（LLM 驱动）

**核心思路**：构造一个轻量 prompt，让 LLM 根据本体的 ruleDescription、options 和已填字段值，推理出未填字段的推荐值。

**实现方式**：

- 在 FormService.generate_form() 中，当 extracted_fields 不完整时（有未填的必填字段），构造一次 LLM 调用
- Prompt 输入：本体全部字段定义（含 ruleDescription + options）+ 已填字段值 + 当前日期
- Prompt 要求：为每个未填字段生成最多 3 个推荐值，并说明推理依据
- 输出格式：`{"field_code": [{"value": "xxx", "reason": "基于...规则推断"}]}`

**关键决策——为什么用 LLM 而非规则引擎**：

- ruleDescription 是自然语言（如"餐饮费单次不超过5000，差旅费不超过50000"），规则引擎无法直接解析
- LLM 已在意图识别阶段证明能理解 ruleDescription（smart_intent_recognition.txt 的字段校验任务）
- 一次 LLM 调用可覆盖所有未填字段，不逐字段调用，延迟可控

**延迟优化**：

- 将本体规则推理和字段提取合并为同一次 LLM 调用（扩展 smart_intent_recognition prompt 的输出格式），避免额外 LLM 调用
- 备选方案：若不想改 intent recognition prompt，则在 FormService 中新增一次独立 LLM 调用（约 1-3s 延迟）

**选择方案**：扩展 intent recognition prompt。理由：

1. 意图识别阶段 LLM 已经看到了完整的本体定义和用户输入，完全有能力同时输出字段推荐
2. 零额外 LLM 调用，零额外延迟
3. prompt 中已有 `fieldRecommendations` 字段定义（第93-96行），但当前只是简单数组，需扩展为带 reason 的结构

### Layer 2：静态默认值（配置驱动）

**核心思路**：本体 options 本身就是最基础的推荐来源。

**实现方式**：

- 对于 select/radio/checkbox 类型的字段，options 数组直接作为 source=static 的推荐
- 对于有 ruleDescription 的字段，config_loader.get_recommendations() 返回的静态推荐值作为兜底
- 优先级最低：仅当 Layer 1 和 Layer 3 都没有推荐时展示

### Layer 3：历史关联推断（SQL 条件查询）

**核心思路**：不是"这个字段历史上最常填什么"，而是"当其他字段是这些值时，这个字段通常是什么"。

**实现方式**：

- 修改 recommendation_engine 的 `_get_history_recommendations()` 和 `_get_user_personalized_recommendations()`
- 新增条件过滤：如果 extracted_fields 中有值，先按已知字段过滤 FormInstance，再统计目标字段分布
- SQL 查询改为：先查 form_code 对应的 FormInstance，再在 Python 中按已知字段过滤（避免复杂 SQL）
- 输出 reason 从"历史填写N次"改为"基于N条相似记录推断"

**修复已有 bug**：

- HistoryService._get_db_recommendations() 缺少 form_code 过滤 → 添加 template_id 过滤条件
- recommendation_engine 去重逻辑（第268行 dict 覆盖问题）→ 改为取最高分
- batch_recommend 只对 extracted_fields key 做推荐 → 改为对本体所有字段做推荐

### 推荐合并策略

对每个字段，按优先级合并三层推荐，去重后输出：

```
优先级：LLM extractedFields(精确值) > Layer1(规则推理) > Layer3(关联推断) > Layer2(静态)
```

合并逻辑：

1. 收集三层推荐结果，每条带 `{value, source, reason, confidence}`
2. 按 value 去重：同一 value 取最高优先级的 source 和 reason
3. 按 source 优先级排序
4. defaultValue 设置：extracted_fields > Layer1 高置信值 > Layer3 首值 > Layer2 首值

### 数据流修复（打通链路）

当前 chat.py 的推荐引擎输出通过 SSE `done` 事件传给前端，但：

- chat.py 第749-754行只取 r.value 丢失了 reason
- ChatAssistant.vue generateForm() 忽略了 fieldRecommendations
- /form/generate 不接收 fieldRecommendations
- FormService 只用 HistoryService

修复：

1. chat.py：推荐引擎输出同时取 value + source + reason + confidence
2. ChatAssistant.vue：generateForm() 透传 fieldRecommendations 给 /form/generate
3. form.py schema：FormGenerateRequest 增加 fieldRecommendations 参数
4. form.py api：传递给 FormService
5. FormService：合并三层推荐数据到 field.recommend[]

### 推荐数据结构增强

当前 `field.recommend[]` 是 `List[str]`。增强为对象数组：

```
[
  {"value": "年假", "source": "llm_rule", "reason": "根据请假类型推断年假最常见", "confidence": 0.8},
  {"value": "病假", "source": "inference", "reason": "基于5条相似记录推断", "confidence": 0.6},
  {"value": "事假", "source": "static", "reason": "常用选项", "confidence": 0.3}
]
```

前端兼容：若元素为字符串则转为 `{value, source:"static", reason:"", confidence:0}`

## 实现要点

### 性能

- Layer 1（LLM 规则推理）通过扩展 intent recognition prompt 实现，零额外调用
- Layer 3（历史关联推断）在现有 recommendation_engine.batch_recommend() 基础上修改，不增加额外 DB 查询
- 推荐数据通过 SSE 传到前端后缓存，/form/generate 时复用，不重复计算

### 向后兼容

- field.recommend[] 兼容字符串和对象两种格式
- 如果 LLM 调用失败或返回格式错误，降级到 Layer3+Layer2
- HistoryService 保持原有接口不变，仅在内部修复 bug

### 扩展性

- 三层推荐引擎独立解耦，每层可单独开关（通过 system_config.json 配置）
- 新增表单类型时，只要本体有 ruleDescription/options 就自动获得 Layer1+Layer2 推荐
- Layer3 随历史数据积累自动增强

## 目录结构

```
backend/
├── app/schemas/form.py                    # [MODIFY] FormGenerateRequest 新增 fieldRecommendations, FormField.recommend 兼容对象
├── app/api/chat.py                        # [MODIFY] 推荐引擎输出结构增强（value+source+reason+confidence），扩展 intent prompt 的 fieldRecommendations 格式
├── app/api/form.py                        # [MODIFY] /form/generate 传递 fieldRecommendations 给 FormService
├── app/services/form_service.py           # [MODIFY] generate_form() 合并三层推荐，新增 _merge_recommendations() 方法
├── app/services/recommendation_engine.py  # [MODIFY] 修复去重bug，batch_recommend 改为全字段，新增条件过滤推断
├── app/services/history_service.py        # [MODIFY] 修复 _get_db_recommendations 缺少 form_code 过滤 bug
├── config/prompts/smart_intent_recognition.txt  # [MODIFY] fieldRecommendations 输出格式扩展为带 reason 的结构
├── config/system_config.json              # [MODIFY] 新增三层推荐开关配置
frontend/
├── src/components/ChatAssistant.vue       # [MODIFY] generateForm() 透传 fieldRecommendations
├── src/components/DynamicForm.vue         # [MODIFY] 推荐标签增强展示（来源颜色、原因tooltip、兼容处理）
```

### 文件详细说明

**backend/app/schemas/form.py** [MODIFY]

- FormGenerateRequest 新增 `fieldRecommendations: Optional[Dict[str, Any]] = None`
- FormField.recommend 类型从 `List[str]` 改为 `List[Any]`

**backend/config/prompts/smart_intent_recognition.txt** [MODIFY]

- fieldRecommendations 输出格式从 `["值1", "值2"]` 改为 `[{"value": "值1", "reason": "推理依据"}]`
- 新增推理指引：要求 LLM 根据本体 ruleDescription 和已提取字段推理未填字段的推荐值

**backend/app/api/chat.py** [MODIFY]

- 第749-754行：推荐引擎输出从只取 r.value 改为取完整 RecommendationItem（value + source + reason + confidence）
- 输出结构改为 `{"items": [{value, source, reason, confidence}], "strategyUsed": [...]}`

**backend/app/api/form.py** [MODIFY]

- 第31-37行：FormService.generate_form() 调用增加 field_recommendations 参数

**backend/app/services/form_service.py** [MODIFY]

- generate_form() 方法签名新增 field_recommendations 参数
- 新增 `_merge_recommendations()` 方法：三层推荐合并 + 去重 + 排序
- recommend[] 数组元素增强为 `{value, source, reason, confidence}` 对象
- defaultValue 逻辑：extracted_fields > llm_rule 高置信值 > inference 首值 > static 首值

**backend/app/services/recommendation_engine.py** [MODIFY]

- 第268行去重 bug 修复：dict 覆盖改为取最高分
- batch_recommend() 改为接受 field_codes 列表参数（而非仅 extracted_fields.keys()），支持全字段推荐
- _get_history_recommendations() 新增 extracted_fields 条件过滤：按已知字段值过滤历史记录后统计
- reason 从"历史填写N次"改为"基于N条相似记录推断"（有条件过滤时）

**backend/app/services/history_service.py** [MODIFY]

- _get_db_recommendations() 第72-76行：添加 template_id 过滤条件（通过 form_code 查 FormTemplate 获取）

**backend/config/system_config.json** [MODIFY]

- recommendation 节点新增三层开关：`enableRuleInference: true`, `enableStaticFallback: true`, `enableHistoryInference: true`

**frontend/src/components/ChatAssistant.vue** [MODIFY]

- 第711行：generateForm() 解构增加 fieldRecommendations
- 第722-728行：/form/generate 请求 body 增加 fieldRecommendations

**frontend/src/components/DynamicForm.vue** [MODIFY]

- 第151-161行：recommend-tags 增强渲染
- 新增 normalizeRecommend() 兼容方法
- 推荐标签按 source 区分样式：llm_rule=紫色, inference=蓝色, static=灰色
- el-tag 添加 title 属性展示 reason tooltip
- selectRecommend() 适配新数据结构（取 item.value）