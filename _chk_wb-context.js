/* ===== 外链上下文解析（工作流结束节点?xsbot-panel 传入，?====
   URL 形如：config-workbench.html?offer_id=xxx&name=xxx&chatId=xxx&req_id=xxx&stage=N&view=stage
   解析后暴露到 window.__WB_QUERY，供页面内导航进度?/ 默认 Tab / 配置对象 / 标题按需取用 */
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
    document.title = (name || offerId) + ' · 配置工作台';
  }
  // 与既有演示数据对接：把外链透传的上下文并入全局配置，页面内读取 window.__CFG 即可感知
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
