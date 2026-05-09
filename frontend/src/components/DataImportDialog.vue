<template>
  <div class="data-import-dialog">
    <el-dialog
      v-model="visible"
      title="📊 历史数据导入"
      width="700px"
      :close-on-click-modal="false"
      @close="handleClose"
    >
      <!-- 步骤引导 -->
      <el-steps :active="currentStep" finish-status="success" simple class="import-steps">
        <el-step title="选择表单" />
        <el-step title="确认导入" />
        <el-step title="查看结果" />
      </el-steps>

      <!-- 步骤1: 选择表单类型 -->
      <div v-if="currentStep === 0" class="step-content">
        <div class="step-description">
          <p>请选择要导入的历史数据类型。系统会自动从 <code>config/import_data/</code> 目录读取数据文件。</p>
        </div>

        <el-alert
          v-if="forms.length === 0"
          title="暂无可导入的数据"
          type="warning"
          :closable="false"
          style="margin-bottom: 16px"
        >
          <template #default>
            请在 <code>backend/config/import_data/</code> 目录下添加数据文件，格式为：<br/>
            • <code>{formCode}.data.jsonl</code> - 数据文件（每行一个JSON对象）<br/>
            • <code>{formCode}.schema.json</code> - 元数据声明（可选）
          </template>
        </el-alert>

        <el-table
          v-else
          :data="forms"
          highlight-current-row
          @current-change="handleFormSelect"
          style="width: 100%"
        >
          <el-table-column label="选择" width="60">
            <template #default="{ row }">
              <el-radio
                v-model="selectedFormCode"
                :label="row.formCode"
                @change="selectedFormCode = row.formCode"
              >
                &nbsp;
              </el-radio>
            </template>
          </el-table-column>
          <el-table-column prop="formCode" label="表单编码" width="180" />
          <el-table-column prop="formName" label="表单名称" width="150" />
          <el-table-column prop="dataType" label="数据格式" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.dataType === 'jsonl' ? 'success' : 'info'">
                {{ row.dataType.toUpperCase() }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
          <el-table-column label="Schema" width="80" align="center">
            <template #default="{ row }">
              <el-icon v-if="row.hasSchema" :color="hasSchemaColor"><Check /></el-icon>
              <el-icon v-else :color="noSchemaColor"><Close /></el-icon>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="selectedForm" class="form-preview">
          <h4>📋 表单信息</h4>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="表单编码">{{ selectedForm.formCode }}</el-descriptions-item>
            <el-descriptions-item label="表单名称">{{ selectedForm.formName }}</el-descriptions-item>
            <el-descriptions-item label="数据格式">{{ selectedForm.dataType }}</el-descriptions-item>
            <el-descriptions-item label="数据文件">{{ selectedForm.dataFile }}</el-descriptions-item>
            <el-descriptions-item label="Schema" :span="2">
              <el-tag v-if="selectedForm.hasSchema" type="success">已配置</el-tag>
              <el-tag v-else type="info">未配置</el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="selectedForm.description" label="描述" :span="2">
              {{ selectedForm.description }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <!-- 步骤2: 确认导入 -->
      <div v-if="currentStep === 1" class="step-content">
        <div class="step-description">
          <p>请确认导入配置，系统将把历史数据写入数据库并建立推荐索引。</p>
        </div>

        <el-card shadow="never" class="import-config-card">
          <template #header>
            <div class="card-header">
              <span>📦 导入配置</span>
            </div>
          </template>
          <el-form label-width="120px">
            <el-form-item label="表单类型">
              <el-tag>{{ selectedForm?.formName }} ({{ selectedForm?.formCode }})</el-tag>
            </el-form-item>
            <el-form-item label="数据源">
              <code>{{ selectedForm?.dataFile }}</code>
            </el-form-item>
            <el-form-item label="限制条数">
              <el-input-number
                v-model="importLimit"
                :min="1"
                :max="10000"
                placeholder="不填则导入全部"
                style="width: 200px"
              />
              <span class="form-tip">可选，用于测试导入</span>
            </el-form-item>
          </el-form>
        </el-card>

        <el-alert
          title="⚠️ 注意事项"
          type="warning"
          :closable="false"
          style="margin-top: 16px"
        >
          <template #default>
            • 导入过程可能需要几分钟，请耐心等待<br/>
            • 重复导入会追加数据，不会覆盖已有记录<br/>
            • 导入完成后，推荐引擎将基于这些数据进行智能推荐
          </template>
        </el-alert>
      </div>

      <!-- 步骤3: 导入结果 -->
      <div v-if="currentStep === 2" class="step-content">
        <div v-if="importing" class="importing-state">
          <el-progress
            type="circle"
            :percentage="importProgress"
            :status="importProgress < 100 ? 'active' : 'success'"
          />
          <p class="importing-text">{{ importingText }}</p>
        </div>

        <div v-else-if="importResult" class="result-state">
          <el-result
            :icon="importResult.success ? 'success' : 'error'"
            :title="importResult.success ? '导入成功' : '导入失败'"
            :sub-title="importResult.message"
          >
            <template #extra>
              <el-button type="primary" @click="handleClose">完成</el-button>
              <el-button @click="resetImport">重新导入</el-button>
            </template>
          </el-result>

          <div v-if="importResult.success && importResult.totalImported > 0" class="result-details">
            <el-card shadow="never">
              <template #header>
                <div class="card-header">
                  <span>📊 数据统计</span>
                </div>
              </template>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="源记录数">{{ importResult.totalSource }}</el-descriptions-item>
                <el-descriptions-item label="实际导入">{{ importResult.totalImported }}</el-descriptions-item>
                <el-descriptions-item label="跳过(空数据)">{{ importResult.totalSkipped }}</el-descriptions-item>
                <el-descriptions-item label="错误数">{{ importResult.totalErrors }}</el-descriptions-item>
              </el-descriptions>
            </el-card>

            <el-card v-if="importResult.fieldStats && importResult.fieldStats.length > 0" shadow="never" style="margin-top: 16px">
              <template #header>
                <div class="card-header">
                  <span>🔍 字段分布 (Top 5)</span>
                </div>
              </template>
              <el-collapse>
                <el-collapse-item
                  v-for="stat in importResult.fieldStats.slice(0, 10)"
                  :key="stat.fieldCode"
                  :title="`${stat.fieldCode} (${stat.distinctValues} 种不同值)`"
                >
                  <el-table :data="stat.topValues || []" size="small" style="width: 100%">
                    <el-table-column prop="value" label="值" show-overflow-tooltip />
                    <el-table-column prop="count" label="出现次数" width="100" align="right">
                      <template #default="{ row }">
                        {{ row.count }} 次
                      </template>
                    </el-table-column>
                  </el-table>
                </el-collapse-item>
              </el-collapse>
            </el-card>

            <el-alert
              v-else-if="importResult.success && importResult.totalImported > 0"
              title="暂无字段统计信息"
              type="info"
              :closable="false"
              style="margin-top: 16px"
            >
              <template #default>
                数据已成功导入，但字段统计信息不可用。这可能是因为数据为空或处理过程中出现问题。<br/>
                建议：检查后端日志或尝试重新导入。
              </template>
            </el-alert>

            <el-alert
              v-if="importResult.nestedFields.length > 0"
              title="嵌套字段已展平处理"
              type="info"
              :closable="false"
              style="margin-top: 16px"
            >
              <template #default>
                检测到嵌套字段：{{ importResult.nestedFields.join(', ') }}<br/>
                这些字段已自动展平并写入 FormHistory，可用于推荐引擎查询。
              </template>
            </el-alert>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleClose" :disabled="importing">取消</el-button>
          <el-button
            v-if="currentStep === 0"
            type="primary"
            :disabled="!selectedFormCode"
            @click="nextStep"
          >
            下一步
          </el-button>
          <el-button
            v-if="currentStep === 1"
            @click="prevStep"
            :disabled="importing"
          >
            上一步
          </el-button>
          <el-button
            v-if="currentStep === 1"
            type="primary"
            :loading="importing"
            @click="startImport"
          >
            开始导入
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Close } from '@element-plus/icons-vue'
import axios from 'axios'

const API_BASE = '/api/v1/config'

const visible = ref(false)
const currentStep = ref(0)
const forms = ref([])
const selectedFormCode = ref('')
const importLimit = ref(null)
const importing = ref(false)
const importingText = ref('正在导入...')
const importProgress = ref(0)
const importResult = ref(null)

const selectedForm = computed(() => {
  return forms.value.find(f => f.formCode === selectedFormCode.value) || null
})

const hasSchemaColor = computed(() => {
  return getComputedStyle(document.documentElement).getPropertyValue('--color-success-600').trim()
})

const noSchemaColor = computed(() => {
  return getComputedStyle(document.documentElement).getPropertyValue('--text-tertiary').trim()
})

// 打开对话框
const open = async () => {
  visible.value = true
  currentStep.value = 0
  selectedFormCode.value = ''
  importLimit.value = null
  importResult.value = null
  await loadForms()
}

// 关闭对话框
const handleClose = () => {
  if (!importing.value) {
    visible.value = false
  }
}

// 加载可导入表单列表
const loadForms = async () => {
  try {
    const res = await axios.get(`${API_BASE}/import/list`)
    if (res.data.success) {
      forms.value = res.data.forms
      if (forms.value.length > 0) {
        selectedFormCode.value = forms.value[0].formCode
      }
    } else {
      ElMessage.error('获取表单列表失败')
    }
  } catch (error) {
    console.error('加载表单列表失败:', error)
    ElMessage.error('加载表单列表失败: ' + (error.response?.data?.detail || error.message))
  }
}

// 选择表单
const handleFormSelect = (row) => {
  if (row) {
    selectedFormCode.value = row.formCode
  }
}

// 下一步
const nextStep = () => {
  if (currentStep.value < 2) {
    currentStep.value++
  }
}

// 上一步
const prevStep = () => {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

// 开始导入
const startImport = async () => {
  if (!selectedFormCode.value) {
    ElMessage.warning('请先选择要导入的表单')
    return
  }

  importing.value = true
  importingText.value = '正在连接服务器...'
  importProgress.value = 10

  try {
    // 模拟进度更新
    const progressInterval = setInterval(() => {
      if (importProgress.value < 90) {
        importProgress.value += Math.random() * 15
        importingText.value = `正在导入 ${selectedForm.value?.formName} 数据...`
      }
    }, 500)

    const res = await axios.post(`${API_BASE}/import/execute`, {
      formCode: selectedFormCode.value,
      limit: importLimit.value || undefined
    })

    clearInterval(progressInterval)
    importProgress.value = 100
    importingText.value = '导入完成'

    // 调试日志：打印返回数据
    console.log('=== 导入结果 ===' )
    console.log('完整响应:', res.data)
    console.log('fieldStats:', res.data.fieldStats)
    if (res.data.fieldStats && res.data.fieldStats.length > 0) {
      console.log('第一个字段统计:', res.data.fieldStats[0])
    }

    if (res.data.success) {
      importResult.value = res.data
      setTimeout(() => {
        importing.value = false
        currentStep.value = 2
      }, 500)
    } else {
      importResult.value = res.data
      importing.value = false
      currentStep.value = 2
    }
  } catch (error) {
    console.error('导入失败:', error)
    importResult.value = {
      success: false,
      message: '导入失败: ' + (error.response?.data?.detail || error.message)
    }
    importing.value = false
    currentStep.value = 2
  }
}

// 重置导入
const resetImport = () => {
  currentStep.value = 0
  selectedFormCode.value = ''
  importLimit.value = null
  importResult.value = null
  importProgress.value = 0
}

// 暴露方法给父组件
defineExpose({
  open
})
</script>

<style scoped>
.data-import-dialog {
  :deep(.el-dialog__body) {
    padding: 20px 30px;
  }
}

.import-steps {
  margin-bottom: 24px;
}

.step-content {
  min-height: 300px;
}

.step-description {
  margin-bottom: 16px;
  color: var(--text-secondary);
  font-size: 14px;
}

.step-description code {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.form-preview {
  margin-top: 20px;
}

.form-preview h4 {
  margin-bottom: 12px;
  color: var(--text-primary);
  font-size: 15px;
}

.import-config-card {
  margin-top: 16px;
}

.card-header {
  font-weight: 500;
  color: var(--text-primary);
}

.form-tip {
  margin-left: 8px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.importing-state {
  text-align: center;
  padding: 40px 0;
}

.importing-text {
  margin-top: 16px;
  color: #606266;
  font-size: 14px;
}

.result-state {
  padding: 20px 0;
}

.result-details {
  margin-top: 24px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
