/* ===== 澶栭摼涓婁笅鏂囪В鏋愶紙宸ヤ綔娴佺粨鏉熻妭鐐?xsbot-panel 浼犲叆锛?====
   URL 褰㈠锛歝onfig-workbench.html?offer_id=xxx&name=xxx&chatId=xxx&req_id=xxx&stage=N&view=stage
   瑙ｆ瀽鍚庢毚闇插埌 window.__WB_QUERY锛屼緵椤甸潰鍐呭鑸繘搴?/ 榛樿 Tab / 閰嶇疆瀵硅薄 / 鏍囬鎸夐渶鍙栫敤銆?*/
(function(){
  var q = {};
  try {
    new URLSearchParams(window.location.search).forEach(function(v, k){ q[k] = v; });
  } catch (e) {}
  window.__WB_QUERY = q;
  var offerId = q.offer_id || q.offerId || '';
  var name = q.name || q.offer_name || '';
  var reqId = q.req_id || q.reqId || '';
  var stage = q.stage || '';
  var view = q.view || 'workbench';
  document.documentElement.setAttribute('data-wb-offer-id', offerId);
  document.documentElement.setAttribute('data-wb-chat-id', q.chatId || q.chat_id || '');
  document.documentElement.setAttribute('data-wb-req-id', reqId);
  document.documentElement.setAttribute('data-wb-stage', stage);
  document.documentElement.setAttribute('data-wb-view', view);
  if (name || offerId) {
    document.title = (name || offerId) + ' 路 閰嶇疆宸ヤ綔鍙?;
  }
  // 涓庢棦鏈夋紨绀烘暟鎹鎺ワ細鎶婂閾鹃€忎紶鐨勪笂涓嬫枃骞跺叆鍏ㄥ眬閰嶇疆锛岄〉闈㈠唴璇诲彇 window.__CFG 鍗冲彲鎰熺煡
  // stage -> view metadata: from stage-map.json (window.__WB_STAGE_MAP) when present, else empty
  var stageMap = window.__WB_STAGE_MAP || {};
  window.__WB_STAGE_META = (stageMap.stages || {})[stage] || null;
  window.__WB_DEFAULT_TAB = stageMap.default_tab || 'config';
  try {
    var cfg = window.__CFG || (window.__CFG = {});
    cfg.__query = q;
    if (offerId && cfg.launchProductConfig && cfg.launchProductConfig.baseInfo) {
      var bi = cfg.launchProductConfig.baseInfo;
      if (bi.prodCode && !bi.prodCode.value) bi.prodCode.value = offerId;
    }
  } catch (e) {}
})();
