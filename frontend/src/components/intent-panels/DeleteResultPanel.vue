<template>
  <div class="delete-result-panel">
    <div class="delete-result-header">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#e74c3c" stroke-width="2">
        <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/>
        <path d="M9 6V4h6v2"/>
      </svg>
      <span>已删除表单「{{ formName || formCode }}」</span>
    </div>
    <p class="delete-result-tip">数据已备份，可随时回退恢复</p>
    
    <!-- 版本历史列表 -->
    <div class="version-history" v-if="versionList?.length">
      <div class="version-history-title">历史版本 ({{ versionList.length }})</div>
      <div v-for="v in versionList" :key="v.id" class="version-item">
        <div class="version-info">
          <span class="version-action">{{ v.action === 'delete' ? '删除前备份' : v.action }}</span>
          <span class="version-time">{{ formatVersionTime(v.timestamp) }}</span>
        </div>
        <button 
          class="rollback-btn" 
          :disabled="rollingBack === v.id"
          @click="$emit('rollback', v)"
          title="回退到此版本"
        >
          {{ rollingBack === v.id ? '回退中...' : '回退' }}
        </button>
      </div>
    </div>

    <!-- 加载版本列表 -->
    <button
      v-if="!versionList && !loadingVersions"
      class="load-versions-btn"
      @click="$emit('load-versions')"
    >查看版本历史</button>
    <div v-if="loadingVersions" class="loading-small">加载中...</div>

    <!-- 回退结果 -->
    <div v-if="rollbackResult" :class="['rollback-result', rollbackResult.success ? 'success' : 'error']">
      {{ rollbackResult.message }}
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  formCode: String,
  formName: String,
  versionList: Array,
  loadingVersions: Boolean,
  rollingBack: String,
  rollbackResult: Object
})

defineEmits(['rollback', 'load-versions'])

const formatVersionTime = (ts) => {
  if (!ts) return ''
  try {
    const d = new Date(ts)
    return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  } catch { return ts }
}
</script>
