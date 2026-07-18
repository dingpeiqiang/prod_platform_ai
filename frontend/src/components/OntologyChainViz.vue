/**
 * 本体推理链 — Vue Flow 网络图（紧凑）
 * 节点 = 本体类实例，边 = ObjectProperty
 */
<template>
  <div v-if="chain" class="onto-net" :class="[{ streaming }, statusClass]">
    <header class="onto-net-head">
      <span class="title">本体推理链</span>
      <span class="sub">类关系网络</span>
      <span v-if="passLabel" class="badge">{{ passLabel }}</span>
    </header>

    <div class="onto-net-canvas">
      <VueFlow
        :id="flowId"
        v-model:nodes="flowNodes"
        v-model:edges="flowEdges"
        :node-types="nodeTypes"
        :nodes-draggable="true"
        :nodes-connectable="false"
        :elements-selectable="false"
        :zoom-on-scroll="true"
        :pan-on-drag="true"
        :min-zoom="0.5"
        :max-zoom="1.4"
        :default-edge-options="defaultEdgeOptions"
        class="flow"
        @nodes-initialized="fit"
      >
        <Background pattern-color="#cbd5e1" :gap="14" :size="1" />
        <Controls :show-interactive="false" position="bottom-right" />
      </VueFlow>
    </div>
  </div>
</template>

