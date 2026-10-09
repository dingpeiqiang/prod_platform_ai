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

  // 接口基址：优先 window.__WB_API_BASE（可被宿主/外链覆盖）；否则生产部署在 /prod-ai/ 前缀下（nginx 剥前缀转发），本地/根路径走 /api
  function apiBase(){
    if (window.__WB_API_BASE) return window.__WB_API_BASE;
    var base = location.pathname.indexOf('/prod-ai/') === 0 ? '/prod-ai' : '';
    return base + '/api/v1/appstore';
  }

  // 安全解析 JSON 字符串（result_json 为字符串，含容错）
  function parseJson(s){
    if (!s) return null;
    if (typeof s === 'object') return s;
    try { return JSON.parse(s); } catch (e) { return null; }
  }

  // 归一销售品列表：兼容 {offers:[...]} / [ ... ] / 单品扁平对象 / JSON 字符串；逐品补 offer_seq；
  // 需求提报单元素形如 {offer_seq, fields:{name,price,resources,...}}，"fields" 拉平到元素顶层便于统一取值。
  // 兼容两类历史/现网形态：①{req_id, offers:[...]}；②单品薄平对象 {name, price, resources, product_type,...}（无 offers 包裹）→ 视为 1 品。
  function looksLikeOffer(o){
    if (!o || typeof o !== 'object' || Array.isArray(o)) return false;
    return ['name', 'price', 'resources', 'prodPrcName', 'offer_name', 'offer_id', 'in_fee', 'plan_json', 'product_type', 'out_price']
      .some(function(k){ return o[k] !== undefined && o[k] !== null && String(o[k]).trim() !== ''; });
  }
  function normalizeOffers(raw){
    var obj = parseJson(raw);
    if (obj && !Array.isArray(obj) && Array.isArray(obj.offers)) obj = obj.offers;
    // 单品扁平对象（含 req_id 等包装键但无 offers）：包成 1 品，避免整条被丢弃导致商品列表为空
    if (obj && !Array.isArray(obj) && looksLikeOffer(obj)) obj = [obj];
    if (!Array.isArray(obj)) return [];
    return obj.filter(function(o){ return o && typeof o === 'object' && !Array.isArray(o); }).map(function(o, i){
      var one = {};
      Object.keys(o).forEach(function(k){ if (k !== 'fields') one[k] = o[k]; });
      if (o.fields && typeof o.fields === 'object'){
        Object.keys(o.fields).forEach(function(k){ if (one[k] === undefined) one[k] = o.fields[k]; });
      }
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

  // 计划体是否形似：含 baseInfo/optionalInfo 等计划键
  function isPlanLike(o){
    if (!o || typeof o !== 'object' || Array.isArray(o)) return false;
    return ['baseInfo', 'optionalInfo', 'releaseInfo', 'monthly_fee', 'monthlyFee', 'in_fee']
      .some(function(k){ return o[k] !== undefined && o[k] !== null; });
  }
  // 计划体可能被模板 id 再包一层（如 {familyBasePrc:{baseInfo,optionalInfo}}、多模板
  // {personMainPrc:{...},familyBasePrc:{...}}）。返回候选计划体数组：外层（若形似）优先，
  // 其余内层对象按出现顺序追加，供调用方逐候选取值（多模板时命中 familyBasePrc 等含值体）。
  function planCandidates(plan){
    if (!plan || typeof plan !== 'object' || Array.isArray(plan)) return [];
    var arr = [];
    if (isPlanLike(plan)) arr.push(plan);
    Object.keys(plan).forEach(function(k){
      var v = plan[k];
      if (v && typeof v === 'object' && !Array.isArray(v) && isPlanLike(v) && arr.indexOf(v) < 0) arr.push(v);
    });
    if (!arr.length) arr.push(plan);
    return arr;
  }
  // 逐候选计划体取值：任一候选命中即返回（配合多模板/模板包裹场景）
  function pickPlan(plans, getter){
    for (var i = 0; i < plans.length; i++){
      var v = getter(plans[i]);
      if (v !== undefined && v !== null && String(v).trim() !== '') return String(v).trim();
    }
    return '';
  }

  // 从某销售品（配置环节 offer 或名册项）解析身份与资源字段，缺失即留空由调用方回填
  function buildOfferFields(off, roster){
    off = off || {};
    // 计划体来源多态：配置环节 plan_json（节点103）/ 执行方案 payload（节点114）/ plan / elements(_json)。
    // 各来源均为合并后的计划体 JSON（含 baseInfo/optionalInfo），逐层解包取首个可用对象。
    var plan = {};
    ['plan_json', 'payload', 'plan', 'elements_json', 'elements', 'product_config'].some(function(k){
      var got = parseJson(off[k]);
      if (!got && off[k] && typeof off[k] === 'object') got = off[k];
      if (!got || typeof got !== 'object' || Array.isArray(got)) return false;
      // 兼容内层再包一层 {elements/payload/fields:{...}} 的形态
      ['elements', 'payload', 'fields'].some(function(w){
        var inner = parseJson(got[w]);
        if (!inner && got[w] && typeof got[w] === 'object') inner = got[w];
        if (inner && typeof inner === 'object' && !Array.isArray(inner) && Object.keys(inner).length){ got = inner; return true; }
        return false;
      });
      plan = got;
      return true;
    });
    // 模板包裹解包：部分计划体以模板 id 再包一层（如 {familyBasePrc:{baseInfo,optionalInfo}}、
    // {personMainPrc:{...}}），收集候选计划体，逐候选取值。
    var plans = planCandidates(plan);
    var opt = (plans[0] || {}).optionalInfo || {};
    var base = (plans[0] || {}).baseInfo || {};
    var name = String(off.name || '').trim() || pickPlan(plans, function(p){ return pick(p.optionalInfo || {}, ['SmsInstCfg.SvcName', 'printContent.prcName']) || pick(p.baseInfo || {}, ['prodPrcName']); });
    var code = pick(off, ['offer_id', 'offerId']) || pick(base, ['prodPrcId', 'prodId']);
    var fee = pick(off, ['in_fee.档位']) || pickPlan(plans, function(p){ return pick(p.optionalInfo || {}, ['acctMonth.fixFee', 'printContent.prcMonthFee']) || pick(p.baseInfo || {}, ['fixFee', 'prodPrcFixFee']) || pick(p, ['monthly_fee', 'monthlyFee', 'price']); }) || pick(off, ['monthly_fee', 'price']);
    var flow = pickPlan(plans, function(p){ return pick(p.optionalInfo || {}, ['billGprsCfg.theshold']); });
    var flowUnit = pickPlan(plans, function(p){ return pick(p.optionalInfo || {}, ['billGprsCfg.unit']); }) || 'GB';
    var voice = pickPlan(plans, function(p){ return pick(p.optionalInfo || {}, ['billVoiceCfg.theshold']); });
    var sms = pickPlan(plans, function(p){ return pick(p.optionalInfo || {}, ['billSmsCfg.theshold']); });
    // 资源可能为合并串（如「120GB国内流量,1000分钟语音」），据此兜底补齐流量/语音/短信
    var res = pickPlan(plans, function(p){ return pick(p.optionalInfo || {}, ['printContent.containResource']); }) || pick(off, ['resources']);
    if (res && (!flow || !voice)){
      var mFlow = res.match(/(\d+(?:\.\d+)?)\s*(TB|GB|MB)/i);
      var mVoice = res.match(/(\d+(?:\.\d+)?)\s*分钟/);
      var mSms = res.match(/(\d+(?:\.\d+)?)\s*条/);
      if (!flow && mFlow){ flow = mFlow[1]; flowUnit = mFlow[2].toUpperCase(); }
      if (!voice && mVoice) voice = mVoice[1];
      if (!sms && mSms) sms = mSms[1];
    }
    return { name: name, code: code, fee: fee, flow: flow, flowUnit: flowUnit, voice: voice, sms: sms, quantity: (off.quantity !== undefined && off.quantity !== null && off.quantity !== '') ? off.quantity : '', offerSeq: (off.offer_seq !== undefined && off.offer_seq !== null && off.offer_seq !== '') ? off.offer_seq : '' };
  }

  // 从节点结果列表（各环节 result_json）解析商品实例：逐字段取，取不到即留空（由调用方回填）
  function buildProductFromNodeResults(list){
    // list 兼容数组 / JSON 字符串（插件契约 list 声明为字符串）
    if (typeof list === 'string') list = parseJson(list) || [];
    if (!Array.isArray(list)) list = [];
    var byNode = {};
    list.forEach(function(rec){
      if (rec && rec.node_name) byNode[rec.node_name] = parseJson(rec.result_json);
    });
    // 配置环节（config）优先：含 offer_id/offer_name/in_fee/plan_json，信息最全
    var cfg = byNode.config || {};
    var plan0 = typeof cfg.plan_json === 'string' ? (parseJson(cfg.plan_json) || {}) : (cfg.plan_json || {});
    var plans = planCandidates(plan0);
    var plan = plans[0] || {};
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
    // 逐品明细来源：优先配置环节 offers（offer_id/plan_json），缺失时回退执行方案等环节
    // （requirement/spec/fee/test 的 offers[{offer_seq,payload}]），保证多商品逐品身份/资源/固定费可解析。
    var detailNode = '';
    ['config', 'requirement', 'spec', 'fee', 'test_pending', 'test', 'audit_pending'].some(function(nm){
      if (normalizeOffers(byNode[nm]).length){ detailNode = nm; return true; }
      return false;
    });
    var cfgOffers = normalizeOffers(detailNode ? byNode[detailNode] : cfg);
    mergeRoster(cfgOffers);
    var cfgBySeq = {};
    cfgOffers.forEach(function(o){ cfgBySeq[String(o.offer_seq)] = o; });

    // ---------- 商品列表：多品时逐品展开为独立商品（头部可切换），单品时一项 ----------
    // 逐品字段：以名册项（需求提报单：name/price/resources）为底，配置环节同 offer_seq 明细补全身份/资源
    var offerProducts = roster.map(function(o){
      var merged = {};
      // 名册项字段（含 fields 拉平后的 name/price/resources）先入底
      Object.keys(o).forEach(function(k){ if (o[k] !== undefined && o[k] !== null && o[k] !== '') merged[k] = o[k]; });
      var detail = cfgBySeq[String(o.offer_seq)] || {};
      Object.keys(detail).forEach(function(k){ if (detail[k] !== undefined && detail[k] !== null && detail[k] !== '') merged[k] = detail[k]; });
      var f = buildOfferFields(merged, roster);
      // 单品时允许回落计划/需求提报单整体字段，保证信息最全
      if (roster.length <= 1){
        f.name = f.name || pick(cfg, ['offer_name']) || pick(opt, ['SmsInstCfg.SvcName']) || pick(base, ['prodPrcName']);
        f.code = f.code || pick(base, ['prodPrcId', 'prodId']);
      }
      return f;
    }).filter(function(f){ return f.name || f.code; });

    // 兜底：名册为空但存在单商品整体字段时，仍构造一项，避免空列表
    if (!offerProducts.length){
      var single = buildOfferFields(cfg, roster);
      single.name = single.name || pick(cfg, ['offer_name']) || pick(opt, ['SmsInstCfg.SvcName', 'printContent.prcName']) || pick(base, ['prodPrcName']) || pick(requirement, ['baseInfo.prodPrcName']);
      single.code = single.code || pick(cfg, ['offer_id', 'offerId']) || pick(base, ['prodPrcId', 'prodId']) || pick(requirement, ['baseInfo.prodPrcId', 'baseInfo.prodId']);
      single.fee = single.fee || pick(cfg, ['in_fee.档位']) || pick(opt, ['acctMonth.fixFee', 'printContent.prcMonthFee']) || pick(plan, ['monthly_fee', 'monthlyFee', 'price']);
      single.quantity = (single.quantity !== '' ) ? single.quantity : (pick(cfg, ['quantity']) || '');
      if (single.name || single.code) offerProducts.push(single);
    }

    // 主商品 = 首品（或 URL 指定 offer_id 命中的那一品），字段取首品，offers 保留完整名册
    var primary = offerProducts[0] || {};
    return {
      name: primary.name || '', code: primary.code || '', fee: primary.fee || '',
      flow: primary.flow || '', flowUnit: primary.flowUnit || 'GB', voice: primary.voice || '', sms: primary.sms || '',
      quantity: primary.quantity || '', offers: roster, offerCount: roster.length,
      products: offerProducts, plan: plan0, raw: cfg, nodeResults: byNode
    };
  }

  // 将接口解析结果写入/追加为商品列表（多品逐品入列，并选中 URL 指定/首品）
  function applyResolvedProduct(resolved){
    if (!resolved) return false;
    var haveAny = resolved.name || resolved.code || resolved.fee || resolved.flow || resolved.voice || resolved.sms
      || (resolved.products && resolved.products.length);
    if (!haveAny) return false;

    var q = window.__WB_QUERY || (window.__WB_QUERY = {});
    var wantedOfferId = String(q.offer_id || q.offerId || '').trim();

    var list = (resolved.products && resolved.products.length) ? resolved.products : [resolved];
    var created = [];
    list.forEach(function(res, idx){
      // id 需保证唯一：多品同 code/name（或均缺失）会使 id 碰撞，导致头部只渲染部分商品且切换失效。
      // 以 offerSeq 优先、其次 code/name，最后追加序号兜底，确保每个商品 id 唯一。
      var key = (res.offerSeq !== undefined && res.offerSeq !== null && res.offerSeq !== '') ? ('seq' + res.offerSeq) : (res.code || res.name || '');
      var id = 'req-' + (key || ('idx' + idx)) + '-' + idx;
      var product = {
        id: id,
        // offer_seq 必须落到商品实例：各页签按 offer_seq 从共享 nodeResults 中定位本品记录，
        // 缺失时会回落到首个销售品，导致多商品切换后内容不匹配。
        offer_seq: (res.offerSeq !== undefined && res.offerSeq !== null && res.offerSeq !== '') ? res.offerSeq : '',
        name: res.name || '',
        code: res.code || '',
        fee: res.fee || '',
        flow: res.flow || '',
        flowUnit: res.flowUnit || 'GB',
        voice: res.voice || '',
        sms: res.sms || '',
        quantity: (res.quantity !== undefined && res.quantity !== null) ? res.quantity : '',
        offers: resolved.offers || [],
        offerCount: resolved.offerCount || (resolved.offers ? resolved.offers.length : 0),
        status: '在售中',
        templateLabel: '',
        category: 'req_id商品',
        // 各环节节点保存内容（按 node_name 索引的原始 result_json 对象）与配置环节原文，供各页签动态渲染
        nodeResults: resolved.nodeResults || {},
        raw: resolved.raw || {}
      };
      var existed = PRODUCTS.filter(function(p){ return p.id === id; })[0];
      if (existed){
        Object.keys(product).forEach(function(k){ existed[k] = product[k]; });
        created.push(existed);
      } else {
        PRODUCTS.push(product);
        created.push(product);
      }
    });
    if (!created.length) return false;

    // 接口解析成功：移除仅由 URL 构造的临时占位商品，避免与真实逐品重复
    for (var i = PRODUCTS.length - 1; i >= 0; i--){
      if (PRODUCTS[i] && PRODUCTS[i].category === '外链商品') PRODUCTS.splice(i, 1);
    }

    // 选中：URL 指定 offer_id 命中优先，否则首品
    var active = (wantedOfferId && created.filter(function(p){ return p.code === wantedOfferId; })[0]) || created[0];
    window.__WB_PRODUCT = active;

    // 同步 URL 上下文与全局实例，供运营视图等独立模块读取
    q.offer_id = active.code || '';
    q.name = active.name || '';
    window.__WB_INSTANCE = { name: active.name, code: active.code };
    // 标题随解析结果更新，并通知独立模块（运营视图）刷新
    if (active.name || active.code) {
      document.title = (active.name || active.code) + ' · 配置工作台';
    }
    try { window.dispatchEvent(new CustomEvent('wb-product-change', { detail: active, products: PRODUCTS })); } catch (e) {}
    return true;
  }

  var q = window.__WB_QUERY || {};
  var offerId = q.offer_id || q.offerId || '';
  var qName = q.name || q.offer_name || '';
  var reqId = q.req_id || q.reqId || '';
  // 多商品：url 可携带 offers=<offer_id,offer_id,...>（面板由工作流 offers[] 拍平），逐品构造占位
  var offersCsv = q.offers || q.offer_ids || '';

  // 无 req_id 时，若 URL 携带 offer_id/name/offers，则构造最小商品占位（字段留空待填充）
  function pickByUrl(){
    var ids = String(offersCsv || '').split(',').map(function(s){ return s.trim(); }).filter(Boolean);
    if (ids.length > 1){
      var first = null;
      ids.forEach(function(id, i){
        var temp = { id: 'url-' + id, name: (i === 0 ? (qName || id) : id), code: id, fee: '', flow: '', flowUnit: 'GB', voice: '', sms: '', status: '草稿', templateLabel: '销售品模板', category: '外链商品' };
        PRODUCTS.push(temp);
        if (i === 0) first = temp;
      });
      return first;
    }
    if (!offerId && !qName) return null;
    var temp = { id: 'url-' + (offerId || qName), name: qName || offerId, code: offerId || '', fee: '', flow: '', flowUnit: 'GB', voice: '', sms: '', status: '草稿', templateLabel: '销售品模板', category: '外链商品' };
    PRODUCTS.unshift(temp);
    return temp;
  }

  // 先落 URL 商品（若有），保证接口不可用时页面仍有基本数据
  window.__WB_PRODUCT = pickByUrl() || {};

  // 供各页签动态渲染：读取当前选中商品对应环节的节点保存内容（按 node_name 索引）。
  // 返回归一化后的对象：{ byNode, offers（权威名册，含 offer_seq/name/quantity 及逐品 fields）, raw }。
  // 无任何节点内容时返回空结构，调用方回落到静态兜底。
  window.__WB_NODE_CONTENT = function(){
    var p = window.__WB_PRODUCT || {};
    var byNode = p.nodeResults || {};
    // 权威名册：优先需求提报单（requirement_report），逐环节回退，最终用当前商品 offers
    var roster = [];
    ['requirement_report', 'requirement', 'config', 'spec', 'fee', 'test'].some(function(nm){
      var got = normalizeOffers(byNode[nm]);
      if (got.length){ roster = got; return true; }
      return false;
    });
    if (!roster.length && Array.isArray(p.offers)) roster = normalizeOffers(p.offers);
    return { byNode: byNode, offers: roster, raw: p.raw || {} };
  };

  // 按 req_id 拉取节点结果并解析；失败/超时静默保留已设置的值。首屏与轮询共用同一实现。
  function fetchResultOnce(){
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
        // 有 req_id 但接口无有效数据：标记降级，供页面提示（占位商品仍可用）
        window.__WB_DATA_DEGRADED = true;
        return window.__WB_PRODUCT;
      })
      .catch(function(){
        window.__WB_DATA_DEGRADED = true;
        return window.__WB_PRODUCT;
      })
      .then(function(p){ clearTimeout(timer); return p; });
  }

  window.__WB_HAS_REQ = !!reqId;

  window.__WB_DATA_READY = fetchResultOnce();

  // 供工作台外链轮询：规格稽核(spec)/自动化测试(test) 后端完成后重新拉取节点结果，
  // 使「进行中」在前端与后端保持同步地转为「完成」（后端无产出时由前端兜底）。
  window.__WB_REFRESH_RESULT = fetchResultOnce;
})();
