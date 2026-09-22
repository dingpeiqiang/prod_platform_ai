const fs = require('fs');
const h = fs.readFileSync('frontend/public/ops-web/config-workbench.html', 'utf8');
const keys = ['productMenuOpen', 'toggleProductMenu', 'onSelectProduct', 'onDocClick', 'activeProductId', 'products,', '__returned__'];
for (const k of keys) {
  const re = new RegExp(k.replace(/[,]/g, '\\,'), 'g');
  let m, n = 0;
  console.log('--- ' + k + ' ---');
  while ((m = re.exec(h)) && n < 12) { console.log(m.index, JSON.stringify(h.slice(m.index - 50, m.index + 60))); n++; }
}
