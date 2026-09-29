const fs = require('fs');
const R = require('./_resolve.js');
const txt = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const line = txt.split('\n')[2];
const out = R.resolve(line);
const i = out.indexOf('months');
const seg = out.slice(i, i + 30);
console.log([...seg].map(c => JSON.stringify(c) + ':' + c.codePointAt(0).toString(16)).join(' '));
