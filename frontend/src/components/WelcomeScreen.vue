<template>
  <div class="welcome-screen">
    <div class="welcome-logo">
      <div class="welcome-logo-inner">
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10"/>
          <circle cx="12" cy="12" r="6"/>
          <circle cx="12" cy="12" r="2"/>
          <path d="M12 6a6 6 0 0 1 4 1.5"/>
          <path d="M12 6a6 6 0 0 0-4 1.5"/>
          <path d="M12 18a6 6 0 0 1 4-1.5"/>
          <path d="M12 18a6 6 0 0 0-4-1.5"/>
          <path d="M6 12a6 6 0 0 1 1.5 4"/>
          <path d="M6 12a6 6 0 0 0 1.5-4"/>
          <path d="M18 12a6 6 0 0 1-1.5 4"/>
          <path d="M18 12a6 6 0 0 0-1.5-4"/>
          <circle cx="7.5" cy="8.5" r="1"/>
          <circle cx="16.5" cy="8.5" r="1"/>
          <circle cx="7.5" cy="15.5" r="1"/>
          <circle cx="16.5" cy="15.5" r="1"/>
        </svg>
      </div>
    </div>
    <h2 class="welcome-title">有什么可以帮你的？</h2>
    <p class="welcome-subtitle">产商品研发助手，助力高效填写业务表单</p>
    <div class="welcome-suggestions">
      <button
        v-for="s in suggestions"
        :key="s.key"
        class="suggestion-card"
        @click="$emit('click', s.text)"
      >
        <span class="suggestion-icon">{{ s.icon }}</span>
        <span class="suggestion-label">{{ s.text }}</span>
        <svg class="suggestion-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  suggestions: {
    type: Array,
    default: () => [
      { key: 'config', icon: '🛠️', text: '我想添加一种新表单' },
      { key: 'help', icon: '💬', text: '你能做什么？' },
    ]
  }
})

defineEmits(['click'])
</script>

<style scoped>
.welcome-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 14vh;
  gap: var(--space-3-5);
}

.welcome-logo { margin-bottom: var(--space-2); }
.welcome-logo-inner {
  width: 64px; height: 64px;
  background: linear-gradient(135deg, var(--color-primary-400), var(--color-primary-500));
  border-radius: var(--radius-xl);
  display: flex; align-items: center; justify-content: center;
  color: var(--text-inverse);
  box-shadow: 0 8px 24px rgba(99,102,241,.25);
}

.welcome-title {
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.welcome-subtitle {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-top: -6px;
}

.welcome-suggestions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-2-5);
  max-width: 520px;
  padding: 0 var(--space-5);
  margin-top: var(--space-4-5);
}

.suggestion-card {
  display: flex;
  align-items: center;
  gap: var(--space-2-5);
  padding: var(--space-3-5) var(--space-4);
  background: var(--bg-primary);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  cursor: pointer;
  transition: all var(--transition-fast);
  text-align: left;
}

.suggestion-card:hover {
  border-color: var(--color-primary-200);
  box-shadow: 0 2px 12px rgba(99,102,241,.1);
  transform: translateY(-1px);
}

.suggestion-icon { font-size: 18px; flex-shrink: 0; }
.suggestion-label { flex: 1; line-height: 1.4; }

.suggestion-arrow {
  color: var(--border-strong);
  flex-shrink: 0;
  transition: color var(--transition-fast), transform var(--transition-fast);
}

.suggestion-card:hover .suggestion-arrow {
  color: var(--color-primary-400);
  transform: translateX(2px);
}
</style>