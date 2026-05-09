<template>
  <div :class="['message', message.type]">
    <div class="message-avatar">
      <el-icon v-if="message.type === 'ai'"><ChatDotRound /></el-icon>
      <el-icon v-else><User /></el-icon>
    </div>
    <div class="message-content">
      <div v-if="message.text" class="message-text" v-html="renderedText"></div>
      <div v-if="message.form" class="message-form">
        <DynamicForm 
          :schema="message.form" 
          :form-data="formData"
          :version="version"
          @field-change="$emit('field-change', $event)"
          @form-submit="$emit('form-submit', $event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ChatDotRound, User } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DynamicForm from './DynamicForm.vue'

const props = defineProps({
  message: {
    type: Object,
    required: true
  },
  formData: {
    type: Object,
    default: () => ({})
  },
  version: {
    type: Number,
    default: 1
  }
})

defineEmits(['field-change', 'form-submit'])

const renderedText = computed(() => {
  if (!props.message.text) return ''
  
  marked.setOptions({
    breaks: true,
    gfm: true,
    headerIds: false,
    mangle: false
  })
  
  try {
    let html = marked.parse(props.message.text)
    html = html.replace(/<script[^>]*>.*?<\/script>/gi, '')
    html = html.replace(/\son\w+\s*=\s*["'][^"']*["']/gi, '')
    return html
  } catch (e) {
    console.error('Markdown 解析错误:', e)
    return props.message.text
  }
})
</script>

<style scoped>
.message {
  display: flex;
  margin-bottom: 20px;
  gap: 12px;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #409eff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-inverse);
  flex-shrink: 0;
}

.message.user .message-avatar {
  background: #67c23a;
}

.message-content {
  max-width: 70%;
}

.message-text {
  background: var(--bg-elevated);
  padding: 12px 16px;
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
  line-height: 1.6;
}

.message-text :deep(h1), .message-text :deep(h2), .message-text :deep(h3) {
  margin-top: 1em;
  margin-bottom: 0.5em;
}

.message-text :deep(p) {
  margin-bottom: 0.8em;
}

.message-text :deep(ul), .message-text :deep(ol) {
  margin-left: 1.5em;
  margin-bottom: 0.8em;
}

.message-text :deep(code) {
  background-color: var(--bg-tertiary);
  padding: 2px 4px;
  border-radius: 3px;
  font-family: monospace;
}

.message-text :deep(pre) {
  background-color: var(--bg-tertiary);
  padding: 10px;
  border-radius: 5px;
  overflow-x: auto;
}

.message.user .message-text {
  background: var(--color-info-50);
}

.message-form {
  background: var(--bg-elevated);
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}
</style>