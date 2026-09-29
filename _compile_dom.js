const fs = require('fs');
const compiler = require('./frontend/node_modules/@vue/compiler-dom');

function check(name, tpl) {
  const errs = [];
  let out;
  try {
    out = compiler.compile(tpl, {
      mode: 'function',
      onError: (e) => errs.push(e),
      onWarn: (e) => errs.push(e),
    });
  } catch (e) {
    console.log(name, 'THROW:', e.message);
    return;
  }
  if (errs.length) {
    errs.forEach(e => console.log(name, 'ERR:', e.message, '@', JSON.stringify(e.loc && e.loc.start)));
    return;
  }
  // try new Function on generated code
  try {
    new Function('Vue', out.code);
    console.log(name, 'OK (compiled+newFunction) len', out.code.length);
  } catch (e) {
    console.log(name, 'newFunction FAIL:', e.message);
    fs.writeFileSync('_bad_' + name.replace(/\W/g, '') + '.js', out.code, 'utf8');
    // locate
    const m = e.stack.match(/<anonymous>:(\d+):(\d+)/);
    if (m) {
      const lines = out.code.split('\n');
      const ln = parseInt(m[1], 10);
      console.log('  at generated line', ln, ':', JSON.stringify(lines[ln - 1] && lines[ln - 1].slice(0, 200)));
    }
  }
}

// ConfigWorkbench template
const marker = 'ConfigWorkbench.template = ';
const s = fs.readFileSync('frontend/public/ops-web/js/app-config-workbench.js', 'utf8');
const start = s.indexOf(marker) + marker.length;
let i = start + 1, out = '';
while (true) { const ch = s[i]; if (ch === '\\') { out += s[i] + s[i + 1]; i += 2; continue; } if (ch === '"') break; out += ch; i++; }
const cw = JSON.parse('"' + out + '"');
check('ConfigWorkbench', cw);

// app-main templates
const am = fs.readFileSync('frontend/public/ops-web/js/app-main.js', 'utf8');
[...am.matchAll(/template:\s*`([\s\S]*?)`\s*,/g)].forEach((m, idx) => check('app-main-' + idx, m[1]));
