const fs = require('fs');
const h = fs.readFileSync('frontend/public/ops-web/config-workbench.html', 'utf8');
const seg = h.slice(1384100, 1385050);
console.log(seg.replace(/\\r\\n/g, '\n').replace(/\\"/g, '"'));
