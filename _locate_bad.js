const fs = require('fs');
const compiler = require('./frontend/node_modules/@vue/compiler-dom');

const marker = 'ConfigWorkbench.template = ';
const s = fs.readFileSync('frontend/public/ops-web/js/app-config-workbench.js', 'utf8');
const start = s.indexOf(marker) + marker.length;
let i = start + 1, out = '';
while (true) { const ch = s[i]; if (ch === '\\') { out += s[i] + s[i + 1]; i += 2; continue; } if (ch === '"') break; out += ch; i++; }
const cw = JSON.parse('"' + out + '"');

const outCode = compiler.compile(cw, { mode: 'function' }).code;
fs.writeFileSync('_badrender.js', outCode, 'utf8');
console.log('render code len', outCode.length);

try {
  new Function(outCode);
  console.log('OK');
} catch (e) {
  console.log('FAIL:', e.message);
  console.log(e.stack.split('\n').slice(0, 6).join('\n'));
  // find via eval with line info: use vm with filename
  const vm = require('vm');
  try {
    new vm.Script(outCode, { filename: 'badrender.js' });
  } catch (e2) {
    console.log('VM:', e2.message);
    const m = /badrender\.js:(\d+)/.exec(e2.stack) || /<anonymous>:(\d+)/.exec(e2.stack);
    if (m) {
      const ln = +m[1];
      const lines = outCode.split('\n');
      for (let k = Math.max(0, ln - 3); k < Math.min(lines.length, ln + 2); k++) {
        console.log((k + 1) + ': ' + lines[k]);
      }
    }
  }
}
