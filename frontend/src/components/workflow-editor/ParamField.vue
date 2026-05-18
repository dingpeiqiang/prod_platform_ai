<template>
  <div class="param-field">
    <label v-if="label" class="field-label">
      {{ label }}
      <span v-if="helpText" class="help-icon" :title="helpText">?</span>
    </label>
    <slot></slot>
    <div v-if="error" class="field-error">{{ error }}</div>
    <div v-if="hint && !error" class="field-hint">{{ hint }}</div>
  </div>
</template>

<script setup>
defineProps({
  label: {
    type: String,
    default: ''
  },
  helpText: {
    type: String,
    default: ''
  },
  error: {
    type: String,
    default: ''
  },
  hint: {
    type: String,
    default: ''
  }
});
</script>

<style scoped>
.param-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: #333;
  display: flex;
  align-items: center;
  gap: 4px;
}

.help-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #e8e8e8;
  color: #999;
  font-size: 11px;
  cursor: help;
  transition: all 0.2s;
}

.help-icon:hover {
  background: #3b82f6;
  color: white;
}

.field-error {
  font-size: 12px;
  color: #ff4d4f;
}

.field-hint {
  font-size: 12px;
  color: #999;
}
</style>
