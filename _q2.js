const fs = require('fs');
const t = fs.readFileSync('frontend/public/ops-web/js/data-presets.js.bak.recon', 'utf8');
const lines = t.split('\n');
const wanted = ['?0', '?9', '?]', '? ', '?M', '?G', '?*', '?/'];
const seen = new Set();
for (let i = 0; i < lines.length && seen.size < wanted.length; i++) {
  for (const w of wanted) {
    if (seen.has(w)) continue;
    if (lines[i].includes(w)) { seen.add(w); console.log(w, '=>', (i + 1), JSON.stringify(lines[i])); }
  }
}
