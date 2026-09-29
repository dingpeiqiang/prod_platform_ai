const fs = require('fs');
const R = require('./_resolve.js');
const txt = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const i = txt.indexOf('months');
const line = txt.slice(txt.lastIndexOf('\n', i) + 1, txt.indexOf('\n', i));
console.log('IN :', JSON.stringify(line));
console.log('OUT:', JSON.stringify(R.resolve(line)));
