const fs = require('fs');
const R = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak.recon', 'utf8').split('\n')[45];
const E = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak', 'utf8');
let i = R.indexOf('_showIndicator(l,l,');
console.log('recon:', i, JSON.stringify(R.slice(i, i + 60)));
// find in bak the byte sequence
let j = E.indexOf('_showIndicator');
while (j >= 0) { console.log('bak:', j, JSON.stringify(E.slice(j, j + 70))); j = E.indexOf('_showIndicator', j + 1); }
