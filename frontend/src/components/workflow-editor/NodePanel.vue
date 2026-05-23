<template>
  <div class="node-panel">
    <h3>节点类型</h3>
    <div v-for="group in nodeGroups" :key="group.id" class="node-group">
      <div class="group-header" @click="toggleGroup(group.id)">
        <svg 
          width="12" 
          height="12" 
          viewBox="0 0 24 24" 
          fill="none" 
          stroke="currentColor" 
          stroke-width="2"
          :class="{ rotated: expandedGroups.includes(group.id) }"
        >
          <polyline points="6 9 12 15 18 9"/>
        </svg>
        <span>{{ group.name }}</span>
      </div>
      <div v-show="expandedGroups.includes(group.id)" class="node-types">
        <div
          v-for="nodeType in group.nodes"
          :key="nodeType.id"
          class="node-type-item"
          :class="{ disabled: disabled }"
          :draggable="!disabled"
          @dragstart="disabled ? undefined : onDragStart($event, nodeType)"
        >
          <span class="node-icon">{{ nodeType.icon }}</span>
          <span class="node-name">{{ nodeType.name }}</span>
          <button
            class="node-help-btn"
            title="配置指导"
            @click.stop="showNodeHelp(nodeType)"
          >
            ❓
          </button>
        </div>
      </div>
    </div>
    
    <div class="templates-section">
      <h4>快速模板</h4>
      <div class="templates-list">
        <button
          v-for="template in quickTemplates"
          :key="template.id"
          @click="disabled ? undefined : $emit('apply-template', template)"
          :disabled="disabled"
          class="template-btn"
          :class="{ disabled: disabled }"
        >
          <div class="template-name">{{ template.name }}</div>
          <div class="template-desc" v-if="template.description">{{ template.description }}</div>
        </button>
      </div>
    </div>

    <!-- 节点配置指导手册弹窗 -->
    <el-dialog
      v-model="showHelp"
      :title="currentHelpNode ? currentHelpNode.name + ' - 配置指导' : '配置指导'"
      width="700px"
      :append-to-body="true"
      :destroy-on-close="true"
      class="node-help-dialog"
    >
      <template #header>
        <div class="help-dialog-header">
          <span class="help-dialog-icon">📖</span>
          <span class="help-dialog-title">{{ currentHelpNode ? currentHelpNode.name : '配置指导' }}</span>
        </div>
      </template>
      <div class="help-content" v-html="renderedHelpContent"></div>
      <template #footer>
        <el-button type="primary" @click="showHelp = false">我知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { marked } from 'marked';
import { ElDialog, ElButton } from 'element-plus';

