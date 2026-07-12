<script setup>
import { computed, ref, watch, nextTick } from 'vue'

const props = defineProps({
  mode: String,
  messages: Array,
  chatInput: String,
  currentSkill: String,
  skillConfig: Object,
  inputPlaceholder: String,
  mobileHidden: { type: Boolean, default: false },
})

const messagesEnd = ref(null)

watch(
  () => props.messages.length,
  () => {
    nextTick(() => messagesEnd.value?.scrollIntoView({ behavior: 'smooth' }))
  },
)

const emit = defineEmits([
  'update:chatInput',
  'card-click',
  'remove-skill',
  'send',
  'file-upload',
  'prepare-product',
  'view-product',
])

const skillTag = computed(() =>
  props.currentSkill && props.skillConfig[props.currentSkill]
    ? props.skillConfig[props.currentSkill]
    : null,
)

function onKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    emit('send')
  }
}
</script>

<template>
  <section class="chat-container" :class="{ split: mode === 'split', 'mobile-hidden': mobileHidden }">
    <div class="messages" role="log" aria-live="polite">
      <div
        v-for="msg in messages"
        :key="msg.id"
        class="message"
        :class="msg.role"
      >
        <div class="message-avatar" :class="msg.role">
          <i
            :class="
              msg.role === 'ai'
                ? 'fa-solid fa-head-side-conceptual'
                : 'fa-solid fa-user'
            "
          />
        </div>
        <div class="message-content">
          <div v-if="msg.welcome" class="message-bubble">
            <p>您好！我是产品智能配置助手，可以帮您快速完成商品配置。</p>
            <div class="welcome-cards">
              <button type="button" class="welcome-card" @click="emit('card-click', 'query')">
                <div class="card-icon"><i class="fa-solid fa-magnifying-glass" /></div>
                <h4>AI智查</h4>
                <p>查询历史商品，快速复制配置</p>
              </button>
              <button type="button" class="welcome-card" @click="emit('card-click', 'file')">
                <div class="card-icon"><i class="fa-solid fa-file-import" /></div>
                <h4>AI方案导入</h4>
                <p>上传文档，批量导入配置</p>
              </button>
              <button type="button" class="welcome-card" @click="emit('card-click', 'chat')">
                <div class="card-icon"><i class="fa-solid fa-comments" /></div>
                <h4>对话式配置</h4>
                <p>自然语言描述，智能生成配置</p>
              </button>
            </div>
          </div>
          <div v-else-if="msg.type === 'query-results'" class="message-bubble">
            <div class="query-results">
              <div v-for="(p, i) in msg.products" :key="p.id" class="query-card">
                <div class="query-card-head">
                  <span class="name">{{ p.name }}</span>
                  <span class="tag">已提交</span>
                </div>
                <p class="desc">{{ p.desc }}</p>
                <div class="query-actions">
                  <button type="button" class="btn-primary-soft" @click="emit('prepare-product', i)">
                    <i class="fa-solid fa-copy" /> 复制配置
                  </button>
                  <button type="button" class="btn-ghost" @click="emit('view-product', i)">
                    <i class="fa-solid fa-eye" /> 查看详情
                  </button>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="message-bubble" v-html="msg.content" />
        </div>
      </div>
      <div ref="messagesEnd" />
    </div>

    <div class="input-area">
      <div v-if="skillTag" class="skill-tags">
        <span class="skill-tag">
          <i :class="`fa-solid ${skillTag.icon}`" />
          {{ skillTag.label }}
          <button type="button" class="close-btn" aria-label="关闭技能" @click="emit('remove-skill')">
            <i class="fa-solid fa-xmark" />
          </button>
        </span>
      </div>
      <div class="input-wrapper">
        <input
          :value="chatInput"
          type="text"
          class="input-field"
          :placeholder="inputPlaceholder"
          @input="emit('update:chatInput', $event.target.value)"
          @keydown="onKeydown"
        />
        <div class="input-actions">
          <button type="button" class="input-btn" title="上传附件" @click="emit('file-upload')">
            <i class="fa-solid fa-paperclip" />
          </button>
          <button type="button" class="input-btn send-btn" title="发送" @click="emit('send')">
            <i class="fa-solid fa-paper-plane" />
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  transition: flex var(--transition);
  min-width: 0;
}

