/* ===== 商品数据源（按 req_id 动态加载，无硬编码商品列表）=====
   数据来源：
     ① req_id 命中节点结果接口（/result/query）→ 解析各环节 result_json 组装商品名称/编码/月费/资源；
     ② URL 传入 offer_id/name → 追加为当前商品的兜底标识；
     ③ 均取不到 → 商品列表为空，当前商品为空对象（由调用方按空态渲染，不再回落内置默认商品）。
   解析完成前通过 window.__WB_DATA_READY（Promise）阻塞挂载，保证首屏即为最终商品。 */
(function(){
  // 动态商品列表（不再内置演示商品；仅由接口/URL 结果填充）
  var PRODUCTS = [];
  window.__WB_PRODUCTS = PRODUCTS;

  // 接口基址：生产部署在 /prod-ai/ 前缀下（nginx 剥前缀转发），本地/根路径走 /api
  function apiBase(){
    var base = location.pathname.indexOf('/prod-ai/') === 0 ? '/prod-ai' : '';
    return base + '/api/v1/appstore';
  }

  // 安全解析 JSON 字符串（result_json 为字符串，含容错）
  function parseJson(s){
    if (!s) return null;
    if (typeof s === 'object') return s;
    try { return JSON.parse(s); } catch (e) { return null; }
  }

  // 归一销售品列表：兼容 {offers:[...]} / [ ... ] / JSON 字符串；逐品补 offer_seq
  function normalizeOffers(raw){
    var obj = parseJson(raw);
    if (obj && !Array.isArray(obj) && Array.isArray(obj.offers)) obj = obj.offers;
    if (!Array.isArray(obj)) return [];
    return obj.filter(function(o){ return o && typeof o === 'object'; }).map(function(o, i){
      var one = {};
      Object.keys(o).forEach(function(k){ one[k] = o[k]; });
      if (one.offer_seq === undefined || one.offer_seq === null || one.offer_seq === '') one.offer_seq = i + 1;
      return one;
    });
  }

  // 从任意层级按候选键路径深取首个非空标量值
  function pick(obj, paths){
    if (!obj) return '';
    for (var i = 0; i < paths.length; i++){
      var cur = obj, parts = paths[i].split('.');
      for (var j = 0; j < parts.length && cur != null; j++) cur = cur[parts[j]];
      if (cur !== undefined && cur !== null && String(cur).trim() !== '') return String(cur).trim();
    }
    return '';
  }

  // 从节点结果列表（各环节 result_json）解析商品实例：逐字段取，取不到即留空（由调用方回填）
  function buildProductFromNodeResults(list){
    var byNode = {};
    (list || []).forEach(function(rec){
      if (rec && rec.node_name) byNode[rec.node_name] = parseJson(rec.result_json);
    });
    // 配置环节（config）优先：含 offer_id/offer_name/in_fee/plan_json，信息最全
    var cfg = byNode.config || {};
    var plan = cfg.plan_json || {};
    var opt = plan.optionalInfo || {};
    var base = plan.baseInfo || {};
    var requirement = byNode.requirement || {};
    var reqReport = byNode.requirement_report || {};

    // 权威销售品名册：以需求提报单（requirement_report）为准（name+quantity）；
    // 逐环节（config/spec/fee/test/report）offers[] 已由工作流按 offer_seq 继承权威名称，此处仅作兜底合并。
    var roster = normalizeOffers(reqReport);
    if (!roster.length){
      ['config', 'spec', 'fee', 'test_pending', 'test', 'audit_pending', 'requirement'].some(function(nm){
        var got = normalizeOffers(byNode[nm]);
        if (got.length){ roster = got; return true; }
        return false;
      });
    }
    var rosterBySeq = {};
    roster.forEach(function(o){
      rosterBySeq[String(o.offer_seq)] = { name: o.name || '', quantity: o.quantity };
    });
    // 各环节 offers[] 缺失 name/quantity 时按 offer_seq 回填权威名册
    function mergeRoster(offers){
      (offers || []).forEach(function(o){
        var r = rosterBySeq[String(o.offer_seq)];
        if (!r) return;
        if (r.name && !String(o.name || '').trim()) o.name = r.name;
        if (r.quantity !== undefined && r.quantity !== null && r.quantity !== '' && (o.quantity === undefined || o.quantity === null || o.quantity === '')) o.quantity = r.quantity;
      });
      return offers;
    }
    mergeRoster(normalizeOffers(cfg));

    // 名称优先级：权威需求提报单 name 最高，其次配置环节，再回落计划/相似产品字段
    var name = pick(reqReport, ['name'])
      || (roster[0] && roster[0].name)
      || pick(cfg, ['offer_name'])
      || pick(opt, ['SmsInstCfg.SvcName', 'printContent.prcName'])
      || pick(base, ['prodPrcName'])
      || pick(requirement, ['baseInfo.prodPrcName']);
    // 数量：权威名册首品数量，其次配置环节透传值
    var qty = (roster[0] && roster[0].quantity !== undefined && roster[0].quantity !== null && roster[0].quantity !== '') ? roster[0].quantity
      : (pick(cfg, ['quantity']) || '');
    var code = pick(cfg, ['offer_id', 'offerId'])
      || pick(base, ['prodPrcId', 'prodId'])
      || pick(requirement, ['baseInfo.prodPrcId', 'baseInfo.prodId'])
      || pick(reqReport, ['offer_id']);
    var fee = pick(cfg, ['in_fee.档位'])
      || pick(opt, ['acctMonth.fixFee', 'printContent.prcMonthFee'])
      || pick(reqReport, ['price']);
    var flow = pick(opt, ['billGprsCfg.theshold']);
    var flowUnit = pick(opt, ['billGprsCfg.unit']) || 'GB';
    var voice = pick(opt, ['billVoiceCfg.theshold']);
    var sms = pick(opt, ['billSmsCfg.theshold']);
    // 资源可能为合并串（如「120GB国内流量,1000分钟语音」），据此兜底补齐流量/语音
    var res = pick(opt, ['printContent.containResource']) || pick(reqReport, ['resources']);
    if (res && (!flow || !voice)){
      var mFlow = res.match(/(\d+(?:\.\d+)?)\s*(TB|GB|MB)/i);
      var mVoice = res.match(/(\d+(?:\.\d+)?)\s*分钟/);
      var mSms = res.match(/(\d+(?:\.\d+)?)\s*条/);
      if (!flow && mFlow){ flow = mFlow[1]; flowUnit = mFlow[2].toUpperCase(); }
      if (!voice && mVoice) voice = mVoice[1];
      if (!sms && mSms) sms = mSms[1];
    }
    return { name: name, code: code, fee: fee, flow: flow, flowUnit: flowUnit, voice: voice, sms: sms, quantity: qty, offers: roster, offerCount: roster.length, plan: plan, raw: cfg, nodeResults: byNode };
  }

  // 将接口解析结果写入/追加为当前商品（全量联动各页签）
  function applyResolvedProduct(resolved){
    if (!resolved) return false;
    var haveAny = resolved.name || resolved.code || resolved.fee || resolved.flow || resolved.voice || resolved.sms;
    if (!haveAny) return false;
    var id = 'req-' + (resolved.code || resolved.name || Date.now());
    var product = {
      id: id,
      name: resolved.name || '',
      code: resolved.code || '',
      fee: resolved.fee || '',
      flow: resolved.flow || '',
      flowUnit: resolved.flowUnit || 'GB',
      voice: resolved.voice || '',
      sms: resolved.sms || '',
      quantity: (resolved.quantity !== undefined && resolved.quantity !== null) ? resolved.quantity : '',
      offers: resolved.offers || [],
      offerCount: resolved.offerCount || (resolved.offers ? resolved.offers.length : 0),
      status: '在售中',
      templateLabel: '',
      category: 'req_id商品'
    };
    var existed = PRODUCTS.filter(function(p){ return p.id === id; })[0];
    if (existed){
      Object.keys(product).forEach(function(k){ existed[k] = product[k]; });
      window.__WB_PRODUCT = existed;
    } else {
      PRODUCTS.unshift(product);
      window.__WB_PRODUCT = product;
    }
    // 同步 URL 上下文与全局实例，供运营视图等独立模块读取
    var q = window.__WB_QUERY || (window.__WB_QUERY = {});
    q.offer_id = product.code || '';
    q.name = product.name || '';
    window.__WB_INSTANCE = { name: product.name, code: product.code };
    // 标题随解析结果更新，并通知独立模块（运营视图）刷新
    if (product.name || product.code) {
      document.title = (product.name || product.code) + ' · 配置工作台';
    }
    try { window.dispatchEvent(new CustomEvent('wb-product-change', { detail: product })); } catch (e) {}
    return true;
  }

  var q = window.__WB_QUERY || {};
  var offerId = q.offer_id || q.offerId || '';
  var qName = q.name || q.offer_name || '';
  var reqId = q.req_id || q.reqId || '';

  // 无 req_id 时，若 URL 携带 offer_id/name，则以此构造一个最小商品占位（字段留空待填充）
  function pickByUrl(){
    if (!offerId && !qName) return null;
    var temp = { id: 'url-' + (offerId || qName), name: qName || offerId, code: offerId || '', fee: '', flow: '', flowUnit: 'GB', voice: '', sms: '', status: '草稿', templateLabel: '销售品模板', category: '外链商品' };
    PRODUCTS.unshift(temp);
    return temp;
  }

  // 先落 URL 商品（若有），保证接口不可用时页面仍有基本数据
  window.__WB_PRODUCT = pickByUrl() || {};

  // 按 req_id 拉取节点结果并解析；失败/超时静默保留已设置的值
  window.__WB_DATA_READY = (function(){
    if (!reqId) return Promise.resolve(window.__WB_PRODUCT);
    var controller = (typeof AbortController !== 'undefined') ? new AbortController() : null;
    var timer = setTimeout(function(){ if (controller) controller.abort(); }, 5000);
    var url = apiBase() + '/result/query?req_id=' + encodeURIComponent(reqId) + '&latest_only=1';
    return fetch(url, { method: 'GET', signal: controller ? controller.signal : undefined })
      .then(function(r){ return r.ok ? r.json() : null; })
      .then(function(data){
        if (data && (data.code === 0 || data.list)) {
          var resolved = buildProductFromNodeResults(data.list);
          if (applyResolvedProduct(resolved)) return window.__WB_PRODUCT;
        }
        return window.__WB_PRODUCT;
      })
      .catch(function(){ return window.__WB_PRODUCT; })
      .then(function(p){ clearTimeout(timer); return p; });
  })();
})();
