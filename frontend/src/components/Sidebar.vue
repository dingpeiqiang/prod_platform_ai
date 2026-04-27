<template>
  <div class="sidebar">
    <div class="sidebar-logo">
      <div class="logo-icon">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/>
        </svg>
      </div>
      <span class="logo-text">AI 助手</span>
    </div>

    <button class="new-chat-btn" @click="$emit('new-session')">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
        <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
      </svg>
      新建对话
    </button>

    <div class="session-list">
      <div class="session-group-label" v-if="todaySessions.length">今天</div>
      <div
        v-for="s in todaySessions"
        :key="s.id"
        :class="['session-item', { active: s.id === activeId }]"
        @click="$emit('switch-session', s.id)"
      >
        <svg class="session-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span class="session-title">{{ s.title }}</span>
        <button class="session-delete" @click.stop="$emit('delete-session', s.id)" title="删除对话">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>

      <div class="session-group-label" v-if="olderSessions.length">更早</div>
      <div
        v-for="s in olderSessions"
        :key="s.id"
        :class="['session-item', { active: s.id === activeId }]"
        @click="$emit('switch-session', s.id)"
      >
        <svg class="session-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <span class="session-title">{{ s.title }}</span>
        <button class="session-delete" @click.stop="$emit('delete-session', s.id)" title="删除对话">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>
      </div>

      <div class="empty-tip" v-if="!sessions.length">暂无历史对话</div>
    </div>

    <div class="sidebar-footer">
      <div class="footer-item">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="12" cy="8" r="4"/><path d="M20 21a8 8 0 1 0-16 0"/>
        </svg>
        <span>用户</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  sessions: { type: Array, default: () => [] },
  activeId: { type: String, default: '' }
})

defineEmits(['new-session', 'switch-session', 'delete-session'])

const today = new Date().toDateString()

const todaySessions = computed(() =>
  props.sessions.filter(s => new Date(s.updatedAt).toDateString() === today)
)
const olderSessions = computed(() =>
  props.sessions.filter(s => new Date(s.updatedAt).toDateString() !== today)
)
</script>

<style scoped>
.sidebar {
  width: 240px;
  min-width: 240px;
  height: 100vh;
  height: 100dvh;
  background: #171717;
  display: flex;
  flex-direction: column;
  padding: 16px 12px;
  overflow: hidden;
}

@media (max-width: 768px) {
  .sidebar {
    width: 280px;
    min-width: 280px;
    box-shadow: 4px 0 32px rgba(0,0,0,.4);
    padding: 16px 14px;
  }
}

.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px 20px;
}
.logo-icon {
  width: 30px; height: 30px;
  background: linear-gradient(135deg, #818cf8, #6366f1);
  border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.logo-text {
  color: #e5e7eb;
  font-size: 14.5px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 14px;
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 10px;
  color: #d1d5db;
  font-size: 13.5px;
  cursor: pointer;
  transition: background .15s, border-color .15s;
  margin-bottom: 14px;
}
.new-chat-btn:hover {
  background: rgba(255,255,255,0.1);
  border-color: rgba(255,255,255,0.15);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
.session-list::-webkit-scrollbar { width: 3px; }
.session-list::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.1); border-radius: 2px; }

.session-group-label {
  font-size: 11px;
  color: #6b7280;
  padding: 10px 12px 5px;
  letter-spacing: 0.5px;
  font-weight: 500;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: #9ca3af;
  font-size: 13px;
  position: relative;
  transition: background .12s, color .12s;
  margin-bottom: 1px;
}
.session-item:hover {
  background: rgba(255,255,255,0.06);
  color: #e5e7eb;
}
.session-item.active {
  background: rgba(99,102,241,0.2);
  color: #e5e7eb;
}

.session-icon { flex-shrink: 0; opacity: 0.5; }
.session-item.active .session-icon { opacity: 0.8; }

.session-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}

.session-delete {
  display: none;
  background: none;
  border: none;
  padding: 3px;
  cursor: pointer;
  color: #6b7280;
  border-radius: 4px;
  flex-shrink: 0;
  line-height: 1;
}
.session-delete:hover { color: #f87171; background: rgba(248,113,113,0.1); }
.session-item:hover .session-delete { display: flex; }

.empty-tip {
  text-align: center;
  color: #4b5563;
  font-size: 12.5px;
  padding: 32px 0;
}

.sidebar-footer {
  border-top: 1px solid rgba(255,255,255,0.06);
  padding-top: 10px;
  margin-top: 8px;
}
.footer-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  color: #6b7280;
  font-size: 13px;
  border-radius: 8px;
  cursor: pointer;
  transition: background .12s, color .12s;
}
.footer-item:hover { background: rgba(255,255,255,0.06); color: #9ca3af; }
</style>