.chat-container.split {
  flex: 0.4;
  min-width: 300px;
  border-right: 1px solid var(--border-color);
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  scroll-behavior: smooth;
}

.message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  animation: fadeInUp 0.35s ease;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-avatar.ai {
  background: linear-gradient(135deg, var(--primary), var(--accent));
  color: white;
}

.message-avatar.user {
  background: var(--surface-muted);
  color: var(--text-secondary);
}

.message-content {
  max-width: 85%;
}

.message-bubble {
  padding: 14px 18px;
  border-radius: var(--radius-lg);
  line-height: 1.65;
  font-size: 0.9375rem;
}

.message.ai .message-bubble {
  background: var(--ai-bg);
  border-top-left-radius: 4px;
}

.message.user .message-bubble {
  background: var(--surface);
  border: 1px solid var(--border);
  border-top-right-radius: 4px;
}

.message-bubble :deep(.msg-hint) {
  font-size: 0.8125rem;
  color: var(--text-secondary);
}

.welcome-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .welcome-cards {
    grid-template-columns: 1fr;
  }
}

.welcome-card {
  text-align: left;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 16px;
  transition: var(--transition);
}

.welcome-card:hover {
  border-color: var(--primary);
  box-shadow: 0 4px 16px var(--primary-glow);
  transform: translateY(-2px);
}

.card-icon {
  width: 42px;
  height: 42px;
  background: var(--primary-muted);
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  color: var(--primary);
  font-size: 1.125rem;
}

.welcome-card h4 {
  font-size: 0.875rem;
  font-weight: 600;
  margin-bottom: 4px;
}

.welcome-card p {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.query-results {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.query-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 14px;
}

.query-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.query-card .name {
  font-weight: 600;
  font-size: 0.875rem;
}

.query-card .tag {
  font-size: 0.6875rem;
  padding: 2px 8px;
  background: var(--success-bg);
  color: var(--success);
  border-radius: 4px;
}

.query-card .desc {
  font-size: 0.8125rem;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.query-actions {
  display: flex;
  gap: 8px;
}

.btn-primary-soft,
.btn-ghost {
  flex: 1;
  padding: 8px;
  border-radius: var(--radius-sm);
  font-size: 0.8125rem;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: var(--transition);
}

.btn-primary-soft {
  background: var(--primary-muted);
  color: var(--primary);
}

.btn-primary-soft:hover {
  background: var(--primary);
  color: white;
}

.btn-ghost {
  background: var(--surface);
  border: 1px solid var(--border);
  color: var(--text-secondary);
}

.btn-ghost:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.input-area {
  padding: 16px 20px 20px;
  border-top: 1px solid var(--border);
  background: var(--surface-elevated);
}

.skill-tags {
  margin-bottom: 10px;
}

.skill-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: var(--primary-muted);
  border: 1px solid var(--primary);
  border-radius: 999px;
  font-size: 0.8125rem;
  color: var(--primary);
}

.close-btn {
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: inherit;
}

.close-btn:hover {
  background: var(--primary);
  color: white;
}

.input-wrapper {
  display: flex;
  gap: 10px;
  align-items: center;
}

.input-field {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  font-size: 0.9375rem;
  transition: var(--transition);
}

.input-field:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-glow);
}

.input-actions {
  display: flex;
  gap: 8px;
}

.input-btn {
  width: 46px;
  height: 46px;
  border: 1px solid var(--border);
  background: var(--surface);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  transition: var(--transition);
}

.input-btn:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.send-btn {
  background: var(--primary);
  border-color: var(--primary);
  color: white;
}

.send-btn:hover {
  background: var(--primary-hover);
}

@media (max-width: 767px) {
  .chat-container.split {
    flex: 1;
    min-width: 0;
    border-right: none;
  }

  .chat-container.mobile-hidden {
    display: none !important;
  }

  .messages {
    padding: 14px 12px;
  }

  .message-content {
    max-width: 92%;
  }

  .welcome-cards {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .welcome-card {
    min-height: 44px;
  }

  .query-actions {
    flex-direction: column;
  }

  .input-area {
    padding: 12px 12px calc(12px + env(safe-area-inset-bottom, 0px));
  }

  .input-field {
    font-size: 16px;
    min-height: 44px;
  }

  .input-btn {
    width: 44px;
    height: 44px;
    flex-shrink: 0;
  }
}
</style>