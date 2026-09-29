const fs = require('fs');
const t = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const i = t.indexOf('series:[lineSeries');
const ln = t.slice(t.lastIndexOf('\n', i) + 1, t.indexOf('\n', i));
console.log('RAW:', JSON.stringify(Array.from(ln).slice(0, 20).join('')));
const ch = Array.from(ln);
const j = ch.findIndex(c => c === '利');
for (let k = j; k < j + 16; k++) console.log(k - j, JSON.stringify(ch[k]), ch[k].codePointAt(0).toString(16));
console.log('RESOLVED LINE:', JSON.stringify(require('./_resolve.js').resolve(ln)));
