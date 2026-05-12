<template>
  <div class="intent-panel-wrapper">
    <!-- config -->
    <ConfigCard
      v-if="intentType === 'config' && panelData"
      :config="panelData.config"
      :keywords="panelData.keywords"
      :validationErrors="panelData.validationErrors"
      :deployed="panelData.deployed"
      :deploying="panelData.deploying"
      @deploy="(cfg) => emit('intent-action', { intentType, action: 'deploy', payload: cfg, msg })"
      @modify="() => emit('intent-action', { intentType, action: 'modify', payload: panelData, msg })"
      @preview="(cfg) => emit('intent-action', { intentType, action: 'preview', payload: cfg, msg })"
      @test="(formCode) => emit('intent-action', { intentType, action: 'test', payload: formCode, msg })"
    />

    <!-- delete_form -->
    <DeleteResultPanel
      v-else-if="intentType === 'delete_form' && panelData"
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
  </div>
</template>

<script setup>
import { computed } from 'vue'
import ConfigCard from '../ConfigCard.vue'
import DeleteResultPanel from './DeleteResultPanel.vue'
import HistoryPanel from './HistoryPanel.vue'
import ValidationResultPanel from './ValidationResultPanel.vue'

const props = defineProps({
  intentType: { type: String, required: true },
  msg: { type: Object, required: true }
})

const emit = defineEmits(['intent-action'])

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