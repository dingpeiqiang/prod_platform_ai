/**
 * 多维根因分析 - ECharts 图表配置 + 结构化数据
 */

// ============ 产品画像卡片数据 ============
export const profileCardData = {
  name: 'WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)',
  code: 'SP-HK-TW-30D',
  status: '✅ 在售中（2026-05-01起售）',
  configItems: [
    { label: '资费类型', value: '一次性（Data Only）' },
    { label: '高速配额', value: '30GB（总量，香港.台湾共用）' },
    { label: '有效期', value: '30天（自然天）' },
    { label: '漫游地', value: '香港.台湾' },
    { label: '卡类型/网络', value: '5G 数据卡' },
    { label: '高速用尽后', value: '不限量限速（PCRF）' },
  ],
  extraInfo: [
    { label: '累计销售', value: '86,420 户（30日套餐主力档）' },
    { label: '销售渠道', value: '线上线下全渠道（APP/网厅/10086/线下营业厅）' },
    { label: '互斥产品', value: 'WeWalk 15日不限量、WeWalk 30日3GB/日高配' },
    { label: '关联资源', value: '香港.台湾漫游覆盖（CMCC-ROAM-HK-TW）' },
  ],
  health: {
    score: 72,
    change: '↓9分，跌破80分警戒线',
    dimensions: [
      { name: '新增转化', score: 62, change: '↓18', weight: '30%' },
      { name: '续约率', score: 78, change: '↓5', weight: '25%' },
      { name: '投诉率', score: 85, change: '↓2', weight: '20%' },
      { name: '竞品对标', score: 68, change: '↓12', weight: '25%' },
    ],
  },
  summary: '核心健康短板在"新增转化"（62分）和"竞品对标"（68分）两个维度，综合评分72分，已跌破80分警戒线。',
}

// ============ 趋势层：双折线图 ============
export const trendChartOption = {
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(30, 41, 59, 0.92)',
    borderColor: 'transparent',
    textStyle: { color: '#f1f5f9', fontSize: 12 },
    axisPointer: { type: 'cross', crossStyle: { color: '#94a3b8' } },
  },
  legend: {
    data: ['新增订购', '退订', '前7日均值'],
    top: 0,
    textStyle: { fontSize: 11, color: '#64748b' },
    itemWidth: 16,
    itemHeight: 10,
  },
  grid: { left: 40, right: 20, top: 40, bottom: 30 },
  xAxis: {
    type: 'category',
    data: ['7/15', '7/16', '7/17', '7/18', '7/19', '7/20', '7/21', '7/22', '7/23', '7/24', '7/25', '7/26', '7/27', '7/28'],
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { fontSize: 10, color: '#64748b' },
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
      name: '新增订购',
      type: 'line',
      data: [265, 258, 249, 237, 228, 215, 208, 195, 182, 170, 162, 175, 168, 177],
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 2.5, color: '#3b82f6' },
      itemStyle: { color: '#3b82f6' },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(59, 130, 246, 0.2)' },
            { offset: 1, color: 'rgba(59, 130, 246, 0)' },
          ],
        },
      },
      markLine: {
        silent: true,
        symbol: 'none',
        lineStyle: { type: 'dashed', color: '#94a3b8', width: 1.5 },
        data: [{ yAxis: 237, label: { formatter: '前7日均值 237', position: 'end', fontSize: 10, color: '#64748b' } }],
      },
    },
    {
      name: '退订',
      type: 'line',
      data: [78, 81, 76, 79, 74, 82, 77, 82, 79, 85, 80, 76, 88, 84],
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: { width: 2, color: '#ef4444' },
      itemStyle: { color: '#ef4444' },
    },
    {
      name: '前7日均值',
      type: 'line',
      data: [237, 237, 237, 237, 237, 237, 237, null, null, null, null, null, null, null],
      symbol: 'none',
      lineStyle: { type: 'dashed', color: '#94a3b8', width: 1.5 },
    },
  ],
}

