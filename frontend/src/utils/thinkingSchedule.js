/**
 * 思考过程 = 统一时间线：意图识别 + 场景模板化调度。
 * 请求中 / 完成后是同一套步骤，结果原地回填，不做两套文案替换。
 */

export function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/** 场景展示名（意图识别结果） */
export const SCENARIO_LABELS = {
  query: '智查·配置检索',
  'file-parse': '智读·文件配置',
  'confirm-batch': '批量入库闭环',
  compare: '多方案对比',
  'config-trace': '配置审计追溯',
  compliance: '智检·合规校验',
  'root-cause': '异动根因分析',
  'risk-audit': '风险稽核',
  'chat-generate': '智聊·对话配置',
}

/**
 * 各场景调度模板（意图识别之后的固定步骤）。
 * id 稳定，用于结果回填；title/content 即面板展示文案。
 */
export const SCENARIO_SCHEDULES = {
  'chat-generate': [
    { id: 'intent', type: 'llm', title: '确认业务意图', content: '解析用户输入，确认配置意图' },
    { id: 'slots', type: 'llm', title: '抽取业务槽位', content: '从对话中抽取场景、品类、固费等关键槽位' },
    {
      id: 'ontology',
      type: 'ontology',
      title: '本体补全与合规',
      content: '调用配置服务完成字段补全与合规判定',
    },
    { id: 'reply', type: 'llm', title: '组织回复话术', content: '汇总业务结论，生成用户可读说明' },
  ],
  query: [
    { id: 'intent', type: 'llm', title: '确认业务意图', content: '解析用户输入，确认检索意图' },
    { id: 'parse', type: 'llm', title: '解析查询条件', content: '抽取关键词与检索范围' },
    {
      id: 'ontology',
      type: 'ontology',
      title: '配置事实检索',
      content: '检索历史可复制配置方案',
    },
    { id: 'reply', type: 'llm', title: '整理可复制方案', content: '汇总命中方案并生成说明' },
  ],
  'file-parse': [
    { id: 'intent', type: 'llm', title: '确认业务意图', content: '识别为智读·文件配置映射' },
    { id: 'parse', type: 'llm', title: '接收与识别文档', content: '解析上传文件并识别业务场景' },
    { id: 'extract', type: 'llm', title: '抽取套餐段落', content: '抽取名称 / 月费 / 要素 / 客群 / 渠道' },
    {
      id: 'ontology',
      type: 'ontology',
      title: '场景映射与合规',
      content: '场景映射、字段补全与合规筛查',
    },
    { id: 'reply', type: 'llm', title: '整理映射清单', content: '生成可读清单并投影场景报文' },
  ],
  'confirm-batch': [
    { id: 'intent', type: 'llm', title: '确认业务意图', content: '识别为批量入库闭环' },
    { id: 'filter', type: 'llm', title: '筛选可入库草稿', content: '检查合规通过且未入库项' },
    {
      id: 'ontology',
      type: 'ontology',
      title: '提交备案闭环',
      content: '合规 → 沉淀本体 → 资费备案工单',
    },
    { id: 'reply', type: 'llm', title: '汇总成败', content: '统计成功与失败项' },
  ],
  compare: [
    { id: 'intent', type: 'llm', title: '确认业务意图', content: '识别多方案对比意图' },
    { id: 'extract', type: 'llm', title: '抽取方案假设', content: '准备定价 / 立项对比维度' },
    { id: 'snapshot', type: 'llm', title: '构建事实快照', content: '构建对比用事实快照' },
    {
      id: 'evaluate',
      type: 'ontology',
      title: '合规与收益评估',
      content: '按策略集评估合规与收益',
    },
    { id: 'conclude', type: 'llm', title: '推荐结论', content: '输出可解释推荐说明' },
  ],
  'config-trace': [
    { id: 'intent', type: 'llm', title: '确认业务意图', content: '识别为配置审计追溯' },
    { id: 'load', type: 'llm', title: '加载审计链路', content: '调用 get_trace 拉取审计步骤' },
    { id: 'explain', type: 'llm', title: '生成业务说明', content: 'explain(audience=business)' },
  ],
  compliance: [
    { id: 'intent', type: 'llm', title: '确认业务意图', content: '解析用户输入，确定合规校验目标' },
    { id: 'locate', type: 'llm', title: '定位校验对象', content: '从草稿或在架商品中定位待检套餐' },
    {
      id: 'ontology',
      type: 'ontology',
      title: '合规规则校验',
      content: '执行配置合规规则检查',
    },
    { id: 'reply', type: 'llm', title: '组织校验结论', content: '汇总通过或阻断说明' },
  ],
  'root-cause': [
    { id: 'intent', type: 'llm', title: '确认业务意图', content: '解析用户目标与关注指标' },
    { id: 'locate', type: 'llm', title: '锁定分析对象', content: '定位异动商品与指标快照' },
    { id: 'confirm', type: 'llm', title: '异动确认', content: '对照阈值确认指标异动' },
    { id: 'drill', type: 'llm', title: '多维下钻', content: '按渠道 / 促销 / 竞品 / 行为扫描' },
    {
      id: 'reason',
      type: 'ontology',
      title: '规则推理',
      content: '执行图谱与 SWRL 归因规则',
    },
    { id: 'conclude', type: 'llm', title: '归因结论', content: '汇总主因路径与处置建议' },
  ],
  'risk-audit': [
    { id: 'intent', type: 'llm', title: '确认业务意图', content: '识别为风险稽核' },
    { id: 'load', type: 'llm', title: '加载在架清单', content: '加载在架商品清单' },
    { id: 'match', type: 'llm', title: '匹配风险规则集', content: '匹配适用稽核规则集' },
    {
      id: 'scan',
      type: 'ontology',
      title: '全量扫描打分',
      content: '按规则全量扫描打分',
    },
    { id: 'conclude', type: 'llm', title: '风险与处置建议', content: '输出风险清单与建议' },
  ],
}

