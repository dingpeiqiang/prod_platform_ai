const fs = require('fs');
const { Script } = require('vm');
const doc = {
  documentElement: { setAttribute() {} },
  createElement(tag) { return { tagName: tag, style: {}, setAttribute() {}, appendChild() {}, children: [] }; },
  createTextNode(t) { return { nodeValue: t }; },
  querySelector() { return null; }, querySelectorAll() { return []; }, getElementById() { return null; },
};
const ctx = {};
ctx.window = ctx; ctx.self = ctx; ctx.globalThis = ctx; ctx.document = doc;
ctx.navigator = { userAgent: 'node' }; ctx.URLSearchParams = require('url').URLSearchParams;
ctx.console = console; ctx.location = { pathname: '/', search: '?', href: 'http://x/' };
ctx.setTimeout = setTimeout; ctx.clearTimeout = clearTimeout; ctx.Promise = Promise; ctx.Element = function(){};
new Script(fs.readFileSync('frontend/public/ops-web/js/vendor-vue.js', 'utf8')).runInNewContext(ctx);

const src = fs.readFileSync('frontend/public/ops-web/js/app-main.js', 'utf8');
// grab both template literals
const tpls = [...src.matchAll(/template:\s*`([\s\S]*?)`\s*,/g)].map(m => m[1]);
console.log('found templates:', tpls.length);
tpls.forEach((t, i) => {
  try { ctx.Vue.compile(t); console.log('template', i, 'OK, len', t.length); }
  catch (e) { console.log('template', i, 'FAIL:', e.message); }
});
