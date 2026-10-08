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

  // ===== 用例 E：需求提报单内嵌「需求审批矩阵」随 URL/节点状态自动更新 =====
  console.log('CASE E: 需求审批矩阵只读自动更新');
  // E1 未发起：无 approval_nodes、非审批环节 → 待发起，节点全待审核
  resetCtx('00', {}, { id: 'req-5', name: '测试商品5', code: 'OFFER5', nodeResults: {} });
  global.window.__WB_REFRESH_RESULT = () => Promise.resolve(PRODUCT);
  const vm5 = mountComponent();
  await sleep(200);
  ok(vm5.demandApproval.value.visible === true, 'E1: 需求审批矩阵可见');
  ok(vm5.demandApproval.value.status === '待发起', 'E1: 未发起时为「待发起」');
  ok(vm5.demandApproval.value.rows.every((r) => r[4] === '待审核'), 'E1: 未发起时节点全为「待审核」');
  disposeVm();

  // E2 已发起未完成：URL 供需求阶段首个已通过、次节点审批中 → 状态审批中
  resetCtx('11', {}, { id: 'req-6', name: '测试商品6', code: 'OFFER6', nodeResults: {} });
  QUERY.approval_nodes = JSON.stringify([
    { seq: 1, stage: '需求', node: '产品经理审核', role: '产品经理', status: '已通过', opinion: '同意' },
    { seq: 2, stage: '需求', node: '资费主管审核', role: '资费主管', status: '审批中' },
  ]);
  const vm6 = mountComponent();
  await sleep(200);
  ok(vm6.demandApproval.value.status === '审批中', 'E2: 部分通过时为「审批中」');
  ok(vm6.demandApproval.value.rows[0][4] === '已通过', 'E2: 首节点沿用真实「已通过」');
  ok(vm6.demandApproval.value.rows.length === 2, 'E2: 仅渲染需求阶段真实节点');
  disposeVm();

  // E3 需求阶段全通过（出现上线阶段）→ 已通过
  resetCtx('11', {}, { id: 'req-7', name: '测试商品7', code: 'OFFER7', nodeResults: {} });
  QUERY.approval_nodes = JSON.stringify([
    { seq: 1, stage: '需求', node: '产品经理审核', role: '产品经理', status: '已通过' },
    { seq: 2, stage: '需求', node: '资费主管审核', role: '资费主管', status: '已通过' },
    { seq: 4, stage: '上线', node: '上线审批', role: 'IT支撑', status: '审批中' },
  ]);
  const vm7 = mountComponent();
  await sleep(200);
  ok(vm7.demandApproval.value.status === '已通过', 'E3: 需求全通过（上线已开启）为「已通过」');
  disposeVm();

  // E4 驳回 → 已驳回
  resetCtx('11', {}, { id: 'req-8', name: '测试商品8', code: 'OFFER8', nodeResults: {} });
  QUERY.approval_nodes = JSON.stringify([
    { seq: 1, stage: '需求', node: '产品经理审核', role: '产品经理', status: '已驳回', opinion: '资源不足' },
  ]);
  const vm8 = mountComponent();
  await sleep(200);
  ok(vm8.demandApproval.value.status === '已驳回', 'E4: 驳回节点为「已驳回」');
  ok(vm8.demandApproval.value.note.indexOf('产品经理审核') >= 0, 'E4: 结论条含驳回节点');
  disposeVm();

  console.log('\n结果: ' + pass + ' passed, ' + fail + ' failed');
  process.exit(fail ? 1 : 0);
}

main().catch((e) => { console.error(e); process.exit(1); });
