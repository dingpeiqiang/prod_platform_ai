/**
 * opsData - 产品运营视图静态数据（迁移自 chat-skeleton 原型 OpsView.vue）
 * 数据为演示 mock：收入总览三卡、八大重点业务、5G新通话下钻十套餐、四维评分规则
 */

/** 收入总览（单位：万元，全省 · 2026年7月） */
export const revenueCards = [
  {
    key: 'cnfamily',
    title: 'CN及家庭产品',
    iconCls: 'ov-icon-blue',
    report: { now: 64653.57, cum: 454312.06, yoy: -0.64 },
    bill: { now: 23107.78, cum: 158966.95, yoy: 5.95 },
  },
  {
    key: 'family',
    title: '家庭产品',
    iconCls: 'ov-icon-emerald',
    report: { now: 54532.19, cum: 378076.8, yoy: 1.67 },
    bill: { now: 18693.26, cum: 126629.51, yoy: 7.83 },
  },
  {
    key: 'cn',
    title: 'CN产品',
    iconCls: 'ov-icon-amber',
    report: { now: 10121.38, cum: 76235.27, yoy: -10.68 },
    bill: { now: 4414.52, cum: 32337.44, yoy: -0.81 },
  },
]

/** 重点产品业务数据 */
export const products = {
  jtkd: {
    name: '家庭宽带',
    key: 'jtkd',
    scale: [
      { label: '到达用户', value: '1182.03万', yoy: null },
      { label: '校园宽带到达', value: '46.39万', yoy: null },
      { label: '活跃率', value: '83.09%', yoy: null },
      { label: '活跃率(剔校园宽带)', value: '85.93%', yoy: null },
    ],
    struct: {
      caption: '到达速率结构',
      groups: [
        { label: '到达速率', rows: [
          { label: '1000M', value: 0.3096 },
          { label: '300-500M', value: 0.432 },
          { label: '300M以下', value: 0.2584 },
        ] },
        { label: '新增速率', rows: [
          { label: '1000M', value: 0.3306 },
          { label: '300-500M', value: 0.5765 },
          { label: '300M以下', value: 0.0928 },
        ] },
        { label: '首宽/二宽', rows: [
          { label: '首宽', value: 0.7624 },
          { label: '二宽', value: 0.2372 },
        ] },
        { label: '资费结构', rows: [
          { label: '129元及以上', value: 0.2591 },
          { label: '59元以下', value: 0.3798 },
        ] },
      ],
    },
    added: [{ label: '当月新增', value: '8.90万', yoy: -36.47 }],
    quality: [
      { label: 'T+1活跃率', value: '92.57%', note: '新增用户' },
      { label: 'T+3离网率', value: '1.57%', note: '1497户' },
      { label: 'T+6离网率', value: '4.93%', note: '7620户' },
      { label: 'T+3沉默率', value: '6.94%', note: null },
      { label: 'T+6沉默率', value: '3.92%', note: null },
      { label: '户均实收(融合群)', value: '118.33元', note: '元/户' },
      { label: '低于50元占比', value: '8.44%', note: null },
      { label: '单宽户均实收', value: '20.70元', note: '元/户' },
    ],
    income: {
      report: { now: 40787.33, cum: 283350.15, yoy: 2.66 },
      bill: { now: 3977.45, cum: 27728.41, yoy: 0.39 },
    },
    channel: [
      { label: '线下', value: 0.7763 },
      { label: '10086', value: 0.1447 },
      { label: '线上', value: 0.079 },
    ],
  },

  znzw: {
    name: '智能组网',
    key: 'znzw',
    scale: [
      { label: '到达用户', value: '360.87万', yoy: null },
      { label: '组网(不含FTTR)', value: '308.10万', yoy: null },
      { label: '活跃率', value: '89.28%', yoy: null },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '到达结构', rows: [
          { label: 'FTTR', value: 0.1462 },
          { label: 'WiFi6', value: 0.7872 },
          { label: '融合网关', value: 0.0231 },
          { label: 'WiFi5', value: 0.0435 },
        ] },
        { label: '新增结构', rows: [
          { label: 'FTTR', value: 0.3061 },
          { label: 'WiFi6', value: 0.6714 },
          { label: '融合网关', value: 0.0225 },
          { label: 'WiFi5', value: 0 },
        ] },
      ],
    },
    added: [
      { label: '当月新增', value: '9.01万', yoy: -41.14 },
      { label: '新增(不含FTTR)', value: '6.25万', yoy: null },
    ],
    quality: [
      { label: 'T+3退订率', value: '7.14%', note: '5109户' },
      { label: 'T+6退订率', value: '6.63%', note: '8055户' },
      { label: 'T+3沉默率', value: '3.22%', note: null },
      { label: 'T+6沉默率', value: '1.93%', note: null },
      { label: '户均实收', value: '9.77元', note: '元/户' },
      { label: '低值占比(<10元)', value: '4.35%', note: null },
    ],
    income: {
      report: { now: 5394.8, cum: 34381.5, yoy: 5.96 },
      bill: { now: 2366.38, cum: 16756.35, yoy: -0.28 },
    },
    channel: [
      { label: '线下', value: 0.8626 },
      { label: '10086', value: 0.1285 },
      { label: '线上', value: 0.0087 },
    ],
  },

  ydyp: {
    name: '移动云盘',
    key: 'ydyp',
    scale: [
      { label: '到达用户', value: '1020.16万', yoy: null },
      { label: '叠加态到达', value: '85.38万', yoy: null },
      { label: '付费活跃', value: '25.09万', yoy: null },
      { label: '付费活跃率', value: '2.46%', yoy: null },
      { label: 'APP活跃', value: '56.56万', yoy: null },
      { label: '高价值', value: '49.84万', yoy: null },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '到达产品结构', rows: [
          { label: '空间包', value: 0.9738 },
          { label: '会员', value: 0.0262 },
        ] },
        { label: '新增产品结构', rows: [
          { label: '空间包', value: 0.9608 },
          { label: '会员', value: 0.0392 },
        ] },
      ],
    },
    added: [
      { label: '当月新增', value: '34.70万', yoy: -16.33 },
      { label: '叠加态新增', value: '1.80万', yoy: null },
    ],
    quality: [
      { label: 'T+1活跃率', value: '6.55%', note: null },
      { label: 'T+3退订率', value: '13.44%', note: null },
      { label: '户均实收', value: '11.06元', note: '元/户' },
      { label: '低值占比', value: '17.88%', note: null },
    ],
    income: {
      report: { now: 3843.57, cum: 26527.23, yoy: 0.61 },
      bill: { now: 117.88, cum: 666.45, yoy: -8.87 },
      extra: { name: '叠加态收入', now: 499.26, cum: 3467.11, yoy: -23.87 },
    },
    channel: [
      { label: '线下', value: 0.4327 },
      { label: '10086', value: 0.5053 },
      { label: '线上', value: 0.062 },
    ],
  },

  spcl: {
    name: '视频彩铃',
    key: 'spcl',
    scale: [
      { label: '到达用户', value: '1750.07万', yoy: null },
      { label: '叠加态到达', value: '145.32万', yoy: null },
      { label: '付费活跃', value: '103.83万', yoy: null },
      { label: '付费活跃率', value: '83.84%', yoy: null },
      { label: '高价值', value: '74.09万', yoy: null },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '到达产品结构', rows: [
          { label: '功能产品', value: 0.9292 },
          { label: '会员产品', value: 0.0708 },
        ] },
        { label: '新增产品结构', rows: [
          { label: '功能产品', value: 0.9563 },
          { label: '会员产品', value: 0.0437 },
        ] },
      ],
    },
    added: [
      { label: '当月新增', value: '53.97万', yoy: -3.97 },
      { label: '叠加态新增', value: '2.21万', yoy: null },
    ],
    quality: [
      { label: 'T+3退订率', value: '6.50%', note: 'O20口径' },
      { label: 'T+6退订率', value: '15.06%', note: 'P20口径' },
      { label: '户均实收', value: '3.90元', note: '元/户' },
      { label: '低值占比', value: '7.26%', note: null },
    ],
    income: {
      report: { now: 2826.21, cum: 17832.9, yoy: -22.55 },
      bill: { now: 1437.37, cum: 7441.77, yoy: 0.89 },
      extra: { name: '叠加态收入', now: 1599.98, cum: 8640.08, yoy: -3.67 },
    },
    channel: [
      { label: '线下', value: 0.5056 },
      { label: '10086', value: 0.4066 },
      { label: '线上', value: 0.017 },
    ],
  },

  mgsp: {
    name: '咪咕视频',
    key: 'mgsp',
    scale: [
      { label: '到达用户', value: '884.70万', yoy: null },
      { label: '叠加态到达', value: '105.72万', yoy: null },
      { label: '付费活跃', value: '33.32万', yoy: null },
      { label: '付费活跃率', value: '3.77%', yoy: null },
      { label: 'APP活跃', value: '87.61万', yoy: null },
      { label: '高价值', value: '35.28万', yoy: null },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '到达产品结构', rows: [
          { label: '视频会员', value: 0.9981 },
          { label: '体育会员', value: 0.0019 },
        ] },
        { label: '新增产品结构', rows: [
          { label: '视频会员', value: 0.99995 },
          { label: '体育会员', value: 0.00005 },
        ] },
      ],
    },
    added: [
      { label: '当月新增', value: '28.02万', yoy: -40.06 },
      { label: '叠加态新增', value: '2.83万', yoy: null },
    ],
    quality: [
      { label: 'T+1活跃率', value: '8.21%', note: null },
      { label: 'T+3退订率', value: '16.36%', note: 'P20口径' },
      { label: '户均实收', value: '2.57元', note: '元/户' },
      { label: '低值占比', value: '5.30%', note: null },
    ],
    income: {
      report: { now: 2475.23, cum: 17732.58, yoy: -22.98 },
      bill: { now: 199.98, cum: 1770.28, yoy: -21.68 },
      extra: { name: '叠加态收入', now: 608.59, cum: 4746.11, yoy: -43.72 },
    },
    channel: [
      { label: '线下', value: 0.6264 },
      { label: '10086', value: 0.3178 },
      { label: '线上', value: 0.0557 },
    ],
  },

  wxth: {
    name: '5G新通话',
    key: 'wxth',
    scale: [
      { label: '到达用户', value: '94.50万', yoy: null },
      { label: '付费活跃', value: '7.71万', yoy: null },
      { label: '付费活跃率', value: '8.16%', yoy: null },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '到达产品结构', rows: [
          { label: '明星来电', value: 0.6231 },
          { label: 'AI速记', value: 0.0169 },
          { label: '能量包', value: 0.0016 },
        ] },
        { label: '新增产品结构', rows: [
          { label: '明星来电', value: 0.8475 },
          { label: 'AI速记', value: 0.0496 },
          { label: '能量包', value: 0.0007 },
        ] },
      ],
    },
    added: [{ label: '当月新增', value: '17.52万', yoy: null, warn: '同比+1279.59%（低基数）' }],
    quality: [
      { label: 'T+3退订率', value: '3.36%', note: null },
      { label: 'T+6退订率', value: '17.12%', note: null },
      { label: '户均实收', value: '1.18元', note: '元/户' },
      { label: '低值占比', value: '91.51%', note: null },
    ],
    income: {
      report: { now: 122.18, cum: 386.36, yoy: 1775.78 },
      bill: { now: 97.42, cum: 466.84, yoy: 2104.99 },
      extra: { name: '叠加态收入', now: 96.47, cum: 464.43, yoy: 100 },
    },
    channel: [
      { label: '线下', value: 0.594 },
      { label: '10086', value: 0.3807 },
      { label: '线上', value: 0.0255 },
    ],
  },

  ldgj: {
    name: '来电管家',
    key: 'ldgj',
    scale: [
      { label: '到达用户', value: '521.84万', yoy: null },
      { label: '叠加态到达', value: '457.60万', yoy: null },
      { label: '付费活跃', value: '377.53万', yoy: null },
      { label: '付费活跃率', value: '72.35%', yoy: null },
      { label: '高价值', value: '244.56万', yoy: null },
    ],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '到达产品结构', rows: [{ label: '升级版', value: 0.0252 }] },
        { label: '新增产品结构', rows: [{ label: '升级版', value: 0.1538 }] },
      ],
    },
    added: [
      { label: '当月新增', value: '9.77万', yoy: -38.26 },
      { label: '叠加态新增', value: '9.76万', yoy: null },
    ],
    quality: [
      { label: 'T+3退订率', value: '3.30%', note: null },
      { label: 'T+6退订率', value: '7.07%', note: 'N20口径' },
      { label: '户均实收', value: '1.94元', note: '元/户' },
      { label: '低值占比', value: '17.88%', note: null },
    ],
    income: {
      report: { now: 1105.91, cum: 7868.66, yoy: -2.35 },
      bill: { now: 1000.2, cum: 7023.34, yoy: 1.07 },
    },
    channel: [
      { label: '线下', value: 0.9908 },
      { label: '10086', value: 0.0073 },
      { label: '线上', value: 0.0019 },
    ],
  },

  dphy: {
    name: '大屏会员',
    key: 'dphy',
    scale: [{ label: '到达用户', value: '200.73万', yoy: null, note: '当月活跃率暂缺' }],
    struct: {
      caption: '到达结构',
      groups: [
        { label: '会员结构', rows: [
          { label: '重点会员占比', value: 0.3792 },
          { label: '新增重点会员占比', value: 0.7523 },
        ] },
      ],
    },
    added: [{ label: '当月新增', value: '4.69万', yoy: null }],
    quality: [
      { label: 'T+3退订率', value: '13.86%', note: '4882户' },
      { label: 'T+6退订率', value: '25.97%', note: '18086户' },
      { label: '户均实收', value: '13.80元', note: '元/户' },
      { label: '实收0元占比', value: '1.81%', note: null },
    ],
    income: {
      report: { now: 1748.93, cum: 13700.67, yoy: -5.41 },
      bill: { now: 1452.62, cum: 10170.51, yoy: -5.37 },
    },
    channel: [
      { label: '线下', value: 0.3428 },
      { label: '10086', value: 0.0316 },
      { label: '线上', value: 0.6255 },
    ],
  },
}

