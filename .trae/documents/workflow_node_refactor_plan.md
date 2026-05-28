# 工作流节点接口规范重构方案（v3.0）

> 基于 Flowable BPMN 引擎架构的学习与借鉴

---

## 一、Flowable 核心架构分析

### 1.1 四层架构模型

| 层级 | Flowable | 我们的现状 |
|------|----------|-----------|
| **持久层** | ACT_RE_*（定义）、ACT_RU_*（运行时）、ACT_HI_*（历史） | 无标准化持久化 |
| **核心引擎层** | RepositoryService / RuntimeService / TaskService / HistoryService / ManagementService | `WorkflowEngine` 一个类承担所有 |
| **服务层** | Java API + REST API 双接口 | 有 REST API 但无 Java Service 层 |
| **扩展层** | SPI 插件、自定义 JavaDelegate | 节点注册表已有，但无 SPI 机制 |

### 1.2 关键设计模式

#### 模式一：Service Task + JavaDelegate

Flowable 中最接近我们"节点"的概念：

```java
// Flowable: 自定义服务任务
public class PaymentService implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        // 通过 execution 获取输入变量
        String orderId = (String) execution.getVariable("orderId");
        // 业务逻辑...
        // 通过 execution 设置输出变量
        execution.setVariable("paymentResult", result);
    }
}
```

**核心要点**：
- `JavaDelegate` 是一个**单方法接口**（`execute`）
- `DelegateExecution` 提供**结构化变量访问**（getVariable/setVariable）
- 输入输出通过**变量映射**完成，不是直接传参

#### 模式二：BPMN Palette

Flowable 的自定义后端任务通过 **palette JSON** 定义输入输出 schema：

```json
{
  "id": "custom-payment-task",
  "name": "支付处理",
  "formProperties": [
    {"id": "orderId", "name": "订单ID", "type": "string", "required": true},
    {"id": "amount", "name": "金额", "type": "double", "required": true}
  ],
  "outputProperties": [
    {"id": "paymentResult", "name": "支付结果", "type": "string"}
  ]
}
```

#### 模式三：变量映射（Variable Mapping）

```xml
<serviceTask id="paymentTask" flowable:class="com.PaymentService">
  <extensionElements>
    <flowable:inputOutput>
      <flowable:inputParameter name="orderId">${orderId}</flowable:inputParameter>
      <flowable:outputParameter name="paymentResult">${paymentResult}</flowable:outputParameter>
    </flowable:inputOutput>
  </extensionElements>
</serviceTask>
```

---

## 二、关键问题：动态出入参如何处理

### 2.1 两种层级的"动态"

```
层级一：参数值动态（常见，容易处理）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
节点声明了参数名，但值由用户配置

  声明: input_schema = {"prompt": ParamSchema(type="str")}
  配置: {"prompt": "请帮我翻译以下内容..."}
  引擎: execution.get("prompt") → 得到配置值
  ✅ schema 定义时已知参数名


层级二：参数名也动态（我们的核心场景）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
参数名本身由用户在编辑器中动态指定

  set_variable 节点:
    用户配置: variable_name="my_var", variable_value="hello"
    期望输出: execution.set("my_var", "hello")
    ❌ schema 定义时不知道参数名

  generate_form 节点:
    用户配置的字段名是可变的
    ❌ schema 定义时不知道输出字段名

  merge_results 节点:
    合并前面多个节点的输出，输出名 = 输入节点名
    ❌ schema 定义时不知道合并哪些节点
```

### 2.2 Flowable 是怎么处理的？

Flowable 的 BPMN **其实不直接支持"参数名动态"**。它的做法是：

```
传统 BPMN 工作流 vs 我们的 LLM 工作流
─────────────────    ────────────────
流程路径固化的                流程路径固化，但每个节点的
审批流程                      输入输出参数可能是动态的

Flowable 场景:              我们的场景:
  ServiceTask 的输入           LLM 调用的 prompt 模板
  输出都是预先定义好的           是由用户写的，包含哪些
  变量名在 BPMN 中固定          变量占位符是动态的

Flowable 应对方式:
  使用 execution.setVariable(name, value)
  虽然 schema 是静态的，但变量系统本身支持任意键
  引擎不会限制你只能 set schema 里声明的变量
```

