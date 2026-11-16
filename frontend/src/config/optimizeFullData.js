/**
 * 优化决策方案 - ECharts 图表配置 + 结构化数据
 * 对照文档：第5轮-优化决策.md
 */

// ============ 方案A：新产品配置对比柱状图 ============
export const productCompareChartOption = {
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(30, 41, 59, 0.92)',
    borderColor: 'transparent',
    textStyle: { color: '#f1f5f9', fontSize: 12 },
    axisPointer: { type: 'shadow' },
  },
  legend: {
    data: ['现有产品（SP-HK-TW-30D）', '新产品（SP-HK-TW-30D-HIGH）'],
    top: 0,
    textStyle: { fontSize: 11, color: '#64748b' },
    itemWidth: 12,
    itemHeight: 10,
  },
  grid: { left: 60, right: 30, top: 40, bottom: 30 },
  xAxis: {
    type: 'category',
    data: ['高速配额(GB)', '有效期(天)', '漫游地(个)', '一次性资费(元)'],
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { fontSize: 11, color: '#475569' },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    axisLabel: { fontSize: 10, color: '#64748b' },
    splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
  },
  series: [
    {
      name: '现有产品（SP-HK-TW-30D）',
      type: 'bar',
      data: [30, 30, 2, 168],
      barWidth: 24,
      itemStyle: { color: '#94a3b8', borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', fontSize: 11, color: '#64748b' },
    },
    {
      name: '新产品（SP-HK-TW-30D-HIGH）',
      type: 'bar',
      data: [50, 30, 3, 198],
      barWidth: 24,
      itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', fontSize: 11, color: '#1e293b', fontWeight: 'bold' },
    },
  ],
}

// ============ 方案A：量化收益预测柱状图 ============
export const revenueForecastChartOption = {
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(30, 41, 59, 0.92)',
    borderColor: 'transparent',
    textStyle: { color: '#f1f5f9', fontSize: 12 },
    axisPointer: { type: 'shadow' },
    formatter: (params) => {
      let html = `<div style="font-weight:600;margin-bottom:4px">${params[0].name}</div>`
      params.forEach((p) => {
        html += `<div>${p.marker} ${p.seriesName}: <b>${p.value}</b></div>`
      })
      return html
    },
  },
  legend: {
    data: ['现有产品(近7日均值)', '新产品预期', '增量'],
    top: 0,
    textStyle: { fontSize: 11, color: '#64748b' },
    itemWidth: 12,
    itemHeight: 10,
  },
  grid: { left: 70, right: 30, top: 40, bottom: 30 },
  xAxis: {
    type: 'category',
    data: ['日均新增(单)', '月新增用户(户)', '月新增收入(万元)', '月净增收益(万元)'],
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { fontSize: 10, color: '#64748b', interval: 0 },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    axisLabel: { fontSize: 10, color: '#64748b' },
    splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
  },
  series: [
    {
      name: '现有产品(近7日均值)',
      type: 'bar',
      data: [177, 5310, 89.2, 0],
      barWidth: 20,
      itemStyle: { color: '#94a3b8', borderRadius: [4, 4, 0, 0] },
    },
    {
      name: '新产品预期',
      type: 'bar',
      data: [400, 12000, 237.6, 61.4],
      barWidth: 20,
      itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', fontSize: 10, color: '#1e293b', fontWeight: 'bold' },
    },
    {
      name: '增量',
      type: 'bar',
      data: [223, 6690, 148.4, 61.4],
      barWidth: 20,
      itemStyle: { color: '#10b981', borderRadius: [4, 4, 0, 0] },
      label: {
        show: true,
        position: 'top',
        fontSize: 10,
        color: '#059669',
        fontWeight: 'bold',
        formatter: '+{c}',
      },
    },
  ],
}

