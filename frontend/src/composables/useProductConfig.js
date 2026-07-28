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
  chatConfigure,
  checkCompliance,
  batchFromDocument,
  batchFromUpload,
  batchFromUploadedFile,
  uploadConfigFile,
  discoverConfigs,
  copyAsDraft,
  saveConfigDraft,
  listConfigDrafts,
  getConfigDraft,
  deleteConfigDraft,
  submitConfigDraft,
  compareConfigSchemes,
  getConfigTrace,
  explainConfig,
  analyzeRootCause,
  auditRisks,
  updateRiskRules,
  listOpsAlerts,
  listWorkOrders,
  createWorkOrder,
  evaluateHypothetical,
  getOpsRules,
} from '../services/productOntologyApi.js'
import {
  nextStepHints,
  buildOntologyChain,
  buildBatchOntologyChain,
  buildOntologyPreview,
  buildBatchOntologyPreview,
  buildRootCauseOntologyChain,
  buildRootCauseOntologyPreview,
  buildRiskAuditOntologyChain,
  buildRiskAuditOntologyPreview,
} from '../services/productOntologyLocal.js'
import { classCn, formatRule, formatWeight } from '../utils/ontologyLabels.js'

export function useProductConfig() {
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
    return resp
  }

  async function runCompareSchemes(text = '') {
    const draft = ontologyDraft.value || currentProduct.value?.ontologyDraft || null
    try {
      const result = await compareConfigSchemes({ draft, text: text || null })
      if (result?.success === false) {
        throw new Error(result.message || '对比失败')
      }
      compareResult.value = result
      showComparePanel.value = true
      lastConfigTraceId.value = result.trace_id || lastConfigTraceId.value
      const lines = (result.comparisons || []).map(
        (c) =>
          `- **${c.label}**：月费 ${c.monthlyFee} · 合规=${c.compliancePass ? '通过' : '未通过'} · ` +
          `预估年营收 ${c.estimatedAnnualRevenue} · 转化率 ${c.conversionRate}`,
      )
      const rec = result.recommended || {}
      return {
        thinkingSteps: [
          { id: 'intent', type: 'llm', title: '确认业务意图', content: '识别多方案对比意图', result: '多方案对比' },
          {
            id: 'extract',
            type: 'llm',
            title: '抽取方案假设',
            content: '准备定价 / 立项对比维度',
            result: `准备 ${result.comparisons?.length || 0} 套方案假设`,
          },
          {
            id: 'snapshot',
            type: 'llm',
            title: '构建事实快照',
            content: '构建对比用事实快照',
            result: result.trace_id ? `快照 ${result.trace_id}` : '已构建事实快照',
          },
          {
            id: 'evaluate',
            type: 'ontology',
            title: '合规与收益评估',
            content: `对比 ${result.comparisons?.length || 0} 套方案并执行合规`,
            result: rec.label || `已评估 ${result.comparisons?.length || 0} 套`,
          },
          { id: 'conclude', type: 'llm', title: '推荐结论', content: '生成可解释推荐结论', result: rec.label || '已生成说明' },
        ],
        content:
          `### 多方案对比结果\n\n${lines.join('\n')}\n\n` +
          (rec.label
            ? `**推荐**：${rec.label}` +
              (rec.compliancePass ? '（合规通过且预期收益更优）' : '（需先修正合规项）')
            : '') +
          `\n\n${result.explanation || ''}` +
          (result.trace_id ? `\n\n审计 trace：\`${result.trace_id}\`` : ''),
        formCard: null,
        compareResult: result,
        showComparePanel: true,
        nextSteps: rec.compliancePass
          ? ['按推荐方案更新当前草稿', '校验当前配置是否符合在架规则']
          : ['修正合规项后再对比', '给家庭用户做一个融合套餐，月费158'],
      }
    } catch (e) {
      return {
        thinkingSteps: ['多方案对比调用失败'],
        content: `多方案对比失败：${e.message || '服务异常'}`,
        formCard: null,
      }
    }
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
    const schema = useOntology
      ? createOfferingFormSchema(product.ontologyDraft)
      : createProductFormSchema(product.data)
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
      '好的，进入**智聊·对话配置**。右侧已打开空白配置画布与合规面板。\n\n' +
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

  async function simulateQuery(keyword) {
    const q = keyword || '近30天大学生套餐'
    const thinkingSteps = [
      `识别查询意图：检索历史商品配置（nl_discover_and_retrieve）`,
      `解析关键词：「${q}」`,
      `调用本体事实图 /config/discover…`,
    ]
    try {
      const result = await discoverConfigs(q, 20)
      if (result?.success === false) {
        throw new Error(result.message || '智查失败')
      }
      const items = result.items || []
      lastConfigTraceId.value = result.trace_id || null
      thinkingSteps.push(`命中 ${items.length} 条可复制配置（trace: ${result.trace_id || '-'}）`)
      const content =
        items.length > 0
          ? `已从本体事实图找到 **${items.length}** 个相关历史方案，可点击「复制配置」一键开稿并自动合规校验。\n\n` +
            `审计 trace：\`${result.trace_id || '-'}\``
          : `未命中「${q}」相关历史方案。可换关键词如「校园」「家庭融合」「59」。`
      return {
        thinkingSteps,
        content,
        queryResults: items,
        formCard: null,
        nextSteps: items.length
          ? ['点击上方卡片复制配置', '校验当前配置是否符合在架规则']
          : ['查一下校园套餐', '给家庭用户做一个融合套餐，月费158'],
        traceId: result.trace_id,
      }
    } catch (e) {
      thinkingSteps.push(`本体智查失败：${e.message || e}`)
      return {
        thinkingSteps,
        content: `本体智查失败：${e.message || '服务异常'}。请确认后端 /config/discover 可用后重试。`,
        queryResults: [],
        formCard: null,
      }
    }
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
          `右侧打开配置画布。${passLabel}。\n\n审计 trace：\`${result.trace_id || '-'}\`${diffHint}\n\n` +
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

  function hasUploadAttachments(attachments = []) {
    return (attachments || []).some(
      (a) => a?.file instanceof Blob || a?.type === 'file' || (a?.name && a?.size != null),
    )
  }

  /** 判断文本是否像可抽取的方案正文（含资费/资源等要素），而非「请按文档生成」类操作话术 */
  function looksLikePlanContent(text = '') {
    const t = String(text || '').trim()
    if (!t) return false
    const signals = [
      /月费|资费|定价|固定费/,
      /\d+\s*元/,
      /\d+\s*GB|\d+\s*G\b|流量/,
      /分钟|语音|通话/,
      /宽带|\d+\s*M(?:bps)?/i,
      /套餐[A-Za-z0-9甲乙丙丁一二三四五六七八九十]|套餐名称|商品名称|offering/i,
      /目标客群|客群|渠道|合约|订购|互斥|依赖/,
    ]
    const hits = signals.filter((re) => re.test(t)).length
    if (hits >= 2) return true
    return t.length >= 80 && hits >= 1
  }

  function deriveDocMeta(documentText = '', attachments = []) {
    const files = (attachments || []).filter((a) => a?.type === 'file' || a?.file || a?.name)
    if (files.length) {
      const first = files[0]
      const totalSize = files.reduce((sum, f) => sum + (Number(f.size) || 0), 0)
      return {
        fileName: first.name || '上传方案文档',
        fileSize: totalSize || Math.max(1, new Blob([documentText || '']).size),
      }
    }
    const text = String(documentText || '').trim()
    const titleMatch = text.match(/[《「"]([^》」"]{2,40})[》」"]/)
    // 粘贴正文：用文档标题或中性名，避免把操作话术切片成「xxx.txt」冒充已上传文件
    const fileName = titleMatch?.[1]
      ? `${titleMatch[1]}.txt`
      : '粘贴方案正文.txt'
    return {
      fileName: fileName.endsWith('.txt') || fileName.endsWith('.md') ? fileName : `${fileName}.txt`,
      fileSize: Math.max(1, new Blob([text]).size),
    }
  }

  /** 需走后端 ConfigDocumentParser 的附件扩展名 */
  function needsServerParse(name = '') {
    return /\.(docx|pdf|xlsx|xlsm|csv|md|txt)$/i.test(name)
  }

  async function readAttachmentTexts(attachments = []) {
    const files = (attachments || []).filter((a) => a?.file instanceof Blob)
    if (!files.length) return ''
    const parts = []
    for (const a of files) {
      // 已上传或需服务端解析的文档，不在前端 Blob.text()
      if (a.fileId || needsServerParse(a.name || a.file?.name || '')) {
        continue
      }
      try {
        const content = await a.file.text()
        parts.push(`【${a.name || '附件'}】\n${content}`)
      } catch {
        parts.push(`【${a.name || '附件'}】（无法读取文本内容，已保留文件名）`)
      }
    }
    return parts.join('\n\n')
  }

  async function simulateFileParse(documentText = '', attachments = []) {
    try {
      // 优先：选择时已上传成功的文件，按 fileId 映射（不再二次上传原文）
      const uploadedFiles = (attachments || []).filter(
        (a) => a?.fileId && a.uploadStatus === 'success' && a.type !== 'image',
      )
      if (uploadedFiles.length) {
        const batches = []
        for (const a of uploadedFiles) {
          const batch = await batchFromUploadedFile(a.fileId, a.name || a.fileName)
          if (batch?.success === false) {
            throw new Error(batch.message || `解析失败：${a.name}`)
          }
          batches.push({
            batch,
            fileName: a.name || a.fileName || 'upload.bin',
            fileSize: a.size || 0,
          })
        }
        if (batches.length === 1) {
          const playbook = buildBatchPlaybook(
            batches[0].batch,
            batches[0].fileName,
            batches[0].fileSize,
          )
          lastConfigTraceId.value = batches[0].batch.trace_id || null
          return {
            ...playbook,
            thinkingSteps: [
              `读取已上传文件：${batches[0].fileName}`,
              `解析文档引擎：${batches[0].batch.parseEngine || 'document'}`,
              ...(playbook.thinkingSteps || []),
            ],
          }
        }
        const merged = {
          success: true,
          total: 0,
          passedCount: 0,
          pendingCount: 0,
          items: [],
          confirmableDrafts: [],
          extractEngine: 'multi-upload',
        }
        for (const b of batches) {
          merged.total += b.batch.total || 0
          merged.passedCount += b.batch.passedCount || 0
          merged.pendingCount += b.batch.pendingCount || 0
          merged.items.push(...(b.batch.items || []))
          merged.confirmableDrafts.push(...(b.batch.confirmableDrafts || []))
        }
        return buildBatchPlaybook(
          merged,
          `${batches.length}份文档`,
          batches.reduce((s, b) => s + b.fileSize, 0),
        )
      }

      // 兼容：仍持有本地 File 且未预上传时，走一步上传+映射
      const localFiles = (attachments || []).filter(
        (a) => a?.file instanceof Blob && needsServerParse(a.name || a.file?.name || ''),
      )
      if (localFiles.length) {
        const batches = []
        for (const a of localFiles) {
          const batch = await batchFromUpload(a.file)
          if (batch?.success === false) {
            throw new Error(batch.message || `解析失败：${a.name}`)
          }
          batches.push({ batch, fileName: a.name || 'upload.bin', fileSize: a.size || a.file.size || 0 })
        }
        if (batches.length === 1) {
          const playbook = buildBatchPlaybook(
            batches[0].batch,
            batches[0].fileName,
            batches[0].fileSize,
          )
          lastConfigTraceId.value = batches[0].batch.trace_id || null
          return {
            ...playbook,
            thinkingSteps: [
              `解析文档引擎：${batches[0].batch.parseEngine || 'document'}`,
              ...(playbook.thinkingSteps || []),
            ],
          }
        }
        const merged = {
          success: true,
          total: 0,
          passedCount: 0,
          pendingCount: 0,
          items: [],
          confirmableDrafts: [],
          extractEngine: 'multi-upload',
        }
        for (const b of batches) {
          merged.total += b.batch.total || 0
          merged.passedCount += b.batch.passedCount || 0
          merged.pendingCount += b.batch.pendingCount || 0
          merged.items.push(...(b.batch.items || []))
          merged.confirmableDrafts.push(...(b.batch.confirmableDrafts || []))
        }
        return buildBatchPlaybook(merged, `${batches.length}份文档`, batches.reduce((s, b) => s + b.fileSize, 0))
      }

      const attachmentText = await readAttachmentTexts(attachments)
      const userText = String(documentText || '').trim()
      const mergedText = [userText, attachmentText].filter(Boolean).join('\n\n')
      if (!mergedText) {
        return {
          thinkingSteps: ['未收到可解析的方案内容'],
          content: '请粘贴方案正文，或上传 .md/.txt/.csv/.docx/.pdf/.xlsx 方案文档后再试。',
          formCard: null,
          nextSteps: ['演示：导入家庭融合方案并生成配置草稿'],
          replaceProgress: true,
        }
      }
      // 无真实附件、且输入只是操作指引时，不伪造「已上传 xxx.txt」
      if (!hasUploadAttachments(attachments) && !attachmentText && !looksLikePlanContent(userText)) {
        return {
          thinkingSteps: ['未检测到已上传文档或可解析的方案正文'],
          content:
            '当前消息未包含方案正文，也未上传文档，无法执行智读映射。\n\n' +
            '请任选一种方式后重试：\n' +
            '1. **粘贴**方案中的套餐段落（含名称、月费、流量/语音、客群、渠道等）\n' +
            '2. **上传** Word / PDF / Excel / Markdown / 文本方案文件\n' +
            '3. 点击下方演示话术，填入样例方案后再发送',
          formCard: null,
          nextSteps: ['演示：导入家庭融合方案并生成配置草稿'],
          replaceProgress: true,
        }
      }
      const { fileName, fileSize } = deriveDocMeta(mergedText, attachments)
      const batch = await batchFromDocument(mergedText)
      lastConfigTraceId.value = batch?.trace_id || null
      return buildBatchPlaybook(batch, fileName, fileSize)
    } catch (e) {
      return {
        thinkingSteps: ['文档映射调用本体服务失败'],
        content: `文档映射失败：${e.message || '本体服务不可用'}`,
        formCard: null,
      }
    }
  }

  function buildBatchPlaybook(batch, fileName, fileSize) {
    if (!batch?.items?.length) {
      return {
        thinkingSteps: [
          {
            type: 'llm',
            content: `已接收「${fileName}」，按原文尝试抽取套餐段落`,
          },
          {
            type: 'llm',
            content: '未识别到可映射套餐，请补充名称、月费、客群等要点后重试',
          },
        ],
        content:
          `已按「${fileName}」解析，但未抽取到套餐草稿。\n\n` +
          '请直接粘贴方案中的套餐段落（含名称、月费、流量/语音、客群、渠道等），或上传完整方案文档。',
        formCard: null,
        batch,
        showBatchPanel: false,
      }
    }

    let formCard = null
    const mapped = []
    for (const it of batch.items || []) {
      const draft = it.draft || {}
      const product = {
        id: 'P' + Date.now() + Math.random().toString(36).slice(2, 5),
        name: draft.offeringName || `草稿${it.index}`,
        desc: `月费${draft.monthlyFee ?? '-'} | ${it.status}`,
        status: 'draft',
        auditStatus: it.compliancePass ? 'pass' : 'fail',
        compliancePass: it.compliancePass,
        issues: it.issues || [],
        inferredFields: it.inferredFields || [],
        ontologyDraft: draft,
        data: draftToFormData(draft),
        sourceExcerpt: it.sourceExcerpt || draft.sourceExcerpt || '',
      }
      products.value.push(product)
      mapped.push({
        ...it,
        productId: product.id,
        sourceExcerpt: product.sourceExcerpt,
      })
      if (!formCard) {
        currentProductId.value = product.id
        syncFormFromProduct(product)
        formCard = buildProductFormCard(product)
      }
    }
    batchItems.value = mapped
    showBatchPanel.value = true

    const lines = mapped.map((it) => {
      const cat = it.draft?.categoryName || it.categoryName || it.draft?.messageRootKey || ''
      const fee = it.draft?.monthlyFee ?? it.draft?.fixedFeeAmount
      return (
        `- **${it.draft?.offeringName || '未命名'}** → ${it.status}` +
        (cat ? ` · 报文「${cat}」` : '') +
        (fee != null && fee !== '' ? ` · 月费${fee}` : '') +
        (it.issues?.length
          ? `（${it.issues.map((i) => `${i.ruleId}`).join('、')}）`
          : '') +
        (it.sourceExcerpt ? `\n  > ${it.sourceExcerpt}` : '')
      )
    })
    const confirmable = batch.confirmableDrafts || []
    const scenarioHint = batch.scenario || mapped[0]?.draft?.bizScenario || '家庭融合'
    const content =
      `文档已映射完成（场景 **${scenarioHint}**）。共 **${batch.total}** 条草稿：通过 ${batch.passedCount}，待修正 ${batch.pendingCount}。\n\n` +
      `${lines.join('\n')}\n\n` +
      '下方打开「智读·文件配置映射清单」：可对照原文 / 映射字段 / 场景报文，对待修正项一键修正并重跑合规。\n' +
      (confirmable.length
        ? `当前可入库：${confirmable.map((d) => d.offeringName).join('、')}。`
        : '当前暂无通过项，请先修正后再入库。')

    const chain = buildBatchOntologyChain(batch)
    return {
      thinkingSteps: [
        {
          id: 'parse',
          type: 'llm',
          title: '接收与识别文档',
          content: '解析上传文件并识别业务场景',
          result: `「${fileName}」（${(fileSize / 1024).toFixed(1)} KB）→ 场景「${scenarioHint}」`,
        },
        {
          id: 'extract',
          type: 'llm',
          title: '抽取套餐段落',
          content: '按家庭融合方案抽取名称 / 月费 / 要素 / 客群 / 渠道',
          result: `识别 ${batch.total} 条候选草稿`,
        },
        {
          id: 'ontology',
          type: 'ontology',
          title: '业务映射',
          content: '场景映射、字段补全与合规筛查',
          result: `通过 ${batch.passedCount} / 待修 ${batch.pendingCount}`,
          ontologyChain: chain,
          ontologyPreview: buildBatchOntologyPreview(batch, chain),
        },
        {
          id: 'reply',
          type: 'llm',
          title: '整理映射清单',
          content: '生成可读清单话术，并投影场景报文结构',
          result: `通过 ${batch.passedCount}，待修正 ${batch.pendingCount}；报文按 familyBasePrc / familyAddPrc 投影`,
        },
      ],
      content,
      formCard,
      batch,
      showBatchPanel: true,
      nextSteps: [
        ...(batch.pendingCount > 0 ? ['修正待修正项后重跑合规'] : []),
        ...(confirmable.length ? ['确认通过项入库'] : ['完善方案字段后重新智读']),
      ],
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

  async function generateProductFromChat(text) {
    try {
      const result = await chatConfigure(text, ontologyDraft.value)
      return buildChatPlaybook(result)
    } catch (e) {
      return {
        thinkingSteps: ['智聊·对话配置调用本体服务失败'],
        content: `配置生成失败：${e.message || '本体服务不可用'}`,
        formCard: null,
      }
    }
  }

  /**
   * 智检·合规校验：按套餐信息校验。
   * - 文案含套餐名/编码 → 已入库（在架）
   * - 「校验当前配置」或仅有草稿 → 未入库草稿
   */
  async function runComplianceCheck(text = '') {
    const localDraft = ontologyDraft.value || currentProduct.value?.ontologyDraft || null
    try {
      const result = await checkCompliance(localDraft, { text: text || null })
      if (result?.success === false) {
        const examples = (result.hintExamples || []).map((e) => `- 「${e}」`).join('\n')
        return {
          thinkingSteps: [
            { type: 'llm', content: `解析校验目标：${text || '（空）'}` },
            { type: 'ontology', title: '套餐定位', content: result.message || '未找到可校验套餐' },
          ],
          content:
            `合规校验未能启动：${result.message || '缺少套餐信息'}\n\n` +
            (examples ? `可试：\n${examples}` : ''),
          formCard: null,
          result,
        }
      }

      const draft = result.draft || localDraft || {}
      const sourceLabel = result.sourceLabel || (result.source === 'shelf' ? '已入库（在架）' : '未入库草稿')
      const name = result.offeringName || draft.offeringName || '目标套餐'
      const issues = result.issues || []
      const chain = buildOntologyChain({
        ...result,
        draft,
        intent: 'compliance_check',
        slots: { bizScenario: draft.bizScenario, offeringName: draft.offeringName },
        inferredFields: [],
      })
      const preview = buildOntologyPreview({
        ...result,
        draft,
        intent: 'compliance_check',
        slots: {},
        inferredFields: [],
      }, chain)

      const product = {
        id:
          result.source === 'draft' && currentProductId.value
            ? currentProductId.value
            : 'P' + Date.now(),
        name,
        desc: `${sourceLabel} | 月费${draft.monthlyFee ?? '-'} | ${result.compliancePass ? '通过' : '未通过'}`,
        status: result.source === 'shelf' ? 'shelf' : (currentProduct.value?.status || 'draft'),
        auditStatus: result.compliancePass ? 'pass' : 'fail',
        compliancePass: !!result.compliancePass,
        issues,
        inferredFields: [],
        ontologyDraft: draft,
        data: draftToFormData(draft),
        offeringId: result.offeringId || draft.offeringId,
      }

      const existingIdx = products.value.findIndex((p) => p.id === product.id)
      if (existingIdx >= 0) {
        products.value[existingIdx] = { ...products.value[existingIdx], ...product }
      } else {
        products.value.push(product)
      }
      currentProductId.value = product.id
      ontologyDraft.value = draft
      syncFormFromProduct(products.value.find((p) => p.id === product.id) || product)
      const formCard = buildProductFormCard(products.value.find((p) => p.id === product.id) || product)

      const high = issues.filter((i) => i.issueLevel === 'HIGH')
      const lines = [
        `已对 **${sourceLabel}**「${name}」完成合规校验。`,
      ]
      if (result.offeringId) {
        lines.push(`套餐编码：\`${result.offeringId}\``)
      }
      if (result.compliancePass) {
        lines.push('✅ **合规通过**（R-C08）。配置满足在架规则，可继续提交流程。')
      } else if (high.length) {
        lines.push(
          '⚠️ **合规已阻断**：' +
            high.map((i) => `${i.ruleId} ${i.message}`).join('；'),
        )
      }
      const others = issues.filter((i) => i.issueLevel !== 'HIGH')
      if (others.length) {
        lines.push('待补充/关注：' + others.map((i) => i.message).join('；'))
      }
      lines.push('规则由本体判定；已入库套餐与未入库草稿共用同一套配置合规规则（R-C03~C08）。')

      const nextSteps = []
      if (!result.compliancePass && result.source === 'draft') {
        nextSteps.push('在右侧画布修正字段后再次校验')
        if (high.some((i) => i.ruleId === 'R-C03')) nextSteps.push('那不加128了，就单独上158')
      }
      if (result.compliancePass && result.source === 'draft') {
        nextSteps.push('生成配置草稿')
      }
      if (result.source === 'shelf' && !result.compliancePass) {
        nextSteps.push('筛查所有在架的0元资费风险商品')
      }

      return {
        thinkingSteps: [
          {
            id: 'intent',
            type: 'llm',
            title: '意图识别',
            content: '解析用户输入，确定合规校验目标',
            result: '按套餐信息做合规校验',
          },
          {
            id: 'locate',
            type: 'llm',
            title: '定位校验对象',
            content: '从草稿或在架商品中定位待检套餐',
            result: `${sourceLabel}${result.offeringId ? ` / ${result.offeringId}` : ''}「${name}」`,
          },
          {
            id: 'ontology',
            type: 'ontology',
            title: '合规校验',
            content: '执行配置合规规则检查',
            result: result.compliancePass
              ? '合规通过'
              : `待处理 ${(result.issues || []).length} 项`,
            ontologyChain: chain,
            ontologyPreview: preview,
          },
          {
            id: 'reply',
            type: 'llm',
            title: '组织校验结论',
            content: result.compliancePass
              ? '汇总通过说明'
              : '汇总阻断说明（不改写本体判定）',
            result: result.compliancePass ? '可继续完善或入库' : '需先修正阻断项',
          },
        ],
        content: lines.join('\n\n'),
        formCard,
        result,
        nextSteps,
      }
    } catch (e) {
      return {
        thinkingSteps: ['智检·合规校验调用本体服务失败'],
        content: `合规校验失败：${e.message || '本体服务不可用'}`,
        formCard: null,
      }
    }
  }

  function buildChatPlaybook(result) {
    const draft = result.draft || {}
    const issues = result.issues || []
    const slots = result.slots || {}
    const inferred = result.inferredFields || []
    const chain = buildOntologyChain(result)
    const preview = buildOntologyPreview(result, chain)
    if (result.trace_id) {
      lastConfigTraceId.value = result.trace_id
    }

    // 思考过程：每步带 title（动作）+ result（结果），便于面板展示
    const intentLabel =
      result.intent === 'create_offering_config'
        ? '创建 / 更新商品配置'
        : result.intent || '配置'
    const slotSummary = summarizeSlots(slots)
    const inferredSummary = inferred.length
      ? inferred.map((f) => `${fieldCn(f.field || f.code || f)}←${sourceCn(f.source)}`).join('，')
      : '本轮无自动补全字段'
    const issueSummary = issues.length
      ? `待处理 ${issues.length} 项：${issues
          .slice(0, 3)
          .map((i) => i.ruleId || i.message || i)
          .join('、')}${issues.length > 3 ? '…' : ''}`
      : '合规通过，无阻断项'

    const thinkingSteps = [
      {
        id: 'intent',
        type: 'llm',
        title: '意图识别',
        content: '解析用户输入，识别配置意图',
        result: intentLabel,
      },
      {
        id: 'slots',
        type: 'llm',
        title: '抽取业务槽位',
        content: '从对话中抽取场景、品类、固费等关键槽位',
        result: slotSummary,
      },
      {
        id: 'ontology',
        type: 'ontology',
        title: '业务推理',
        content: '调用配置服务完成字段补全与合规判定',
        result: `${inferredSummary}；${issueSummary}`,
        ontologyChain: chain,
        ontologyPreview: preview,
      },
      {
        id: 'reply',
        type: 'llm',
        title: '组织回复话术',
        content: '汇总本体结论，生成用户可读说明（不改写合规判定）',
        result: result.compliancePass ? '合规通过，可继续完善或入库' : '存在待处理项，需先修正',
      },
    ]

    ontologyDraft.value = draft
    const name = draft.offerName || draft.offeringName || '配置方案草稿'
    const fee = draft.fixedFeeAmount ?? draft.monthlyFee
    const product = {
      id: currentProductId.value && currentProduct.value?.ontologyDraft
        ? currentProductId.value
        : 'P' + Date.now(),
      name,
      desc: `固费${fee ?? '-'} | ${draft.categoryName || draft.messageRootKey || ''} | ${draft.bizScenario || ''} | ${draft.channelScope || ''}`,
      status: 'draft',
      auditStatus: result.compliancePass ? 'pass' : 'pending',
      compliancePass: result.compliancePass,
      issues,
      inferredFields: inferred,
      ontologyDraft: draft,
      messagePreview: result.messagePreview || null,
      data: draftToFormData(draft),
    }

    const existingIdx = products.value.findIndex((p) => p.id === product.id)
    if (existingIdx >= 0) {
      products.value[existingIdx] = product
    } else {
      products.value.push(product)
    }
    currentProductId.value = product.id
    syncFormFromProduct(product)
    const formCard = buildProductFormCard(product)
    persistCurrentDraft()

    return {
      thinkingSteps,
      content: buildNaturalChatReply(result),
      formCard,
      result,
      nextSteps: nextStepHints(result),
    }
  }

  function summarizeSlots(slots) {
    const parts = []
    if (slots.bizScenario) parts.push(`场景=${slots.bizScenario}`)
    if (slots.categoryCode) parts.push(`品类=${slots.categoryCode}`)
    if (slots.messageRootKey) parts.push(`报文根键=${slots.messageRootKey}`)
    const fee = slots.fixedFeeAmount ?? slots.monthlyFee
    if (fee !== undefined && fee !== '') parts.push(`固费=${fee}`)
    if (slots.includeBroadband) parts.push(`宽带=${slots.includeBroadband}`)
    if (slots.channelScope) parts.push(`渠道=${slots.channelScope}`)
    if (slots.offerName || slots.offeringName) parts.push(`名称=${slots.offerName || slots.offeringName}`)
    if (slots.bindExistingMainPkg) parts.push(`绑定在架=${slots.bindExistingMainPkg === 'OF-HF-128' ? '家庭融合畅享128' : slots.bindExistingMainPkg}`)
    if (slots.clearBindExisting) parts.push('解除主套餐绑定')
    return parts.length ? parts.join('，') : '（本轮无明显新槽位，沿用草稿）'
  }

  function fieldCn(code) {
    const map = {
      includeVoice: '语音',
      includeData: '流量',
      includeBroadband: '宽带',
      offeringName: '资费名称',
      offerName: '资费名称',
      monthlyFee: '月费',
      fixedFeeAmount: '固费金额',
      messageRootKey: '报文根键',
      categoryCode: '产品品类',
      categoryName: '品类名称',
      bizScenario: '业务场景',
      targetUser: '目标用户',
      channelScope: '销售渠道',
      regionScope: '发布地市',
      workOrderId: '需求工单',
      mutexGroup: '互斥组',
      basedOnTemplate: '配置模板',
      bindExistingMainPkg: '绑定在架主套餐',
      dependOn: '依赖主资费',
    }
    return map[code] || code
  }

  function sourceCn(src) {
    const map = {
      scenario_default: '场景缺省',
      template: '模板推荐',
      user_said: '用户表述',
    }
    return map[src] || src
  }

  function buildNaturalChatReply(result) {
    const draft = result.draft || {}
    const inferred = result.inferredFields || []
    const issues = result.issues || []
    const lines = []
    const name = draft.offerName || draft.offeringName
    const fee = draft.fixedFeeAmount ?? draft.monthlyFee

    if (name) {
      lines.push(`已更新配置方案草稿 **「${name}」**。`)
    } else {
      lines.push('已根据您的描述起草配置方案。')
    }

    if (draft.bizScenario || fee != null || draft.messageRootKey) {
      const bits = []
      if (draft.categoryName || draft.messageRootKey) {
        bits.push(`品类「${draft.categoryName || draft.messageRootKey}」`)
      }
      if (draft.bizScenario) bits.push(`场景「${draft.bizScenario}」`)
      if (draft.targetUser) bits.push(`客群「${draft.targetUser}」`)
      if (fee != null && fee !== '') bits.push(`固费 ${fee} 元`)
      if (draft.includeBroadband) bits.push(`宽带 ${draft.includeBroadband}`)
      if (draft.channelScope) bits.push(`渠道「${draft.channelScope}」`)
      lines.push(bits.join(' · ') + '。')
    }

    if (result.messagePreview && draft.messageRootKey) {
      lines.push(`已按规范生成报文投影根键 **${draft.messageRootKey}**（可在发布时落库）。`)
    }

    const autoFilled = inferred.filter((f) => f.fillSource === 'scenario_default' || f.fillSource === 'template')
    if (autoFilled.length) {
      lines.push(
        '本体按品类/场景缺省补全了：' +
          autoFilled.map((f) => `**${fieldCn(f.field)}**=${f.value}`).join('、') +
          '（非模型臆造，可在画布查看字段来源）。',
      )
    }

    if (issues.length) {
      const high = issues.filter((i) => i.issueLevel === 'HIGH')
      const others = issues.filter((i) => i.issueLevel !== 'HIGH')
      if (high.length) {
        lines.push(
          '⚠️ **合规已阻断**：' +
            high.map((i) => `${i.ruleId} ${i.message}`).join('；'),
        )
      }
      if (others.length) {
        lines.push(
          '还需要补充：' +
            others.map((i) => i.message).join('；'),
        )
      }
      if (issues.some((i) => i.field === 'offeringName' || i.field === 'offerName')) {
        lines.push('请直接回复资费名称，例如：就叫家庭融合畅享158')
      } else if (high.some((i) => i.ruleId === 'R-C03')) {
        lines.push('如需解除互斥，可回复：那不加128了，就单独上158')
      }
    } else if (result.compliancePass) {
      lines.push('✅ 合规通过（R-C08）。右侧画布已同步，可点击顶部「智能稽核」提交配置草稿。')
    }

    lines.push('复杂规则由本体判定，大模型不会把未通过说成可提交。')
    return lines.join('\n\n')
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

  async function runRootCauseAnalysis(text = '') {
    try {
      const result = await analyzeRootCause(null, text || null)
      if (result?.success === false) {
        rootCauseResult.value = null
        showRootCausePanel.value = false
        return {
          thinkingSteps: [
            { type: 'llm', content: `解析用户目标：${text || '（空）'}` },
            { type: 'ontology', title: '图谱检索', content: result.message || '未找到产商品事实' },
          ],
          content: `根因分析失败：${result.message || '无法解析产商品或缺少图谱事实'}`,
          formCard: null,
          showRootCausePanel: false,
        }
      }

      const offeringName = result.offeringName || result.offeringId || '目标商品'
      const anomalies = result.anomalies || []
      const paths = result.paths || []

      if (!anomalies.length || !paths.length) {
        rootCauseResult.value = result
        showRootCausePanel.value = false
        showRiskAuditPanel.value = false
        return {
          thinkingSteps: [
            { type: 'llm', content: `识别意图=根因分析，商品=${offeringName}` },
            {
              type: 'ontology',
              title: '异动判定',
              content: result.message || '图谱事实不足，无法归因',
            },
          ],
          content:
            `### ${offeringName} 异动根因分析\n\n` +
            (result.message || '未检出异动或未命中归因规则'),
          formCard: null,
          rootCauseResult: result,
          showRootCausePanel: false,
        }
      }

      rootCauseResult.value = result
      showRootCausePanel.value = true
      showRiskAuditPanel.value = false
      showMonitorPanel.value = false
      const chain = buildRootCauseOntologyChain(result)
      rootCauseOntologyChain.value = chain
      activeRootCauseRank.value = 1
      const pathLines = paths
        .map(
          (p) =>
            `${p.rank}. **${p.name}**（${classCn(p.rootCauseType) || p.rootCauseType}）权重 ${formatWeight(p.weight)} ← ${formatRule(p.ruleId)}` +
            (p.rank === 1 ? ' ★主因' : '') +
            `\n   证据：${(p.evidence || []).join('；')}`,
        )
        .join('\n')
      const anomaly = anomalies[0]
      return {
        thinkingSteps: [
          {
            id: 'intent',
            type: 'llm',
            title: '确认业务意图',
            content: '解析用户目标与关注指标',
            result: `异动归因 · ${offeringName}`,
          },
          {
            id: 'locate',
            type: 'llm',
            title: '锁定分析对象',
            content: '定位异动商品与指标快照',
            result: `${offeringName}（${result.offeringId}）`,
          },
          {
            id: 'confirm',
            type: 'llm',
            title: '异动确认',
            content: '对照阈值确认指标异动',
            result: anomaly?.message || '指标异动已确认',
          },
          {
            id: 'drill',
            type: 'llm',
            title: '多维下钻',
            content: '按渠道 / 促销 / 竞品 / 行为扫描',
            result: paths.length ? `命中 ${paths.length} 条路径` : '暂无命中维度',
          },
          {
            id: 'reason',
            type: 'ontology',
            title: '规则推理',
            content: '执行图谱与 SWRL 归因规则',
            result: paths.length
              ? `主因「${paths[0]?.name || '-'}」，共 ${paths.length} 条路径`
              : '未形成有效归因路径',
            ontologyChain: chain,
            ontologyPreview: buildRootCauseOntologyPreview(result, chain),
          },
          {
            id: 'conclude',
            type: 'llm',
            title: '归因结论',
            content: '汇总主因路径与处置建议',
            result: anomaly?.message || '已汇总归因结论',
          },
        ],
        content:
          `### ${offeringName} 异动根因分析\n\n` +
          `**异动结论**：${anomaly?.message || '—'}（${anomaly?.ruleId || '—'}）\n\n` +
          `**根因路径 Top${paths.length}**\n${pathLines}\n\n` +
          `**策略建议**\n${(result.actionList || []).map((a) => `- ${a}`).join('\n')}\n\n` +
          '右侧已打开根因面板：可下钻路径、查看证据链，并一键生成优化工单草稿。',
        formCard: null,
        rootCauseResult: result,
        showRootCausePanel: true,
        nextSteps: ['生成产品优化工单草稿', '打开证据链', '稽核高风险零元资费商品'],
      }
    } catch (e) {
      return {
        thinkingSteps: ['根因图谱查询失败'],
        content: `根因分析失败：${e.message}`,
        formCard: null,
      }
    }
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
          '右侧已打开监控面板：选择告警后点击「智能归因」即可下钻。',
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

  async function runRiskAuditFlow(options = {}) {
    try {
      if (options.zeroSalesShelfDays) {
        await updateRiskRules({ zeroSalesShelfDays: options.zeroSalesShelfDays })
      }
      const result = await auditRisks()
      riskAuditResult.value = result
      showRiskAuditPanel.value = true
      showRootCausePanel.value = false
      showMonitorPanel.value = false
      const chain = buildRiskAuditOntologyChain(result)
      const focus = (result.items || []).find((i) => i.offeringId === 'OF-RISK-001')
      const low = (result.items || []).find((i) => i.offeringId === 'OF-LOW-019')
      const lines = (result.items || [])
        .slice(0, 8)
        .map(
          (it) =>
            `- **${it.offeringName}** [${it.riskLevel}] 分值${it.riskScore}` +
            (it.urgent ? ' ·紧急' : '') +
            `\n  ${(it.risks || []).map((r) => `${r.ruleId}:${r.feature}`).join('；')}`,
        )
        .join('\n')
      return {
        thinkingSteps: [
          {
            id: 'intent',
            type: 'llm',
            title: '确认业务意图',
            content: '识别为风险稽核',
            result: `风险稽核 · ${result.ruleVersion || 'RiskRules-v1.2'}`,
          },
          {
            id: 'load',
            type: 'llm',
            title: '加载在架清单',
            content: '加载在架商品清单',
            result: `扫描范围 ${result.scannedCount || 80} 条`,
          },
          {
            id: 'match',
            type: 'llm',
            title: '匹配风险规则集',
            content: '匹配适用稽核规则集',
            result: result.ruleVersion || 'RiskRules-v1.2',
          },
          {
            id: 'scan',
            type: 'ontology',
            title: '全量扫描打分',
            content: '按规则全量扫描打分',
            result: `高风险 ${result.highCount} / 中风险 ${result.mediumCount}`,
            ontologyChain: chain,
            ontologyPreview: buildRiskAuditOntologyPreview(result, chain),
          },
          {
            id: 'conclude',
            type: 'llm',
            title: '风险与处置建议',
            content: '输出风险清单与建议',
            result: `建议下架 ${result.suggestDelistCount}`,
          },
        ],
        content:
          `### 全量智能稽核结果\n\n` +
          `扫描 **${result.scannedCount || 80}** 条 · 规则 **${result.ruleVersion || 'RiskRules-v1.2'}**\n\n` +
          `| 高风险 | 中风险 | 建议下架 |\n| --- | --- | --- |\n| **${result.highCount}** | **${result.mediumCount}** | **${result.suggestDelistCount}** |\n\n` +
          `${lines}\n` +
          ((result.items || []).length > 8 ? `\n…共 ${result.total} 项，详见右侧清单\n` : '\n') +
          (focus
            ? `\n**重点下钻 A**：\`${focus.offeringId}\` ${focus.offeringName} → ${focus.riskLevel}` +
              (focus.urgent ? '（紧急预警）' : '') +
              '\n'
            : '') +
          (low
            ? `**重点下钻 B**：\`${low.offeringId}\` ${low.offeringName} → 在架 ${low.shelfDays} 天、销量 0\n`
            : '') +
          '\n右侧可筛选「建议下架」、调整零销阈值并重新推理，支持导出 JSON 清单。',
        formCard: null,
        riskAuditResult: result,
        showRiskAuditPanel: true,
        nextSteps: ['查看OF-RISK-001证据', '筛选建议下架', '零销阈值改为90天', '导出风险清单'],
      }
    } catch (e) {
      return {
        thinkingSteps: ['风险稽核服务调用失败'],
        content: `风险稽核失败：${e.message}`,
        formCard: null,
      }
    }
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
    }
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

  function deleteProduct(id) {
    const target = products.value.find((p) => p.id === id)
    if (target?.draftId) {
      deleteConfigDraft(target.draftId).catch((e) =>
        console.warn('[useProductConfig] delete draft failed:', e.message || e),
      )
    }
    products.value = products.value.filter((p) => p.id !== id)
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

  async function loadConfigTrace(traceId = lastConfigTraceId.value) {
    if (!traceId) {
      configTraceSteps.value = []
      configExplainText.value = '暂无审计 trace，请先执行智查/复制/入库。'
      showConfigTracePanel.value = true
      return { steps: [], explanation: configExplainText.value }
    }
    try {
      const [trace, explain] = await Promise.all([
        getConfigTrace(traceId),
        explainConfig(traceId, 'business'),
      ])
      configTraceSteps.value = trace?.steps || []
      configExplainText.value = explain?.explanation || ''
      lastConfigTraceId.value = traceId
      showConfigTracePanel.value = true
      return { steps: configTraceSteps.value, explanation: configExplainText.value, traceId }
    } catch (e) {
      configExplainText.value = `加载审计失败：${e.message || e}`
      showConfigTracePanel.value = true
      return { steps: [], explanation: configExplainText.value, error: e }
    }
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
    getSkillGuideMessage,
    detectScenario,
    setSessionContext,
    persistCurrentDraft,
    loadPersistedDrafts,
    submitCurrentDraft,
    runCompareSchemes,
    simulateQuery,
    prepareProduct,
    simulateFileParse,
    generateProductFromChat,
    runComplianceCheck,
    confirmPassedDrafts,
    runRootCauseAnalysis,
    applyRootCauseFromSse,
    applyRiskAuditFromSse,
    applyMonitorFromSse,
    openRulesPanel,
    applyRulesCatalog,
    runRiskAuditFlow,
    runOpsMonitorFlow,
    submitWorkOrder,
    runHypotheticalAndOrder,
    loadConfigTrace,
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
