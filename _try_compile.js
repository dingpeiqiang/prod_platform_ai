const fs = require('fs');
const { Script } = require('vm');
const doc = {
  documentElement: { setAttribute() {} },
  createElement(tag) {
    return { tagName: tag, style: {}, setAttribute() {}, appendChild() {}, children: [] };
  },
  createTextNode(t) { return { nodeValue: t }; },
  querySelector() { return null; },
  querySelectorAll() { return []; },
  getElementById() { return null; },
};
const ctx = {};
ctx.window = ctx; ctx.self = ctx; ctx.globalThis = ctx; ctx.document = doc;
ctx.navigator = { userAgent: 'node' };
ctx.URLSearchParams = require('url').URLSearchParams;
ctx.console = console;
ctx.location = { pathname: '/', search: '?', href: 'http://x/' };
ctx.setTimeout = setTimeout; ctx.clearTimeout = clearTimeout; ctx.Promise = Promise;
ctx.Element = function(){};
new Script(fs.readFileSync('frontend/public/ops-web/js/vendor-vue.js', 'utf8')).runInNewContext(ctx);
console.log('Vue?', typeof ctx.Vue, 'compile?', ctx.Vue && typeof ctx.Vue.compile);
const tpl = fs.readFileSync('_tpl.html', 'utf8');
try {
  const render = ctx.Vue.compile(tpl);
  console.log('ConfigWorkbench template compiles OK');
} catch (e) {
  console.log('COMPILE FAIL:', e.message);
}
