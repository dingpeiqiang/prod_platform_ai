/* ===== 根组件与挂载入口 =====
   组装 NavBar + ConfigWorkbench 并挂载到 #app。
   ConfigWorkbench 由 app-config-workbench.js 暴露到 window.ConfigWorkbench。 */
(function(){
  const { createApp } = Vue;
  const CFG = window.__CFG || (window.__CFG = {});

  // 展示全部工作流页签：状态置为终态 channel，并开启测试/受理/上线环节数据
  if (CFG.launchProductConfig) CFG.launchProductConfig.productInfo.status = 'channel';
  if (CFG.testReport) CFG.testReport.uploaded = true;
  if (CFG.filing) { CFG.filing.started = true; CFG.filing.submitted = false; }
  if (CFG.salesChannel) CFG.salesChannel.submitted = false;

  const NavBar = {
    template: `
      <nav class="biz-nav">
        <div class="stepper">
          <div class="stepper-track"></div>
          <div v-for="(n,i) in navs" :key="n" class="stepper-item"
               :class="{ done: i < activeIndex, active: i === activeIndex }">
            <span class="stepper-circle" v-if="i < activeIndex">✓</span>
            <span class="stepper-circle" v-else>{{ i+1 }}</span>
            <div class="stepper-label">{{ n }}</div>
          </div>
        </div>
      </nav>
    `,
    data(){ return { navs: ['需求提报','需求分析','销售品智能配置','配置规格稽核','资费校准','销售品自动测试','受理验证','上线审批','监控运维'], stage: '' }; },
    created(){ this.stage = (window.__WB_QUERY && window.__WB_QUERY.stage) || ''; },
    computed: {
      // stage -> 导航步进索引（环节口径：08/09/10 辅助子流按就近展示）
      activeIndex(){
        var map = { '00':0, '01':1, '02':2, '03':3, '05':4, '04':5, '06':7, '07':8, '08':7, '09':8, '10':8 };
        var st = this.stage || '';
        return map[st] !== undefined ? map[st] : 2;
      }
    }
  };

  const App = {
    components: { NavBar, ConfigWorkbench: window.ConfigWorkbench },
    template: `
      <NavBar />
      <div class="page-body">
        <ConfigWorkbench />
      </div>
    `,
  };

  function mount(){
    const el = document.getElementById('app');
    if (!el) return;
    if (el.children.length) el.innerHTML = '';
    createApp(App).mount(el);
  }
  if (document.readyState === 'loading') {
    window.addEventListener('DOMContentLoaded', mount);
  } else {
    mount();
  }
})();
