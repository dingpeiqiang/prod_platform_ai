
function opsOpenRules(){
  var panel = document.getElementById('ops-rules-panel');
  if (!panel) return;
  panel.style.display = 'block';
  document.querySelectorAll('#view-board [id^="health-detail"]').forEach(function(el){ el.style.display='none'; });
  var bl = document.getElementById('board-list'); if (bl) bl.style.display = 'none';
  if (window.__opsApplyScale) window.__opsApplyScale();
  setTimeout(function(){ opsScrollTo(panel); }, 30);
  setTimeout(function(){ opsScrollTo(panel); }, 220);
}
function opsCloseRules(){
  var panel = document.getElementById('ops-rules-panel');
  if (panel) panel.style.display = 'none';
  opsShowPortrait();
  var hd = document.getElementById('health-detail');
  if (hd) { setTimeout(function(){ opsScrollTo(hd); }, 30); }
}
// 把某个元素（运营视图内的区块）顶部对齐到视口顶部
function opsScrollTo(el){
  if (!el) return;
  var y = el.getBoundingClientRect().top + window.pageYOffset - 8;
  try { window.scrollTo({ top: y < 0 ? 0 : y, behavior: 'auto' }); } catch(e){ window.scrollTo(0, y < 0 ? 0 : y); }
}
function opsScrollToRoot(){
  var r = document.getElementById('ops-view-root');
  opsScrollTo(r);
}
function opsShowPortrait(){
  var board = document.getElementById('view-board');
  if (board && !board.classList.contains('active')) {
    document.querySelectorAll('#ops-view-root .view').forEach(function(v){ v.classList.remove('active'); });
    board.classList.add('active');
  }
  var panel = document.getElementById('ops-rules-panel');
  if (panel) panel.style.display = 'none';
  var bl = document.getElementById('board-list'); if (bl) bl.style.display = 'none';
  var hd = document.getElementById('health-detail');
  if (hd) hd.style.display = 'block';
  // 标题设为商品实例单品（随实例数据联动，默认回营199 元演示单品）
  var n = document.getElementById('hd-name'); if (n) n.textContent = (window.__WB_INSTANCE && window.__WB_INSTANCE.name) || '5G-A套餐单品199元';
  var c = document.getElementById('hd-code'); if (c) c.textContent = (window.__WB_INSTANCE && window.__WB_INSTANCE.code) || '900113045';
  setTimeout(function(){
    if (window.__opsCharts) window.__opsCharts.forEach(function(cc){ if (cc && cc.resize) cc.resize(); });
    if (window.__srChartsResize) window.__srChartsResize();
    if (window.__opsApplyScale) window.__opsApplyScale();
  }, 60);
}


  function switchView(name){
    document.querySelectorAll('.view').forEach(v=>v.classList.remove('active'));
    document.getElementById('view-'+name).classList.add('active');
    if(name==='board'){ closeHealth(); }
    // view-board 之前处于 display:none，ECharts 付00 尺寸初始化；激活后需 resize 重绘
    if(name==='board'){
      setTimeout(function(){
        if (window.__srChartsResize) window.__srChartsResize();
      }, 50);
    }
    window.scrollTo({top:0,behavior:'smooth'});
  }

  var healthProfiles = {
    '900102308': 'health-detail',
    '900102310': 'health-detail-299'
  };

  function openHealth(name, code, type){
    var active = document.querySelector('.view.active');
    if (active && active.id !== 'view-board') {
      document.querySelectorAll('.view').forEach(v=>v.classList.remove('active'));
      document.getElementById('view-board').classList.add('active');
    }
    var targetId = healthProfiles[code] || 'health-detail';
    var detail = document.getElementById(targetId);
    document.querySelectorAll('#view-board [id^="health-detail"]').forEach(function(el){ el.style.display='none'; });
    document.getElementById('board-list').style.display='none';
    if (targetId === 'health-detail') {
      document.getElementById('hd-name').textContent = name;
      document.getElementById('hd-code').textContent = code;
      setType('hd-type', type);
    } else {
      document.getElementById('hd-name-299').textContent = name;
      document.getElementById('hd-code-299').textContent = code;
      setType('hd-type-299', type);
    }
    detail.style.display='block';
    setTimeout(function(){
      detail.scrollIntoView({behavior:'smooth', block:'start'});
      if (window.__opsCharts) window.__opsCharts.forEach(function(c){ if (c) c.resize(); });
    }, 50);
  }

  function setType(id, type){
    var typeEl = document.getElementById(id);
    typeEl.textContent = type;
    typeEl.className = 'prod-type ' + (type==='融合' ? 'pt-fusion' : 'pt-single');
    typeEl.style.marginBottom = '0';
  }

  function closeHealth(){
    document.querySelectorAll('#view-board [id^="health-detail"]').forEach(function(el){ el.style.display='none'; });
    document.getElementById('board-list').style.display='block';
  }

  /* ===== 右侧：健康度分析 ===== */

      // 健康度分布下拉（不健康'健康/亚健康'套餐明细）
  (function initHealthDropdowns(){
    var rows = document.querySelectorAll('.health-count-row--warn');
    rows.forEach(function(row){
      var dropdownId = row.getAttribute('data-dropdown');
      var menu = dropdownId ? document.getElementById(dropdownId) : null;
      if (!menu) return;
      row.addEventListener('click', function(e){
        e.stopPropagation();
        var isOpen = row.classList.contains('open');
        rows.forEach(function(r){ r.classList.remove('open'); });
        document.querySelectorAll('.health-warn-menu').forEach(function(m){ m.classList.remove('show'); });
        if (!isOpen) {
          row.classList.add('open');
          menu.classList.add('show');
        }
      });
      menu.querySelectorAll('.item').forEach(function(item){
        item.addEventListener('click', function(e){ e.stopPropagation(); });
      });
    });
    document.addEventListener('click', function(){
      rows.forEach(function(r){ r.classList.remove('open'); });
      document.querySelectorAll('.health-warn-menu').forEach(function(m){ m.classList.remove('show'); });
    });
  })();

  // 健康度三段环形饼图（30/40/30，与参考首页同次03D 观感 SVG）
  (function initHealthPieChart(){
    var svg = document.getElementById('healthPieSvg');
    var topGroup = document.getElementById('healthPieTop');
    var bottomGroup = document.getElementById('healthPieBottom');
    if (!svg || !topGroup || !bottomGroup) return;

    var segments = [
      { p: 30, color: '#0ea5ff', bottomColor: '#0b66c3' },
      { p: 40, color: '#ff2a7a', bottomColor: '#c40055' },
      { p: 30, color: '#ff7a00', bottomColor: '#c75a00' }
    ];

    var cx = 110, cy = 55, r = 48, strokeW = 26;

    function pointOnCircle(angleDeg){
      var rad = angleDeg * Math.PI / 180;
      return { x: cx + r * Math.sin(rad), y: cy - r * Math.cos(rad) };
    }
    function arcPath(startDeg, endDeg){
      var start = pointOnCircle(startDeg);
      var end = pointOnCircle(endDeg);
      var sweepFlag = 1;
      var largeArcFlag = (endDeg - startDeg) > 180 ? 1 : 0;
      return 'M ' + start.x + ' ' + start.y + ' A ' + r + ' ' + r + ' 0 ' + largeArcFlag + ' ' + sweepFlag + ' ' + end.x + ' ' + end.y;
    }

    topGroup.innerHTML = '';
    bottomGroup.innerHTML = '';
    var current = 0;
    segments.forEach(function(seg){
      var span = seg.p * 3.6;
      var next = current + span;
      var d = arcPath(current, next);

      var pathTop = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      pathTop.setAttribute('d', d);
      pathTop.setAttribute('stroke', seg.color);
      pathTop.setAttribute('stroke-width', strokeW);
      pathTop.setAttribute('fill', 'none');
      pathTop.setAttribute('stroke-linecap', 'butt');
      topGroup.appendChild(pathTop);

      var pathBottom = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      pathBottom.setAttribute('d', d);
      pathBottom.setAttribute('stroke', seg.bottomColor);
      pathBottom.setAttribute('stroke-width', strokeW);
      pathBottom.setAttribute('fill', 'none');
      pathBottom.setAttribute('stroke-linecap', 'butt');
      bottomGroup.appendChild(pathBottom);

      current = next;
    });
  })();

  /* ===== 单产品画像：运营问题模块图表，359 元画像试点） ===== */
  window.__opsCharts = [];
  (function initOpsCharts(){
    if (typeof echarts === 'undefined') return;
    var months = ['1月','2月','3月','4月','5月','6月','7月','8月','9月'];
    var fontSub = '#5C6B84', fontLt = '#8896AC', gridLine = '#EEF2F7';
    var blue = '#0A5FD6', green = '#00B578', red = '#F5222D', yellow = '#F5A623';

    function mk(id){
      var el = document.getElementById(id);
      if (!el) return null;
      var c = echarts.init(el);
      window.__opsCharts.push(c);
      return c;
    }
    function baseAxis(extra){
      var o = {
        grid:{left:6,right:12,top:24,bottom:4,containLabel:true},
        tooltip:{trigger:'axis',confine:true,axisPointer:{type:'shadow'}},
        xAxis:{type:'category',data:months,axisLine:{lineStyle:{color:gridLine}},axisTick:{show:false},axisLabel:{color:fontSub,fontSize:11}},
        yAxis:{type:'value',splitLine:{lineStyle:{color:gridLine}},axisLabel:{color:fontLt,fontSize:11}},
        color:[blue,yellow,green]
      };
      for (var k in extra) o[k] = extra[k];
      return o;
    }
    function lineSeries(name,data,color,area){
      var s = {name:name,type:'line',data:data,smooth:true,symbolSize:5,lineStyle:{width:2.5,color:color},itemStyle:{color:color}};
      if (area) s.areaStyle = {opacity:.12,color:color};
      return s;
    }

    // ⑀'效益：三标签（收入趋动'/ 利润率'/ 营销费用占比）
    var effectIncomeChart = mk('opsEffectIncome');
    var effectProfitChart = mk('opsEffectProfit');
    var effectCrmChart = mk('opsEffectCrm');

    // 收入趋势：双查'当月收入左轴 + 累计收入右轴) + 同'环比折线(右轴)
    var effectRateState = 'yoy';
    var monthIncome = [2950,3010,2980,3050,3120,3060,3150,3210,3168];
    var cumIncome = [2950,5960,8940,11990,15110,18170,21320,24530,27698];
    var rateData = {
      yoy:  [6.5,7.1,6.8,7.9,8.7,9.0,9.2,9.4,9.6],
      mom:  [null,2.0,-1.0,2.3,2.3,-1.9,2.9,1.9,-1.3]
    };
    function renderEffectIncome(){
      if (!effectIncomeChart) return;
      var rateName = effectRateState === 'yoy' ? '同比(%)' : '环比(%)';
      function barGrad(c1,c2){
        return new echarts.graphic.LinearGradient(0,0,0,1,[
          {offset:0,color:c1},{offset:1,color:c2}
        ]);
      }
      function barGrad(c1,c2){
        return new echarts.graphic.LinearGradient(0,0,0,1,[
          {offset:0,color:c1},{offset:1,color:c2}
        ]);
      }
      effectIncomeChart.setOption({
        grid:{left:6,right:6,top:10,bottom:4,containLabel:true},
        tooltip:{trigger:'axis',confine:true,axisPointer:{type:'cross'}},
        xAxis:{type:'category',data:months,boundaryGap:true,axisLine:{lineStyle:{color:gridLine}},axisTick:{show:false},axisLabel:{color:fontSub,fontSize:11}},
        yAxis:[
          {type:'value',splitLine:{lineStyle:{color:gridLine,type:'dashed'}},axisLabel:{color:fontLt,fontSize:10}},
          {type:'value',name:'%',nameTextStyle:{color:fontLt,fontSize:10},splitLine:{show:false},axisLabel:{color:fontLt,fontSize:10}}
        ],
        series:[
          {name:'当月收入(万元)',type:'bar',data:monthIncome,barWidth:11,barCategoryGap:'20%',
            itemStyle:{color:barGrad('#1d8bff','#9fd8ff'),shadowColor:'rgba(29,139,255,0.35)',shadowBlur:8,shadowOffsetY:3,borderRadius:[2,2,0,0]},z:3},
          {name:'累计收入(万元)',type:'bar',data:cumIncome,barWidth:11,barCategoryGap:'20%',
            itemStyle:{color:barGrad('#0a4eb0','#7ab4f5'),shadowColor:'rgba(10,78,176,0.35)',shadowBlur:8,shadowOffsetY:3,borderRadius:[2,2,0,0]},z:3},
          {name:rateName,type:'line',yAxisIndex:1,data:rateData[effectRateState],smooth:true,symbol:'circle',symbolSize:5,lineStyle:{width:2},itemStyle:{color:yellow,borderColor:'#fff',borderWidth:1.5},z:12}
        ]
      });
    }
    renderEffectIncome();

    // 利润率：折线
    if (effectProfitChart){
      effectProfitChart.setOption(Object.assign(baseAxis(), {
        tooltip:{trigger:'axis',confine:true,axisPointer:{type:'line'}},
        color:[blue],
        series:[lineSeries('利润率(%)',[11.8,12.2,12.0,12.9,13.4,13.1,13.8,14.0,14.2],blue)]
      }));
    }
    // 营销费用占比：折线
    if (effectCrmChart){
      effectCrmChart.setOption(Object.assign(baseAxis(), {
        tooltip:{trigger:'axis',confine:true,axisPointer:{type:'line'}},
        color:[yellow],
        series:[lineSeries('营销费用占比(%)',[18.2,17.8,18.0,17.4,17.0,17.2,16.8,16.6,16.5],yellow)]
      }));
    }

    // 效益三主标签切换
    var effectTabs = document.querySelectorAll('#opsEffectTabs button');
    function setEffectPane(key){
      var map = { income:'opsEffectIncomePane', profit:'opsEffectProfitPane', crm:'opsEffectCrmPane' };
      effectTabs.forEach(function(b){ b.classList.toggle('active', b.getAttribute('data-effect')===key); });
      ['opsEffectIncomePane','opsEffectProfitPane','opsEffectCrmPane'].forEach(function(id){
        document.getElementById(id).classList.toggle('active', id===map[key]);
      });
      setTimeout(function(){
        [effectIncomeChart,effectProfitChart,effectCrmChart].forEach(function(c){ if (c) c.resize(); });
      }, 20);
    }
    effectTabs.forEach(function(btn){
      btn.addEventListener('click', function(){ setEffectPane(this.getAttribute('data-effect')); });
    });
    // 同'环比子切换
    var rateBtns = document.querySelectorAll('#opsEffectRateTabs button');
    rateBtns.forEach(function(btn){
      btn.addEventListener('click', function(){
        rateBtns.forEach(function(b){ b.classList.remove('active'); });
        btn.classList.add('active');
        effectRateState = btn.getAttribute('data-rate');
        renderEffectIncome();
      });
    });

    // ⑀'市场-用户：双查到达用户+月净增 + 同环比折线
    var mktScaleChart = mk('opsMktScale');
    var arriveUsers = [10.8,11.0,11.2,11.4,11.6,11.8,12.0,12.3,12.5];
    var netIncrease = [6.4,6.6,6.2,6.9,7.1,6.8,7.4,7.5,7.9];
    var mktRateData = {
      yoy: [5.8,6.0,6.1,6.3,6.5,6.7,6.9,7.1,7.3],
      mom: [null,1.9,1.8,1.8,1.8,1.7,1.7,2.5,1.6]
    };
    var mktRateState = 'yoy';
    function renderMktScale(){
      if (!mktScaleChart) return;
      var rateName = mktRateState === 'yoy' ? '同比(%)' : '环比(%)';
      mktScaleChart.setOption({
        grid:{left:6,right:6,top:8,bottom:2,containLabel:true},
        tooltip:{trigger:'axis',confine:true,axisPointer:{type:'cross'}},
        xAxis:{type:'category',data:months,boundaryGap:true,axisLine:{lineStyle:{color:gridLine}},axisTick:{show:false},axisLabel:{color:fontSub,fontSize:10}},
        yAxis:[
          {type:'value',splitLine:{lineStyle:{color:gridLine,type:'dashed'}},axisLabel:{color:fontLt,fontSize:10}},
          {type:'value',name:'%',nameTextStyle:{color:fontLt,fontSize:9},splitLine:{show:false},axisLabel:{color:fontLt,fontSize:10}}
        ],
        series:[
          {name:'到达用户(户)',type:'bar',data:arriveUsers,barWidth:8,
            itemStyle:{color:new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'#1d8bff'},{offset:1,color:'#9fd8ff'}]),shadowColor:'rgba(29,139,255,0.35)',shadowBlur:7,shadowOffsetY:3,borderRadius:[2,2,0,0]},z:3},
          {name:'月净增(户)',type:'bar',data:netIncrease,barWidth:8,
            itemStyle:{color:new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:'#0a4eb0'},{offset:1,color:'#7ab4f5'}]),shadowColor:'rgba(10,78,176,0.35)',shadowBlur:7,shadowOffsetY:3,borderRadius:[2,2,0,0]},z:3},
          {name:rateName,type:'line',yAxisIndex:1,data:mktRateData[mktRateState],smooth:true,symbol:'circle',symbolSize:4,lineStyle:{width:2},itemStyle:{color:yellow,borderColor:'#fff',borderWidth:1.5},z:12}
        ]
      });
    }
    renderMktScale();

    // ⑀'市场-用户：活跃用户率 + 付费活跃率'双折线（直接展示）
    var activeTrendChart = mk('opsActiveTrend');
    var activeUserData = [82.1,82.6,83.0,83.9,84.5,85.2,85.8,86.1,86.4];
    var paidActiveData = [89.5,90.0,90.3,91.0,91.4,91.9,92.2,92.5,92.8];
    function renderActiveTrend(){
      if (!activeTrendChart) return;
      activeTrendChart.setOption(Object.assign(baseAxis(), {
        tooltip:{trigger:'axis',confine:true,axisPointer:{type:'line'}},
        color:[green,blue],
        series:[
          lineSeries('活跃用户率(%)',activeUserData,green),
          lineSeries('付费活跃率(%)',paidActiveData,blue)
        ]
      }));
    }
    renderActiveTrend();

    // ⑀'市场-用户：规模'活跃 标签切换
    var userTabMap = { scale:'opsUserScalePane', active:'opsUserActivePane' };
    var userTabs = document.querySelectorAll('#opsUserTabs button');
    function setUserPane(key){
      userTabs.forEach(function(b){ b.classList.toggle('active', b.getAttribute('data-utab')===key); });
      ['opsUserScalePane','opsUserActivePane'].forEach(function(id){
        document.getElementById(id).classList.toggle('active', id===userTabMap[key]);
      });
      setTimeout(function(){
        [mktScaleChart,activeTrendChart].forEach(function(c){ if (c) c.resize(); });
      }, 20);
    }
    userTabs.forEach(function(btn){
      btn.addEventListener('click', function(){ setUserPane(this.getAttribute('data-utab')); });
    });

    // ⑀'市场-渠道划分：饼图（月份可选）
    var channelPieChart = mk('opsChannelPie');
    var channelMonthData = {
      '2025-12': { name:'2025年12月', data:[['线下营业原',41],['中国电信线上渠道',26],['10000热线',19],['安徽电信线上渠道',14]] },
      '2026-01': { name:'2026年1月', data:[['线下营业原',40],['中国电信线上渠道',27],['10000热线',19],['安徽电信线上渠道',14]] },
      '2026-02': { name:'2026年1月', data:[['线下营业原',39],['中国电信线上渠道',28],['10000热线',20],['安徽电信线上渠道',13]] },
      '2026-03': { name:'2026年1月', data:[['线下营业原',40],['中国电信线上渠道',27],['10000热线',19],['安徽电信线上渠道',14]] },
      '2026-04': { name:'2026年1月', data:[['线下营业原',39],['中国电信线上渠道',28],['10000热线',20],['安徽电信线上渠道',13]] },
      '2026-05': { name:'2026年1月', data:[['线下营业原',38],['中国电信线上渠道',29],['10000热线',20],['安徽电信线上渠道',13]] },
      '2026-06': { name:'2026年1月', data:[['线下营业原',38],['中国电信线上渠道',28],['10000热线',21],['安徽电信线上渠道',13]] },
      '2026-07': { name:'2026年1月', data:[['线下营业原',38],['中国电信线上渠道',28],['10000热线',20],['安徽电信线上渠道',14]] },
      '2026-08': { name:'2026年1月', data:[['线下营业原',38],['中国电信线上渠道',28],['10000热线',20],['安徽电信线上渠道',14]] },
      '2026-09': { name:'2026年1月', data:[['线下营业原',38],['中国电信线上渠道',28],['10000热线',20],['安徽电信线上渠道',14]] }
    };
    var channelColors = ['#1d8bff','#0a4eb0','#10b981','#F5A623'];
    function renderChannelPie(monthKey){
      if (!channelPieChart) return;
      var item = channelMonthData[monthKey] || channelMonthData['2026-09'];
      channelPieChart.setOption({
        color:channelColors,
        tooltip:{formatter:'{b}：{c}%',confine:true},
        legend:{bottom:0,itemWidth:9,itemHeight:9,textStyle:{fontSize:10,color:fontSub}},
        series:[{
          type:'pie',radius:['46%','72%'],center:['50%','46%'],avoidLabelOverlap:false,
          label:{show:true,formatter:'{d}%',fontSize:10,color:fontSub},labelLine:{length:8,length2:6},
          data:item.data.map(function(d){ return {name:d[0],value:d[1]}; })
        }]
      }, true);
    }
    renderChannelPie('2026-09');

    // ⑀'市场-用户：同/环比子切换
    var gRateBtns = document.querySelectorAll('#opsGrowthRateTabs button');
    gRateBtns.forEach(function(btn){
      btn.addEventListener('click', function(){
        gRateBtns.forEach(function(b){ b.classList.remove('active'); });
        btn.classList.add('active');
        mktRateState = btn.getAttribute('data-grate');
        renderMktScale();
      });
    });

    // ⑀'市场-渠道：月份选择
    var channelMonthSel = document.getElementById('opsChannelMonth');
    if (channelMonthSel){
      channelMonthSel.addEventListener('change', function(){ renderChannelPie(this.value); });
    }

    // ⑀'运营质量：留存（T+1活跃率'/ T+3 / T+6 退订率）
    var qRetainChart = mk('opsQRetain');
    var qRetainActive = [74.2,74.9,75.5,76.1,76.8,77.3,77.8,78.2,78.4];
    var qRetainT3 = [1.85,1.78,1.70,1.66,1.60,1.55,1.50,1.46,1.40];
    var qRetainT6 = [2.82,2.75,2.68,2.60,2.54,2.48,2.42,2.36,2.30];
    function lastMark(val,color){
      return {type:'max',name:'最新',symbol:'circle',symbolSize:51,symbolRotate:0,
        itemStyle:{color:color,borderColor:'#fff',borderWidth:2,shadowBlur:8,shadowColor:'rgba(0,0,0,0.12)'},
        label:{show:true,fontSize:10,fontWeight:700,color:color,position:'top',formatter:function(p){return val;},offset:[0,-4]}};
    }
    function renderQRetain(){
      if (!qRetainChart) return;
      qRetainChart.setOption({
        grid:{left:6,right:10,top:16,bottom:2,containLabel:true},
        tooltip:{trigger:'axis',confine:true,axisPointer:{type:'line'}},
        color:[green,yellow,blue],
        xAxis:{type:'category',data:months,boundaryGap:false,axisLine:{lineStyle:{color:gridLine}},axisTick:{show:false},axisLabel:{color:fontSub,fontSize:10}},
        yAxis:[
          {type:'value',name:'活跃率(%',nameTextStyle:{color:fontLt,fontSize:9,padding:[0,0,0,26]},splitLine:{lineStyle:{color:gridLine,type:'dashed'}},axisLabel:{color:fontLt,fontSize:10}},
          {type:'value',name:'退订率 %',nameTextStyle:{color:fontLt,fontSize:9,padding:[0,24,0,0]},splitLine:{show:false},axisLabel:{color:fontLt,fontSize:10}}
        ],
        series:[
          {name:'T+1活跃率(%)',type:'line',data:qRetainActive,yAxisIndex:0,smooth:true,symbol:'circle',symbolSize:6,symbolKeepAspect:true,lineStyle:{width:3},itemStyle:{color:green,borderColor:'#fff',borderWidth:1.5,shadowBlur:6,shadowColor:'rgba(0,181,120,0.35)'},markPoint:lastMark(78.4,green),z:12},
          {name:'T+3退订率(%)',type:'line',data:qRetainT3,yAxisIndex:1,smooth:true,symbol:'circle',symbolSize:5,lineStyle:{width:2.5},itemStyle:{color:yellow,borderColor:'#fff',borderWidth:1.5},markPoint:lastMark('1.40',yellow),z:12},
          {name:'T+6退订率(%)',type:'line',data:qRetainT6,yAxisIndex:1,smooth:true,symbol:'circle',symbolSize:5,lineStyle:{width:2.5},itemStyle:{color:blue,borderColor:'#fff',borderWidth:1.5},markPoint:lastMark('2.30',blue),z:12}
        ]
      });
    }
    renderQRetain();

    // ⑀'运营质量：服务（业务办理成功玀'/ 资费投诉率）
    var qServiceChart = mk('opsQService');
    var qServiceOk = [98.4,98.5,98.7,98.8,98.9,99.0,99.0,99.1,99.2];
    var qServiceGrievance = [0.095,0.088,0.081,0.075,0.068,0.062,0.052,0.038,0.02];
    function renderQService(){
      if (!qServiceChart) return;
      qServiceChart.setOption({
        grid:{left:6,right:10,top:16,bottom:2,containLabel:true},
        tooltip:{trigger:'axis',confine:true,axisPointer:{type:'line'}},
        color:[blue,red],
        xAxis:{type:'category',data:months,boundaryGap:false,axisLine:{lineStyle:{color:gridLine}},axisTick:{show:false},axisLabel:{color:fontSub,fontSize:10}},
        yAxis:[
          {type:'value',name:'成功玀(%',nameTextStyle:{color:fontLt,fontSize:9,padding:[0,0,0,26]},splitLine:{lineStyle:{color:gridLine,type:'dashed'}},axisLabel:{color:fontLt,fontSize:10}},
          {type:'value',name:'投诉率(%',nameTextStyle:{color:fontLt,fontSize:9,padding:[0,24,0,0]},splitLine:{show:false},axisLabel:{color:fontLt,fontSize:10}}
        ],
        series:[
          {name:'业务办理成功玀(%)',type:'line',data:qServiceOk,yAxisIndex:0,smooth:true,symbol:'circle',symbolSize:6,lineStyle:{width:3},itemStyle:{color:blue,borderColor:'#fff',borderWidth:1.5,shadowBlur:6,shadowColor:'rgba(10,95,214,0.35)'},markPoint:lastMark('99.2',blue),z:12},
          {name:'资费投诉率(%)',type:'line',data:qServiceGrievance,yAxisIndex:1,smooth:true,symbol:'circle',symbolSize:5,lineStyle:{width:2.5},itemStyle:{color:red,borderColor:'#fff',borderWidth:1.5},markPoint:lastMark('0.02',red),z:12}
        ]
      });
    }
    renderQService();

    // ⑀'运营质量：留存'服务 标签切换
    var qTabMap = { retain:'opsQRetainPane', service:'opsQServicePane' };
    var qTabs = document.querySelectorAll('#opsQualityTabs button');
    function setQPane(key){
      qTabs.forEach(function(b){ b.classList.toggle('active', b.getAttribute('data-qt')===key); });
      ['opsQRetainPane','opsQServicePane'].forEach(function(id){
        document.getElementById(id).classList.toggle('active', id===qTabMap[key]);
      });
      setTimeout(function(){
        [qRetainChart,qServiceChart].forEach(function(c){ if (c) c.resize(); });
      }, 20);
    }
    qTabs.forEach(function(btn){
      btn.addEventListener('click', function(){ setQPane(this.getAttribute('data-qt')); });
    });

    /* ===== 299 元画像：图表（亚健康画像，独立数据） ===== */
    (function(){
      function barGrad(c1,c2){ return new echarts.graphic.LinearGradient(0,0,0,1,[{offset:0,color:c1},{offset:1,color:c2}]); }
      // 效益
      var ecIn = mk('opsEffectIncome299'), ecProfit = mk('opsEffectProfit299'), ecCrm = mk('opsEffectCrm299');
      var mIn299 = [1395,1380,1368,1352,1340,1326,1300,1282,1246];
      var cum299 = [1395,2775,4143,5495,6835,8161,9461,10743,11989];
      var rd299 = { yoy:[5.2,4.9,4.6,4.3,4.0,3.6,3.2,2.7,2.1], mom:[null,-1.1,-0.9,-1.2,-0.9,-1.0,-2.0,-1.4,-2.8] };
      var rs299 = 'yoy';
      function renderIn299(){
        if (!ecIn) return;
        var rn = rs299==='yoy' ? '同比(%)' : '环比(%)';
        ecIn.setOption({
          grid:{left:6,right:6,top:10,bottom:4,containLabel:true},
          tooltip:{trigger:'axis',confine:true,axisPointer:{type:'cross'}},
          xAxis:{type:'category',data:months,boundaryGap:true,axisLine:{lineStyle:{color:gridLine}},axisTick:{show:false},axisLabel:{color:fontSub,fontSize:11}},
          yAxis:[
            {type:'value',splitLine:{lineStyle:{color:gridLine,type:'dashed'}},axisLabel:{color:fontLt,fontSize:10}},
            {type:'value',name:'%',nameTextStyle:{color:fontLt,fontSize:10},splitLine:{show:false},axisLabel:{color:fontLt,fontSize:10}}
          ],
          series:[
            {name:'当月收入(万元)',type:'bar',data:mIn299,barWidth:11,barCategoryGap:'20%',itemStyle:{color:barGrad('#1d8bff','#9fd8ff'),shadowColor:'rgba(29,139,255,0.35)',shadowBlur:8,shadowOffsetY:3,borderRadius:[2,2,0,0]},z:3},
            {name:'累计收入(万元)',type:'bar',data:cum299,barWidth:11,barCategoryGap:'20%',itemStyle:{color:barGrad('#0a4eb0','#7ab4f5'),shadowColor:'rgba(10,78,176,0.35)',shadowBlur:8,shadowOffsetY:3,borderRadius:[2,2,0,0]},z:3},
            {name:rn,type:'line',yAxisIndex:1,data:rd299[rs299],smooth:true,symbol:'circle',symbolSize:5,lineStyle:{width:2},itemStyle:{color:yellow,borderColor:'#fff',borderWidth:1.5},z:12}
          ]
        });
      }
      renderIn299();
      if (ecProfit) ecProfit.setOption(Object.assign(baseAxis(),{tooltip:{trigger:'axis',confine:true,axisPointer:{type:'line'}},color:[blue],series:[lineSeries('利润率(%)',[12.8,12.6,12.5,12.4,12.2,12.1,11.9,11.8,11.6],blue)]}));
      if (ecCrm) ecCrm.setOption(Object.assign(baseAxis(),{tooltip:{trigger:'axis',confine:true,axisPointer:{type:'line'}},color:[yellow],series:[lineSeries('营销费用占比(%)',[17.2,17.5,17.8,17.9,18.1,18.3,18.5,18.7,18.9],yellow)]}));
      var et299 = document.querySelectorAll('#opsEffectTabs299 button');
      function setEff299(key){
        var m = { income:'opsEffectIncomePane299', profit:'opsEffectProfitPane299', crm:'opsEffectCrmPane299' };
        et299.forEach(function(b){ b.classList.toggle('active', b.getAttribute('data-effect')===key); });
        ['opsEffectIncomePane299','opsEffectProfitPane299','opsEffectCrmPane299'].forEach(function(id){ document.getElementById(id).classList.toggle('active', id===m[key]); });
        setTimeout(function(){ [ecIn,ecProfit,ecCrm].forEach(function(c){ if (c) c.resize(); }); }, 20);
      }
      et299.forEach(function(btn){ btn.addEventListener('click', function(){ setEff299(this.getAttribute('data-effect')); }); });
      var rt299 = document.querySelectorAll('#opsEffectRateTabs299 button');
      rt299.forEach(function(btn){
        btn.addEventListener('click', function(){
          rt299.forEach(function(b){ b.classList.remove('active'); });
          btn.classList.add('active');
          rs299 = btn.getAttribute('data-rate');
          renderIn299();
        });
      });

      // 市场
      var mktScale299 = mk('opsMktScale299');
      var arrive299=[3.4,3.5,3.6,3.7,3.8,3.9,4.0,4.1,4.2];
      var net299=[0.22,0.20,0.19,0.18,0.17,0.16,0.15,0.14,0.12];
      var mktR299={yoy:[3.8,3.5,3.3,3.1,2.9,2.7,2.5,2.3,2.1],mom:[null,2.9,2.9,2.8,2.7,2.6,2.6,2.5,2.4]};
      var mktRs299='yoy';
      function renderMkt299(){
        if (!mktScale299) return;
        var rn = mktRs299==='yoy' ? '同比(%)' : '环比(%)';
        mktScale299.setOption({
          grid:{left:6,right:6,top:8,bottom:2,containLabel:true},
          tooltip:{trigger:'axis',confine:true,axisPointer:{type:'cross'}},
          xAxis:{type:'category',data:months,boundaryGap:true,axisLine:{lineStyle:{color:gridLine}},axisTick:{show:false},axisLabel:{color:fontSub,fontSize:10}},
          yAxis:[
            {type:'value',splitLine:{lineStyle:{color:gridLine,type:'dashed'}},axisLabel:{color:fontLt,fontSize:10}},
            {type:'value',name:'%',nameTextStyle:{color:fontLt,fontSize:9},splitLine:{show:false},axisLabel:{color:fontLt,fontSize:10}}
          ],
          series:[
            {name:'到达用户(户)',type:'bar',data:arrive299,barWidth:8,itemStyle:{color:barGrad('#1d8bff','#9fd8ff'),shadowColor:'rgba(29,139,255,0.35)',shadowBlur:7,shadowOffsetY:3,borderRadius:[2,2,0,0]},z:3},
            {name:'月净增(户)',type:'bar',data:net299,barWidth:8,itemStyle:{color:barGrad('#0a4eb0','#7ab4f5'),shadowColor:'rgba(10,78,176,0.35)',shadowBlur:7,shadowOffsetY:3,borderRadius:[2,2,0,0]},z:3},
            {name:rn,type:'line',yAxisIndex:1,data:mktR299[mktRs299],smooth:true,symbol:'circle',symbolSize:4,lineStyle:{width:2},itemStyle:{color:yellow,borderColor:'#fff',borderWidth:1.5},z:12}
          ]
        });
      }
      renderMkt299();
      var act299 = mk('opsActiveTrend299');
      if (act299) act299.setOption(Object.assign(baseAxis(),{tooltip:{trigger:'axis',confine:true,axisPointer:{type:'line'}},color:[green,blue],series:[lineSeries('活跃用户率(%)',[78.2,78.5,78.9,79.2,79.5,79.8,80.1,80.3,80.5],green),lineSeries('付费活跃率(%)',[88.0,88.2,88.5,88.8,89.0,89.2,89.4,89.5,89.6],blue)]}));
      var ut299 = document.querySelectorAll('#opsUserTabs299 button');
      function setUser299(key){
        var m = { scale:'opsUserScalePane299', active:'opsUserActivePane299' };
        ut299.forEach(function(b){ b.classList.toggle('active', b.getAttribute('data-utab')===key); });
        ['opsUserScalePane299','opsUserActivePane299'].forEach(function(id){ document.getElementById(id).classList.toggle('active', id===m[key]); });
        setTimeout(function(){ [mktScale299,act299].forEach(function(c){ if (c) c.resize(); }); }, 20);
      }
      ut299.forEach(function(btn){ btn.addEventListener('click', function(){ setUser299(this.getAttribute('data-utab')); }); });
      var gr299 = document.querySelectorAll('#opsGrowthRateTabs299 button');
      gr299.forEach(function(btn){
        btn.addEventListener('click', function(){
          gr299.forEach(function(b){ b.classList.remove('active'); });
          btn.classList.add('active');
          mktRs299 = btn.getAttribute('data-grate');
          renderMkt299();
        });
      });
      // 渠道
      var chPie299 = mk('opsChannelPie299');
      var chD299 = {
        '2025-12':[['线下营业原',44],['中国电信线上渠道',26],['10000热线',17],['安徽电信线上渠道',13]],
        '2026-01':[['线下营业原',43],['中国电信线上渠道',27],['10000热线',17],['安徽电信线上渠道',13]],
        '2026-02':[['线下营业原',43],['中国电信线上渠道',27],['10000热线',17],['安徽电信线上渠道',13]],
        '2026-03':[['线下营业原',44],['中国电信线上渠道',26],['10000热线',17],['安徽电信线上渠道',13]],
        '2026-04':[['线下营业原',43],['中国电信线上渠道',27],['10000热线',17],['安徽电信线上渠道',13]],
        '2026-05':[['线下营业原',42],['中国电信线上渠道',28],['10000热线',17],['安徽电信线上渠道',13]],
        '2026-06':[['线下营业原',42],['中国电信线上渠道',28],['10000热线',17],['安徽电信线上渠道',13]],
        '2026-07':[['线下营业原',43],['中国电信线上渠道',27],['10000热线',17],['安徽电信线上渠道',13]],
        '2026-08':[['线下营业原',43],['中国电信线上渠道',27],['10000热线',17],['安徽电信线上渠道',13]],
        '2026-09':[['线下营业原',43],['中国电信线上渠道',27],['10000热线',17],['安徽电信线上渠道',13]]
      };
      var chC299=['#1d8bff','#0a4eb0','#10b981','#F5A623'];
      function renderCh299(k){
        if (!chPie299) return;
        var d = chD299[k] || chD299['2026-09'];
        chPie299.setOption({ color:chC299, tooltip:{formatter:'{b}：{c}%',confine:true}, legend:{bottom:0,itemWidth:9,itemHeight:9,textStyle:{fontSize:10,color:fontSub}},
          series:[{type:'pie',radius:['46%','72%'],center:['50%','46%'],avoidLabelOverlap:false,label:{show:true,formatter:'{d}%',fontSize:10,color:fontSub},labelLine:{length:8,length2:6},data:d.map(function(x){ return {name:x[0],value:x[1]}; })}] }, true);
      }
      renderCh299('2026-09');
      var chSel299 = document.getElementById('opsChannelMonth299');
      if (chSel299) chSel299.addEventListener('change', function(){ renderCh299(this.value); });

      // 运营质量
      var qR299 = mk('opsQRetain299');
      var qRA299=[72.0,72.6,73.1,73.7,74.2,74.9,75.4,75.8,76.1], qRT3_299=[2.40,2.34,2.30,2.25,2.21,2.18,2.15,2.13,2.10], qRT6_299=[3.50,3.44,3.40,3.35,3.32,3.29,3.26,3.23,3.20];
      function renderQRetain299(){
        if (!qR299) return;
        qR299.setOption({
          grid:{left:6,right:10,top:16,bottom:2,containLabel:true},
          tooltip:{trigger:'axis',confine:true,axisPointer:{type:'line'}},
          color:[green,yellow,blue],
          xAxis:{type:'category',data:months,boundaryGap:false,axisLine:{lineStyle:{color:gridLine}},axisTick:{show:false},axisLabel:{color:fontSub,fontSize:10}},
          yAxis:[
            {type:'value',name:'活跃率(%',nameTextStyle:{color:fontLt,fontSize:9,padding:[0,0,0,26]},splitLine:{lineStyle:{color:gridLine,type:'dashed'}},axisLabel:{color:fontLt,fontSize:10}},
            {type:'value',name:'退订率 %',nameTextStyle:{color:fontLt,fontSize:9,padding:[0,24,0,0]},splitLine:{show:false},axisLabel:{color:fontLt,fontSize:10}}
          ],
          series:[
            {name:'T+1活跃率(%)',type:'line',data:qRA299,yAxisIndex:0,smooth:true,symbol:'circle',symbolSize:6,lineStyle:{width:3},itemStyle:{color:green,borderColor:'#fff',borderWidth:1.5,shadowBlur:6,shadowColor:'rgba(0,181,120,0.35)'},markPoint:lastMark('76.1',green),z:12},
            {name:'T+3退订率(%)',type:'line',data:qRT3_299,yAxisIndex:1,smooth:true,symbol:'circle',symbolSize:5,lineStyle:{width:2.5},itemStyle:{color:yellow,borderColor:'#fff',borderWidth:1.5},markPoint:lastMark('2.10',yellow),z:12},
            {name:'T+6退订率(%)',type:'line',data:qRT6_299,yAxisIndex:1,smooth:true,symbol:'circle',symbolSize:5,lineStyle:{width:2.5},itemStyle:{color:blue,borderColor:'#fff',borderWidth:1.5},markPoint:lastMark('3.20',blue),z:12}
          ]
        });
      }
      renderQRetain299();
      var qS299 = mk('opsQService299');
      var qOk299=[97.8,97.9,98.0,98.1,98.2,98.2,98.3,98.3,98.4], qG299=[0.030,0.032,0.033,0.035,0.036,0.037,0.039,0.039,0.04];
      function renderQService299(){
        if (!qS299) return;
        qS299.setOption({
          grid:{left:6,right:10,top:16,bottom:2,containLabel:true},
          tooltip:{trigger:'axis',confine:true,axisPointer:{type:'line'}},
          color:[blue,red],
          xAxis:{type:'category',data:months,boundaryGap:false,axisLine:{lineStyle:{color:gridLine}},axisTick:{show:false},axisLabel:{color:fontSub,fontSize:10}},
          yAxis:[
            {type:'value',name:'成功玀(%',nameTextStyle:{color:fontLt,fontSize:9,padding:[0,0,0,26]},splitLine:{lineStyle:{color:gridLine,type:'dashed'}},axisLabel:{color:fontLt,fontSize:10}},
            {type:'value',name:'投诉率(%',nameTextStyle:{color:fontLt,fontSize:9,padding:[0,24,0,0]},splitLine:{show:false},axisLabel:{color:fontLt,fontSize:10}}
          ],
          series:[
            {name:'业务办理成功玀(%)',type:'line',data:qOk299,yAxisIndex:0,smooth:true,symbol:'circle',symbolSize:6,lineStyle:{width:3},itemStyle:{color:blue,borderColor:'#fff',borderWidth:1.5,shadowBlur:6,shadowColor:'rgba(10,95,214,0.35)'},markPoint:lastMark('98.4',blue),z:12},
            {name:'资费投诉率(%)',type:'line',data:qG299,yAxisIndex:1,smooth:true,symbol:'circle',symbolSize:5,lineStyle:{width:2.5},itemStyle:{color:red,borderColor:'#fff',borderWidth:1.5},markPoint:lastMark('0.04',red),z:12}
          ]
        });
      }
      renderQService299();
      var qt299 = document.querySelectorAll('#opsQualityTabs299 button');
      function setQ299(key){
        var m = { retain:'opsQRetainPane299', service:'opsQServicePane299' };
        qt299.forEach(function(b){ b.classList.toggle('active', b.getAttribute('data-qt')===key); });
        ['opsQRetainPane299','opsQServicePane299'].forEach(function(id){ document.getElementById(id).classList.toggle('active', id===m[key]); });
        setTimeout(function(){ [qR299,qS299].forEach(function(c){ if (c) c.resize(); }); }, 20);
      }
      qt299.forEach(function(btn){ btn.addEventListener('click', function(){ setQ299(this.getAttribute('data-qt')); }); });
    })();

    // 视口变化重绘
    window.addEventListener('resize', function(){
      window.__opsCharts.forEach(function(c){ if (c) c.resize(); });
    });
  })();

  /* ===== 第二排：单套餐收入下钻（三系列'tab 切换，'===== */
  (function initSeriesRevenueTabs(){
    var srContent = document.getElementById('srContent');
    var tabs = document.querySelectorAll('.sr-tab');
    var srExportState = { tabKey: 'wga', tabName: '5G-A系列收入', cardTitle: '', rightMode: 'year', leftTitle: '', rightTitle: '', leftCfg: null, rightCfg: null };

    var monthLabels = ['1月','2月','3月','4月','5月','6月','7月','8月','9月'];

    // 渠道结构 & 地市分布（按套餐下钻，每套餐 4 项渠道'+ 5 项地市）
    function makeChannelData(base, drift){
      return [
        { name: '线下营业原',     pct: +(base[0] + drift).toFixed(2) },
        { name: '10000热线',      pct: +(base[1] - drift * 0.6).toFixed(2) },
        { name: '中国电信线上渠道', pct: +(base[2] - drift * 0.2).toFixed(2) },
        { name: '安徽电信线上渠道', pct: +(base[3] - drift * 0.2).toFixed(2) }
      ];
    }
    function makeCityData(base, drift){
      return [
        { name: '合肥', pct: +(base[0] + drift).toFixed(2) },
        { name: '芜湖', pct: +(base[1] - drift * 0.3).toFixed(2) },
        { name: '蚌埠', pct: +(base[2] - drift * 0.2).toFixed(2) },
        { name: '阜阳', pct: +(base[3] - drift * 0.3).toFixed(2) },
        { name: '安庆', pct: +(base[4] - drift * 0.2).toFixed(2) }
      ];
    }

    var seriesTabs = {
      wga: {
        cards: [
          { title: '5G-A套餐159元', value: '680.00', unit: '万元', yoyText: '月同比8.6% →' },
          { title: '5G-A套餐单品199元', value: '598.40', unit: '万元', yoyText: '月同比6.9% →' },
          { title: '5G-A套餐单品239元', value: '561.20', unit: '万元', yoyText: '月同比-4.5% →' },
          { title: '5G-A套餐单品299元', value: '524.00', unit: '万元', yoyText: '月同比5.2% →' }
        ],
        // 左图：单柱状图（横坐标'= 月份，月收入 + 月同比）
        leftLegend: ['月收入', '月同比'],
        leftChart: { xLabels: monthLabels, barsA: [680,720,650,710,630,700,680,690,620], line: [70,68,72,66,74,69,71,67,73], leftMax: 900, rightMax: 90, seriesNames: { barA: '月收入', line1: '月同比' } },
        // 右图：单柱状图（横坐标'= 月份，到达用户'+ 月同比）
        rightChart: { xLabels: monthLabels, bars: [4350,4380,4380,4560,4680,4590,4590,4740,4740], line: [75,74,72,76,78,73,71,74,72], leftMax: 6000, rightMax: 90, lineColor: '#f59e0b' },
        // 渠道结构 & 地市分布（按套餐，与 cards 索引一一对应）
        channelData: [
          makeChannelData([59.40, 30.57, 6.12, 3.91], 0),
          makeChannelData([57.80, 31.60, 6.30, 4.30], 0.4),
          makeChannelData([61.20, 29.40, 5.90, 3.50], -0.3),
          makeChannelData([56.90, 32.10, 6.60, 4.40], 0.2)
        ],
        cityData: [
          makeCityData([31.2, 14.6, 11.3, 9.8, 8.4], 0),
          makeCityData([29.5, 15.8, 12.1, 10.2, 8.9], 0.3),
          makeCityData([33.0, 13.9, 10.8, 9.5, 8.1], -0.2),
          makeCityData([28.7, 16.2, 12.6, 10.5, 9.0], 0.1)
        ]
      },
      quanyi: {
        cards: [
          { title: '权益随心通9元', value: '620.00', unit: '万元', yoyText: '月同比5.4% →' },
          { title: '权益随心通9元', value: '576.60', unit: '万元', yoyText: '月同比-3.8% →' },
          { title: '权益随心通9元', value: '538.80', unit: '万元', yoyText: '月同比7.1% →' }
        ],
        leftLegend: ['月收入', '月同比'],
        leftChart: { xLabels: monthLabels, barsA: [620,680,610,670,640,690,610,650,600], line: [72,70,74,68,76,71,73,69,75], leftMax: 900, rightMax: 90, seriesNames: { barA: '月收入', line1: '月同比' } },
        rightChart: { xLabels: monthLabels, bars: [4440,4440,4440,4650,4740,4650,4650,4800,4800], line: [78,76,74,79,80,75,73,76,74], leftMax: 6000, rightMax: 90, lineColor: '#f59e0b' },
        // 渠道结构 & 地市分布（按套餐，与 cards 索引一一对应）
        channelData: [
          makeChannelData([54.30, 33.80, 7.20, 4.70], 0),
          makeChannelData([56.10, 32.50, 6.80, 4.60], 0.3),
          makeChannelData([52.80, 34.60, 7.50, 5.10], -0.2)
        ],
        cityData: [
          makeCityData([27.6, 17.1, 13.4, 11.0, 9.6], 0),
          makeCityData([26.2, 18.0, 14.0, 11.6, 10.1], 0.2),
          makeCityData([29.1, 16.3, 12.7, 10.4, 9.2], -0.1)
        ]
      },
      ronghe: {
        cards: [
          { title: '5G-A融合套餐199元', value: '590.00', unit: '万元', yoyText: '月同比8.8% →' },
          { title: '5G-A融合套餐239元', value: '548.70', unit: '万元', yoyText: '月同比6.2% →' },
          { title: '5G-A融合套餐299元', value: '512.30', unit: '万元', yoyText: '月同比-4.2% →' }
        ],
        leftLegend: ['月收入', '月同比'],
        leftChart: { xLabels: monthLabels, barsA: [590,630,600,650,620,670,610,640,600], line: [70,68,72,66,74,69,71,67,73], leftMax: 900, rightMax: 90, seriesNames: { barA: '月收入', line1: '月同比' } },
        rightChart: { xLabels: monthLabels, bars: [4350,4350,4350,4590,4710,4560,4560,4710,4710], line: [75,73,71,77,79,72,70,73,71], leftMax: 6000, rightMax: 90, lineColor: '#f59e0b' },
        // 渠道结构 & 地市分布（按套餐，与 cards 索引一一对应）
        channelData: [
          makeChannelData([62.50, 28.30, 5.60, 3.60], 0),
          makeChannelData([60.80, 29.60, 5.90, 3.70], 0.3),
          makeChannelData([64.10, 27.20, 5.20, 3.50], -0.2)
        ],
        cityData: [
          makeCityData([34.8, 13.5, 10.6, 9.2, 7.8], 0),
          makeCityData([32.9, 14.8, 11.4, 9.8, 8.3], 0.2),
          makeCityData([36.2, 12.6, 10.0, 8.7, 7.4], -0.1)
        ]
      }
    };

    function csvEscape(value){
      var str = value == null ? '' : String(value);
      if (/[",\n]/.test(str)) return '"' + str.replace(/"/g, '""') + '"';
      return str;
    }
    function triggerCsvDownload(filename, rows){
      var csv = rows.map(function(row){ return row.map(csvEscape).join(','); }).join('\n');
      var blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' });
      var url = URL.createObjectURL(blob);
      var a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    }

    function formatYoyHtml(yoyText){
      var raw = String(yoyText || '月同比10.1% →');
      var numberMatch = raw.match(/-?\d+(\.\d+)?%?/);
      var numText = numberMatch ? numberMatch[0].replace('%', '') : '10.1';
      var isNegative = raw.indexOf('-') >= 0;
      var color = isNegative ? '#16a34a' : '#dc2626';
      var arrow = isNegative ? '▼' : '▲';
      var showValue = (isNegative ? '-' : '') + numText + '%';
      return ''
        + '<span style="font-size:11.5px;color:#111827;">月同比</span>'
        + '<span style="font-size:11.5px;color:' + color + ';font-weight:600;display:inline-flex;align-items:center;gap:3px;">'
        + showValue + ' <i class="caret">' + arrow + '</i>'
        + '</span>';
    }

    // 左图：单柱状图（月收入'+ 月同比折线），横坐标 = 月份
    function buildLeftOption(cfg){
      return {
        grid: { left: 36, right: 36, top: 20, bottom: 26 },
        graphic: [
          { type: 'text', left: 4, top: 0, style: { text: '单位：万元', fill: '#9ca3af', fontSize: 11 } },
          { type: 'text', right: 4, top: 0, style: { text: '单位，', fill: '#9ca3af', fontSize: 11 } }
        ],
        tooltip: { trigger: 'axis' },
        xAxis: {
          type: 'category',
          data: cfg.xLabels,
          axisLine: { lineStyle: { color: '#d1d5db' } },
          axisLabel: { color: '#9ca3af', fontSize: 11 },
          axisTick: { show: false }
        },
        yAxis: [
          {
            type: 'value', max: cfg.leftMax,
            splitLine: { lineStyle: { color: '#e5e7eb', type: 'dashed' } },
            axisLabel: { color: '#9ca3af', fontSize: 10 },
            axisLine: { show: false }, axisTick: { show: false }
          },
          {
            type: 'value', max: cfg.rightMax,
            splitLine: { show: false },
            axisLabel: { color: '#9ca3af', fontSize: 10 },
            axisLine: { show: false }, axisTick: { show: false }
          }
        ],
        series: [
          {
            name: (cfg.seriesNames && cfg.seriesNames.barA) || '月收入',
            type: 'bar', barWidth: 10, data: cfg.barsA,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#3b82f6' }, { offset: 1, color: '#b9dcff' }
              ]),
              shadowColor: 'rgba(59, 130, 246, 0.35)', shadowBlur: 6, shadowOffsetY: 3,
              borderRadius: [2, 2, 0, 0]
            }
          },
          {
            name: '本年顶盖', type: 'pictorialBar', data: cfg.barsA,
            symbol: 'diamond', symbolSize: [13, 6], symbolPosition: 'end', symbolOffset: [0, -2], z: 12,
            tooltip: { show: false },
            itemStyle: { color: '#6fb2ff', opacity: 1 }
          },
          {
            name: (cfg.seriesNames && cfg.seriesNames.line1) || '月同比',
            type: 'line', yAxisIndex: 1, data: cfg.line,
            smooth: true, symbol: 'circle', symbolSize: 4,
            lineStyle: { color: '#f59e0b', width: 2 },
            itemStyle: { color: '#fff', borderColor: '#f59e0b', borderWidth: 1.5 }
          }
        ]
      };
    }

    // 右图：单柱状图（当前选中套餐各月到达用户 + 月同比折线）
    function buildRightOption(cfg){
      return {
        grid: { left: 44, right: 36, top: 20, bottom: 26 },
        graphic: [
          { type: 'text', left: 4, top: 0, style: { text: '单位：户', fill: '#9ca3af', fontSize: 11 } },
          { type: 'text', right: 4, top: 0, style: { text: '单位，', fill: '#9ca3af', fontSize: 11 } }
        ],
        tooltip: { trigger: 'axis' },
        xAxis: {
          type: 'category',
          data: cfg.xLabels,
          axisLine: { lineStyle: { color: '#d1d5db' } },
          axisLabel: { color: '#9ca3af', fontSize: 11 },
          axisTick: { show: false }
        },
        yAxis: [
          {
            type: 'value', max: cfg.leftMax,
            splitLine: { lineStyle: { color: '#e5e7eb', type: 'dashed' } },
            axisLabel: { color: '#9ca3af', fontSize: 10 },
            axisLine: { show: false }, axisTick: { show: false }
          },
          {
            type: 'value', max: cfg.rightMax,
            splitLine: { show: false },
            axisLabel: { color: '#9ca3af', fontSize: 10 },
            axisLine: { show: false }, axisTick: { show: false }
          }
        ],
        series: [
          {
            name: '到达用户', type: 'bar', barWidth: 10, data: cfg.bars,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#1d8bff' }, { offset: 1, color: '#9fd8ff' }
              ]),
              shadowColor: 'rgba(29, 139, 255, 0.35)', shadowBlur: 8, shadowOffsetY: 3,
              borderRadius: [2, 2, 0, 0]
            }
          },
          {
            name: '到达用户顶盖', type: 'pictorialBar', data: cfg.bars,
            symbol: 'diamond', symbolSize: [13, 6], symbolPosition: 'end', symbolOffset: [0, -2], z: 12,
            tooltip: { show: false },
            itemStyle: { color: '#74beff', opacity: 1 }
          },
          {
            name: '月同比', type: 'line', yAxisIndex: 1, data: cfg.line,
            smooth: true, symbol: 'circle', symbolSize: 5,
            lineStyle: { color: cfg.lineColor, width: 2 },
            itemStyle: { color: '#fff', borderColor: cfg.lineColor, borderWidth: 1.5 }
          }
        ]
      };
    }

    function cardAdjusted(chartCfg, idx, scaleSmall){
      var factor = 1 + (idx * (scaleSmall ? 0.05 : 0.08));
      var bias = idx * (scaleSmall ? 2 : 4);
      var out = {
        xLabels: chartCfg.xLabels.slice(),
        leftMax: chartCfg.leftMax, rightMax: chartCfg.rightMax,
        lineColor: chartCfg.lineColor, seriesNames: chartCfg.seriesNames
      };
      if (chartCfg.bars) out.bars = chartCfg.bars.map(function(v, i){ return Math.round(v * factor + (i % 3 - 1) * bias); });
      if (chartCfg.barsA) out.barsA = chartCfg.barsA.map(function(v, i){ return Math.round(v * factor + (i % 4 - 1.5) * bias); });
      if (chartCfg.line) out.line = chartCfg.line.map(function(v, i){ return Math.max(1, Math.round(v + idx * 2 + (i % 4 - 2))); });
      return out;
    }

    var tabNameMap = { wga: '5G-A系列收入', quanyi: '权益随心选系列收入', ronghe: '融合套餐系列收入' };
    var activeCardIndex = 0;
    var leftMode = 'month';
    var leftChart = null, rightChart = null;
    var leftChartEl = null, rightChartEl = null;
    var leftTitleEl = null, rightTitleEl = null;
    var cardsEl = [];

    function getRightTitleByCard(){
      return '用户分析-' + (seriesTabs[srExportState.tabKey].cards[activeCardIndex] ? seriesTabs[srExportState.tabKey].cards[activeCardIndex].title : '');
    }

    function renderRegionRows(){
      var data = seriesTabs[srExportState.tabKey];
      var cardTitle = data.cards[activeCardIndex] ? data.cards[activeCardIndex].title : '';
      var chTitleEl = document.getElementById('srChannelTitle');
      var chRowsEl = document.getElementById('srChannelRows');
      var cityTitleEl = document.getElementById('srCityTitle');
      var cityRowsEl = document.getElementById('srCityRows');
      var chList = (data.channelData && data.channelData[activeCardIndex]) || [];
      var cityList = (data.cityData && data.cityData[activeCardIndex]) || [];

      if (chTitleEl) chTitleEl.textContent = '渠道结构-' + cardTitle;
      if (cityTitleEl) cityTitleEl.textContent = '地市分布（Top 5，' + cardTitle;

      if (chRowsEl) {
        chRowsEl.innerHTML = chList.map(function(item){
          return ''
            + '<div>'
            + '  <div class="region-row-top"><span>' + item.name + '</span><b>' + item.pct.toFixed(2) + '%</b></div>'
            + '  <div class="region-bar"><div style="width:' + item.pct + '%;background:linear-gradient(90deg,#0A5FD6,#4A9EFF);"></div></div>'
            + '</div>';
        }).join('');
      }
      if (cityRowsEl) {
        cityRowsEl.innerHTML = cityList.map(function(item){
          return ''
            + '<div>'
            + '  <div class="region-row-top"><span>' + item.name + '</span><b>' + item.pct.toFixed(2) + '%</b></div>'
            + '  <div class="region-bar"><div style="width:' + item.pct + '%;background:linear-gradient(90deg,#00B578,#4BD9A5);"></div></div>'
            + '</div>';
        }).join('');
      }
    }

    function renderChartsByActiveCard(){
      var data = seriesTabs[srExportState.tabKey];
      var leftCfgBase = cardAdjusted(data.leftChart, activeCardIndex, false);
      var leftCfg = leftMode === 'month'
        ? {
            xLabels: leftCfgBase.xLabels,
            barsA: leftCfgBase.barsA.map(function(v){ return Math.round(v * 0.72); }),
            line: leftCfgBase.line.map(function(v){ return Math.max(1, Math.round(v * 0.82)); }),
            leftMax: leftCfgBase.leftMax, rightMax: leftCfgBase.rightMax,
            seriesNames: leftCfgBase.seriesNames
          }
        : leftCfgBase;
      var rightCfg = cardAdjusted(data.rightChart, activeCardIndex, true);

      if (leftChart) leftChart.setOption(buildLeftOption(leftCfg), true);
      if (rightChart) rightChart.setOption(buildRightOption(rightCfg), true);
      if (leftTitleEl) leftTitleEl.textContent = '收入分析-' + (data.cards[activeCardIndex] ? data.cards[activeCardIndex].title : '');
      if (rightTitleEl) rightTitleEl.textContent = getRightTitleByCard();
      renderRegionRows();

      Array.prototype.forEach.call(cardsEl, function(el){
        var idx = Number(el.getAttribute('data-card-idx'));
        if (idx === activeCardIndex) {
          el.classList.add('active');
        } else {
          el.classList.remove('active');
        }
      });

      srExportState.cardTitle = (data.cards[activeCardIndex] ? data.cards[activeCardIndex].title : '');
      srExportState.rightMode = leftMode;
      srExportState.leftTitle = leftTitleEl ? leftTitleEl.textContent : '';
      srExportState.rightTitle = rightTitleEl ? rightTitleEl.textContent : '';
      srExportState.leftCfg = {
        xLabels: leftCfg.xLabels ? leftCfg.xLabels.slice() : [],
        barsA: leftCfg.barsA ? leftCfg.barsA.slice() : [],
        line: leftCfg.line ? leftCfg.line.slice() : [],
        seriesNames: leftCfg.seriesNames || {}
      };
      srExportState.rightCfg = {
        xLabels: rightCfg.xLabels ? rightCfg.xLabels.slice() : [],
        bars: rightCfg.bars ? rightCfg.bars.slice() : [],
        line: rightCfg.line ? rightCfg.line.slice() : []
      };
    }

    function setMode(mode){
      leftMode = mode;
      renderChartsByActiveCard();
      var pairs = [
        ['leftModeYearBtn', 'leftModeMonthBtn'],
        ['rightModeYearBtn', 'rightModeMonthBtn']
      ];
      pairs.forEach(function(p){
        var yearBtn = document.getElementById(p[0]);
        var monthBtn = document.getElementById(p[1]);
        if (yearBtn && monthBtn) {
          if (mode === 'month') {
            monthBtn.classList.add('active'); yearBtn.classList.remove('active');
          } else {
            yearBtn.classList.add('active'); monthBtn.classList.remove('active');
          }
        }
      });
    }

    function renderTab(key){
      if (!srContent) return;
      var data = seriesTabs[key] || seriesTabs.wga;
      activeCardIndex = 0;
      srExportState.tabKey = key;
      srExportState.tabName = tabNameMap[key] || '系列收入';

      var n = data.cards.length;
      var html = '';
      html += '<div class="sr-cards" style="grid-template-columns:repeat(' + n + ',1fr);">';
      data.cards.forEach(function(card, idx){
        var active = idx === 0;
        var yoyText = card.yoyText || '';
        html += '<div class="revenue-card' + (active ? ' active' : '') + '" data-card-idx="' + idx + '">';
        html += '  <div class="rc-inner">';
        html += '    <div class="rc-icon">◀</div>';
        html += '    <div style="flex:1;min-width:0;">';
        html += '      <div class="rc-title">' + card.title + '</div>';
        html += '      <div class="rc-value">' + card.value + '<span class="unit">' + card.unit + '</span></div>';
        html += '      <div class="rc-yoy">' + formatYoyHtml(yoyText) + '</div>';
        html += '    </div>';
        html += '  </div>';
        html += '</div>';
      });
      html += '</div>';

      html += '<div class="sr-charts">';
      html += '  <div class="sr-chart-block">';
      html += '    <div class="sr-chart-head">';
      html += '      <div class="sr-chart-title" id="leftChartTitle">收入分析-' + (data.cards[0] ? data.cards[0].title : '') + '</div>';
      html += '      <div class="mode-switch">'
        + '        <button type="button" id="leftModeYearBtn">年度分析</button>'
        + '        <button type="button" id="leftModeMonthBtn" class="active">月度分析</button>'
        + '      </div>';
      html += '    </div>';
      var lg = data.leftLegend || ['月收入', '月同比'];
      html += '    <div class="sr-legend">'
        + '      <span><i class="sq" style="background:#3b82f6;"></i>' + lg[0] + '</span>'
        + '      <span><i class="minus" style="background:#f59e0b;"></i>' + lg[1] + '</span>'
        + '    </div>';
      html += '    <div class="sr-chart-body" id="srLeftChart"></div>';
      html += '  </div>';
      html += '  <div class="sr-chart-block">';
      html += '    <div class="sr-chart-head">';
      html += '      <div class="sr-chart-title" id="rightChartTitle"></div>';
      html += '      <div class="mode-switch">'
        + '        <button type="button" id="rightModeYearBtn">年度分析</button>'
        + '        <button type="button" id="rightModeMonthBtn" class="active">月度分析</button>'
        + '      </div>';
      html += '    </div>';
      html += '    <div class="sr-legend">'
        + '      <span><i class="sq" style="background:#1d8bff;"></i>到达用户</span>'
        + '      <span><i class="minus" style="background:#f59e0b;"></i>月同比</span>'
        + '    </div>';
      html += '    <div class="sr-chart-body" id="srRightChart"></div>';
      html += '  </div>';
      html += '</div>';

      // 渠道结构 & 地市分布（与已'右图同层级，随所选套餐联动）
      html += '<div class="sr-charts" style="margin-top:18px;">';
      html += '  <div class="sr-chart-block" style="padding:16px 18px;">';
      html += '    <div class="sr-chart-head" style="margin-bottom:10px;">';
      html += '      <div class="sr-chart-title" id="srChannelTitle"></div>';
      html += '    </div>';
      html += '    <div class="region-rows" id="srChannelRows"></div>';
      html += '  </div>';
      html += '  <div class="sr-chart-block" style="padding:16px 18px;">';
      html += '    <div class="sr-chart-head" style="margin-bottom:10px;">';
      html += '      <div class="sr-chart-title" id="srCityTitle"></div>';
      html += '    </div>';
      html += '    <div class="region-rows" id="srCityRows"></div>';
      html += '  </div>';
      html += '</div>';

      srContent.innerHTML = html;

      leftChartEl = srContent.querySelector('#srLeftChart');
      rightChartEl = srContent.querySelector('#srRightChart');
      leftChart = leftChartEl ? echarts.init(leftChartEl) : null;
      rightChart = rightChartEl ? echarts.init(rightChartEl) : null;
      leftTitleEl = srContent.querySelector('#leftChartTitle');
      rightTitleEl = srContent.querySelector('#rightChartTitle');
      cardsEl = srContent.querySelectorAll('[data-card-idx]');

      Array.prototype.forEach.call(cardsEl, function(el){
        el.addEventListener('click', function(){
          activeCardIndex = Number(el.getAttribute('data-card-idx')) || 0;
          renderChartsByActiveCard();
        });
      });

      var yearBtn = srContent.querySelector('#leftModeYearBtn');
      var monthBtn = srContent.querySelector('#leftModeMonthBtn');
      var rYearBtn = srContent.querySelector('#rightModeYearBtn');
      var rMonthBtn = srContent.querySelector('#rightModeMonthBtn');
      if (yearBtn) yearBtn.addEventListener('click', function(){ setMode('year'); });
      if (monthBtn) monthBtn.addEventListener('click', function(){ setMode('month'); });
      if (rYearBtn) rYearBtn.addEventListener('click', function(){ setMode('year'); });
      if (rMonthBtn) rMonthBtn.addEventListener('click', function(){ setMode('month'); });

      setMode('month');
    }

    // init
    var activeTab = document.querySelector('.sr-tab.active');
    renderTab(activeTab ? activeTab.getAttribute('data-tab') : 'wga');

    tabs.forEach(function(tab){
      tab.addEventListener('click', function(){
        var key = this.getAttribute('data-tab');
        tabs.forEach(function(t){ t.classList.remove('active'); });
        this.classList.add('active');
        renderTab(key);
      });
    });

    // 窗口 resize 时重绘图表
    window.addEventListener('resize', function(){
      if (leftChart) leftChart.resize();
      if (rightChart) rightChart.resize();
    });

    // view-board 付'display:none 激活时重绘（否分'ECharts 付00 尺寸渲染导致柱状图折叠）
    window.__srChartsResize = function(){
      if (leftChart) { leftChart.resize(); leftChart.setOption(leftChart.getOption(), true); }
      if (rightChart) { rightChart.resize(); rightChart.setOption(rightChart.getOption(), true); }
      if (window.__opsCharts) {
        window.__opsCharts.forEach(function(c){ if (c) { c.resize(); c.setOption(c.getOption(), true); } });
      }
    };

    // 导出 CSV
    var exportBtn = document.getElementById('srExportBtn');
    if (exportBtn) {
      exportBtn.addEventListener('click', function(){
        if (!srExportState.leftCfg || !srExportState.rightCfg) {
          alert('当前暂无可导出数据');
          return;
        }
        var now = new Date();
        var yyyy = now.getFullYear();
        var mm = String(now.getMonth() + 1).padStart(2, '0');
        var dd = String(now.getDate()).padStart(2, '0');
        var hh = String(now.getHours()).padStart(2, '0');
        var mi = String(now.getMinutes()).padStart(2, '0');
        var ss = String(now.getSeconds()).padStart(2, '0');

        var left = srExportState.leftCfg;
        var right = srExportState.rightCfg;
        var leftNames = left.seriesNames || {};

        var rows = [];
        rows.push(['导出时间', yyyy + '-' + mm + '-' + dd + ' ' + hh + ':' + mi + ':' + ss]);
        rows.push(['页签', srExportState.tabName]);
        rows.push(['当前选中套餐', srExportState.cardTitle]);
        rows.push(['左图模式', srExportState.rightMode === 'month' ? '月度分析' : '年度分析']);
        rows.push([]);

        rows.push(['左图', srExportState.leftTitle]);
        rows.push(['维度', leftNames.barA || '月收入', leftNames.line1 || '月同比']);
        (left.xLabels || []).forEach(function(label, i){
          rows.push([
            label,
            left.barsA && left.barsA[i] != null ? left.barsA[i] : '',
            left.line && left.line[i] != null ? left.line[i] : ''
          ]);
        });
        rows.push([]);

        rows.push(['右图', srExportState.rightTitle]);
        rows.push(['维度', '到达用户', '月同比']);
        (right.xLabels || []).forEach(function(label, i){
          rows.push([
            label,
            right.bars && right.bars[i] != null ? right.bars[i] : '',
            right.line && right.line[i] != null ? right.line[i] : ''
          ]);
        });

        var safeCardName = (srExportState.cardTitle || '汇态').replace(/[\\/:*?"<>|]/g, '_');
        var filename = '套餐收入导出_' + srExportState.tabName + '_' + safeCardName + '_' + yyyy + mm + dd + '_' + hh + mi + ss + '.csv';
        triggerCsvDownload(filename, rows);
      });
    }
  })();
