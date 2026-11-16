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
    data: ['现有产品（SP-2024-0590）', '新产品（SP-2024-0591）'],
    top: 0,
    textStyle: { fontSize: 11, color: '#64748b' },
    itemWidth: 12,
    itemHeight: 10,
  },
  grid: { left: 60, right: 30, top: 40, bottom: 30 },
  xAxis: {
    type: 'category',
    data: ['月费(元)', '国内流量(GB)', '视频权益(个)', '权益成本(元/户/月)'],
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
      name: '现有产品（SP-2024-0590）',
      type: 'bar',
      data: [59, 30, 1, 3.0],
      barWidth: 24,
      itemStyle: { color: '#94a3b8', borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', fontSize: 11, color: '#64748b' },
    },
    {
      name: '新产品（SP-2024-0591）',
      type: 'bar',
      data: [59, 40, 2, 5.8],
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
    axisLabel: { fontSize: 10, color: '#475569', interval: 0 },
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
      data: [177, 5310, 31.3, 0],
      barWidth: 20,
      itemStyle: { color: '#94a3b8', borderRadius: [4, 4, 0, 0] },
    },
    {
      name: '新产品预期',
      type: 'bar',
      data: [400, 12000, 70.8, 30.7],
      barWidth: 20,
      itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] },
      label: { show: true, position: 'top', fontSize: 10, color: '#1e293b', fontWeight: 'bold' },
    },
    {
      name: '增量',
      type: 'bar',
      data: [223, 6690, 39.5, 30.7],
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
    data: ['与畅享99元定位区隔', '竞品后续跟进', '资费审批门槛', '互斥规则冲突', '权益成本波动', '存量用户影响'],
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
    data: ['预计投诉风险', '预计迁移周期(月)', '承接产品(个)', '合约用户(户)', '月收入贡献(万元)', '在网用户规模(户)'],
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
          const labels = ['3-5%（高）', '4-6个月', '无', '317,426', '5,074', '859,752']
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
      data: '**【助手·产品研发助手 + 产品运营助手 协同】**\n\n> 基于以上四维分析，已形成明确结论：本次异动为典型的**竞争性新增萎缩**，核心矛盾集中在流量额度配置落后于竞品均值29%、叠加年轻客群在信息流渠道被竞品截流。存量基本盘稳定，产品自身未出现衰退迹象。\n\n> 综合判断后，给出两类决策方向。**方案A为当前推荐路径**，以下逐一展开。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 方案A：新产品策划（⭐ 推荐）\n\n> **核心策略**：在现有59元档位基础上，**策划一款新产品**，与现有产品（SP-2024-0590）并行销售，新用户默认引导至新产品，存量用户不受影响。',
    },
    {
      type: 'markdown',
      data: '### 一、新产品配置方案\n\n| 项目 | 现有产品（SP-2024-0590） | **新产品（建议）** | 调整说明 |\n| :--- | :--- | :--- | :--- |\n| 产品编码 | SP-2024-0590 | **SP-2024-0591**（新编码） | 独立产品，不与现有产品覆盖 |\n| 月费 | 59元 | **59元** | 不变，新产品需走资费审批流程 |\n| 国内流量 | 30GB | **40GB** | +10GB，追平竞品A（动感青春版）（40GB） |\n| 视频权益 | 1个（腾讯视频） | **2个**（腾讯+爱奇艺） | +1个，补齐竞品差距 |\n| 权益成本/户/月 | 3.0元 | **5.8元** | 腾讯3.0元+爱奇艺2.8元，成本可控 |\n| 存量用户影响 | — | **无** | 新老产品并行销售，互不干扰 |\n| 新老产品关系 | — | **互斥** | 用户不可同时订购，避免重复 |',
    },
    {
      type: 'markdown',
      data: '> **调整依据**：\n>\n> - 流量（30GB→40GB）：竞品均值为42.5GB，40GB可实现与竞品A持平、缩小与竞品C的差距，同时避免过度配置导致的成本失控；\n> - 视频权益（1个→2个）：友商A（动感青春版）已标配2个权益，必须补齐。爱奇艺权益当前已有成熟供应商在合作，直接复用，无需新建对接。',
    },
    {
      type: 'markdown',
      data: '### 二、量化收益预测\n\n> 新产品上线后的预期收益测算（仅针对新增用户部分，存量86万用户不纳入计算）：',
    },
    {
      type: 'markdown',
      data: '| 指标 | 现有产品（近7日均值） | **新产品预期** | 增量 |\n| :--- | :--- | :--- | :--- |\n| 日均新增 | 177单 | **380~420单** | +203~243单 |\n| 月新增用户数 | 5,310户 | **11,400~12,600户** | +6,090~7,290户 |\n| 月新增收入贡献 | — | **67.3~74.3万元**（新增用户首月） | — |\n| 月新增权益成本 | — | 3.2~3.5万元 | — |\n| **月净增收益** | — | **+28.1~33.3万元** | — |',
    },
    {
      type: 'markdown',
      data: '> **计算口径说明**：\n>\n> - 月新增用户 = 日均新增 × 30天；\n> - 月新增收入 = 月新增用户 × 59元月费；\n> - 月新增权益成本 = 月新增用户 × 2.8元（新增爱奇艺权益成本）；\n> - 以上测算**仅针对新增用户部分**，现有产品86万存量用户的收入与权益成本不变，不纳入计算。',
    },
    {
      type: 'markdown',
      data: '### 三、风险评估\n\n> 方案A的风险评估矩阵，6项风险指标中3项为低风险、3项为中低风险：',
    },
    {
      type: 'markdown',
      data: '| 风险项 | 等级 | 说明 |\n| :--- | :--- | :--- |\n| 存量用户影响 | 🟢 低 | 新产品独立编码，与现有产品并行销售，互斥规则确保用户不重复订购，存量用户不受影响 |\n| 权益成本波动 | 🟢 低 | 爱奇艺权益已有成熟供应商在合作（视频定向流量包），授权合约至2027年6月，成本锁定 |\n| 互斥规则冲突 | 🟢 低 | 新产品继承现有产品的互斥规则（与畅享99元5G套餐、校园特惠39元套餐互斥），经事中预检无冲突 |\n| 资费审批门槛 | 🟡 中低 | 新产品为独立编码，需走资费审批流程，预计审批周期3-5个工作日 |\n| 竞品后续跟进 | 🟡 中低 | 新产品配置已追平友商A（动感青春版）（40GB+2权益），但若竞品继续升级，需持续监控并保留后续调整空间 |\n| 与畅享99元定位区隔 | 🟡 中低 | 新产品（40GB+2权益）与畅享99元（60GB+3权益）差距缩小，建议后续评估99元套餐是否需要同步升级 |',
    },
    {
      type: 'markdown',
      data: '> **关于互斥规则的补充说明**：新产品与畅享99元5G套餐保持互斥，用户在订购时二选一。事中预检提示：新产品上线后，畅享59元档与畅享99元档之间的流量差距将从30GB缩小至20GB，权益差距从2个缩小至1个。建议本次配置保持互斥规则不变，后续2-4周内评估畅享99元的配置是否需要同步调整，以维持两档产品之间的定位区隔。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 方案B：产品下线（⚠️ 当前不推荐）\n\n> 列举方案B的目的在于说明**当前不下线的核心理由**，以供对照参考。',
    },
    {
      type: 'markdown',
      data: '| 评估项 | 数据 | 不推荐理由 |\n| :--- | :--- | :--- |\n| 在网用户规模 | **859,752户** | 存量用户基数庞大，无有效承接方案 |\n| 月收入贡献 | **约5,074万元** | 直接收入损失过大，且无对冲手段 |\n| 合约用户 | **317,426户（36.9%）** | 提前下线需承担合约违约成本，预估约2,300万元 |\n| 承接产品 | **无** | 59元档位当前仅有此一款产品，无可直接承接的替代产品 |\n| 预计迁移周期 | **4-6个月** | 86万用户逐批迁移，成本高、周期长 |\n| 预计投诉风险 | **高（3-5%）** | 存量用户被动迁移，投诉率将远超正常水平（正常<1%） |',
    },
    {
      type: 'markdown',
      data: '> **不下线的核心理由**：在网用户86万、月收入超5000万、合约用户超31万，三个指标均指向**高退出成本、高执行风险、低可行性**。\n\n> **下线适用条件**（供参考）：\n> 1. 合约用户自然到期比例>80%；\n> 2. 已有明确的1:1承接产品上线；\n> 3. 在网用户自然衰减至20万以下。\n>\n> 三个条件满足后方具备下线可行性。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 决策建议\n\n> **综合评估结论**：\n>\n> - **方案A（新产品策划）**：低风险、高收益、可快速落地，预期月净增收益28-33万元，3项低风险+3项中低风险，需走资费审批流程（预计3-5个工作日）。**强烈推荐**。\n> - **方案B（产品下线）**：高退出成本、高执行风险、低可行性，当前不满足下线的三个必要条件。**当前不推荐**。\n\n> 请确认决策方向后，我将根据选择启动对应的执行流程。',
    },
  ]
}

