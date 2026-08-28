/**
 * 翻译层业务可读词典：把内部意图码 / 工具码 / 参数键统一映射为业务人员能看懂的中文。
 * 供 QueryPlanCard / ToolResultPanel / ThinkingProcessPanel 等面板复用，避免各处硬编码。
 */
import { entityCn } from './ontologyLabels.js'

const INTENT_LABELS = {
  SPARQL_QUERY: '数据查询',
  SWRL_INFER: '推理分析',
  RULE_EXPLAIN: '规则解释',
  ONTOLOGY_EXPLAIN: '概念解释',
  CLARIFY: '待补充信息',
  REUSE_EVIDENCE: '证据复用',
  CHAT: '通用对话',
  // 新版业务意图命名（历史兼容）
  product_ops_query: '数据查询',
  product_ops_reason: '异动归因',
  product_ops_policy: '风险稽核',
  product_ops_monitor: '运营监控',
  product_ops_compare: '对比分析',
  // 产商品研发助手意图
  RD_CONFIG_CHAT: '对话配置',
  RD_FILE_PARSE: '方案解析',
  RD_COMPLIANCE: '合规校验',
  RD_CONFIG_DISCOVER: '配置查询',
  RD_SCHEME_COMPARE: '方案对比',
}

const TOOL_LABELS = {
  sparql_query: '数据查询',
  swrl_root_cause: '异动归因',
  swrl_risk_audit: '风险稽核',
  rule_explain: '规则解释',
  ontology_explain: '概念解释',
  // 产商品研发助手工具
  rd_config_chat: '对话配置生成',
  rd_file_parse: '方案文档解析',
  rd_compliance: '合规校验',
  rd_config_discover: '历史配置查询',
  rd_scheme_compare: '多方案对比',
}

const PARAM_LABELS = {
  question: '查询内容',
  offering: '分析对象',
  offeringIds: '商品范围',
  time: '时间范围',
  rule_set: '规则集',
  dimension: '分析维度',
  metric: '指标',
  maxEntities: '最多实体数',
  ruleId: '规则编号',
  concept: '本体概念',
  intent_type: '业务意图',
  action: '执行动作',
  // 产商品研发助手参数
  text: '配置需求',
  draft: '配置草稿',
  offering_id: '商品编码',
  file_id: '文档标识',
  file_name: '文档名称',
  document_text: '文档内容',
  patches: '候选方案',
  compliance_pass: '是否通过',
  issues: '风险明细',
  comparisons: '候选方案',
  recommended: '推荐方案',
  items: '草稿清单',
}

/** 参数名 → 业务中文标签 */
export function paramLabel(key) {
  if (key == null || key === '') return key
  const k = String(key)
  return PARAM_LABELS[k] || k
}

/** 参数值 → 业务可读展示（offering 编码转中文名，已是中文则原样） */
export function paramValue(key, val) {
  if (val == null || val === '') return '—'
  if (typeof val === 'object') return JSON.stringify(val)
  const k = String(key)
  const s = String(val)
  if (k === 'offering' && /^OF-/.test(s)) {
    return entityCn(s) || s
  }
  if (k === 'intent_type') {
    const cn = intentLabel(s)
    return cn && cn !== s ? cn : s
  }
  if (k === 'action') {
    const cn = actionValue(s)
    return cn !== s ? cn : s
  }
  return /[\u4e00-\u9fff]/.test(s) ? s : s
}

const ACTION_LABELS = {
  query: '查询',
  root_cause: '异动归因',
  risk_audit: '风险稽核',
  online_check: '在架检查',
  ops_monitor: '运营监控',
  compare: '对比分析',
  // 产商品研发助手动作（params.action 实际取值为 generate/parse/compliance/discover）
  generate: '配置生成',
  parse: '方案解析',
  compliance: '合规校验',
  discover: '配置查询',
  config_chat: '对话配置',
  file_parse: '方案解析',
  config_discover: '配置查询',
  scheme_compare: '方案对比',
}

function actionValue(key) {
  if (key == null || key === '') return ''
  const k = String(key)
  return ACTION_LABELS[k] || k
}

