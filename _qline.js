const fs = require('fs');
const t = fs.readFileSync('frontend/public/ops-web/js/data-presets.js.bak', 'utf8');
const lines = t.split('\n');
const pats = ['?]', '? ', '?"', '?/', '?,', '?)', '?.', '?|'];
const out = [];
for (let i = 0; i < lines.length; i++) {
  for (const p of pats) {
    if (lines[i].includes(p)) { out.push((i + 1) + ' ' + JSON.stringify(lines[i])); break; }
  }
  if (out.length > 12) break;
}
fs.writeFileSync('_qlines.txt', out.join('\n'), 'utf8');
console.log(out.join('\n'));