function cloneStep(tpl, status = 'pending') {
  return {
    id: tpl.id,
    type: tpl.type || 'llm',
    title: tpl.title || '',
    content: tpl.content || '',
    result: null,
    metadata: { phase: status === 'running' ? 'running' : status, scheduleId: tpl.id },
    details: null,
    elapsed: null,
    ontologyChain: null,
    ontologyPreview: null,
    chainRevealCount: 0,
    status,
    stepStartedAt: status === 'running' ? Date.now() : null,
    timestamp: Date.now(),
  }
}

/** 按场景生成完整调度表（全部 pending，首步 running） */
export function createSchedule(scenario) {
  const key = SCENARIO_SCHEDULES[scenario] ? scenario : 'chat-generate'
  const tpls = SCENARIO_SCHEDULES[key]
  const steps = tpls.map((tpl, i) => cloneStep(tpl, i === 0 ? 'running' : 'pending'))
  return steps
}

export function scenarioLabel(scenario) {
  return SCENARIO_LABELS[scenario] || SCENARIO_LABELS['chat-generate']
}

function findStepIndex(list, idOrIndex) {
  if (typeof idOrIndex === 'number') return idOrIndex
  return list.findIndex((s) => s.id === idOrIndex || s.metadata?.scheduleId === idOrIndex)
}

/** 将指定步骤标为 running（读秒起点） */
export function markRunning(list, idOrIndex) {
  const steps = [...(list || [])]
  const idx = findStepIndex(steps, idOrIndex)
  if (idx < 0) return steps
  const now = Date.now()
  steps[idx] = {
    ...steps[idx],
    status: 'running',
    metadata: { ...(steps[idx].metadata || {}), phase: 'running' },
    stepStartedAt: steps[idx].stepStartedAt || now,
    timestamp: steps[idx].timestamp || now,
  }
  return steps
}

/** 完成一步并可选写入 result；自动将下一步标为 running */
export function completeStep(list, idOrIndex, patch = {}) {
  const steps = [...(list || [])]
  const idx = findStepIndex(steps, idOrIndex)
  if (idx < 0) return steps
  const prev = steps[idx]
  const now = Date.now()
  const started = prev.stepStartedAt || prev.timestamp || now
  const elapsed =
    patch.elapsed != null
      ? patch.elapsed
      : Math.round(((now - started) / 1000) * 1000) / 1000

  steps[idx] = {
    ...prev,
    ...patch,
    status: 'done',
    metadata: { ...(prev.metadata || {}), ...(patch.metadata || {}), phase: 'done' },
    elapsed,
    result: patch.result !== undefined ? patch.result : prev.result,
  }

  const next = steps[idx + 1]
  if (next && next.status === 'pending') {
    steps[idx + 1] = {
      ...next,
      status: 'running',
      metadata: { ...(next.metadata || {}), phase: 'running' },
      stepStartedAt: now,
      timestamp: now,
    }
  }
  return steps
}

/**
 * 用 playbook 结果原地回填同一条调度时间线。
 * 优先按 id 对齐，否则按 index。
 */
