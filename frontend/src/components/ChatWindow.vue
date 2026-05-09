<template>
  <div class="chat-window">
    <el-card class="chat-card">
      <template #header>
        <div class="card-header">
          <span>AI表单助手</span>
          <el-tag v-if="formId" type="success">表单ID: {{ formId }}</el-tag>
        </div>
      </template>
      
      <div class="chat-messages" ref="messagesContainer">
        <MessageItem
          v-for="(msg, index) in messages"
          :key="index"
          :message="msg"
          :form-data="formData"
          :version="version"
          @field-change="handleFieldChange"
          @form-submit="handleFormSubmit"
        />
      </div>
      
      <div class="chat-input">
        <el-input
          v-model="userInput"
          type="textarea"
          :rows="2"
          placeholder="输入您的需求，例如：帮我填一个销售订单..."
          @keydown.enter.ctrl="sendMessage"
        />
        <el-button type="primary" @click="sendMessage" :loading="loading">
          发送
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import MessageItem from './MessageItem.vue'

const messages = ref([])
const userInput = ref('')
const loading = ref(false)
const formId = ref('')
const formSchema = ref(null)
const formData = ref({})
const version = ref(1)
const ws = ref(null)
const messagesContainer = ref(null)

const addMessage = (type, text = null, form = null) => {
  messages.value.push({ type, text, form })
  scrollToBottom()
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

const connectWebSocket = (fId) => {
  const wsUrl = `ws://localhost:6173/api/v1/ws/form/${fId}`
  ws.value = new WebSocket(wsUrl)
  
  ws.value.onopen = () => console.log('WebSocket连接成功')
  ws.value.onmessage = (event) => handleWebSocketMessage(JSON.parse(event.data))
  ws.value.onclose = () => console.log('WebSocket连接关闭')
  ws.value.onerror = (error) => console.error('WebSocket错误:', error)
}

const handleWebSocketMessage = (data) => {
  switch (data.type) {
    case 'init':
      version.value = data.version
      if (data.schema) formSchema.value = data.schema
      break
    case 'fieldChange':
      if (data.fieldCode && data.fieldValue !== undefined) {
        formData.value[data.fieldCode] = data.fieldValue
        version.value = data.version
      }
      break
    case 'formControl':
      ElMessage.info(`收到控制指令: ${data.controlType}`)
      break
  }
}

const sendMessage = async () => {
  if (!userInput.value.trim()) return
  
  const input = userInput.value
  userInput.value = ''
  loading.value = true
  
  addMessage('user', input)
  
  try {
    const response = await axios.post('/api/v1/form/generate', {
      userInput: input,
      userId: 'user_001'
    })
    
    if (response.data.success) {
      formId.value = response.data.formId
      formSchema.value = response.data.formSchema
      formData.value = {}
      version.value = 1
      
      formSchema.value.fields.forEach(field => {
        if (field.defaultValue !== undefined) {
          formData.value[field.fieldCode] = field.defaultValue
        }
      })
      
      addMessage('ai', `已为您生成${formSchema.value.formName}，请填写：`, formSchema.value)
      connectWebSocket(formId.value)
    } else {
      addMessage('ai', response.data.message || '生成表单失败')
    }
  } catch (error) {
    console.error('Error:', error)
    addMessage('ai', '网络错误，请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleFieldChange = (fieldCode, fieldValue) => {
  formData.value[fieldCode] = fieldValue
  
  if (ws.value && ws.value.readyState === WebSocket.OPEN) {
    ws.value.send(JSON.stringify({
      type: 'fieldChange',
      formId: formId.value,
      fieldCode,
      fieldValue,
      userId: 'user_001',
      version: version.value
    }))
  }
}

const handleFormSubmit = async () => {
  if (!formId.value) return
  
  loading.value = true
  
  try {
    const response = await axios.post('/api/v1/form/submit', {
      formId: formId.value,
      data: formData.value,
      userId: 'user_001',
      version: version.value
    })
    
    if (response.data.success) {
      ElMessage.success(response.data.message)
      addMessage('ai', response.data.message)
      resetForm()
    } else {
      ElMessage.error(response.data.message)
    }
  } catch (error) {
    console.error('Error:', error)
    ElMessage.error('提交失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  formId.value = ''
  formSchema.value = null
  formData.value = {}
  if (ws.value) {
    ws.value.close()
    ws.value = null
  }
}

onMounted(() => {
  addMessage('ai', '您好！我是AI表单助手，请告诉我您需要填写什么表单？')
})

onUnmounted(() => {
  if (ws.value) ws.value.close()
})
</script>

<style scoped>
.chat-window {
  width: 100%;
  max-width: 800px;
}

.chat-card {
  height: calc(100vh - 140px);
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f9f9f9;
}

.chat-input {
  padding: 20px;
  border-top: 1px solid #e6e6e6;
  display: flex;
  gap: 12px;
}

.chat-input .el-textarea {
  flex: 1;
}
</style>