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
      </aside>

      <main class="workbench-main">
        <ChatMessageList :messages="messages" />

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
