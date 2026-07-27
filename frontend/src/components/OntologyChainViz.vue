/**
 * 本体推理链 — Vue Flow 网络图（紧凑）
 * 节点 = 本体类实例，边 = ObjectProperty
 */
<template>
  <div v-if="chain" class="onto-net" :class="[{ streaming, compact }, statusClass]">
    <header class="onto-net-head">
      <span class="title">{{ chartTitle }}</span>
      <span class="sub">{{ chartSub }}</span>
      <span v-if="passLabel" class="badge">{{ passLabel }}</span>
    </header>

    <div class="onto-net-canvas" :style="canvasStyle">
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
import { classCn, predicateCn } from '../utils/ontologyLabels.js'
import '@vue-flow/controls/dist/style.css'

const props = defineProps({
  chain: { type: Object, default: null },
  revealCount: { type: Number, default: 0 },
  streaming: { type: Boolean, default: false },
  compact: { type: Boolean, default: false },
  hideStatus: { type: Boolean, default: false },
  /** 高亮的关系 id；非空时其余边/节点降不透明度 */
  focusRelationIds: { type: Array, default: () => [] },
})

const flowId = `onto-rel-${Math.random().toString(36).slice(2, 9)}`
const { fitView } = useVueFlow({ id: flowId })

const nodeTypes = { onto: markRaw(OntologyRelNode) }

const defaultEdgeOptions = {
  type: 'default',
  style: { stroke: '#94a3b8', strokeWidth: 1.4 },
  markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8', width: 14, height: 14 },
}

