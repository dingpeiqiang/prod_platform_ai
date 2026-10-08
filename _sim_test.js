/* 轻量 Vue 桩 + 行为仿真：验证稽核/测试/审批「进行中→完成」随轮询/兜底翻转。
   运行：node _sim_test.js */
const fs = require('fs');
const path = require('path');

// ---------- 最小 Vue 桩 ----------
let mountedCbs = [];
let unmountedCbs = [];
const Vue = {
  ref: (v) => ({ value: v }),
  reactive: (o) => o,
  computed: (fn) => ({ get value() { return fn() } }),
  watch: () => {},
  onMounted: (cb) => mountedCbs.push(cb),
  onUnmounted: (cb) => unmountedCbs.push(cb),
};

// ---------- 全局环境 ----------
global.requestAnimationFrame = (cb) => setTimeout(cb, 0);
global.setTimeout = setTimeout;
global.clearTimeout = clearTimeout;
global.setInterval = setInterval;
global.clearInterval = clearInterval;
global.document = { title: '', documentElement: { setAttribute() {} }, getElementById: () => null, addEventListener() {}, removeEventListener() {} };
global.CustomEvent = function () {};

// 可变的商品/查询上下文（供用例驱动）
let PRODUCT = { id: 'req-1', name: '测试商品', code: 'OFFER1', nodeResults: {} };
const QUERY = { req_id: 'REQ1', stage: '03', offer_id: 'OFFER1' };

global.window = {
  __CFG: { launchProductConfig: { baseInfo: {}, resourceSections: [], marketingSections: [], salesSections: [], previewSections: [] }, testReport: {} },
  __WB_QUERY: QUERY,
  __WB_PRODUCT: PRODUCT,
  __WB_PRODUCTS: [PRODUCT],
  __WB_HAS_REQ: true,
  __WB_REFRESH_RESULT: null,
  addEventListener() {}, removeEventListener() {}, dispatchEvent() {},
};
// window 上的赋值需能同步回上方 PRODUCT
Object.defineProperty(global.window, '__WB_PRODUCT', {
  get() { return PRODUCT; },
  set(v) { PRODUCT = v; },
});
global.window.Vue = Vue;
global.Vue = Vue;
global.ElMessage = { success() {}, warning() {}, error() {} };

// 加载组件文件
const src = fs.readFileSync(path.join('frontend/public/ops-web/js/app-config-workbench.js'), 'utf8');
const fnForLoad = new Function('window', 'global', 'Vue', 'ElMessage', src);
fnForLoad(global.window, global, Vue, global.ElMessage);

const C = global.window.ConfigWorkbench;
if (!C) { console.error('ConfigWorkbench 未挂载'); process.exit(1); }

// ---------- 断言工具 ----------
let pass = 0, fail = 0;
function ok(cond, msg) {
  if (cond) { pass++; console.log('  PASS ' + msg); }
  else { fail++; console.log('  FAIL ' + msg); }
}
function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

// 每次用例重置全局上下文
function resetCtx(stage, nodeResults, product) {
  mountedCbs = []; unmountedCbs = [];
  PRODUCT = product || { id: 'req-1', name: '测试商品', code: 'OFFER1', nodeResults: nodeResults || {} };
  Object.keys(QUERY).forEach((k) => delete QUERY[k]);
  Object.assign(QUERY, { req_id: 'REQ1', stage: stage, offer_id: 'OFFER1' });
}

let curUnmount = [];
function mountComponent() {
  mountedCbs = []; unmountedCbs = [];
  const vm = C.setup({ products: [PRODUCT], activeProductId: PRODUCT.id }, { expose() {}, emit() {} });
  mountedCbs.forEach((cb) => cb());
  curUnmount = unmountedCbs;
  return vm;
}
function disposeVm() { try { curUnmount.forEach((cb) => cb()); } catch (e) {} curUnmount = []; }