export const productKeys = ['jtkd', 'znzw', 'ydyp', 'spcl', 'mgsp', 'wxth', 'ldgj', 'dphy']

/** 四维评分规则（效益/市场/运营质量/生命周期，二级指标 0-10 分加权） */
export const dimMeta = [
  { key: 'eff', name: '效益维度', weight: '45%', full: 45, items: [
    { name: '累计收入', full: 9 }, { name: '月均ARPU', full: 11.25 }, { name: '收入环比', full: 6.75 },
    { name: '营销费用占比', full: 9 }, { name: '利润率', full: 9 },
  ] },
  { key: 'market', name: '市场维度', weight: '25%', full: 25, items: [
    { name: '当前活跃用户', full: 8.75 }, { name: '月净增用户', full: 6.25 },
    { name: '订购转化率', full: 6.25 }, { name: '渠道上架率', full: 3.75 },
  ] },
  { key: 'quality', name: '运营质量维度', weight: '20%', full: 20, items: [
    { name: '资费投诉率', full: 8 }, { name: '月度退订率', full: 6 }, { name: '业务办理成功率', full: 6 },
  ] },
  { key: 'life', name: '生命周期维度', weight: '10%', full: 10, items: [
    { name: '新用户吸引占比', full: 4.5 }, { name: '沉默用户占比', full: 3.5 }, { name: '同类资费排名', full: 2 },
  ] },
]