const props = defineProps({
  quickTemplates: {
    type: Array,
    default: () => []
  },
  disabled: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['drag-start', 'apply-template']);

const expandedGroups = ref(['flow', 'llm', 'tools', 'parser']);
const showHelp = ref(false);
const currentHelpNode = ref(null);

// 节点帮助文档配置
const nodeHelpDocs = {
  start: `
## 开始节点

工作流的入口节点，标识流程的起点。每个工作流必须有且仅有一个开始节点。

### 基本配置
- **节点名称**：标识节点的显示名称

### 使用说明
1. 开始节点是工作流的唯一入口
2. 工作流从开始节点自动启动执行
3. 开始节点没有输入端口，只有输出端口

### 注意事项
- 一个工作流只能有一个开始节点
- 开始节点通常连接第一个业务节点
  `,
  end: `
## 结束节点

工作流的出口节点，标识流程的终点。

### 基本配置
- **结束方式**：
  - 正常结束：流程成功完成
  - 异常结束：流程执行失败
- **输出内容**：定义结束时的返回内容

### 使用说明
1. 结束节点是工作流的出口
2. 可设置正常或异常两种结束方式
3. 结束节点没有输出端口，只有输入端口

### 注意事项
- 一个工作流可以有多个结束节点
- 不同结束方式可返回不同的结果
  `,
  condition: `
## 条件分支节点

根据条件表达式的结果，将工作流分流到不同的执行路径。

### 基本配置
- **左值**：比较运算符左侧的值（变量名或常量）
- **运算符**：
  - \`==\` 等于
  - \`!=\` 不等于
  - \`>\` 大于
  - \`<\` 小于
  - \`>=\` 大于等于
  - \`<=\` 小于等于
- **右值**：比较运算符右侧的值（变量名或常量）

### 分支说明
- **如果**：条件满足时执行的分支
- **否则**：条件不满足时执行的分支

### 使用示例
\`\`\`
左值: {{user_age}}
运算符: >=
右值: 18
\`\`\`
结果：判断用户年龄是否大于等于18岁

### 注意事项
- 条件表达式支持 \`{{变量名}}\` 语法引用变量
- 支持嵌套条件（将条件节点作为分支的下一个节点）
  `,
  loop: `
## 循环节点

重复执行循环体中的节点，直到满足循环结束条件。

### 基本配置
- **循环类型**：
  - 固定次数：指定循环执行的次数
  - 条件循环：当条件满足时持续循环
- **循环变量**：当前循环的索引值变量名

### 使用说明
1. 循环节点包含循环体和结束标记
2. 循环体中的节点会被重复执行
3. 支持在循环过程中修改循环变量

### 示例：固定循环3次
\`\`\`
循环次数: 3
循环变量: i
\`\`\`
循环体中将 \`i\` 从0循环到2

### 注意事项
- 注意设置循环退出条件，避免死循环
- 循环变量可在循环体中引用和修改
  `,
  userInput: `
## 用户输入节点

暂停工作流执行，等待用户输入后再继续。

### 基本配置
- **输入类型**：
  - 文本输入
  - 选择输入
  - 文件上传
- **提示文字**：显示给用户的提示信息
- **变量名**：保存用户输入值的变量名

### 使用说明
1. 工作流执行到用户输入节点会暂停
2. 等待用户在界面上输入或选择
3. 用户确认后，工作流继续执行

### 示例：询问用户姓名
\`\`\`
输入类型: 文本输入
提示文字: 请输入您的姓名
变量名: user_name
\`\`\`
用户输入的值会保存到 \`user_name\` 变量中

### 注意事项
- 用户输入节点会阻塞工作流执行
- 确保前端界面提供了对应的输入组件
  `,
  prompt: `
## 提示词节点

定义发送给LLM的提示词模板，支持变量插值。

### 基本配置
- **提示词内容**：输入提示词模板文本
- **变量插值**：使用 \`{{变量名}}\` 语法引用变量

### 使用说明
1. 提示词节点定义发送给语言模型的内容
2. 支持 \`{{变量名}}\` 语法插入变量值
3. 可设置系统提示词和用户提示词

### 示例
\`\`\`
系统提示词：你是一个专业的客服助手
用户提示词：您好，{{user_name}}！有什么可以帮助您的？
\`\`\`

### 高级配置
- **温度参数**：控制输出的随机性（0-1）
- **最大Token数**：限制输出的最大长度
  `,
  llm: `
## LLM调用节点

调用大语言模型（LLM）处理输入并生成输出。

### 基本配置
- **模型选择**：选择使用的LLM模型
- **提示词**：发送给模型的提示词
- **输入变量**：要处理的输入数据

### 使用说明
1. 将输入内容发送给LLM处理
2. 支持多种LLM提供商（OpenAI、Claude等）
3. 可配置模型参数（温度、最大Token等）

### 输入输出
- **输入**：上一个节点的输出或指定的变量
- **输出**：LLM生成的文本响应

### 配置参数
| 参数 | 说明 | 默认值 |
|------|------|--------|
| 模型 | 使用的LLM模型 | gpt-3.5-turbo |
| 温度 | 输出随机性 0-1 | 0.7 |
| 最大Token | 输出最大长度 | 2048 |

### 注意事项
- 确保LLM服务可用
- 根据需求调整温度参数
  `,
  tool: `
## MCP 工具节点

从 MCP ToolHub 动态加载并执行工具，支持表单工具、知识库、LLM、外部 API 等所有已注册 MCP 工具。

### 基本配置
- **分类筛选**：可按工具分类（表单工具、知识库、LLM等）筛选
- **选择工具**：从下拉列表选择要执行的 MCP 工具
- **工具参数**：根据所选工具的 input_schema 动态生成参数输入框
- **变量引用**：参数值支持 \`{{变量名}}\` 语法引用工作流变量

### 工具分类
| 分类 | 说明 |
|------|------|
| form | 表单相关工具 |
| kb | 知识库工具 |
| llm | LLM 工具 |
| system | 系统工具 |
| tariff | 资费工具 |
| workflow | 工作流工具 |
| external | 外部 API 工具 |
| general | 通用工具 |

### 参数说明
- 参数名由工具的 input_schema 自动生成，不可修改
- 参数类型（字符串/数字/布尔等）由 schema 推断
- 参数值可输入常量，也可通过 \`{{变量名}}\` 引用工作流变量

### 使用示例
\`\`\`
1. 从下拉列表选择工具（如 query_form_template）
2. 系统自动加载该工具的参数定义
3. 为每个参数填写值或引用变量
4. 执行时参数值会通过 \`{{变量名}}\` 语法替换为实际值
\`\`\`

### 注意事项
- 工具列表在节点创建时从后端 API 加载
- 如需刷新工具列表，可切换分类或重新打开节点
- 工具执行结果通过 \`__node_output__\` 变量传递给下一节点
  `,
  http: `
## HTTP请求节点

发送HTTP请求到外部API。

### 基本配置
- **请求方法**：GET、POST、PUT、DELETE等
- **请求URL**：目标API地址
- **请求头**：HTTP请求头
- **请求体**：POST/PUT请求的内容

### 使用说明
1. 配置目标URL和请求参数
2. 支持GET、POST、PUT、DELETE等方法
3. 响应数据保存到变量供后续节点使用

### 示例：POST请求
\`\`\`
方法: POST
URL: https://api.example.com/users
请求头: {"Content-Type": "application/json"}
请求体: {"name": "{{user_name}}", "email": "{{user_email}}"}
\`\`\`

### 响应处理
- 响应状态码保存到 \`httpStatus\`
- 响应体保存到 \`httpResult\`
- 支持JSON路径提取响应数据
  `,
  code: `
## 代码执行节点

执行自定义JavaScript代码片段。

### 基本配置
- **代码内容**：输入要执行的JavaScript代码
- **输出变量**：保存执行结果的变量名

### 使用说明
1. 在代码编辑器中编写JavaScript代码
2. 代码可以访问工作流变量
3. 通过 \`return\` 语句返回结果

### 示例：字符串处理
\`\`\`javascript
const name = variables.user_name || '游客';
const greeting = \`你好，\${name}！\`;
return greeting;
\`\`\`

### 示例：数值计算
\`\`\`javascript
const price = variables.price || 0;
const tax = price * 0.13;
return Math.round(tax * 100) / 100;
\`\`\`

### 注意事项
- 代码在沙箱环境中执行，保证安全性
- 避免执行耗时过长的代码
- 可访问 \`variables\` 对象读取工作流变量
  `,
  variable: `
## 变量赋值节点

用于在工作流执行过程中创建、修改变量，供后续节点引用。

### 基本配置
- **变量名**：设置变量的名称
- **变量类型**：string/number/boolean/array/object/date/json
- **值来源**：常量值/表达式/引用变量/函数调用/JSON路径

### 值来源类型
| 类型 | 说明 | 示例 |
|------|------|------|
| 常量值 | 直接输入固定值 | \`Hello World\` |
| 表达式 | 算术运算和字符串拼接 | \`{{a}} + {{b}}\` |
| 引用变量 | 引用其他变量 | \`{{other_var}}\` |
| 函数调用 | 内置函数生成动态值 | \`{{now()}}\` |
| JSON路径 | 从复杂对象提取数据 | \`$.data.items[0]\` |

### 内置函数
| 函数 | 说明 | 示例 |
|------|------|------|
| \`{{now()}}\` | 当前时间 | 2024-01-15 14:30:00 |
| \`{{uuid()}}\` | 生成UUID | 550e8400-e29b... |
| \`{{random()}}\` | 随机数 | 0.123456 |
| \`{{upper()}}\` | 转大写 | HELLO |
| \`{{lower()}}\` | 转小写 | hello |
| \`{{len()}}\` | 字符串长度 | 11 |
| \`{{trim()}}\` | 去除空白 | Hello |
| \`{{json()}}\` | JSON序列化 | {"key":"value"} |
| \`{{parseJson()}}\` | JSON解析 | 解析JSON字符串 |

### 变量作用域
| 作用域 | 说明 |
|--------|------|
| workflow | 仅在当前工作流内可用 |
| step | 仅在当前步骤内可用 |
| global | 全局共享，跨工作流可用 |

### 典型使用场景
1. 保存LLM输出：\`变量名: summary, 值: {{llm_output}}\`
2. 组合字符串：\`变量名: greeting, 值: 您好，{{name}}！\`
3. 提取JSON数据：\`变量名: token, 值: $.data.access_token\`
4. 生成唯一ID：\`变量名: order_id, 值: {{uuid()}}\`
  `,
  knowledgeBase: `
## 知识库节点

从知识库中检索相关文档或回答问题。

### 基本配置
- **知识库选择**：选择要查询的知识库
- **查询模式**：
  - 检索（retrieve）：根据关键词检索相关文档
  - 问答（qa）：基于知识库内容回答问题
  - 摘要（summarize）：对文档进行摘要

### 使用说明
1. 选择目标知识库
2. 设置查询模式和查询内容
3. 支持 \`{{变量名}}\` 语法引用变量

### 输入输出
- **输入**：查询文本或问题
- **输出**：检索结果、答案或摘要

### 示例：检索文档
\`\`\`
知识库: 产品知识库
查询模式: retrieve
查询内容: {{user_question}}
\`\`\`

### 示例：知识问答
\`\`\`
知识库: 常见问题库
查询模式: qa
查询内容: 如何重置密码？
\`\`\`

### 注意事项
- 确保知识库已正确配置
- 根据场景选择合适的查询模式
  `,
  parser: `
## 输出解析节点

解析和转换上一个节点的输出内容。

### 基本配置
- **解析方式**：
  - JSON解析：将JSON字符串解析为对象
  - 文本提取：使用正则表达式提取内容
  - 格式转换：转换数据格式

### 使用说明
1. 接收上一个节点的输出
2. 按照配置的解析方式进行解析
3. 输出解析后的结果

### 解析方式
| 方式 | 说明 | 示例 |
|------|------|------|
| JSON解析 | 解析JSON字符串 | \`{"name":"张三"}\` → 对象 |
| 正则提取 | 使用正则表达式提取 | 提取手机号 |
| 字段映射 | 映射和重命名字段 | {a:1} → {name:1} |

### 示例：JSON解析
\`\`\`
解析方式: JSON解析
输入: {{json_string}}
输出变量: parsed_data
\`\`\`

### 示例：正则提取手机号
\`\`\`
解析方式: 正则提取
正则表达式: 1[3-9]\\d{9}
输入: 联系电话：13812345678
\`\`\`
  `,
  form: `
## 表单节点

在工作流中展示表单，收集用户输入。

### 基本配置
- **表单定义**：定义表单的字段和布局
- **提交处理**：表单提交后的处理方式
- **验证规则**：设置字段的验证规则

### 使用说明
1. 定义表单的结构和字段
2. 配置字段的类型和验证规则
3. 设置表单提交后的处理逻辑

### 字段类型
| 类型 | 说明 | 示例 |
|------|------|------|
| input | 单行文本 | 用户名 |
| textarea | 多行文本 | 备注 |
| number | 数字 | 年龄 |
| select | 下拉选择 | 性别 |
| radio | 单选 | 婚姻状态 |
| checkbox | 多选 | 爱好 |
| date | 日期 | 生日 |
| datetime | 日期时间 | 预约时间 |

### 示例：用户注册表单
\`\`\`
字段:
  - name: 用户名, type: input, required: true
  - age: 年龄, type: number
  - gender: 性别, type: select, options: [男, 女]
  - interests: 爱好, type: checkbox, options: [阅读, 运动, 音乐]
\`\`\`
  `,
  validate: `
## 数据验证节点

验证工作流中的数据是否符合预设规则。

### 基本配置
- **验证规则**：设置验证条件和错误提示
- **验证模式**：
  - 严格模式：验证失败则停止执行
  - 宽松模式：验证失败仅记录警告

### 使用说明
1. 配置验证规则和条件
2. 指定要验证的数据源
3. 设置验证失败时的处理方式

### 内置规则
| 规则 | 说明 | 示例 |
|------|------|------|
| required | 必填验证 | 字段不能为空 |
| email | 邮箱格式 | 合法的邮箱地址 |
| phone | 手机号格式 | 11位手机号 |
| length | 长度验证 | 字符串长度范围 |
| range | 数值范围 | 数值在指定范围内 |
| pattern | 正则验证 | 符合正则表达式 |

### 示例：验证手机号
\`\`\`
验证字段: {{phone}}
规则: phone
错误提示: 请输入正确的手机号码
\`\`\`

### 示例：验证年龄范围
\`\`\`
验证字段: {{age}}
规则: range
最小值: 18
最大值: 100
错误提示: 年龄必须在18-100岁之间
\`\`\`
  `
};

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true,
  headerIds: false,
  mangle: false
});

