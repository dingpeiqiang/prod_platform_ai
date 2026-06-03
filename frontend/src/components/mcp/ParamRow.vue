<template>
  <tr :key="param.id" :class="'level-' + level">
    <td class="param-name-td" :data-level="level">
      <div class="param-name-cell">
        <span 
          v-if="param.children && param.children.length > 0"
          class="expand-icon"
          @click="toggleExpand(param)"
        >
          <svg v-if="param.expanded" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="6 9 12 15 18 9"></polyline>
          </svg>
          <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="9 6 15 12 9 18"></polyline>
          </svg>
        </span>
        <span v-else class="expand-icon placeholder"></span>
        <el-input 
          v-model="param.name" 
          placeholder="请输入" 
          size="small"
          class="name-input"
        />
      </div>
    </td>
    <td>
      <el-input 
        v-model="param.description" 
        placeholder="请输入" 
        size="small"
      />
    </td>
    <td>
      <el-select 
        v-model="param.data_type" 
        size="small" 
        @change="onTypeChange(param)"
      >
        <el-option label="string" value="string" />
        <el-option label="integer" value="integer" />
        <el-option label="number" value="number" />
        <el-option label="boolean" value="boolean" />
        <el-option label="object" value="object" />
        <el-option label="array" value="array" />
      </el-select>
    </td>
    <td>
      <el-switch v-model="param.required" size="small" />
    </td>
    <template v-if="!isOutput">
      <td>
        <el-input 
          v-model="param.enum_values" 
          placeholder="多个用英文逗号分隔" 
          size="small"
        />
      </td>
    </template>
    <template v-if="!isOutput">
      <td>
        <el-input 
          v-model="param.default_value" 
          placeholder="请输入" 
          size="small"
        />
      </td>
    </template>
    <td>
      <el-button 
        size="small" 
        icon="Delete" 
        type="danger"
        @click="remove(param)"
      />
    </td>
    <td>
      <el-button 
        v-if="param.data_type === 'object' || param.data_type === 'array'"
        size="small" 
        icon="Plus" 
        @click="addChild(param)"
        class="add-child-btn"
      />
    </td>
  </tr>
  <template v-if="param.expanded && param.children && param.children.length > 0">
    <ParamRow
      v-for="child in param.children"
      :key="child.id"
      :param="child"
      :level="level + 1"
      :is-output="isOutput"
      @add-child="$emit('add-child', $event)"
      @remove="$emit('remove', $event)"
      @type-change="$emit('type-change', $event)"
    />
  </template>
</template>

<script setup>
defineProps({
  param: {
    type: Object,
    required: true
  },
  level: {
    type: Number,
    default: 0
  },
  isOutput: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['add-child', 'remove', 'type-change'])

const toggleExpand = (param) => {
  param.expanded = !param.expanded
}

const addChild = (param) => {
  emit('add-child', param)
}

const remove = (param) => {
  emit('remove', param)
}

const onTypeChange = (param) => {
  emit('type-change', param)
}
</script>

<style scoped>
.param-name-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.expand-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #909399;
  flex-shrink: 0;
  border-radius: 3px;
  transition: all 0.2s;
}

.expand-icon:hover {
  color: #5b7cfa;
  background: #f0f5ff;
}

.expand-icon.placeholder {
  visibility: hidden;
}

.name-input {
  flex: 1;
  min-width: 0;
}

.add-child-btn {
  color: #409eff;
  border-color: #409eff;
}

:deep(.el-input),
:deep(.el-select) {
  width: 100%;
  margin: 0;
}

:deep(.el-input__wrapper),
:deep(.el-select__wrapper) {
  margin: 0;
  padding: 0;
  justify-content: flex-start;
}

:deep(.el-input__inner),
:deep(.el-select__placeholder) {
  text-align: left;
}

:deep(.el-switch) {
  margin: 0;
}

:deep(.el-button) {
  margin: 0;
}

.param-name-td {
  min-width: 150px;
}

.param-name-td[data-level="0"] {
  padding-left: 16px !important;
}

.param-name-td[data-level="1"] {
  padding-left: 36px !important;
}

.param-name-td[data-level="2"] {
  padding-left: 56px !important;
}

.param-name-td[data-level="3"] {
  padding-left: 76px !important;
}

.param-name-td[data-level="4"],
.param-name-td[data-level="5"],
.param-name-td[data-level="6"],
.param-name-td[data-level="7"],
.param-name-td[data-level="8"],
.param-name-td[data-level="9"] {
  padding-left: 96px !important;
}

:deep(.level-0 > td) {
  background-color: #ffffff !important;
  border-left: 4px solid #5b7cfa !important;
}

:deep(.level-1 > td) {
  background-color: #f0f5ff !important;
  border-left: 4px solid #60a5fa !important;
}

:deep(.level-2 > td) {
  background-color: #e0f2fe !important;
  border-left: 4px solid #22d3ee !important;
}

:deep(.level-3 > td) {
  background-color: #dcfce7 !important;
  border-left: 4px solid #4ade80 !important;
}

:deep(.level-4 > td),
:deep(.level-5 > td),
:deep(.level-6 > td),
:deep(.level-7 > td),
:deep(.level-8 > td),
:deep(.level-9 > td) {
  background-color: #fef3c7 !important;
  border-left: 4px solid #fbbf24 !important;
}
</style>
