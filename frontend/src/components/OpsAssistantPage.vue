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
      </aside>

      <main class="workbench-main">
        <ChatMessageList :messages="messages" />

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
</script>

<style scoped>
.assistant-workbench { display: flex; flex-direction: column; height: 100%; }
.workbench-body { display: grid; grid-template-columns: 240px 1fr; flex: 1; min-height: 0; overflow: hidden; }
.workbench-side { border-right: 1px solid #e5e7eb; padding: 16px; overflow-y: auto; background: #fafafa; }
.side-section { display: flex; flex-direction: column; gap: 10px; }
.side-title { font-weight: 700; color: #334155; }
.side-btn { border: 1px solid #e2e8f0; background: #fff; padding: 10px 12px; border-radius: 12px; text-align: left; cursor: pointer; display: flex; flex-direction: column; gap: 4px; }
.btn-label { font-weight: 600; color: #0f172a; }
.btn-scene { font-size: 12px; color: #64748b; }
.workbench-main { display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.workbench-input { padding: 16px; border-top: 1px solid #e5e7eb; background: #fff; flex-shrink: 0; }
</style>