const renderedHelpContent = computed(() => {
  if (!currentHelpNode.value) return '';
  const doc = nodeHelpDocs[currentHelpNode.value.type] || `
## ${currentHelpNode.value.name}

暂无配置指导文档。

### 基本说明
- 节点类型：${currentHelpNode.value.type}
- 节点名称：${currentHelpNode.value.name}

请联系开发者添加此节点的配置指导。
  `;
  try {
    return marked.parse(doc);
  } catch (e) {
    console.error('Markdown 解析错误:', e);
    return doc;
  }
});

const showNodeHelp = (nodeType) => {
  currentHelpNode.value = nodeType;
  showHelp.value = true;
};

const nodeGroups = ref([
  {
    id: 'flow',
    name: '流程控制',
    nodes: [
      { id: 'start', name: '开始', icon: '🚀', type: 'start' },
      { id: 'end', name: '结束', icon: '🏁', type: 'end' },
      { id: 'condition', name: '条件分支', icon: '🔀', type: 'condition' },
      { id: 'loop', name: '循环', icon: '🔄', type: 'loop' },
      { id: 'userInput', name: '用户输入', icon: '👤', type: 'userInput' }
    ]
  },
  {
    id: 'llm',
    name: 'LLM 相关',
    nodes: [
      { id: 'prompt', name: '提示词', icon: '📝', type: 'prompt' },
      { id: 'llm', name: 'LLM 调用', icon: '🤖', type: 'llm' }
    ]
  },
  {
    id: 'tools',
    name: '工具与数据',
    nodes: [
      { id: 'tool', name: 'MCP工具', icon: '🔌', type: 'tool' },
      { id: 'http', name: 'HTTP请求', icon: '🌐', type: 'http' },
      { id: 'code', name: '代码执行', icon: '💻', type: 'code' },
      { id: 'variable', name: '变量赋值', icon: '📦', type: 'variable' },
      { id: 'knowledgeBase', name: '知识库', icon: '📚', type: 'knowledgeBase' }
    ]
  },
  {
    id: 'parser',
    name: '数据处理',
    nodes: [
      { id: 'parser', name: '输出解析', icon: '📊', type: 'parser' }
    ]
  },
  {
    id: 'forms',
    name: '表单操作',
    nodes: [
      { id: 'form', name: '表单节点', icon: '📋', type: 'form' },
      { id: 'validate', name: '数据验证', icon: '✓', type: 'validate' }
    ]
  }
]);