/** 工具内部码 → 业务中文；已含中文/无法识别时原样返回 */
export function toolLabel(name) {
  if (name == null || name === '') return ''
  const key = String(name)
  if (TOOL_LABELS[key]) return TOOL_LABELS[key]
  return /[\u4e00-\u9fff]/.test(key) ? key : key
}

/** 意图内部码 → 业务中文；支持大小写回退（params.intent_type 为小写归一化码），已含中文/无法识别时原样返回 */
export function intentLabel(intent) {
  if (intent == null || intent === '') return '—'
  const key = String(intent)
  if (INTENT_LABELS[key]) return INTENT_LABELS[key]
  const upper = key.toUpperCase()
  if (INTENT_LABELS[upper]) return INTENT_LABELS[upper]
  return /[\u4e00-\u9fff]/.test(key) ? key : key
}

/**
 * 结构化工具输出 → 业务可读摘要文本（供思考过程步骤「输出」展示）。
 * @param {string} toolName 工具内部码
 * @param {Object} output  后端 buildToolOutput 下发的结构化输出
 */
export function toolOutputSummary(toolName, output) {
  if (!output || typeof output !== 'object') return ''
  if (output.summary) return String(output.summary)
  return ''
}

/**
 * 结构化工具输出 → 业务元数据标签（供思考过程步骤渲染为「输出：…」后的徽标）。
 * 返回 [{ key, label, value }]。
 */
export function toolOutputEntries(toolName, output) {
  if (!output || typeof output !== 'object') return []
  const entries = []
  if (output.total != null) {
    entries.push({ key: 'result', label: '风险命中', value: output.total + ' 条' })
  }
  if (output.scannedCount != null) {
    entries.push({ key: 'result', label: '扫描', value: output.scannedCount + ' 条' })
  }
  if (output.highCount != null) {
    entries.push({ key: 'verdict', label: '高风险', value: output.highCount + ' 条' })
  }
  if (output.mediumCount != null) {
    entries.push({ key: 'verdict', label: '中风险', value: output.mediumCount + ' 条' })
  }
  if (output.suggestDelistCount != null) {
    entries.push({ key: 'verdict', label: '建议下架', value: output.suggestDelistCount + ' 条' })
  }
  if (output.offeringName != null && output.offeringName !== '') {
    entries.push({ key: 'target', label: '分析对象', value: String(output.offeringName) })
  }
  if (output.pathCount != null) {
    entries.push({ key: 'result', label: '归因路径', value: output.pathCount + ' 条' })
  }
  if (output.remark != null && output.remark !== '') {
    entries.push({ key: 'verdict', label: '说明', value: String(output.remark) })
  }
  // ---- 产商品研发助手工具输出 ----
  if (toolName === 'rd_compliance' && output.compliance_pass != null) {
    entries.push({
      key: 'verdict',
      label: '合规判定',
      value: output.compliance_pass === true ? '通过' : '未通过',
    })
  }
  if (Array.isArray(output.issues) && (toolName === 'rd_compliance')) {
    entries.push({ key: 'result', label: '风险明细', value: output.issues.length + ' 项' })
  }
  if (Array.isArray(output.items) && toolName === 'rd_file_parse') {
    entries.push({ key: 'result', label: '草稿清单', value: output.items.length + ' 条' })
  }
  if (Array.isArray(output.comparisons) && toolName === 'rd_scheme_compare') {
    entries.push({ key: 'result', label: '候选方案', value: output.comparisons.length + ' 套' })
  }
  if (output.recommended && typeof output.recommended === 'object' && toolName === 'rd_scheme_compare') {
    const label = output.recommended.label || output.recommended.offeringName || ''
    if (label) entries.push({ key: 'target', label: '推荐方案', value: String(label) })
  }
  if (output.draft && typeof output.draft === 'object') {
    const draftName = output.draft.offerName || output.draft.offeringName
    if (draftName) entries.push({ key: 'target', label: '配置草稿', value: String(draftName) })
  }
  return entries
}
