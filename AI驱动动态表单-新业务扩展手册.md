# AI驱动动态表单 - 新业务扩展手册

## 📋 目录

1. [系统架构概述](#系统架构概述)
2. [表单类型编码表](#表单类型编码表)
3. [添加新业务表单的完整步骤](#添加新业务表单的完整步骤)
4. [历史数据推荐功能](#历史数据推荐功能) ⭐ NEW
5. [配置文件详解](#配置文件详解)
6. [Prompt模板说明](#prompt模板说明)
7. [测试与验证](#测试与验证)
8. [常见问题](#常见问题)

---

## 系统架构概述

### 整体架构

```
用户输入 → 前端界面
    ↓
后端API (/api/v1/chat/stream)
    ↓
意图识别 (LLM + SceneRecognitionSkill)
    ↓
┌─ 表单意图 → 字段提取 (+ 历史推荐) → 返回表单Schema + 推荐值
└─ 聊天意图 → 聊天回复 (LLM)
```

### 核心组件

| 组件 | 路径 | 说明 |
|------|------|------|
| LLM Service | `app/services/llm_service.py` | 大模型调用服务 |
| Scene Recognition | `app/skills/scene_recognition.py` | 场景识别（关键词匹配） |
| Field Extraction | `app/skills/field_extraction.py` | 字段提取（正则匹配） |
| **RecommendationEngine** | `app/services/recommendation_engine.py` | **智能推荐引擎（独立组件）** |
| Intent Recognition | `app/core/config_loader.py` | 意图识别Prompt管理 |
| Form Schemas | `backend/config/ontologies/` | 表单Schema定义（实体-字段结构） |

---

## 表单类型编码表

### 现有表单类型

| 编码 | 类型 | 必填字段 | 可选字段 | 描述 |
|------|------|----------|----------|------|
| `leave` | 请假申请 | leave_type, leave_days | reason, start_date, end_date | 员工请假 |
| `expense` | 报销申请 | amount, category | description, receipt_ids | 费用报销 |
| `sales_order` | 销售订单 | customer_name, order_amount | customer_phone, order_date, remark | 销售订单 |
| `tariff_filing_publicity` | 资费备案公示 | bossid, tariff_code | （根据Schema定义） | 资费备案公示 |
| `external_api_demo` | 外部API演示 | - | - | API调用演示 |
| `validation_demo` | 校验演示 | - | - | 表单校验演示 |

### 字段类型定义

| 类型 | 格式 | 示例 |
|------|------|------|
| `string` | 任意文本 | "张三" |
| `input` | 文本输入 | "张三" |
| `textarea` | 多行文本 | "详细描述..." |
| `integer` | 整数 | 3 |
| `number` | 数值 | 123.45 |
| `boolean` | true/false | true |
| `date` | YYYY-MM-DD | 2026-04-17 |
| `datetime` | ISO 8601 | 2026-04-17T10:30:00 |
| `email` | 邮箱格式 | user@example.com |
| `phone` | 手机号 | 13812345678 |
| `enum` | 枚举值 | ["年假", "病假", "事假"] |

---

## 添加新业务表单的完整步骤

### 以"合同审批"为例

假设要添加一个新的业务：**合同审批**（表单编码：`contract`）

### 📝 Step 1: 创建表单Schema文件

**文件路径**: `backend/config/ontologies/contract.json`

```json
{
  "formCode": "contract",
  "formName": "合同审批",
  "description": "公司合同审批流程",
  "entities": [
    {
      "entityCode": "contract_info",
      "entityName": "合同信息",
      "fields": [
        {
          "fieldCode": "contract_name",
          "fieldName": "合同名称",
          "fieldType": "input",
          "required": true,
          "ruleDescription": "合同的完整名称，至少2个字符"
        },
        {
          "fieldCode": "contract_type",
          "fieldName": "合同类型",
          "fieldType": "enum",
          "required": true,
          "enumValues": ["采购合同", "销售合同", "租赁合同", "服务合同", "劳动合同"],
          "ruleDescription": "合同的类型分类"
        },
        {
          "fieldCode": "contract_amount",
          "fieldName": "合同金额",
          "fieldType": "number",
          "required": true,
          "ruleDescription": "正数，最小0.01，最大9999999.99"
        },
        {
          "fieldCode": "party_a",
          "fieldName": "甲方",
          "fieldType": "input",
          "required": true,
          "ruleDescription": "合同甲方公司/个人名称"
        },
        {
          "fieldCode": "party_b",
          "fieldName": "乙方",
          "fieldType": "input",
          "required": true,
          "ruleDescription": "合同乙方公司/个人名称"
        },
        {
          "fieldCode": "sign_date",
          "fieldName": "签订日期",
          "fieldType": "date",
          "required": true,
          "ruleDescription": "合同签订的日期，格式YYYY-MM-DD"
        },
        {
          "fieldCode": "start_date",
          "fieldName": "生效日期",
          "fieldType": "date",
          "required": true,
          "ruleDescription": "合同开始生效的日期"
        },
        {
          "fieldCode": "end_date",
          "fieldName": "到期日期",
          "fieldType": "date",
          "required": false,
          "ruleDescription": "合同到期的日期"
        },
        {
          "fieldCode": "remark",
          "fieldName": "备注",
          "fieldType": "textarea",
          "required": false,
          "ruleDescription": "其他需要说明的事项"
        }
      ]
    }
  ]
}
```

### 📝 Step 2: 更新场景映射

**文件路径**: `backend/config/scenes/scene_mapping.json`

在 `sceneMappings` 数组中添加新的映射：

```json
{
  "sceneMappings": [
    {
      "sceneCode": "contract",
      "keywords": ["合同", "审批", "盖章", "签约", "协议"],
      "priority": 10
    }
  ],
  "defaultScene": "generic"
}
```

### 📝 Step 3: 更新Prompt模板（可选）

**文件路径**: `backend/config/prompts/intent_recognition.txt`

在意图识别关键词中添加新的业务词汇：

```
可识别业务关键词：
- 请假、休假、请假申请（leave）
- 报销、发票、费用（expense）
- 订单、销售、采购（sales_order）
- 资费、备案（tariff_filing_publicity）
- 合同、审批、盖章、签约（contract）  ← 新增
```

**文件路径**: `backend/config/prompts/smart_intent_recognition.txt`

在Prompt模板的 `ontologies` 部分添加新表单信息。

### 📝 Step 4: 添加静态推荐值（可选）

**文件路径**: `backend/config/templates/recommendations.json`

```json
{
  "recommendations": {
    "contract": {
      "contract_type": ["采购合同", "销售合同", "服务合同"],
      "party_a": ["本公司名称"],
      "remark": ["常规合同", "加急处理", "需法务审核"]
    }
  }
}
```

### 📝 Step 5: 重启服务

```bash
# 停止当前服务（Ctrl+C）

# 重新启动
start-all.bat
```

### 📝 Step 6: 测试验证

#### 方式1：使用API测试端点

访问：`http://localhost:6173/api/v1/test/llm-call`

或者用curl：

```bash
curl -X POST http://localhost:6173/api/v1/test/llm-call
```

#### 方式2：使用聊天界面

在聊天窗口输入：

```
我想创建一个销售合同，合同名称是"设备采购合同"，金额50万
```

预期输出：
- 识别为 `form` 意图
- 表单类型为 `contract`
- 提取到字段：`contract_name`, `contract_amount`, `contract_type` 等

#### 方式3：查看日志

在后端终端查看详细的LLM调用日志。

---

## 历史数据推荐功能 ⭐

### 功能概述

**RecommendationEngine** 是独立的智能推荐组件，基于历史填写数据生成推荐列表。

**核心能力**：
1. **多策略融合**：频率分析、用户个性化、时间衰减、上下文感知
2. **灵活配置**：推荐条数、策略权重、数据源可配置
3. **结构化输出**：详细的推荐结果和元数据
4. **批量推荐**：支持一次为多个字段生成推荐

### 工作原理

```
用户输入 → 意图识别 → 字段提取
    ↓
推荐引擎（多策略融合）
    ├── frequency: 历史频率分析
    ├── user_personalized: 用户个性化
    ├── time_decay: 时间衰减
    └── context_aware: 上下文感知
    ↓
返回推荐结果列表
```

### 推荐策略详解

| 策略 | 权重 | 说明 |
|------|------|------|
| `frequency` | 40% | 基于历史填写频率，次数越多得分越高 |
| `user_personalized` | 40% | 同一用户的历史记录优先推荐 |
| `time_decay` | 20% | 近期数据权重更高，时间越近得分越高 |
| `static` | 兜底 | 无历史数据时使用配置文件默认值 |

### 代码实现

**服务类**: `app/services/recommendation_engine.py`

**全局实例获取**:
```python
from app.services.recommendation_engine import get_recommendation_engine

# 获取推荐引擎实例
engine = get_recommendation_engine()
```

**单字段推荐**:
```python
result = engine.recommend(
    form_code="sales_order",      # 表单编码
    field_code="customer_name",   # 字段编码
    user_input="我想订一批货",      # 用户当前输入
    user_id="user123",            # 用户ID（可选）
    conversation_context={},      # 对话上下文（可选）
    max_recommendations=5,        # 最大推荐条数
    strategies=["frequency", "user_personalized", "time_decay"],
    db=db_session                 # 数据库会话
)

# result 是 RecommendationResult 对象
if result.success:
    for item in result.recommendations:
        print(f"{item.value} (score={item.score}, confidence={item.confidence})")
```

**批量推荐**:
```python
results = engine.batch_recommend(
    form_code="sales_order",
    extracted_fields={"customer_name": "张三", "order_amount": "5000"},
    user_input="我想订一批货",
    user_id="user123",
    max_per_field=5,
    db=db_session
)

# results 是 Dict[field_code, RecommendationResult]
for field_code, result in results.items():
    if result.success:
        print(f"{field_code}: {[r.value for r in result.recommendations]}")
```

### 数据结构

**RecommendationResult**:
```python
@dataclass
class RecommendationResult:
    success: bool                    # 是否成功
    field_code: str                 # 字段编码
    recommendations: List[RecommendationItem]  # 推荐列表
    total_candidates: int           # 候选总数
    strategy_used: List[str]        # 使用的策略
    processing_time_ms: float       # 处理耗时
    error: Optional[str]            # 错误信息
```

**RecommendationItem**:
```python
@dataclass
class RecommendationItem:
    value: str           # 推荐值
    field_code: str      # 字段编码
    score: float         # 综合评分
    source: str          # 来源: "history", "static", "context"
    confidence: float     # 置信度 0-1
    match_type: str      # 匹配类型: "exact", "fuzzy", "inferred"
    reason: str          # 推荐原因
    metadata: Dict       # 附加元数据
```

### 评分算法

系统使用加权评分算法计算推荐值的优先级：

**计算公式**：
```
final_score = count_score × 0.4 + user_score × 0.4 + time_score × 0.2
```

| 因素 | 权重 | 计算方式 |
|------|------|----------|
| 频率得分 | 40% | `min(count × 0.1, 1.0)` |
| 用户得分 | 40% | `min(user_count × 0.2, 1.0)` |
| 时间得分 | 20% | `max(0, 1.0 - days_since / 30)` |

### 配置文件

**文件路径**: `backend/config/app_config.json`

```json
{
  "recommendation": {
    "recommendationLimit": 5,
    "historyQueryLimit": 1000,
    "countScoreWeight": 0.4,
    "userScoreWeight": 0.4,
    "timeScoreWeight": 0.2,
    "timeDecayDays": 30,
    "countScorePerUnit": 0.1,
    "userScorePerUnit": 0.2,
    "recentDaysThreshold": 90
  }
}
```

### 静态配置兜底

**文件路径**: `backend/config/templates/recommendations.json`

```json
{
  "recommendations": {
    "sales_order": {
      "customer_name": ["张三", "李四", "王五"],
      "customer_phone": ["13800138000", "13900139000"],
      "order_amount": ["5000", "10000", "20000"],
      "remark": ["常规订单", "加急处理", "月度结算"]
    }
  }
}
```

### 在 Chat API 中集成

推荐引擎已集成到流式聊天API中（`chat.py`），自动为已提取的字段生成推荐：

```python
# 构建对话上下文
conversation_context = {
    "messages": [{"role": msg.role, "content": msg.content} for msg in request.messages],
    "extractedFields": extracted,
    "lastUserMessage": last_user_message
}

# 批量推荐
recommendations_result = recommendation_engine.batch_recommend(
    form_code=form_code,
    extracted_fields=extracted,
    user_input=last_user_message,
    user_id=request.userId,
    conversation_context=conversation_context,
    max_per_field=5,
    db=db
)

# 将推荐数据添加到响应
intent_data["fieldRecommendations"] = all_recommendations
```

### API测试

```bash
# 测试推荐引擎（通过聊天API间接测试）
curl -X POST http://localhost:6173/api/v1/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"我想创建销售订单"}]}'
```

预期响应中会包含 `fieldRecommendations` 字段。

---

## 配置文件详解

### Schema文件结构

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
          "fieldCode": "string",  // 字段编码（英文小写）
          "fieldName": "string",  // 字段显示名称（中文）
          "fieldType": "string",  // 字段类型
          "required": true/false, // 是否必填
          "min": 0,              // 最小值（number类型）
          "max": 1000000,        // 最大值（number类型）
          "enumValues": [...],    // 枚举值（enum类型）
          "pattern": "正则表达式", // 自定义验证正则
          "ruleDescription": "字符串" // 验证规则描述（用于LLM校验）
        }
      ]
    }
  ]
}
```

### 场景映射文件结构

**文件路径**: `backend/config/scenes/scene_mapping.json`

```json
{
  "sceneMappings": [
    {
      "sceneCode": "string",      // 场景编码（与formCode一致）
      "keywords": ["keyword1", "keyword2"], // 触发关键词
      "priority": 10              // 优先级（数字越小优先级越高）
    }
  ],
  "defaultScene": "generic"       // 默认场景
}
```

### Prompt模板变量说明

意图识别Prompt支持以下变量：

| 变量 | 说明 | 示例 |
|------|------|------|
| `{ontologies_info}` | 所有表单的Schema信息 | JSON格式的表单列表 |
| `{scene_keywords}` | 场景关键词映射 | JSON格式的关键词 |
| `{separators}` | 分隔符定义 | ，、；等 |
| `{messages_text}` | 对话历史 | "user: xxx\nassistant: xxx" |
| `{last_user_message}` | 最后一条用户消息 | "我想请假" |
| `{field_recommendations}` | 历史推荐数据（可选） | {"field": ["value1", "value2"]} |

---

## Prompt模板说明

### 意图识别流程

```
用户输入 → SceneRecognitionSkill（关键词匹配）
    ↓
如果匹配成功 → 返回表单类型
    ↓
如果匹配失败 → LLM意图识别
    ↓
┌─ form → 字段提取 + 历史推荐
└─ chat → 闲聊回复
```

### Prompt设计原则

1. **清晰的角色定义**: AI作为智能表单助手
2. **完整的上下文**: 提供所有可用表单信息
3. **明确的输出格式**: JSON结构化输出
4. **合理的约束**: 置信度阈值、正则表达式
5. **历史参考**: 支持推荐值作为参考

### 示例Prompt结构

```
你是智能表单助手。用户会描述他们的需求，你需要识别：
1. 是否需要填写表单（form）还是闲聊（chat）
2. 如果是表单，识别具体类型并提取字段

## 可识别的表单类型：
{ontologies_info}

## 场景关键词：
{scene_keywords}

## 历史推荐数据（参考）：
{field_recommendations}

## 输出格式要求：
- intentType: "form" 或 "chat"
- formCode: 表单编码（仅form时）
- extractedFields: 提取的字段名值对
- fieldRecommendations: 推荐值（可选）
- confidence: 置信度（0-1）

## 当前对话：
{messages_text}

## 最后一条用户消息：
{last_user_message}
```

---

## 测试与验证

### 功能测试清单

- [ ] Schema文件语法正确（JSON格式）
- [ ] 场景映射配置正确
- [ ] 表单可以被正确识别
- [ ] 字段提取完整
- [ ] 流式输出正常
- [ ] 日志记录完整
- [ ] 历史推荐功能正常 ⭐ NEW
- [ ] 推荐值正确显示 ⭐ NEW

### API测试命令

```bash
# 健康检查
curl http://localhost:6173/api/v1/health

# 测试意图识别
curl -X POST http://localhost:6173/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"我想创建合同"}]}'

# 测试流式输出
curl -X POST http://localhost:6173/api/v1/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"我想创建合同"}]}'

# 测试历史推荐
curl -X POST http://localhost:6173/api/v1/history/getRecommendValues \
  -H "Content-Type: application/json" \
  -d '{"formCode":"sales_order","fieldCode":"customer_name"}'
```

### 日志位置

| 日志类型 | 路径 |
|----------|------|
| 应用日志 | `backend/logs/app.log` |
| 终端输出 | 标准输出（开发模式） |

---

## 常见问题

### Q1: 新表单无法识别

**可能原因**：
- Schema文件JSON格式错误
- 关键词没有覆盖用户输入
- LLM识别置信度不够

**解决方法**：
1. 检查Schema文件语法：`python -c "import json; json.load(open('contract.json'))"`
2. 添加更多关键词到 `scene_mapping.json`
3. 在Prompt中添加更多示例

### Q2: 字段提取不完整

**可能原因**：
- Prompt中没有说明该字段
- 用户输入中没有提到该字段
- 字段名称不够直观

**解决方法**：
1. 在Schema的 `ruleDescription` 中添加详细说明
2. 提供更多示例让LLM学习
3. 降低该字段的 `required` 要求

### Q3: 识别为chat而不是form

**可能原因**：
- 用户输入不够明确
- 关键词匹配失败
- LLM判断错误

**解决方法**：
1. 检查 `scene_mapping.json` 配置
2. 调整LLM的Prompt模板
3. 提高form识别的优先级

### Q4: 端口被占用

**解决方法**：
```bash
# 查找占用端口的进程
netstat -ano | findstr :6173

# 结束进程
taskkill /F /PID <进程ID>
```

### Q5: 历史推荐为空

**可能原因**：
- 数据库中没有该表单的历史记录
- 新用户第一次使用该表单
- 静态配置中也未定义

**解决方法**：
1. 确保 `config/templates/recommendations.json` 中有该表单的配置
2. 用户填写表单后，数据会自动保存到历史
3. 查看日志确认历史查询是否成功
4. 检查 `app_config.json` 中 `recommendation` 配置是否正确

### Q6: 历史推荐不准确

**可能原因**：
- 历史数据太少
- 推荐算法权重不合理
- 时间衰减设置不当

**解决方法**：
1. 调整 `app_config.json` 中的 `countScoreWeight`、`userScoreWeight`、`timeScoreWeight` 权重配置
2. 增加静态配置中的推荐值作为兜底
3. 等待历史数据积累
4. 调整 `timeDecayDays` 控制时间衰减速度

---

## 📞 扩展建议

### 高级功能

1. **自定义验证规则**: 在Schema中添加 `ruleDescription` 字段（已支持）
2. **级联字段**: 字段之间建立依赖关系
3. **智能推荐**: 基于相似用户的历史数据推荐 ⭐ 已实现（RecommendationEngine）
4. **多语言支持**: 扩展国际化能力

### 性能优化

1. **缓存Schema**: 避免重复加载
2. **批量验证**: 减少API调用
3. **流式处理**: 实时显示识别进度
4. **推荐缓存**: 缓存历史推荐结果 ⭐ NEW

---

**手册版本**: v2.3  
**新增功能**: 推荐引擎配置说明、场景映射格式更新、Schema结构规范  
**最后更新**: 2026-05-07  
**维护团队**: AI Team