const toggleGroup = (groupId) => {
  const index = expandedGroups.value.indexOf(groupId);
  if (index > -1) {
    expandedGroups.value.splice(index, 1);
  } else {
    expandedGroups.value.push(groupId);
  }
};

const onDragStart = (event, nodeType) => {
  event.dataTransfer.setData('application/vueflow', JSON.stringify(nodeType));
  event.dataTransfer.effectAllowed = 'move';
  
  // 保存当前元素引用
  const currentElement = event.currentTarget;
  
  // 添加拖动时的视觉反馈
  const dragImage = event.currentTarget.cloneNode(true);
  dragImage.style.position = 'absolute';
  dragImage.style.top = '-1000px';
  dragImage.style.opacity = '0.8';
  document.body.appendChild(dragImage);
  event.dataTransfer.setDragImage(dragImage, 0, 0);
  
  // 添加 dragging 类
  if (currentElement) {
    currentElement.classList.add('dragging');
  }
  
  // 拖拽结束后移除 dragging 类
  setTimeout(() => {
    if (currentElement && currentElement.classList) {
      currentElement.classList.remove('dragging');
    }
    if (document.body.contains(dragImage)) {
      document.body.removeChild(dragImage);
    }
  }, 0);
  
  emit('drag-start', nodeType);
};
</script>

