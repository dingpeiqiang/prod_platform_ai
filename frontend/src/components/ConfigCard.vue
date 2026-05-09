<template>
  <div class="config-card" :class="{ deployed }">
    <!-- 头部 -->
    <div class="config-header">
      <div class="config-header-left">
        <span class="config-icon">🛠️</span>
        <div>
          <div class="config-title">{{ config.formName || '新表单' }}</div>
          <div class="config-code">{{ config.formCode }}</div>
        </div>
      </div>
      <span v-if="deployed" class="deployed-badge">✅ 已部署</span>
      <span v-else-if="hasErrors" class="error-badge">⚠️ 有问题</span>
    </div>

    <!-- 描述 -->
    <div v-if="config.description" class="config-desc">{{ config.description }}</div>

    <!-- 字段预览 -->
    <div class="entities-section">
      <div
        v-for="(entity, ei) in config.entities || []"
        :key="ei"
        class="entity-group"
      >
        <div class="entity-name" @click="toggleEntity(ei)">
          <svg
            :style="{ transform: collapsedEntities[ei] ? 'rotate(0deg)' : 'rotate(90deg)' }"
            width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
            style="transition:transform .2s"
          >
            <polyline points="9 18 15 12 9 6"/>
          </svg>
          {{ entity.entityName || entity.entityCode }}
          <span class="field-count">{{ entity.fields?.length || 0 }} 个字段</span>
        </div>
        <transition name="collapse">
          <div v-if="!collapsedEntities[ei]" class="fields-list">
            <div
              v-for="(field, fi) in entity.fields || []"
              :key="fi"
              class="field-row"
            >
              <span class="field-code">{{ field.fieldCode }}</span>
              <span class="field-name">{{ field.fieldName }}</span>
              <span :class="['field-type-badge', `type-${field.fieldType}`]">{{ field.fieldType }}</span>
              <span v-if="field.required" class="required-star">*</span>
            </div>
          </div>
        </transition>
      </div>
    </div>

    <!-- 关键词预览 -->
    <div v-if="keywords.length" class="keywords-section">
      <div class="section-label">🔑 场景关键词</div>
      <div class="keyword-tags">
        <span v-for="(kw, i) in keywords" :key="i" class="keyword-tag">{{ kw }}</span>
      </div>
    </div>

    <!-- 校验错误 -->
    <div v-if="validationErrors.length" class="errors-section">
      <div class="section-label error-label">⚠️ 校验问题</div>
      <ul class="error-list">
        <li v-for="(err, i) in validationErrors" :key="i">{{ err }}</li>
      </ul>
    </div>

    <!-- 操作按钮 -->
    <div v-if="!deployed" class="config-actions">
      <button class="deploy-btn" :disabled="hasErrors || deploying" @click="$emit('deploy', { config, keywords })">
        <span v-if="deploying" class="btn-loading">⏳</span>
        <span v-else>🚀</span>
        {{ deploying ? '部署中...' : '一键部署' }}
      </button>
      <button class="preview-btn" @click="$emit('preview', config)">
        👁️ 预览
      </button>
      <button class="modify-btn" @click="$emit('modify')">
        ✏️ 修改
      </button>
    </div>

    <!-- 已部署后的测试按钮 -->
    <div v-if="deployed" class="deployed-actions">
      <button class="test-btn" @click="$emit('test', config.formCode)">
        🧪 立即测试
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'

const props = defineProps({
  config:       { type: Object, default: () => ({}) },
  keywords:     { type: Array, default: () => [] },
  validationErrors: { type: Array, default: () => [] },
  deployed:     { type: Boolean, default: false },
  deploying:    { type: Boolean, default: false }
})

defineEmits(['deploy', 'modify', 'test', 'preview'])

const collapsedEntities = reactive({})

const hasErrors = computed(() => props.validationErrors.length > 0)

const toggleEntity = (idx) => {
  collapsedEntities[idx] = !collapsedEntities[idx]
}
</script>

<style scoped>
.config-card {
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: 14px;
  overflow: hidden;
  margin: 8px 0;
  box-shadow: var(--shadow-sm);
  transition: border-color .2s;
}
.config-card.deployed {
  border-color: #86efac;
  background: var(--bg-elevated);
}
.config-card:not(.deployed):hover {
  border-color: #c7c7fa;
}

