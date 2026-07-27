/**
 * assistantModes - 助手模式配置
 *
 * 研发助手 / 运营助手的所有差异化数据集中定义。
 * AssistantShell、RdAssistantPage、OpsAssistantPage 读取此配置实现模式切换。
 */
import { ZHIDU_TEST_PROMPT } from '../data/zhiduTestDoc.js'

/**
 * 构建场景欢迎信息（本地展示，不走模型）。
 * @returns {{ content: string, nextSteps: string[], placeholder: string }}
 */
export function buildSceneWelcome(item = {}) {
  if (item.welcome) {
    return {
      content: item.welcome,
      nextSteps: item.nextSteps || (item.text ? [item.text] : []),
      placeholder: item.placeholder || '',
    }
  }
  const label = item.label || item.title || '该场景'
  const desc = item.desc || item.description || ''
  const example = item.example || (item.text && String(item.text).length < 120 ? item.text : '')
  const lines = [
    `### ${label}`,
    '',
    desc || '已进入该场景，可直接在下方输入你的诉求。',
    '',
    '**你可以这样开始：**',
  ]
  if (example) {
    lines.push(`- ${example}`)
  } else {
    lines.push('- 直接描述你的业务需求')
  }
  lines.push('', '点击下方推荐话术可填入输入框，确认后发送。')
  return {
    content: lines.join('\n'),
    nextSteps: item.nextSteps || (item.text && String(item.text).length < 200 ? [item.text] : []),
    placeholder: item.placeholder || '',
  }
}

function withWelcome(item) {
  const welcome = buildSceneWelcome(item)
  return {
    ...item,
    welcome: welcome.content,
    nextSteps: item.nextSteps || welcome.nextSteps,
    placeholder: item.placeholder || welcome.placeholder,
  }
}

