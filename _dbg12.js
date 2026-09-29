const fs = require('fs');
const txt = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const line = txt.split('\n')[2];
const chars = Array.from(line);
const i = chars.findIndex((c, k) => chars.slice(k, k + 6).join('') === 'months');
const seg = chars.slice(i + 10, i + 22);
console.log(seg.map(c => JSON.stringify(c) + ':' + c.codePointAt(0).toString(16)).join(' '));
