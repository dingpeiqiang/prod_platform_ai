const fs = require('fs');
const R = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak.recon', 'utf8').split('\n')[35];
let c = 0; const ctx = [];
for (let i = 0; i < R.length; i++) {
  if (R[i] === '?') { c++; if (ctx.length < 50) ctx.push([i, R.slice(i - 18, i + 12)]); }
}
console.log('total ? in recon line36:', c);
ctx.forEach(([i, x]) => console.log(i, JSON.stringify(x)));
// CJK present?
console.log('has CJK:', /[\u4e00-\u9fff]/.test(R));
const cj = R.match(/[\u4e00-\u9fff]+/g);
console.log('CJK runs:', cj ? cj.slice(0, 30) : []);