// ============ 下载用的纯文本版本 ============
export function buildOptimizeFullText() {
  return `**【助手·产品研发助手 + 产品运营助手 协同】**

> 基于以上四维分析，已形成明确结论：本次异动为典型的**竞争性新增萎缩**，核心矛盾集中在流量额度配置落后于竞品均值29%、叠加年轻客群在信息流渠道被竞品截流。存量基本盘稳定，产品自身未出现衰退迹象。

> 综合判断后，给出两类决策方向。**方案A为当前推荐路径**，以下逐一展开。

---

## 方案A：新产品策划（⭐ 推荐）

> **核心策略**：在现有59元档位基础上，**策划一款新产品**，与现有产品（SP-2024-0590）并行销售，新用户默认引导至新产品，存量用户不受影响。

### 一、新产品配置方案

| 项目 | 现有产品（SP-2024-0590） | **新产品（建议）** | 调整说明 |
| :--- | :--- | :--- | :--- |
| 产品编码 | SP-2024-0590 | **SP-2024-0591**（新编码） | 独立产品，不与现有产品覆盖 |
| 月费 | 59元 | **59元** | 不变，新产品需走资费审批流程 |
| 国内流量 | 30GB | **40GB** | +10GB，追平竞品A（动感青春版）（40GB） |
| 视频权益 | 1个（腾讯视频） | **2个**（腾讯+爱奇艺） | +1个，补齐竞品差距 |
| 权益成本/户/月 | 3.0元 | **5.8元** | 腾讯3.0元+爱奇艺2.8元，成本可控 |
| 存量用户影响 | — | **无** | 新老产品并行销售，互不干扰 |
| 新老产品关系 | — | **互斥** | 用户不可同时订购，避免重复 |

> **调整依据**：
>
> - 流量（30GB→40GB）：竞品均值为42.5GB，40GB可实现与竞品A持平、缩小与竞品C的差距，同时避免过度配置导致的成本失控；
> - 视频权益（1个→2个）：友商A（动感青春版）已标配2个权益，必须补齐。爱奇艺权益当前已有成熟供应商在合作，直接复用，无需新建对接。

### 二、量化收益预测

| 指标 | 现有产品（近7日均值） | **新产品预期** | 增量 |
| :--- | :--- | :--- | :--- |
| 日均新增 | 177单 | **380~420单** | +203~243单 |
| 月新增用户数 | 5,310户 | **11,400~12,600户** | +6,090~7,290户 |
| 月新增收入贡献 | — | **67.3~74.3万元**（新增用户首月） | — |
| 月新增权益成本 | — | 3.2~3.5万元 | — |
| **月净增收益** | — | **+28.1~33.3万元** | — |

> **计算口径说明**：
>
> - 月新增用户 = 日均新增 × 30天；
> - 月新增收入 = 月新增用户 × 59元月费；
> - 月新增权益成本 = 月新增用户 × 2.8元（新增爱奇艺权益成本）；
> - 以上测算**仅针对新增用户部分**，现有产品86万存量用户的收入与权益成本不变，不纳入计算。

### 三、风险评估

| 风险项 | 等级 | 说明 |
| :--- | :--- | :--- |
| 存量用户影响 | 🟢 低 | 新产品独立编码，与现有产品并行销售，互斥规则确保用户不重复订购，存量用户不受影响 |
| 权益成本波动 | 🟢 低 | 爱奇艺权益已有成熟供应商在合作（视频定向流量包），授权合约至2027年6月，成本锁定 |
| 互斥规则冲突 | 🟢 低 | 新产品继承现有产品的互斥规则（与畅享99元5G套餐、校园特惠39元套餐互斥），经事中预检无冲突 |
| 资费审批门槛 | 🟡 中低 | 新产品为独立编码，需走资费审批流程，预计审批周期3-5个工作日 |
| 竞品后续跟进 | 🟡 中低 | 新产品配置已追平友商A（动感青春版）（40GB+2权益），但若竞品继续升级，需持续监控并保留后续调整空间 |
| 与畅享99元定位区隔 | 🟡 中低 | 新产品（40GB+2权益）与畅享99元（60GB+3权益）差距缩小，建议后续评估99元套餐是否需要同步升级 |

> **关于互斥规则的补充说明**：新产品与畅享99元5G套餐保持互斥，用户在订购时二选一。事中预检提示：新产品上线后，畅享59元档与畅享99元档之间的流量差距将从30GB缩小至20GB，权益差距从2个缩小至1个。建议本次配置保持互斥规则不变，后续2-4周内评估畅享99元的配置是否需要同步调整，以维持两档产品之间的定位区隔。

---

## 方案B：产品下线（⚠️ 当前不推荐）

> 列举方案B的目的在于说明**当前不下线的核心理由**，以供对照参考。

| 评估项 | 数据 | 不推荐理由 |
| :--- | :--- | :--- |
| 在网用户规模 | **859,752户** | 存量用户基数庞大，无有效承接方案 |
| 月收入贡献 | **约5,074万元** | 直接收入损失过大，且无对冲手段 |
| 合约用户 | **317,426户（36.9%）** | 提前下线需承担合约违约成本，预估约2,300万元 |
| 承接产品 | **无** | 59元档位当前仅有此一款产品，无可直接承接的替代产品 |
| 预计迁移周期 | **4-6个月** | 86万用户逐批迁移，成本高、周期长 |
| 预计投诉风险 | **高（3-5%）** | 存量用户被动迁移，投诉率将远超正常水平（正常<1%） |

> **不下线的核心理由**：在网用户86万、月收入超5000万、合约用户超31万，三个指标均指向**高退出成本、高执行风险、低可行性**。

> **下线适用条件**（供参考）：
> 1. 合约用户自然到期比例>80%；
> 2. 已有明确的1:1承接产品上线；
> 3. 在网用户自然衰减至20万以下。
>
> 三个条件满足后方具备下线可行性。

---

## 决策建议

> **综合评估结论**：
>
> - **方案A（新产品策划）**：低风险、高收益、可快速落地，预期月净增收益28-33万元，3项低风险+3项中低风险，需走资费审批流程（预计3-5个工作日）。**强烈推荐**。
> - **方案B（产品下线）**：高退出成本、高执行风险、低可行性，当前不满足下线的三个必要条件。**当前不推荐**。

> 请确认决策方向后，我将根据选择启动对应的执行流程。`
}
