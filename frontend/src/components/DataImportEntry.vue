<template>
  <div class="data-import-entry">
    <el-card shadow="hover" class="import-card">
      <template #header>
        <div class="card-header">
          <span>📥 {{ formName || formCode }} - 历史数据导入</span>
        </div>
      </template>

      <div class="import-content">
        <el-alert
          :title="message || '请上传您的历史数据文件'"
          type="info"
          :closable="false"
          style="margin-bottom: 20px"
        />

        <!-- 步骤1: 下载模板 -->
        <div class="step-section">
          <h4>📋 步骤1: 下载导入模板</h4>
          <p class="step-description">
            下载模板文件，了解数据格式要求，然后按照格式准备您的真实业务数据。
          </p>
          <el-button 
            type="primary" 
            plain
            @click="downloadTemplate"
            :loading="downloading"
          >
            <el-icon><Download /></el-icon>
            下载模板文件
          </el-button>
          <p class="hint">
            💡 提示：模板文件展示了正确的JSONL格式，您可以参考它来准备自己的数据
          </p>
        </div>

        <!-- 步骤2: 准备数据 -->
        <div class="step-section">
          <h4>✏️ 步骤2: 准备您的数据</h4>
          <p class="step-description">
            从您的业务系统（OA/ERP/CRM等）导出数据，并转换为JSONL格式。
          </p>
          <el-alert
            title="数据格式要求"
            type="warning"
            :closable="false"
          >
            <template #default>
              • 文件格式：.jsonl（每行一个JSON对象）<br/>
              • 字段名：必须与本体定义中的fieldCode一致<br/>
              • 编码：UTF-8<br/>
              • 示例：{"field1":"value1","field2":"value2"}
            </template>
          </el-alert>
        </div>

        <!-- 步骤3: 上传文件 -->
        <div class="step-section">
          <h4>📤 步骤3: 上传并导入</h4>
          <p class="step-description">
            上传您准备好的数据文件，系统将立即执行导入并生成报告。
          </p>
          
          <el-upload
            ref="uploadRef"
            class="upload-demo"
            drag
            :auto-upload="false"
            :on-change="handleFileChange"
            :limit="1"
            accept=".jsonl"
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                只支持 .jsonl 格式文件
              </div>
            </template>
          </el-upload>

          <div v-if="selectedFile" class="file-info">
            <el-tag type="success">
              <el-icon><Document /></el-icon>
              {{ selectedFile.name }}
            </el-tag>
            <span class="file-size">({{ formatFileSize(selectedFile.size) }})</span>
          </div>

          <el-button
            type="primary"
            @click="startImport"
            :loading="importing"
            :disabled="!selectedFile"
            style="margin-top: 16px; width: 100%"
          >
            {{ importing ? '正在导入...' : '开始导入' }}
          </el-button>
        </div>

        <!-- 导入进度 -->
        <div v-if="importing" class="import-progress">
          <el-progress 
            :percentage="importProgress" 
            :status="importProgress < 100 ? 'active' : 'success'"
          />
          <p class="progress-text">{{ importingText }}</p>
        </div>
      </div>
    </el-card>

    <!-- 导入结果报告 -->
    <el-dialog
      v-model="showReport"
      title="📊 导入结果报告"
      width="700px"
      :close-on-click-modal="false"
    >
      <div v-if="importResult" class="report-content">
        <el-result
          :icon="importResult.success ? 'success' : 'error'"
          :title="importResult.success ? '导入成功' : '导入失败'"
          :sub-title="importResult.message"
        />

        <div v-if="importResult.success" class="report-details">
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
            v-if="importResult.nestedFields && importResult.nestedFields.length > 0"
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

      <template #footer>
        <el-button type="primary" @click="showReport = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, UploadFilled, Document } from '@element-plus/icons-vue'
import axios from 'axios'

const props = defineProps({
  formCode: {
    type: String,
    required: true
  },
  formName: {
    type: String,
    default: ''
  },
  message: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['import-complete'])

const downloading = ref(false)
const importing = ref(false)
const importProgress = ref(0)
const importingText = ref('')
const selectedFile = ref(null)
const uploadRef = ref(null)
const showReport = ref(false)
const importResult = ref(null)

// 下载模板
const downloadTemplate = async () => {
  downloading.value = true
  try {
    const response = await axios.get(
      `/api/v1/config/import/template/${props.formCode}`,
      { responseType: 'blob' }
    )
    
    // 创建下载链接
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `${props.formCode}_template.jsonl`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('模板下载成功')
  } catch (error) {
    console.error('下载模板失败:', error)
    ElMessage.error('下载模板失败: ' + (error.response?.data?.detail || error.message))
  } finally {
    downloading.value = false
  }
}

// 处理文件选择
const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

// 开始导入
const startImport = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择要上传的文件')
    return
  }

  importing.value = true
  importProgress.value = 10
  importingText.value = '正在上传文件...'

  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)

    // 模拟进度更新
    const progressInterval = setInterval(() => {
      if (importProgress.value < 90) {
        importProgress.value += Math.random() * 15
        importingText.value = '正在导入数据...'
      }
    }, 500)

    const response = await axios.post(
      `/api/v1/config/import/upload?formCode=${props.formCode}`,
      formData
    )

    clearInterval(progressInterval)
    importProgress.value = 100
    importingText.value = '导入完成'

    importResult.value = response.data
    showReport.value = true

    // 通知父组件导入完成
    emit('import-complete', response.data)

    setTimeout(() => {
      importing.value = false
    }, 500)

  } catch (error) {
    console.error('导入失败:', error)
    importResult.value = {
      success: false,
      message: '导入失败: ' + (error.response?.data?.detail || error.message)
    }
    showReport.value = true
    importing.value = false
  }
}
</script>

<style scoped>
.data-import-entry {
  margin: 16px 0;
}

.import-card {
  border-radius: 12px;
}

.card-header {
  font-weight: 600;
  font-size: 16px;
  color: #303133;
}

.import-content {
  padding: 8px 0;
}

.step-section {
  margin-bottom: 24px;
  padding: 16px;
  background: #f9fafb;
  border-radius: 8px;
}

.step-section h4 {
  margin: 0 0 8px 0;
  color: #303133;
  font-size: 15px;
}

.step-description {
  margin: 0 0 12px 0;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
}

.hint {
  margin: 8px 0 0 0;
  color: #909399;
  font-size: 13px;
}

.file-info {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.file-size {
  color: #909399;
  font-size: 13px;
}

.import-progress {
  margin-top: 20px;
  text-align: center;
}

.progress-text {
  margin-top: 8px;
  color: #606266;
  font-size: 14px;
}

.report-content {
  min-height: 300px;
}

.report-details {
  margin-top: 20px;
}
</style>
