# AGENTS.md - 编码质量规范

> 本文档定义编码风格、设计模式和编码原则，旨在提升代码质量和可维护性。

---

## 文档索引

| 文档 | 描述 |
| ---- | ---- |
| [ARCHITECTURE.md](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/ARCHITECTURE.md) | 架构设计规范（核心原则、工作流程、性能要求） |
| [FORMS.md](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/FORMS.md) | 表单系统规范（字段推断规则、Schema 结构） |
| [TOOLS.md](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/TOOLS.md) | 工具与安全规范（工具使用、错误处理、护栏系统） |
| [DEVELOPMENT.md](file:///d:/工作/sitech/项目/研发/git_workspace/AI/prod_platform_ai/DEVELOPMENT.md) | 开发指南（意图处理器规范、开发流程） |

---

## 编码质量规范

### 1. 代码风格规范

#### Python 代码风格

- **遵循 PEP 8**：所有 Python 代码必须符合 PEP 8 规范
- **缩进**：使用 4 个空格，禁止使用 Tab
- **行长度**：单行不超过 120 字符
- **空白行**：函数/类之间用 2 个空行分隔，方法之间用 1 个空行
- **导入顺序**：标准库 → 第三方库 → 项目内部模块，每组之间空一行

```python
import os
import re
from typing import Optional, List

import requests
from fastapi import FastAPI

from app.utils.logger import get_logger
```

#### JavaScript/TypeScript 代码风格

- **遵循 Airbnb 规范**：所有 JS/TS 代码必须符合 Airbnb 风格指南
- **缩进**：使用 2 个空格
- **分号**：语句末尾必须加分号
- **引号**：使用单引号，JSX 中使用双引号
- **箭头函数**：单行函数体可以省略大括号

```javascript
const handleClick = (e: React.MouseEvent) => {
  dispatch({ type: 'CLICK', payload: e.target.value });
};

const getValue = () => defaultValue;
```

### 2. 命名规范

| 类型        | 规则       | 示例                                    |
| --------- | -------- | ------------------------------------- |
| **变量**    | 小驼峰      | `userInput`, `formData`               |
| **函数/方法** | 小驼峰      | `extractFields`, `validateForm`       |
| **类**     | 大驼峰      | `BaseIntentHandler`, `InputGuard`     |
| **常量**    | 全大写下划线分隔 | `MAX_RETRY`, `DANGEROUS_PATTERNS`     |
| **文件**    | 小写连字符    | `intent-handler.js`, `input-guard.py` |
| **目录**    | 小写连字符    | `intent-handlers/`, `config/`         |

### 3. 代码审查规范

#### PR 提交要求

1. **单一职责**：每个 PR 只解决一个问题或实现一个功能
2. **测试覆盖**：新增代码必须有对应的单元测试，覆盖率 ≥ 80%
3. **文档更新**：修改影响使用方式的代码时，必须更新相关文档
4. **无警告**：代码通过所有 lint 和类型检查

#### 审查要点

| 检查项      | 说明            |
| -------- | ------------- |
| **正确性**  | 代码是否正确实现需求    |
| **可读性**  | 代码是否易于理解      |
| **可维护性** | 是否符合设计模式和架构原则 |
| **安全性**  | 是否存在安全隐患      |
| **性能**   | 是否有性能优化空间     |
| **测试**   | 测试用例是否完整      |

### 4. 测试规范

#### 测试类型

| 类型        | 说明        | 覆盖率要求  |
| --------- | --------- | ------ |
| **单元测试**  | 测试单个函数/方法 | ≥ 80%  |
| **集成测试**  | 测试模块间交互   | 关键路径覆盖 |
| **端到端测试** | 测试完整业务流程  | 核心场景覆盖 |

#### 测试命名约定

```python
# 测试文件：test_{module_name}.py
# 测试类：Test{ClassName}
# 测试方法：test_{scenario}_{expected_behavior}

class TestInputGuard:
    def test_should_block_xss_script(self):
        pass
    
    def test_should_allow_valid_input(self):
        pass
```

### 5. 文档规范

#### 代码注释

- **模块级**：每个模块顶部必须有文档字符串，说明功能、输入输出
- **函数级**：公共函数必须有文档字符串
- **复杂逻辑**：非直观的业务逻辑必须添加注释说明
- **禁止冗余**：代码自解释时无需注释

```python
def extract_fields(user_input: str, schema: dict) -> dict:
    """从用户输入中提取字段值
    
    Args:
        user_input: 用户输入文本
        schema: 表单 Schema 定义
        
    Returns:
        提取的字段字典，key 为字段编码，value 为字段值
        
    Raises:
        ValidationError: Schema 格式不正确时抛出
    """
    pass
```

#### API 文档

- **OpenAPI 规范**：所有 API 必须有完整的 OpenAPI 文档
- **参数说明**：每个参数必须说明类型、是否必填、含义
- **返回值说明**：说明返回结构和字段含义

### 6. 代码复杂度控制

| 指标       | 阈值     | 说明           |
| -------- | ------ | ------------ |
| **圈复杂度** | ≤ 10   | 单个函数/方法的分支数量 |
| **函数长度** | ≤ 50 行 | 超出应拆分为多个函数   |
| **类方法数** | ≤ 20   | 超出应考虑拆分类     |
| **嵌套深度** | ≤ 4    | 超出应重构        |

### 7. 静态分析工具

#### 必用工具

| 工具           | 用途                       | 配置文件             |
| ------------ | ------------------------ | ---------------- |
| **flake8**   | Python 代码检查              | `.flake8`        |
| **mypy**     | Python 类型检查              | `mypy.ini`       |
| **eslint**   | JavaScript/TypeScript 检查 | `.eslintrc.json` |
| **prettier** | 代码格式化                    | `.prettierrc`    |

#### 配置要求

- **CI 集成**：所有静态分析工具必须集成到 CI 流程
- **预提交钩子**：使用 `pre-commit` 确保提交代码符合规范

```yaml
repos:
  - repo: https://github.com/PyCQA/flake8
    rev: 6.0.0
    hooks:
      - id: flake8
  - repo: https://github.com/python/mypy
    rev: v1.5.1
    hooks:
      - id: mypy
```

### 8. 依赖管理

- **版本锁定**：使用 `requirements.txt` 或 `pyproject.toml` 锁定依赖版本
- **定期更新**：每季度检查并更新依赖版本
- **安全扫描**：使用 `safety` 或 `pip-audit` 扫描已知安全漏洞

---

## 设计模式规范

### 1. 创建型模式

#### 工厂模式 (Factory Pattern)

**适用场景**：对象创建逻辑复杂，需要统一管理创建过程

```python
class StrategyFactory:
    _strategies = {}

    @classmethod
    def register(cls, strategy_type: str, strategy_class):
        cls._strategies[strategy_type] = strategy_class

    @classmethod
    def create(cls, strategy_type: str, **kwargs):
        if strategy_type not in cls._strategies:
            raise ValueError(f"Unknown strategy: {strategy_type}")
        return cls._strategies[strategy_type](**kwargs)
```

#### 单例模式 (Singleton Pattern)

**适用场景**：全局唯一的资源管理器、配置管理器

```python
class SingletonMeta(type):
    _instances = {}

    def __call__(cls, *args, **kwargs):
        if cls not in cls._instances:
            cls._instances[cls] = super().__call__(*args, **kwargs)
        return cls._instances[cls]
```

### 2. 结构型模式

#### 策略模式 (Strategy Pattern)

**适用场景**：算法需要灵活切换，避免大量条件判断

```python
from abc import ABC, abstractmethod

class RecommendationStrategy(ABC):
    @abstractmethod
    def recommend(self, user_input: str, context: dict) -> list:
        pass
```

#### 适配器模式 (Adapter Pattern)

**适用场景**：接口不兼容的组件需要协同工作

**应用示例**：外部 API 数据格式转换

```python
class ExternalApiAdapter:
    def __init__(self, external_api):
        self._external_api = external_api

    def get_form_data(self, form_code: str) -> dict:
        raw_data = self._external_api.fetch(form_code)
        return self._transform(raw_data)

    def _transform(self, raw_data: dict) -> dict:
        return {
            'formCode': raw_data.get('form_code'),
            'fields': self._transform_fields(raw_data.get('fields', []))
        }
```

**使用位置**：`backend/app/external/adapters/`

#### 装饰器模式 (Decorator Pattern)

**适用场景**：需要动态添加功能，且不修改原有代码

```python
def log_execution(func):
    async def wrapper(*args, **kwargs):
        logger.info(f"Executing {func.__name__}")
        start_time = time.time()
        try:
            return await func(*args, **kwargs)
        finally:
            logger.info(f"{func.__name__} completed in {time.time() - start_time:.2f}s")
    return wrapper
```

### 3. 行为型模式

#### 观察者模式 (Observer Pattern)

**适用场景**：一个对象状态变化需要通知多个观察者

#### 模板方法模式 (Template Method Pattern)

**适用场景**：算法骨架固定，细节步骤可定制

#### 责任链模式 (Chain of Responsibility Pattern)

**适用场景**：请求需要经过多个处理器依次处理

#### 命令模式 (Command Pattern)

**适用场景**：需要将操作封装为对象，支持撤销/重做

### 4. 架构模式

#### MVC 模式 (Model-View-Controller)

| 层级             | 职责        |
| -------------- | --------- |
| **Model**      | 数据模型和业务逻辑 |
| **View**       | 数据展示（前端）  |
| **Controller** | 请求处理和协调   |

#### 依赖注入模式 (Dependency Injection)

**应用示例**：FastAPI 依赖注入

```python
from fastapi import Depends

async def get_service(db: Session = Depends(get_db)) -> Service:
    return Service(db=db)
```

### 5. 设计模式使用指南

| 场景   | 推荐模式   | 理由         |
| ---- | ------ | ---------- |
| 对象创建 | 工厂模式   | 解耦创建逻辑与使用方 |
| 算法切换 | 策略模式   | 运行时动态选择算法  |
| 功能扩展 | 装饰器模式  | 无侵入式添加功能   |
| 状态通知 | 观察者模式  | 解耦发布者与订阅者  |
| 请求处理 | 责任链模式  | 灵活组合处理器    |
| 代码复用 | 模板方法模式 | 定义算法骨架     |
| 接口适配 | 适配器模式  | 兼容不同接口     |

---

## 编码原则

### 1. SOLID 原则

| 原则 | 定义 | 要点 |
| ---- | ---- | ---- |
| **SRP** | 单一职责原则 | 一个类应该只有一个引起它变化的原因 |
| **OCP** | 开闭原则 | 软件实体应该对扩展开放，对修改关闭 |
| **LSP** | 里氏替换原则 | 子类对象应该能够替换父类对象 |
| **ISP** | 接口隔离原则 | 客户端不应该被迫依赖它不需要的接口 |
| **DIP** | 依赖倒置原则 | 高层模块不应该依赖低层模块，两者都应该依赖抽象 |

### 2. DRY 原则 (Don't Repeat Yourself)

**定义**：不要重复代码，相同的逻辑应该只出现一次

### 3. KISS 原则 (Keep It Simple, Stupid)

**定义**：保持代码简单易懂，避免不必要的复杂性

### 4. YAGNI 原则 (You Ain't Gonna Need It)

**定义**：不要实现当前不需要的功能

### 5. 高内聚低耦合

**定义**：模块内部应该高度相关，模块之间应该松散耦合

### 6. 关注点分离 (SoC)

**定义**：将不同的关注点分离到不同的模块中

| 关注点    | 模块            |
| ------ | ------------- |
| 数据访问   | `repository/` |
| 业务逻辑   | `service/`    |
| API 控制 | `api/`        |
| 数据验证   | `validation/` |
| 配置管理   | `config/`     |

### 7. 最小惊讶原则

**定义**：代码行为应该符合用户的预期

### 8. 防御性编程

**定义**：假设输入可能是错误的，提前处理异常情况

### 9. 可测试性原则

**实践要点**：

- 使用依赖注入，便于 mock
- 保持函数单一职责
- 避免全局状态
- 返回确定的结果

### 10. 代码可读性原则

| 原则         | 说明          |
| ---------- | ----------- |
| **有意义的命名** | 使用描述性名称     |
| **适当的注释**  | 解释为什么，而非做什么 |
| **合理的结构**  | 清晰的代码组织     |
| **一致的风格**  | 统一的编码风格     |
| **避免魔法数字** | 使用常量替代      |

---

## 组件化编码规范

### 1. 目录结构规范

```
frontend/src/components/
├── intent-panels/           # 意图面板
├── workflow-editor/         # 工作流编辑器
│   └── nodes/               # 节点组件目录
└── common/                  # 通用组件
```

### 2. 组件划分原则

| 原则       | 说明                      |
| -------- | ----------------------- |
| **单一职责** | 每个组件只负责一个功能             |
| **可复用性** | 组件应易于在其他地方复用            |
| **解耦合**  | 组件间通过 props 和 events 通信 |
| **可测试性** | 组件应易于单元测试               |

### 3. Vue 组件规范

#### 组件结构

```vue
<template>
  <!-- 模板部分 -->
</template>

<script setup>
// 逻辑部分：使用 Composition API
defineProps({});
defineEmits([]);
</script>

<style scoped>
/* 样式部分 */
</style>
```

#### 命名规范

| 类型          | 规则         |
| ----------- | ---------- |
| **组件文件**    | PascalCase |
| **组件目录**    | kebab-case |
| **props**   | camelCase  |
| **events**  | kebab-case |
| **methods** | camelCase  |

### 4. 组件通信模式

#### 4.1 Parent → Child（父传子）

通过 `props` 传递数据：

```vue
<!-- Parent.vue -->
<ChildComponent :message="parentMessage" />

<!-- ChildComponent.vue -->
<script setup>
const props = defineProps({
  message: {
    type: String,
    required: true
  }
});
</script>
```

#### 4.2 Child → Parent（子传父）

通过 `emit` 触发事件：

```vue
<!-- ChildComponent.vue -->
<button @click="$emit('custom-event', data)">点击</button>

<!-- Parent.vue -->
<ChildComponent @custom-event="handleEvent" />
```

#### 4.3 Sibling → Sibling（兄弟组件）

通过父组件作为中介：

```vue
<!-- Parent.vue -->
<PanelA @update="handlePanelAUpdate" />
<PanelB :data="sharedData" />

<script setup>
const sharedData = ref(null);

const handlePanelAUpdate = (data) => {
  sharedData.value = data;
};
</script>
```

### 5. 组件复用策略

#### 5.1 通用组件提取

提取通用功能为独立组件：

```vue
<!-- 通用按钮组件 -->
<template>
  <button :class="['btn', `btn-${variant}`]" @click="$emit('click')">
    <slot />
  </button>
</template>

<script setup>
defineProps({
  variant: {
    type: String,
    default: 'primary'
  }
});

defineEmits(['click']);
</script>
```

#### 5.2 组合式函数

使用 `useXxx` 组合式函数复用逻辑：

```javascript
// useValidation.js
export function useValidation() {
  const errors = ref({});
  
  const validate = (data, rules) => {
    // 验证逻辑
    return errors.value;
  };
  
  return { errors, validate };
}

// 在组件中使用
import { useValidation } from './useValidation';

const { errors, validate } = useValidation();
```

### 6. 性能优化

- **懒加载**：对大型组件使用动态导入

```javascript
const HeavyComponent = defineAsyncComponent(() => 
  import('./HeavyComponent.vue')
);
```

- **虚拟滚动**：对大量数据使用虚拟滚动

```vue
<VirtualList :items="largeList" item-height="50">
  <template #default="{ item }">
    <ListItem :data="item" />
  </template>
</VirtualList>
```

- **缓存计算结果**：使用 `computed` 缓存计算结果

```javascript
const processedData = computed(() => {
  return heavyComputation(props.rawData);
});
```

---

**最后更新**：2026-05-14\
**维护者**：AI Team\
**版本**：v2.0