<style scoped>
.node-panel {
  width: 180px;
  background-color: #f8fafc;
  border-right: 1px solid #e2e8f0;
  padding: 12px;
  overflow-y: auto;
  flex-shrink: 0;
}

.node-panel h3 {
  margin: 0 0 12px 0;
  font-size: 13px;
  color: #334155;
  font-weight: 600;
}

.node-group {
  margin-bottom: 8px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  cursor: pointer;
  border-radius: 4px;
  font-size: 12px;
  color: #475569;
}

.group-header svg {
  transition: transform 0.2s;
}

.group-header svg.rotated {
  transform: rotate(180deg);
}

.node-types {
  padding-left: 8px;
}

.node-type-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 4px;
  background-color: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: grab;
  user-select: none;
  transition: all 0.2s;
  position: relative;
}

.node-type-item:hover {
  background-color: #eff6ff;
  border-color: #3b82f6;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.1);
}

.node-type-item:hover .node-help-btn {
  opacity: 1;
}

.node-type-item:active {
  cursor: grabbing;
}

.node-type-item.dragging {
  opacity: 0.5;
}

.node-type-item.disabled {
  cursor: not-allowed;
  opacity: 0.6;
  background-color: #f1f5f9;
  border-color: #e2e8f0;
}

.node-type-item.disabled:hover {
  background-color: #f1f5f9;
  border-color: #e2e8f0;
  transform: none;
  box-shadow: none;
}