export const assistantModes = {
  rd: {
    navTitle: '产商品研发 · 配置与合规',
    inputPlaceholder: '描述研发配置需求；智读可粘贴/拖入方案文件',
    defaultScene: 'rd',
    sceneShortcuts: [
      withWelcome({
        label: '智聊·对话配置',
        scene: 'rd.chat',
        desc: '自然语言生成配置',
        text: '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售',
        placeholder: '用自然语言描述套餐诉求，例如：家庭融合 158 元 / 500M',
        welcome: [
          '### 智聊·对话配置',
          '',
          '用自然语言描述业务诉求，本体自动补全配置字段并拦截冲突。',
          '',
          '**怎么用**',
          '1. 直接说出客群、月费、资源、渠道等关键信息',
          '2. 右侧会生成/更新配置草稿，冲突会即时提示',
          '3. 确认无误后可继续合规校验与提交',
          '',
          '**试试这些话术**',
        ].join('\n'),
        nextSteps: [
          '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售',
          '做一个大学生校园套餐，月费39，含30G流量',
        ],
      }),
      withWelcome({
        label: '智读·文件配置',
        scene: 'rd.import',
        desc: '按文档内容映射草稿',
        text: ZHIDU_TEST_PROMPT,
        example: '粘贴或上传方案文档，按内容映射配置草稿',
        placeholder: '粘贴方案正文，或拖入 Word/PDF/Excel/Markdown 文件',
        welcome: [
          '### 智读·文件配置',
          '',
          '粘贴或上传方案文档，系统按内容映射为多套配置草稿并做合规检查。',
          '',
          '**怎么用**',
          '1. 粘贴方案段落，或上传 Word / PDF / Excel / Markdown',
          '2. 等待抽取与映射完成，查看草稿清单',
          '3. 对待修正项一键修正后重跑合规',
          '',
          '**支持格式**：`.md` `.txt` `.docx` `.pdf` `.xlsx` `.csv`',
          '',
          '准备好后，粘贴内容或上传文件即可开始。',
        ].join('\n'),
        nextSteps: ['粘贴方案文档后说：请按文档内容生成配置草稿'],
      }),
      withWelcome({
        label: '智查·历史复用',
        scene: 'rd.query',
        desc: '检索并复用历史配置',
        text: '查一下近30天大学生套餐配置',
        placeholder: '输入商品名称、编码或关键词，例如：近30天大学生套餐',
        welcome: [
          '### 智查·历史复用',
          '',
          '检索历史商品与成熟配置，快速复制复用，并自动走合规校验。',
          '',
          '**怎么用**',
          '1. 输入商品名、编码或业务关键词',
          '2. 在结果中选择要复用的配置',
          '3. 一键复制后自动触发合规检查',
          '',
          '**试试这些话术**',
        ].join('\n'),
        nextSteps: [
          '查一下近30天大学生套餐配置',
          '检索家庭融合畅享相关历史配置',
        ],
      }),
      withWelcome({
        label: '智检·合规校验',
        scene: 'rd.compliance',
        desc: '按套餐信息校验已入库/未入库配置',
        text: '校验校园体验流量包0元是否符合在架规则',
        placeholder: '输入要校验的套餐信息，或说：校验当前配置',
        welcome: [
          '### 智检·合规校验',
          '',
          '按套餐信息校验：已入库在架套餐，或当前未入库配置草稿。',
          '',
          '**怎么用**',
          '1. 指定在架套餐名称/编码，或先智聊/智读生成草稿',
          '2. 发起合规校验，查看规则命中与风险项',
          '3. 按建议修正后可再次校验或提交',
          '',
          '**试试这些话术**',
        ].join('\n'),
        nextSteps: [
          '校验校园体验流量包0元是否符合在架规则',
          '校验当前配置',
        ],
      }),
      withWelcome({
        label: '多方案对比',
        scene: 'rd.compare',
        desc: '定价方案合规与收益对比',
        text: '对比方案A 39元与方案B 59元，目标市场约15万户',
        placeholder: '描述要对比的方案，例如：方案A 39元 vs 方案B 59元',
        welcome: [
          '### 多方案对比',
          '',
          '对当前草稿应用不同资费补丁，输出合规与预估收益推荐。',
          '',
          '**怎么用**',
          '1. 先有一份基础配置草稿（智聊/智读均可）',
          '2. 给出 2 个及以上对比方案（资费、资源、市场规模等）',
          '3. 查看合规结果与收益对比，择优落地',
          '',
          '**试试这些话术**',
        ].join('\n'),
        nextSteps: [
          '对比方案A 39元与方案B 59元，目标市场约15万户',
        ],
      }),
    ],
    tips: [
      '点击左侧场景标签，会直接展示该场景的欢迎说明与推荐话术。',
      '智聊·对话配置时，本体自动填字段并拦截冲突；大模型负责理解业务表达。',
      '智读·文件配置：支持粘贴或上传 Word/PDF/Excel/Markdown；后端抽取后映射草稿并合规。',
      '智查·历史复用：走本体事实图检索，一键复制后自动合规。',
      '智检·合规校验：可校验在架已入库套餐，或先智聊/智读后再说「校验当前配置」。',
      '多方案对比：对当前草稿应用不同资费补丁，输出合规与预估收益推荐。',
    ],
  },

  ops: {
    navTitle: '产商品运营 · 洞察与决策',
    inputPlaceholder: '描述运营分析需求，例如：分析家庭融合畅享128本月收入下滑原因',
    defaultScene: 'ops',
    sceneShortcuts: [
      withWelcome({
        label: '市场洞察',
        scene: 'market_insight',
        desc: '在售商品与增长指标',
        text: '查一下在售5G套餐的增长趋势和风险商品',
        placeholder: '例如：查一下在售5G套餐的增长趋势和风险商品',
        welcome: [
          '### 市场洞察',
          '',
          '自然语言检索在售商品、增长指标与风险商品，结论可追溯到运营事实图。',
          '',
          '**怎么用**',
          '1. 用自然语言描述你要看的品类/指标',
          '2. 查看增长趋势与明细面板',
          '3. 可继续追问风险商品或下钻归因',
          '',
          '**试试这些话术**',
        ].join('\n'),
        nextSteps: [
          '查一下在售5G套餐的增长趋势和风险商品',
          '列出增长为负的在售家庭融合商品',
        ],
      }),
      withWelcome({
        label: '立项研判',
        scene: 'online_check',
        desc: '上线门槛与多方案对比',
        text: '新品5G套餐立项：目标市场个人客户约8万户，对比方案A 39元与方案B 59元能否通过审核',
        placeholder: '例如：新品套餐立项，目标市场约8万户，对比 39 元与 59 元方案',
        welcome: [
          '### 立项研判',
          '',
          '评估新品是否满足上线门槛与风险红线，支持多方案对比。',
          '',
          '**怎么用**',
          '1. 说明客群、资费、市场规模等关键信息',
          '2. 可同时给出方案 A/B 做对比',
          '3. 查看门槛命中、风险结论与建议',
          '',
          '**试试这些话术**',
        ].join('\n'),
        nextSteps: [
          '新品5G套餐立项：目标市场个人客户约8万户，对比方案A 39元与方案B 59元能否通过审核',
        ],
      }),
      withWelcome({
        label: '运营监控',
        scene: 'ops_monitor',
        desc: '异动告警与一键归因',
        text: '打开运营监控告警列表',
        placeholder: '例如：打开运营监控告警列表',
        welcome: [
          '### 运营监控',
          '',
          '查看异动告警与处置工单，支持一键跳转归因分析。',
          '',
          '**怎么用**',
          '1. 打开监控面板，查看告警列表',
          '2. 对高优先级告警发起归因或生成工单',
          '3. 在工单页闭环：开始处理 → 完成',
          '',
          '**试试这些话术**',
        ].join('\n'),
        nextSteps: ['打开运营监控告警列表'],
      }),
      withWelcome({
        label: '异动归因',
        scene: 'root_cause',
        desc: '收入留存根因追溯',
        text: '分析家庭融合畅享128本月收入下滑原因',
        placeholder: '例如：分析家庭融合畅享128本月收入下滑原因',
        welcome: [
          '### 异动归因',
          '',
          '多跳关联推理，定位收入、留存、渠道变化主因，结论可追溯规则与证据。',
          '',
          '**怎么用**',
          '1. 说明商品与异动现象（下滑/离网/环比等）',
          '2. 查看根因路径、证据与规则引用',
          '3. 可继续生成优化工单或复盘建议',
          '',
          '**试试这些话术**',
        ].join('\n'),
        nextSteps: [
          '分析家庭融合畅享128本月收入下滑原因',
        ],
      }),
      withWelcome({
        label: '风险稽核',
        scene: 'risk_audit',
        desc: '零费低效批量筛查',
        text: '筛查所有在架的0元资费风险商品',
        placeholder: '例如：筛查所有在架的0元资费风险商品',
        welcome: [
          '### 风险稽核',
          '',
          '批量识别零费、低效与长期零销商品，并给出处置建议。',
          '',
          '**怎么用**',
          '1. 发起在架风险筛查',
          '2. 查看高/中风险清单与建议下架项',
          '3. 可生成风险处置工单或复审',
          '',
          '**试试这些话术**',
        ].join('\n'),
        nextSteps: [
          '筛查所有在架的0元资费风险商品',
        ],
      }),
      withWelcome({
        label: '规则运营',
        scene: 'ops_rules',
        desc: '阈值覆盖与规则目录',
        text: '打开规则运营面板',
        placeholder: '规则运营以右侧面板为主，也可说：打开规则运营面板',
        welcome: [
          '### 规则运营',
          '',
          '查看 R-A/B/C/D 规则目录，调整风险阈值覆盖，查看变更审计或热重载规则。',
          '',
          '**怎么用**',
          '1. 打开右侧规则运营面板',
          '2. 浏览目录 / 调整阈值覆盖',
          '3. 保存后可热重载 `ops_rules.json`',
          '',
          '点击下方推荐话术，或直接使用右侧面板。',
        ].join('\n'),
        nextSteps: ['打开规则运营面板'],
      }),
    ],
    tips: [
      '点击左侧场景标签，会直接展示该场景的欢迎说明与推荐话术。',
      '入口：市场洞察、立项研判、运营监控、异动归因、风险稽核、规则运营。',
      '五类意图走真实 SSE；规则运营为 REST 面板（阈值覆盖 / 目录 / 审计 / 热重载）。',
      '运营监控含「告警 / 处置工单」双页：工单可开始处理→完成闭环。',
    ],
  },
}