const relations = computed(() => {
  if (props.chain?.relations?.length) {
    return props.chain.relations.map((r) => ({
      ...r,
      pCn: r.pCn || predicateCn(r.p),
      s: r.s
        ? { ...r.s, classCn: r.s.classCn || classCn(r.s.className) }
        : r.s,
      o: r.o
        ? { ...r.o, classCn: r.o.classCn || classCn(r.o.className) }
        : r.o,
    }))
  }
  return (props.chain?.hops || []).map((h, i) => ({
    id: h.id || `h-${i}`,
    s: {
      id: `s-${i}-${h.subjectKind}`,
      label: h.subject,
      className: h.subjectKind,
      classCn: classCn(h.subjectKind),
    },
    p: h.predicate,
    pCn: predicateCn(h.predicate) || h.predicate,
    o: {
      id: `o-${i}-${h.objectKind}`,
      label: h.object,
      className: h.objectKind,
      classCn: classCn(h.objectKind),
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

const chartTitle = computed(() => {
  const hubClass = props.chain?.hub?.className || ''
  if (/OfferingConfig/.test(hubClass)) return '配置关系图'
  const kinds = [
    hubClass,
    ...(props.chain?.relations || []).flatMap((r) => [r.s?.className, r.o?.className]),
  ].join(' ')
  if (/BizScenario|ConfigTemplate|ConfigRule|ComplianceIssue/.test(kinds)) return '配置关系图'
  if (/Metric|Channel|Promotion|Competitor|UserBehavior/.test(kinds)) return '归因关系图'
  return '本体关系图'
})

const chartSub = computed(() => {
  if (props.focusRelationIds?.length) return '当前路径高亮'
  if (chartTitle.value === '配置关系图') return '场景 · 模板 · 规则 · 合规'
  if (chartTitle.value === '归因关系图') return '产商品与根因关系'
  return '实体与关系'
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

function statusRank(status) {
  if (status === 'block' || status === 'conflict') return 0
  if (status === 'warn') return 1
  if (status === 'pass') return 2
  return 3
}

/** 异动归因 / 风险稽核：星型图（中心产商品 → 右侧指标与根因） */
function isAttributionStar(entities, hub) {
  if (!hub) return false
  const others = entities.filter((e) => e.id !== hub.id)
  if (!others.length) return false
  const configKinds = /BizScenario|Template|Element|Property|Compliance|Shelf/
  const starKinds = /Metric|Channel|Promotion|Competitor|UserBehavior|RiskFeature|Risk|Price/
  const starN = others.filter((e) => starKinds.test(e.className || '')).length
  const configN = others.filter((e) => configKinds.test(e.className || '')).length
  return configN === 0 && starN >= Math.ceil(others.length * 0.5)
}

function placeColumn(list, x, startY, rowGap, pos) {
  if (!list.length) return
  const total = (list.length - 1) * rowGap
  const y0 = startY - total / 2
  list.forEach((e, i) => {
    pos[e.id] = { x, y: list.length === 1 ? startY : y0 + i * rowGap }
  })
}

function placeRow(list, centerX, y, colGap, pos) {
  if (!list.length) return
  const total = (list.length - 1) * colGap
  const x0 = centerX - total / 2
  list.forEach((e, i) => {
    pos[e.id] = { x: list.length === 1 ? centerX : x0 + i * colGap, y }
  })
}

/** 按相对方位选锚点，避免「右出左进」绕圈交叉 */
function pickHandles(src, tgt) {
  const dx = (tgt?.x || 0) - (src?.x || 0)
  const dy = (tgt?.y || 0) - (src?.y || 0)
  if (Math.abs(dx) >= Math.abs(dy) * 0.85) {
    if (dx >= 0) return { sourceHandle: 's-right', targetHandle: 't-left' }
    return { sourceHandle: 's-left', targetHandle: 't-right' }
  }
  if (dy >= 0) return { sourceHandle: 's-bottom', targetHandle: 't-top' }
  return { sourceHandle: 's-top', targetHandle: 't-bottom' }
}

function layoutAttribution(entities, hub) {
  const pos = {}
  const colGap = 240
  const rowGap = 108
  const others = entities.filter((e) => e.id !== hub?.id)

  const metrics = others.filter((e) => /Metric|Price/.test(e.className || ''))
  const causes = others
    .filter((e) => !metrics.some((m) => m.id === e.id))
    .slice()
    .sort((a, b) => statusRank(a.status) - statusRank(b.status))

  // 右列：指标在上，根因按严重度向下排列，避免边与标签挤在一起
  const rightCol = [...metrics, ...causes]
  placeColumn(rightCol, colGap, 0, rowGap, pos)

  if (hub) {
    const first = rightCol[0]
    const last = rightCol[rightCol.length - 1]
    const midY = first && last ? (pos[first.id].y + pos[last.id].y) / 2 : 0
    pos[hub.id] = { x: 0, y: midY }
  }
  return pos
}

function layoutConfig(entities, hub) {
  const others = entities.filter((e) => e.id !== hub?.id)
  const pos = {}
  const colGap = 230
  const rowGap = 100

  if (hub) pos[hub.id] = { x: 0, y: 0 }

  // 左：场景/模板；右：要素/定价/客群；下横排：规则 → 合规
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

  // 模板在下、场景在上，阅读顺序更自然
  left.sort((a, b) => {
    const score = (e) => (/Template/.test(e.className || '') ? 1 : 0)
    return score(a) - score(b)
  })
  right.sort((a, b) => {
    const score = (e) => (/Price|Metric/.test(e.className || '') ? 0 : 1)
    return score(a) - score(b)
  })

  placeColumn(left, -colGap, 0, rowGap, pos)
  placeColumn(right, colGap, 0, rowGap, pos)

  const rules = bottom.filter((e) => /Rule/.test(e.className || ''))
  const issues = bottom.filter((e) => /Compliance|Issue/.test(e.className || ''))
  const restBottom = bottom.filter(
    (e) => !rules.some((r) => r.id === e.id) && !issues.some((r) => r.id === e.id),
  )
  const bottomRow = [...rules, ...restBottom, ...issues]
  const sideH = Math.max(left.length, right.length, 1)
  const bottomY = 56 + sideH * (rowGap / 2) + 72
  placeRow(bottomRow, 0, bottomY, 200, pos)

  const placed = new Set(Object.keys(pos))
  const rest = others.filter((e) => !placed.has(e.id))
  if (rest.length) placeColumn(rest, colGap, 0, rowGap, pos)

  return pos
}

function layout(entities) {
  const hub = entities.find((e) => e.hub) || entities[0]
  if (isAttributionStar(entities, hub)) return layoutAttribution(entities, hub)
  return layoutConfig(entities, hub)
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

const canvasStyle = computed(() => {
  // 关系数 + hub ≈ 节点数；画布随节点增高，避免紧凑模式下挤成一团
  const n = visibleRelations.value.length + 1
  const base = props.compact ? 280 : 360
  const per = props.compact ? 48 : 52
  const h = Math.min(560, Math.max(base, 120 + n * per))
  return { height: `${h}px` }
})

function rebuild() {
  const rels = visibleRelations.value
  const ents = collectEntities(rels, props.chain?.hub)
  const hub = ents.find((e) => e.hub) || ents[0]
  const star = isAttributionStar(ents, hub)
  const pos = layout(ents)
  const latestId = rels.length ? rels[rels.length - 1].o?.id : ''
  const focusSet = new Set((props.focusRelationIds || []).filter(Boolean))
  const hasFocus = focusSet.size > 0
  const focusedNodeIds = new Set()
  if (hasFocus) {
    rels.forEach((r) => {
      const rid = r.id || ''
      if (focusSet.has(rid) || r.shared) {
        if (r.s?.id) focusedNodeIds.add(r.s.id)
        if (r.o?.id) focusedNodeIds.add(r.o.id)
      }
    })
  }

  flowNodes.value = ents.map((e) => {
    const dimmed = hasFocus && !focusedNodeIds.has(e.id) && !e.hub
    return {
      id: e.id,
      type: 'onto',
      position: pos[e.id] || { x: 0, y: 0 },
      data: {
        label: e.label,
        className: e.className,
        classCn: e.classCn || classCn(e.className) || '',
        clsKey: classKey(e.className),
        hub: !!e.hub,
        status: e.status || 'idle',
        latest: props.streaming && e.id === latestId,
        dimmed,
        focused: hasFocus && focusedNodeIds.has(e.id),
      },
      style: dimmed ? { opacity: 0.22 } : { opacity: 1 },
      draggable: true,
      selectable: false,
    }
  })

  // 按相对方位选锚点；配置图用正交折线更整齐，星型图用贝塞尔
  const edgeType = star ? 'default' : 'smoothstep'
  flowEdges.value = rels.map((r, i) => {
    const rid = r.id || `e-${i}`
    const focused = !hasFocus || focusSet.has(rid) || !!r.shared
    const st = edgeStyle(focused ? r.status : 'idle')
    const showLabel = focused
    const srcPos = pos[r.s?.id] || { x: 0, y: 0 }
    const tgtPos = pos[r.o?.id] || { x: 0, y: 0 }
    const handles = pickHandles(srcPos, tgtPos)
    return {
      id: rid,
      source: r.s.id,
      target: r.o.id,
      sourceHandle: handles.sourceHandle,
      targetHandle: handles.targetHandle,
      label: showLabel ? (r.pCn || r.p) : undefined,
      type: edgeType,
      animated: (props.streaming && i === rels.length - 1) || (hasFocus && focused && !r.shared),
      style: {
        stroke: st.stroke,
        strokeWidth: focused && hasFocus ? 2.2 : 1.35,
        opacity: focused ? 1 : 0.14,
      },
      markerEnd: st.markerEnd,
      labelStyle: {
        fill: '#334155',
        fontSize: 10,
        fontWeight: 650,
      },
      labelBgStyle: { fill: '#fff', fillOpacity: 0.95 },
      labelBgPadding: [4, 6],
      labelBgBorderRadius: 4,
    }
  })
}

function fit() {
  nextTick(() => {
    try {
      fitView({
        padding: 0.22,
        duration: props.streaming ? 160 : 280,
        maxZoom: 1.05,
        minZoom: 0.55,
      })
    } catch (_) {
      /* ignore */
    }
  })
}

watch(
  () => [props.chain, props.revealCount, props.streaming, props.focusRelationIds],
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
  height: 380px;
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
