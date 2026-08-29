/**
 * 产品配置 / 产商品本体 状态管理
 * 对接后端 product-ontology 推理 API，兼容 DynamicForm formCard
 */
import { ref, reactive, computed } from 'vue'
import { genId } from '../utils/chatUtils.js'
import {
  createEmptyFormData,
  createProductFormSchema,
  createOfferingFormSchema,
  draftToFormData,
} from '../data/productMockData.js'
import {
  checkCompliance,
  copyAsDraft,
  saveConfigDraft,
  listConfigDrafts,
  getConfigDraft,
  deleteConfigDraft,
  submitConfigDraft,
  listOpsAlerts,
  listWorkOrders,
  createWorkOrder,
  evaluateHypothetical,
  getOpsRules,
  getConfigTrace,
  explainConfig,
  getOntologyMeta,
  fetchTemplateSchema,
} from '../services/productOntologyApi.js'
import {
  buildRootCauseOntologyChain,
} from '../services/productOntologyLocal.js'

// ------------------------------------------------------------------
// P1-4 模板渲染 schema 缓存（§11.8）：categoryCode -> { template, schema }
// 按 categoryCode 拉取后端模板驱动表单渲染；接口未就绪/未命中时降级本地 mock schema
// ------------------------------------------------------------------
const templateSchemaCache = new Map()
const templateFetchInflight = new Set()

/** 草稿键 → 模板字段码别名（P1 边界：仅渲染期取值预填，不改报文契约） */
const DRAFT_TO_TEMPLATE_FIELDS = {
  offerName: 'prodPrcName',
  offeringName: 'prodPrcName',
  monthlyFee: 'prcMonthFee',
  fixedFeeAmount: 'fixFee',
  regionScope: 'groupId',
}

/** 异步拉取单个品类模板 schema 入缓存；失败静默（调用方走 mock 降级） */
async function ensureTemplateSchema(categoryCode) {
  if (!categoryCode || templateSchemaCache.has(categoryCode) || templateFetchInflight.has(categoryCode)) {
    return
  }
  templateFetchInflight.add(categoryCode)
  try {
    const result = await fetchTemplateSchema(categoryCode)
    if (result) {
      templateSchemaCache.set(categoryCode, result)
    }
  } finally {
    templateFetchInflight.delete(categoryCode)
  }
}

/** 预取全部品类模板（/meta productTemplates），失败静默 */
async function prefetchTemplateSchemas() {
  try {
    const meta = await getOntologyMeta()
    const templates = Array.isArray(meta?.productTemplates) ? meta.productTemplates : []
    await Promise.all(templates.map((t) => ensureTemplateSchema(t?.category_code)))
  } catch (e) {
    // 模板接口未就绪：降级本地 mock schema
  }
}

/** 用草稿值填充模板 schema：fieldCode 精确匹配 + 别名映射；保留模板默认值与 sections */
function mergeDraftIntoTemplateSchema(cached, draft) {
  const fill = draft.fillSources || {}
  const aliasByTarget = Object.fromEntries(
    Object.entries(DRAFT_TO_TEMPLATE_FIELDS).map(([k, v]) => [v, k]),
  )
  const resolveValue = (fieldCode) => {
    if (draft[fieldCode] != null && draft[fieldCode] !== '') return draft[fieldCode]
    const aliasKey = aliasByTarget[fieldCode]
    if (aliasKey && draft[aliasKey] != null && draft[aliasKey] !== '') return draft[aliasKey]
    return undefined
  }
  return {
    formName: cached.schema.formName,
    formCode: 'offering_config',
    categoryCode: cached.schema.categoryCode,
    messageRootKey: cached.schema.messageRootKey,
    templateVersion: cached.schema.templateVersion,
    sections: cached.schema.sections,
    deriveRules: cached.schema.deriveRules,
    fields: (cached.schema.fields || []).map((field) => {
      const value = resolveValue(field.fieldCode)
      return {
        ...field,
        ...(value !== undefined ? { value } : {}),
        fillSource: fill[field.fieldCode] || '',
      }
    }),
  }
}

