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
const Vue = ctx.Vue;

const tpl = fs.readFileSync('_tpl.html', 'utf8');
const out = Vue.compile(tpl, { mode: 'function' });
console.log('keys:', Object.keys(out));
const code = out.code || (out.render && out.render.toString());
fs.writeFileSync('_gen_render.js', code, 'utf8');
console.log('generated len', code.length);
try { new Function(code); console.log('new Function OK'); }
catch (e) { console.log('new Function FAIL:', e.message); }
