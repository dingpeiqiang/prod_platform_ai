const fs = require('fs');
for (const f of ['ops-view', 'data-presets', 'wb-context', 'app-config-workbench']) {
  const s = fs.readFileSync('_chk_' + f + '.js', 'utf8');
  const lines = s.split('\n');
  const odd = [];
  lines.forEach((l, n) => { const q = (l.match(/'/g) || []).length; if (q % 2) odd.push(n + 1); });
  console.log('=== ' + f + ' oddQuoteLines:', JSON.stringify(odd));
}
