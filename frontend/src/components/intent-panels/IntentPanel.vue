<template>
  <div class="intent-panel-wrapper">
    <!-- delete_form -->
    <DeleteResultPanel
      v-if="intentType === 'delete_form' && panelData"
      :formCode="panelData.formCode"
      :formName="panelData.formName"
      :versionList="panelData.versionList || []"
      :loadingVersions="panelData.loadingVersions"
      :rollingBack="panelData.rollingBack"
      :rollbackResult="panelData.rollbackResult"
      @rollback="(v) => emit('intent-action', { intentType, action: 'rollback', payload: v, msg })"
      @load-versions="() => emit('intent-action', { intentType, action: 'load-versions', payload: panelData, msg })"
    />

    <!-- manage_history -->
    <HistoryPanel
      v-else-if="intentType === 'manage_history' && panelData"
      :historyData="panelData"
      :importing="panelData.importing"
      :importResult="panelData.importResult"
      @import="() => emit('intent-action', { intentType, action: 'import', payload: panelData, msg })"
      @analyze="() => emit('intent-action', { intentType, action: 'analyze', payload: panelData, msg })"
      @export="(opts) => emit('intent-action', { intentType, action: 'export', payload: opts, msg })"
    />

    <!-- validation_fail / validation_pass -->
    <ValidationResultPanel
      v-else-if="(intentType === 'validation_fail' || intentType === 'validation_pass') && panelData"
      :formCode="panelData.formCode"
      :passed="panelData.passed"
      :errors="panelData.errors || []"
      :warnings="panelData.warnings || []"
      :step="panelData.step"
      :rule_engine_passed="panelData.rule_engine_passed"
      :validationTable="panelData.validationTable"
    />

    <!-- product_ops_query / product_ops_policy / product_ops_reason / product_ops_compare -->
    <ProductOpsPanel
      v-else-if="isProductOps"
      :intentType="intentType"
      :msg="msg"
      @intent-action="(e) => emit('intent-action', e)"
    />

    <!-- form / form_update -->
    <FormIntentPanel
      v-else-if="intentType === 'form' || intentType === 'form_update'"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import DeleteResultPanel from './DeleteResultPanel.vue'
import HistoryPanel from './HistoryPanel.vue'
import ValidationResultPanel from './ValidationResultPanel.vue'
import ProductOpsPanel from './ProductOpsPanel.vue'
import FormIntentPanel from './FormIntentPanel.vue'

const props = defineProps({
  intentType: { type: String, required: true },
  msg: { type: Object, required: true }
})

const emit = defineEmits(['intent-action'])

const productOpsTypes = ['product_ops_query', 'product_ops_policy', 'product_ops_reason', 'product_ops_compare']
const isProductOps = computed(() => productOpsTypes.includes(props.intentType))

// 从 msg._intentData[intentType] 取意图数据
const panelData = computed(() => {
  return props.msg._intentData?.[props.intentType] || null
})
</script>

<style scoped>
.intent-panel-wrapper {
  width: 100%;
}
</style>