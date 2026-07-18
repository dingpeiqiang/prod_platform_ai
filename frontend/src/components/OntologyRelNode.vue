/**
 * 本体类节点（Vue Flow）
 */
<template>
  <div
    class="onto-node"
    :class="[`cls-${data.clsKey}`, `st-${data.status || 'idle'}`, { hub: data.hub, latest: data.latest }]"
  >
    <Handle type="target" :position="Position.Left" class="h" />
    <div class="cls">{{ data.className }}</div>
    <div class="name">{{ data.label }}</div>
    <div class="cn">{{ data.classCn }}</div>
    <Handle type="source" :position="Position.Right" class="h" />
  </div>
</template>

<script setup>
import { Handle, Position } from '@vue-flow/core'

defineProps({
  data: { type: Object, default: () => ({}) },
})
</script>

<style scoped>
.onto-node {
  min-width: 108px;
  max-width: 140px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #fff;
  border: 1.5px solid #cbd5e1;
  text-align: center;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

.onto-node.hub {
  border-color: #2563eb;
  background: #eff6ff;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.12);
}

.onto-node.latest {
  border-color: #2563eb;
}

.onto-node.cls-scn { border-color: #64748b; background: #f8fafc; }
.onto-node.cls-tpl { border-color: #7c3aed; background: #f5f3ff; }
.onto-node.cls-el { border-color: #0d9488; background: #f0fdfa; }
.onto-node.cls-price { border-color: #ea580c; background: #fff7ed; }
.onto-node.cls-user { border-color: #0284c7; background: #f0f9ff; }
.onto-node.cls-rule { border-color: #0369a1; background: #e0f2fe; }
.onto-node.cls-rel { border-color: #d97706; background: #fffbeb; }
.onto-node.cls-issue { border-color: #059669; background: #f0fdf4; }
.onto-node.st-block { border-color: #dc2626; background: #fef2f2; }
.onto-node.st-warn { border-color: #d97706; background: #fffbeb; }
.onto-node.st-pass { border-color: #059669; background: #ecfdf5; }

.cls {
  font-size: 9px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: #64748b;
  line-height: 1.2;
}

.name {
  margin-top: 2px;
  font-size: 11px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.25;
  word-break: break-word;
}

.cn {
  margin-top: 1px;
  font-size: 9px;
  color: #94a3b8;
}

.h {
  width: 5px !important;
  height: 5px !important;
  min-width: 5px !important;
  min-height: 5px !important;
  background: #94a3b8 !important;
  border: 1px solid #fff !important;
}
</style>
