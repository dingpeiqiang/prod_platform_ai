const fs = require('fs');
const h = fs.readFileSync('frontend/public/ops-web/config-workbench.html', 'utf8');
console.log(JSON.stringify(h.slice(3390, 3900)));
