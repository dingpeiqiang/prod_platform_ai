/**
 * assistantModes - 助手模式配置
 *
 * 研发助手 / 运营助手的所有差异化数据集中定义。
 * AssistantShell、RdAssistantPage、OpsAssistantPage 读取此配置实现模式切换。
 */
import { ZHIDU_TEST_PROMPT } from '../data/zhiduTestDoc.js'

export const assistantModes = {
  rd: {
    navTitle: '产商品研发 · 配置与合规',
    inputPlaceholder: '描述研发配置需求；智读可粘贴/拖入方案文件',
    defaultScene: 'rd',
    sceneShortcuts: [
      {
        label: '智聊·对话配置',
        scene: 'rd.chat',
        desc: '自然语言生成配置',
        text: '给家庭用户做一个融合套餐，月费158，带500M宽带，全渠道销售',
      },
      {
        label: '智读·文件配置',
        scene: 'rd.import',
        desc: '按文档内容映射草稿',
        text: ZHIDU_TEST_PROMPT,
      },
      {
        label: '智查·历史复用',
        scene: 'rd.query',
        desc: '检索并复用历史配置',
        text: '查一下近30天大学生套餐配置',
      },
      {
        label: '智检·合规校验',
        scene: 'rd.compliance',
        desc: '按套餐信息校验已入库/未入库配置',
        text: '校验校园体验流量包0元是否符合在架规则',
      },
    ],
    tips: [
      '点击左侧场景可把示例填入输入框，确认后再发送。',
      '智聊·对话配置时，本体自动填字段并拦截冲突；大模型负责理解业务表达。',
      '智读·文件配置：支持粘贴或上传 Word/PDF/Excel/Markdown；后端抽取后映射草稿并合规。',
      '智查·历史复用：走本体事实图检索（非本地 mock），一键复制后自动 R-C* 合规（方案别名 R-CONF）。',
      '配置合规由 Java 规则引擎执行（非 Drools）；说「查看审计追溯」可打开 get_trace/explain。',
      '智检·合规校验：可校验在架已入库套餐，或先智聊/智读后再说「校验当前配置」。',
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
