/**
 * assistantModes - 助手模式配置
 *
 * 研发助手 / 运营助手的所有差异化数据集中定义。
 * AssistantShell、RdAssistantPage、OpsAssistantPage 读取此配置实现模式切换。
 */

export const assistantModes = {
  rd: {
    navTitle: '产商品研发 · 配置与合规',
    inputPlaceholder: '描述研发配置需求，例如：给家庭用户做 500M 融合套餐，月费158',
    defaultScene: 'rd',
    sceneShortcuts: [
      {
        label: '对话配置',
        scene: 'rd.chat',
        desc: '自然语言生成配置',
        text: '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售',
      },
      {
        label: '批量生成',
        scene: 'rd.import',
        desc: '方案文档一键映射',
        text: '帮我导入校园迎新方案',
      },
      {
        label: 'AI智查',
        scene: 'market_insight',
        desc: '检索并复用历史配置',
        text: '查一下近30天大学生套餐配置',
      },
      {
        label: '合规校验',
        scene: 'online_check',
        desc: '事前拦截在架冲突',
        text: '校验当前配置是否符合在架规则',
      },
    ],
    tips: [
      '点击左侧场景可一键带入示例问题。',
      '对话配置时，本体自动填字段并拦截冲突；大模型负责理解业务表达。',
      '也可上传方案文档，走批量生成映射为多套配置草稿。',
    ],
  },

  ops: {
    navTitle: '产商品运营 · 洞察与决策',
    inputPlaceholder: '描述运营分析需求，例如：分析家庭融合畅享128本月收入下滑原因',
    defaultScene: 'ops',
    sceneShortcuts: [
      {
        label: '市场洞察',
        scene: 'market_insight',
        desc: '在售商品与增长指标',
        text: '查一下在售5G套餐的增长趋势和风险商品',
      },
      {
        label: '立项研判',
        scene: 'online_check',
        desc: '上线门槛与风险红线',
        text: '评估新推出的青春卡套餐能否通过立项审核',
      },
      {
        label: '异动归因',
        scene: 'root_cause',
        desc: '收入留存根因追溯',
        text: '分析家庭融合畅享128本月收入下滑原因',
      },
      {
        label: '风险稽核',
        scene: 'risk_audit',
        desc: '零费低效批量筛查',
        text: '筛查所有在架的0元资费风险商品',
      },
    ],
    tips: [
      '四大场景：市场洞察、立项研判、异动归因、风险稽核。',
      '输入问题后，AI 自动识别意图并调用本体推理与规则引擎。',
      '结论可追溯：本体负责事实链路，规则负责红线判定。',
    ],
  },
}
