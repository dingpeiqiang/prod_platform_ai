const fs = require('fs');
const recon = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const i = recon.indexOf('月收元?+');
const line = recon.slice(recon.lastIndexOf('\n', i) + 1, recon.indexOf('\n', i));
const col = line.indexOf('月收元?+');
const before = line.slice(0, col);
console.log('col', col, 'sq before', (before.match(/'/g) || []).length, 'dq before', (before.match(/"/g) || []).length);
console.log(JSON.stringify(before.slice(-60)));