<script setup>
import { computed, markRaw, nextTick, ref, watch } from 'vue'
import { VueFlow, MarkerType, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import OntologyRelNode from './OntologyRelNode.vue'
import '@vue-flow/controls/dist/style.css'

const props = defineProps({
  chain: { type: Object, default: null },
  revealCount: { type: Number, default: 0 },
  streaming: { type: Boolean, default: false },
  compact: { type: Boolean, default: false },
  hideStatus: { type: Boolean, default: false },
})

const flowId = `onto-rel-${Math.random().toString(36).slice(2, 9)}`
const { fitView } = useVueFlow({ id: flowId })

const nodeTypes = { onto: markRaw(OntologyRelNode) }

const defaultEdgeOptions = {
  type: 'smoothstep',
  style: { stroke: '#94a3b8', strokeWidth: 1.4 },
  markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8', width: 14, height: 14 },
}

const relations = computed(() => {
  if (props.chain?.relations?.length) return props.chain.relations
  return (props.chain?.hops || []).map((h, i) => ({
    id: h.id || `h-${i}`,
    s: {
      id: `s-${i}-${h.subjectKind}`,
      label: h.subject,
      className: h.subjectKind,
      classCn: '',
    },
    p: h.predicate,
    pCn: h.predicate,
    o: {
      id: `o-${i}-${h.objectKind}`,
      label: h.object,
      className: h.objectKind,
      classCn: '',
    },
    rule: h.rule,
    status: h.status,
  }))
})

const revealN = computed(() => {
  if (!props.revealCount || props.revealCount <= 0) return relations.value.length
  return Math.min(props.revealCount, relations.value.length)
})

const visibleRelations = computed(() => relations.value.slice(0, revealN.value))

const passLabel = computed(() => {
  if (!props.chain || props.hideStatus) return ''
  if (props.chain.compliancePass) return '合规通过'
  if (props.chain.blocked) return '已阻断'
  return props.chain.summary || ''
})

const statusClass = computed(() => {
  if (!props.chain) return ''
  if (props.chain.compliancePass) return 'is-pass'
  if (props.chain.blocked) return 'is-block'
  return 'is-warn'
})

function classKey(className) {
  const k = String(className || '')
  if (/OfferingConfig/.test(k)) return 'cfg'
  if (/Offering/.test(k)) return 'cfg'
  if (/BizScenario/.test(k)) return 'scn'
  if (/Template/.test(k)) return 'tpl'
  if (/Element|Property/.test(k)) return 'el'
  if (/Price|Metric/.test(k)) return 'price'
  if (/TargetUser|User|Behavior/.test(k)) return 'user'
  if (/Channel|Promotion|Competitor/.test(k)) return 'rel'
  if (/RiskFeature|Risk/.test(k)) return 'issue'
  if (/Rule/.test(k)) return 'rule'
  if (/Relation|Shelf/.test(k)) return 'rel'
  if (/Compliance|Issue/.test(k)) return 'issue'
  return 'ent'
}

function collectEntities(rels, hub) {
  const map = new Map()
  const upsert = (e, extra = {}) => {
    if (!e?.id) return
    const prev = map.get(e.id) || {}
    map.set(e.id, {
      ...prev,
      ...e,
      hub: !!(prev.hub || e.hub || extra.hub),
      status: e.status || prev.status || 'idle',
    })
  }
  if (hub) upsert(hub, { hub: true })
  rels.forEach((r) => {
    upsert(r.s)
    upsert(r.o)
  })
  return [...map.values()]
}

function layout(entities) {
  const hub = entities.find((e) => e.hub) || entities[0]
  const others = entities.filter((e) => e.id !== hub?.id)
  const pos = {}
  const colGap = 200
  const rowGap = 78

  if (hub) pos[hub.id] = { x: 0, y: 0 }

  // 左：场景/模板；右：要素/定价/客群；下：规则/关系/合规
  const leftKinds = /BizScenario|Template/
  const bottomKinds = /Rule|Relation|Compliance|Issue|Shelf/
  const left = []
  const right = []
  const bottom = []
  others.forEach((e) => {
    const k = e.className || ''
    if (leftKinds.test(k)) left.push(e)
    else if (bottomKinds.test(k)) bottom.push(e)
    else right.push(e)
  })

  const placeColumn = (list, x, startY) => {
    const total = (list.length - 1) * rowGap
    const y0 = startY - total / 2
    list.forEach((e, i) => {
      pos[e.id] = { x, y: list.length === 1 ? startY : y0 + i * rowGap }
    })
  }

  placeColumn(left, -colGap, 0)
  placeColumn(right, colGap, 0)
  placeColumn(bottom, 0, Math.max(110, 70 + Math.max(left.length, right.length) * 20))

  // 若某侧为空导致挤在一起，把剩余节点均匀排到右侧
  const placed = new Set(Object.keys(pos))
  const rest = others.filter((e) => !placed.has(e.id))
  if (rest.length) placeColumn(rest, colGap, 0)

  return pos
}

function edgeStyle(status) {
  if (status === 'pass') {
    return {
      stroke: '#059669',
      markerEnd: { type: MarkerType.ArrowClosed, color: '#059669', width: 14, height: 14 },
    }
  }
  if (status === 'block' || status === 'conflict') {
    return {
      stroke: '#dc2626',
      markerEnd: { type: MarkerType.ArrowClosed, color: '#dc2626', width: 14, height: 14 },
    }
  }
  if (status === 'warn') {
    return {
      stroke: '#d97706',
      markerEnd: { type: MarkerType.ArrowClosed, color: '#d97706', width: 14, height: 14 },
    }
  }
  return {
    stroke: '#64748b',
    markerEnd: { type: MarkerType.ArrowClosed, color: '#64748b', width: 14, height: 14 },
  }
}

const flowNodes = ref([])
const flowEdges = ref([])

function rebuild() {
  const rels = visibleRelations.value
  const ents = collectEntities(rels, props.chain?.hub)
  const pos = layout(ents)
  const latestId = rels.length ? rels[rels.length - 1].o?.id : ''

  flowNodes.value = ents.map((e) => ({
    id: e.id,
    type: 'onto',
    position: pos[e.id] || { x: 0, y: 0 },
    data: {
      label: e.label,
      className: e.className,
      classCn: e.classCn || '',
      clsKey: classKey(e.className),
      hub: !!e.hub,
      status: e.status || 'idle',
      latest: props.streaming && e.id === latestId,
    },
    draggable: true,
    selectable: false,
  }))

  flowEdges.value = rels.map((r, i) => {
    const st = edgeStyle(r.status)
    return {
      id: r.id || `e-${i}`,
      source: r.s.id,
      target: r.o.id,
      label: r.pCn || r.p,
      type: 'smoothstep',
      animated: props.streaming && i === rels.length - 1,
      style: { stroke: st.stroke, strokeWidth: 1.4 },
      markerEnd: st.markerEnd,
      labelStyle: { fill: '#475569', fontSize: 9, fontWeight: 650 },
      labelBgStyle: { fill: '#fff', fillOpacity: 0.92 },
      labelBgPadding: [3, 5],
      labelBgBorderRadius: 4,
    }
  })
}

function fit() {
  nextTick(() => {
    try {
      fitView({ padding: 0.28, duration: props.streaming ? 160 : 260, maxZoom: 1 })
    } catch (_) {
      /* ignore */
    }
  })
}

watch(
  () => [props.chain, props.revealCount, props.streaming],
  () => {
    rebuild()
    fit()
  },
  { immediate: true, deep: true },
)
</script>

<style scoped>
.onto-net {
  margin: 0;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  overflow: hidden;
}

.onto-net.is-pass { border-color: #bbf7d0; }
.onto-net.is-block { border-color: #fecaca; }
.onto-net.is-warn { border-color: #fde68a; }
.onto-net.streaming { border-color: #93c5fd; }

.onto-net-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.title {
  font-size: 12px;
  font-weight: 700;
  color: #0f172a;
}

.sub {
  font-size: 10px;
  color: #94a3b8;
}

.badge {
  margin-left: auto;
  font-size: 10px;
  font-weight: 650;
  padding: 1px 7px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #334155;
}

.is-pass .badge { background: #dcfce7; color: #166534; }
.is-block .badge { background: #fee2e2; color: #991b1b; }
.is-warn .badge { background: #fef3c7; color: #92400e; }

.onto-net-canvas {
  height: 360px;
  position: relative;
}

.flow {
  width: 100%;
  height: 100%;
  background:
    radial-gradient(ellipse at 30% 20%, rgba(37, 99, 235, 0.05), transparent 50%),
    #fcfcfd;
}
</style>

<style>
.onto-net .vue-flow__controls {
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.1);
  border-radius: 6px;
  overflow: hidden;
  margin: 6px !important;
}

.onto-net .vue-flow__controls-button {
  width: 22px;
  height: 22px;
  border: none;
  border-bottom: 1px solid #e2e8f0;
}

.onto-net .vue-flow__edge-text {
  font-size: 9px !important;
}
</style>
