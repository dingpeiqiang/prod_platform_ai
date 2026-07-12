<template>
  <section class="chat-container" :class="{ 'mobile-hidden': mobileHidden }">
    <!-- 消息列表 -->
    <div class="messages" ref="messagesContainer" role="log" aria-live="polite">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="message"
        :class="msg.type"
      >
        <div class="message-avatar" :class="msg.type">
          <i
            :class="msg.type === 'ai' ? 'fa-solid fa-robot' : 'fa-solid fa-user'"
          />
        </div>
        <div class="message-content">
          <!-- 欢迎消息 -->
          <div v-if="msg.isWelcome" class="message-bubble">
            <p>{{ msg.text }}</p>
            <div class="welcome-cards">
              <button type="button" class="welcome-card" @click="handleCardClick('query')">
                <div class="card-icon"><i class="fa-solid fa-magnifying-glass" /></div>
                <h4>AI智查</h4>
                <p>查询历史商品，快速复制配置</p>
              </button>
              <button type="button" class="welcome-card" @click="handleCardClick('file')">
                <div class="card-icon"><i class="fa-solid fa-file-import" /></div>
                <h4>AI方案导入</h4>
                <p>上传文档，批量导入配置</p>
              </button>
              <button type="button" class="welcome-card" @click="handleCardClick('chat')">
                <div class="card-icon"><i class="fa-solid fa-comments" /></div>
                <h4>对话式配置</h4>
                <p>自然语言描述，智能生成配置</p>
              </button>
            </div>
          </div>
          <!-- 查询结果消息 -->
          <div v-else-if="msg.isQueryResults" class="message-bubble">
            <div class="query-results">
              <div v-for="(product, i) in msg.products" :key="product.id" class="query-card">
                <div class="query-card-head">
                  <span class="name">{{ product.name }}</span>
                  <span class="tag">已提交</span>
                </div>
                <p class="desc">{{ product.desc }}</p>
                <div class="query-actions">
                  <button type="button" class="btn-primary-soft" @click="handlePrepareProduct(i)">
                    <i class="fa-solid fa-copy" /> 复制配置
                  </button>
                  <button type="button" class="btn-ghost" @click="handleViewProduct(i)">
                    <i class="fa-solid fa-eye" /> 查看详情
                  </button>
                </div>
              </div>
            </div>
          </div>
          <!-- 表单消息 -->
          <div v-else-if="msg.form" class="message-bubble form-bubble">
            <p class="message-text">{{ msg.text }}</p>
            <MessageItem
              :message="msg"
              :form-data="formData"
              :version="version"
              @field-change="handleFieldChange"
              @form-submit="handleFormSubmit"
            />
          </div>
          <!-- 普通文本消息 -->
          <div v-else class="message-bubble" v-html="msg.text" />
        </div>
      </div>
      <div ref="messagesEnd" />
    </div>

    <!-- 输入区域 -->
    <div class="input-area">
      <div v-if="currentSkill" class="skill-tags">
        <span class="skill-tag">
          <i :class="`fa-solid ${getSkillIcon(currentSkill)}`" />
          {{ getSkillLabel(currentSkill) }}
          <button type="button" class="close-btn" aria-label="关闭技能" @click="removeSkill">
            <i class="fa-solid fa-xmark" />
          </button>
        </span>
      </div>
      <div class="input-wrapper">
        <textarea
          v-model="userInput"
          class="input-field"
          :placeholder="inputPlaceholder"
          :rows="2"
          @keydown.enter.ctrl="sendMessage"
        />
        <div class="input-actions">
          <button type="button" class="input-btn" title="上传附件" @click="handleFileUpload">
            <i class="fa-solid fa-paperclip" />
          </button>
          <button type="button" class="input-btn send-btn" title="发送" @click="sendMessage">
            <i class="fa-solid fa-paper-plane" />
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import MessageItem from './MessageItem.vue'

const props = defineProps({
  mobileHidden: { type: Boolean, default: false }
})

const emit = defineEmits([
  'card-click',
  'remove-skill',
  'file-upload',
  'prepare-product',
  'view-product',
  'skill-change'
])

