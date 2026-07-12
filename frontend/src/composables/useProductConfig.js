/**
 * 产品配置状态管理 composable
 * 移植自 prodai-cfg-demo/src/composables/useAppState.js
 * 适配现有项目的消息流和 formCard 机制
 */
import { ref, reactive, computed } from 'vue'
import { genId } from '../utils/chatUtils.js'
import {
  mockProducts,
  scene2Products,
  createEmptyFormData,
  createProductFormSchema,
  CAMPUS_PRODUCT_DATA,
} from '../data/productMockData.js'

export function useProductConfig() {
  const products = ref([])
  const currentProductId = ref(null)
  const formData = reactive(createEmptyFormData())
  const auditStatus = ref('pending')
  const isModified = ref(false)
  const showProductListPanel = ref(false)
  const showAuditPanel = ref(false)
  const auditResults = ref([])
  const auditPhase = ref('idle')

  const currentProduct = computed(() =>
    products.value.find((p) => p.id === currentProductId.value) ?? null,
  )

  /**
   * 同步表单数据从商品
   */
  function syncFormFromProduct(product) {
    const data = product?.data || createEmptyFormData()
    Object.keys(formData).forEach((key) => {
      if (data[key] !== undefined) {
        formData[key] = data[key]
      } else {
        formData[key] = createEmptyFormData()[key]
      }
    })
    isModified.value = false
    auditStatus.value = product?.auditStatus || 'pending'
  }

  /**
   * 构建兼容 FormPanel 的 formCard 对象
   */
  function buildProductFormCard(product) {
    const schema = createProductFormSchema(product.data)
    return {
      msgId: genId(),
      formId: product.id,
      formName: product.name,
      formCode: 'productConfig',
      status: 'filling',
      fieldCount: schema.fields.length,
      createdAt: new Date().toISOString(),
      formSchema: schema,
      formData: { ...product.data },
    }
  }

  /**
   * 添加商品并切换为当前编辑
   */
  function addProductAndActivate(product) {
    products.value.push(product)
    currentProductId.value = product.id
    syncFormFromProduct(product)
    return buildProductFormCard(product)
  }

  /**
   * 处理技能卡片点击，返回引导消息内容
   */
  function getSkillGuideMessage(type) {
    if (type === 'query') {
      return '好的，让我帮您查询历史商品，您可以快速复制配置。\n\n您可以在下方输入框输入以下任一信息进行查询：\n- 商品名称关键词（如：动感地带、5G套餐）\n- 商品编码（如：WO20250115001）\n- 部分名称（如：青春卡、校园卡）'
    }
    if (type === 'file') {
      return '好的，让我帮您导入AI方案文档。\n\n请按以下步骤操作：\n1. 点击输入框左侧的 📎 附件按钮\n2. 选择您的活动方案文档（支持 .docx、.pdf、.xlsx、.doc 格式）\n3. 系统将自动解析并批量生成商品配置'
    }
    return '好的，让我帮您通过自然语言生成商品配置。\n\n您可以用自然语言描述您的需求，例如：\n- "我要一个大学生套餐，月费50元左右"\n- "创建一个5G合约套餐，含30G流量"\n- "做一个月费99元的动感地带套餐"'
  }

  /**
   * 检测消息是否命中产品配置场景
   * 返回场景类型：'query' | 'file-parse' | 'chat-generate' | null
   */
  function detectScenario(text) {
    if (!text) return null
    const lowerText = text.toLowerCase()
    if (lowerText.includes('查询') || lowerText.includes('智查') || lowerText.includes('历史商品')) {
      return 'query'
    }
    if (lowerText.includes('导入') || lowerText.includes('方案') || lowerText.includes('文档')) {
      return 'file-parse'
    }
    if (
      lowerText.includes('套餐') ||
      lowerText.includes('校园') ||
      lowerText.includes('大学生') ||
      lowerText.includes('动感地带') ||
      lowerText.includes('5g') ||
      lowerText.includes('流量') ||
      lowerText.includes('月费') ||
      lowerText.includes('配置')
    ) {
      return 'chat-generate'
    }
    return null
  }

  /**
   * 模拟查询历史商品，返回消息数组
   */
  function simulateQuery(keyword) {
    const messages = []
    messages.push({
      id: genId(),
      role: 'assistant',
      content: `正在为您搜索包含「${keyword}」的商品...`,
      done: true,
      type: 'chat',
    })
    messages.push({
      id: genId(),
      role: 'assistant',
      content: `在历史商品库中检索到 **${mockProducts.length}** 个相关商品：`,
      done: true,
      type: 'chat',
      queryResults: mockProducts,
    })
    return messages
  }

  /**
   * 从历史商品复制配置，返回消息和 formCard
   */
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
      messages: [
        {
          id: genId(),
          role: 'assistant',
          content: `已将商品「${product.name}」添加到配置列表！正在打开配置界面...`,
          done: true,
          type: 'chat',
        },
      ],
      formCard,
    }
  }

  /**
   * 模拟文件上传解析流程，返回消息数组和 formCard
   */
  function simulateFileParse(fileName, fileSize) {
    const messages = []
    messages.push({
      id: genId(),
      role: 'assistant',
      content: `文件接收成功！正在开始解析...\n文件大小：${(fileSize / 1024).toFixed(1)} KB`,
      done: true,
      type: 'chat',
    })
    messages.push({
      id: genId(),
      role: 'assistant',
      content: '**AI正在分析文档内容...**\n- 正在提取商品清单\n- 正在识别关键字段信息\n- 正在匹配业务类型',
      done: true,
      type: 'chat',
    })
    messages.push({
      id: genId(),
      role: 'assistant',
      content: '**检测到3个待生成商品：**\n- 畅享校园卡39元套餐\n- 畅享校园卡59元套餐\n- 畅享校园卡79元套餐',
      done: true,
      type: 'chat',
    })

    const imported = scene2Products.map((p) =>
      JSON.parse(JSON.stringify({ ...p, id: 'P' + Date.now() + Math.random().toString(36).slice(2, 6) })),
    )
    imported.forEach((p) => products.value.push(p))
    const firstProduct = imported[0]
    currentProductId.value = firstProduct.id
    syncFormFromProduct(firstProduct)
    const formCard = buildProductFormCard(firstProduct)

    messages.push({
      id: genId(),
      role: 'assistant',
      content: `文档解析完成！已将 ${imported.length} 个商品添加到商品列表，正在打开第一个商品的配置界面...`,
      done: true,
      type: 'chat',
      formCard,
    })

    return { messages, formCard }
  }

  /**
   * 对话式生成商品配置，返回消息数组和 formCard
   */
  function generateProductFromChat(text) {
    const messages = []
    const isCampus = text.includes('大学生') || text.includes('校园')

    messages.push({
      id: genId(),
      role: 'assistant',
      content: '**正在分析您的需求...**\n- 理解语义意图\n- 提取关键配置参数\n- 匹配业务模板\n- 生成配置方案',
      done: true,
      type: 'chat',
    })

    if (isCampus) {
      messages.push({
        id: genId(),
        role: 'assistant',
        content: '已分析您的需求！\n检测到关键词：**大学生/校园**\n匹配业务：**个人主资费**\n已提取参数：月费49元/月、通用流量15GB，本地语音200分钟，短信20条',
        done: true,
        type: 'chat',
      })
      messages.push({
        id: genId(),
        role: 'assistant',
        content: '**正在根据历史案例推算配置...**\n- 参考同类型套餐定价策略\n- 分析校园用户消费习惯\n- 计算资源包最优配比',
        done: true,
        type: 'chat',
      })

      const newProduct = {
        id: 'P' + Date.now(),
        name: '5G-A校园卡49元G（大学生专享）',
        desc: '月费49元 | 15G流量 | 200分钟语音 | 合约36个月',
        template: 'personMainPrc',
        status: 'draft',
        auditStatus: 'pending',
        data: CAMPUS_PRODUCT_DATA(),
      }
      const formCard = addProductAndActivate(newProduct)

      messages.push({
        id: genId(),
        role: 'assistant',
        content: '配置生成完成！已在右侧打开配置表单，请查看并编辑详情。',
        done: true,
        type: 'chat',
        formCard,
      })

      return { messages, formCard }
    }

    messages.push({
      id: genId(),
      role: 'assistant',
      content: '已收到您的需求，正在分析中...\n请提供更多细节，如：套餐名称、月费、包含资源等',
      done: true,
      type: 'chat',
    })
    return { messages, formCard: null }
  }

  /**
   * 选择商品编辑
   */
  function selectProduct(id) {
    const product = products.value.find((p) => p.id === id)
    if (!product) return null
    currentProductId.value = id
    syncFormFromProduct(product)
    return buildProductFormCard(product)
  }

  /**
   * 复制商品
   */
  function copyProduct(id) {
    const product = products.value.find((p) => p.id === id)
    if (!product) return null
    const newProduct = {
      id: 'P' + Date.now(),
      name: product.name + ' (副本)',
      desc: product.desc,
      template: product.template,
      status: 'draft',
      auditStatus: 'pending',
      data: JSON.parse(JSON.stringify(product.data)),
    }
    products.value.push(newProduct)
    return newProduct
  }

  /**
   * 删除商品
   */
  function deleteProduct(id) {
    products.value = products.value.filter((p) => p.id !== id)
    if (currentProductId.value === id) {
      const first = products.value[0]
      currentProductId.value = first?.id ?? null
      if (first) {
        syncFormFromProduct(first)
        return buildProductFormCard(first)
      }
    }
    return null
  }

  /**
   * 保存草稿
   */
  function saveDraft() {
    const product = currentProduct.value
    if (!product) return
    product.data = JSON.parse(JSON.stringify(formData))
    product.name = formData.prodPrcName || product.name
    product.auditStatus = 'pending'
    auditStatus.value = 'pending'
    isModified.value = false
  }

  /**
   * 构建稽核结果
   */
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

  /**
   * 执行稽核，返回结果
   */
  function runAudit() {
    const results = buildAuditResults()
    const hasError = results.some((r) => r.type === 'error')
    auditStatus.value = hasError ? 'fail' : 'pass'
    const product = currentProduct.value
    if (product) {
      product.auditStatus = auditStatus.value
      if (!hasError) {
        product.status = 'submitted'
      }
    }
    auditResults.value = results
    return { results, hasError }
  }

  /**
   * 更新表单字段
   */
  function updateFormField(fieldCode, value) {
    formData[fieldCode] = value
    isModified.value = true
    const product = currentProduct.value
    if (product) {
      product.data = product.data || {}
      product.data[fieldCode] = value
    }
  }

  return {
    products,
    currentProductId,
    currentProduct,
    formData,
    auditStatus,
    isModified,
    showProductListPanel,
    showAuditPanel,
    auditResults,
    auditPhase,
    getSkillGuideMessage,
    detectScenario,
    simulateQuery,
    prepareProduct,
    simulateFileParse,
    generateProductFromChat,
    selectProduct,
    copyProduct,
    deleteProduct,
    saveDraft,
    runAudit,
    updateFormField,
    buildProductFormCard,
    syncFormFromProduct,
  }
}
