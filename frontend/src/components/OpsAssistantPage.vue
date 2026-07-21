<template>
  <div class="assistant-page assistant-workbench">
    <AssistantNavBar mode="ops" title="洞察、研判、归因、稽核" />

    <div class="workbench-body">
      <aside class="workbench-side">
        <div class="side-section">
          <div class="side-title">运营场景</div>
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
          <p class="tip-text">市场洞察：查询在售商品、增长指标、风险商品。</p>
          <p class="tip-text">立项研判：评估新品是否满足上市门槛。</p>
          <p class="tip-text">异动归因：追溯收入下滑或指标异常的根因。</p>
          <p class="tip-text">风险稽核：筛查零资费、低效等风险商品。</p>
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
            placeholder="描述你想做的运营分析，例如：筛查所有零资费风险商品"
            assistantMode="ops"
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
  { label: '市场洞察', scene: 'market_insight', text: '查一下在售5G套餐和风险商品' },
  { label: '立项研判', scene: 'online_check', text: '判断这个新品能不能立项' },
  { label: '异动归因', scene: 'root_cause', text: '分析家庭融合畅享128本月收入下滑原因' },
  { label: '风险稽核', scene: 'risk_audit', text: '筛查所有在架的0元资费风险商品' },
]

const onSend = (payload) => {
  const text = payload?.text || inputText.value
  if (!text) return
  sendMessage({ text, scene: payload?.scene || 'ops' })
  inputText.value = ''
}

const onSuggest = (text) => {
  sendMessage({ text, scene: 'ops' })
}

const onIntentAction = (event) => {
  if (event.action === 'follow_up' && event.payload?.text) {
    sendMessage({ text: event.payload.text, scene: 'ops' })
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
.tip-text { font-size: 12px; color: #94a3b8; line-height: 1.6; margin: 0; }
.workbench-main { display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.workbench-input { padding: 16px; border-top: 1px solid #e5e7eb; background: #fff; flex-shrink: 0; }
</style>
