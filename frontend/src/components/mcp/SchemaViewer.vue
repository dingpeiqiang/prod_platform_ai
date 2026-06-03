<template>
  <div class="schema-viewer" :class="{ 'edit-mode': mode === 'edit' }">
    <!-- 头部标签 -->
    <div class="schema-header">
      <span class="schema-label">{{ label || 'Schema' }}</span>
      <div class="schema-meta" v-if="!showJson">
        <span class="type-badge">{{ displayType }}</span>
        <span class="required-count" v-if="requiredList.length > 0">
          必填 {{ requiredList.length }} 项
        </span>
      </div>
      <!-- 编辑模式切换按钮 -->
      <el-button
        v-if="mode === 'edit'"
        size="small"
        text
        @click="toggleJsonView"
        class="toggle-json-btn"
      >
        {{ showJson ? '📝 表单编辑' : '📄 JSON 编辑' }}
      </el-button>
    </div>

    <!-- 表格视图 -->
    <div v-if="!showJson" class="schema-table-wrapper">
      <el-table
        :data="params"
        border
        size="small"
        style="width: 100%"
        :header-cell-style="{ background: '#f5f7fa', color: '#606266', fontSize: '12px' }"
        empty-text="暂无参数定义"
      >
        <!-- 参数名 -->
        <el-table-column label="参数名" prop="name" width="150">
          <template #default="{ row }">
            <el-input
              v-if="mode === 'edit'"
              v-model="row.name"
              size="small"
              placeholder="参数名"
              @change="syncToSchema"
            />
            <span v-else class="param-name">{{ row.name }}</span>
          </template>
        </el-table-column>

        <!-- 类型 -->
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            <el-select
              v-if="mode === 'edit'"
              v-model="row.type"
              size="small"
              style="width: 100%"
              @change="syncToSchema"
            >
              <el-option label="string" value="string" />
              <el-option label="integer" value="integer" />
              <el-option label="number" value="number" />
              <el-option label="boolean" value="boolean" />
              <el-option label="array" value="array" />
              <el-option label="object" value="object" />
            </el-select>
            <span v-else>
              <el-tag size="small" :type="getTypeTagType(row.type)">{{ row.type }}</el-tag>
            </span>
          </template>
        </el-table-column>

        <!-- 必填 -->
        <el-table-column label="必填" width="80" align="center">
          <template #default="{ row }">
            <el-checkbox
              v-if="mode === 'edit'"
              :model-value="isRequired(row.name)"
              @update:model-value="(val) => toggleRequired(row.name, val)"
            />
            <span v-else>
              <span v-if="isRequired(row.name)" class="required-mark">●</span>
              <span v-else class="optional-mark">○</span>
            </span>
          </template>
        </el-table-column>

        <!-- 描述 -->
        <el-table-column label="描述" min-width="180">
          <template #default="{ row }">
            <el-input
              v-if="mode === 'edit'"
              v-model="row.description"
              size="small"
              placeholder="参数描述"
              @change="syncToSchema"
            />
            <span v-else class="param-desc">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>

        <!-- 操作列（编辑模式） -->
        <el-table-column v-if="mode === 'edit'" label="操作" width="60" align="center">
          <template #default="{ row, $index }">
            <el-button
              size="small"
              type="danger"
              text
              @click="removeParam($index)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 新增参数按钮（编辑模式） -->
      <div v-if="mode === 'edit'" class="add-param-row">
        <el-button size="small" @click="addParam">
          + 新增参数
        </el-button>
      </div>
    </div>

    <!-- JSON 视图（编辑模式） -->
    <div v-if="mode === 'edit' && showJson" class="json-editor">
      <el-input
        v-model="jsonText"
        type="textarea"
        :rows="10"
        placeholder='{"type": "object", "properties": {}, "required": []}'
        @blur="syncFromJson"
      />
      <div v-if="jsonError" class="json-error">{{ jsonError }}</div>
    </div>

    <!-- 只读模式：空状态 -->
    <div v-if="mode === 'readonly' && params.length === 0" class="empty-state">
      <span>暂无参数定义</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  // v-model for edit mode
  modelValue: {
    type: Object,
    default: () => ({ type: 'object', properties: {}, required: [] })
  },
  // direct schema prop for readonly mode
  schema: {
    type: Object,
    default: () => ({ type: 'object', properties: {}, required: [] })
  },
  // 'readonly' | 'edit'
  mode: {
    type: String,
    default: 'readonly'
  },
  label: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

// ============ 数据解析 ============

