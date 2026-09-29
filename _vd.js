const fs = require('fs');
const R = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak.recon', 'utf8').split('\n')[35];
const needles = ['pt(o)||pt(r)', 'var n=cr[0]<0', "Can' use", 'Can'];
for (const nd of needles) {
  let i = R.indexOf(nd);
  while (i >= 0) { console.log(nd, i, JSON.stringify(R.slice(i - 20, i + 30))); i = R.indexOf(nd, i + 1); }
}
// show all '?' with context in R that are near quotes
let c = 0;
for (let i = 0; i < R.length && c < 40; i++) {
  if (R[i] === "'") { console.log('SQ', i, JSON.stringify(R.slice(i - 25, i + 25))); c++; }
}
console.log('total sq in recon', (R.match(/'/g) || []).length);
