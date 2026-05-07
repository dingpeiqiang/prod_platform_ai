<template>
  <div class="login-screen">
    <!-- 背景动效 -->
    <div class="bg-gradient"></div>
    <div class="bg-grid"></div>

    <!-- 登录卡片 -->
    <div class="login-card">
      <!-- Logo -->
      <div class="login-logo">
        <div class="logo-icon">
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
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
        <h1 class="logo-title">数智赋能</h1>
        <p class="logo-subtitle">智能表单填写系统</p>
      </div>

      <!-- 表单 -->
      <div class="login-form">
        <div class="input-group">
          <label class="input-label">用户名</label>
          <div class="input-wrapper">
            <svg class="input-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="8" r="4"/>
              <path d="M20 21a8 8 0 1 0-16 0"/>
            </svg>
            <input
              ref="inputRef"
              v-model="inputName"
              class="login-input"
              :class="{ error: errorMsg }"
              placeholder="请输入用户名"
              maxlength="20"
              @keyup.enter="handleLogin"
            />
          </div>
          <div class="error-tip" v-if="errorMsg">{{ errorMsg }}</div>
        </div>

        <button class="login-btn" @click="handleLogin" :disabled="loading">
          <span v-if="!loading">登 录</span>
          <span v-else class="loading-dots">
            <span></span><span></span><span></span>
          </span>
        </button>
      </div>

      <p class="login-tip">模拟登录，输入任意用户名即可进入</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '../stores/user'

const userStore = useUserStore()
const inputName = ref('')
const errorMsg = ref('')
const loading = ref(false)
const inputRef = ref(null)

onMounted(() => {
  inputRef.value?.focus()
})

const handleLogin = async () => {
  errorMsg.value = ''
  const name = inputName.value.trim()
  if (!name) {
    errorMsg.value = '请输入用户名'
    inputRef.value?.focus()
    return
  }
  loading.value = true
  // 模拟网络延迟
  await new Promise(r => setTimeout(r, 600))
  loading.value = false
  const result = userStore.login(name)
  if (!result.success) {
    errorMsg.value = result.message
  }
}
</script>

<style scoped>
.login-screen {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-gray-950);
  z-index: var(--z-modal);
  overflow: hidden;
}

/* 背景渐变 */
.bg-gradient {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 60% at 50% -10%, rgba(99,102,241,0.18) 0%, transparent 70%),
    radial-gradient(ellipse 60% 40% at 80% 110%, rgba(139,92,246,0.12) 0%, transparent 60%);
  pointer-events: none;
}

/* 网格 */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.025) 1px, transparent 1px);
  background-size: 48px 48px;
  pointer-events: none;
  mask-image: radial-gradient(ellipse 80% 70% at 50% 50%, black 40%, transparent 100%);
}

/* 卡片 */
.login-card {
  position: relative;
  width: 380px;
  max-width: calc(100vw - 32px);
  background: rgba(255,255,255,0.035);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: var(--radius-2xl);
  padding: var(--space-11) var(--space-9) var(--space-9);
  backdrop-filter: blur(20px);
  box-shadow:
    0 0 0 1px rgba(255,255,255,0.05) inset,
    0 24px 80px rgba(0,0,0,0.5),
    0 0 60px rgba(99,102,241,0.08);
  animation: cardIn 0.4s cubic-bezier(.16,1,.3,1) both;
}

@keyframes cardIn {
  from { opacity: 0; transform: translateY(24px) scale(0.96); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}

/* Logo */
.login-logo {
  text-align: center;
  margin-bottom: var(--space-9);
}

.logo-icon {
  width: 56px;
  height: 56px;
  background: linear-gradient(135deg, var(--color-primary-400), var(--color-primary-500));
  border-radius: var(--radius-xl);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-inverse);
  margin: 0 auto var(--space-4);
  box-shadow: 0 8px 32px rgba(99,102,241,0.35);
}

.logo-title {
  font-size: var(--font-size-2xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-gray-100);
  letter-spacing: 1px;
  margin-bottom: var(--space-1-5);
}

.logo-subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-gray-500);
}

/* 输入框 */
.login-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.input-label {
  font-size: var(--font-size-sm);
  color: var(--color-gray-400);
  font-weight: var(--font-weight-medium);
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 14px;
  color: var(--color-gray-500);
  pointer-events: none;
  z-index: 1;
}

.login-input {
  width: 100%;
  padding: 12px 14px 12px 42px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: var(--radius-md);
  color: var(--color-gray-100);
  font-size: var(--font-size-base);
  outline: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.login-input::placeholder { color: var(--text-tertiary); }
.login-input:focus {
  border-color: rgba(99,102,241,0.6);
  box-shadow: 0 0 0 3px rgba(99,102,241,0.12);
}
.login-input.error {
  border-color: rgba(239,68,68,0.5);
  box-shadow: 0 0 0 3px rgba(239,68,68,0.1);
}

.error-tip {
  font-size: var(--font-size-xs);
  color: var(--color-error-500);
  padding-left: 2px;
}

/* 登录按钮 */
.login-btn {
  width: 100%;
  padding: 13px;
  background: linear-gradient(135deg, var(--color-primary-500), var(--color-primary-600));
  border: none;
  border-radius: var(--radius-md);
  color: var(--text-inverse);
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  cursor: pointer;
  transition: opacity var(--transition-fast), transform 0.15s, box-shadow var(--transition-fast);
  box-shadow: 0 4px 20px rgba(99,102,241,0.35);
  letter-spacing: 3px;
  margin-top: var(--space-1);
}
.login-btn:hover:not(:disabled) {
  opacity: 0.92;
  transform: translateY(-1px);
  box-shadow: 0 8px 28px rgba(99,102,241,0.45);
}
.login-btn:active:not(:disabled) {
  transform: translateY(0);
}
.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* loading */
.loading-dots {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}
.loading-dots span {
  width: 6px; height: 6px;
  background: var(--text-inverse);
  border-radius: 50%;
  animation: dotBounce 1.2s ease-in-out infinite;
}
.loading-dots span:nth-child(2) { animation-delay: .2s; }
.loading-dots span:nth-child(3) { animation-delay: .4s; }

@keyframes dotBounce {
   0%, 80%, 100% { transform: scale(0.7); opacity: .5; }
   40% { transform: scale(1); opacity: 1; }
}

/* 底部提示 */
.login-tip {
  text-align: center;
  font-size: var(--font-size-xs);
  color: var(--color-gray-600);
  margin-top: var(--space-5);
}

/* 暗色主题适配 */
[data-theme="dark"] .login-screen {
  background: var(--color-gray-900);
}
</style>