// 当前使用的 schema（edit 模式用 modelValue，readonly 模式用 schema）
const sourceSchema = computed(() => {
  return props.mode === 'edit' ? props.modelValue : (props.schema || {})
})

// properties 解析为参数列表
const params = computed(() => {
  const schema = sourceSchema.value
  const properties = schema.properties || {}
  const required = schema.required || []

  return Object.entries(properties).map(([name, config]) => ({
    name,
    type: config.type || 'string',
    description: config.description || '',
    _required: required.includes(name)
  }))
})

// required 列表
const requiredList = computed(() => {
  const schema = sourceSchema.value
  return schema.required || []
})

// 顶层 type 显示
const displayType = computed(() => {
  const t = sourceSchema.value.type
  return t ? `type: ${t}` : 'type: object'
})

// ============ 编辑模式：表单 ←→ Schema 同步 ============

// 添加参数
const addParam = () => {
  const newParams = [...params.value, { name: '', type: 'string', description: '', _required: false }]
  emitUpdate(newParams)
}

// 删除参数
const removeParam = (index) => {
  const newParams = params.value.filter((_, i) => i !== index)
  emitUpdate(newParams)
}

// 切换必填状态
const toggleRequired = (name, val) => {
  const current = requiredList.value
  let newRequired
  if (val) {
    newRequired = [...current, name]
  } else {
    newRequired = current.filter(n => n !== name)
  }
  const schema = { ...sourceSchema.value, required: newRequired }
  emit('update:modelValue', schema)
}

// 是否必填
const isRequired = (name) => {
  return requiredList.value.includes(name)
}

// 从表单同步回 schema（仅修改 properties 和 required）
const syncToSchema = () => {
  const properties = {}
  for (const p of params.value) {
    if (p.name) {
      properties[p.name] = {
        type: p.type,
        description: p.description
      }
    }
  }
  const required = params.value.filter(p => p._required).map(p => p.name)
  emitUpdate(params.value, required)
}

const emitUpdate = (newParams, newRequired) => {
  const properties = {}
  for (const p of newParams) {
    if (p.name) {
      properties[p.name] = {
        type: p.type || 'string',
        description: p.description || ''
      }
    }
  }
  const required = newRequired !== undefined
    ? newRequired
    : (props.modelValue?.required || [])

  emit('update:modelValue', {
    ...props.modelValue,
    type: 'object',
    properties,
    required
  })
}

// ============ JSON 视图 ============
const showJson = ref(false)
const jsonText = ref('')
const jsonError = ref('')

const toggleJsonView = () => {
  if (!showJson.value) {
    // 切换到 JSON 视图：序列化当前 schema
    jsonText.value = JSON.stringify(props.modelValue || {}, null, 2)
    jsonError.value = ''
  }
  showJson.value = !showJson.value
}

const syncFromJson = () => {
  try {
    const parsed = JSON.parse(jsonText.value)
    jsonError.value = ''
    emit('update:modelValue', parsed)
  } catch (e) {
    jsonError.value = 'JSON 格式错误: ' + e.message
  }
}

// ============ 工具方法 ============
const getTypeTagType = (type) => {
  const map = {
    string: 'primary',
    integer: 'success',
    number: 'success',
    boolean: 'warning',
    array: 'info',
    object: 'info'
  }
  return map[type] || 'primary'
}
</script>

<style scoped>
.schema-viewer {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}

.schema-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  font-size: 12px;
}

.schema-label {
  font-weight: 600;
  color: #303133;
}

.schema-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-badge {
  background: #ecf5ff;
  color: #409eff;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
}

.required-count {
  color: #e6a23c;
  font-size: 11px;
}

.toggle-json-btn {
  margin-left: auto;
  font-size: 11px;
}

.schema-table-wrapper {
  padding: 8px;
}

.param-name {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #409eff;
}

.param-desc {
  font-size: 12px;
  color: #606266;
}

.required-mark {
  color: #f56c6c;
  font-size: 14px;
}

.optional-mark {
  color: #c0c4cc;
  font-size: 14px;
}

.add-param-row {
  padding: 8px;
  border-top: 1px dashed #e4e7ed;
}

.json-editor {
  padding: 8px;
}

.json-error {
  color: #f56c6c;
  font-size: 11px;
  margin-top: 4px;
}

.empty-state {
  padding: 20px;
  text-align: center;
  color: #909399;
  font-size: 12px;
}

:deep(.el-table .cell) {
  padding: 4px 8px;
}

:deep(.el-input__wrapper) {
  font-size: 12px;
}
</style>