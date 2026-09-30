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
    data(){ return { navs: ['需求提报','需求分析','销售品智能配置','配置规格稽核','资费校准','销售品自动测试','受理验证','上线审批','监控运维'], stage: '', stepStage: '' }; },
    created(){
      const q = window.__WB_QUERY || {};
      this.stage = q.stage || '';
      // step_stage：审批进度查询子流（stage=08）由工作流按真实审批矩阵计算的业务进度索引，
      // 与 stage（页面默认 Tab 选择）解耦，避免步骤条无条件停在「上线审批」。
      this.stepStage = (q.step_stage !== undefined && q.step_stage !== null && q.step_stage !== '') ? String(q.step_stage) : '';
    },
    computed: {
      // stage -> 导航步进索引（环节口径：08/09/10 辅助子流按就近展示；00 提报与 11 需求工单发起/审批同属「需求提报」环节）
      activeIndex(){
        // step_stage 优先：工作流已按真实审批进度给出业务步骤索引（如 08 审批查询可能落在 0/1/7/8）
        const ss = this.stepStage;
        if (ss !== '') {
          const n = parseInt(ss, 10);
          if (!isNaN(n) && n >= 0 && n < this.navs.length) return n;
        }
        var map = { '00':0, '11':0, '01':1, '02':2, '03':3, '05':4, '04':5, '06':7, '07':8, '08':7, '09':8, '10':8, '12':8 };
        var st = this.stage || '';
        return map[st] !== undefined ? map[st] : 2;
      }
    }
  };

  // 多商品：以 window.__WB_PRODUCTS 为数据源；外部解析（wb-products.js）完成或变更时同步，
  // 头部商品下拉/切换由此驱动，切换商品即更新当前商品实例并通知工作台重填。
  const App = {
    components: { NavBar, ConfigWorkbench: window.ConfigWorkbench },
    data(){
      return { products: (window.__WB_PRODUCTS || []).slice(), activeProductId: (window.__WB_PRODUCT && window.__WB_PRODUCT.id) || '' };
    },
    template: `
      <NavBar />
      <div class="page-body">
        <ConfigWorkbench
          :products="products"
          :active-product-id="activeProductId"
          @select-product="onSelectProduct"
        />
      </div>
    `,
    methods: {
      syncProducts(){
        const list = window.__WB_PRODUCTS || [];
        this.products = list.slice();
        const cur = window.__WB_PRODUCT || {};
        this.activeProductId = cur.id || '';
      },
      onSelectProduct(p){
        if (!p || p.id === this.activeProductId) return;
        // 切换当前商品实例：全局 __WB_PRODUCT 为各页签派生数据的唯一来源
        window.__WB_PRODUCT = p;
        const q = window.__WB_QUERY || (window.__WB_QUERY = {});
        q.offer_id = p.code || '';
        q.name = p.name || '';
        this.activeProductId = p.id;
        if (p.name || p.code) document.title = (p.name || p.code) + ' · 配置工作台';
        try { window.dispatchEvent(new CustomEvent('wb-product-change', { detail: p })); } catch (e) {}
      },
    },
    mounted(){
      this._onProductsChange = () => this.syncProducts();
      window.addEventListener('wb-product-change', this._onProductsChange);
    },
    unmounted(){
      if (this._onProductsChange) window.removeEventListener('wb-product-change', this._onProductsChange);
    },
  };

  function mount(){
    const el = document.getElementById('app');
    if (!el) return;
    if (el.children.length) el.innerHTML = '';
    createApp(App).mount(el);
  }

  // 首屏等待商品解析完成（req_id 拉取节点结果），避免挂载后商品列表为空再补刷
  function whenReady(){
    const ready = window.__WB_DATA_READY;
    if (ready && typeof ready.then === 'function') {
      ready.then(mount).catch(mount);
    } else {
      mount();
    }
  }
  if (document.readyState === 'loading') {
    window.addEventListener('DOMContentLoaded', whenReady);
  } else {
    whenReady();
  }
})();
