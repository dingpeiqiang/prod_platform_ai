/**
 * queryAssistantData - 产品查询助手数据层（mock）
 *
 * 三个场景共用：
 *   1. 智能问答（query.scene1）：自然语言问答 + 制度知识库（带引用来源）
 *   2. 档案调阅（query.scene2）：产品档案检索与只读档案调阅（复用 stockProductData + 草稿库）
 *   3. 比对分析（query.scene3）：多产品横向比对（表格 + 图表 + 结论）
 *
 * 数据源：
 *   - stockProducts（存量产品库，已在架产品）
 *   - draftStore.draftList（跨场景串联：对话式/批量配置生成的草稿产品也可查询）
 *   - knowledgeBase（制度/规范类知识条目，问答带引用来源）
 */
import { searchStockProducts, stockProducts } from './stockProductData'

// ============ 制度知识库（RAG 风格：回答带引用来源） ============
export const knowledgeBase = [
  {
    id: 'KB-001',
    title: '资费产品备案公示管理规范（2026版）',
    source: '集团公司市场部文件 · 市场〔2026〕12号',
    updatedAt: '2026-03-15',
    keywords: ['备案', '公示', '备案公示', '公示期', '监管'],
    summary: '新上市资费产品须在测试审核通过后 10 个工作日内发起备案公示，公示期不少于 5 个工作日。',
    detail: [
      '一、备案范围：面向公众销售的套餐类、增值类资费产品均须备案；仅面向内部测试的产品可豁免。',
      '二、备案时限：测试审核通过后 10 个工作日内提交；公示期不少于 5 个工作日。',
      '三、公示内容：资费标准、计费规则、有效期、办理渠道、退订方式，须与工作台配置完全一致。',
      '四、变更要求：已备案产品资费要素变更（月费、资源量、套外费率）须重新备案；仅展示类信息变更走备案变更流程。',
      '五、罚则：未备案先售的，按《电信服务监管办法》追责，并纳入省公司考核。',
    ],
  },
  {
    id: 'KB-002',
    title: '产品命名与编码规则',
    source: '产品管理中心规范 · 产管〔2025〕07号',
    updatedAt: '2025-11-02',
    keywords: ['命名', '编码', '产品名称', '产品编码', '命名规则'],
    summary: '产品编码采用 SP + 8位日期序号（如 SP2024059001）；产品名称须体现资费档位，禁用"最优惠""第一"等绝对化用语。',
    detail: [
      '一、编码规则：SP + 8位日期序号 + 2位流水（示例：SP2024059001），一级产品编码 APAP + 5位数字。',
      '二、命名要求：① 体现档位（如 59元套餐）；② 体现定位（如 校园/政企/5G）；③ 禁用绝对化用语（最优惠/第一/国家级）；④ 同档位重名须加定位后缀区分。',
      '三、命名示例：畅享59元套餐、智享99元5G套餐、畅享校园卡39元套餐。',
    ],
  },
  {
    id: 'KB-003',
    title: '资费产品测试审核工作指引',
    source: '网络与测试中心 · 测试〔2025〕33号',
    updatedAt: '2025-09-20',
    keywords: ['测试', '测试审核', '测试报告', '固费核查', '计费核查'],
    summary: '产品上架前须完成四类核查：固费核查、计费核查、提醒核查、工单核查，全部通过方可进入备案公示。',
    detail: [
      '一、测试方式：新业务测试（含固费核查、计费核查、提醒核查、工单核查）。',
      '二、固费核查：月租固定费与配置一致、固定费生效时间符合受理协议。',
      '三、计费核查：流量/语音/短信套内不计费、套外按配置费率自动叠加。',
      '四、提醒核查：流量使用达 80%/95% 提醒下发、订购成功欢迎短信下发。',
      '五、工单核查：订购/退订工单正确生成与流转；退订按受理规则次月生效。',
      '六、时限：受理后 5 个工作日内出具测试报告，结论分"通过/不通过/有条件通过"。',
    ],
  },
  {
    id: 'KB-004',
    title: '产品上下线流程与生命周期管理',
    source: '产品管理中心规范 · 产管〔2025〕11号',
    updatedAt: '2026-01-08',
    keywords: ['上下线', '上线', '下线', '生命周期', '退市', '流程'],
    summary: '产品全生命周期六阶段：方案设计 → 产品配置 → 预览审批 → 测试审核 → 备案公示 → 上架销售；下线须提前 30 天公告。',
    detail: [
      '一、上线流程：方案设计确认 → 资费配置 → 提交预览审批 → 审批通过后测试 → 测试通过后备案公示 → 公示无异议选择销售渠道上架。',
      '二、审批权限：月费 ≤ 100 元由省公司产品委员会审批；> 100 元须报集团公司备案审批。',
      '三、下线流程：提前 30 天向存量用户公告，提供平移套餐；下线产品保留档案 3 年备查。',
      '四、状态定义：草稿 → 待审核 → 审批通过 → 测试中 → 待备案公示 → 备案公示中 → 待选择销售渠道 → 销售中。',
    ],
  },
  {
    id: 'KB-005',
    title: '校园市场资费产品专项管理要求',
    source: '市场经营部 · 市营〔2026〕05号',
    updatedAt: '2026-07-30',
    keywords: ['校园', '学生', '校园卡', '学生证', '校园市场'],
    summary: '校园套餐须实名+学生证双重认证，单人限办 1 张，不得以赠费方式变相低于成本销售。',
    detail: [
      '一、受理限制：目标客群限在校大学生，须上传学生证认证，仅限本人实名办理，每位学生限办 1 张。',
      '二、资费要求：不得以赠费、返券等方式变相低于成本销售；赠费类优惠须在备案中说明成本来源。',
      '三、渠道要求：校园渠道受理须现场实名核验，禁止网络渠道异地批量开卡。',
      '四、时间窗口：开学季（8-9月）专项营销方案须提前 15 个工作日报备。',
    ],
  },
]

