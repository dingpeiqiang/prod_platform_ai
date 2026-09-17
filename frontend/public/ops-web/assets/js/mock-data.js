/* ============================================================
   单品监控运营 · 数据层 (mock-data.js)
   来源 方案/运营视图2/运营视图2.html 单品健康度详情，按商品归档为数据。
   product-detail.html 通过 ?product_id= 读取；未收录商品回退到 DEFAULT 画像。

   结构（每商品）：
   {
     name, code, type(单品|融合), statusClass/st-status, statusText,
     score, scoreClass(hs-green|hs-yellow|hs-red),
     tagline, desc, heroMetrics:[{label,value,color?}],
     dims:[{effect,market,quality,life 逐维度: 分维度分数 + 图表数据}],
     alert:{level(alert-green|alert-yellow|alert-red), text},
     updated, monthCaption
   }
   ============================================================ */
window.OPS_MOCK = {};

/* 月份横轴（1-12 通用） */
var MONTHS = ['1月','2月','3月','4月','5月','6月','7月','8月','9月','10月','11月','12月'];
window.OPS_MONTHS = MONTHS;

/* ============ 5G-A 套餐 159 元（健康画像） ============ */
window.OPS_MOCK['900102308'] = {
  name: '5G-A套餐159元',
  code: '900102308',
  type: '单品',
  statusClass: 'st-ok',
  statusText: '健康 · 已上线',
  score: 88,
  scoreClass: 'hs-green',
  taglineClass: 'background:var(--green-bg);color:var(--green);',
  tagline: '健康 · 较上月 +4 分',
  desc: '5G-A 网络驻留比持续提升，高值用户占比稳定；当月新增 1.82 万、新增占比 10.0% 保持稳健，续约率 91.5%、整体运营健康。',
  heroMetrics: [
    { label: '到达用户', value: '12.5 万' },
    { label: '当月新增', value: '1.82 万', color: 'var(--green)' },
    { label: '当月收入', value: '3,168 万' }
  ],
  dims: {
    effect: {
      score: 40,
      income: { month: [2950,3010,2980,3050,3120,3060,3150,3210,3168], cum: [2950,5960,8940,11990,15110,18170,21320,24530,27698], yoy: [6.5,7.1,6.8,7.9,8.7,9.0,9.2,9.4,9.6], mom: [null,2.0,-1.0,2.3,2.3,-1.9,2.9,1.9,-1.3] },
      profit: { value: '14.2%', data: [11.8,12.2,12.0,12.9,13.4,13.1,13.8,14.0,14.2] },
      crm: { value: '16.5%', data: [18.2,17.8,18.0,17.4,17.0,17.2,16.8,16.6,16.5] }
    },
    market: {
      score: 22,
      arrive: [10.8,11.0,11.2,11.4,11.6,11.8,12.0,12.3,12.5],
      net: [6.4,6.6,6.2,6.9,7.1,6.8,7.4,7.5,7.9],
      yoy: [5.8,6.0,6.1,6.3,6.5,6.7,6.9,7.1,7.3],
      mom: [null,1.9,1.8,1.8,1.8,1.7,1.7,2.5,1.6],
      active: [82.1,82.6,83.0,83.9,84.5,85.2,85.8,86.1,86.4],
      paid: [89.5,90.0,90.3,91.0,91.4,91.9,92.2,92.5,92.8],
      channel: {
        '2025-12':[['线下营业厅',41],['中国电信线上渠道',26],['10000热线',19],['安徽电信线上渠道',14]],
        '2026-09':[['线下营业厅',38],['中国电信线上渠道',28],['10000热线',20],['安徽电信线上渠道',14]]
      },
      channelDefault: '2026-09'
    },
    quality: {
      score: 18,
      chips: [
        { c:'c-green', ico:'📈', label:'T+1 活跃率', value:'78.4%', trend:'▲0.2', em:'新增次日仍活跃' },
        { c:'c-yellow', ico:'📉', label:'T+3 退订率', value:'1.40%', trend:'▼0.06', em:'新增 3 日退订' },
        { c:'c-blue',   ico:'📉', label:'T+6 退订率', value:'2.30%', trend:'▼0.06', em:'新增 6 日退订' }
      ],
      retain: { active: [74.2,74.9,75.5,76.1,76.8,77.3,77.8,78.2,78.4], t3: [1.85,1.78,1.70,1.66,1.60,1.55,1.50,1.46,1.40], t6: [2.82,2.75,2.68,2.60,2.54,2.48,2.42,2.36,2.30] },
      serviceChips: [
        { c:'c-blue', ico:'✅', label:'业务办理成功率', value:'99.2%', trend:'▲0.1', em:'高于 98% 目标线' },
        { c:'c-red',  ico:'⚠️', label:'资费投诉率', value:'0.02%', trend:'▼', em:'远低于 0.1% 预警线' }
      ],
      service: { ok: [98.4,98.5,98.7,98.8,98.9,99.0,99.0,99.1,99.2], grievance: [0.095,0.088,0.081,0.075,0.068,0.062,0.052,0.038,0.02] }
    },
    life: {
      score: 8,
      nodes: [
        { tagClass:'ln-t-new', tag:'新增', ico:'c-blue',  name:'新增用户', value:'10.0', unit:'%', pct:['var(--blue)','本月新增占比'] },
        { tagClass:'ln-t-act', tag:'激活', ico:'c-green', name:'活跃用户', value:'86.4', unit:'%', pct:['var(--green)','月活跃率'] },
        { tagClass:'ln-t-ret', tag:'留存', ico:'c-purple',name:'留存用户', value:'97.7', unit:'%', pct:['var(--purple)','T+6 留存'] },
        { tagClass:'ln-t-sil', tag:'沉默', ico:'c-amber', name:'沉默用户', value:'8.5',  unit:'%', pct:['#D48806','沉默占比'] },
        { tagClass:'ln-t-churn', tag:'流失 · 续约', ico:'c-red', name:'续约率', value:'91.5', unit:'%', pct:['var(--red)','到期续约率'] }
      ],
      flow: [
        { label:'活跃用户', pct:'86.4%', w:86.4, color:'linear-gradient(90deg,#00A86C,#00B578)' },
        { label:'沉默', pct:'8.5%', w:8.5, color:'linear-gradient(90deg,#C07A06,#D48806)' },
        { label:'流失', pct:'5.1%', w:5.1, color:'linear-gradient(90deg,#D91B27,#F5222D)' }
      ]
    }
  },
  alert: {
    level: 'alert-green',
    text: '<b>存量基本盘稳定：</b>投诉率 0.02%、T+6 退订率 2.30%、业务办理成功率 99.2%、续约率 91.5% 均在正常范围'
  }
};