export function useProductConfig() {
  // P1-4：预热全部品类模板 schema（fire-and-forget，失败降级 mock）
  prefetchTemplateSchemas()
  const products = ref([])
  const currentProductId = ref(null)
  const formData = reactive(createEmptyFormData())
  const ontologyDraft = ref(null)
  const auditStatus = ref('pending')
  const isModified = ref(false)
  const showProductListPanel = ref(false)
  const showAuditPanel = ref(false)
  const auditResults = ref([])
  const auditPhase = ref('idle')
  const batchItems = ref([])
  const showBatchPanel = ref(false)
  const showRootCausePanel = ref(false)
  const showRiskAuditPanel = ref(false)
  const showMonitorPanel = ref(false)
  const showRulesPanel = ref(false)
  const opsRulesCatalog = ref(null)
  const rulesLoading = ref(false)
  const rootCauseResult = ref(null)
  const riskAuditResult = ref(null)
  const monitorResult = ref(null)
  const monitorWorkOrders = ref([])
  const monitorLoading = ref(false)
  const activeRootCauseRank = ref(1)
  const rootCauseOntologyChain = ref(null)
  /** 配置审计 trace（智查/复制/入库） */
  const lastConfigTraceId = ref(null)
  const configTraceSteps = ref([])
  const configExplainText = ref('')
  const showConfigTracePanel = ref(false)
  /** 会话绑定，用于草稿持久化 */
  const boundSessionId = ref(null)
  const boundUserId = ref('anonymous')
  const compareResult = ref(null)
  const showComparePanel = ref(false)

  /** 可逆操作与执行态（v3.2）：
   * undoStack: [{ id, kind, label, ts, revert }] 已执行、可回退的动作栈
   * actionStates: product.id (或 actId) -> 'suggested'|'executing'|'executed'|'failed'|'reverted'
   */
  const undoStack = ref([])
  const actionStates = reactive({})

  function setActionState(key, state) {
    if (key == null) return
    actionStates[key] = state
  }

  /** 登记一个可回退动作，返回 actionId；revert 为回退回调（应可幂等） */
  function trackUndoable({ kind, label, key, revert }) {
    const id = 'act_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6)
    undoStack.value = [
      { id, kind, label, key, ts: Date.now(), revert: typeof revert === 'function' ? revert : null },
      ...undoStack.value,
    ]
    if (key != null) setActionState(key, 'executed')
    return id
  }

  /** 撤销最近一个或指定动作；成功返回 { success, label } */
  async function undoAction(actionId = null) {
    const entry = actionId
      ? undoStack.value.find((a) => a.id === actionId)
      : undoStack.value[0]
    if (!entry) return { success: false, message: '当前没有可撤销的动作' }
    if (typeof entry.revert !== 'function') {
      return { success: false, message: '该动作暂不支持撤销' }
    }
    undoStack.value = undoStack.value.filter((a) => a.id !== entry.id)
    try {
      await entry.revert()
      if (entry.key != null) setActionState(entry.key, 'reverted')
      return { success: true, id: entry.id, label: entry.label }
    } catch (e) {
      undoStack.value = [entry, ...undoStack.value]
      return { success: false, message: e.message || e, id: entry.id }
    }
  }

  function clearUndoStack() {
    undoStack.value = []
  }

  const currentProduct = computed(() =>
    products.value.find((p) => p.id === currentProductId.value) ?? null,
  )

  function setSessionContext({ sessionId = null, userId = 'anonymous' } = {}) {
    if (sessionId) boundSessionId.value = sessionId
    if (userId) boundUserId.value = userId
  }

  async function persistCurrentDraft(sessionId = boundSessionId.value) {
    const product = currentProduct.value
    if (!product?.ontologyDraft && !product?.data) return null
    try {
      const resp = await saveConfigDraft({
        draft: product.ontologyDraft || product.data || {},
        draftId: product.draftId || null,
        clientId: product.id,
        sessionId: sessionId || boundSessionId.value,
        userId: boundUserId.value,
        compliancePass: !!product.compliancePass,
      })
      if (resp?.success === false) {
        throw new Error(resp.message || '保存失败')
      }
      if (resp?.draftId != null) product.draftId = resp.draftId
      if (resp?.clientId) product.id = product.id || resp.clientId
      product.persisted = true
      return resp
    } catch (e) {
      console.warn('[useProductConfig] persist draft failed:', e.message || e)
      return null
    }
  }

  async function loadPersistedDrafts(sessionId = boundSessionId.value) {
    if (!sessionId) return []
    try {
      const resp = await listConfigDrafts({ sessionId, userId: boundUserId.value })
      const items = resp?.items || []
      if (!items.length) return []
      const restored = []
      for (const item of items) {
        if (!item.draftId) continue
        const full = await getConfigDraft(item.draftId)
        const draft = full?.draft || item.draft || {}
        const product = {
          id: item.clientId || `P${item.draftId}`,
          draftId: item.draftId,
          name: item.offeringName || draft.offeringName || '配置草稿',
          desc: `月费${item.monthlyFee || draft.monthlyFee || '-'} | ${item.status || 'draft'}`,
          status: item.status === 'filing' || item.status === 'submitted' ? 'submitted' : 'draft',
          auditStatus: item.compliancePass ? 'pass' : 'pending',
          compliancePass: !!item.compliancePass,
          issues: [],
          ontologyDraft: draft,
          data: draftToFormData(draft),
          offeringId: item.offeringId,
          workOrderId: item.workOrderId,
          persisted: true,
        }
        restored.push(product)
      }
      const byId = new Map(products.value.map((p) => [p.id, p]))
      for (const p of restored) {
        byId.set(p.id, { ...(byId.get(p.id) || {}), ...p })
      }
      products.value = Array.from(byId.values())
      if (!currentProductId.value && products.value.length) {
        currentProductId.value = products.value[0].id
        syncFormFromProduct(products.value[0])
      }
      return restored
    } catch (e) {
      console.warn('[useProductConfig] load drafts failed:', e.message || e)
      return []
    }
  }

  async function submitCurrentDraft(sessionId = boundSessionId.value) {
    saveDraftLocal()
    const product = currentProduct.value
    if (!product) {
      return { success: false, message: '没有可提交的配置' }
    }
    const resp = await submitConfigDraft({
      draft: product.ontologyDraft || product.data || {},
      draftId: product.draftId || null,
      clientId: product.id,
      sessionId: sessionId || boundSessionId.value,
      userId: boundUserId.value,
    })
    if (resp?.success === false) {
      return resp
    }
    product.status = 'submitted'
    product.auditStatus = 'pass'
    product.compliancePass = true
    product.draftId = resp.draftId || product.draftId
    product.offeringId = resp.offeringId
    product.workOrderId = resp.workOrder?.workOrderId || resp.workOrder?.work_order_id
    lastConfigTraceId.value = resp.trace_id || lastConfigTraceId.value
    // 可逆操作：登记「入库/备案」动作，撤销时回退为草稿（仅供对话内本地回退，不破坏在架数据）
    const snapshotId = product.id
    const actionId = trackUndoable({
      kind: 'submit',
      label: `撤销入库 · ${product.name}`,
      key: snapshotId,
      revert: () => {
        const p = products.value.find((x) => x.id === snapshotId)
        if (!p) return
        setActionState(snapshotId, 'reverted')
        p.status = 'draft'
        p.auditStatus = 'pending'
        p.compliancePass = false
      },
    })
    return { ...resp, actionId }
  }

  function syncFormFromProduct(product) {
    const data = product?.data || createEmptyFormData()
    Object.keys(createEmptyFormData()).forEach((key) => {
      formData[key] = data[key] !== undefined ? data[key] : createEmptyFormData()[key]
    })
    // 同步本体草稿字段到 formData，供画布编辑与稽核
    const draft = product?.ontologyDraft
    if (draft) {
      Object.keys(draft).forEach((key) => {
        formData[key] = draft[key]
      })
      if (draft.fillSources && typeof draft.fillSources === 'object') {
        formData.fillSources = JSON.stringify(draft.fillSources, null, 2)
      }
    }
    ontologyDraft.value = product?.ontologyDraft || null
    isModified.value = false
    auditStatus.value = product?.auditStatus || 'pending'
  }

  function buildProductFormCard(product) {
    const useOntology = !!product.ontologyDraft
    let schema
    if (useOntology) {
      const draft = product.ontologyDraft
      const category = draft.categoryCode || draft.messageRootKey || ''
      const cached = category ? templateSchemaCache.get(category) : null
      if (cached) {
        // P1-4：按 categoryCode 拉取的后端模板 schema 驱动渲染（§11.8）
        schema = mergeDraftIntoTemplateSchema(cached, draft)
      } else {
        // 未命中：异步预热（下次命中），本次降级本地 mock schema
        if (category) ensureTemplateSchema(category)
        schema = createOfferingFormSchema(draft)
      }
    } else {
      schema = createProductFormSchema(product.data)
    }
    return {
      msgId: genId(),
      formId: product.id,
      formName: product.name,
      formCode: useOntology ? 'offering_config' : 'productConfig',
      status: 'filling',
      fieldCount: schema.fields.length,
      createdAt: new Date().toISOString(),
      formSchema: schema,
      formData: useOntology ? { ...product.ontologyDraft } : { ...product.data },
      compliancePass: product.compliancePass,
      issues: product.issues || [],
      inferredFields: product.inferredFields || [],
    }
  }

  function addProductAndActivate(product) {
    products.value.push(product)
    currentProductId.value = product.id
    syncFormFromProduct(product)
    return buildProductFormCard(product)
  }

  function getSkillGuideMessage(type) {
    if (type === 'query') {
      return '好的，进入**智查·历史复用**。让我帮您查询历史商品，您可以快速复制配置。\n\n可输入：商品名称关键词、编码，或「查一下近30天大学生套餐」。'
    }
    if (type === 'file') {
      return (
        '好的，进入**智读·文件配置**。\n\n' +
        '可粘贴或拖入方案文件到输入框，也可直接粘贴方案正文后发送。\n\n' +
        '测试文档：`docs/testdata/智读测试方案_家庭融合.md`\n' +
        '示例话术见左侧「智读·文件配置」快捷入口。'
      )
    }
    if (type === 'ops') {
      return (
        '产商品运营助手已就绪。\n\n' +
        '金句：**本体负责推理，大模型负责表达**。\n\n' +
        '可试：\n' +
        '- 「分析家庭融合畅享128本月收入下滑原因」\n' +
        '- 「筛查所有在架的0元资费风险商品」\n' +
        '- 或点首页「一键体验」自动演示完整闭环'
      )
    }
    if (type === 'compliance') {
      return (
        '好的，进入**智检·合规校验**。\n\n' +
        '支持两类对象：\n' +
        '- **已入库（在架）**：直接说套餐名称/编码，如「校验校园体验流量包0元是否符合在架规则」\n' +
        '- **未入库草稿**：先智聊/智读生成配置后，再说「校验当前配置是否符合在架规则」'
      )
    }
    return (
      '好的，进入**智聊·对话配置**。配置表单会以卡片内联在消息流中，可直接编辑并一键合规。\n\n' +
      '金句：大模型听懂人话，**本体负责填对字段、拦住冲突**。\n\n' +
      '示例：\n' +
      '- 「给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售」\n' +
      '- 「就叫家庭融合畅享158」\n' +
      '- 「再绑一个畅享128主套餐一起卖」（演示互斥拦截）\n' +
      '- 「那不加128了，就单独上158」'
    )
  }

  function detectScenario(text) {
    if (!text) return null
    // 使用说明/勿执行：不做业务场景关键词强制匹配
    if (
      /使用指导|使用说明|操作步骤|怎么用|如何使用|使用手册|只输出使用说明|仅输出使用说明|只要使用说明|不要直接执行|不要执行|勿执行|不要生成配置结果|仅说明|只要说明/.test(
        text
      )
    ) {
      return null
    }
    const t = text.toLowerCase()
    if (/运营监控|告警列表|查看告警|监控看板|异动告警/.test(text)) return 'ops-monitor'
    if (/根因|异动|离网|累计收入|归因|下滑原因|收入下滑/.test(text)) return 'root-cause'
    if (/稽核|零元资费|高风险|优胜劣汰|筛查.*在架|风险商品|下架建议/.test(text)) return 'risk-audit'
    if (/立项|上线门槛|新品.*套餐|能否通过审核|PS_PRODUCT_ONLINE/.test(text)) return 'online-check'
    if (/在售|增长趋势|市场洞察|竞品|增长指标/.test(text)) return 'market-insight'
    if (/方案对比|多方案|对比.*元|资费对比|立项对比|compare_state|哪个方案/.test(text)) {
      return 'compare'
    }
    // 智检·合规校验：按套餐信息校验（已入库/未入库），须在 chat-generate 之前
    if (
      /合规校验|智检|在架规则|是否可上架|校验当前配置|校验.*是否符合|合规检查/.test(text) ||
      /校验.*(套餐|配置|商品|流量包|畅享)/.test(text)
    ) {
      return 'compliance'
    }
    if (/确认.*入库|入库通过|确认通过项|提交备案|发起备案/.test(text)) return 'confirm-batch'
    if (/审计追溯|查看审计|查看校验依据|配置追溯|get_trace/.test(text)) return 'config-trace'
    // 「查一下」是首页/智查示例常用说法，需在 chat-generate 之前命中
    if (
      /查询|查一下|查下|查找|检索|智查|历史商品|复制配置/.test(text) ||
      t.includes('query')
    ) {
      return 'query'
    }
    if (/导入|方案|文档|智读|批量/.test(text)) return 'file-parse'
    if (
      /套餐|校园|大学生|动感地带|流量|月费|配置|融合|家庭|宽带|畅享|不加|单独上|协议期|内部验证/.test(text) ||
      t.includes('5g')
    ) {
      return 'chat-generate'
    }
    return null
  }

  async function prepareProduct(indexOrItem) {
    let offeringId = null
    let label = null
    if (typeof indexOrItem === 'number') {
      return {
        thinkingSteps: ['复制配置需要本体方案编码'],
        content: '请从智查结果卡片点击「复制配置」，或提供 offeringId。',
        formCard: null,
      }
    }
    if (indexOrItem && typeof indexOrItem === 'object') {
      offeringId = indexOrItem.offeringId || indexOrItem.offering_id || indexOrItem.code || indexOrItem.id
      label = indexOrItem.name || indexOrItem.offeringName || indexOrItem.offering_name
    }
    if (!offeringId) return null

    try {
      const result = await copyAsDraft(offeringId, label || null)
      if (result?.success === false) {
        throw new Error(result.message || '复制失败')
      }
      const draft = result.draft || {}
      lastConfigTraceId.value = result.trace_id || null
      const newProduct = {
        id: 'P' + Date.now(),
        name: draft.offeringName || label || '配置草稿',
        code: 'NEW' + Date.now(),
        desc: `基于 ${result.source_offering_name || offeringId} 复制`,
        template: draft.basedOnTemplate,
        status: 'draft',
        auditStatus: result.compliancePass ? 'pass' : 'fail',
        compliancePass: !!result.compliancePass,
        issues: result.issues || [],
        ontologyDraft: draft,
        data: draftToFormData(draft),
        copiedFrom: result.source_offering_id,
        traceId: result.trace_id,
        diffs: result.diffs || [],
      }
      const formCard = addProductAndActivate(newProduct)
      formCard.compliancePass = newProduct.compliancePass
      formCard.issues = newProduct.issues
      showAuditPanel.value = true
      auditResults.value = mapIssuesToAuditResults(result.issues, result.compliancePass)
      auditStatus.value = newProduct.auditStatus
      await persistCurrentDraft(null)
      const passLabel = result.compliancePass ? '✅ 合规通过' : '⚠️ 存在待处理项'
      const diffHint =
        result.diffs?.length > 0
          ? `\n差异字段：${result.diffs
              .slice(0, 6)
              .map((d) => d.field)
              .join('、')}`
          : ''
      return {
        thinkingSteps: [
          `选中历史方案「${result.source_offering_name || offeringId}」`,
          'retrieve_facts → 深拷贝生成草稿',
          'evaluate_policy 执行 R-C* 合规校验',
          '草稿已持久化至 /config/drafts',
          `结果：${passLabel}`,
        ],
        content:
          `已将「${result.source_offering_name || offeringId}」复制为新草稿（标注基于源方案复制），` +
          `配置表单已内联在消息中。${passLabel}。\n\n审计 trace：\`${result.trace_id || '-'}\`${diffHint}\n\n` +
          `规则说明：配置侧 Java R-C*（方案别名 R-CONF-001→R-C09，R-CONF-002→R-C03）。`,
        formCard,
        traceId: result.trace_id,
        nextSteps: ['校验当前配置是否符合在架规则', '查看审计追溯'],
      }
    } catch (e) {
      return {
        thinkingSteps: [`复制配置失败：${e.message || e}`],
        content: `复制配置失败：${e.message || '本体服务不可用'}。请确认历史方案编码有效。`,
        formCard: null,
      }
    }
  }

  async function applyBatchFix(productId, fixKey) {
    const product = products.value.find((p) => p.id === productId)
    if (!product?.ontologyDraft) return null
    const draft = { ...product.ontologyDraft }
    if (fixKey === 'contract12') {
      draft.hasContract = '1'
      draft.contractMonths = 12
      draft.repeatable = 'false'
    } else if (fixKey === 'internal') {
      draft.channelScope = '内部验证'
    } else if (fixKey === 'fee19') {
      draft.monthlyFee = 19
    } else if (fixKey === 'dependBb') {
      draft.dependOn = '宽带主服务'
    }
    const result = await checkCompliance(draft)
    product.ontologyDraft = draft
    product.issues = result.issues || []
    product.compliancePass = !!result.compliancePass
    product.auditStatus = result.compliancePass ? 'pass' : 'fail'
    product.desc = `月费${draft.monthlyFee ?? '-'} | ${result.compliancePass ? '通过' : '待修正'}`
    product.data = draftToFormData(draft)

    const idx = batchItems.value.findIndex((i) => i.productId === productId)
    if (idx >= 0) {
      batchItems.value[idx] = {
        ...batchItems.value[idx],
        draft,
        issues: product.issues,
        compliancePass: product.compliancePass,
        status: product.compliancePass ? '通过' : '待修正',
      }
    }

    if (currentProductId.value === productId) {
      syncFormFromProduct(product)
    }
    return buildProductFormCard(product)
  }

  function createEmptyOfferingCanvas() {
    const draft = {
      offeringName: '',
      offeringType: 'fusion',
      bizScenario: '',
      targetUser: '',
      channelScope: '',
      monthlyFee: '',
      includeVoice: '',
      includeData: '',
      includeBroadband: '',
      fillSources: {},
    }
    const product = {
      id: 'P' + Date.now(),
      name: '商品配置草稿',
      desc: '等待对话填报',
      status: 'draft',
      auditStatus: 'pending',
      compliancePass: false,
      issues: [
        { ruleId: 'R-C06', issueType: '必填缺失', issueLevel: 'MEDIUM', field: 'offeringName', message: '缺少必填字段：商品名称' },
        { ruleId: 'R-C06', issueType: '必填缺失', issueLevel: 'MEDIUM', field: 'monthlyFee', message: '缺少必填字段：月费' },
        { ruleId: 'R-C06', issueType: '必填缺失', issueLevel: 'MEDIUM', field: 'targetUser', message: '缺少必填字段：目标用户' },
        { ruleId: 'R-C06', issueType: '必填缺失', issueLevel: 'MEDIUM', field: 'channelScope', message: '缺少必填字段：销售渠道' },
      ],
      inferredFields: [],
      ontologyDraft: draft,
      data: draftToFormData(draft),
    }
    products.value = [product]
    currentProductId.value = product.id
    ontologyDraft.value = draft
    syncFormFromProduct(product)
    return buildProductFormCard(product)
  }

  /** SSE 完成后灌入右侧根因面板（与 REST 同源结果结构） */
  function applyRootCauseFromSse(root) {
    if (!root || typeof root !== 'object') return false
    if (root.success === false) {
      rootCauseResult.value = null
      showRootCausePanel.value = false
      return false
    }
    rootCauseResult.value = root
    const paths = root.paths || []
    const anomalies = root.anomalies || []
    if (!paths.length && !anomalies.length) {
      showRootCausePanel.value = false
      return true
    }
    rootCauseOntologyChain.value = buildRootCauseOntologyChain(root)
    activeRootCauseRank.value = 1
    showRootCausePanel.value = true
    showRiskAuditPanel.value = false
    showMonitorPanel.value = false
    showRulesPanel.value = false
    return true
  }

  /** SSE 完成后灌入右侧风险稽核面板 */
  function applyRiskAuditFromSse(result) {
    if (!result || typeof result !== 'object') return false
    if (result.success === false) {
      riskAuditResult.value = null
      showRiskAuditPanel.value = false
      return false
    }
    riskAuditResult.value = result
    showRiskAuditPanel.value = true
    showRootCausePanel.value = false
    showMonitorPanel.value = false
    showRulesPanel.value = false
    return true
  }

  /** 打开规则运营面板（REST，不走 SSE） */
  async function openRulesPanel() {
    showRulesPanel.value = true
    showMonitorPanel.value = false
    showRootCausePanel.value = false
    showRiskAuditPanel.value = false
    rulesLoading.value = true
    try {
      const resp = await getOpsRules()
      opsRulesCatalog.value = resp?.data || resp
      return opsRulesCatalog.value
    } finally {
      rulesLoading.value = false
    }
  }

  function applyRulesCatalog(catalog) {
    if (catalog && typeof catalog === 'object') {
      opsRulesCatalog.value = catalog
    }
  }

  /** SSE 完成后灌入右侧运营监控面板（与 REST /ops/alerts、/ops/work-orders 同源） */
  function applyMonitorFromSse(data) {
    if (!data || typeof data !== 'object') return false
    if (data.success === false) {
      monitorResult.value = null
      showMonitorPanel.value = false
      return false
    }
    const alerts = data.alerts && typeof data.alerts === 'object' ? data.alerts : null
    const items = Array.isArray(data.alertItems)
      ? data.alertItems
      : Array.isArray(alerts?.items)
        ? alerts.items
        : []
    monitorResult.value = {
      items,
      total: data.alertCount ?? alerts?.total ?? items.length,
      generatedAt: data.generatedAt || alerts?.generatedAt || new Date().toISOString(),
      highPriorityCount: data.highPriorityCount,
      openWorkOrderCount: data.openWorkOrderCount,
    }
    const woItems = Array.isArray(data.workOrderItems)
      ? data.workOrderItems
      : Array.isArray(data.workOrders?.items)
        ? data.workOrders.items
        : []
    monitorWorkOrders.value = woItems
    showMonitorPanel.value = true
    showRootCausePanel.value = false
    showRiskAuditPanel.value = false
    showRulesPanel.value = false
    return true
  }

  /**
   * 刷新监控面板（REST）。对话主路径已走 SSE product_ops_monitor；
   * silent=true 时只更新右侧面板，不生成剧本回复。
   */
  async function runOpsMonitorFlow(options = {}) {
    const silent = Boolean(options.silent)
    monitorLoading.value = true
    try {
      const [alertsResp, woResp] = await Promise.all([listOpsAlerts(), listWorkOrders()])
      const alerts = alertsResp?.items || alertsResp?.data?.items || alertsResp?.alerts || []
      const pack = alertsResp?.items
        ? alertsResp
        : { items: alerts, generatedAt: alertsResp?.generatedAt || new Date().toISOString(), total: alerts.length }
      monitorResult.value = pack
      monitorWorkOrders.value = woResp?.items || woResp?.data?.items || []
      showMonitorPanel.value = true
      showRootCausePanel.value = false
      showRiskAuditPanel.value = false
      showRulesPanel.value = false
      if (silent) {
        return { ok: true, pack }
      }
      const high = (pack.items || []).filter((a) => a.severity === 'HIGH' || a.type === 'anomaly')
      const lines = (pack.items || [])
        .slice(0, 6)
        .map((a) => `- [${a.tag || a.type}] **${a.offeringName || a.id}**：${a.text}`)
        .join('\n')
      return {
        thinkingSteps: [
          { type: 'llm', content: '识别意图=运营监控，加载异动告警与处置工单' },
          { type: 'ontology', title: '告警事实', content: `从 opsGraph 指标异动生成告警 ${pack.total || alerts.length} 条` },
          { type: 'llm', content: `高优先级 ${high.length} 条，可一键跳转智能归因` },
        ],
        content:
          `### 运营监控告警\n\n` +
          `共 **${pack.total || alerts.length}** 条告警，其中高优先级约 **${high.length}** 条。\n\n` +
          `${lines || '（暂无告警）'}\n\n` +
          '已展示告警清单，可继续「智能归因」下钻根因。',
        showMonitorPanel: true,
        nextSteps: ['智能归因', '打开风险稽核', '刷新告警'],
      }
    } catch (e) {
      return {
        thinkingSteps: ['告警列表加载失败'],
        content: `运营监控加载失败：${e.message}`,
      }
    } finally {
      monitorLoading.value = false
    }
  }

  async function submitWorkOrder(payload = {}) {
    const resp = await createWorkOrder(payload)
    const wo = resp?.workOrder || resp?.data?.workOrder || resp
    // 刷新工单列表
    try {
      const woResp = await listWorkOrders()
      monitorWorkOrders.value = woResp?.items || woResp?.data?.items || []
    } catch {
      /* ignore */
    }
    return wo
  }

  async function runHypotheticalAndOrder({ mode = 'delist', offeringId, changes, autoOrder = false } = {}) {
    const hypo = await evaluateHypothetical({ mode, offeringId, changes })
    const data = hypo?.data || hypo
    if (autoOrder && offeringId) {
      const wo = await submitWorkOrder({
        offeringId,
        source: mode === 'delist' ? 'risk_delist' : 'risk_price',
        title: undefined,
        summary: data?.summary || '',
        actions: [
          mode === 'delist' ? '启动退市流程并做好用户迁转' : '按推演结果调整资费并复核风险',
          ...(data?.impacts || []).slice(0, 2).map((i) => i.conclusion).filter(Boolean),
        ],
        hypoMode: mode,
        impacts: data?.impacts,
      })
      return { hypo: data, workOrder: wo }
    }
    return { hypo: data, workOrder: null }
  }

  async function confirmPassedDrafts() {
    const passed = products.value.filter((p) => p.compliancePass && p.status !== 'submitted')
    if (!passed.length) {
      return {
        thinkingSteps: ['检查商品列表中合规通过且未入库的草稿…', '未找到可入库项'],
        content: '当前没有「合规通过」且未入库的草稿。请先完成智读·文件配置或修正待修正项后重跑。',
        formCard: null,
      }
    }
    const lines = []
    const filed = []
    const submittedIds = []
    for (const p of passed) {
      currentProductId.value = p.id
      syncFormFromProduct(p)
      try {
        const resp = await submitConfigDraft({
          draft: p.ontologyDraft || p.data || {},
          draftId: p.draftId || null,
          clientId: p.id,
          sessionId: boundSessionId.value,
          userId: boundUserId.value,
        })
        if (resp?.success === false) {
          lines.push(`- ${p.name} → 失败：${resp.message || '提交被拒'}`)
          continue
        }
        const offeringId = resp.offeringId || p.offeringId
        const woId = resp.workOrder?.workOrderId || resp.workOrder?.work_order_id || '-'
        p.status = 'submitted'
        p.draftId = resp.draftId || p.draftId
        p.offeringId = offeringId
        p.workOrderId = woId
        lastConfigTraceId.value = resp.trace_id || lastConfigTraceId.value
        filed.push(offeringId)
        submittedIds.push(p.id)
        const bi = batchItems.value.findIndex((i) => i.productId === p.id)
        if (bi >= 0) {
          batchItems.value[bi] = {
            ...batchItems.value[bi],
            draftId: offeringId,
            status: '已备案',
          }
        }
        lines.push(`- ${p.name} → 商品 \`${offeringId}\` · 工单 \`${woId}\``)
      } catch (e) {
        lines.push(`- ${p.name} → 失败：${e.message || e}`)
      }
    }
    showBatchPanel.value = true
    // 可逆操作：登记「批量入库」，撤销时把本次成功项全部回退为草稿（本地回退）
    let batchActionId = null
    if (submittedIds.length) {
      const batchKey = 'batch_' + Date.now()
      batchActionId = trackUndoable({
        kind: 'batch-submit',
        label: `撤销入库 ${submittedIds.length} 条`,
        key: batchKey,
        revert: () => {
          for (const id of submittedIds) {
            const p = products.value.find((x) => x.id === id)
            if (!p) continue
            setActionState(p.id, 'reverted')
            p.status = 'draft'
            p.auditStatus = 'pending'
            p.compliancePass = false
            const bi = batchItems.value.findIndex((i) => i.productId === id)
            if (bi >= 0) batchItems.value[bi] = { ...batchItems.value[bi], status: '通过' }
          }
          setActionState(batchKey, 'reverted')
        },
      })
    }
    return {
      thinkingSteps: [
        '筛选 compliancePass=true 且未入库草稿',
        `提交 ${passed.length} 条：合规 → 沉淀本体 → 资费备案工单`,
        filed.length ? `成功闭环 ${filed.length} 条` : '本批无成功提交',
      ],
      content:
        `已处理 **${passed.length}** 条通过项（提交/备案闭环）：\n${lines.join('\n')}\n\n` +
        '待修正项**不会入库**。成功项可被智查复用，并已生成备案工单。' +
        (lastConfigTraceId.value ? `\n\n审计 trace：\`${lastConfigTraceId.value}\`` : ''),
      formCard: null,
      nextSteps: lastConfigTraceId.value ? ['查看审计追溯'] : undefined,
      traceId: lastConfigTraceId.value,
      _undoable: batchActionId
        ? {
            actionId: batchActionId,
            state: 'executed',
            label: `入库 ${submittedIds.length} 条`,
            undoLabel: `撤销入库 ${submittedIds.length} 条`,
          }
        : null,
    }
  }

  /** 配置审计追溯（对齐方案 get_trace + explain）：拉取链路并生成业务视角说明 */
  async function openConfigTrace(traceId = null) {
    const id = traceId || lastConfigTraceId.value
    if (!id) {
      return {
        thinkingSteps: ['未找到当前会话的配置审计 trace'],
        content:
          '当前会话还没有可追溯的审计记录。请先完成一次**智查复制**、**智读批量入库**或**提交备案**操作，' +
          '系统会为每次配置动作生成审计 trace，届时可回溯完整链路。',
        formCard: null,
      }
    }
    try {
      const [trace, explain] = await Promise.all([
        getConfigTrace(id),
        explainConfig(id, 'business'),
      ])
      const steps = Array.isArray(trace?.steps) ? trace.steps : []
      configTraceSteps.value = steps
      configExplainText.value = explain?.explanation || ''
      lastConfigTraceId.value = id
      showConfigTracePanel.value = true

      if (!steps.length) {
        return {
          thinkingSteps: [`get_trace(${id}) 返回空链路`],
          content: `未找到 trace \`${id}\` 的审计记录（服务重启后内存 trace 会清空）。请重新执行一次配置动作后再查看追溯。`,
          formCard: null,
        }
      }

      const rows = steps
        .map((s, i) => {
          const detail = Object.entries(s)
            .filter(([k]) => !['step', 'timestamp'].includes(k))
            .map(([k, v]) => `${traceKeyZh(k)}=${formatTraceValue(v)}`)
            .join(' · ')
          const ts = s.timestamp ? String(s.timestamp).replace('T', ' ').slice(0, 19) : ''
          return `| ${i + 1} | \`${s.step || '?'}\` | ${detail || '-'} | ${ts} |`
        })
        .join('\n')
      const audience = explain?.audience === 'technical' ? '技术' : '业务'
      return {
        thinkingSteps: [
          `定位审计 trace \`${id}\``,
          `get_trace 拉取 ${steps.length} 步审计链路`,
          'explain 生成业务视角审计说明',
        ],
        content:
          `### 配置审计追溯 \`${id}\`\n\n` +
          `| # | 步骤 | 关键信息 | 时间 |\n|---|------|---------|------|\n${rows}\n\n` +
          `**审计说明（${audience}视角）**\n\n${explain?.explanation || '（服务未返回说明）'}`,
        formCard: null,
        traceId: id,
        nextSteps: ['查一下近30天大学生套餐'],
      }
    } catch (e) {
      return {
        thinkingSteps: [`审计追溯失败：${e.message || e}`],
        content: `审计追溯加载失败：${e.message || '本体服务不可用'}`,
        formCard: null,
      }
    }
  }

  function traceKeyZh(key) {
    const map = {
      offering_id: '商品',
      copied_from: '复制自',
      compliance_pass: '合规',
      applied_rules: '规则',
      query: '查询',
      file_name: '文档',
      engine: '解析引擎',
      total: '总数',
      passed: '通过',
      uri: '本体URI',
      message_root_key: '报文根键',
      step: '步骤',
      timestamp: '时间',
    }
    return map[key] || key
  }

  function formatTraceValue(v) {
    if (v === null || v === undefined) return '-'
    if (Array.isArray(v)) return v.join('、')
    return String(v)
  }

  function selectProduct(id) {
    const product = products.value.find((p) => p.id === id)
    if (!product) return null
    currentProductId.value = id
    syncFormFromProduct(product)
    return buildProductFormCard(product)
  }

  function copyProduct(id) {
    const product = products.value.find((p) => p.id === id)
    if (!product) return null
    const newProduct = {
      ...JSON.parse(JSON.stringify(product)),
      id: 'P' + Date.now(),
      name: product.name + ' (副本)',
      status: 'draft',
      auditStatus: 'pending',
    }
    products.value.push(newProduct)
    return newProduct
  }

  function deleteProduct(id, { undoable = true } = {}) {
    const target = products.value.find((p) => p.id === id)
    if (target?.draftId) {
      deleteConfigDraft(target.draftId).catch((e) =>
        console.warn('[useProductConfig] delete draft failed:', e.message || e),
      )
    }
    const snapshot = target ? JSON.parse(JSON.stringify(target)) : null
    products.value = products.value.filter((p) => p.id !== id)
    if (undoable && snapshot) {
      // 可逆操作：登记「删除草稿」，撤销即恢复该条草稿
      const pid = snapshot.id
      trackUndoable({
        kind: 'delete',
        label: `恢复草稿 · ${snapshot.name}`,
        key: pid,
        revert: () => {
          if (!products.value.find((p) => p.id === pid)) {
            products.value.push(JSON.parse(JSON.stringify(snapshot)))
          }
          setActionState(pid, 'reverted')
        },
      })
    }
    if (currentProductId.value === id) {
      const first = products.value[0]
      currentProductId.value = first?.id ?? null
      if (first) {
        syncFormFromProduct(first)
        return buildProductFormCard(first)
      }
      ontologyDraft.value = null
    }
    return null
  }

  function saveDraftLocal() {
    const product = currentProduct.value
    if (!product) return
    if (product.ontologyDraft) {
      const next = { ...product.ontologyDraft }
      Object.keys(formData).forEach((k) => {
        if (formData[k] !== undefined && formData[k] !== '') {
          next[k] = formData[k]
        }
      })
      if (typeof next.fillSources === 'string') {
        try {
          next.fillSources = JSON.parse(next.fillSources)
        } catch {
          /* keep string */
        }
      }
      product.ontologyDraft = next
      product.name = next.offeringName || product.name
      ontologyDraft.value = next
    }
    product.data = JSON.parse(JSON.stringify(formData))
    product.name = formData.prodPrcName || formData.offeringName || product.name
    product.auditStatus = product.compliancePass ? product.auditStatus : 'pending'
    auditStatus.value = product.auditStatus || 'pending'
    isModified.value = false
  }

  function saveDraft() {
    saveDraftLocal()
    // 异步落库，不阻塞 UI
    persistCurrentDraft()
  }

  function mapIssuesToAuditResults(issues, pass) {
    if (!issues?.length && pass) {
      return [
        { type: 'success', title: '合规通过 (R-C08)', desc: '无 HIGH 问题且必填齐全，允许提交配置草稿' },
      ]
    }
    return (issues || []).map((i) => {
      const alias = i.proposalAlias ? ` / ${i.proposalAlias}` : ''
      return {
        type: i.issueLevel === 'HIGH' ? 'error' : i.issueLevel === 'MEDIUM' ? 'warning' : 'success',
        title: `${i.ruleId}${alias} · ${i.issueType}`,
        desc: i.message + (i.evidence?.length ? ` | 证据：${i.evidence.join('；')}` : ''),
      }
    })
  }

  async function runAudit() {
    saveDraftLocal()
    const product = currentProduct.value
    const draft = product?.ontologyDraft || {
      offeringName: formData.prodPrcName || formData.offeringName,
      monthlyFee: formData.monthlyFee,
      targetUser: formData.targetUser || '个人',
      channelScope: formData.channelScope || '全渠道',
      includeData: formData.flowAmount ? `${formData.flowAmount}${formData.flowUnit || 'GB'}` : '',
      includeVoice: formData.voiceAmount ? `${formData.voiceAmount}分钟` : '',
      bizScenario: formData.bizScenario,
      offeringType: formData.offeringType || 'main_pkg',
      hasContract: formData.hasContract || '0',
      mutexGroup: formData.mutexGroup || 'MAIN_PKG',
      bindExistingMainPkg: formData.bindExistingMainPkg,
      discountPercent: formData.discountPercent,
      repeatable: formData.repeatable,
      dependOn: formData.dependOn,
      oneTimeFee: formData.oneTimeFee || 0,
    }

    try {
      const result = await checkCompliance(draft)
      if (result?.success === false) {
        throw new Error(result.message || '合规校验失败')
      }
      const results = mapIssuesToAuditResults(result.issues, result.compliancePass)
      const hasError = !result.compliancePass
      auditStatus.value = hasError ? 'fail' : 'pass'
      if (product) {
        product.auditStatus = auditStatus.value
        product.compliancePass = result.compliancePass
        product.issues = result.issues || []
      }
      auditResults.value = results
      await persistCurrentDraft()
      return { results, hasError }
    } catch (e) {
      const results = [{
        type: 'error',
        title: '智检·合规校验失败',
        desc: e.message || '本体服务不可用',
      }]
      auditStatus.value = 'fail'
      auditResults.value = results
      if (product) {
        product.auditStatus = 'fail'
        product.compliancePass = false
      }
      return { results, hasError: true }
    }
  }

  function updateFormField(fieldCode, value) {
    formData[fieldCode] = value
    isModified.value = true
    const product = currentProduct.value
    if (product) {
      product.data = product.data || {}
      product.data[fieldCode] = value
      if (product.ontologyDraft) {
        product.ontologyDraft[fieldCode] = value
        ontologyDraft.value = product.ontologyDraft
      }
    }
  }

  function resetState() {
    products.value = []
    currentProductId.value = null
    ontologyDraft.value = null
    auditStatus.value = 'pending'
    auditResults.value = []
    auditPhase.value = 'idle'
    showProductListPanel.value = false
    showAuditPanel.value = false
    batchItems.value = []
    showBatchPanel.value = false
    showRootCausePanel.value = false
    showRiskAuditPanel.value = false
    showMonitorPanel.value = false
    showRulesPanel.value = false
    rootCauseResult.value = null
    riskAuditResult.value = null
    monitorResult.value = null
    monitorWorkOrders.value = []
    opsRulesCatalog.value = null
    rootCauseOntologyChain.value = null
    activeRootCauseRank.value = 1
    lastConfigTraceId.value = null
    configTraceSteps.value = []
    configExplainText.value = ''
    showConfigTracePanel.value = false
    compareResult.value = null
    showComparePanel.value = false
    undoStack.value = []
    Object.keys(actionStates).forEach((k) => delete actionStates[k])
    Object.assign(formData, createEmptyFormData())
  }

  return {
    products,
    currentProductId,
    currentProduct,
    formData,
    ontologyDraft,
    auditStatus,
    isModified,
    showProductListPanel,
    showAuditPanel,
    auditResults,
    auditPhase,
    batchItems,
    showBatchPanel,
    showRootCausePanel,
    showRiskAuditPanel,
    showMonitorPanel,
    showRulesPanel,
    opsRulesCatalog,
    rulesLoading,
    rootCauseResult,
    riskAuditResult,
    monitorResult,
    monitorWorkOrders,
    monitorLoading,
    rootCauseOntologyChain,
    activeRootCauseRank,
    lastConfigTraceId,
    configTraceSteps,
    configExplainText,
    showConfigTracePanel,
    compareResult,
    showComparePanel,
    boundSessionId,
    undoStack,
    actionStates,
    setActionState,
    trackUndoable,
    undoAction,
    clearUndoStack,
    getSkillGuideMessage,
    detectScenario,
    setSessionContext,
    persistCurrentDraft,
    loadPersistedDrafts,
    submitCurrentDraft,
    prepareProduct,
    confirmPassedDrafts,
    openConfigTrace,
    applyRootCauseFromSse,
    applyRiskAuditFromSse,
    applyMonitorFromSse,
    openRulesPanel,
    applyRulesCatalog,
    runOpsMonitorFlow,
    submitWorkOrder,
    runHypotheticalAndOrder,
    selectProduct,
    copyProduct,
    deleteProduct,
    saveDraft,
    saveDraftLocal,
    runAudit,
    updateFormField,
    buildProductFormCard,
    syncFormFromProduct,
    applyBatchFix,
    createEmptyOfferingCanvas,
    resetState,
  }
}
