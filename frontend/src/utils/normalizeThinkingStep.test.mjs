/**
 * Node smoke test for normalizeThinkingStep — run with:
 *   node --experimental-vm-modules frontend/src/utils/normalizeThinkingStep.test.mjs
 * or: node frontend/src/utils/normalizeThinkingStep.test.mjs
 */
import {
  attachRootCauseOntology,
  normalizeThinkingStep,
} from './normalizeThinkingStep.js'

function assert(cond, msg) {
  if (!cond) throw new Error(msg || 'assertion failed')
}

const rich = normalizeThinkingStep({
  id: 'locate',
  title: '锁定分析对象',
  content: '定位分析对象与指标快照',
  result: '家庭融合畅享128（OF-HF-128）',
  stepType: 'llm',
  metadata: { step: 2, scheduleId: 'locate', phase: 'done' },
})
assert(rich.title === '锁定分析对象', 'backend title must be preserved')
assert(rich.content === '定位分析对象与指标快照', 'backend content must be preserved')
assert(rich.result === '家庭融合畅享128（OF-HF-128）', 'backend result must be preserved')
assert(rich.id === 'locate', 'id preserved')

const reason = normalizeThinkingStep({
  id: 'reason',
  title: '规则推理',
  content: '执行图谱与 SWRL 归因规则',
  result: 'openllet 命中',
  stepType: 'ontology',
  metadata: { step: 5, scheduleId: 'reason' },
})
assert(reason.type === 'ontology', 'reason step is ontology')

const attached = attachRootCauseOntology(
  [reason],
  { paths: [{ name: '营业厅', rank: 1, weight: 0.4, rootCauseType: 'Channel' }], anomalies: [{ metricCode: '累计收入' }], offeringName: '测试套餐' },
  {
    buildChain: () => ({ hops: [{ id: 'h1' }], conclusion: '主因营业厅' }),
    buildPreview: () => ({ conclusion: '主因营业厅' }),
  },
)
assert(attached[0].ontologyChain?.conclusion === '主因营业厅', 'ontology chain attached')
assert(attached[0].ontologyPreview?.conclusion === '主因营业厅', 'ontology preview attached')

console.log('normalizeThinkingStep.test.mjs: OK')
