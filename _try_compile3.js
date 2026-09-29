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

function compileAll(file) {
  const s = fs.readFileSync(file, 'utf8');
  // backtick template literals
  let n = 0;
  for (const m of s.matchAll(/template:\s*`([\s\S]*?)`\s*,/g)) {
    n++;
    const t = m[1];
    try { Vue.compile(t); } catch (e) { console.log('  [backtick] FAIL len', t.length, '::', e.message); }
  }
  // assigned .template = "..." (double-quoted json-ish)
  let idx = 0;
  while ((idx = s.indexOf('.template = "', idx)) !== -1) {
    let i = s.indexOf('"', idx + 13) + 1, out = '';
    const startCh = s.indexOf('"', idx + 13) + 1;
    i = startCh;
    while (true) { const ch = s[i]; if (ch === '\\') { out += s[i] + s[i+1]; i += 2; continue; } if (ch === '"') break; out += ch; i++; }
    let t; try { t = JSON.parse('"' + out + '"'); } catch (e) { console.log('  [assigned] JSON FAIL', e.message); idx = i; continue; }
    n++;
    try { Vue.compile(t); console.log('  [assigned] OK len', t.length); } catch (e) { console.log('  [assigned] FAIL len', t.length, '::', e.message); }
    idx = i;
  }
  console.log(file, '=> templates checked:', n);
}
compileAll('frontend/public/ops-web/js/app-config-workbench.js');
compileAll('frontend/public/ops-web/js/data-presets.js');
compileAll('frontend/public/ops-web/js/ops-view.js');