export function applyScheduleResults(list, thinkingSteps = []) {
  const steps = [...(list || [])]
  const results = (thinkingSteps || []).map((raw) => {
    if (typeof raw === 'string') {
      return { type: 'llm', content: raw, result: null }
    }
    return raw
  })

  results.forEach((res, i) => {
    let idx = -1
    if (res.id) idx = findStepIndex(steps, res.id)
    if (idx < 0) idx = i
    if (idx < 0 || idx >= steps.length) {
      steps.push({
        id: res.id || `extra_${i}`,
        type: res.type || 'llm',
        title: res.title || (res.type === 'ontology' ? '本体推理' : ''),
        content: res.content || '',
        result: res.result != null ? res.result : null,
        metadata: res.metadata || { phase: 'done' },
        details: res.details || null,
        elapsed: res.elapsed != null ? res.elapsed : null,
        ontologyChain: res.ontologyChain || null,
        ontologyPreview: res.ontologyPreview || null,
        chainRevealCount: 0,
        status: 'done',
        stepStartedAt: Date.now(),
        timestamp: Date.now(),
      })
      return
    }
    const prev = steps[idx]
    steps[idx] = {
      ...prev,
      type: res.type || prev.type,
      title: res.title || prev.title,
      content: res.content || prev.content,
      result: res.result !== undefined ? res.result : prev.result,
      metadata: {
        ...(prev.metadata || {}),
        ...(res.metadata || {}),
        phase: 'done',
        scheduleId: prev.id,
      },
      details: res.details != null ? res.details : prev.details,
      elapsed: res.elapsed != null ? res.elapsed : prev.elapsed,
      ontologyChain: res.ontologyChain != null ? res.ontologyChain : prev.ontologyChain,
      ontologyPreview: res.ontologyPreview != null ? res.ontologyPreview : prev.ontologyPreview,
      status: 'done',
    }
  })

  // 未覆盖到的步骤也收尾，避免卡在 running
  return steps.map((s) =>
    s.status === 'done'
      ? s
      : {
          ...s,
          status: 'done',
          metadata: { ...(s.metadata || {}), phase: 'done' },
        },
  )
}

/** 找当前 running 步骤下标 */
export function runningIndex(list) {
  return (list || []).findIndex((s) => s.status === 'running' || s.metadata?.phase === 'running')
}

/**
 * 在 API 等待期间按模板推进：意图快速完成，中间步骤短暂停顿，卡在本体/主步骤上读秒。
 * @returns {{ stop: Function }} 停止推进（API 返回时调用）
 */
export function startScheduleTicker(msg, { onTick, stepDelay = 420 } = {}) {
  let stopped = false
  let timer = null

  const tick = async () => {
    while (!stopped) {
      const list = msg.reasoning || []
      const ri = runningIndex(list)
      if (ri < 0) break

      const step = list[ri]
      const isBlocking =
        step.type === 'ontology' ||
        step.id === 'ontology' ||
        ri >= list.length - 2

      if (isBlocking) {
        // 主步骤：仅刷新读秒，不自动 complete
        await sleep(1000)
        if (stopped) break
        onTick?.()
        continue
      }

      await sleep(stepDelay)
      if (stopped) break

      const patch = {}
      if (step.id === 'intent') {
        patch.result = scenarioLabel(msg._scenario)
      }
      msg.reasoning = completeStep(msg.reasoning, ri, patch)
      onTick?.()
    }
  }

  timer = Promise.resolve().then(tick)

  return {
    stop() {
      stopped = true
    },
    done: timer,
  }
}

/**
 * 逐步揭示本体链（同一 step 原地更新 chainRevealCount）
 */
export async function revealOntologyChain(msg, stepId, { onTick, delay = 90 } = {}) {
  const list = msg.reasoning || []
  const idx = findStepIndex(list, stepId)
  if (idx < 0) return
  const step = list[idx]
  const chainTotal =
    step.ontologyChain?.hops?.length || step.ontologyChain?.nodes?.length || 0
  if (chainTotal <= 1) {
    step.chainRevealCount = 0
    msg.reasoning = [...list]
    onTick?.()
    return
  }
  for (let n = 1; n <= chainTotal; n++) {
    await sleep(delay)
    const cur = [...(msg.reasoning || [])]
    if (!cur[idx]) break
    cur[idx] = { ...cur[idx], chainRevealCount: n }
    msg.reasoning = cur
    onTick?.()
  }
  const cur = [...(msg.reasoning || [])]
  if (cur[idx]) {
    cur[idx] = { ...cur[idx], chainRevealCount: 0 }
    msg.reasoning = cur
    onTick?.()
  }
}
