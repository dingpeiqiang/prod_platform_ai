<template>
  <div class="app-shell">
    <Loading :visible="isLoading" :text="loadingText" />
    <transition name="slide-down">
      <div v-if="!isOnline" class="network-banner offline">网络已断开 <button @click="checkNetwork">重连</button></div>
      <div v-else-if="!isBackendOnline" class="network-banner warning">服务器连接异常，正在重连...</div>
    </transition>
    <RouterView />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { RouterView } from 'vue-router'
import { storeToRefs } from 'pinia'
import Loading from './components/Loading.vue'
import { useLoadingStore } from './stores/loading'

const loadingStore = useLoadingStore()
const { isLoading, loadingText } = storeToRefs(loadingStore)
const isOnline = ref(navigator.onLine)
const isBackendOnline = ref(true)
let timer = null

const checkBackendOnline = async () => {
  try {
    const response = await fetch('/api/v1/health', { method: 'HEAD' })
    isBackendOnline.value = response.ok
  } catch {
    isBackendOnline.value = false
  }
}
const checkNetwork = () => {
  isOnline.value = navigator.onLine
  if (isOnline.value) checkBackendOnline()
}

onMounted(() => {
  window.addEventListener('online', checkNetwork)
  window.addEventListener('offline', checkNetwork)
  checkBackendOnline()
  timer = setInterval(checkBackendOnline, 30000)
})
onUnmounted(() => {
  window.removeEventListener('online', checkNetwork)
  window.removeEventListener('offline', checkNetwork)
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.app-shell {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}
</style>