// ============ 结构层：客群结构柱状图 ============
export const customerChartOption = {
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(30, 41, 59, 0.92)',
    borderColor: 'transparent',
    textStyle: { color: '#f1f5f9', fontSize: 12 },
    axisPointer: { type: 'shadow' },
  },
  legend: {
    data: ['前30日占比', '近7日占比'],
    top: 0,
    textStyle: { fontSize: 11, color: '#64748b' },
    itemWidth: 12,
    itemHeight: 10,
  },
  grid: { left: 90, right: 30, top: 40, bottom: 20 },
  xAxis: { type: 'value', axisLabel: { fontSize: 10, color: '#64748b', formatter: '{value}%' }, splitLine: { lineStyle: { color: '#f1f5f9' } } },
  yAxis: {
    type: 'category',
    data: ['46岁以上', '36-45岁中年', '26-35岁白领', '18-25岁学生/年轻职场'],
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { fontSize: 10, color: '#475569' },
    axisTick: { show: false },
  },
  series: [
    {
      name: '前30日占比',
      type: 'bar',
      data: [6, 22, 41, 31],
      barWidth: 10,
      itemStyle: { color: '#cbd5e1', borderRadius: [0, 3, 3, 0] },
    },
    {
      name: '近7日占比',
      type: 'bar',
      data: [12, 27, 43, 18],
      barWidth: 10,
      itemStyle: { color: '#3b82f6', borderRadius: [0, 3, 3, 0] },
      label: {
        show: true,
        position: 'right',
        fontSize: 10,
        color: '#1e293b',
        formatter: (params) => {
          const changes = ['+6pp', '+5pp', '+2pp', '-13pp']
          return changes[params.dataIndex]
        },
      },
    },
  ],
}

// ============ 结构层：渠道贡献柱状图 ============
export const channelChartOption = {
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(30, 41, 59, 0.92)',
    borderColor: 'transparent',
    textStyle: { color: '#f1f5f9', fontSize: 12 },
    axisPointer: { type: 'shadow' },
  },
  legend: {
    data: ['前30日占比', '近7日占比'],
    top: 0,
    textStyle: { fontSize: 11, color: '#64748b' },
    itemWidth: 12,
    itemHeight: 10,
  },
  grid: { left: 110, right: 40, top: 40, bottom: 20 },
  xAxis: { type: 'value', axisLabel: { fontSize: 10, color: '#64748b', formatter: '{value}%' }, splitLine: { lineStyle: { color: '#f1f5f9' } } },
  yAxis: {
    type: 'category',
    data: ['10086外呼', '第三方合作', '线下营业厅', '抖音/快手信息流', '线上营业厅APP'],
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { fontSize: 10, color: '#475569' },
    axisTick: { show: false },
  },
  series: [
    {
      name: '前30日占比',
      type: 'bar',
      data: [2, 5, 18, 27, 48],
      barWidth: 10,
      itemStyle: { color: '#cbd5e1', borderRadius: [0, 3, 3, 0] },
    },
    {
      name: '近7日占比',
      type: 'bar',
      data: [
        { value: 6, itemStyle: { color: '#3b82f6', borderRadius: [0, 3, 3, 0] } },
        { value: 10, itemStyle: { color: '#3b82f6', borderRadius: [0, 3, 3, 0] } },
        { value: 22, itemStyle: { color: '#3b82f6', borderRadius: [0, 3, 3, 0] } },
        { value: 18, itemStyle: { color: '#ef4444', borderRadius: [0, 3, 3, 0] } },
        { value: 44, itemStyle: { color: '#3b82f6', borderRadius: [0, 3, 3, 0] } },
      ],
      barWidth: 10,
      label: {
        show: true,
        position: 'right',
        fontSize: 10,
        color: '#1e293b',
        formatter: (params) => {
          const changes = ['+4pp', '+5pp', '+4pp', '-9pp', '-4pp']
          return changes[params.dataIndex]
        },
      },
    },
  ],
}

