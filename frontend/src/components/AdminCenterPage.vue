<template>
  <div class="admin-center">
    <AdminNavBar title="后台管理中心" />

    <main class="admin-main">
      <div class="admin-hero">
        <h2 class="admin-hero-title">后台管理中心</h2>
        <p class="admin-hero-desc">集中管理平台级能力：工作流编排、模型接入等配置入口。</p>
      </div>

      <div class="admin-grid">
        <button
          v-for="item in entries"
          :key="item.key"
          type="button"
          class="admin-card"
          :style="{ '--card-accent': item.color, '--card-bg': item.bg }"
          @click="router.push(item.route)"
        >
          <span class="admin-card-icon" v-html="item.icon" />
          <span class="admin-card-body">
            <span class="admin-card-title">{{ item.title }}</span>
            <span class="admin-card-desc">{{ item.desc }}</span>
          </span>
          <svg class="admin-card-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="9 18 15 12 9 6" />
          </svg>
        </button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AdminNavBar from './AdminNavBar.vue'

const router = useRouter()

const entries = [
  {
    key: 'workflow',
    title: '工作流管理',
    desc: '创建、编排与调试 LangChain 工作流，管理节点与执行参数。',
    color: '#15803d',
    bg: '#f0fdf4',
    route: '/workflow-editor',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/><path d="M10 6.5h6a1.5 1.5 0 0 1 1.5 1.5v6"/><path d="M6.5 10v6a1.5 1.5 0 0 0 1.5 1.5h6"/></svg>',
  },
  {
    key: 'model',
    title: '模型管理',
    desc: '管理推理平台模型配置：新增/测试配置、切换当前生效模型。',
    color: '#2563eb',
    bg: '#eff6ff',
    route: '/model-config',
    icon: '<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
  },
]
</script>

<style scoped>
.admin-center {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: #f8fafc;
}
.admin-main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 40px 32px;
}
.admin-hero {
  max-width: 960px;
  margin: 0 auto 28px;
}
.admin-hero-title {
  font-size: 24px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 6px;
}
.admin-hero-desc {
  font-size: 13.5px;
  color: #64748b;
  margin: 0;
}
.admin-grid {
  max-width: 960px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}
.admin-card {
  display: flex;
  align-items: center;
  gap: 14px;
  text-align: left;
  padding: 20px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, transform 0.15s;
}
.admin-card:hover {
  border-color: color-mix(in srgb, var(--card-accent) 40%, #e2e8f0);
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.08);
  transform: translateY(-2px);
}
.admin-card-icon {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--card-accent);
  background: var(--card-bg);
}
.admin-card-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.admin-card-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}
.admin-card-desc {
  font-size: 12.5px;
  color: #64748b;
  line-height: 1.6;
}
.admin-card-arrow {
  color: #cbd5e1;
  flex-shrink: 0;
  transition: color 0.15s, transform 0.15s;
}
.admin-card:hover .admin-card-arrow {
  color: var(--card-accent);
  transform: translateX(2px);
}

@media (max-width: 640px) {
  .admin-main { padding: 24px 16px; }
  .admin-grid { grid-template-columns: 1fr; }
}
</style>
