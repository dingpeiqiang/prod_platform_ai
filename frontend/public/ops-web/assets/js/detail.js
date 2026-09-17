/* ============================================================
   单品监控运营详情页 · 渲染与图表 (detail.js)
   - 解析 ?product_id=&name= 定位商品数据（OPS_MOCK / 默认画像）
   - 把数据注入到 product-detail.html 静态骨架
   - 初始化四维度 echarts + 标签切换
   纯前端确定性渲染，无后端依赖（POC 数据层见 mock-data.js）
   ============================================================ */
(function () {
  'use strict';

  var fontSub = '#5C6B84', fontLt = '#8896AC', gridLine = '#EEF2F7';
  var blue = '#0A5FD6', green = '#00B578', red = '#F5222D', yellow = '#F5A623';
  var MONTHS = window.OPS_MONTHS || [];

  var charts = [];

  function mk(id) {
    var el = document.getElementById(id);
    if (!el || typeof echarts === 'undefined') return null;
    var c = echarts.init(el);
    charts.push(c);
    return c;
  }

  function slice9(arr) { return arr.slice(0, 9); }
  function months9() { return MONTHS.slice(0, 9); }

  function barGrad(c1, c2) {
    return new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: c1 }, { offset: 1, color: c2 }]);
  }

  function baseAxis(extra) {
    var o = {
      grid: { left: 6, right: 12, top: 24, bottom: 4, containLabel: true },
      tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'shadow' } },
      xAxis: { type: 'category', data: months9(), axisLine: { lineStyle: { color: gridLine } }, axisTick: { show: false }, axisLabel: { color: fontSub, fontSize: 11 } },
      yAxis: { type: 'value', splitLine: { lineStyle: { color: gridLine } }, axisLabel: { color: fontLt, fontSize: 11 } },
      color: [blue, yellow, green]
    };
    for (var k in extra) o[k] = extra[k];
    return o;
  }

  function lineSeries(name, data, color, area) {
    var s = { name: name, type: 'line', data: slice9(data), smooth: true, symbolSize: 5, lineStyle: { width: 2.5, color: color }, itemStyle: { color: color } };
    if (area) s.areaStyle = { opacity: .12, color: color };
    return s;
  }

  function lastMark(val, color) {
    return { type: 'max', name: '最新', symbol: 'circle', symbolSize: 51,
      itemStyle: { color: color, borderColor: '#fff', borderWidth: 2, shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.12)' },
      label: { show: true, fontSize: 10, fontWeight: 700, color: color, position: 'top', formatter: function () { return val; }, offset: [0, -4] } };
  }

  function renderIncome(id, income) {
    var c = mk(id);
    if (!c) return;
    var stateEl = id + 'Rate', rateState = 'yoy';
    var rateData = { yoy: slice9(income.yoy), mom: slice9(income.mom) };
    function draw() {
      c.setOption({
        grid: { left: 6, right: 6, top: 10, bottom: 4, containLabel: true },
        tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'cross' } },
        xAxis: { type: 'category', data: months9(), boundaryGap: true, axisLine: { lineStyle: { color: gridLine } }, axisTick: { show: false }, axisLabel: { color: fontSub, fontSize: 11 } },
        yAxis: [
          { type: 'value', splitLine: { lineStyle: { color: gridLine, type: 'dashed' } }, axisLabel: { color: fontLt, fontSize: 10 } },
          { type: 'value', name: '%', nameTextStyle: { color: fontLt, fontSize: 10 }, splitLine: { show: false }, axisLabel: { color: fontLt, fontSize: 10 } }
        ],
        series: [
          { name: '当月收入(万元)', type: 'bar', data: slice9(income.month), barWidth: 11, barCategoryGap: '20%', itemStyle: { color: barGrad('#1d8bff', '#9fd8ff'), shadowColor: 'rgba(29,139,255,0.35)', shadowBlur: 8, shadowOffsetY: 3, borderRadius: [2, 2, 0, 0] }, z: 3 },
          { name: '累计收入(万元)', type: 'bar', data: slice9(income.cum), barWidth: 11, barCategoryGap: '20%', itemStyle: { color: barGrad('#0a4eb0', '#7ab4f5'), shadowColor: 'rgba(10,78,176,0.35)', shadowBlur: 8, shadowOffsetY: 3, borderRadius: [2, 2, 0, 0] }, z: 3 },
          { name: (rateState === 'yoy' ? '同比(%)' : '环比(%)'), type: 'line', yAxisIndex: 1, data: rateData[rateState], smooth: true, symbol: 'circle', symbolSize: 5, lineStyle: { width: 2 }, itemStyle: { color: yellow, borderColor: '#fff', borderWidth: 1.5 }, z: 12 }
        ]
      });
    }
    draw();
    var btns = document.querySelectorAll('#' + stateEl + ' button');
    if (btns[0]) btns[0].classList.add('active');
    btns.forEach(function (btn) {
      btn.addEventListener('click', function () {
        btns.forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        rateState = btn.getAttribute('data-rate');
        draw();
      });
    });
  }

  function renderLine(id, seriesArr, color) {
    var c = mk(id);
    if (!c) return;
    c.setOption(Object.assign(baseAxis(), { tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'line' } }, color: [color], series: seriesArr }));
  }

  function renderMktScale(id, market) {
    var c = mk(id);
    if (!c) return;
    var state = 'yoy';
    var rd = { yoy: slice9(market.yoy), mom: slice9(market.mom) };
    function draw() {
      c.setOption({
        grid: { left: 6, right: 6, top: 8, bottom: 2, containLabel: true },
        tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'cross' } },
        xAxis: { type: 'category', data: months9(), boundaryGap: true, axisLine: { lineStyle: { color: gridLine } }, axisTick: { show: false }, axisLabel: { color: fontSub, fontSize: 10 } },
        yAxis: [
          { type: 'value', splitLine: { lineStyle: { color: gridLine, type: 'dashed' } }, axisLabel: { color: fontLt, fontSize: 10 } },
          { type: 'value', name: '%', nameTextStyle: { color: fontLt, fontSize: 9 }, splitLine: { show: false }, axisLabel: { color: fontLt, fontSize: 10 } }
        ],
        series: [
          { name: '到达用户(万)', type: 'bar', data: slice9(market.arrive), barWidth: 8, itemStyle: { color: barGrad('#1d8bff', '#9fd8ff'), shadowColor: 'rgba(29,139,255,0.35)', shadowBlur: 7, shadowOffsetY: 3, borderRadius: [2, 2, 0, 0] }, z: 3 },
          { name: '月净增(万)', type: 'bar', data: slice9(market.net), barWidth: 8, itemStyle: { color: barGrad('#0a4eb0', '#7ab4f5'), shadowColor: 'rgba(10,78,176,0.35)', shadowBlur: 7, shadowOffsetY: 3, borderRadius: [2, 2, 0, 0] }, z: 3 },
          { name: (state === 'yoy' ? '同比(%)' : '环比(%)'), type: 'line', yAxisIndex: 1, data: rd[state], smooth: true, symbol: 'circle', symbolSize: 4, lineStyle: { width: 2 }, itemStyle: { color: yellow, borderColor: '#fff', borderWidth: 1.5 }, z: 12 }
        ]
      });
    }
    draw();
    var btns = document.querySelectorAll('#' + id + 'Rate button');
    if (btns[0]) btns[0].classList.add('active');
    btns.forEach(function (btn) {
      btn.addEventListener('click', function () {
        btns.forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        state = btn.getAttribute('data-grate');
        draw();
      });
    });
  }

  function renderChannel(id, market, selectId) {
    var c = mk(id);
    if (!c) return;
    var colors = ['#1d8bff', '#0a4eb0', '#10b981', '#F5A623'];
    var data = market.channel || {};
    var def = market.channelDefault || Object.keys(data)[0];
    var sel = document.getElementById(selectId);
    if (sel) {
      sel.innerHTML = '';
      Object.keys(data).forEach(function (k) {
        var opt = document.createElement('option');
        opt.value = k;
        opt.textContent = k.replace('-', '年') + '月';
        opt.selected = (k === def);
        sel.appendChild(opt);
      });
    }
    function draw(k) {
      if (!data[k]) return;
      c.setOption({
        color: colors,
        tooltip: { formatter: '{b}：{c}%', confine: true },
        legend: { bottom: 0, itemWidth: 9, itemHeight: 9, textStyle: { fontSize: 10, color: fontSub } },
        series: [{ type: 'pie', radius: ['46%', '72%'], center: ['50%', '46%'], avoidLabelOverlap: false,
          label: { show: true, formatter: '{d}%', fontSize: 10, color: fontSub }, labelLine: { length: 8, length2: 6 },
          data: data[k].map(function (d) { return { name: d[0], value: d[1] }; }) }]
      }, true);
    }
    draw(def);
    if (sel) sel.addEventListener('change', function () { draw(this.value); });
  }

  function renderQRetain(id, retain) {
    var c = mk(id);
    if (!c) return;
    c.setOption({
      grid: { left: 6, right: 10, top: 16, bottom: 2, containLabel: true },
      tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'line' } },
      color: [green, yellow, blue],
      xAxis: { type: 'category', data: months9(), boundaryGap: false, axisLine: { lineStyle: { color: gridLine } }, axisTick: { show: false }, axisLabel: { color: fontSub, fontSize: 10 } },
      yAxis: [
        { type: 'value', name: '活跃率 %', nameTextStyle: { color: fontLt, fontSize: 9, padding: [0, 0, 0, 26] }, splitLine: { lineStyle: { color: gridLine, type: 'dashed' } }, axisLabel: { color: fontLt, fontSize: 10 } },
        { type: 'value', name: '退订率 %', nameTextStyle: { color: fontLt, fontSize: 9, padding: [0, 24, 0, 0] }, splitLine: { show: false }, axisLabel: { color: fontLt, fontSize: 10 } }
      ],
      series: [
        { name: 'T+1活跃率(%)', type: 'line', data: slice9(retain.active), yAxisIndex: 0, smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { width: 3 }, itemStyle: { color: green, borderColor: '#fff', borderWidth: 1.5, shadowBlur: 6, shadowColor: 'rgba(0,181,120,0.35)' }, markPoint: lastMark(slice9(retain.active)[8], green), z: 12 },
        { name: 'T+3退订率(%)', type: 'line', data: slice9(retain.t3), yAxisIndex: 1, smooth: true, symbol: 'circle', symbolSize: 5, lineStyle: { width: 2.5 }, itemStyle: { color: yellow, borderColor: '#fff', borderWidth: 1.5 }, markPoint: lastMark(slice9(retain.t3)[8], yellow), z: 12 },
        { name: 'T+6退订率(%)', type: 'line', data: slice9(retain.t6), yAxisIndex: 1, smooth: true, symbol: 'circle', symbolSize: 5, lineStyle: { width: 2.5 }, itemStyle: { color: blue, borderColor: '#fff', borderWidth: 1.5 }, markPoint: lastMark(slice9(retain.t6)[8], blue), z: 12 }
      ]
    });
  }

  function renderQService(id, service) {
    var c = mk(id);
    if (!c) return;
    c.setOption({
      grid: { left: 6, right: 10, top: 16, bottom: 2, containLabel: true },
      tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'line' } },
      color: [blue, red],
      xAxis: { type: 'category', data: months9(), boundaryGap: false, axisLine: { lineStyle: { color: gridLine } }, axisTick: { show: false }, axisLabel: { color: fontSub, fontSize: 10 } },
      yAxis: [
        { type: 'value', name: '成功率 %', nameTextStyle: { color: fontLt, fontSize: 9, padding: [0, 0, 0, 26] }, splitLine: { lineStyle: { color: gridLine, type: 'dashed' } }, axisLabel: { color: fontLt, fontSize: 10 } },
        { type: 'value', name: '投诉率 %', nameTextStyle: { color: fontLt, fontSize: 9, padding: [0, 24, 0, 0] }, splitLine: { show: false }, axisLabel: { color: fontLt, fontSize: 10 } }
      ],
      series: [
        { name: '业务办理成功率(%)', type: 'line', data: slice9(service.ok), yAxisIndex: 0, smooth: true, symbol: 'circle', symbolSize: 6, lineStyle: { width: 3 }, itemStyle: { color: blue, borderColor: '#fff', borderWidth: 1.5, shadowBlur: 6, shadowColor: 'rgba(10,95,214,0.35)' }, markPoint: lastMark(slice9(service.ok)[8], blue), z: 12 },
        { name: '资费投诉率(%)', type: 'line', data: slice9(service.grievance), yAxisIndex: 1, smooth: true, symbol: 'circle', symbolSize: 5, lineStyle: { width: 2.5 }, itemStyle: { color: red, borderColor: '#fff', borderWidth: 1.5 }, markPoint: lastMark(parseFloat(slice9(service.grievance)[8]).toFixed(2), red), z: 12 }
      ]
    });
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  /* ===== 生命周期图标（内联 SVG，与源页面一致） ===== */
  var LIFE_ICONS = {
    'c-blue': '<svg viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M16 11a4 4 0 1 0-8 0 4 4 0 0 0 8 0z"/><path d="M2 19a6 6 0 0 1 10-4.2"/><path d="M19 8v6M16 11h6"/></svg>',
    'c-green': '<svg viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2 3 14h7l-1 8 10-12h-7l1-8z"/></svg>',
    'c-purple': '<svg viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3 4 6v5c0 5 3.4 8.5 8 10 4.6-1.5 8-5 8-10V6l-8-3z"/><path d="m9 12 2 2 4-4"/></svg>',
    'c-amber': '<svg viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 13.5A9 9 0 1 1 10.5 3a7 7 0 0 0 10.5 10.5z"/></svg>',
    'c-red': '<svg viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a9 9 0 1 1-2.9-6.6"/><path d="M21 3v5h-5"/></svg>'
  };

  function fillHero(d) {
    document.getElementById('hd-name').textContent = d.name;
    document.getElementById('hd-code').textContent = d.code;
    var typeEl = document.getElementById('hd-type');
    typeEl.textContent = d.type;
    typeEl.className = 'prod-type ' + (d.type === '融合' ? 'pt-fusion' : 'pt-single');

    var scoreEl = document.getElementById('hd-score');
    scoreEl.textContent = d.score;
    scoreEl.className = 'hero-score ' + d.scoreClass;
    document.getElementById('hd-tagline').style.cssText = d.taglineClass;
    document.getElementById('hd-tagline').textContent = d.tagline;
    document.getElementById('hd-desc').textContent = d.desc;

    var hm = document.getElementById('hd-metrics');
    hm.innerHTML = d.heroMetrics.map(function (m) {
      return '<div>' + escapeHtml(m.label) + '<b' + (m.color ? ' style="color:' + m.color + '"' : '') + '>' + escapeHtml(m.value) + '</b></div>';
    }).join('');

    var statusEl = document.getElementById('hd-status');
    if (statusEl) {
      statusEl.className = 'status-pill ' + d.statusClass;
      statusEl.innerHTML = '<span class="status-dot"></span>' + escapeHtml(d.statusText);
    }
    var updated = document.getElementById('hd-updated');
    if (updated) updated.textContent = (d.updated || '2026年9月更新');
  }

  function fillLife(d) {
    var life = d.dims.life;
    var wrap = document.getElementById('life-nodes');
    wrap.innerHTML = life.nodes.map(function (n, i) {
      return '<div class="ops-life-node">' +
        '<span class="ops-tag ' + n.tagClass + '">' + escapeHtml(n.tag) + '</span>' +
        '<div class="ln-ico ' + n.ico + '">' + (LIFE_ICONS[n.ico] || '') + '</div>' +
        '<div class="ln-name">' + escapeHtml(n.name) + '</div>' +
        '<div class="ln-val">' + escapeHtml(n.value) + '<small>' + escapeHtml(n.unit) + '</small></div>' +
        '<div class="ln-pct"><i style="background:' + n.pct[0] + ';"></i>' + escapeHtml(n.pct[1]) + '</div>' +
        '</div>';
    }).join('');
    var flow = document.getElementById('life-flow');
    flow.innerHTML = life.flow.map(function (f) {
      return '<div class="ops-flow-seg" style="width:' + f.w + '%;background:' + f.color + ';">' + escapeHtml(f.label) + '<small>' + escapeHtml(f.pct) + '</small></div>';
    }).join('');
  }

  function fillChips(elId, chips) {
    var el = document.getElementById(elId);
    if (!el) return;
    el.innerHTML = chips.map(function (chip) {
      return '<div class="ops-q-chip ' + chip.c + '"><span class="q-ico">' + chip.ico + '</span><span>' + chip.label + '</span><div class="q-row"><b>' + chip.value + '</b><span class="q-trend">' + chip.trend + '</span></div><em>' + chip.em + '</em></div>';
    }).join('');
  }

  function fillAlert(d) {
    var el = document.getElementById('hd-alert');
    if (!el) return;
    el.className = 'alert ' + d.alert.level;
    var icon = d.alert.level === 'alert-green' ? '✓' : '!';
    el.innerHTML = '<div class="alert-icon">' + icon + '</div><div class="alert-text">' + d.alert.text + '</div>';
  }

  /* 维度分数填充 */
  function fillDimScore(id, score) {
    var el = document.getElementById(id);
    if (!el) return;
    el.innerHTML = score + '<small> 分</small>';
  }

  function wireTab(tabId, paneMap) {
    var tabs = document.querySelectorAll('#' + tabId + ' button');
    if (!tabs[0]) return;
    tabs.forEach(function (btn) {
      btn.addEventListener('click', function () {
        tabs.forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        var key = btn.getAttribute('data-effect') || btn.getAttribute('data-utab') || btn.getAttribute('data-qt');
        var target = paneMap[key];
        Object.keys(paneMap).forEach(function (k) {
          var el = document.getElementById(paneMap[k]);
          if (el) el.classList.toggle('active', k === key);
        });
        setTimeout(function () { charts.forEach(function (c) { if (c) c.resize(); }); }, 20);
      });
    });
  }

  function renderAll(d) {
    fillHero(d);
    fillLife(d);
    fillAlert(d);
    var dims = d.dims;
    fillDimScore('score-effect', dims.effect.score);
    fillDimScore('score-market', dims.market.score);
    fillDimScore('score-quality', dims.quality.score);
    fillDimScore('score-life', dims.life.score);

    fillChips('q-retain-chips', dims.quality.chips);
    fillChips('q-service-chips', dims.quality.serviceChips);

    renderIncome('opsEffectIncome', dims.effect.income);
    renderLine('opsEffectProfit', [lineSeries('利润率(%)', dims.effect.profit.data, blue)], blue);
    renderLine('opsEffectCrm', [lineSeries('营销费用占比(%)', dims.effect.crm.data, yellow)], yellow);

    renderMktScale('opsMktScale', dims.market);
    renderLine('opsActiveTrend', [lineSeries('活跃用户率(%)', dims.market.active, green), lineSeries('付费活跃率(%)', dims.market.paid, blue)], [green, blue]);
    renderChannel('opsChannelPie', dims.market, 'opsChannelMonth');

    renderQRetain('opsQRetain', dims.quality.retain);
    renderQService('opsQService', dims.quality.service);

    wireTab('opsEffectTabs', { income: 'opsEffectIncomePane', profit: 'opsEffectProfitPane', crm: 'opsEffectCrmPane' });
    wireTab('opsUserTabs', { scale: 'opsUserScalePane', active: 'opsUserActivePane' });
    wireTab('opsQualityTabs', { retain: 'opsQRetainPane', service: 'opsQServicePane' });
  }

  function getParam(name) {
    var m = new RegExp('[?&]' + name + '=([^&]*)').exec(window.location.search);
    return m ? decodeURIComponent(m[1]) : '';
  }

  function main() {
    var code = getParam('product_id') || getParam('productId') || getParam('offer_id') || getParam('offerId');
    var data = window.OPS_MOCK[code] || window.OPS_MOCK_DEFAULT(code, getParam('name'), getParam('type'));
    if (code) data.code = code;
    var name = getParam('name');
    if (name) data.name = name;
    document.title = data.name + ' · 单品监控运营';
    renderAll(data);
    var back = document.getElementById('back-link');
    if (back && !getParam('back')) back.style.display = 'none';
    window.addEventListener('resize', function () { charts.forEach(function (c) { if (c) c.resize(); }); });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', main);
  } else {
    main();
  }
})();
