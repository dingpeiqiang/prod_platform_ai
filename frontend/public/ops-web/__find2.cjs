const fs = require('fs');
const h = fs.readFileSync('frontend/public/ops-web/config-workbench.html', 'utf8');
for (const s of ['config-status-tag', 'config-close-btn', 'config-header-actions', 'statusText', 'statusClass']) {
  let i = 0, n = 0;
  while ((i = h.indexOf(s, i)) !== -1 && n < 6) { console.log('[' + s + ']', i, JSON.stringify(h.slice(i - 50, i + 90))); i++; n++; }
  console.log('---');
}
