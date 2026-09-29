const fs = require('fs');
const s = fs.readFileSync('frontend/public/ops-web/js/app-config-workbench.js', 'utf8');
const marker = 'ConfigWorkbench.template = ';
const idx = s.indexOf(marker);
const start = idx + marker.length;
let i = start + 1, out = '';
while (true) {
  const ch = s[i];
  if (ch === '\\') { out += s[i] + s[i + 1]; i += 2; continue; }
  if (ch === '"') break;
  out += ch; i++;
}
const tpl = JSON.parse('"' + out + '"');
fs.writeFileSync('_tpl.html', tpl, 'utf8');
console.log('tpl len', tpl.length, 'FFFD', (tpl.match(/\uFFFD/g) || []).length);
console.log('backticks', (tpl.match(/`/g) || []).length);
console.log('dollarbrace', (tpl.match(/\$\{/g) || []).length);