const messages = ref([])
const userInput = ref('')
const loading = ref(false)
const formId = ref('')
const formSchema = ref(null)
const formData = ref({})
const version = ref(1)
const ws = ref(null)
const messagesContainer = ref(null)
const messagesEnd = ref(null)
const currentSkill = ref('')

// 技能配置
const skillConfig = {
  query: { icon: 'fa-magnifying-glass', label: 'AI智查' },
  file: { icon: 'fa-file-import', label: 'AI方案导入' },
  chat: { icon: 'fa-comments', label: '对话式配置' }
}

const inputPlaceholder = ref('输入您的需求，或使用上方技能...')

const getSkillIcon = (skill) => skillConfig[skill]?.icon || 'fa-wand-magic-sparkles'
const getSkillLabel = (skill) => skillConfig[skill]?.label || skill

const addMessage = (type, text = null, options = {}) => {
  messages.value.push({
    type,
    text,
    ...options
  })
  scrollToBottom()
}

const scrollToBottom = () => {
  nextTick(() => {
    messagesEnd.value?.scrollIntoView({ behavior: 'smooth' })
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

      addMessage('ai', `已为您生成${formSchema.value.formName}，请填写：`, {
        form: formSchema.value
      })
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

// 欢迎卡片点击
const handleCardClick = (type) => {
  currentSkill.value = type
  emit('card-click', type)

  switch (type) {
    case 'query':
      inputPlaceholder.value = '请输入要查询的商品名称...'
      break
    case 'file':
      inputPlaceholder.value = '描述您要导入的文档内容...'
      break
    case 'chat':
      inputPlaceholder.value = '请用自然语言描述您的配置需求...'
      break
  }
}

// 移除技能
const removeSkill = () => {
  currentSkill.value = ''
  inputPlaceholder.value = '输入您的需求，或使用上方技能...'
  emit('remove-skill')
}

// 文件上传
const handleFileUpload = () => {
  emit('file-upload')
}

// 准备产品（复制配置）
const handlePrepareProduct = (index) => {
  emit('prepare-product', index)
}

// 查看产品详情
const handleViewProduct = (index) => {
  emit('view-product', index)
}

onMounted(() => {
  addMessage('ai', '您好！我是产品智能配置助手，可以帮您快速完成商品配置。', { isWelcome: true })
})

onUnmounted(() => {
  if (ws.value) ws.value.close()
})

// 暴露方法给父组件
defineExpose({
  addMessage,
  resetForm,
  currentSkill,
  setQueryResults: (products) => {
    addMessage('ai', '', { isQueryResults: true, products })
  }
})
</script>

<style scoped>
.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  transition: flex var(--transition);
  min-width: 0;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  scroll-behavior: smooth;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  animation: fadeInUp 0.35s ease;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-avatar.ai {
  background: linear-gradient(135deg, var(--primary), var(--el-color-primary-light-3, #66b1ff));
  color: white;
}

.message-avatar.user {
  background: var(--surface-muted, #f5f7fa);
  color: var(--text-secondary, #666);
}

.message-content {
  max-width: 85%;
}

.message-bubble {
  padding: 14px 18px;
  border-radius: 8px;
  line-height: 1.65;
  font-size: 0.9375rem;
}

.message.ai .message-bubble {
  background: var(--ai-bg, #e6f4ff);
  border-top-left-radius: 4px;
}

.message.user .message-bubble {
  background: var(--surface, #fff);
  border: 1px solid var(--border-color, #e5e5e5);
  border-top-right-radius: 4px;
}

.message-text {
  margin-bottom: 12px;
}

.form-bubble {
  min-width: 400px;
}

/* 欢迎卡片 */
.welcome-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .welcome-cards {
    grid-template-columns: 1fr;
  }
}

.welcome-card {
  text-align: left;
  background: var(--surface, #fff);
  border: 1px solid var(--border-color, #e5e5e5);
  border-radius: 8px;
  padding: 16px;
  transition: all 0.2s;
  cursor: pointer;
}

.welcome-card:hover {
  border-color: var(--primary, #25b2fe);
  box-shadow: 0 4px 16px rgba(37, 178, 254, 0.2);
  transform: translateY(-2px);
}

.card-icon {
  width: 42px;
  height: 42px;
  background: var(--primary-light, #e6f4ff);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  color: var(--primary, #25b2fe);
  font-size: 1.125rem;
}

.welcome-card h4 {
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 4px;
}

.welcome-card p {
  font-size: 0.75rem;
  color: var(--text-muted, #999);
}

/* 查询结果 */
.query-results {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.query-card {
  background: var(--surface, #fff);
  border: 1px solid var(--border-color, #e5e5e5);
  border-radius: 8px;
  padding: 14px;
}

.query-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.query-card .name {
  font-weight: 600;
  font-size: 0.875rem;
}

.query-card .tag {
  font-size: 0.6875rem;
  padding: 2px 8px;
  background: #f6ffed;
  color: #52c41a;
  border-radius: 4px;
}

.query-card .desc {
  font-size: 0.8125rem;
  color: var(--text-secondary, #666);
  margin-bottom: 12px;
}

.query-actions {
  display: flex;
  gap: 8px;
}

.btn-primary-soft,
.btn-ghost {
  flex: 1;
  padding: 8px;
  border-radius: 6px;
  font-size: 0.8125rem;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s;
  cursor: pointer;
}

.btn-primary-soft {
  background: var(--primary-light, #e6f4ff);
  color: var(--primary, #25b2fe);
}

.btn-primary-soft:hover {
  background: var(--primary, #25b2fe);
  color: white;
}

.btn-ghost {
  background: var(--surface, #fff);
  border: 1px solid var(--border-color, #e5e5e5);
  color: var(--text-secondary, #666);
}

.btn-ghost:hover {
  border-color: var(--primary, #25b2fe);
  color: var(--primary, #25b2fe);
}

/* 输入区域 */
.input-area {
  padding: 16px 20px 20px;
  border-top: 1px solid var(--border-color, #e5e5e5);
  background: var(--surface-elevated, #fafafa);
}

.skill-tags {
  margin-bottom: 10px;
}

.skill-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--primary-light, #e6f4ff);
  border: 1px solid var(--primary, #25b2fe);
  border-radius: 999px;
  font-size: 0.8125rem;
  color: var(--primary, #25b2fe);
}

.close-btn {
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: inherit;
  cursor: pointer;
}

.close-btn:hover {
  background: var(--primary, #25b2fe);
  color: white;
}

.input-wrapper {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.input-field {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid var(--border-color, #e5e5e5);
  border-radius: 8px;
  font-size: 0.9375rem;
  transition: all 0.2s;
  font-family: inherit;
  resize: none;
  min-height: 46px;
}

.input-field:focus {
  border-color: var(--primary, #25b2fe);
  box-shadow: 0 0 0 3px rgba(37, 178, 254, 0.2);
  outline: none;
}

.input-actions {
  display: flex;
  gap: 8px;
}

.input-btn {
  width: 46px;
  height: 46px;
  border: 1px solid var(--border-color, #e5e5e5);
  background: var(--surface, #fff);
  border-radius: 8px;
  color: var(--text-secondary, #666);
  transition: all 0.2s;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.input-btn:hover {
  border-color: var(--primary, #25b2fe);
  color: var(--primary, #25b2fe);
}

.send-btn {
  background: var(--primary, #25b2fe);
  border-color: var(--primary, #25b2fe);
  color: white;
}

.send-btn:hover {
  background: var(--primary-dark, #1e8fd6);
  border-color: var(--primary-dark, #1e8fd6);
}

/* 动画 */
@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 移动端适配 */
@media (max-width: 767px) {
  .chat-container.mobile-hidden {
    display: none !important;
  }

  .messages {
    padding: 14px 12px;
  }

  .message-content {
    max-width: 92%;
  }

  .welcome-cards {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .welcome-card {
    min-height: 44px;
  }

  .query-actions {
    flex-direction: column;
  }

  .form-bubble {
    min-width: unset;
    width: 100%;
  }

  .input-area {
    padding: 12px 12px calc(12px + env(safe-area-inset-bottom, 0px));
  }

  .input-field {
    font-size: 16px;
    min-height: 44px;
  }

  .input-btn {
    width: 44px;
    height: 44px;
    flex-shrink: 0;
  }
}
</style>
