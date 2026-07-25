<template>
  <aside class="config-trace-panel" v-if="visible">
    <header class="ctp-header">
      <div>
        <h3>配置审计追溯</h3>
        <p class="ctp-sub">trace: {{ traceId || '—' }}</p>
      </div>
      <button type="button" class="ctp-close" @click="$emit('close')">关闭</button>
    </header>

    <section class="ctp-section">
      <h4>操作链路</h4>
      <ol v-if="steps?.length" class="ctp-steps">
        <li v-for="(s, idx) in steps" :key="idx">
          <strong>{{ s.step || 'step' }}</strong>
          <span v-if="s.timestamp" class="ctp-ts">{{ s.timestamp }}</span>
          <pre v-if="hasDetail(s)" class="ctp-detail">{{ formatDetail(s) }}</pre>
        </li>
      </ol>
      <p v-else class="ctp-empty">暂无步骤记录</p>
    </section>

    <section class="ctp-section">
      <h4>业务说明（explain · audience=business）</h4>
      <pre class="ctp-explain">{{ explanation || '暂无说明' }}</pre>
    </section>
  </aside>
</template>

<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  traceId: { type: String, default: '' },
  steps: { type: Array, default: () => [] },
  explanation: { type: String, default: '' },
})

defineEmits(['close'])

function hasDetail(s) {
  return s && Object.keys(s).some((k) => !['step', 'timestamp'].includes(k))
}

function formatDetail(s) {
  const copy = { ...s }
  delete copy.step
  delete copy.timestamp
  return JSON.stringify(copy, null, 2)
}
</script>

<style scoped>
.config-trace-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  padding: 16px 18px;
  background: linear-gradient(180deg, #f7fafc 0%, #eef3f8 100%);
  border-left: 1px solid #d7e0ea;
  overflow: auto;
}
.ctp-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.ctp-header h3 {
  margin: 0;
  font-size: 16px;
  color: #1a2b3c;
}
.ctp-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6b7c8f;
  word-break: break-all;
}
.ctp-close {
  border: 1px solid #c5d0dc;
  background: #fff;
  border-radius: 6px;
  padding: 4px 10px;
  cursor: pointer;
  font-size: 12px;
}
.ctp-section h4 {
  margin: 0 0 8px;
  font-size: 13px;
  color: #2c3e50;
}
.ctp-steps {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ctp-steps li {
  font-size: 13px;
  color: #243447;
}
.ctp-ts {
  display: block;
  font-size: 11px;
  color: #7a8b9c;
  margin-top: 2px;
}
.ctp-detail,
.ctp-explain {
  margin: 6px 0 0;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #dce5ef;
  border-radius: 8px;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-word;
  color: #334455;
}
.ctp-empty {
  margin: 0;
  font-size: 13px;
  color: #7a8b9c;
}
</style>