// 按关键词匹配知识库条目（简单打分：标题 3 分 / 关键词 2 分 / 摘要+正文 1 分）
export function searchKnowledge(text) {
  const t = (text || '').trim()
  if (!t) return []
  return knowledgeBase
    .map((kb) => {
      let score = 0
      if (kb.title.includes(t)) score += 3
      kb.keywords.forEach((k) => { if (t.includes(k)) score += 2 })
      if (kb.summary.includes(t)) score += 1
      kb.detail.forEach((d) => { if (d.includes(t.slice(0, 6))) score += 1 })
      return { ...kb, score }
    })
    .filter((x) => x.score > 0)
    .sort((a, b) => b.score - a.score)
}

// ============ 问答意图识别 ============
// 已知产品名集合（存量 + 运行时由 ChatPage 注入草稿名）
const productAliasMap = [
  { alias: '畅享59', code: 'SP2024059001' },
  { alias: '动感地带59', code: 'SP2024061203' },
  { alias: '校园卡39', code: 'SP2024070815' },
  { alias: '智享99', code: 'SP2024082002' },
]

// 从问题中提取产品（优先全名，其次别名；extraProducts 为草稿等运行时产品）
export function extractProductFromText(text, extraProducts = []) {
  const t = text || ''
  // 引号内名称优先
  const quoted = t.match(/[“"']([^“”"']{2,20})[”"']/)
  if (quoted) {
    const hit = findProductByName(quoted[1], extraProducts)
    if (hit) return hit
  }
  // 存量产品全名/别名匹配（长名优先）
  const all = [
    ...stockProducts.map((p) => ({ name: p.name, code: p.code, from: 'stock' })),
    ...productAliasMap.map((a) => ({ name: a.alias, code: a.code, from: 'alias' })),
    ...extraProducts.map((p) => ({ name: p.name, code: p.code, from: 'draft' })),
  ].sort((a, b) => b.name.length - a.name.length)
  // 产品编码匹配（如 SP2024082002 / sp2024082002），支持大小写，优先级靠前
  const codeHit = all.find((p) => p.code && t.toLowerCase().includes(p.code.toLowerCase()))
  if (codeHit) return { name: codeHit.name, code: codeHit.code, from: codeHit.from }
  for (const p of all) {
    if (t.includes(p.name)) return { name: p.name, code: p.code, from: p.from }
  }
  return null
}

function findProductByName(name, extraProducts = []) {
  const all = [
    ...stockProducts.map((p) => ({ name: p.name, code: p.code, from: 'stock' })),
    ...extraProducts.map((p) => ({ name: p.name, code: p.code, from: 'draft' })),
  ]
  return all.find((p) => p.name === name || p.name.includes(name) || name.includes(p.name)) || null
}

// 问答意图分类：档案要素问询（流量/月费…）/ 状态问询 / 制度问询
export function classifyQuestion(text) {
  const t = text || ''
  if (/(备案|公示|命名|编码|命名规则|测试审核|测试流程|上下线|下线|退市|生命周期|校园.{0,6}(要求|规定|限制))/.test(t) &&
      !/(套餐|流量|语音|月费|月租)/.test(t)) {
    return 'policy'
  }
  if (/(多少|包含|有什么|套餐内容|资费|流量|语音|短信|月费|月租|权益|套外)/.test(t)) return 'profile'
  if (/(状态|版本|上架|销售中|还在|下架|归哪个|谁负责|负责团队)/.test(t)) return 'status'
  return 'general'
}

// ============ 比对分析 ============
// 从文本中提取 2 个及以上待比对产品
export function extractCompareProducts(text, extraProducts = []) {
  const t = text || ''
  const found = []
  const all = [
    ...stockProducts.map((p) => ({ name: p.name, code: p.code, from: 'stock' })),
    ...extraProducts.map((p) => ({ name: p.name, code: p.code, from: 'draft' })),
  ].sort((a, b) => b.name.length - a.name.length)
  const seen = new Set()
  for (const p of all) {
    if (t.includes(p.name) && !seen.has(p.code)) {
      seen.add(p.code)
      found.push(p)
    }
  }
  // 兜底：未识别出具体产品但明确是比对意图时，默认取前两档档位差大的存量产品
  return found
}

// 统一产品档案视图（存量 snapshot / 草稿 parsedFields 归一化），供比对面板消费
export function buildCompareItem(name, code, from, detail) {
  const v = detail.values || detail.parsedFields || {}
  const num = (x) => { const n = parseFloat(x); return isNaN(n) ? 0 : n }
  return {
    name,
    code,
    from,
    status: detail.status || (from === 'draft' ? 'draft' : 'listed'),
    monthlyFee: num(v.monthlyFee),
    flowGB: v.flowUnit === 'MB' ? num(v.flowAmount) / 1024 : num(v.flowAmount),
    voiceMin: num(v.voiceAmount),
    smsCount: num(v.smsAmount),
    flowOvercharge: v.flowOvercharge || '--',
    voiceOvercharge: v.voiceOvercharge || '--',
    rights: (v.rightsTable || []).map((r) => r.rightName).join('、') || '无',
    targetGroup: (v.preLimitTable || []).find((r) => r.label === 'targetGroup')?.limitValue || '公众客户',
    channels: (v.channelType && v.channelType.length)
      ? v.channelType.map((c) => ({ offline: '线下营业厅', online: '网上营业厅' }[c] || c)).join('/')
      : '线下营业厅/网上营业厅',
    tags: detail.tags || [],
  }
}