// ============ 方案A：风险评估矩阵图 ============
export const riskAssessChartOption = {
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(30, 41, 59, 0.92)',
    borderColor: 'transparent',
    textStyle: { color: '#f1f5f9', fontSize: 12 },
    axisPointer: { type: 'shadow' },
    formatter: (params) => {
      const p = params[0]
      const levels = ['🟢 低', '🟢 低', '🟢 低', '🟢 低', '🟡 中低', '🟡 中低']
      return `<div style="font-weight:600">${p.name}</div><div>风险等级: ${levels[p.dataIndex]}</div>`
    },
  },
  grid: { left: 140, right: 60, top: 20, bottom: 30 },
  xAxis: {
    type: 'value',
    max: 100,
    axisLine: { show: false },
    axisLabel: {
      fontSize: 10,
      color: '#64748b',
      formatter: (val) => {
        if (val === 20) return '低'
        if (val === 50) return '中低'
        if (val === 80) return '中'
        if (val === 100) return '高'
        return ''
      },
    },
    splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
  },
  yAxis: {
    type: 'category',
    data: ['与WeWalk 15日定位区隔', '竞品后续跟进', '资费审批门槛', '互斥规则冲突', '漫游结算成本波动', '存量用户影响'],
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { fontSize: 11, color: '#475569' },
    axisTick: { show: false },
    inverse: true,
  },
  series: [
    {
      type: 'bar',
      data: [
        { value: 20, itemStyle: { color: '#10b981', borderRadius: [0, 4, 4, 0] } },
        { value: 35, itemStyle: { color: '#f59e0b', borderRadius: [0, 4, 4, 0] } },
        { value: 15, itemStyle: { color: '#10b981', borderRadius: [0, 4, 4, 0] } },
        { value: 15, itemStyle: { color: '#10b981', borderRadius: [0, 4, 4, 0] } },
        { value: 15, itemStyle: { color: '#10b981', borderRadius: [0, 4, 4, 0] } },
        { value: 15, itemStyle: { color: '#10b981', borderRadius: [0, 4, 4, 0] } },
      ],
      barWidth: 16,
      label: {
        show: true,
        position: 'right',
        fontSize: 11,
        color: '#1e293b',
        fontWeight: 'bold',
        formatter: (params) => {
          const labels = ['🟢 低', '🟢 低', '🟢 低', '🟢 低', '🟡 中低', '🟡 中低']
          return labels[params.dataIndex]
        },
      },
    },
  ],
}

