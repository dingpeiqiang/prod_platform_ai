/**
 * assistantModes - 助手模式配置
 *
 * 研发助手 / 运营助手的所有差异化数据集中定义。
 * AssistantShell、RdAssistantPage、OpsAssistantPage 读取此配置实现模式切换。
 */

export const assistantModes = {
  rd: {
    navTitle: '研发配置与批量生成',
    inputPlaceholder: '描述你的研发配置需求，例如：给家庭用户做 500M 融合套餐',
    defaultScene: 'rd',
    sceneShortcuts: [
      { label: '对话配置', scene: 'rd.chat', text: '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售' },
      { label: '批量生成', scene: 'rd.import', text: '帮我导入校园迎新方案' },
      { label: 'AI智查', scene: 'market_insight', text: '查一下近30天大学生套餐配置' },
      { label: '合规校验', scene: 'online_check', text: '校验当前配置是否符合在架规则' },
    ],
    tips: [
      '点击左侧按钮可快速发起场景对话。',
      '在对话中可随时追问，AI 会基于上下文继续回答。',
    ],
  },

  ops: {
    navTitle: '运营分析与决策支持',
    inputPlaceholder: '描述你的运营分析需求，例如：分析家庭融合畅享128本月收入下滑原因',
    defaultScene: 'ops',
    sceneShortcuts: [
      { label: '市场洞察', scene: 'market_insight', text: '查一下在售5G套餐的增长趋势和风险商品' },
      { label: '立项研判', scene: 'online_check', text: '评估新推出的青春卡套餐能否通过立项审核' },
      { label: '异动归因', scene: 'root_cause', text: '分析家庭融合畅享128本月收入下滑原因' },
      { label: '商品稽核', scene: 'risk_audit', text: '筛查所有在架的0元资费风险商品' },
    ],
    tips: [
      '运营助手支持市场洞察、立项研判、异动归因等分析场景。',
      '输入运营问题后，AI 会自动识别意图并调用本体推理。',
    ],
  },
}