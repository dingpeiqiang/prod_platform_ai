<template>
  <div class="sparql-result-graph">
    <div v-if="!results || results.length === 0" class="empty-state">
      <el-empty description="暂无查询结果" />
    </div>
    <div v-else class="graph-container">
      <div class="graph-header">
        <span class="result-count">共 {{ results.length }} 条结果</span>
        <el-button size="small" @click="resetZoom">重置视图</el-button>
      </div>
      <svg ref="svgRef" class="graph-svg" />
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, nextTick } from 'vue'

const props = defineProps({
  results: {
    type: Array,
    default: () => []
  },
  width: {
    type: Number,
    default: 800
  },
  height: {
    type: Number,
    default: 500
  }
})

const svgRef = ref(null)

const buildGraphData = (sparqlResults) => {
  const nodes = []
  const links = []
  const nodeMap = new Map()

  sparqlResults.forEach((row) => {
    Object.entries(row).forEach(([key, value]) => {
      if (key === 's' || key === 'subject' || key === '_subject') {
        if (!nodeMap.has(value)) {
          nodeMap.set(value, { id: value, label: extractLabel(value), type: 'subject' })
          nodes.push(nodeMap.get(value))
        }
      }
    })
  })

  sparqlResults.forEach((row) => {
    const subject = row.s || row.subject || row._subject
    const predicate = row.p || row.predicate || row._predicate
    const objectVal = row.o || row.object || row._object

    if (subject && predicate && objectVal) {
      if (!nodeMap.has(objectVal)) {
        const isLiteral = objectVal.startsWith('"') || objectVal.startsWith("'")
        nodeMap.set(objectVal, {
          id: objectVal,
          label: extractLabel(objectVal),
          type: isLiteral ? 'literal' : 'object'
        })
        nodes.push(nodeMap.get(objectVal))
      }

      links.push({
        source: subject,
        target: objectVal,
        label: extractLabel(predicate)
      })
    }
  })

  return { nodes, links }
}

const extractLabel = (iri) => {
  if (!iri) return ''
  const str = iri.replace(/["']/g, '')
  if (str.includes('#')) return str.split('#').pop()
  if (str.includes('/')) return str.split('/').pop()
  return str
}

const renderGraph = (svg, graphData) => {
  const { width, height } = props
  svg.setAttribute('viewBox', `0 0 ${width} ${height}`)

  const centerX = width / 2
  const centerY = height / 2
  const radius = Math.min(width, height) * 0.35

  svg.innerHTML = ''

  const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs')
  const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker')
  marker.setAttribute('id', 'arrowhead')
  marker.setAttribute('viewBox', '0 0 10 10')
  marker.setAttribute('refX', 10)
  marker.setAttribute('refY', 5)
  marker.setAttribute('markerWidth', 8)
  marker.setAttribute('markerHeight', 8)
  marker.setAttribute('orient', 'auto')
  const arrowPath = document.createElementNS('http://www.w3.org/2000/svg', 'path')
  arrowPath.setAttribute('d', 'M 0 0 L 10 5 L 0 10 z')
  arrowPath.setAttribute('fill', '#999')
  marker.appendChild(arrowPath)
  defs.appendChild(marker)
  svg.appendChild(defs)

  const positions = {}
  graphData.nodes.forEach((node, i) => {
    const angle = (2 * Math.PI * i) / graphData.nodes.length
    positions[node.id] = {
      x: centerX + radius * Math.cos(angle),
      y: centerY + radius * Math.sin(angle)
    }
  })

  graphData.links.forEach((link) => {
    const src = positions[link.source]
    const tgt = positions[link.target]
    if (!src || !tgt) return

    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line')
    line.setAttribute('x1', src.x)
    line.setAttribute('y1', src.y)
    line.setAttribute('x2', tgt.x)
    line.setAttribute('y2', tgt.y)
    line.setAttribute('stroke', '#999')
    line.setAttribute('stroke-width', 1.5)
    line.setAttribute('marker-end', 'url(#arrowhead)')
    svg.appendChild(line)

    const midX = (src.x + tgt.x) / 2
    const midY = (src.y + tgt.y) / 2
    const text = document.createElementNS('http://www.w3.org/2000/svg', 'text')
    text.setAttribute('x', midX)
    text.setAttribute('y', midY - 6)
    text.setAttribute('text-anchor', 'middle')
    text.setAttribute('fill', '#666')
    text.setAttribute('font-size', '11')
    text.textContent = link.label
    svg.appendChild(text)
  })

  graphData.nodes.forEach((node) => {
    const pos = positions[node.id]
    if (!pos) return

    const colorMap = { subject: '#409eff', object: '#67c23a', literal: '#e6a23c' }
    const fillColor = colorMap[node.type] || '#909399'

    const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle')
    circle.setAttribute('cx', pos.x)
    circle.setAttribute('cy', pos.y)
    circle.setAttribute('r', 24)
    circle.setAttribute('fill', fillColor)
    circle.setAttribute('stroke', '#fff')
    circle.setAttribute('stroke-width', 2)
    svg.appendChild(circle)

    const text = document.createElementNS('http://www.w3.org/2000/svg', 'text')
    text.setAttribute('x', pos.x)
    text.setAttribute('y', pos.y + 40)
    text.setAttribute('text-anchor', 'middle')
    text.setAttribute('fill', '#333')
    text.setAttribute('font-size', '12')
    text.textContent = node.label
    svg.appendChild(text)
  })
}

const resetZoom = () => {
  if (svgRef.value) {
    renderGraph(svgRef.value, buildGraphData(props.results))
  }
}

watch(
  () => props.results,
  async (newResults) => {
    if (newResults && newResults.length > 0 && svgRef.value) {
      await nextTick()
      const graphData = buildGraphData(newResults)
      renderGraph(svgRef.value, graphData)
    }
  },
  { deep: true }
)

onMounted(async () => {
  if (props.results && props.results.length > 0 && svgRef.value) {
    await nextTick()
    const graphData = buildGraphData(props.results)
    renderGraph(svgRef.value, graphData)
  }
})
</script>

<style scoped>
.sparql-result-graph {
  width: 100%;
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}

.graph-container {
  width: 100%;
  overflow: auto;
}

.graph-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.result-count {
  font-size: 13px;
  color: #606266;
}

.graph-svg {
  width: 100%;
  min-height: 400px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}
</style>
