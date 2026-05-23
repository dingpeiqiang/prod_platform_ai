<template>
  <div class="tool-list">
    <div class="toolbar">
      <el-input 
        v-model="searchKeyword" 
        placeholder="搜索工具名称或描述"
        prefix-icon="Search"
        style="width: 300px"
        clearable
      />
      <el-select v-model="filterCategory" placeholder="全部分类" clearable style="width: 200px">
        <el-option label="全部分类" value="" />
        <el-option 
          v-for="cat in categories" 
          :key="cat.code" 
          :label="cat.name" 
          :value="cat.code"
        />
      </el-select>
    </div>

    <el-table :data="filteredTools" stripe style="width: 100%">
      <el-table-column prop="name" label="工具名称" width="200">
        <template #default="{ row }">
          <strong>{{ row.name }}</strong>
        </template>
      </el-table-column>
      
      <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip />
      
      <el-table-column label="分类" width="120">
        <template #default="{ row }">
          <el-tag size="small">{{ getCategoryName(row.metadata?.category) }}</el-tag>
        </template>
      </el-table-column>
      
      <el-table-column label="调用统计" width="180">
        <template #default="{ row }">
          <div class="stats-cell">
            <div>总调用: {{ row.stats?.total_calls || 0 }}</div>
            <div :class="calculateSuccessRate(row.stats) > 90 ? 'success-text' : 'warning-text'">
              成功率: {{ calculateSuccessRate(row.stats) }}%
            </div>
          </div>
        </template>
      </el-table-column>
      
      <el-table-column label="平均响应" width="120">
        <template #default="{ row }">
          {{ row.stats?.avg_response_time_ms?.toFixed(0) || 0 }} ms
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="$emit('test-tool', row)">测试</el-button>
          <el-button size="small" @click="openDetail(row)">详情</el-button>
          <el-button size="small" @click="$emit('view-logs', row.name)">日志</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="filteredTools.length === 0" class="empty-state">
      <p>暂无工具数据</p>
    </div>

    <!-- 工具详情抽屉 -->
    <el-drawer
      v-model="showDetailDrawer"
      :title="selectedTool?.name || '工具详情'"
      size="560px"
      direction="rtl"
    >
      <template #default>
        <div v-if="selectedTool" class="tool-detail">
          <!-- 基本信息 -->
          <div class="detail-section">
            <h4 class="section-title">基本信息</h4>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">工具名称</span>
                <span class="info-value">{{ selectedTool.name }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">分类</span>
                <el-tag size="small">{{ getCategoryName(selectedTool.metadata?.category) }}</el-tag>
              </div>
              <div class="info-item" style="grid-column: 1 / -1;">
                <span class="info-label">描述</span>
                <span class="info-value desc-value">{{ selectedTool.description || '无' }}</span>
              </div>
            </div>
          </div>

          <!-- 入参 Schema -->
          <div class="detail-section">
            <h4 class="section-title">入参定义 (inputSchema)</h4>
            <SchemaViewer
              :schema="selectedTool.inputSchema || {}"
              mode="readonly"
              label="入参"
            />
          </div>

          <!-- 出参 Schema -->
          <div class="detail-section">
            <h4 class="section-title">出参定义 (outputSchema)</h4>
            <SchemaViewer
              v-if="selectedTool.outputSchema && Object.keys(selectedTool.outputSchema).length > 0"
              :schema="selectedTool.outputSchema"
              mode="readonly"
              label="出参"
            />
            <div v-else class="no-schema">
              <span>暂无出参定义</span>
            </div>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import SchemaViewer from './SchemaViewer.vue'

const props = defineProps({
  tools: {
    type: Array,
    default: () => []
  },
  categories: {
    type: Array,
    default: () => []
  }
})

defineEmits(['test-tool', 'view-logs'])

const searchKeyword = ref('')
const filterCategory = ref('')
const showDetailDrawer = ref(false)
const selectedTool = ref(null)

const filteredTools = computed(() => {
  let result = props.tools
  
  if (filterCategory.value) {
    result = result.filter(t => t.metadata?.category === filterCategory.value)
  }
  
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(t => 
      t.name.toLowerCase().includes(keyword) ||
      t.description?.toLowerCase().includes(keyword)
    )
  }
  
  return result
})

const calculateSuccessRate = (stats) => {
  if (!stats || stats.total_calls === 0) return 0
  return ((stats.success_calls / stats.total_calls) * 100).toFixed(1)
}

const getCategoryName = (category) => {
  const cat = props.categories.find(c => c.code === category)
  return cat?.name || category
}

const openDetail = (tool) => {
  selectedTool.value = tool
  showDetailDrawer.value = true
}
</script>

<style scoped>
.tool-list {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.stats-cell {
  font-size: 12px;
  line-height: 1.5;
}

.success-text {
  color: #67c23a;
  font-weight: 500;
}

.warning-text {
  color: #e6a23c;
  font-weight: 500;
}

.empty-state {
  text-align: center;
  padding: 30px;
  color: #909399;
}

:deep(.el-table) {
  flex: 1;
  min-height: 0;
}

:deep(.el-table__body-wrapper) {
  overflow-y: auto;
}

/* 详情抽屉样式 */
.tool-detail {
  padding: 0 4px;
}

.detail-section {
  margin-bottom: 24px;
}

.section-title {
  margin: 0 0 12px 0;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  border-left: 3px solid #409eff;
  padding-left: 8px;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 11px;
  color: #909399;
}

.info-value {
  font-size: 12px;
  color: #303133;
}

.desc-value {
  line-height: 1.5;
}

.no-schema {
  padding: 16px;
  text-align: center;
  color: #c0c4cc;
  font-size: 12px;
  border: 1px dashed #e4e7ed;
  border-radius: 6px;
}
</style>