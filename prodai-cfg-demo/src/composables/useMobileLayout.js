import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

const MOBILE_BP = 768

export function useMobileLayout(mode) {
  const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
  const mobilePane = ref('chat')

  const isMobile = computed(() => windowWidth.value < MOBILE_BP)

  function updateWidth() {
    windowWidth.value = window.innerWidth
  }

  onMounted(() => {
    updateWidth()
    window.addEventListener('resize', updateWidth, { passive: true })
    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', updateWidth)
    }
  })

  onUnmounted(() => {
    window.removeEventListener('resize', updateWidth)
    if (window.visualViewport) {
      window.visualViewport.removeEventListener('resize', updateWidth)
    }
  })

  watch(
    () => mode.value,
    (m) => {
      if (m === 'split' && isMobile.value) {
        mobilePane.value = 'config'
      }
      if (m === 'full') {
        mobilePane.value = 'chat'
      }
    },
  )

  function showChatOnMobile() {
    mobilePane.value = 'chat'
  }

  function showConfigOnMobile() {
    if (mode.value === 'split') mobilePane.value = 'config'
  }

  return {
    isMobile,
    mobilePane,
    showChatOnMobile,
    showConfigOnMobile,
  }
}