.node-help-btn {
  position: absolute;
  right: 6px;
  bottom: 6px;
  width: 20px;
  height: 20px;
  border: none;
  background: rgba(59, 130, 246, 0.1);
  border-radius: 4px;
  cursor: pointer;
  font-size: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: all 0.2s;
  color: #3b82f6;
}

.node-help-btn:hover {
  background: rgba(59, 130, 246, 0.2);
  transform: scale(1.1);
}

.node-icon {
  font-size: 16px;
}

.node-name {
  font-size: 12px;
  color: #334155;
}

.templates-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

.templates-section h4 {
  margin: 0 0 10px 0;
  font-size: 11px;
  color: #64748b;
  text-transform: uppercase;
}

.templates-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.template-btn {
  padding: 8px 10px;
  background-color: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  cursor: pointer;
  text-align: left;
  color: #334155;
  transition: all 0.2s;
}

.template-btn:hover {
  background-color: #eff6ff;
  border-color: #3b82f6;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(59, 130, 246, 0.1);
}

.template-btn.disabled {
  cursor: not-allowed;
  opacity: 0.6;
  background-color: #f1f5f9;
  border-color: #e2e8f0;
}

.template-btn.disabled:hover {
  background-color: #f1f5f9;
  border-color: #e2e8f0;
  transform: none;
  box-shadow: none;
}

