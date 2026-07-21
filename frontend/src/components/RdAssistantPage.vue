<template>
  <div class="assistant-page assistant-workbench">
    <AssistantNavBar mode="rd" title="研发配置与批量生成" />

    <div class="workbench-body">
      <aside class="workbench-side">
        <div class="side-section">
          <div class="side-title">快捷场景</div>
          <button
            v-for="item in sceneShortcuts"
            :key="item.label"
            class="side-btn"
            @click="sendMessage({ text: item.text, scene: item.scene })"
          >
            <span class="btn-label">{{ item.label }}</span>
            <span class="btn-scene">{{ item.scene }}</span>
          </button>
        </div>
        <div class="side-section side-tips">
          <div class="side-title">使用提示</div>
          <p class="tip-text">点击左侧按钮可快速发起场景对话。</p>
          <p class="tip-text">在对话中可随时追问，AI 会基于上下文继续回答。</p>
        </div>
      </aside>

      <main class="workbench-main">
        <ChatMessageList
          :messages="messages"
          :showWelcome="messages.length === 0"
          @suggest="onSuggest"
          @intent-action="onIntentAction"
        />

        <div class="workbench-input">
          <ChatInput
            :modelValue="inputText"
            :disabled="streaming"
            placeholder="描述你的研发配置需求，例如：给家庭用户做 500M 融合套餐"
            assistantMode="rd"
            @update:modelValue="inputText = $event"
            @send="onSend"
          />
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import AssistantNavBar from './AssistantNavBar.vue'
import ChatMessageList from './ChatMessageList.vue'
import ChatInput from './ChatInput.vue'
import { useChatStream } from '../composables/useChatStream.js'

const inputText = ref('')
const { messages, streaming, sendMessage } = useChatStream()

const sceneShortcuts = [
  { label: '对话配置', scene: 'rd.chat', text: '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售' },
  { label: '批量生成', scene: 'rd.import', text: '帮我导入校园迎新方案' },
  { label: 'AI智查', scene: 'market_insight', text: '查一下近30天大学生套餐配置' },
  { label: '合规校验', scene: 'online_check', text: '校验当前配置是否符合在架规则' },
]

const onSend = (payload) => {
  const text = payload?.text || inputText.value
  if (!text) return
  sendMessage({ text, scene: payload?.scene || 'rd' })
  inputText.value = ''
}

const onSuggest = (text) => {
  sendMessage({ text, scene: 'rd' })
}

const onIntentAction = (event) => {
  if (event.action === 'follow_up' && event.payload?.text) {
    sendMessage({ text: event.payload.text, scene: 'rd' })
  } else if (event.action === 'export' && event.payload) {
    exportConclusion(event.payload)
  }
}

const exportConclusion = (payload) => {
  const lines = []
  lines.push(`意图类型：${payload.intentType}`)
  if (payload.stats) {
    Object.entries(payload.stats).forEach(([k, v]) => {
      lines.push(`${k}：${typeof v === 'object' ? JSON.stringify(v) : v}`)
    })
  }
  if (payload.streamText) {
    lines.push('')
    lines.push('--- AI 回答 ---')
    lines.push(payload.streamText)
  }
  const text = lines.join('\n')
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('结论已复制到剪贴板')
  }).catch(() => {
    ElMessage.warning('复制失败，请手动选择文本')
  })
}
</script>

<style scoped>
.assistant-workbench { display: flex; flex-direction: column; height: 100%; }
.workbench-body { display: grid; grid-template-columns: 240px 1fr; flex: 1; min-height: 0; overflow: hidden; }
.workbench-side { border-right: 1px solid #e5e7eb; padding: 16px; overflow-y: auto; background: #fafafa; display: flex; flex-direction: column; gap: 20px; }
.side-section { display: flex; flex-direction: column; gap: 10px; }
.side-title { font-weight: 700; color: #334155; font-size: 13px; }
.side-btn { border: 1px solid #e2e8f0; background: #fff; padding: 10px 12px; border-radius: 12px; text-align: left; cursor: pointer; display: flex; flex-direction: column; gap: 4px; transition: border-color 0.15s; }
.side-btn:hover { border-color: #93c5fd; background: #f0f9ff; }
.btn-label { font-weight: 600; color: #0f172a; font-size: 13px; }
.btn-scene { font-size: 11px; color: #64748b; font-family: monospace; }
.side-tips { border-top: 1px solid #e5e7eb; padding-top: 14px; }
.tip-text { font-size: 12px; color: #94a3b8; line-height: 1.6; }
.workbench-main { display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.workbench-input { padding: 16px; border-top: 1px solid #e5e7eb; background: #fff; flex-shrink: 0; }
</style>