async function main() {
  // ===== 用例 A：后端 3s 后写出 spec/test/report → 轮询自动翻转 =====
  console.log('CASE A: 后端产出后由轮询自动翻转');
  resetCtx('03', {}, { id: 'req-1', name: '测试商品', code: 'OFFER1', nodeResults: {} });
  QUERY.stage = '99'; // 整体视图：稽核/测试/审批均 expected
  resetCtx('99', {}, { id: 'req-1', name: '测试商品', code: 'OFFER1', nodeResults: {} });
  // 轮询函数：模拟后端在 3s 后写入最终节点
  let backendReady = false;
  setTimeout(() => { backendReady = true; }, 3000);
  global.window.__WB_REFRESH_RESULT = () => {
    if (backendReady) {
      PRODUCT.nodeResults.spec = JSON.stringify({ pass: '1', error_list: [] });
      PRODUCT.nodeResults.test = JSON.stringify({ pass: '1', test_report: 'ok' });
      PRODUCT.nodeResults.report = JSON.stringify({ approval_suggest: '通过' });
      global.window.__WB_PRODUCT = PRODUCT;
    }
    return Promise.resolve(PRODUCT);
  };
  const vm = mountComponent();
  await sleep(500);
  ok(vm.audit.value.ready === false, 'A: 初始稽核 pending');
  ok(vm.testRep.value.ready === false, 'A: 初始测试 pending');
  ok(vm.approval.value.ready === false, 'A: 初始审批 pending');
  await sleep(4500);
  ok(vm.audit.value.ready === true, 'A: 轮询后稽核翻转为完成');
  ok(vm.testRep.value.ready === true, 'A: 轮询后测试翻转为完成');
  ok(vm.approval.value.ready === true, 'A: 轮询后审批翻转为完成');
  disposeVm();

  // ===== 用例 B：后端无产出 → 5s 兜底翻转 =====
  console.log('CASE B: 后端无产出由 5s 兜底翻转');
  resetCtx('99', {}, { id: 'req-2', name: '测试商品2', code: 'OFFER2', nodeResults: {} });
  global.window.__WB_REFRESH_RESULT = () => Promise.resolve(PRODUCT); // 永不产出
  const vm2 = mountComponent();
  await sleep(500);
  ok(vm2.audit.value.ready === false, 'B: 初始稽核 pending');
  ok(vm2.testRep.value.ready === false, 'B: 初始测试 pending');
  ok(vm2.approval.value.ready === false, 'B: 初始审批 pending');
  await sleep(5200);
  ok(vm2.audit.value.ready === true, 'B: 兜底后稽核翻转完成');
  ok(vm2.testRep.value.ready === true, 'B: 兜底后测试翻转完成');
  ok(vm2.approval.value.ready === true, 'B: 兜底后审批翻转完成');
  disposeVm();

  // ===== 用例 C：非审批环节（stage=03 稽核页签独立）审批不显示 pending =====
  console.log('CASE C: 无关环节不误置 pending');
  resetCtx('03', {}, { id: 'req-3', name: '测试商品3', code: 'OFFER3', nodeResults: {} });
  global.window.__WB_REFRESH_RESULT = () => Promise.resolve(PRODUCT);
  const vm3 = mountComponent();
  await sleep(300);
  ok(vm3.approval.value.ready === true, 'C: stage=03 审批不 pending（已 ready 渲染矩阵）');
  ok(vm3.audit.value.ready === false, 'C: stage=03 稽核仍 pending');
  disposeVm();

  // ===== 用例 D：URL 透传 approval_nodes → 审批直接 ready =====
  console.log('CASE D: URL 透传审批矩阵直接 ready');
  resetCtx('08', {}, { id: 'req-4', name: '测试商品4', code: 'OFFER4', nodeResults: {} });
  QUERY.approval_nodes = JSON.stringify([{ seq: 1, stage: '需求', node: '产品经理审核', role: '产品经理', status: '已通过' }]);
  global.window.__WB_REFRESH_RESULT = () => Promise.resolve(PRODUCT);
  const vm4 = mountComponent();
  await sleep(300);
  ok(vm4.approval.value.ready === true, 'D: 有 approval_nodes 直接 ready');
  disposeVm();

  console.log('\n结果: ' + pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(1); });
