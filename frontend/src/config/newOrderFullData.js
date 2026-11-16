/**
 * newOrderFullData - 套餐新增异动归因 · 完整版根因分析报告
 *
 * 对应「产品运营助手 · 异动归因分析（ops.scene2 · 平台视角）」的套餐新增异动场景，
 * 数据口径与 trafficOpsData.js 的 platformAnomaly.newOrder 保持一致：
 *   平台本月新增订购 21000 笔（上月 28400，环比 -26.1%），转折点 8月12日；
 *   按套餐 / 地区 / 代理商三向拆解降幅贡献，最后给根因贡献度与结论。
 *
 * 结构对齐 analyzeFullData.js：buildNewOrderFullBlocks() / buildNewOrderFullText()，
 * 通过 streamAssistantReply 的 detailCard 在对话区渲染可点击卡片，点击右侧打开详情。
 */

// ============ 图表层：各套餐档位降幅贡献（柱状图） ============
export const packageChartOption = {
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(30, 41, 59, 0.92)',
    borderColor: 'transparent',
    textStyle: { color: '#f1f5f9', fontSize: 12 },
    axisPointer: { type: 'shadow' },
    formatter: (params) => {
      const p = params[0]
      return `${p.name}<br/>${p.seriesName}：${p.value}%`
    },
  },
  grid: { left: 60, right: 60, top: 40, bottom: 20 },
  xAxis: {
    type: 'category',
    data: ['30GB档', '15日档', '3日档'],
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { fontSize: 12, color: '#475569' },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    name: '降幅贡献(%)',
    nameTextStyle: { color: '#94a3b8', fontSize: 11 },
    axisLabel: { fontSize: 10, color: '#64748b', formatter: '{value}%' },
    splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
  },
  series: [
    {
      name: '降幅贡献',
      type: 'bar',
      data: [
        { value: 54, itemStyle: { color: '#ef4444', borderRadius: [4, 4, 0, 0] } },
        { value: 26, itemStyle: { color: '#f59e0b', borderRadius: [4, 4, 0, 0] } },
        { value: 13, itemStyle: { color: '#94a3b8', borderRadius: [4, 4, 0, 0] } },
      ],
      barWidth: 42,
      label: {
        show: true,
        position: 'top',
        fontSize: 12,
        fontWeight: 'bold',
        color: '#1e293b',
        formatter: '{c}%',
      },
    },
  ],
}

// ============ 图表层：各代理商降幅贡献（柱状图） ============
export const agentChartOption = {
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(30, 41, 59, 0.92)',
    borderColor: 'transparent',
    textStyle: { color: '#f1f5f9', fontSize: 12 },
    axisPointer: { type: 'shadow' },
  },
  grid: { left: 60, right: 60, top: 40, bottom: 20 },
  xAxis: {
    type: 'category',
    data: ['鑫联国际', '港通数码', '百川数据', '天翼环宇'],
    axisLine: { lineStyle: { color: '#e2e8f0' } },
    axisLabel: { fontSize: 11, color: '#475569' },
    axisTick: { show: false },
  },
  yAxis: {
    type: 'value',
    name: '降幅贡献(%)',
    nameTextStyle: { color: '#94a3b8', fontSize: 11 },
    axisLabel: { fontSize: 10, color: '#64748b', formatter: '{value}%' },
    splitLine: { lineStyle: { color: '#f1f5f9', type: 'dashed' } },
  },
  series: [
    {
      name: '降幅贡献',
      type: 'bar',
      data: [
        { value: 38, itemStyle: { color: '#ef4444', borderRadius: [4, 4, 0, 0] } },
        { value: 25, itemStyle: { color: '#f59e0b', borderRadius: [4, 4, 0, 0] } },
        { value: 16, itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] } },
        { value: 12, itemStyle: { color: '#94a3b8', borderRadius: [4, 4, 0, 0] } },
      ],
      barWidth: 34,
      label: {
        show: true,
        position: 'top',
        fontSize: 12,
        fontWeight: 'bold',
        color: '#1e293b',
        formatter: '{c}%',
      },
    },
  ],
}

// ============ 图表层：根因贡献度（饼图） ============
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
        { value: 46, name: '30GB 高速档落后竞品', itemStyle: { color: '#ef4444' } },
        { value: 24, name: '15日档被竞品 20日档分流', itemStyle: { color: '#f59e0b' } },
        { value: 18, name: '高价值渠道铺货收缩', itemStyle: { color: '#3b82f6' } },
        { value: 12, name: '季节性淡季+竞品降价', itemStyle: { color: '#94a3b8' } },
      ],
    },
  ],
}