/** 5G新通话代表性套餐下钻数据（十套餐，健康度按四大维度加权） */
export const wxthDrill = {
  mxld: {
    name: '5G新通话-明星来电',
    health: 88, healthDelta: '+3', healthLevel: '良好',
    healthNote: '整体稳健，新增与收入双升',
    dims: {
      eff: [{ s: 9, v: '286.5万' }, { s: 8, v: '1.02元' }, { s: 9, v: '+18.3%' }, { s: 8, v: '11.2%' }, { s: 8, v: '21.5%' }],
      market: [{ s: 10, v: '58.9万' }, { s: 9, v: '11.2万' }, { s: 8, v: '8.6%' }, { s: 9, v: '92%' }],
      quality: [{ s: 9, v: '0.12%' }, { s: 8, v: '2.6%' }, { s: 9, v: '99.1%' }],
      life: [{ s: 9, v: '18.4%' }, { s: 8, v: '7.2%' }, { s: 9, v: '前20%' }],
    },
    anomalies: [
      { level: 'high', text: 'T+6退订率 17.4%，超同类均值 5.3pp，重点监控' },
      { level: 'mid', text: '权益装机带动新增，但低值占比偏高' },
    ],
    scale: { label: '到达用户', value: '58.9万' },
    added: { label: '当月新增', value: '11.2万', delta: '+18.3%' },
    income: { label: '报表收入', value: '75.3万', delta: '+89.6%' },
    quality: [
      { label: '付费活跃率', value: '8.42%', delta: '+1.1pp' },
      { label: 'T+6退订率', value: '17.4%', delta: '+2.6pp' },
      { label: '户均实收', value: '1.02元', delta: null },
    ],
  },
  dlsp: {
    name: '5G新通话-点亮屏幕标准版',
    health: 85, healthDelta: '+2', healthLevel: '健康',
    healthNote: '版本结构逐步收敛，新增回升、退订健康',
    dims: {
      eff: [{ s: 8, v: '112.6万' }, { s: 8, v: '0.72元' }, { s: 9, v: '+6.8%' }, { s: 8, v: '13.5%' }, { s: 9, v: '18.4%' }],
      market: [{ s: 8, v: '21.4万' }, { s: 8, v: '1.2万' }, { s: 9, v: '7.8%' }, { s: 9, v: '92%' }],
      quality: [{ s: 9, v: '0.18%' }, { s: 8, v: '8.6%' }, { s: 9, v: '99.0%' }],
      life: [{ s: 9, v: '15.2%' }, { s: 8, v: '9.6%' }, { s: 9, v: '中上游' }],
    },
    anomalies: [{ level: 'low', text: '新增小幅回升，版本结构逐步收敛，暂无异动' }],
    scale: { label: '到达用户', value: '21.4万' },
    added: { label: '当月新增', value: '1.8万', delta: '+18.6%' },
    income: { label: '报表收入', value: '28.9万', delta: '+9.4%' },
    quality: [
      { label: '付费活跃率', value: '6.2%', delta: '+0.8pp' },
      { label: 'T+6退订率', value: '12.4%', delta: '-1.6pp' },
      { label: '户均实收', value: '1.34元', delta: null },
    ],
  },
  aisj: {
    name: '5G新通话-AI速记100分钟包',
    health: 85, healthDelta: '+1', healthLevel: '健康',
    healthNote: '分钟包梯度清晰，长期包承接良好，增长稳健',
    dims: {
      eff: [{ s: 9, v: '46.2万' }, { s: 8, v: '2.74元' }, { s: 9, v: '+9.2%' }, { s: 8, v: '12.8%' }, { s: 8, v: '18.6%' }],
      market: [{ s: 9, v: '3.2万' }, { s: 8, v: '1.0万' }, { s: 8, v: '8.1%' }, { s: 9, v: '90%' }],
      quality: [{ s: 9, v: '0.12%' }, { s: 9, v: '7.4%' }, { s: 9, v: '98.9%' }],
      life: [{ s: 8, v: '13.8%' }, { s: 8, v: '9.2%' }, { s: 9, v: '中上游' }],
    },
    anomalies: [{ level: 'low', text: '推出 300 分钟包后，长期包承接良好，本包新增稳定' }],
    scale: { label: '到达用户', value: '3.2万' },
    added: { label: '当月新增', value: '1.0万', delta: '+9.2%' },
    income: { label: '报表收入', value: '9.2万', delta: '+14.1%' },
    quality: [
      { label: '付费活跃率', value: '13.4%', delta: '+0.8pp' },
      { label: 'T+6退订率', value: '7.4%', delta: '-1.2pp' },
      { label: '户均实收', value: '2.74元', delta: null },
    ],
  },
  znfy: {
    name: '5G新通话-智能翻译100分钟包',
    health: 84, healthDelta: '+2', healthLevel: '良好',
    healthNote: '需求稳定，复购良好',
    dims: {
      eff: [{ s: 9, v: '58.6万' }, { s: 9, v: '2.81元' }, { s: 9, v: '+9.4%' }, { s: 9, v: '9.6%' }, { s: 9, v: '24.8%' }],
      market: [{ s: 9, v: '1.4万' }, { s: 9, v: '0.4万' }, { s: 9, v: '8.8%' }, { s: 9, v: '95%' }],
      quality: [{ s: 9, v: '0.08%' }, { s: 9, v: '7.9%' }, { s: 10, v: '99.3%' }],
      life: [{ s: 9, v: '16.2%' }, { s: 9, v: '6.4%' }, { s: 9, v: '前20%' }],
    },
    anomalies: [{ level: 'low', text: '叠加包复购占比提升至 32%，可做到期提醒运营' }],
    scale: { label: '到达用户', value: '1.4万' },
    added: { label: '当月新增', value: '0.4万', delta: '+5.6%' },
    income: { label: '报表收入', value: '3.9万', delta: '+7.2%' },
    quality: [
      { label: '付费活跃率', value: '14.1%', delta: '+0.4pp' },
      { label: 'T+6退订率', value: '7.9%', delta: '-0.9pp' },
      { label: '户均实收', value: '2.81元', delta: null },
    ],
  },
  tscy: {
    name: '畅享59元5G套餐',
    health: 72, healthDelta: '-9', healthLevel: '亚健康',
    healthNote: '典型"竞争性新增萎缩"——流量配置落后竞品，年轻新客转化下降；存量基本盘稳定，问题在获客端',
    dims: {
      eff: [{ s: 8, v: '5000万/月' }, { s: 7, v: '58元' }, { s: 7, v: '-1.6%' }, { s: 7, v: '16.5%' }, { s: 7, v: '14.2%' }],
      market: [{ s: 7, v: '86.0万' }, { s: 6, v: '0.28万' }, { s: 6, v: '5.2%' }, { s: 8, v: '90%' }],
      quality: [{ s: 8, v: '0.03%' }, { s: 8, v: '3.1%' }, { s: 9, v: '99.2%' }],
      life: [{ s: 6, v: '11.2%' }, { s: 6, v: '9.8%' }, { s: 6, v: '中游偏下' }],
    },
    anomalies: [
      { level: 'high', text: '新增下滑 35% 已持续 7 天，累计影响新增订购约 670 单，折合月收入损失约 3.9 万元' },
      { level: 'high', text: '新增转化仅 62 分（↓18），跌破正常水平，年轻客群在信息流渠道被截流' },
      { level: 'mid', text: '竞品对标 68 分（↓12），流量 30GB 落后竞品均值 42.5GB 约 29%' },
      { level: 'low', text: '存量基本盘稳定：投诉率 85 分、退订日均 82 单（+5.1%）均在正常范围' },
    ],
    scale: { label: '在网用户', value: '86.0万' },
    added: { label: '日均新增', value: '177单', delta: '-35%' },
    income: { label: '月收入', value: '5000万', delta: '+0.3%' },
    quality: [
      { label: '月度退订', value: '82单/日', delta: '+5.1%' },
      { label: '投诉率', value: '85分', delta: '-2' },
      { label: '续约率', value: '78分', delta: '-5' },
    ],
  },
  nlb: {
    name: '5G新通话能量包A',
    health: 85, healthDelta: '+2', healthLevel: '健康',
    healthNote: '体量稳步增长，户均实收改善，结构健康',
    dims: {
      eff: [{ s: 8, v: '27.8万' }, { s: 8, v: '0.71元' }, { s: 9, v: '+4.8%' }, { s: 8, v: '14.2%' }, { s: 9, v: '16.8%' }],
      market: [{ s: 8, v: '1.8万' }, { s: 8, v: '0.4万' }, { s: 9, v: '7.6%' }, { s: 9, v: '84%' }],
      quality: [{ s: 9, v: '0.16%' }, { s: 8, v: '9.8%' }, { s: 8, v: '98.2%' }],
      life: [{ s: 9, v: '12.6%' }, { s: 8, v: '10.4%' }, { s: 9, v: '中上游' }],
    },
    anomalies: [{ level: 'low', text: '覆盖与活跃双升，暂无显著异动' }],
    scale: { label: '到达用户', value: '1.8万' },
    added: { label: '当月新增', value: '0.4万', delta: '+18.6%' },
    income: { label: '报表收入', value: '2.1万', delta: '+8.4%' },
    quality: [
      { label: '付费活跃率', value: '10.6%', delta: '+0.9pp' },
      { label: 'T+6退订率', value: '9.8%', delta: '-1.2pp' },
      { label: '户均实收', value: '0.71元', delta: null },
    ],
  },
  aith: {
    name: '5G新通话-AI通话黄金会员',
    health: 82, healthDelta: '+2', healthLevel: '良好',
    healthNote: '会员分层承接良好',
    dims: {
      eff: [{ s: 9, v: '96.2万' }, { s: 10, v: '2.84元' }, { s: 9, v: '+11.8%' }, { s: 8, v: '8.4%' }, { s: 9, v: '26.1%' }],
      market: [{ s: 9, v: '2.6万' }, { s: 9, v: '0.7万' }, { s: 9, v: '9.2%' }, { s: 10, v: '96%' }],
      quality: [{ s: 9, v: '0.06%' }, { s: 9, v: '8.4%' }, { s: 9, v: '99.4%' }],
      life: [{ s: 9, v: '15.8%' }, { s: 9, v: '5.6%' }, { s: 10, v: '前20%' }],
    },
    anomalies: [{ level: 'low', text: '黄金/钻石会员 ARPU 明显高于基础会员' }],
    scale: { label: '到达用户', value: '2.6万' },
    added: { label: '当月新增', value: '0.7万', delta: '+9.2%' },
    income: { label: '报表收入', value: '7.4万', delta: '+11.8%' },
    quality: [
      { label: '付费活跃率', value: '11.3%', delta: '+0.7pp' },
      { label: 'T+6退订率', value: '8.4%', delta: '-1.0pp' },
      { label: '户均实收', value: '2.84元', delta: null },
    ],
  },
  qwth: {
    name: '5G新通话-趣味通话',
    health: 86, healthDelta: '+1', healthLevel: '健康',
    healthNote: '趣味玩法持续带动低频用户活跃，增长健康',
    dims: {
      eff: [{ s: 9, v: '32.6万' }, { s: 8, v: '2.24元' }, { s: 9, v: '+6.2%' }, { s: 9, v: '11.4%' }, { s: 8, v: '18.2%' }],
      market: [{ s: 8, v: '1.1万' }, { s: 8, v: '0.4万' }, { s: 9, v: '8.6%' }, { s: 9, v: '88%' }],
      quality: [{ s: 9, v: '0.12%' }, { s: 9, v: '7.8%' }, { s: 9, v: '98.8%' }],
      life: [{ s: 8, v: '15.4%' }, { s: 9, v: '8.6%' }, { s: 9, v: '中上游' }],
    },
    anomalies: [{ level: 'low', text: '潮智版占比提升，年轻客群稳定增长' }],
    scale: { label: '到达用户', value: '1.1万' },
    added: { label: '当月新增', value: '0.4万', delta: '+12.8%' },
    income: { label: '报表收入', value: '2.6万', delta: '+9.4%' },
    quality: [
      { label: '付费活跃率', value: '11.6%', delta: '+0.7pp' },
      { label: 'T+6退订率', value: '7.8%', delta: '-0.9pp' },
      { label: '户均实收', value: '2.24元', delta: null },
    ],
  },
  yyb: {
    name: '5G新通话6元包',
    health: 86, healthDelta: '+1', healthLevel: '健康',
    healthNote: '入门引流型产品，覆盖稳定，向高阶包转化良好',
    dims: {
      eff: [{ s: 8, v: '68.4万' }, { s: 9, v: '0.48元' }, { s: 9, v: '+12.4%' }, { s: 8, v: '15.6%' }, { s: 8, v: '15.8%' }],
      market: [{ s: 9, v: '12.3万' }, { s: 9, v: '2.6万' }, { s: 9, v: '8.8%' }, { s: 9, v: '92%' }],
      quality: [{ s: 9, v: '0.18%' }, { s: 9, v: '8.4%' }, { s: 9, v: '98.6%' }],
      life: [{ s: 9, v: '21.4%' }, { s: 8, v: '8.8%' }, { s: 8, v: '中上游' }],
    },
    anomalies: [{ level: 'low', text: '作为低门槛入口，向高阶功能包转化良好' }],
    scale: { label: '到达用户', value: '12.3万' },
    added: { label: '当月新增', value: '2.6万', delta: '+12.4%' },
    income: { label: '报表收入', value: '5.6万', delta: '+13.8%' },
    quality: [
      { label: '付费活跃率', value: '9.8%', delta: '+0.7pp' },
      { label: 'T+6退订率', value: '8.4%', delta: '-1.1pp' },
      { label: '户均实收', value: '0.48元', delta: null },
    ],
  },
  thzm: {
    name: '5G新通话-通话字幕',
    health: 83, healthDelta: '+1', healthLevel: '良好',
    healthNote: '基础能力承载，使用稳定',
    dims: {
      eff: [{ s: 9, v: '46.2万' }, { s: 9, v: '0.81元' }, { s: 9, v: '+9.8%' }, { s: 9, v: '10.4%' }, { s: 8, v: '22.6%' }],
      market: [{ s: 9, v: '3.6万' }, { s: 9, v: '0.5万' }, { s: 9, v: '8.4%' }, { s: 9, v: '93%' }],
      quality: [{ s: 9, v: '0.09%' }, { s: 9, v: '9.7%' }, { s: 9, v: '99.0%' }],
      life: [{ s: 9, v: '14.6%' }, { s: 9, v: '8.2%' }, { s: 9, v: '前30%' }],
    },
    anomalies: [{ level: 'low', text: '作为能力底座，承载各功能产品增强包' }],
    scale: { label: '到达用户', value: '3.6万' },
    added: { label: '当月新增', value: '0.5万', delta: '+4.2%' },
    income: { label: '报表收入', value: '2.9万', delta: '+9.8%' },
    quality: [
      { label: '付费活跃率', value: '10.4%', delta: '+0.2pp' },
      { label: 'T+6退订率', value: '9.7%', delta: '-0.5pp' },
      { label: '户均实收', value: '0.81元', delta: null },
    ],
  },
}

export const wxthDrillKeys = Object.keys(wxthDrill)

/** 哪些业务 tab 支持下钻到单产品（当前仅 5G新通话） */
export const drillableProduct = (key) => key === 'wxth'

/** 格式化上架时间（时间戳 → 年-月-日 时:分） */
export function formatSaleTime(ts) {
  if (!ts) return '—'
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return '—'
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 模块级共享状态：下钻选中的套餐 key 在组件卸载/重挂载后依然保留，保证折叠后恢复运营视图还原 */
export const opsDrillState = {
  key: null,
  reset() {
    this.key = null
  },
}
