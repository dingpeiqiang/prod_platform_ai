# 变量名同步更新功能实现计划

## 需求分析

### 问题描述
当工作流编辑器中的变量节点（VariableNode）的变量名修改后，其他节点中通过 `{{变量名}}` 格式引用该变量的地方没有同步更新，导致引用失效。

### 当前变量引用机制分析

**变量定义方式：**
- 变量节点使用 `data.variable_name` 存储变量名（如 `my_variable`）
- 其他节点（如 PromptNode、LlmNode 等）使用 `data.outputVar` 存储输出变量名

**变量引用方式：**
- 在文本字段中通过 `{{变量名}}` 格式引用（如 `"请分析数据：{{my_variable}}"`）
- `getAvailableVariables()` 函数收集可用变量列表
- VariablePicker 和 VariableSelector 组件用于选择变量并插入引用

**引用关系的问题：**
- 当前引用是基于变量名字符串匹配的
- 变量名是唯一的识别标识，没有独立的唯一ID
- 变量名修改后，引用不会自动更新

---

## 实现方案

### 方案选择

经过分析，我们选择**方案A：使用UUID作为变量唯一标识**，这是更优雅的解决方案：

| 方案 | 描述 | 优点 | 缺点 |
|-----|------|-----|-----|
| **方案A** | 为变量分配UUID，引用使用UUID，显示使用变量名 | 引用稳定，修改变量名不影响引用 | 需要修改变量插入和解析逻辑 |
| **方案B** | 变量名变更时，自动替换所有引用 | 改动小 | 需要处理正则转义，性能考虑 |

### 方案A：UUID唯一标识方案

#### 核心思想
- **变量定义**：每个变量有 `variable_id`（UUID）和 `variable_name`（显示名）
- **变量引用**：内部存储使用 `{{#variable_id}}` 格式，显示时转换为 `{{variable_name}}`
- **变量解析**：执行时根据 `variable_id` 查找变量值