// ============ 完整版 blocks 构建 ============
export function buildNewOrderFullBlocks() {
  return [
    {
      type: 'markdown',
      data: '**【助手·产品运营助手】**\n\n> 好的，已围绕「本月新增异动最大的是哪个套餐，由哪个地区、哪个代理商贡献」对平台 2026年8月 各套餐新增订购数据完成多维归因，结论分四层讲，最后给根因定位。\n\n**总判断**：这是一起典型的 **"竞争性新增萎缩"** ——长线高速档（30日/15日）在配置上被竞品反超，导致香港、台湾地区的主要降幅集中，鑫联国际、港通数码两家代理商贡献显著；而短途日卡基本盘（1日/3日）稳定，说明存量客群没有流失，问题出在长线获客端。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 一、异动总览：本月新增 21000 笔，环比 -26.1%\n\n> 先看整体盘子——这是平台 2026年8月 各套餐新增订购的总体表现：',
    },
    {
      type: 'markdown',
      data: '| 指标 | 本月 | 上月 | 环比 |\n| :--- | :--- | :--- | :--- |\n| 新增订购 | **21,000 笔** | 28,400 笔 | **-26.1%** |\n| 转折点 | 8月12日之后 | — | — |\n| 主导档位 | 30GB 高速档（贡献 54%） | — | — |\n| 主导地区 | 香港（贡献 41%）· 台湾（27%） | — | — |\n| 主导代理商 | 鑫联国际（38%）· 港通数码（25%） | — | — |\n\n> 整体新增从 28,400 笔降至 21,000 笔，环比 -26.1%，跌幅集中出现在 8月12日 之后，属于一次方向明确的新增获客异动。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 二、按套餐拆解：30GB 档降幅最大，贡献 54%\n\n> 把总降幅拆到各档位，看谁的缺口最大：',
    },
    { type: 'chart', title: '各套餐档位降幅贡献', data: packageChartOption, height: 300 },
    {
      type: 'markdown',
      data: '| 套餐档位 | 上月新增 | 本月新增 | 环比 | 降幅贡献 | 原因 |\n| :--- | :--- | :--- | :--- | :--- | :--- |\n| **30GB 高速档（30日）** | 2,860 | 1,380 | **-51.7%** | **54%** | 30GB 档落后竞品（竞品已升至 50GB），长线出境客群转化下滑 |\n| **15日高速档** | 2,450 | 1,690 | **-31.0%** | **26%** | 中期档被竞品 20日档分流，主力客群出现替代 |\n| **3日 800MB/日** | 3,520 | 3,160 | **-10.2%** | **13%** | 短途日卡基本盘稳定，小幅回落属正常波动 |\n\n> **套餐层结论**：新增异动最大的档位是 **30GB 高速档**（贡献 54%），15日中期档次之（26%）。两者均为长中期高速档，指向同一个方向——长线高速配置的竞争力在下滑。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 三、按地区 / 代理商拆解：香港·台湾、鑫联·港通 主要贡献\n\n> 再分别按漫游地区、代理商两个维度看降幅由谁贡献：',
    },
    {
      type: 'markdown',
      data: '| 地区 | 环比 | 降幅贡献 |\n| :--- | :--- | :--- |\n| **香港** | -33.5% | **41%** |\n| **台湾** | -28.2% | **27%** |\n| 澳门 | -21.6% | 18% |\n| 新加坡 | -12.4% | 14% |\n',
    },
    { type: 'chart', title: '各代理商降幅贡献', data: agentChartOption, height: 300 },
    {
      type: 'markdown',
      data: '| 代理商 | 地区 | 环比 | 降幅贡献 |\n| :--- | :--- | :--- | :--- |\n| **鑫联国际** | 深圳 | -29.8% | **38%** |\n| **港通数码** | 香港 | -24.5% | **25%** |\n| 百川数据 | 厦门 | -19.1% | 16% |\n| 天翼环宇 | 上海 | -14.7% | 12% |\n',
    },
    {
      type: 'markdown',
      data: '> **维度层结论**：地区上主要由 **香港（41%）+ 台湾（27%）** 贡献降幅；代理商上主要由 **鑫联国际（38%）+ 港通数码（25%）** 贡献。这两个维度与"长中期高速档"高度重叠，说明是同一批长线客群的退坡，而非个别渠道的孤立问题。',
    },
    {
      type: 'markdown',
      data: '---\n\n## 四、根因定位（综合判断）\n\n> 把三向拆解串起来，给出根因定位和贡献度拆解：',
    },
    { type: 'chart', title: '四类根因按贡献度占比', data: rootCauseChartOption, height: 300 },
    {
      type: 'markdown',
      data: '| 根因 | 贡献度 | 说明 |\n| :--- | :--- | :--- |\n| **主因：30GB 高速档落后竞品** | **46%** | 竞品已升至 50GB 高速，本品 30GB 无跟进，长线出境客群转化下滑、铺货意愿降低 |\n| **辅因1：15日档被竞品 20日档分流** | **24%** | 竞品以 20日档承接香港.台湾主力客群，出现替代 |\n| **辅因2：高价值渠道（港澳台专线）铺货收缩** | **18%** | 渠道铺货收敛，减少了长线档曝光与转化入口 |\n| **其它：季节性淡季 + 竞品降价** | **12%** | 8月为传统淡季，叠加竞品价格动作 |\n',
    },
    {
      type: 'markdown',
      data: '> **最终结论**：本次异动为 **"竞争性新增萎缩"** ——30GB 档异动最大，香港、台湾地区、鑫联国际、港通数码两家代理商贡献了主要降幅；日卡基本盘（1日/3日）稳定，存量未流失。属于**可优化型异动**：优先对 **30GB / 15GB 档做高速配额升级试点**，可快速修复。',
    },
  ]
}

