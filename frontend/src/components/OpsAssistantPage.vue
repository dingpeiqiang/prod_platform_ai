<template>
  <AssistantShell
    mode="ops"
    :streaming="streaming"
    v-model:inputText="inputText"
    :sessions="sessionList"
    :sessionsLoading="historyLoading"
    @send="onSend"
    @stop="stop"
    @new-session="onNewSession"
    @refresh-sessions="loadSessions"
    @switch-session="onSwitchSession"
    @shortcut="onShortcut"
  >
    <ChatMessageList
      mode="ops"
      :messages="messages"
      :showWelcome="messages.length === 0"
      @suggest="onSuggest"
      @intent-action="onIntentAction"
    />
  </AssistantShell>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import AssistantShell from './AssistantShell.vue'
import ChatMessageList from './ChatMessageList.vue'
import { useChatStream } from '../composables/useChatStream.js'
import { assistantModes } from '../config/assistantModes.js'

const inputText = ref('')
const historyLoading = ref(false)
const { messages, streaming, sendMessage, loadSessions, switchSession, newSession, sessionList, stop } = useChatStream()

const config = assistantModes.ops

onMounted(async () => {
  historyLoading.value = true
  try { await loadSessions() } finally { historyLoading.value = false }
})

const onSend = (payload) => {
  const text = payload?.text || inputText.value
  if (!text) return
  sendMessage({ text, scene: payload?.scene || config.defaultScene })
  inputText.value = ''
}

const onSuggest = (text) => {
  sendMessage({ text, scene: config.defaultScene })
}

const onShortcut = (item) => {
  sendMessage({ text: item.text, scene: item.scene })
}

const onSwitchSession = (sessionId) => {
  switchSession(sessionId)
}

const onNewSession = () => {
  newSession()
}

const onIntentAction = (event) => {
  if (event.action === 'follow_up' && event.payload?.text) {
    sendMessage({ text: event.payload.text, scene: config.defaultScene })
  }
}
</script>