所以 Flowable 的实际做法是：
1. **Palette schema** 只声明静态的配置参数（如 variable_name, variable_value 字段）
2. **变量系统** 本身是 KV 存储，不限制键名
3. 节点通过 `execution.setVariable(dynamic_name, value)` 写出动态变量名
4. 引擎**不做 schema 校验**，只收集所有 set 过的变量

### 2.3 我们的设计方案

借鉴 Flowable 的做法，结合我们的 LLM 场景，采用**动静分离**策略：

```
┌──────────────────────────────────────────────────────┐
│                  节点的"两种角色"                      │
├──────────────────────────────────────────────────────┤
│                                                      │
│  ① Palette Schema（面向编辑器）                       │
│     声明：这个节点在编辑器中长什么样                     │
│     包括：静态配置字段 + 动态输出标记                    │
│                                                      │
│  ② Execution 变量系统（面向引擎）                      │
│     运行时：节点可以 set 任意变量名                     │
│     引擎：通过 output_mapping 决定哪些变量流入上下文      │
│                                                      │
└──────────────────────────────────────────────────────┘
```

#### 具体实现

```python
class WorkflowNode(ABC):
    """工作流节点基类"""

    # ================================================================
    # 面向编辑器：Palette Schema（静态声明）
    # ================================================================
    name: str = ""
    display_name: str = ""
    description: str = ""

    # 这些是编辑器中需要用户填写的配置字段
    # 例如 set_variable 节点只有两个配置字段：
    #   variable_name, variable_value
    config_fields: Dict[str, ParamSchema] = {}

    # 节点的固定输出（非动态部分）
    output_fields: Dict[str, ParamSchema] = {}

    # 标记：该节点是否有动态输出
    has_dynamic_output: bool = False

    @classmethod
    def get_palette_schema(cls) -> dict:
        """给编辑器用的完整 schema"""
        return {
            "name": cls.name,
            "display_name": cls.display_name,
            "description": cls.description,
            "config_fields": {k: v.to_dict() for k, v in cls.config_fields.items()},
            "output_fields": {k: v.to_dict() for k, v in cls.output_fields.items()},
            "has_dynamic_output": cls.has_dynamic_output,
        }

    # ================================================================
    # 面向引擎：核心执行
    # ================================================================
    async def execute(self, execution: 'DelegateExecution') -> None:
        """节点执行

        节点通过 execution.get/set 读写变量。
        变量名可以是任意的（支持动态）。
        """
        ...

    # （可选）节点可根据配置返回动态输出声明的字段名
    def get_dynamic_outputs(self, config_data: dict) -> Dict[str, ParamSchema]:
        """返回动态输出 schema

        引擎调用此方法获知节点会输出哪些动态变量。
        主要用于 output_mapping 的自动生成。

        例如 set_variable 节点：
          config_data = {"variable_name": "my_var"}
          → 返回 {"my_var": ParamSchema(type="any", description="")}
        """
        return {}


class DelegateExecution:
    """委托执行上下文"""

    def __init__(self, context: ExecutionContext, step_def: StepDefinition):
        self._context = context
        self._step_def = step_def
        self._variables: Dict[str, Any] = {}

    def get(self, name: str, default=None) -> Any:
        """获取变量"""
        val = self._variables.get(name)
        if val is not None:
            return val
        return self._context.get_variable(name, default)

    def set(self, name: str, value: Any):
        """设置变量（支持动态参数名）"""
        self._variables[name] = value

    @property
    def variables(self) -> Dict[str, Any]:
        return dict(self._variables)
```

### 2.4 各种节点的动态性分类

| 节点类型 | 动态性级别 | 处理方式 |
|----------|-----------|---------|
| **call_llm** | 值动态 | `input_schema` 固定字段（prompt, model, temperature），值在编辑器配 |
| **http_request** | 值动态 | 固定字段（url, method, headers, body） |
| **execute_code** | 值动态 | 固定字段（code, language） |
| **query_knowledge** | 值动态 | 固定字段（query, knowledge_base） |
| **set_variable** | **名+值都动态** | `config_fields` 声明 variable_name/variable_value；`get_dynamic_outputs()` 返回动态变量名 |
| **generate_form** | **名+值都动态** | `get_dynamic_outputs()` 根据配置的字段列表动态声明输出 |
| **merge_results** | **名+值都动态** | 输出 = 各输入节点 ID + 内容 |
| **workflow_start** | 值动态 | `config_fields` 声明 initial_params，输出是当前上下文 |
| **workflow_end** | 值动态 | `output_fields` 声明输出参数列表 |

