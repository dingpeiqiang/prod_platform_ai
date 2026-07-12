import { ref, reactive, computed, nextTick } from 'vue'
import { mockProducts, scene2Products, SKILL_CONFIG, createEmptyFormData } from '../data/mockData.js'

let messageId = 0

const CAMPUS_PRODUCT_DATA = () => ({
  ...createEmptyFormData(),
  workOrderId: 'WO' + Date.now(),
  prodPrcName: '5G-A校园卡49元G（大学生专享）',
  effRuleId: '1001',
  expRuleId: '1023',
  effDate: new Date().toISOString().split('T')[0],
  expDate: '2099-12-31',
  prodId: 'APAP00201',
  prodPrcId: 'ACAG00201',
  chnClassLimit: 'A7',
  groupId: '10001',
  prcMonthFee: '49元/月',
  containResource: '15G流量+200分钟语音',
  chargeStandard: '0.29元/MB',
  limitCondition: '仅限四川在校大学生办理，合约期内不可销户',
  otherEquity: '生日特权、流量结转',
  smsContent: '尊敬的用户，您的套餐已办理成功',
  sysNoteNow:
    '恭喜您成功办理5G-A校园卡49元G套餐，月费49元，含15G流量和200分钟语音，感谢您的支持！',
  sysNoteNext: '您的5G-A校园卡49元G套餐已生效，月费49元，生效当月起享受套餐权益。',
  sysNoteCancle: '您好，您的5G-A校园卡49元G套餐已退订，合约期内退订需支付违约金。',
  sysNoteErke: '尊敬的客户，您正在办理5G-A校园卡49元G套餐，月费49元，是否确认办理？',
  chargeType: 'monthly',
  monthlyFee: '49.00',
  feeSubject: '001',
  taxRate: '6',
  minConsumeType: 'none',
  flowType: 'general',
  flowAmount: '15',
  flowUnit: 'GB',
  flowOvercharge: '0.29元/MB',
  flowCarryover: 'yes',
  voiceType: 'local',
  voiceAmount: '200',
  voiceOvercharge: '0.15元/分钟',
  smsAmount: '20',
  smsOvercharge: '0.1元/条',
})

