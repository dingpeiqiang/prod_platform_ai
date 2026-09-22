const fs = require('fs');
const h = fs.readFileSync('frontend/public/ops-web/config-workbench.html', 'utf8');
const start = h.indexOf('<div class=\\"config-header-actions\\">');
console.log(JSON.stringify(h.slice(start, start + 900)));