// ============ 方案B：下线评估指标图 ============
export const offlineAssessChartOption = {
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(30, 41, 59, 0.92)',
    borderColor: 'transparent',
    textStyle: { color: '#f1f5f9', fontSize: 12 },
    axisPointer: { type: 'shadow' },
  },
  grid: { left: 130, right: 80, top: 20, bottom: 30 },
  xAxis: {
    type: 'value',
    axisLine: { show: false },
    axisLabel: { fontSize: 10, color: '#64748b' },
    splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
  },
  yAxis: {
    type: 'category',
    data: ['预计投诉风险', '预计迁移周期(月)', '承接产品(个)', '在途激活用户(户)', '月收入贡献(万元)', '累计销售(户)'],
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { fontSize: 10, color: '#475569' },
    axisTick: { show: false },
    inverse: true,
  },
  series: [
    {
      type: 'bar',
      data: [
        { value: 4, itemStyle: { color: '#ef4444', borderRadius: [0, 4, 4, 0] } },
        { value: 5, itemStyle: { color: '#ef4444', borderRadius: [0, 4, 4, 0] } },
        { value: 0, itemStyle: { color: '#ef4444', borderRadius: [0, 4, 4, 0] } },
        { value: 32, itemStyle: { color: '#f59e0b', borderRadius: [0, 4, 4, 0] } },
        { value: 51, itemStyle: { color: '#ef4444', borderRadius: [0, 4, 4, 0] } },
        { value: 86, itemStyle: { color: '#ef4444', borderRadius: [0, 4, 4, 0] } },
      ],
      barWidth: 16,
      label: {
        show: true,
        position: 'right',
        fontSize: 11,
        color: '#1e293b',
        fontWeight: 'bold',
        formatter: (params) => {
          const labels = ['3-5%（高）', '4-6个月', '无', '86,420', '12,080', '86,420']
          return labels[params.dataIndex]
        },
      },
    },
  ],
}
// ============ 完整版 blocks 构建 ============
export function buildOptimizeFullBlocks() {
  return [
    {
      type: 'markdown',
      data: '**【助手·产品研发助手 + 产品运营助手 协同】**\n\n> 基于以上四维分析，已形成明确结论：本次异动为典型的**竞争性新增萎缩**，核心矛盾集中在高速配额配置落后于竞品均值29%、叠加年轻客群在信息流渠道被竞品截流。存量漫游用户基本稳定，产品自身未出现衰退迹象。\n\n> 综合判断后，给出两类决策方向。**方案A为当前推荐路径**，以下逐一展开。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 方案A：新产品策划（⭐ 推荐）\n\n> **核心策略**：在现有WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)基础上，**策划一款更高配的数据卡**，与现有产品（SP-HK-TW-30D）并行销售，新用户默认引导至新产品，存量用户不受影响。',
    },
    {
      type: 'markdown',
      data: '### 一、新产品配置方案\n\n| 项目 | 现有产品（SP-HK-TW-30D） | **新产品（建议）** | 调整说明 |\n| :--- | :--- | :--- | :--- |\n| 产品编码 | SP-HK-TW-30D | **SP-HK-TW-30D-HIGH**（新编码） | 独立产品，不与现有产品覆盖 |\n| 资费类型 | 一次性 | **一次性** | 不变，新产品需走资费审批流程 |\n| 高速配额 | 30GB（总量） | **50GB（总量）** | +20GB，追平友商C（大湾区伴游30日）（50GB） |\n| 有效期 | 30天 | **30天** | 不变，维持自然天 |\n| 漫游地 | 香港.台湾 | **香港.台湾+澳门（3地共用）** | +澳门，补齐友商A优势 |\n| 一次性资费 | 168元 | **198元** | 增加30元，权衡成本与吸引力 |\n| 高速用尽后 | 不限量限速（PCRF） | **不限量限速（PCRF）** | 维持FUP规则 |\n| 存量用户影响 | — | **无** | 新老产品并行销售，互不干扰 |\n| 新老产品关系 | — | **互斥** | 用户不可同时订购，避免重复 |',
    },
    {
      type: 'markdown',
      data: '> **调整依据**：\n>\n> - 高速配额（30GB→50GB）：竞品均值为42.5GB，友商C已给到50GB高速。50GB可实现与友商C持平、显著领先友商A/B，同时维持一次性资费的合理定价；\n> - 漫游地（香港.台湾→+澳门）：友商A（游讯1日卡）已新增澳门漫游（7/1），必须补齐，以稳住年轻出境客群；\n> - 网络速率维持5G，与新产品定位一致。',
    },
    {
      type: 'markdown',
      data: '### 二、量化收益预测\n\n> 新产品上线后的预期收益测算（仅针对新增用户部分，存量86,420户不纳入计算）：',
    },
    {
      type: 'markdown',
      data: '| 指标 | 现有产品（近7日均值） | **新产品预期** | 增量 |\n| :--- | :--- | :--- | :--- |\n| 日均新增 | 177单 | **380~420单** | +203~243单 |\n| 月新增用户数 | 5,310户 | **11,400~12,600户** | +6,090~7,290户 |\n| 月新增收入贡献 | — | **225.7~249.5万元**（新增用户首月） | — |\n| 月新增漫游结算成本 | — | 3.2~3.5万元 | — |\n| **月净增收益** | — | **+57.9~64.9万元** | — |',
    },
    {
      type: 'markdown',
      data: '> **计算口径说明**：\n>\n> - 月新增用户 = 日均新增 × 30天；\n> - 月新增收入 = 月新增用户 × 198元一次性资费；\n> - 月新增漫游结算成本 = 月新增用户 × 约28.4元/户（新增澳门覆盖的结算成本）；\n> - 以上测算**仅针对新增用户部分**，现有产品86,420户存量用户的收入与成本不变，不纳入计算。',
    },
    {
      type: 'markdown',
      data: '### 三、风险评估\n\n> 方案A的风险评估矩阵，6项风险指标中3项为低风险、3项为中低风险：',
    },
    {
      type: 'markdown',
      data: '| 风险项 | 等级 | 说明 |\n| :--- | :--- | :--- |\n| 存量用户影响 | 🟢 低 | 新产品独立编码，与现有产品并行销售，互斥规则确保用户不重复订购，存量漫游用户不受影响 |\n| 漫游结算成本波动 | 🟢 低 | 澳门漫游覆盖（CMCC-ROAM-HK-TW-MO）已与合作伙伴签订结算协议，成本锁定 |\n| 互斥规则冲突 | 🟢 低 | 新产品继承现有产品的互斥规则（与WeWalk 15日不限量、WeWalk 30日3GB/日高配互斥），经事中预检无冲突 |\n| 资费审批门槛 | 🟡 中低 | 新产品为独立编码，需走资费审批流程，预计审批周期3-5个工作日 |\n| 竞品后续跟进 | 🟡 中低 | 新产品配置已追平友商A/C（50GB高速+3地覆盖），但若竞品继续升级，需持续监控并保留后续调整空间 |\n| 与WeWalk 15日定位区隔 | 🟡 中低 | 新产品（50GB高速+3地）与WeWalk 15日不限量（15GB高速、香港.台湾）差距拉大，建议后续评估15日是否同步调整 |',
    },
    {
      type: 'markdown',
      data: '> **关于互斥规则的补充说明**：新产品与WeWalk 15日不限量保持互斥，用户在订购时二选一。事中预检提示：新产品上线后，WeWalk 30日高配与WeWalk 15日之间的高速配额差距将从15GB拉大至35GB，漫游地差距从0地拉大至1地。建议本次配置保持互斥规则不变，后续2-4周内评估WeWalk 15日的配置是否需要同步调整，以维持两档数据卡之间的定位区隔。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 方案B：产品下线（⚠️ 当前不推荐）\n\n> 列举方案B的目的在于说明**当前不下线的核心理由**，以供对照参考。',
    },
    {
      type: 'markdown',
      data: '| 评估项 | 数据 | 不推荐理由 |\n| :--- | :--- | :--- |\n| 累计销售 | **86,420户** | 存量用户基数庞大，无有效承接方案 |\n| 月收入贡献 | **约1,451万元** | 直接收入损失过大，且无对冲手段 |\n| 在途激活用户 | **约32,000户（30日有效期内）** | 提前下线需承担已经激活套餐的服务义务，无法随意终止 |\n| 承接产品 | **无** | 30日档位当前仅有此一款产品，无可直接承接的替代产品 |\n| 预计迁移周期 | **4-6个月** | 用户逐批迁移，成本高、周期长 |\n| 预计投诉风险 | **高（3-5%）** | 在途用户被动处理，投诉率将远超正常水平（正常<1%） |',
    },
    {
      type: 'markdown',
      data: '> **不下线的核心理由**：累计销售86,420户、月收入超1,400万、在途激活用户超3万，三个指标均指向**高退出成本、高执行风险、低可行性**。\n\n> **下线适用条件**（供参考）：\n> 1. 在途激活用户自然到期比例>80%；\n> 2. 已有明确的1:1承接数据卡上线；\n> 3. 总累计销售自然衰减至2万以下。\n>\n> 三个条件满足后方具备下线可行性。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 决策建议\n\n> **综合评估结论**：\n>\n> - **方案A（新产品策划）**：低风险、高收益、可快速落地，预期月净增收益58-65万元，3项低风险+3项中低风险，需走资费审批流程（预计3-5个工作日）。**强烈推荐**。\n> - **方案B（产品下线）**：高退出成本、高执行风险、低可行性，当前不满足下线的三个必要条件。**当前不推荐**。\n\n> 请确认决策方向后，我将根据选择启动对应的执行流程。',
    },
  ]
}

