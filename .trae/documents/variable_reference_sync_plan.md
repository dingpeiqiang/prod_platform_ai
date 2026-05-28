# 变量引用同步更新功能 - 最小化实施计划

## 问题分析

当前工作流编辑器中，变量节点的变量名直接作为引用标识：
- 变量引用格式为 `{{variable_name}}`
- 当用户修改变量名时，其他节点中对该变量的引用不会自动更新
- 这导致工作流执行时找不到变量，产生运行时错误

## 最小化解决方案

不引入UUID，直接在变量名修改时遍历所有节点，自动替换旧变量名引用，并添加变量名唯一性校验。

### 核心思路

```
变量名修改 → 检测变量名唯一性 → 获取旧变量名 → 遍历所有节点 → 替换 {{旧变量名}} 为 {{新变量名}}
```

### 技术实现

#### 1. 变量名唯一性检查

```javascript
// 检查变量名是否已存在
function isVariableNameExists(varName, excludeNodeId = null) {
  return elements.value.some(el => 
    !el.source &&  // 是节点（非连线）
    el.type === 'variable' &&  // 是变量节点
    el.id !== excludeNodeId &&  // 排除当前节点
    el.data?.variable_name === varName  // 变量名相同
  );
}
```

#### 2. 变量名修改检测与引用替换

```javascript
const updateNodeData = (nodeId, data) => {
  saveHistory();
  
  const node = elements.value.find(el => el.id === nodeId);
  const oldVarName = node?.type === 'variable' ? node.data?.variable_name : null;
  const newVarName = node?.type === 'variable' ? data?.variable_name : null;
  
  // 变量名唯一性检查
  if (newVarName && node?.type === 'variable' && oldVarName !== newVarName) {
    if (isVariableNameExists(newVarName, nodeId)) {
      ElMessage.error(`变量名 "${newVarName}" 已存在，请使用其他名称`);
      return; // 阻止修改
    }
  }
  
  if (node) {
    node.data = { ...node.data, ...data };
    markDirty();
    
    // 检测变量名是否变化
    if (oldVarName && newVarName && oldVarName !== newVarName) {
      // 同步更新所有引用
      replaceVariableReferences(oldVarName, newVarName);
    }
    
    // 触发 Vue Flow 重新渲染
    setTimeout(() => {
      elements.value = [...elements.value];
    }, 100);
  }
};

const replaceVariableReferences = (oldName, newName) => {
  const regex = new RegExp(`{{\\s*${oldName}\\s*}}`, 'g');
  
  elements.value.forEach(el => {
    if (el.source) return; // 跳过连线
    
    // 递归替换对象中的所有字符串
    const replaceInObj = (obj) => {
      if (typeof obj === 'string') {
        return obj.replace(regex, `{{${newName}}}`);
      } else if (typeof obj === 'object' && obj !== null) {
        if (Array.isArray(obj)) {
          return obj.map(item => replaceInObj(item));
        } else {
          const result = {};
          for (const key in obj) {
            if (obj.hasOwnProperty(key)) {
              result[key] = replaceInObj(obj[key]);
            }
          }
          return result;
        }
      }
      return obj;
    };
    
    el.data = replaceInObj(el.data);
  });
};
```

## 修改文件清单

| 文件路径 | 修改内容 |
|----------|----------|
| `frontend/src/components/workflow-editor/LangChainEditor.vue` | 在 `updateNodeData` 中添加变量名唯一性检查和引用替换 |

## 实施步骤

### 步骤1：添加变量名唯一性检查函数

在 `updateNodeData` 附近添加：
- `isVariableNameExists(varName, excludeNodeId)`：检查变量名是否已存在

### 步骤2：修改 updateNodeData 方法

在更新前：
- 检测是否为变量节点且变量名变化
- 调用唯一性检查
- 如果重复，提示用户并阻止修改

### 步骤3：添加变量引用替换函数

添加：
- `replaceVariableReferences(oldName, newName)`：替换所有节点中的变量引用

## 兼容性考虑

- **完全兼容**：无需修改数据结构
- **无迁移需求**：直接工作在现有数据上
- **向后兼容**：旧工作流无需任何改动

## 风险评估

| 风险 | 描述 | 应对措施 |
|------|------|----------|
| 变量名冲突 | 多个变量使用相同名称 | 添加唯一性检查 |
| 引用替换遗漏 | 某些字段未被替换 | 使用递归遍历所有字段 |
| 误替换 | 非变量引用被替换 | 使用 `{{变量名}}` 精确匹配 |

## 测试要点

1. **创建变量节点**：设置变量名如 `myVar`
2. **创建重复变量名**：尝试创建同名变量，验证提示错误
3. **在其他节点引用**：输入 `{{myVar}}`
4. **修改变量名**：改为 `newVar`，验证引用自动更新为 `{{newVar}}`
5. **修改为重复名称**：尝试改为已存在的名称，验证提示错误
6. **工作流执行**：验证变量引用正确解析

---

**最后更新**：2026-05-28  
**维护者**：AI Team