// ============ 竞品层：雷达图 ============
export const competitorRadarOption = {
  tooltip: {
    backgroundColor: 'rgba(30, 41, 59, 0.92)',
    borderColor: 'transparent',
    textStyle: { color: '#f1f5f9', fontSize: 12 },
  },
  legend: {
    data: ['本产品', '友商A（游讯1日卡）', '友商B（NiceTrip 15日）', '友商C（大湾区伴游30日）'],
    top: 0,
    textStyle: { fontSize: 10, color: '#64748b' },
    itemWidth: 12,
    itemHeight: 10,
    type: 'scroll',
  },
  radar: {
    center: ['50%', '58%'],
    radius: '62%',
    indicator: [
      { name: '资费成本', max: 100 },
      { name: '高速配额', max: 100 },
      { name: '有效期', max: 100 },
      { name: '漫游覆盖', max: 100 },
      { name: '网络速率', max: 100 },
    ],
    axisName: { fontSize: 11, color: '#475569' },
    splitLine: { lineStyle: { color: '#e2e8f0' } },
    splitArea: { areaStyle: { color: ['#fafbfc', '#f1f5f9'] } },
    axisLine: { lineStyle: { color: '#e2e8f0' } },
  },
  series: [
    {
      type: 'radar',
      data: [
        {
          value: [60, 45, 50, 50, 0],
          name: '本产品',
          lineStyle: { width: 2, color: '#ef4444' },
          itemStyle: { color: '#ef4444' },
          areaStyle: { color: 'rgba(239, 68, 68, 0.1)' },
        },
        {
          value: [60, 75, 50, 100, 0],
          name: '友商A（游讯1日卡）',
          lineStyle: { width: 2, color: '#3b82f6' },
          itemStyle: { color: '#3b82f6' },
          areaStyle: { color: 'rgba(59, 130, 246, 0.08)' },
        },
        {
          value: [100, 65, 100, 50, 80],
          name: '友商B（NiceTrip 15日）',
          lineStyle: { width: 2, color: '#10b981' },
          itemStyle: { color: '#10b981' },
          areaStyle: { color: 'rgba(16, 185, 129, 0.08)' },
        },
        {
          value: [60, 100, 30, 0, 80],
          name: '友商C（大湾区伴游30日）',
          lineStyle: { width: 2, color: '#f59e0b' },
          itemStyle: { color: '#f59e0b' },
          areaStyle: { color: 'rgba(245, 158, 11, 0.08)' },
        },
      ],
    },
  ],
}

