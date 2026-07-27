/**
 * 本体类节点（Vue Flow）— 中文类型优先；四向锚点避免绕线
 */
<template>
  <div
    class="onto-node"
    :class="[`cls-${data.clsKey}`, `st-${data.status || 'idle'}`, { hub: data.hub, latest: data.latest }]"
    :title="tooltip"
  >
    <Handle id="t-left" type="target" :position="Position.Left" class="h" />
    <Handle id="t-right" type="target" :position="Position.Right" class="h" />
    <Handle id="t-top" type="target" :position="Position.Top" class="h" />
    <Handle id="t-bottom" type="target" :position="Position.Bottom" class="h" />
    <div class="cls">{{ displayClass }}</div>
    <div class="name">{{ data.label }}</div>
    <Handle id="s-left" type="source" :position="Position.Left" class="h" />
    <Handle id="s-right" type="source" :position="Position.Right" class="h" />
    <Handle id="s-top" type="source" :position="Position.Top" class="h" />
    <Handle id="s-bottom" type="source" :position="Position.Bottom" class="h" />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'
import { classCn } from '../utils/ontologyLabels.js'

const props = defineProps({
  data: { type: Object, default: () => ({}) },
})

const displayClass = computed(() => {
  const d = props.data || {}
  return d.classCn || classCn(d.className) || d.className || '实体'
})

const tooltip = computed(() => {
  const d = props.data || {}
  const en = d.className
  if (!en || en === displayClass.value) return d.label || ''
  return `${displayClass.value} · ${en}`
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
.onto-node.cls-cfg { border-color: #2563eb; background: #eff6ff; }
.onto-node.cls-price { border-color: #ea580c; background: #fff7ed; }
.onto-node.cls-user { border-color: #0284c7; background: #f0f9ff; }
.onto-node.cls-rule { border-color: #0369a1; background: #e0f2fe; }
.onto-node.cls-rel { border-color: #d97706; background: #fffbeb; }
.onto-node.cls-issue { border-color: #059669; background: #f0fdf4; }
.onto-node.cls-ent { border-color: #94a3b8; background: #fff; }
.onto-node.st-block { border-color: #dc2626; background: #fef2f2; }
.onto-node.st-warn { border-color: #d97706; background: #fffbeb; }
.onto-node.st-pass { border-color: #059669; background: #ecfdf5; }

.cls {
  font-size: 10px;
  color: #64748b;
  line-height: 1.2;
  font-weight: 600;
}

.name {
  margin-top: 2px;
  font-size: 11px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.25;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  max-height: 2.5em;
}

/* 锚点仅用于路由，视觉上隐藏避免干扰 */
.h {
  width: 6px !important;
  height: 6px !important;
  min-width: 6px !important;
  min-height: 6px !important;
  opacity: 0 !important;
  border: none !important;
  background: transparent !important;
  pointer-events: none;
}
</style>