.template-name {
  font-size: 12px;
  font-weight: 500;
  color: #334155;
  margin-bottom: 2px;
}

.template-desc {
  font-size: 10px;
  color: #94a3b8;
  line-height: 1.4;
}

/* ============================================
   节点帮助对话框样式 - Modern UI
   ============================================ */
:deep(.node-help-dialog) {
  --help-primary: #3b82f6;
  --help-success: #10b981;
  --help-warning: #f59e0b;
  --help-danger: #ef4444;
  --help-gray-50: #f9fafb;
  --help-gray-100: #f3f4f6;
  --help-gray-200: #e5e7eb;
  --help-gray-300: #d1d5db;
  --help-gray-400: #9ca3af;
  --help-gray-500: #6b7280;
  --help-gray-600: #4b5563;
  --help-gray-700: #374151;
  --help-gray-800: #1f2937;
  --help-gray-900: #111827;
}

:deep(.node-help-dialog .el-dialog) {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
}

/* 自定义头部 */
:deep(.node-help-dialog .help-dialog-header) {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 4px;
}

:deep(.node-help-dialog .help-dialog-icon) {
  font-size: 20px;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.1));
}

:deep(.node-help-dialog .help-dialog-title) {
  font-size: 16px;
  font-weight: 600;
  color: white;
  letter-spacing: 0.3px;
}