// ============ 下载用的纯文本版本 ============
export function buildNewOrderFullText() {
  return `**【助手·产品运营助手】**

> 好的，已围绕「本月新增异动最大的是哪个套餐，由哪个地区、哪个代理商贡献」对平台 2026年8月 各套餐新增订购数据完成多维归因，结论分四层讲，最后给根因定位。

**总判断**：这是一起典型的 **"竞争性新增萎缩"** ——长线高速档（30日/15日）在配置上被竞品反超，导致香港、台湾地区的主要降幅集中，鑫联国际、港通数码两家代理商贡献显著；而短途日卡基本盘（1日/3日）稳定，说明存量客群没有流失，问题出在长线获客端。

---

## 一、异动总览：本月新增 21000 笔，环比 -26.1%

| 指标 | 本月 | 上月 | 环比 |
| :--- | :--- | :--- | :--- |
| 新增订购 | **21,000 笔** | 28,400 笔 | **-26.1%** |
| 转折点 | 8月12日之后 | — | — |
| 主导档位 | 30GB 高速档（贡献 54%） | — | — |
| 主导地区 | 香港（贡献 41%）· 台湾（27%） | — | — |
| 主导代理商 | 鑫联国际（38%）· 港通数码（25%） | — | — |

> 整体新增从 28,400 笔降至 21,000 笔，环比 -26.1%，跌幅集中出现在 8月12日 之后，属于一次方向明确的新增获客异动。

---

## 二、按套餐拆解：30GB 档降幅最大，贡献 54%

[图表：各套餐档位降幅贡献]

| 套餐档位 | 上月新增 | 本月新增 | 环比 | 降幅贡献 | 原因 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **30GB 高速档（30日）** | 2,860 | 1,380 | **-51.7%** | **54%** | 30GB 档落后竞品（竞品已升至 50GB），长线出境客群转化下滑 |
| **15日高速档** | 2,450 | 1,690 | **-31.0%** | **26%** | 中期档被竞品 20日档分流，主力客群出现替代 |
| **3日 800MB/日** | 3,520 | 3,160 | **-10.2%** | **13%** | 短途日卡基本盘稳定，小幅回落属正常波动 |

> **套餐层结论**：新增异动最大的档位是 **30GB 高速档**（贡献 54%），15日中期档次之（26%）。两者均为长中期高速档，指向同一个方向——长线高速配置的竞争力在下滑。

---

## 三、按地区 / 代理商拆解：香港·台湾、鑫联·港通 主要贡献

| 地区 | 环比 | 降幅贡献 |
| :--- | :--- | :--- |
| **香港** | -33.5% | **41%** |
| **台湾** | -28.2% | **27%** |
| 澳门 | -21.6% | 18% |
| 新加坡 | -12.4% | 14% |

[图表：各代理商降幅贡献]

| 代理商 | 地区 | 环比 | 降幅贡献 |
| :--- | :--- | :--- | :--- |
| **鑫联国际** | 深圳 | -29.8% | **38%** |
| **港通数码** | 香港 | -24.5% | **25%** |
| 百川数据 | 厦门 | -19.1% | 16% |
| 天翼环宇 | 上海 | -14.7% | 12% |

> **维度层结论**：地区上主要由 **香港（41%）+ 台湾（27%）** 贡献降幅；代理商上主要由 **鑫联国际（38%）+ 港通数码（25%）** 贡献。这两个维度与"长中期高速档"高度重叠，说明是同一批长线客群的退坡，而非个别渠道的孤立问题。

---

## 四、根因定位（综合判断）

[图表：四类根因按贡献度占比]

| 根因 | 贡献度 | 说明 |
| :--- | :--- | :--- |
| **主因：30GB 高速档落后竞品** | **46%** | 竞品已升至 50GB 高速，本品 30GB 无跟进，长线出境客群转化下滑、铺货意愿降低 |
| **辅因1：15日档被竞品 20日档分流** | **24%** | 竞品以 20日档承接香港.台湾主力客群，出现替代 |
| **辅因2：高价值渠道（港澳台专线）铺货收缩** | **18%** | 渠道铺货收敛，减少了长线档曝光与转化入口 |
| **其它：季节性淡季 + 竞品降价** | **12%** | 8月为传统淡季，叠加竞品价格动作 |

> **最终结论**：本次异动为 **"竞争性新增萎缩"** ——30GB 档异动最大，香港、台湾地区、鑫联国际、港通数码两家代理商贡献了主要降幅；日卡基本盘（1日/3日）稳定，存量未流失。属于**可优化型异动**：优先对 **30GB / 15GB 档做高速配额升级试点**，可快速修复。`
}