// ============ 根因层：饼图 ============
export const rootCauseChartOption = {
  tooltip: {
    trigger: 'item',
    backgroundColor: 'rgba(30, 41, 59, 0.92)',
    borderColor: 'transparent',
    textStyle: { color: '#f1f5f9', fontSize: 12 },
    formatter: '{b}: {c}% ({d}%)',
  },
  legend: {
    orient: 'vertical',
    right: 10,
    top: 'center',
    textStyle: { fontSize: 11, color: '#475569' },
    itemWidth: 12,
    itemHeight: 10,
    formatter: (name) => {
      const map = {
        '高速配额落后竞品均值29%': '高速落后',
        '年轻客群在信息流渠道被截流': '渠道截流',
        '漫游覆盖受限': '覆盖不足',
        '友商B降价竞争': '价格分流',
        '其他（季节性等）': '其他',
      }
      return map[name] || name
    },
  },
  series: [
    {
      type: 'pie',
      radius: ['40%', '68%'],
      center: ['35%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
      label: {
        show: true,
        fontSize: 11,
        color: '#1e293b',
        formatter: '{b|{b}}\n{c|{c}%}',
        rich: {
          b: { fontSize: 10, color: '#64748b', lineHeight: 14 },
          c: { fontSize: 14, fontWeight: 'bold', color: '#1e293b' },
        },
      },
      labelLine: { length: 8, length2: 8 },
      data: [
        { value: 50, name: '高速配额落后竞品均值29%', itemStyle: { color: '#ef4444' } },
        { value: 20, name: '年轻客群在信息流渠道被截流', itemStyle: { color: '#f59e0b' } },
        { value: 15, name: '漫游覆盖受限', itemStyle: { color: '#3b82f6' } },
        { value: 10, name: '友商B降价竞争', itemStyle: { color: '#10b981' } },
        { value: 5, name: '其他（季节性等）', itemStyle: { color: '#94a3b8' } },
      ],
    },
  ],
}

// ============ 完整版 blocks 构建 ============
export function buildAnalyzeFullBlocks() {
  return [
    {
      type: 'markdown',
      data: '**【助手·产品运营助手】**\n\n> 好的，已对「WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)」近30天运营数据完成多维分析，结论分四层讲，最后给根因定位。\n\n**总判断**：这是一起典型的 **"竞争性新增萎缩"** ——我们的产品在高速配额上被竞品反超，导致年轻新客的转化效率显著下降。已有漫游用户基本稳定，说明存量基本盘没有垮，问题出在获客端。',
    },
    { type: 'markdown', data: '---\n\n## 一、产品画像：当前在售产品的完整快照\n\n> 先确认当前分析对象的基本面——这是「WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)」作为在售产品的完整配置和运营状态：' },
    { type: 'profile-card', data: profileCardData },
    {
      type: 'markdown',
      data: '---\n\n## 二、趋势层：新增萎缩、存量稳定、退订未恶化\n\n> 先看核心指标走势。这是近14天的数据：',
    },
    { type: 'chart', title: '近14天新增订购与退订趋势', data: trendChartOption, height: 340 },
    {
      type: 'markdown',
      data: '| 日期 | 新增订购 | 环比 | 退订 | 净增 | 在网用户数 |\n| :--- | :--- | :--- | :--- | :--- | :--- |\n| 7/15 | 265 | — | 78 | +187 | 857,842 |\n| 7/16 | 258 | -2.6% | 81 | +177 | 858,019 |\n| 7/17 | 249 | -3.5% | 76 | +173 | 858,192 |\n| 7/18 | 237 | -4.8% | 79 | +158 | 858,350 |\n| 7/19 | 228 | -3.8% | 74 | +154 | 858,504 |\n| 7/20 | 215 | -5.7% | 82 | +133 | 858,637 |\n| 7/21 | 208 | -3.3% | 77 | +131 | 858,768 |\n| **前7日均值** | **237** | — | **78** | **+159** | — |\n| 7/22 | 195 | -6.3% | 82 | +113 | 859,210 |\n| 7/23 | 182 | -6.7% | 79 | +103 | 859,313 |\n| 7/24 | 170 | -6.6% | 85 | +85 | 859,398 |\n| 7/25 | 162 | -4.7% | 80 | +82 | 859,480 |\n| 7/26 | 175 | +8.0% | 76 | +99 | 859,579 |\n| 7/27 | 168 | -4.0% | 88 | +80 | 859,659 |\n| 7/28 | 177 | +5.4% | 84 | +93 | 859,752 |\n| **后7日均值** | **177** | — | **82** | **+94** | — |\n| **变化** | **-25.3%** | — | **+5.1%** | **-40.9%** | **+0.4%** |',
    },
    {
      type: 'markdown',
      data: '> - 新增日均177单，对比前7日237单，降幅25.3%。同比5月上架时（日均约320单），降幅已接近45%\n> - 退订日均82单，环比+5.1%，波动在正常范围内\n> - 在网用户规模86万，净增逐日递减但仍为正数，大盘没有失血\n> - 转折点出现在7月20日之后\n\n> **趋势层结论**：这是一次新增获客侧的异动，而非存量流失型衰退。问题在"进水管"，不在"出水管"。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 三、结构层：年轻客群在流失，信息流渠道效率下降\n\n> 再拆新增用户的画像，把**客群结构**和**渠道来源**两个维度结合起来看——谁在流失、从哪里流失。',
    },
    { type: 'chart', title: '客群结构变化（近7日 vs 前30日）', data: customerChartOption, height: 280 },
    {
      type: 'markdown',
      data: '| 客群标签 | 近7日占比 | 前30日占比 | 变化 | 日均新增 |\n| :--- | :--- | :--- | :--- | :--- |\n| 18-25岁学生/年轻职场 | **18%** | 31% | **-13pp** | ≈32单 |\n| 26-35岁白领 | 43% | 41% | +2pp | ≈76单 |\n| 36-45岁中年 | 27% | 22% | +5pp | ≈48单 |\n| 46岁以上 | 12% | 6% | +6pp | ≈21单 |\n\n> 年轻客群（18-25岁）从第一客群跌至第三，绝对数量从日均73单降至32单，日流失约41单。',
    },
    { type: 'chart', title: '渠道贡献变化（近7日 vs 前30日）', data: channelChartOption, height: 280 },
    {
      type: 'markdown',
      data: '| 渠道 | 近7日占比 | 前30日占比 | 变化 | 备注 |\n| :--- | :--- | :--- | :--- | :--- |\n| 线上营业厅APP | 44% | 48% | -4pp | 下滑温和 |\n| **抖音/快手信息流** | **18%** | 27% | **-9pp** | ⚠️ 降幅最大 |\n| 线下营业厅 | 22% | 18% | +4pp | 未受影响 |\n| 第三方合作 | 10% | 5% | +5pp | 基数小 |\n| 10086外呼 | 6% | 2% | +4pp | 基数小 |\n\n> **交叉分析**：抖音/快手渠道的年轻客群占比从52%骤降至31%。竞品近两周在该人群投放出价提高约22%，直接挤压我们的曝光量。\n\n> **结构层结论**：新增下滑主要集中在"18-25岁 × 抖音/快手信息流"这个交叉象限。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 四、竞品层：流量额度落后29%，且竞品在7月集体升级\n\n> 再看竞品。系统抓取了三大友商同档位产品的最新数据，加了一列"近1个月变化"，帮你看清动态：',
    },
    { type: 'chart', title: '竞品五维对比雷达图（资费/高速配额/有效期/漫游覆盖/网络速率）', data: competitorRadarOption, height: 360 },
    {
      type: 'markdown',
      data: '| 维度 | 本产品 | 友商A（游讯1日卡） | 友商B（NiceTrip 15日） | 友商C（大湾区伴游30日） |\n| :--- | :--- | :--- | :--- | :--- |\n| 资费 | 一次性 | 一次性 | 一次性（更便宜） | 一次性 |\n| 高速配额 | **30GB（总量）** | **20GB/日** | **15GB（总量）** | **50GB（总量）** |\n| 有效期 | 30天 | 1天 | **15天** | 30天 |\n| 漫游地 | 香港.台湾 | **香港.台湾+澳门** | 香港.台湾 | **亚太多国** |\n| 网络速率 | 5G | 4G | **5G** | 5G |\n| **近1个月变化** | ➡️ 无变化 | ⬆️ 新增澳门漫游（7/1） | ⬇️ 降价（7/15） | ⬆️ 高速50GB（7/20） |',
    },
    {
      type: 'markdown',
      data: '> 1. **高速配额落后**：30GB vs 竞品均值42.5GB，落后 **29%**。三家竞品中高速额度我们偏低\n> 2. 友商A和C在7月集中升级（澳门漫游、50GB高速），友商B降价，**我们没有任何配置调整响应**\n> 3. 友商A的游讯1日卡目前日均新增约 **620单**，是本品的 **3.5倍**，其中约30%的新增用户是从本品分流过去的\n\n> **竞品层结论**：竞品在7月完成了对本品 **"高速配额+漫游覆盖"的双重包围**。我们的相对竞争力在过去30天内被动大幅下滑。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 五、根因定位（综合判断）\n\n> 把四层串起来，给出根因定位和贡献度拆解：',
    },
    { type: 'chart', title: '四类根因按贡献度占比', data: rootCauseChartOption, height: 300 },
    {
      type: 'markdown',
      data: '| 根因 | 贡献度 | 说明 |\n| :--- | :--- | :--- |\n| **主因：高速配额落后竞品均值29%** | **50%** | 本品30GB vs 竞品均值42.5GB，且竞品7月集中升级，本品无跟进 |\n| **辅因1：年轻客群在信息流渠道被截流** | **20%** | 竞品在抖音/快手渠道提价抢量，叠加产品吸引力不足 |\n| **辅因2：漫游覆盖受限** | **15%** | 友商A新增澳门漫游，本品的香港.台湾双地覆盖吸引力下降 |\n| **辅因3：友商B降价竞争** | **10%** | 价格敏感型用户被分流 |\n| **其他（季节性/大促空窗等）** | **5%** | 7月为传统淡季，叠加无大型营销活动 |',
    },
    {
      type: 'markdown',
      data: '> **最终结论**：本次异动为 **"竞争性新增萎缩"** ，核心矛盾是**高速配额配置落后于竞品**，次要矛盾是**信息流渠道获客效率下降**和**漫游覆盖不足**。属于**可优化型异动**——通过新产品策划和渠道投放策略调整，可快速修复。',
    },
  ]
}

// ============ 下载用的纯文本版本 ============
export function buildAnalyzeFullText() {
  return `**【助手·产品运营助手】**

> 好的，已对「WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速)」近30天运营数据完成多维分析，结论分四层讲，最后给根因定位。

**总判断**：这是一起典型的 **"竞争性新增萎缩"** ——我们的产品在高速配额上被竞品反超，导致年轻新客的转化效率显著下降。已有漫游用户基本稳定，说明存量基本盘没有垮，问题出在获客端。

---

## 一、产品画像：当前在售产品的完整快照

| 字段 | 内容 |
| :--- | :--- |
| 产品名称 | WeWalk-Data Only-澳门.香港.台湾共用30日不限量(30GB高速) |
| 产品编码 | SP-HK-TW-30D |
| 在售状态 | ✅ 在售中（2026-05-01起售） |
| 资费类型 | 一次性（Data Only） |
| 高速配额 | 30GB（总量，香港.台湾共用） |
| 有效期 | 30天（自然天） |
| 漫游地 | 香港.台湾 |
| 卡类型/网络 | 5G 数据卡 |
| 高速用尽后 | 不限量限速（PCRF） |

**累计销售**：86,420户（30日套餐主力档）
**销售渠道**：线上线下全渠道（APP/网厅/10086/线下营业厅）
**互斥产品**：WeWalk 15日不限量、WeWalk 30日3GB/日高配
**关联资源**：香港.台湾漫游覆盖（CMCC-ROAM-HK-TW）

**当前健康评分：72分**（较上周↓9分，跌破80分警戒线）

| 维度 | 得分 | 变化 | 权重 |
| :--- | :--- | :--- | :--- |
| 新增转化 | 62 | ↓18 | 30% |
| 续约率 | 78 | ↓5 | 25% |
| 投诉率 | 85 | ↓2 | 20% |
| 竞品对标 | 68 | ↓12 | 25% |

> **产品画像小结**：核心健康短板在"新增转化"（62分）和"竞品对标"（68分）两个维度。

---

## 二、趋势层：新增萎缩、存量稳定、退订未恶化

[图表：近14天新增订购与退订趋势]

| 日期 | 新增订购 | 环比 | 退订 | 净增 | 在网用户数 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 7/15 | 265 | — | 78 | +187 | 857,842 |
| 7/16 | 258 | -2.6% | 81 | +177 | 858,019 |
| 7/17 | 249 | -3.5% | 76 | +173 | 858,192 |
| 7/18 | 237 | -4.8% | 79 | +158 | 858,350 |
| 7/19 | 228 | -3.8% | 74 | +154 | 858,504 |
| 7/20 | 215 | -5.7% | 82 | +133 | 858,637 |
| 7/21 | 208 | -3.3% | 77 | +131 | 858,768 |
| **前7日均值** | **237** | — | **78** | **+159** | — |
| 7/22 | 195 | -6.3% | 82 | +113 | 859,210 |
| 7/23 | 182 | -6.7% | 79 | +103 | 859,313 |
| 7/24 | 170 | -6.6% | 85 | +85 | 859,398 |
| 7/25 | 162 | -4.7% | 80 | +82 | 859,480 |
| 7/26 | 175 | +8.0% | 76 | +99 | 859,579 |
| 7/27 | 168 | -4.0% | 88 | +80 | 859,659 |
| 7/28 | 177 | +5.4% | 84 | +93 | 859,752 |
| **后7日均值** | **177** | — | **82** | **+94** | — |
| **变化** | **-25.3%** | — | **+5.1%** | **-40.9%** | **+0.4%** |

> - 新增日均177单，对比前7日237单，降幅25.3%。同比5月上架时（日均约320单），降幅已接近45%
> - 退订日均82单，环比+5.1%，波动在正常范围内
> - 在网用户规模86万，净增逐日递减但仍为正数，大盘没有失血
> - 转折点出现在7月20日之后

> **趋势层结论**：这是一次新增获客侧的异动，而非存量流失型衰退。问题在"进水管"，不在"出水管"。

---

## 三、结构层：年轻客群在流失，信息流渠道效率下降

[图表：客群结构变化]

| 客群标签 | 近7日占比 | 前30日占比 | 变化 | 日均新增 |
| :--- | :--- | :--- | :--- | :--- |
| 18-25岁学生/年轻职场 | **18%** | 31% | **-13pp** | ≈32单 |
| 26-35岁白领 | 43% | 41% | +2pp | ≈76单 |
| 36-45岁中年 | 27% | 22% | +5pp | ≈48单 |
| 46岁以上 | 12% | 6% | +6pp | ≈21单 |

> 年轻客群（18-25岁）从第一客群跌至第三，绝对数量从日均73单降至32单，日流失约41单。

[图表：渠道贡献变化]

| 渠道 | 近7日占比 | 前30日占比 | 变化 | 备注 |
| :--- | :--- | :--- | :--- | :--- |
| 线上营业厅APP | 44% | 48% | -4pp | 下滑温和 |
| **抖音/快手信息流** | **18%** | 27% | **-9pp** | ⚠️ 降幅最大 |
| 线下营业厅 | 22% | 18% | +4pp | 未受影响 |
| 第三方合作 | 10% | 5% | +5pp | 基数小 |
| 10086外呼 | 6% | 2% | +4pp | 基数小 |

> **交叉分析**：抖音/快手渠道的年轻客群占比从52%骤降至31%。竞品近两周在该人群投放出价提高约22%，直接挤压我们的曝光量。

> **结构层结论**：新增下滑主要集中在"18-25岁 × 抖音/快手信息流"这个交叉象限。

---

## 四、竞品层：高速配额落后29%，且竞品在7月集体升级

[图表：竞品五维对比雷达图]

| 维度 | 本产品 | 友商A（游讯1日卡） | 友商B（NiceTrip 15日） | 友商C（大湾区伴游30日） |
| :--- | :--- | :--- | :--- | :--- |
| 资费 | 一次性 | 一次性 | 一次性（更便宜） | 一次性 |
| 高速配额 | **30GB（总量）** | **20GB/日** | **15GB（总量）** | **50GB（总量）** |
| 有效期 | 30天 | 1天 | **15天** | 30天 |
| 漫游地 | 香港.台湾 | **香港.台湾+澳门** | 香港.台湾 | **亚太多国** |
| 网络速率 | 5G | 4G | **5G** | 5G |
| **近1个月变化** | ➡️ 无变化 | ⬆️ 新增澳门漫游（7/1） | ⬇️ 降价（7/15） | ⬆️ 高速50GB（7/20） |

> 1. **高速配额落后**：30GB vs 竞品均值42.5GB，落后 **29%**。三家竞品中高速额度我们偏低
> 2. 友商A和C在7月集中升级（澳门漫游、50GB高速），友商B降价，**我们没有任何配置调整响应**
> 3. 友商A的游讯1日卡目前日均新增约 **620单**，是本品的 **3.5倍**，其中约30%的新增用户是从本品分流过去的

> **竞品层结论**：竞品在7月完成了对本品 **"高速配额+漫游覆盖"的双重包围**。我们的相对竞争力在过去30天内被动大幅下滑。

---

## 五、根因定位（综合判断）

[图表：四类根因按贡献度占比]

| 根因 | 贡献度 | 说明 |
| :--- | :--- | :--- |
| **主因：高速配额落后竞品均值29%** | **50%** | 本品30GB vs 竞品均值42.5GB，且竞品7月集中升级，本品无跟进 |
| **辅因1：年轻客群在信息流渠道被截流** | **20%** | 竞品在抖音/快手渠道提价抢量，叠加产品吸引力不足 |
| **辅因2：漫游覆盖受限** | **15%** | 友商A新增澳门漫游，本品的香港.台湾双地覆盖吸引力下降 |
| **辅因3：友商B降价竞争** | **10%** | 价格敏感型用户被分流 |
| **其他（季节性/大促空窗等）** | **5%** | 7月为传统淡季，叠加无大型营销活动 |

> **最终结论**：本次异动为 **"竞争性新增萎缩"** ，核心矛盾是**高速配额配置落后于竞品**，次要矛盾是**信息流渠道获客效率下降**和**漫游覆盖不足**。属于**可优化型异动**——通过新产品策划和渠道投放策略调整，可快速修复。`
}
