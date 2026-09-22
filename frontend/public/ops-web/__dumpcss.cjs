const fs = require('fs');
const h = fs.readFileSync('frontend/public/ops-web/config-workbench.html', 'utf8');
fs.writeFileSync('frontend/public/ops-web/__pp.css', h.slice(2360, 3700));
console.log(fs.readFileSync('frontend/public/ops-web/__pp.css', 'utf8'));