export function useAppState() {
  const mode = ref('full')
  const products = ref([])
  const currentProductId = ref(null)
  const isModified = ref(false)
  const auditStatus = ref('pending')
  const currentSkill = ref(null)
  const auditPassed = ref(false)

  const messages = ref([
    {
      id: messageId++,
      role: 'ai',
      welcome: true,
    },
  ])

  const chatInput = ref('')
  const showProductPanel = ref(false)
  const showAuditPanel = ref(false)
  const showSubmitModal = ref(false)
  const toastMessage = ref('')
  const toastVisible = ref(false)

  const activeTab = ref('base')
  const showProductListView = ref(false)
  const formData = reactive(createEmptyFormData())
  const modifiedFields = ref(new Set())

  const auditContentHtml = ref('')
  const auditPhase = ref('idle') // idle | progress | results

  const currentProduct = computed(() =>
    products.value.find((p) => p.id === currentProductId.value) ?? null,
  )

  const inputPlaceholder = computed(() => {
    if (currentSkill.value && SKILL_CONFIG[currentSkill.value]) {
      return SKILL_CONFIG[currentSkill.value].placeholder
    }
    return '输入商品名称进行模糊查询...'
  })

  function scrollMessages() {
    nextTick(() => {
      const el = document.querySelector('.messages')
      if (el) el.scrollTop = el.scrollHeight
    })
  }

  function addUserMessage(text) {
    messages.value.push({ id: messageId++, role: 'user', content: text, html: false })
    scrollMessages()
  }

  function addAIMessage(content, options = {}) {
    const msg = {
      id: messageId++,
      role: 'ai',
      content,
      html: options.html !== false,
      loading: options.loading,
    }
    messages.value.push(msg)
    scrollMessages()
    return msg
  }

  function removeMessage(msg) {
    const i = messages.value.findIndex((m) => m.id === msg.id)
    if (i >= 0) messages.value.splice(i, 1)
  }

  function showToast(message) {
    toastMessage.value = message
    toastVisible.value = true
    setTimeout(() => {
      toastVisible.value = false
    }, 2000)
  }

  function switchToSplitMode() {
    mode.value = 'split'
  }

  function setSkill(type) {
    currentSkill.value = type
  }

  function removeSkill() {
    currentSkill.value = null
  }

  function syncFormFromProduct(product) {
    const data = product?.data || createEmptyFormData()
    Object.assign(formData, { ...createEmptyFormData(), ...data })
    modifiedFields.value = new Set()
    isModified.value = false
    auditStatus.value = product?.auditStatus || 'pending'
  }

  function markFieldModified(field) {
    modifiedFields.value = new Set([...modifiedFields.value, field])
    isModified.value = true
  }

  function selectProduct(id) {
    const product = products.value.find((p) => p.id === id)
    if (!product) return
    currentProductId.value = id
    syncFormFromProduct(product)
  }

  function saveFormToCurrentProduct() {
    const product = currentProduct.value
    if (!product) return
    product.data = JSON.parse(JSON.stringify(formData))
    product.name = formData.prodPrcName || product.name
    product.auditStatus = auditStatus.value
  }

  function updateFormWithProduct(product) {
    if (!product) return
    currentProductId.value = product.id
    syncFormFromProduct(product)
  }

  function handleCardClick(type) {
    setSkill(type)
    const loadingText =
      type === 'query'
        ? '正在准备智查模式...'
        : type === 'file'
          ? '正在准备文档导入模式...'
          : '正在准备对话模式...'
    const loading = addAIMessage(`<i class="fa-solid fa-spinner fa-spin"></i> ${loadingText}`, {
      loading: true,
    })
    setTimeout(() => {
      removeMessage(loading)
      if (type === 'query') {
        addAIMessage(
          `<i class="fa-solid fa-wand-magic-sparkles"></i> <strong>好的，让我帮您查询历史商品，您可以快速复制配置。</strong><br><br>
          <span style="font-size:13px;">您可以在下方输入框输入以下任一信息进行查询：</span><br>
          <span style="font-size:13px;color:#666;">• 商品名称关键词（如：动感地带、5G套餐）</span><br>
          <span style="font-size:13px;color:#666;">• 商品编码（如：WO20250115001）</span><br>
          <span style="font-size:13px;color:#666;">• 部分名称（如：青春卡、校园卡）</span><br><br>
          <span style="font-size:12px;color:#999;">请在下方输入框输入查询条件，然后点击发送</span>`,
        )
      } else if (type === 'file') {
        addAIMessage(
          `<i class="fa-solid fa-file-import"></i> <strong>好的，让我帮您导入AI方案文档。</strong><br><br>
          <span style="font-size:13px;">请按以下步骤操作：</span><br>
          <span style="font-size:13px;color:#666;">1. 点击左侧的 <i class="fa-solid fa-paperclip"></i> 附件按钮</span><br>
          <span style="font-size:13px;color:#666;">2. 选择您的活动方案文档（支持 .docx、.pdf、.xlsx、.doc 格式）</span><br>
          <span style="font-size:13px;color:#666;">3. 系统将自动解析并批量生成商品配置</span><br><br>
          <span style="font-size:12px;color:#999;">请开始上传您的方案文档</span>`,
        )
      } else {
        addAIMessage(
          `<i class="fa-solid fa-comments"></i> <strong>好的，让我帮您通过自然语言生成商品配置。</strong><br><br>
          <span style="font-size:13px;">您可以用自然语言描述您的需求，例如：</span><br>
          <span style="font-size:13px;color:#666;">• "我要一个大学生套餐，月费50元左右"</span><br>
          <span style="font-size:13px;color:#666;">• "创建一个5G合约套餐，含30G流量"</span><br>
          <span style="font-size:13px;color:#666;">• "做一个月费99元的动感地带套餐"</span><br><br>
          <span style="font-size:12px;color:#999;">请在下方输入框描述您的配置需求，然后点击发送</span>`,
        )
      }
    }, 500)
  }

  function simulateQuery(keyword) {
    addAIMessage(
      `<i class="fa-solid fa-search"></i> 正在为您搜索包含「<strong>${keyword}</strong>」的商品...`,
    )
    setTimeout(() => {
      addAIMessage(
        `<i class="fa-solid fa-database"></i> 在历史商品库中检索到 <strong>2</strong> 个相关商品：`,
      )
      setTimeout(() => {
        messages.value.push({
          id: messageId++,
          role: 'ai',
          type: 'query-results',
          products: mockProducts,
        })
        scrollMessages()
      }, 800)
    }, 1200)
  }

  function prepareProduct(index) {
    const product = mockProducts[index]
    const loading = addAIMessage(
      `<i class="fa-solid fa-spinner fa-spin"></i> 正在准备商品「${product.name}」的配置...`,
      { loading: true },
    )
    setTimeout(() => {
      removeMessage(loading)
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
      products.value.push(newProduct)
      updateFormWithProduct(newProduct)
      auditStatus.value = 'pending'
      switchToSplitMode()
      showProductPanel.value = false
      addAIMessage(
        `<i class="fa-solid fa-check-circle" style="color:#52c41a;"></i> 已将商品「${product.name}」添加到配置列表！<br><span style="font-size:12px;">正在打开配置界面...</span>`,
      )
      showToast('商品已准备完成')
    }, 1000)
  }

  function handleFileUpload(file) {
    if (!file) return
    addUserMessage(`上传了文件：${file.name}`)
    const loading = addAIMessage(
      `<i class="fa-solid fa-spinner fa-spin"></i> 正在接收文件「${file.name}」...`,
      { loading: true },
    )
    setTimeout(() => {
      removeMessage(loading)
      addAIMessage(
        `<i class="fa-solid fa-file-check"></i> 文件接收成功！正在开始解析...<p style="margin-top:8px;font-size:13px;color:#999;">文件大小：${(file.size / 1024).toFixed(1)} KB</p>`,
      )
      setTimeout(() => {
        addAIMessage(
          `<i class="fa-solid fa-brain"></i> <strong>AI正在分析文档内容...</strong><br>
          <span style="font-size:12px;color:#666;">• 正在提取商品清单<br>
          • 正在识别关键字段信息<br>
          • 正在匹配业务类型</span>`,
        )
        setTimeout(() => {
          addAIMessage(
            `<i class="fa-solid fa-search"></i> <strong>检测到3个待生成商品：</strong><br>
            <div class="scene-indicator" style="display:inline-flex;align-items:center;gap:6px;padding:4px 12px;background:var(--primary-light);border-radius:20px;font-size:12px;color:var(--primary-color);margin:8px 0;"><i class="fa-solid fa-file-import"></i> AI文案导入解析结果</div><br>
            <span style="font-size:13px;color:#666;">
            • 畅享校园卡39元套餐<br>
            • 畅享校园卡59元套餐<br>
            • 畅享校园卡79元套餐</span>`,
          )
          setTimeout(() => {
            addAIMessage(
              `<i class="fa-solid fa-check-circle" style="color:#52c41a;"></i> 文档解析完成！<br><span style="font-size:13px;">已将3个商品添加到商品列表</span>`,
            )
            switchToSplitMode()
            const imported = scene2Products.map((p) =>
              JSON.parse(JSON.stringify({ ...p, data: { ...p.data } })),
            )
            imported.forEach((p) => products.value.push(p))
            updateFormWithProduct(imported[0])
            showProductPanel.value = true
          }, 1500)
        }, 2000)
      }, 2000)
    }, 1500)
  }

  function sendMessage() {
    const text = chatInput.value.trim()
    if (!text) return
    addUserMessage(text)
    chatInput.value = ''

    if (currentSkill.value === 'query') {
      removeSkill()
      simulateQuery(text)
      return
    }

    if (text.includes('查询') || text.includes('智查')) {
      const keyword = text.replace(/查询|智查/g, '').trim() || '动感地带'
      simulateQuery(keyword)
      return
    }

    const loading = addAIMessage(
      `<i class="fa-solid fa-brain"></i> <strong>正在分析您的需求...</strong><br>
      <span style="font-size:12px;color:#666;">• 理解语义意图<br>
      • 提取关键配置参数<br>
      • 匹配业务模板<br>
      • 生成配置方案</span>`,
      { loading: true },
    )
    setTimeout(() => {
      removeMessage(loading)
      if (text.includes('大学生') || text.includes('校园')) {
        addAIMessage(
          `<i class="fa-solid fa-check-circle" style="color:#52c41a;"></i> 已分析您的需求！<br>
          <span style="font-size:13px;">检测到关键词：<strong>大学生/校园</strong><br>
          匹配业务：<strong>个人主资费</strong><br>
          已提取参数：月费49元/月、通用流量15GB，本地语音200分钟，短信20条</span>`,
        )
        setTimeout(() => {
          addAIMessage(
            `<i class="fa-solid fa-sparkles"></i> <strong>正在根据历史案例推算配置...</strong><br>
            <span style="font-size:12px;color:#666;">• 参考同类型套餐定价策略<br>
            • 分析校园用户消费习惯<br>
            • 计算资源包最优配比</span>`,
          )
          setTimeout(() => {
            addAIMessage(
              `<i class="fa-solid fa-check-circle" style="color:#52c41a;"></i> 配置生成完成！<br><span style="font-size:13px;">已在右侧生成商品列表，请点击编辑查看详情</span>`,
            )
            const newProduct = {
              id: 'P' + Date.now(),
              name: '5G-A校园卡49元G（大学生专享）',
              desc: '月费49元 | 15G流量 | 200分钟语音 | 合约36个月',
              template: 'personMainPrc',
              status: 'draft',
              auditStatus: 'pending',
              data: CAMPUS_PRODUCT_DATA(),
            }
            products.value.push(newProduct)
            updateFormWithProduct(newProduct)
            switchToSplitMode()
            showProductPanel.value = true
          }, 1500)
        }, 1500)
      } else {
        addAIMessage(
          `<i class="fa-solid fa-circle-info"></i> 已收到您的需求，正在分析中...<br><span style="font-size:12px;">请提供更多细节，如：套餐名称、月费、包含资源等</span>`,
        )
      }
    }, 2000)
  }

  function copyProduct(id) {
    const product = products.value.find((p) => p.id === id)
    if (!product) return
    products.value.push({
      id: 'P' + Date.now(),
      name: product.name + ' (副本)',
      desc: product.desc,
      template: product.template,
      status: 'draft',
      auditStatus: 'pending',
      data: JSON.parse(JSON.stringify(product.data)),
    })
    showToast('商品复制成功')
  }

  function deleteProduct(id) {
    const product = products.value.find((p) => p.id === id)
    if (!product || !confirm(`确定要删除商品「${product.name}」吗？`)) return
    products.value = products.value.filter((p) => p.id !== id)
    if (currentProductId.value === id) {
      const first = products.value[0]
      currentProductId.value = first?.id ?? null
      if (first) syncFormFromProduct(first)
      else auditStatus.value = 'pending'
    }
    showToast('商品删除成功')
  }

  function showProductEditor(id) {
    const product = products.value.find((p) => p.id === id)
    if (!product) return
    currentProductId.value = id
    auditStatus.value = product.auditStatus || 'pending'
    showProductPanel.value = false
    showProductListView.value = false
    switchToSplitMode()
    updateFormWithProduct(product)
    showToast(`正在编辑：${product.name}`)
  }

  function saveDraft() {
    saveFormToCurrentProduct()
    const product = currentProduct.value
    if (product) {
      product.status = 'draft'
      product.auditStatus = 'pending'
      auditStatus.value = 'pending'
    }
    isModified.value = false
    modifiedFields.value = new Set()
    showToast('草稿保存成功')
  }

  function buildAuditResults() {
    const results = []
    if (!formData.prodPrcName) {
      results.push({ type: 'error', title: '缺少资费名称', desc: '资费名称未填写，根据规范必填' })
    }
    if (!formData.monthlyFee) {
      results.push({ type: 'error', title: '缺少套餐固定费', desc: '套餐固定费未填写，根据规范必填' })
    }
    if (!formData.flowAmount) {
      results.push({ type: 'error', title: '缺少流量资源配置', desc: '流量资源配置未填写，根据规范必填' })
    }
    if (!formData.smsAmount) {
      results.push({ type: 'error', title: '缺少短信资源配置', desc: '短信资源配置未填写，根据规范必填' })
    }
    if (!results.length) {
      results.push({ type: 'success', title: '必填项检查通过', desc: '所有必填字段已填写' })
      results.push({ type: 'success', title: '日期配置合规', desc: '销售日期配置符合业务规范' })
      results.push({ type: 'success', title: '资源配额检查通过', desc: '流量、语音、短信资源配置完整' })
    }
    return results
  }

  function renderAuditProgress(icon, title, text) {
    return `
      <div class="audit-progress">
        <i class="fa-solid ${icon}"></i>
        <p>${title}</p>
        <div class="loading-dots"><span></span><span></span><span></span></div>
        <p class="text">${text}</p>
      </div>`
  }

  function renderAuditResultsHtml(results, hasError) {
    let html = results
      .map(
        (item) => `
      <div class="audit-item ${item.type}">
        <div class="audit-icon"><i class="fa-solid fa-${item.type === 'error' ? 'xmark' : item.type === 'warning' ? 'exclamation' : 'check'}"></i></div>
        <div class="audit-text">
          <div class="audit-title">${item.title}</div>
          <div class="audit-desc">${item.desc}</div>
        </div>
      </div>`,
      )
      .join('')
    if (!hasError && currentProduct.value) {
      html += `
        <div class="audit-item success" style="margin-top: 20px;">
          <div class="audit-icon"><i class="fa-solid fa-check"></i></div>
          <div class="audit-text">
            <div class="audit-title">配置提交成功</div>
            <div class="audit-desc">商品状态已更新为待审批</div>
          </div>
        </div>
        <div style="text-align: center; margin-top: 20px;">
          <button type="button" class="btn-submit audit-close-btn" style="padding: 10px 30px;">关闭</button>
        </div>`
    } else if (hasError) {
      html += `
        <div style="text-align: center; margin-top: 20px; color: #ff4d4f; font-size: 13px;">
          请修正上述错误项后重新提交配置
        </div>`
    }
    return html
  }

  function runAudit() {
    showAuditPanel.value = true
    auditPhase.value = 'progress'
    auditContentHtml.value = renderAuditProgress('fa-spinner fa-spin', '正在执行智能稽核...', '正在检查配置完整性...')

    setTimeout(() => {
      auditContentHtml.value = renderAuditProgress('fa-search', '基础信息完整性校验', '正在检查必填字段...')
    }, 1500)
    setTimeout(() => {
      auditContentHtml.value = renderAuditProgress('fa-shield-check', '业务规则校验', '正在验证字段联动规则...')
    }, 3000)
    setTimeout(() => {
      auditContentHtml.value = renderAuditProgress('fa-comment-sms', '短信模板合规性检查', '正在检查关键词合规性...')
    }, 4500)
    setTimeout(() => {
      const results = buildAuditResults()
      const hasError = results.some((r) => r.type === 'error')
      auditStatus.value = hasError ? 'fail' : 'pass'
      saveFormToCurrentProduct()
      if (currentProduct.value) {
        currentProduct.value.auditStatus = auditStatus.value
        if (!hasError) {
          currentProduct.value.status = 'submitted'
          auditPassed.value = true
        }
      }
      auditPhase.value = 'results'
      auditContentHtml.value = renderAuditResultsHtml(results, hasError)
      if (!hasError) showToast('配置提交成功！')
    }, 6000)
  }

  function hideAuditPanel() {
    showAuditPanel.value = false
    if (auditPassed.value) {
      showProductListView.value = true
      auditPassed.value = false
    }
  }

  function confirmSubmit() {
    showSubmitModal.value = false
    runAudit()
  }

  return {
    mode,
    products,
    currentProductId,
    currentProduct,
    isModified,
    auditStatus,
    currentSkill,
    messages,
    chatInput,
    showProductPanel,
    showAuditPanel,
    showSubmitModal,
    toastMessage,
    toastVisible,
    activeTab,
    showProductListView,
    formData,
    modifiedFields,
    auditContentHtml,
    auditPhase,
    inputPlaceholder,
    SKILL_CONFIG,
    handleCardClick,
    setSkill,
    removeSkill,
    sendMessage,
    prepareProduct,
    handleFileUpload,
    selectProduct,
    copyProduct,
    deleteProduct,
    showProductEditor,
    markFieldModified,
    saveDraft,
    runAudit,
    hideAuditPanel,
    confirmSubmit,
    showToast,
    switchToSplitMode,
    syncFormFromProduct,
  }
}