:deep(.node-help-dialog .el-dialog__header) {
  padding: 16px 20px;
  margin: 0;
  background: linear-gradient(135deg, var(--help-primary) 0%, #2563eb 100%);
  border-bottom: none;
}

:deep(.node-help-dialog .el-dialog__headerbtn) {
  top: 16px;
  right: 16px;
  width: 28px;
  height: 28px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

:deep(.node-help-dialog .el-dialog__headerbtn:hover) {
  background: rgba(255, 255, 255, 0.25);
}

:deep(.node-help-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: white;
  font-size: 16px;
  font-weight: bold;
}

:deep(.node-help-dialog .el-dialog__body) {
  padding: 0;
  max-height: 65vh;
  overflow: hidden;
}

:deep(.node-help-dialog .help-content) {
  padding: 20px 24px;
  overflow-y: auto;
  max-height: 65vh;
  line-height: 1.7;
  font-size: 13px;
  color: var(--help-gray-600);
  background: linear-gradient(180deg, #fafbfc 0%, #ffffff 100%);
}

/* 章节标题 */
:deep(.node-help-dialog .help-content h2) {
  font-size: 14px;
  font-weight: 600;
  color: var(--help-gray-800);
  margin: 20px 0 12px 0;
  padding: 8px 0 8px 12px;
  border: none;
  border-left: 3px solid var(--help-primary);
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.08) 0%, transparent 100%);
  border-radius: 0 6px 6px 0;
  display: flex;
  align-items: center;
}

:deep(.node-help-dialog .help-content h2:first-child) {
  margin-top: 0;
}

/* 子标题 */
:deep(.node-help-dialog .help-content h3) {
  font-size: 13px;
  font-weight: 600;
  color: var(--help-gray-700);
  margin: 16px 0 8px 0;
  padding-bottom: 4px;
  border-bottom: 1px dashed var(--help-gray-200);
}

/* 段落 */
:deep(.node-help-dialog .help-content p) {
  margin: 8px 0;
  color: var(--help-gray-600);
  line-height: 1.7;
}

/* 列表 */
:deep(.node-help-dialog .help-content ul),
:deep(.node-help-dialog .help-content ol) {
  margin: 8px 0;
  padding-left: 18px;
  color: var(--help-gray-600);
}

:deep(.node-help-dialog .help-content li) {
  margin: 6px 0;
  padding-left: 4px;
}

:deep(.node-help-dialog .help-content ul li::marker) {
  color: var(--help-primary);
}

/* 内联代码 */
:deep(.node-help-dialog .help-content code) {
  background: linear-gradient(135deg, #fef3c7 0%, #fde68a 100%);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'JetBrains Mono', 'Fira Code', Monaco, monospace;
  font-size: 11.5px;
  color: #92400e;
  font-weight: 500;
  border: 1px solid #fcd34d;
}

/* 代码块 */
:deep(.node-help-dialog .help-content pre) {
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
  padding: 14px 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
  border: 1px solid #334155;
  box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.2);
}

:deep(.node-help-dialog .help-content pre code) {
  background: transparent;
  padding: 0;
  border-radius: 0;
  font-family: 'JetBrains Mono', 'Fira Code', Monaco, monospace;
  font-size: 12px;
  color: #e2e8f0;
  font-weight: 400;
  border: none;
  letter-spacing: 0.3px;
  line-height: 1.6;
}

/* 表格 */
:deep(.node-help-dialog .help-content table) {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  margin: 12px 0;
  font-size: 12px;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  border: 1px solid var(--help-gray-200);
}

:deep(.node-help-dialog .help-content th) {
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  padding: 10px 12px;
  text-align: left;
  font-weight: 600;
  color: var(--help-gray-700);
  border-bottom: 2px solid var(--help-gray-200);
  font-size: 11.5px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

:deep(.node-help-dialog .help-content td) {
  padding: 10px 12px;
  border-bottom: 1px solid var(--help-gray-100);
  color: var(--help-gray-600);
  background: white;
}

:deep(.node-help-dialog .help-content tr:last-child td) {
  border-bottom: none;
}

:deep(.node-help-dialog .help-content tr:hover td) {
  background: #f8faff;
}

/* 强调文本 */
:deep(.node-help-dialog .help-content strong) {
  color: var(--help-gray-800);
  font-weight: 600;
}

/* 链接 */
:deep(.node-help-dialog .help-content a) {
  color: var(--help-primary);
  text-decoration: none;
  font-weight: 500;
}

:deep(.node-help-dialog .help-content a:hover) {
  text-decoration: underline;
}

/* 分割线 */
:deep(.node-help-dialog .help-content hr) {
  border: none;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--help-gray-200), transparent);
  margin: 20px 0;
}

/* 对话框底部 */
:deep(.node-help-dialog .el-dialog__footer) {
  padding: 14px 20px;
  background: linear-gradient(180deg, #fafbfc 0%, #f3f4f6 100%);
  border-top: 1px solid var(--help-gray-200);
  display: flex;
  justify-content: flex-end;
}

:deep(.node-help-dialog .el-dialog__footer .el-button) {
  padding: 9px 24px;
  border-radius: 8px;
  font-weight: 500;
  font-size: 13px;
  transition: all 0.2s;
}

:deep(.node-help-dialog .el-dialog__footer .el-button--primary) {
  background: linear-gradient(135deg, var(--help-primary) 0%, #2563eb 100%);
  border: none;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

:deep(.node-help-dialog .el-dialog__footer .el-button--primary:hover) {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
  transform: translateY(-1px);
}
</style>