#### 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                      变量引用架构                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  变量节点定义                    变量引用                          │
│  ┌──────────────┐              ┌──────────────┐                 │
│  │ variable_id  │              │ {{#var-xxx}} │  ← 内部存储格式   │
│  │ var-abc123   │              │ {{#var-xxx}} │                 │
│  │ variable_name│              │              │                 │
│  │ my_variable  │              │              │                 │
│  │ variable_value│             │              │                 │
│  └──────────────┘              └──────────────┘                 │
│         │                              │                        │
│         │ 变量列表                      │ 变量解析               │
│         ▼                              ▼                        │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              变量解析器 (VariableResolver)               │    │
│  │  - 存储: {{#var-xxx}} → 显示: {{my_variable}}          │    │
│  │  - 执行: {{#var-xxx}} → 实际值                          │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 技术实现

**步骤1：修改 VariableNode.vue - 添加UUID生成**

```vue
<input
  v-model="data.variable_name"
  class="var-name-input"
  placeholder="变量名"
/>
```

```javascript
import { ref, onMounted, computed } from 'vue';
import { v4 as uuidv4 } from 'uuid';

const props = defineProps({
  data: { type: Object, required: true },
  ...
});

// 确保变量有唯一ID
onMounted(() => {
  if (!props.data.variable_id) {
    props.data.variable_id = `var-${uuidv4().slice(0, 8)}`;
  }
});

// 显示用的变量引用格式
const displayReference = computed(() => {
  return `{{${props.data.variable_name || '未命名变量'}}}`;
});

// 内部存储用的变量引用格式
const internalReference = computed(() => {
  return `{{#${props.data.variable_id}}}`;
});
```

**步骤2：修改 VariablePicker.vue - 使用UUID插入**

```javascript
const handleInsert = (varItem) => {
  // 使用UUID格式存储
  const variableRef = `{{#${varItem.variableId}}}`;
  emit('update:modelValue', variableRef);
  emit('insert', varItem);
  closeSelector();
};
```

**步骤3：修改 getAvailableVariables() - 添加variableId**

```javascript
const getAvailableVariables = (nodeId) => {
  // ...
  case 'variable':
    const varId = node.data?.variable_id || `var-${node.id}`;
    outputVarName = node.data?.variable_name || node.data?.varName || node.data?.variableName || node.data?.outputVar || node.data?.label || nodeType;
    variables.push({
      id: varId,                                    // UUID
      variableId: varId,                            // 明确的变量ID字段
      name: `${outputVarName} (输出)`,              // 显示名称
      displayName: outputVarName,                   // 纯显示名
      nodeId: node.id,
      nodeType: nodeType,
      nodeName: nodeName,
      type: node.data?.varType || 'any',
      source: 'node_output',
      sourceNodeType: nodeType,
      sourceNodeName: nodeName
    });
    break;
  // ...
};
```

**步骤4：创建变量解析器工具**

新建文件 `frontend/src/components/workflow-editor/utils/variableResolver.js`

```javascript
/**
 * 变量解析器 - 处理变量引用的存储和显示转换
 */
export class VariableResolver {
  /**
   * 将内部UUID格式转换为显示格式
   * @param {string} text - 包含变量引用的文本
   * @param {Array} variables - 变量列表
   * @returns {string} 转换后的文本
   */
  static toDisplay(text, variables) {
    if (!text || !variables || variables.length === 0) {
      return text;
    }
    
    let result = text;
    
    // 匹配 {{#var-id}} 格式
    const pattern = /\{\{#([^}]+)\}\}/g;
    result = result.replace(pattern, (match, varId) => {
      const variable = variables.find(v => v.id === varId || v.variableId === varId);
      if (variable) {
        return `{{${variable.displayName || variable.name.replace(/ \(输出\)$/, '')}}}`;
      }
      return match;
    });
    
    return result;
  }
  
  /**
   * 将显示格式转换为内部UUID格式
   * @param {string} text - 包含变量引用的文本
   * @param {Array} variables - 变量列表
   * @returns {string} 转换后的文本
   */
  static toInternal(text, variables) {
    if (!text || !variables || variables.length === 0) {
      return text;
    }
    
    let result = text;
    
    // 匹配 {{变量名}} 格式（不包含 # 前缀）
    const pattern = /\{\{([^#][^}]*)\}\}/g;
    result = result.replace(pattern, (match, varName) => {
      const trimmedName = varName.trim();
      const variable = variables.find(v => {
        const displayName = v.displayName || v.name.replace(/ \(输出\)$/, '');
        return displayName === trimmedName;
      });
      if (variable) {
        return `{{#${variable.id}}}`;
      }
      return match;
    });
    
    return result;
  }
  
  /**
   * 执行时解析变量引用，获取实际值
   * @param {string} text - 包含变量引用的文本
   * @param {Object} context - 执行上下文（包含变量值）
   * @param {Array} variables - 变量列表（用于映射ID到名称）
   * @returns {string} 解析后的文本
   */
  static resolve(text, context, variables) {
    if (!text) return text;
    
    let result = text;
    const pattern = /\{\{#?([^}]+)\}\}/g;
    
    result = result.replace(pattern, (match, varIdOrName) => {
      const trimmed = varIdOrName.trim();
      
      // 检查是否是UUID格式
      if (trimmed.startsWith('var-')) {
        // 通过ID查找变量名
        const variable = variables.find(v => v.id === trimmed || v.variableId === trimmed);
        if (variable) {
          const displayName = variable.displayName || variable.name.replace(/ \(输出\)$/, '');
          return context?.variables?.[displayName] || match;
        }
        return match;
      } else {
        // 直接使用变量名查找
        return context?.variables?.[trimmed] || match;
      }
    });
    
    return result;
  }
  
  /**
   * 获取文本中引用的所有变量ID
   * @param {string} text - 包含变量引用的文本
   * @returns {Array} 变量ID列表
   */
  static extractVariableIds(text) {
    if (!text) return [];
    
    const ids = [];
    const pattern = /\{\{#([^}]+)\}\}/g;
    let match;
    
    while ((match = pattern.exec(text)) !== null) {
      ids.push(match[1]);
    }
    
    return [...new Set(ids)];
  }
}
```

**步骤5：修改 VariableSelector.vue - 显示变量名**

```vue
<div class="variable-item" ...>
  <span class="var-icon">{{ getVarIcon(varItem.source) }}</span>
  <div class="var-info">
    <span class="var-name">{{ varItem.displayName || varItem.name }}</span>
    <span class="var-type">{{ getTypeLabel(varItem.type) }}</span>
  </div>
</div>
```

**步骤6：修改执行引擎 - 支持UUID格式**

修改 `executionEngine.js` 中的变量解析逻辑：

```javascript
import { VariableResolver } from './variableResolver';

// 在执行节点时
const executeNode = async (node, context) => {
  // 获取可用变量列表
  const variables = this.getAvailableVariables(node.id);
  
  // 解析节点数据中的变量引用
  const resolvedData = JSON.parse(
    VariableResolver.resolve(
      JSON.stringify(node.data),
      context,
      variables
    )
  );
  
  // 使用 resolvedData 执行节点...
};
```

**步骤7：修改节点组件中的变量显示**

以 PromptNode.vue 为例：

```javascript
import { computed } from 'vue';
import { VariableResolver } from '../utils/variableResolver';

const props = defineProps({
  data: { type: Object, required: true },
  availableVariables: { type: Array, default: () => [] }
});

// 显示给用户的提示词（转换UUID为变量名）
const displayPrompt = computed(() => {
  return VariableResolver.toDisplay(props.data.prompt || '', props.availableVariables);
});

// 保存时转换为内部格式
const savePrompt = (prompt) => {
  const internalPrompt = VariableResolver.toInternal(prompt, props.availableVariables);
  emit('update', props.data.id, { prompt: internalPrompt });
};
```

---

## 数据迁移

对于现有工作流数据，需要进行迁移：

```javascript
/**
 * 迁移现有工作流数据，将 {{变量名}} 转换为 {{#var-id}} 格式
 */
const migrateWorkflowVariables = (elements) => {
  const variables = [];
  
  // 第一步：收集所有变量节点，为没有ID的变量生成ID
  elements.forEach(el => {
    if (!el.source && !el.target && el.type === 'variable') {
      if (!el.data.variable_id) {
        el.data.variable_id = `var-${uuidv4().slice(0, 8)}`;
      }
      variables.push({
        id: el.data.variable_id,
        displayName: el.data.variable_name || el.data.label || '未命名变量'
      });
    }
  });
  
  // 第二步：转换所有节点中的变量引用
  elements.forEach(el => {
    if (!el.source && !el.target && el.data) {
      const jsonString = JSON.stringify(el.data);
      const migratedString = VariableResolver.toInternal(jsonString, variables);
      el.data = JSON.parse(migratedString);
    }
  });
  
  return elements;
};
```

---

## 测试用例

| 场景 | 步骤 | 预期结果 |
|-----|-----|---------|
| **基本引用** | 创建变量节点 `var1`；在 Prompt 节点引用；修改变量名为 `var2` | 引用显示自动更新为 `{{var2}}` |
| **多处引用** | 同一变量被多个节点引用；修改变量名 | 所有引用显示都更新 |
| **变量重命名** | 修改变量名后执行工作流 | 执行正常，使用新变量名 |
| **数据迁移** | 加载旧格式工作流 | 自动转换为新格式 |
| **变量删除** | 删除变量节点 | 引用显示为未解析状态 |

---

## 风险评估

| 风险类型 | 风险描述 | 影响 | 缓解措施 |
|---------|---------|-----|---------|
| **数据损坏** | 迁移过程出错 | 高 | 备份机制、迁移前验证 |
| **兼容性** | 旧格式数据无法正常工作 | 中 | 自动迁移、向后兼容 |
| **性能问题** | 大量变量时解析耗时 | 低 | 缓存解析结果 |
| **引用失效** | 删除变量后引用无法解析 | 中 | 验证时检测并警告 |

---

## 代码修改清单

| 文件 | 修改内容 | 优先级 |
|-----|---------|-------|
| `VariableNode.vue` | 添加UUID生成和管理 | 高 |
| `VariablePicker.vue` | 使用UUID格式插入变量 | 高 |
| `VariableSelector.vue` | 显示变量名而非ID | 高 |
| `LangChainEditor.vue` | 修改getAvailableVariables | 高 |
| `variableResolver.js` | 新建变量解析器 | 高 |
| `executionEngine.js` | 使用解析器处理变量 | 高 |
| `PromptNode.vue` | 使用解析器显示变量 | 中 |
| `LlmNode.vue` | 使用解析器显示变量 | 中 |
| 其他节点 | 使用解析器显示变量 | 中 |

---

## 兼容性

- **向后兼容**：现有工作流数据自动迁移
- **数据格式升级**：首次加载时自动转换为新格式
- **后端无影响**：修改仅在前端进行，数据存储格式不变（UUID存储在节点data中）

---

## 后续优化建议

1. **引用追踪**：维护变量引用关系图，显示哪些节点引用了某个变量
2. **引用验证**：删除变量时提示有哪些节点引用了该变量
3. **批量重命名**：支持同时修改变量名和所有引用
4. **变量别名**：支持一个变量有多个显示名称