/* ============ 5G-A 套餐单品 299 元（亚健康画像） ============ */
window.OPS_MOCK['900102310'] = {
  name: '5G-A套餐单品299元',
  code: '900102310',
  type: '单品',
  statusClass: 'st-warn',
  statusText: '亚健康 · 需关注',
  score: 79,
  scoreClass: 'hs-yellow',
  taglineClass: 'background:var(--yellow-bg);color:#D48806;',
  tagline: '亚健康 · 较上月 -3 分',
  desc: '高端档位用户增长放缓，收入环比连续两月下滑（-2.8%），利润率降至 11.6% 而营销费用占比升至 18.9%，续约率 88.6% 偏低、沉默用户占比 14.2% 偏高，需重点维系高值存量用户并加大权益焕活。',
  heroMetrics: [
    { label: '到达用户', value: '4.2 万' },
    { label: '当月新增', value: '0.42 万', color: 'var(--green)' },
    { label: '当月收入', value: '1,246 万' }
  ],
  dims: {
    effect: {
      score: 33,
      income: { month: [1395,1380,1368,1352,1340,1326,1300,1282,1246], cum: [1395,2775,4143,5495,6835,8161,9461,10743,11989], yoy: [5.2,4.9,4.6,4.3,4.0,3.6,3.2,2.7,2.1], mom: [null,-1.1,-0.9,-1.2,-0.9,-1.0,-2.0,-1.4,-2.8] },
      profit: { value: '11.6%', data: [12.8,12.6,12.5,12.4,12.2,12.1,11.9,11.8,11.6] },
      crm: { value: '18.9%', data: [17.2,17.5,17.8,17.9,18.1,18.3,18.5,18.7,18.9] }
    },
    market: {
      score: 19,
      arrive: [3.4,3.5,3.6,3.7,3.8,3.9,4.0,4.1,4.2],
      net: [0.22,0.20,0.19,0.18,0.17,0.16,0.15,0.14,0.12],
      yoy: [3.8,3.5,3.3,3.1,2.9,2.7,2.5,2.3,2.1],
      mom: [null,2.9,2.9,2.8,2.7,2.6,2.6,2.5,2.4],
      active: [78.2,78.5,78.9,79.2,79.5,79.8,80.1,80.3,80.5],
      paid: [88.0,88.2,88.5,88.8,89.0,89.2,89.4,89.5,89.6],
      channel: {
        '2025-12':[['线下营业厅',44],['中国电信线上渠道',26],['10000热线',17],['安徽电信线上渠道',13]],
        '2026-09':[['线下营业厅',43],['中国电信线上渠道',27],['10000热线',17],['安徽电信线上渠道',13]]
      },
      channelDefault: '2026-09'
    },
    quality: {
      score: 17,
      chips: [
        { c:'c-green', ico:'📈', label:'T+1 活跃率', value:'76.1%', trend:'▲0.3', em:'新增次日仍活跃' },
        { c:'c-red',   ico:'⚠️', label:'T+3 退订率', value:'2.10%', trend:'▼0.03', em:'新增 3 日退订' },
        { c:'c-yellow',ico:'⚠️', label:'T+6 退订率', value:'3.20%', trend:'▼0.02', em:'新增 6 日退订' }
      ],
      retain: { active: [72.0,72.6,73.1,73.7,74.2,74.9,75.4,75.8,76.1], t3: [2.40,2.34,2.30,2.25,2.21,2.18,2.15,2.13,2.10], t6: [3.50,3.44,3.40,3.35,3.32,3.29,3.26,3.23,3.20] },
      serviceChips: [
        { c:'c-yellow',ico:'⚠️', label:'业务办理成功率', value:'98.4%', trend:'▲0.1', em:'略低于 99% 目标' },
        { c:'c-red',   ico:'⚠️', label:'资费投诉率', value:'0.04%', trend:'▼', em:'接近 0.05% 预警' }
      ],
      service: { ok: [98.4,98.5,98.6,98.5,98.4,98.5,98.4,98.3,98.4], grievance: [0.041,0.042,0.040,0.041,0.040,0.039,0.040,0.039,0.04] }
    },
    life: {
      score: 6,
      nodes: [
        { tagClass:'ln-t-new', tag:'新增', ico:'c-blue',  name:'新增用户', value:'10.5', unit:'%', pct:['var(--blue)','本月新增占比'] },
        { tagClass:'ln-t-act', tag:'激活', ico:'c-green', name:'活跃用户', value:'80.5', unit:'%', pct:['var(--green)','月活跃率'] },
        { tagClass:'ln-t-ret', tag:'留存', ico:'c-purple',name:'留存用户', value:'96.8', unit:'%', pct:['var(--purple)','T+6 留存'] },
        { tagClass:'ln-t-sil', tag:'沉默', ico:'c-amber', name:'沉默用户', value:'14.2', unit:'%', pct:['#D48806','沉默占比'] },
        { tagClass:'ln-t-churn', tag:'流失 · 续约', ico:'c-red', name:'续约率', value:'88.6', unit:'%', pct:['var(--red)','到期续约率'] }
      ],
      flow: [
        { label:'活跃用户', pct:'80.5%', w:80.5, color:'linear-gradient(90deg,#00A86C,#00B578)' },
        { label:'沉默', pct:'14.2%', w:14.2, color:'linear-gradient(90deg,#C07A06,#D48806)' },
        { label:'流失', pct:'5.3%', w:5.3, color:'linear-gradient(90deg,#D91B27,#F5222D)' }
      ]
    }
  },
  alert: {
    level: 'alert-yellow',
    text: '<b>收入连续两月环比下滑（-2.8%）</b>，续约率 88.6% 与 T+6 退订率 3.20% 均偏离健康线、沉默用户占比 14.2% 偏高，建议针对到期用户开展权益加码维系并加大沉默用户唤醒'
  }
};

/* 默认画像：未收录商品回退到此（以 159 健康画像为模板） */
function makeDefault(code, name, type) {
  var base = JSON.parse(JSON.stringify(window.OPS_MOCK['900102308']));
  base.code = code || 'UNKNOWN';
  base.name = name || ('销售品 ' + base.code);
  base.type = type || '单品';
  return base;
}
window.OPS_MOCK_DEFAULT = makeDefault;