// ============ 下载用的纯文本版本 ============
export function buildOptimizeFullText() {
  return `**【助手·产品研发助手 + 产品运营助手 协同】**

> 基于以上四维分析，已形成明确结论：本次异动为典型的**竞争性新增萎缩**，核心矛盾集中在高速配额配置落后于竞品均值29%、叠加年轻客群在信息流渠道被竞品截流。存量漫游用户基本稳定，产品自身未出现衰退迹象。

> 综合判断后，给出两类决策方向。**方案A为当前推荐路径**，以下逐一展开。

---

## 方案A：新产品策划（⭐ 推荐）

> **核心策略**：在现有WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)基础上，**策划一款更高配的数据卡**，与现有产品（SP-HK-TW-30D）并行销售，新用户默认引导至新产品，存量用户不受影响。

### 一、新产品配置方案

| 项目 | 现有产品（SP-HK-TW-30D） | **新产品（建议）** | 调整说明 |
| :--- | :--- | :--- | :--- |
| 产品编码 | SP-HK-TW-30D | **SP-HK-TW-30D-HIGH**（新编码） | 独立产品，不与现有产品覆盖 |
| 资费类型 | 一次性 | **一次性** | 不变，新产品需走资费审批流程 |
| 高速配额 | 30GB（总量） | **50GB（总量）** | +20GB，追平友商C（大湾区伴游30日）（50GB） |
| 有效期 | 30天 | **30天** | 不变，维持自然天 |
| 漫游地 | 香港.台湾 | **香港.台湾+澳门（3地共用）** | +澳门，补齐友商A优势 |
| 一次性资费 | 168元 | **198元** | 增加30元，权衡成本与吸引力 |
| 高速用尽后 | 不限量限速（PCRF） | **不限量限速（PCRF）** | 维持FUP规则 |
| 存量用户影响 | — | **无** | 新老产品并行销售，互不干扰 |
| 新老产品关系 | — | **互斥** | 用户不可同时订购，避免重复 |

> **调整依据**：
>
> - 高速配额（30GB→50GB）：竞品均值为42.5GB，友商C已给到50GB高速。50GB可实现与友商C持平、显著领先友商A/B，同时维持一次性资费的合理定价；
> - 漫游地（香港.台湾→+澳门）：友商A（游讯1日卡）已新增澳门漫游（7/1），必须补齐，以稳住年轻出境客群；
> - 网络速率维持5G，与新产品定位一致。

### 二、量化收益预测

| 指标 | 现有产品（近7日均值） | **新产品预期** | 增量 |
| :--- | :--- | :--- | :--- |
| 日均新增 | 177单 | **380~420单** | +203~243单 |
| 月新增用户数 | 5,310户 | **11,400~12,600户** | +6,090~7,290户 |
| 月新增收入贡献 | — | **225.7~249.5万元**（新增用户首月） | — |
| 月新增漫游结算成本 | — | 3.2~3.5万元 | — |
| **月净增收益** | — | **+57.9~64.9万元** | — |

> **计算口径说明**：
>
> - 月新增用户 = 日均新增 × 30天；
> - 月新增收入 = 月新增用户 × 198元一次性资费；
> - 月新增漫游结算成本 = 月新增用户 × 约28.4元/户（新增澳门覆盖的结算成本）；
> - 以上测算**仅针对新增用户部分**，现有产品86,420户存量用户的收入与成本不变，不纳入计算。

### 三、风险评估

| 风险项 | 等级 | 说明 |
| :--- | :--- | :--- |
| 存量用户影响 | 🟢 低 | 新产品独立编码，与现有产品并行销售，互斥规则确保用户不重复订购，存量漫游用户不受影响 |
| 漫游结算成本波动 | 🟢 低 | 澳门漫游覆盖（CMCC-ROAM-HK-TW-MO）已与合作伙伴签订结算协议，成本锁定 |
| 互斥规则冲突 | 🟢 低 | 新产品继承现有产品的互斥规则（与WeWalk 15日不限量、WeWalk 30日3GB/日高配互斥），经事中预检无冲突 |
| 资费审批门槛 | 🟡 中低 | 新产品为独立编码，需走资费审批流程，预计审批周期3-5个工作日 |
| 竞品后续跟进 | 🟡 中低 | 新产品配置已追平友商A/C（50GB高速+3地覆盖），但若竞品继续升级，需持续监控并保留后续调整空间 |
| 与WeWalk 15日定位区隔 | 🟡 中低 | 新产品（50GB高速+3地）与WeWalk 15日不限量（15GB高速、香港.台湾）差距拉大，建议后续评估15日是否同步调整 |

> **关于互斥规则的补充说明**：新产品与WeWalk 15日不限量保持互斥，用户在订购时二选一。事中预检提示：新产品上线后，WeWalk 30日高配与WeWalk 15日之间的高速配额差距将从15GB拉大至35GB，漫游地差距从0地拉大至1地。建议本次配置保持互斥规则不变，后续2-4周内评估WeWalk 15日的配置是否需要同步调整，以维持两档数据卡之间的定位区隔。

---

## 方案B：产品下线（⚠️ 当前不推荐）

> 列举方案B的目的在于说明**当前不下线的核心理由**，以供对照参考。

| 评估项 | 数据 | 不推荐理由 |
| :--- | :--- | :--- |
| 累计销售 | **86,420户** | 存量用户基数庞大，无有效承接方案 |
| 月收入贡献 | **约1,451万元** | 直接收入损失过大，且无对冲手段 |
| 在途激活用户 | **约32,000户（30日有效期内）** | 提前下线需承担已经激活套餐的服务义务，无法随意终止 |
| 承接产品 | **无** | 30日档位当前仅有此一款产品，无可直接承接的替代产品 |
| 预计迁移周期 | **4-6个月** | 用户逐批迁移，成本高、周期长 |
| 预计投诉风险 | **高（3-5%）** | 在途用户被动处理，投诉率将远超正常水平（正常<1%） |

> **不下线的核心理由**：累计销售86,420户、月收入超1,400万、在途激活用户超3万，三个指标均指向**高退出成本、高执行风险、低可行性**。

> **下线适用条件**（供参考）：
> 1. 在途激活用户自然到期比例>80%；
> 2. 已有明确的1:1承接数据卡上线；
> 3. 总累计销售自然衰减至2万以下。
>
> 三个条件满足后方具备下线可行性。

---

## 决策建议

> **综合评估结论**：
>
> - **方案A（新产品策划）**：低风险、高收益、可快速落地，预期月净增收益58-65万元，3项低风险+3项中低风险，需走资费审批流程（预计3-5个工作日）。**强烈推荐**。
> - **方案B（产品下线）**：高退出成本、高执行风险、低可行性，当前不满足下线的三个必要条件。**当前不推荐**。

> 请确认决策方向后，我将根据选择启动对应的执行流程。`
}
