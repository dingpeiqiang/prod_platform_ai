/**
 * 产品配置 / 本体 MVP 状态管理
 * 对接后端 ontology-mvp 推理 API，兼容 DynamicForm formCard
 */
import { ref, reactive, computed } from 'vue'
import { genId } from '../utils/chatUtils.js'
import {
  mockProducts,
  scene2Products,
  createEmptyFormData,
  createProductFormSchema,
  CAMPUS_PRODUCT_DATA,
  createOfferingFormSchema,
  draftToFormData,
} from '../data/productMockData.js'
import {
  chatConfigure,
  checkCompliance,
  batchFromDocument,
  analyzeRootCause,
  auditRisks,
  updateRiskRules,
} from '../services/ontologyMvpApi.js'
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
} from '../services/ontologyMvpLocal.js'

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
  const rootCauseResult = ref(null)
  const riskAuditResult = ref(null)

  const currentProduct = computed(() =>
    products.value.find((p) => p.id === currentProductId.value) ?? null,
  )

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
      return '好的，让我帮您查询历史商品，您可以快速复制配置。\n\n可输入：商品名称关键词、编码，或「查一下近30天大学生套餐」。'
    }
    if (type === 'file') {
      return (
        '好的，进入**智读·批量生成**。\n\n' +
        '发送「帮我导入校园迎新方案」，或点首页「一键体验」。\n' +
        '演示闭环：\n' +
        '1. 一文映射 3 条草稿（三列：原文 | 映射 | 场景/模板）\n' +
        '2. 批量合规：A 通过 / B·C 待修正（含规则 ID + 证据链）\n' +
        '3. 一键修正后重跑 → 仅通过项可「确认入库」\n\n' +
        '演示文档：`校园迎新产商品方案_2026.md`'
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
    const t = text.toLowerCase()
    if (/根因|异动|离网|累计收入|归因|下滑原因|收入下滑/.test(text)) return 'root-cause'
    if (/稽核|零元|风险|下架|优胜劣汰|筛查/.test(text)) return 'risk-audit'
    if (/确认.*入库|入库通过|确认通过项/.test(text)) return 'confirm-batch'
    if (/查询|智查|历史商品/.test(text) || t.includes('query')) return 'query'
    if (/导入|方案|文档|智读|批量/.test(text)) return 'file-parse'
    if (
      /套餐|校园|大学生|动感地带|流量|月费|配置|融合|家庭|宽带|畅享|不加|单独上|协议期|内部验证/.test(text) ||
      t.includes('5g')
    ) {
      return 'chat-generate'
    }
    return null
  }

  function simulateQuery(keyword) {
    const thinkingSteps = [
      `识别查询意图：检索历史商品配置`,
      `解析关键词：「${keyword || '近30天大学生套餐'}」`,
      `在历史库中匹配模板与商品档案…`,
      `命中 ${mockProducts.length} 条可复制配置，准备展示卡片`,
    ]
    const content =
      `已为您找到 **${mockProducts.length}** 个相关历史商品，可点击下方卡片「复制配置」快速开稿。`
    return {
      thinkingSteps,
      content,
      queryResults: mockProducts,
      formCard: null,
    }
  }

  function prepareProduct(index) {
    const product = mockProducts[index]
    if (!product) return null
    const newProduct = {
      id: 'P' + Date.now(),
      name: product.name,
      code: 'NEW' + Date.now(),
      desc: product.desc,
      template: product.template,
      status: 'draft',
      auditStatus: 'pending',
      data: JSON.parse(JSON.stringify(product.data)),
    }
    const formCard = addProductAndActivate(newProduct)
    return {
      thinkingSteps: [
        `选中历史商品「${product.name}」`,
        '结构化复制字段到新草稿（不含在架状态）',
        '打开右侧配置画布供微调',
      ],
      content: `已将「${product.name}」复制为新草稿，右侧打开配置表单，可继续修改后稽核提交。`,
      formCard,
    }
  }

  async function simulateFileParse(fileName = '校园迎新产商品方案_2026.md', fileSize = 12 * 1024) {
    try {
      const batch = await batchFromDocument(`校园迎新方案 文档名=${fileName}`)
      return buildBatchPlaybook(batch, fileName, fileSize)
    } catch (e) {
      return fallbackFileParsePlaybook(e.message)
    }
  }

  function buildBatchPlaybook(batch, fileName, fileSize) {
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

    const lines = mapped.map(
      (it) =>
        `- **${it.draft?.offeringName || '未命名'}** → ${it.status}` +
        (it.issues?.length
          ? `（${it.issues.map((i) => `${i.ruleId}`).join('、')}）`
          : '') +
        (it.sourceExcerpt ? `\n  > ${it.sourceExcerpt}` : ''),
    )
    const confirmable = batch.confirmableDrafts || []
    const content =
      `文档已映射完成。共 **${batch.total}** 条草稿：通过 ${batch.passedCount}，待修正 ${batch.pendingCount}。\n\n` +
      `${lines.join('\n')}\n\n` +
      '下方打开「智读批量映射清单」：可对照原文 / 映射字段 / 场景模板，对待修正项一键修正并重跑合规。\n' +
      (confirmable.length
        ? `当前可入库：${confirmable.map((d) => d.offeringName).join('、')}。`
        : '当前暂无通过项，请先修正后再入库。')

    const chain = buildBatchOntologyChain(batch)
    return {
      thinkingSteps: [
        {
          type: 'llm',
          content: `接收文档「${fileName}」（${(fileSize / 1024).toFixed(1)} KB），准备按配置本体做段落抽取`,
        },
        { type: 'llm', content: '大模型分段理解套餐段落（名称 / 月费 / 要素 / 客群 / 渠道）' },
        {
          type: 'ontology',
          title: '本体推理',
          content: '调用本体平台',
          ontologyChain: chain,
          ontologyPreview: buildBatchOntologyPreview(batch, chain),
        },
        {
          type: 'llm',
          content: `整理清单话术：通过 ${batch.passedCount}，待修正 ${batch.pendingCount}`,
        },
      ],
      content,
      formCard,
      batch,
      showBatchPanel: true,
      nextSteps: ['补协议期12个月并取消可重复', '确认月费19元', '确认通过项入库'],
    }
  }

  function fallbackFileParsePlaybook(errMsg) {
    // 与后端 _default_campus_packages 对齐的 A/B/C，保证演示口径
    const campusMock = {
      total: 3,
      passedCount: 1,
      pendingCount: 2,
      confirmableDrafts: [{ index: 1, offeringName: '校园青春59' }],
      items: [
        {
          index: 1,
          status: '通过',
          compliancePass: true,
          sourceExcerpt: '套餐A：校园青春59元；含20GB+200分钟；目标校园；电渠+厅店',
          issues: [],
          inferredFields: [],
          draft: {
            offeringName: '校园青春59',
            monthlyFee: 59,
            includeData: '20GB',
            includeVoice: '200分钟',
            targetUser: '校园',
            channelScope: '电渠+厅店',
            bizScenario: '校园体验',
            offeringType: 'main_pkg',
            hasContract: '1',
            basedOnTemplate: 'TPL-CAMPUS-59',
            fillSources: { monthlyFee: 'user_said', includeData: 'scenario_default' },
          },
        },
        {
          index: 2,
          status: '待修正',
          compliancePass: false,
          sourceExcerpt: '套餐B：校园体验0元流量包；无合约；可重复订购',
          issues: [
            { ruleId: 'R-C05', issueType: '高风险资费', issueLevel: 'HIGH', field: 'monthlyFee', message: '月费为0且无合约' },
            { ruleId: 'R-C07', issueType: '异常优惠漏洞', issueLevel: 'HIGH', field: 'discountPercent', message: '折扣100%且可重复订购' },
          ],
          inferredFields: [],
          draft: {
            offeringName: '校园体验0元流量包',
            monthlyFee: 0,
            includeData: '5GB',
            targetUser: '校园',
            channelScope: '全渠道',
            bizScenario: '校园体验',
            offeringType: 'addon',
            hasContract: '0',
            repeatable: 'true',
            discountPercent: 100,
            dependOn: '',
          },
        },
        {
          index: 3,
          status: '待修正',
          compliancePass: false,
          sourceExcerpt: '套餐C：校园融合加装包；依赖宽带；未写月费',
          issues: [
            { ruleId: 'R-C06', issueType: '必填缺失', issueLevel: 'MEDIUM', field: 'monthlyFee', message: '缺少必填字段：月费' },
            { ruleId: 'R-C04', issueType: '规则漏洞', issueLevel: 'HIGH', field: 'dependOn', message: '附加包缺少依赖' },
          ],
          inferredFields: [],
          draft: {
            offeringName: '校园融合加装包',
            targetUser: '校园',
            channelScope: '电渠+厅店',
            bizScenario: '校园体验',
            offeringType: 'addon',
            dependOn: '',
          },
        },
      ],
      appliedRules: ['R-D01', 'R-D02', 'R-D03', 'R-D04', 'R-D05'],
    }
    const playbook = buildBatchPlaybook(campusMock, '校园迎新产商品方案_2026.md', 12 * 1024)
    playbook.thinkingSteps = [
      {
        type: 'llm',
        content: `本体服务暂不可用（${errMsg}），切换本地校园迎新 A/B/C mock`,
      },
      ...playbook.thinkingSteps.slice(1),
    ]
    playbook.content = `已回退本地演示文档映射。\n\n${playbook.content}`
    return playbook
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
      return fallbackChatPlaybook(text, e.message)
    }
  }

  function buildChatPlaybook(result) {
    const draft = result.draft || {}
    const issues = result.issues || []
    const slots = result.slots || {}
    const inferred = result.inferredFields || []
    const chain = buildOntologyChain(result)
    const preview = buildOntologyPreview(result, chain)

    // 思考过程：模型思考 vs 本体推理（仅推理链）
    const thinkingSteps = [
      {
        type: 'llm',
        content: `理解用户意图：${result.intent === 'create_offering_config' ? '创建 / 更新商品配置' : (result.intent || '配置')}`,
      },
      {
        type: 'llm',
        content: `抽取业务槽位：${summarizeSlots(slots)}`,
      },
      {
        type: 'ontology',
        title: '本体推理',
        content: '调用本体平台',
        ontologyChain: chain,
        ontologyPreview: preview,
      },
      {
        type: 'llm',
        content: '组织回复话术（不改写本体合规结论）',
      },
    ]

    ontologyDraft.value = draft
    const name = draft.offeringName || '商品配置草稿'
    const product = {
      id: currentProductId.value && currentProduct.value?.ontologyDraft
        ? currentProductId.value
        : 'P' + Date.now(),
      name,
      desc: `月费${draft.monthlyFee ?? '-'} | ${draft.bizScenario || ''} | ${draft.channelScope || ''}`,
      status: 'draft',
      auditStatus: result.compliancePass ? 'pass' : 'pending',
      compliancePass: result.compliancePass,
      issues,
      inferredFields: inferred,
      ontologyDraft: draft,
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
    if (slots.monthlyFee !== undefined && slots.monthlyFee !== '') parts.push(`月费=${slots.monthlyFee}`)
    if (slots.includeBroadband) parts.push(`宽带=${slots.includeBroadband}`)
    if (slots.channelScope) parts.push(`渠道=${slots.channelScope}`)
    if (slots.offeringName) parts.push(`名称=${slots.offeringName}`)
    if (slots.bindExistingMainPkg) parts.push(`绑定在架=${slots.bindExistingMainPkg === 'OF-HF-128' ? '家庭融合畅享128' : slots.bindExistingMainPkg}`)
    if (slots.clearBindExisting) parts.push('解除主套餐绑定')
    return parts.length ? parts.join('，') : '（本轮无明显新槽位，沿用草稿）'
  }

  function fieldCn(code) {
    const map = {
      includeVoice: '语音',
      includeData: '流量',
      includeBroadband: '宽带',
      offeringName: '商品名称',
      monthlyFee: '月费',
      bizScenario: '业务场景',
      targetUser: '目标用户',
      channelScope: '销售渠道',
      mutexGroup: '互斥组',
      basedOnTemplate: '配置模板',
      bindExistingMainPkg: '绑定在架主套餐',
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

    if (draft.offeringName) {
      lines.push(`已更新商品配置草稿 **「${draft.offeringName}」**。`)
    } else {
      lines.push('已根据您的描述起草商品配置。')
    }

    if (draft.bizScenario || draft.monthlyFee != null) {
      const bits = []
      if (draft.bizScenario) bits.push(`场景「${draft.bizScenario}」`)
      if (draft.targetUser) bits.push(`客群「${draft.targetUser}」`)
      if (draft.monthlyFee != null && draft.monthlyFee !== '') bits.push(`月费 ${draft.monthlyFee} 元`)
      if (draft.includeBroadband) bits.push(`宽带 ${draft.includeBroadband}`)
      if (draft.channelScope) bits.push(`渠道「${draft.channelScope}」`)
      lines.push(bits.join(' · ') + '。')
    }

    const autoFilled = inferred.filter((f) => f.fillSource === 'scenario_default' || f.fillSource === 'template')
    if (autoFilled.length) {
      lines.push(
        '本体按场景缺省补全了：' +
          autoFilled.map((f) => `**${f.field}**=${f.value}`).join('、') +
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
      if (issues.some((i) => i.field === 'offeringName')) {
        lines.push('请直接回复商品名称，例如：就叫家庭融合畅享158')
      } else if (high.some((i) => i.ruleId === 'R-C03')) {
        lines.push('如需解除互斥，可回复：那不加128了，就单独上158')
      }
    } else if (result.compliancePass) {
      lines.push('✅ 合规通过（R-C08）。右侧画布已同步，可点击顶部「智能稽核」提交配置草稿。')
    }

    lines.push('复杂规则由本体判定，大模型不会把未通过说成可提交。')
    return lines.join('\n\n')
  }

  function fallbackChatPlaybook(text, errMsg) {
    const isCampus = /大学生|校园/.test(text)
    if (!isCampus) {
      return {
        thinkingSteps: [
          `本体服务暂不可用（${errMsg}）`,
          '尝试本地关键词匹配…',
          '信息不足，需要更具体的业务描述',
        ],
        content: '请描述更具体的需求，例如：给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售。',
        formCard: null,
      }
    }
    const newProduct = {
      id: 'P' + Date.now(),
      name: '5G-A校园卡49元G（大学生专享）',
      desc: '月费49元 | 15G流量 | 200分钟语音',
      status: 'draft',
      auditStatus: 'pending',
      data: CAMPUS_PRODUCT_DATA(),
    }
    const formCard = addProductAndActivate(newProduct)
    return {
      thinkingSteps: [
        `本体服务暂不可用（${errMsg}），回退校园模板`,
        '匹配大学生套餐模板并填充默认字段',
      ],
      content: '已按校园模板生成配置草稿，右侧打开表单，可继续微调。',
      formCard,
    }
  }

  async function runRootCauseAnalysis() {
    try {
      const result = await analyzeRootCause('OF-HF-128')
      rootCauseResult.value = result
      showRootCausePanel.value = true
      showRiskAuditPanel.value = false
      const chain = buildRootCauseOntologyChain(result)
      const pathLines = (result.paths || [])
        .map(
          (p) =>
            `${p.rank}. **${p.name}**（${p.rootCauseType}）权重 ${p.weight} ← ${p.ruleId}` +
            (p.rank === 1 ? ' ★主因' : '') +
            `\n   证据：${(p.evidence || []).join('；')}`,
        )
        .join('\n')
      const anomaly = result.anomalies?.[0]
      return {
        thinkingSteps: [
          {
            type: 'llm',
            content: '识别意图=根因分析，槽位=OF-HF-128、累计收入',
          },
          {
            type: 'llm',
            content: '锁定异动商品：家庭融合畅享128，准备沿本体关系受控遍历（非全文检索）',
          },
          {
            type: 'ontology',
            title: '本体推理',
            content: '调用本体平台',
            ontologyChain: chain,
            ontologyPreview: buildRootCauseOntologyPreview(result, chain),
          },
          {
            type: 'llm',
            content: '基于固定证据 JSON 生成报告话术（不改写关键数字）',
          },
        ],
        content:
          `### ${result.offeringName} 异动根因分析\n\n` +
          `**异动结论**：${anomaly?.message || '累计收入环比 -18%'}（${anomaly?.ruleId || 'R-A01'}）\n\n` +
          `**根因路径 Top3**\n${pathLines}\n\n` +
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

  async function runRiskAuditFlow(options = {}) {
    try {
      if (options.zeroSalesShelfDays) {
        await updateRiskRules({ zeroSalesShelfDays: options.zeroSalesShelfDays })
      }
      const result = await auditRisks()
      riskAuditResult.value = result
      showRiskAuditPanel.value = true
      showRootCausePanel.value = false
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
            type: 'llm',
            content: `识别意图=风险稽核，规则版本 ${result.ruleVersion || 'RiskRules-v1.2'}`,
          },
          {
            type: 'llm',
            content: `加载在架清单 ${result.scannedCount || 80} 条，准备全量规则扫描`,
          },
          {
            type: 'ontology',
            title: '本体推理',
            content: '调用本体平台',
            ontologyChain: chain,
            ontologyPreview: buildRiskAuditOntologyPreview(result, chain),
          },
          {
            type: 'llm',
            content: `汇总话术：高风险 ${result.highCount} / 中风险 ${result.mediumCount} / 建议下架 ${result.suggestDelistCount}`,
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

  function confirmPassedDrafts() {
    const passed = products.value.filter((p) => p.compliancePass && p.status !== 'submitted')
    if (!passed.length) {
      return {
        thinkingSteps: ['检查商品列表中合规通过且未入库的草稿…', '未找到可入库项'],
        content: '当前没有「合规通过」且未入库的草稿。请先完成智读批量或修正待修正项后重跑。',
        formCard: null,
      }
    }
    const lines = passed.map((p) => {
      const draftId = `DRAFT-${Date.now().toString(36).toUpperCase()}-${Math.random().toString(36).slice(2, 5).toUpperCase()}`
      p.status = 'submitted'
      p.draftId = draftId
      const bi = batchItems.value.findIndex((i) => i.productId === p.id)
      if (bi >= 0) {
        batchItems.value[bi] = {
          ...batchItems.value[bi],
          draftId,
          status: '已入库',
        }
      }
      return `- ${p.name} → \`${draftId}\``
    })
    showBatchPanel.value = true
    return {
      thinkingSteps: [
        '筛选 compliancePass=true 且未入库草稿',
        `确认 ${passed.length} 条通过项写入 Mock 产商品中心`,
        '待修正项跳过，不生成 draftId',
      ],
      content:
        `已确认 **${passed.length}** 条通过项入库（Mock）：\n${lines.join('\n')}\n\n` +
        '待修正项**不会入库**。冲突与漏洞在配置当下被本体拦住，而不是事后稽核。',
      formCard: null,
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

  function saveDraft() {
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
    product.auditStatus = 'pending'
    auditStatus.value = 'pending'
    isModified.value = false
  }

  function mapIssuesToAuditResults(issues, pass) {
    if (!issues?.length && pass) {
      return [
        { type: 'success', title: '合规通过 (R-C08)', desc: '无 HIGH 问题且必填齐全，允许提交配置草稿' },
      ]
    }
    return (issues || []).map((i) => ({
      type: i.issueLevel === 'HIGH' ? 'error' : i.issueLevel === 'MEDIUM' ? 'warning' : 'success',
      title: `${i.ruleId} · ${i.issueType}`,
      desc: i.message + (i.evidence?.length ? ` | 证据：${i.evidence.join('；')}` : ''),
    }))
  }

  async function runAudit() {
    saveDraft()
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
      const results = mapIssuesToAuditResults(result.issues, result.compliancePass)
      const hasError = !result.compliancePass
      auditStatus.value = hasError ? 'fail' : 'pass'
      if (product) {
        product.auditStatus = auditStatus.value
        product.compliancePass = result.compliancePass
        product.issues = result.issues || []
        if (!hasError) product.status = 'submitted'
      }
      auditResults.value = results
      return { results, hasError }
    } catch (e) {
      const results = buildLocalAuditResults()
      const hasError = results.some((r) => r.type === 'error')
      auditStatus.value = hasError ? 'fail' : 'pass'
      auditResults.value = results
      return { results, hasError }
    }
  }

  function buildLocalAuditResults() {
    const results = []
    if (!formData.prodPrcName && !formData.offeringName) {
      results.push({ type: 'error', title: '缺少商品名称', desc: 'R-C06 必填缺失' })
    }
    if (!formData.monthlyFee && formData.monthlyFee !== 0) {
      results.push({ type: 'error', title: '缺少月费', desc: 'R-C06 必填缺失' })
    }
    if (!results.length) {
      results.push({ type: 'success', title: '必填项检查通过', desc: '本地回退校验通过' })
    }
    return results
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
    rootCauseResult.value = null
    riskAuditResult.value = null
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
    rootCauseResult,
    riskAuditResult,
    getSkillGuideMessage,
    detectScenario,
    simulateQuery,
    prepareProduct,
    simulateFileParse,
    generateProductFromChat,
    confirmPassedDrafts,
    runRootCauseAnalysis,
    runRiskAuditFlow,
    selectProduct,
    copyProduct,
    deleteProduct,
    saveDraft,
    runAudit,
    updateFormField,
    buildProductFormCard,
    syncFormFromProduct,
    applyBatchFix,
    createEmptyOfferingCanvas,
    resetState,
  }
}