### 2.5 引擎执行流程

```
引擎执行 ACTION 步骤时：
┌───────────────────────────────────────────────────┐
│ 1. 创建 DelegateExecution                          │
│    → 加载 context.outputs 到 execution             │
├───────────────────────────────────────────────────┤
│ 2. apply_input_mapping(step_def, execution)        │
│    → 将 config_fields 中的值填充到 execution        │
│      （这部分是编辑器配好的，直接按字段名映射）         │
├───────────────────────────────────────────────────┤
│ 3. node.execute(execution)                        │
│    → 节点通过 execution.get/set 读写变量            │
│    → 动态变量名直接 execution.set("my_var", val)    │
├───────────────────────────────────────────────────┤
│ 4. collect_outputs(step_def, node, execution)      │
│    → 收集所有 execution.set 过的变量                │
│      - 静态 output_fields 里的变量 → 一定收集        │
│      - 动态变量 → 根据 get_dynamic_outputs() 收集    │
│      - 其余不声明的变量也透传到 context               │
└───────────────────────────────────────────────────┘
```

### 2.6 编辑器如何处理动态节点

以 set_variable 节点为例：

```
编辑器显示：
┌─ 设置变量 ──────────────────────┐
│  变量名: [my_var          ]     │ ← config_fields: variable_name
│  变量值: [hello            ]    │ ← config_fields: variable_value
│  来源:   [前一个节点输出 ▼  ]    │ ← config_fields: source
├─────────────────────────────────┤
│  输出预览:                       │
│  📤 my_var  → 上下文            │ ← 编辑器提示：此节点会动态输出 my_var
└─────────────────────────────────┘

编辑器保存的 JSON：
{
  "type": "workflow.set_variable",
  "config": {
    "variable_name": "my_var",
    "variable_value": "hello",
    "source": "__node_output__"
  }
}

转换器生成 StepDefinition 时调用：
  node.get_dynamic_outputs({"variable_name": "my_var"})
  → 得到 {"my_var": ParamSchema(type="any")}
  → 存入 step_def.dynamic_outputs

引擎执行时：
  execution.set("my_var", {resolved_value})
  → engine 在 collect_outputs 时根据 dynamic_outputs 收集 my_var
  → context.outputs["my_var"] = resolved_value
```

### 2.7 总结

| 场景 | Flowable 做法 | 我们采纳的做法 |
|------|-------------|---------------|
| **参数值动态** | `execution.getVariable()` | `execution.get()` |
| **参数名动态** | 不限制变量名，引擎不校验 | `get_dynamic_outputs()` 声明 + `execution.set()` 自由写 |
| **编辑器衔接** | palette JSON 声明 config 字段 | `config_fields` + `has_dynamic_output` |
| **变量映射** | inputMapping/outputMapping XML | 引擎的 input_mapping + output_mapping |

动态参数名的关键是：
- **编辑器**：通过 `config_fields` 声明配置字段，用户可以自由填变量名
- **节点**：通过 `get_dynamic_outputs()` 告诉引擎它会输出什么动态变量
- **引擎**：不做 schema 校验，只按声明收集，未声明的也透传

---

## 三、执行计划

| 阶段 | 内容 | 优先级 |
|:----:|------|:------:|
| 1 | 定义 `ParamSchema`、`DelegateExecution`、重构 `WorkflowNode` 基类 | P0 |
| 2 | 实现引擎的 input_mapping / output_mapping / 动态输出收集 | P0 |
| 3 | 重构通用节点（call_llm, execute_code, set_variable 等） | P0 |
| 4 | 实现节点发现 API（包括 config_fields / get_dynamic_outputs） | P1 |
| 5 | 编辑器动态渲染增强（可选） | P2 |
| 6 | 新增节点开发规范文档 | P1 |

---

*版本：v3.1*
*日期：2026-05-28*
*状态：待评审*