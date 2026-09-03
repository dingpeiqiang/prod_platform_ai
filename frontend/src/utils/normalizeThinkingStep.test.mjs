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
  result: '样例套餐A（OF-DEMO-001）',
  stepType: 'llm',
  metadata: { step: 2, scheduleId: 'locate', phase: 'done' },
})
assert(rich.title === '锁定分析对象', 'backend title must be preserved')
assert(rich.content === '定位分析对象与指标快照', 'backend content must be preserved')
assert(rich.result === '样例套餐A（OF-DEMO-001）', 'backend result must be preserved')
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

// 汇总步骤：后端顶层 output（summary + branch_taken）平铺进 io，trace 透传
const generate = normalizeThinkingStep({
  id: 'generate',
  title: '汇总结果',
  content: '正在整合各环节处理结果，生成配置结论与建议…',
  status: 'running',
  output: { summary: '已整合 1 个环节的处理结果，生成结论与建议', branch_taken: '意图明确 → 直接执行' },
  trace: [{ stage: 'llm', message: '大模型已按「结论先行 + 依据支撑」结构生成回答' }],
})
assert(generate.io?.output?.summary === '已整合 1 个环节的处理结果，生成结论与建议', 'output.summary flows into io')
assert(generate.io?.output?.branch_taken === '意图明确 → 直接执行', 'branch_taken flows into io.output')
assert(generate.trace?.length === 1, 'trace passthrough preserved')

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
