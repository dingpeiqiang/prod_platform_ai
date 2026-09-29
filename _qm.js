const fs = require('fs');
const bak = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak');
const pat = Buffer.from(',&');
let idx = bak.indexOf(pat);
while (idx >= 0 && bak[idx + 2] !== 0x3f) idx = bak.indexOf(pat, idx + 1);
console.log('byte ctx:', JSON.stringify(bak.slice(idx - 8, idx + 10).toString('latin1')));
console.log('hex:', bak.slice(idx, idx + 8).toString('hex'));
