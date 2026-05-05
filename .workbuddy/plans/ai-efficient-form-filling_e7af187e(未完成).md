---
name: ai-efficient-form-filling
overview: 打通推荐引擎(recommendation_engine)与前端的数据链路，让5策略融合推荐结果真正到达用户界面
todos:
  - id: schema-add-recommendations
    content: FormGenerateRequest 新增 fieldRecommendations 参数，FormField.recommend 兼容对象数组
    status: pending
  - id: chat-enrich-output
    content: chat.py 推荐引擎输出增加 reason/confidence 字段
    status: pending
  - id: api-pass-recommendations
    content: form.py 端点传递 fieldRecommendations 给 FormService
    status: pending
    dependencies:
      - schema-add-recommendations
  - id: formservice-merge
    content: FormService.generate_form() 合并推荐引擎数据，增强 recommend 结构
    status: pending
    dependencies:
      - schema-add-recommendations
      - chat-enrich-output
  - id: frontend-pass-recommendations
    content: ChatAssistant.vue generateForm() 传递 fieldRecommendations 给 /form/generate
    status: pending
  - id: dynamicform-enhance-tags
    content: DynamicForm.vue 推荐标签增强展示（来源样式、原因 tooltip、兼容处理）
    status: pending
    dependencies:
      - formservice-merge
---

## 产品概述

打通推荐引擎与前端的数据链路，使 recommendation_engine 的5策略融合推荐结果能真正到达用户界面

## 核心功能

- 后端 FormGenerateRequest 接收推荐引擎数据，FormService 合并5策略推荐与 HistoryService 推荐
- 前端 ChatAssistant 将 SSE 传来的 fieldRecommendations 传给 /form/generate
- DynamicForm 推荐标签区分来源（AI推荐/历史记录），展示推荐原因

## 技术栈

- 后端：Python + FastAPI + SQLAlchemy
- 前端：Vue 3 + Element Plus
- 数据流：SSE → fetch API → 动态表单渲染

## 实现方案

### 数据流修复

当前断裂链路：`recommendation_engine.batch_recommend()` → SSE `done` 事件 → 前端收到但未传给 `/form/generate` → `FormService` 仅用 `HistoryService` 简单推荐

修复后链路：`recommendation_engine.batch_recommend()` → SSE `done` 事件 → 前端透传 `fieldRecommendations` → `/form/generate` 接收 → `FormService` 合并两种推荐源 → DynamicForm 增强展示

### 推荐合并策略

优先级：推荐引擎 values（5策略融合，更智能）> HistoryService 推荐（DB频次，兜底保障）

- 对每个字段：先取 HistoryService 基础推荐，再用推荐引擎 values 去重合并到前面
- defaultValue 设置：优先 extracted_fields > 推荐引擎首个值 > HistoryService 首个值

### 推荐数据结构增强

当前 `field.recommend[]` 是扁平字符串数组。增强为对象数组：

```
[
  {"value": "年假", "source": "engine", "reason": "您历史填写3次"},
  {"value": "病假", "source": "history", "reason": ""}
]
```

前端兼容处理：若元素为字符串则转为 `{value, source:"history", reason:""}`

## 实现要点

- 后端 `chat.py` 推荐引擎输出结构中 `values` 是字符串列表，`source` 是策略名列表，需在合并时为每个 value 关联 source 和 reason
- 推荐引擎 `RecommendationItem` 含 `reason` 字段（如"历史填写5次"、"您历史填写3次"），但当前 `chat.py` 第750行只取了 `r.value`，丢失了 reason。需同时传递 reason 信息
- 前端 DynamicForm 的 `selectRecommend` 方法需适配新数据结构
- 前端需兼容旧数据格式（纯字符串数组），保证向后兼容

## 目录结构

```
backend/
├── app/schemas/form.py          # [MODIFY] FormGenerateRequest 新增 fieldRecommendations 参数
├── app/api/form.py              # [MODIFY] /form/generate 传递 fieldRecommendations 给 FormService
├── app/services/form_service.py # [MODIFY] generate_form() 合并推荐引擎数据，增强 recommend 结构
├── app/api/chat.py              # [MODIFY] 推荐引擎输出增加 reason 字段（第749-754行）
frontend/
├── src/components/ChatAssistant.vue # [MODIFY] generateForm() 传递 fieldRecommendations
├── src/components/DynamicForm.vue   # [MODIFY] 推荐标签增强展示（来源、原因、样式区分）
```

### 文件详细说明

**backend/app/schemas/form.py** [MODIFY]

- `FormGenerateRequest` 新增 `fieldRecommendations: Optional[Dict[str, Any]] = None`
- `FormField.recommend` 类型从 `List[str]` 改为 `List[Any]`（兼容字符串和对象）

**backend/app/api/chat.py** [MODIFY]

- 第749-754行：推荐引擎输出从只取 `r.value` 改为同时取 `r.value`、`r.source`、`r.reason`、`r.confidence`
- 输出结构从 `{"values": [...], "source": [...]}` 改为 `{"items": [{"value", "source", "reason", "confidence"}], "strategyUsed": [...]}`

**backend/app/api/form.py** [MODIFY]

- 第31-37行：`FormService.generate_form()` 调用增加 `field_recommendations=request.fieldRecommendations` 参数

**backend/app/services/form_service.py** [MODIFY]

- `generate_form()` 方法签名新增 `field_recommendations: Dict[str, Any] = None` 参数
- 第104-111行推荐逻辑重写：先用 HistoryService 获取基础推荐，再用 field_recommendations 合并
- `recommend[]` 数组元素从纯字符串增强为 `{"value", "source", "reason"}` 对象
- defaultValue 设置逻辑：优先 extracted_fields > 推荐引擎高置信度值 > HistoryService 首个值

**frontend/src/components/ChatAssistant.vue** [MODIFY]

- 第711行：`generateForm()` 解构增加 `fieldRecommendations`
- 第722-728行：`/form/generate` 请求 body 增加 `fieldRecommendations` 字段

**frontend/src/components/DynamicForm.vue** [MODIFY]

- 第151-161行：recommend-tags 区域增强渲染
- 新增 `normalizeRecommend()` 方法：兼容字符串和对象两种格式
- 推荐标签按 source 区分样式（AI推荐=紫色/历史=蓝色）
- 高置信度推荐标签加粗显示
- 鼠标悬停显示推荐原因 tooltip
- `selectRecommend()` 方法适配新数据结构