/* 头部 */
.config-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  background: linear-gradient(135deg, #f5f3ff, #faf5ff);
  border-bottom: 1px solid #ede9fe;
}
.config-header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.config-icon {
  font-size: 22px;
}
.config-title {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
}
.config-code {
  font-size: 12px;
  color: #8b5cf6;
  font-family: 'JetBrains Mono', monospace;
  margin-top: 2px;
}
.deployed-badge {
  padding: 4px 10px;
  background: #dcfce7;
  color: #16a34a;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}
.error-badge {
  padding: 4px 10px;
  background: #fef3c7;
  color: #d97706;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

/* 描述 */
.config-desc {
  padding: 10px 18px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
  border-bottom: 1px solid var(--border-light);
}

/* 实体组 */
.entities-section {
  padding: 12px 18px;
}
.entity-group {
  margin-bottom: 8px;
}
.entity-group:last-child {
  margin-bottom: 0;
}
.entity-name {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-primary);
  cursor: pointer;
  border-radius: 6px;
  transition: background .15s;
}
.entity-name:hover {
  background: var(--bg-tertiary);
}
.field-count {
  font-size: 11px;
  color: var(--text-tertiary);
  font-weight: 400;
  margin-left: 4px;
}

/* 字段列表 */
.fields-list {
  padding: 4px 0 4px 24px;
}
.field-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  font-size: 13px;
  border-radius: 4px;
  transition: background .1s;
}
.field-row:hover {
  background: #fafafa;
}
.field-code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #7c3aed;
  min-width: 100px;
}
.field-name {
  flex: 1;
  color: var(--text-primary);
}
.field-type-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  font-family: 'JetBrains Mono', monospace;
}
.type-input    { background: #ede9fe; color: #7c3aed; }
.type-number   { background: #dbeafe; color: #2563eb; }
.type-date     { background: #fef3c7; color: #d97706; }
.type-select   { background: #d1fae5; color: #059669; }
.type-textarea { background: #fce7f3; color: #db2777; }
.required-star {
  color: #ef4444;
  font-weight: 700;
  font-size: 14px;
}

/* 关键词 */
.keywords-section {
  padding: 10px 18px;
  border-top: 1px solid var(--border-light);
}
.section-label {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 8px;
}
.error-label {
  color: var(--color-warning-600);
}
.keyword-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.keyword-tag {
  padding: 3px 10px;
  background: #f5f3ff;
  color: #7c3aed;
  border-radius: 12px;
  font-size: 12px;
  border: 1px solid #ede9fe;
}

/* 校验错误 */
.errors-section {
  padding: 10px 18px;
  border-top: 1px solid var(--border-light);
}
.error-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12.5px;
  color: var(--color-warning-600);
  line-height: 1.8;
}

/* 操作按钮 */
.config-actions {
  display: flex;
  gap: 10px;
  padding: 14px 18px;
  border-top: 1px solid var(--border-light);
  background: var(--bg-secondary);
}
.deploy-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 20px;
  background: linear-gradient(135deg, #818cf8, #6366f1);
  color: var(--text-inverse);
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all .2s;
}
.deploy-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(99,102,241,.35);
}
.deploy-btn:disabled {
  opacity: .5;
  cursor: not-allowed;
}
.btn-loading {
  animation: spin 1s linear infinite;
  display: inline-block;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
.modify-btn {
  padding: 10px 20px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: 10px;
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all .15s;
}
.modify-btn:hover {
  border-color: #818cf8;
  color: #6366f1;
}
.preview-btn {
  padding: 10px 20px;
  background: var(--bg-elevated);
  border: 1px solid #c7d2fe;
  border-radius: 10px;
  font-size: 14px;
  color: #6366f1;
  cursor: pointer;
  transition: all .15s;
}
.preview-btn:hover {
  background: var(--color-primary-50);
  border-color: #818cf8;
}

/* 已部署后 */
.deployed-actions {
  padding: 14px 18px;
  border-top: 1px solid var(--border-light);
  background: var(--bg-secondary);
}
.test-btn {
  width: 100%;
  padding: 10px;
  background: linear-gradient(135deg, #34d399, #059669);
  color: var(--text-inverse);
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all .2s;
}
.test-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(5,150,105,.35);
}

/* 折叠动画 */
.collapse-enter-active, .collapse-leave-active {
  transition: max-height .25s ease, opacity .2s ease;
  overflow: hidden;
  max-height: 400px;
}
.collapse-enter-from, .collapse-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
