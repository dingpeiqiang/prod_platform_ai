const fs = require('fs');
const vm = require('vm');
const src = fs.readFileSync('_chk_vendor-echarts.js', 'utf8');
const lines = src.split('\n');
const header = lines.slice(0, 35).join('\n');
const L = lines[35];
// binary search smallest prefix of L that yields a SyntaxError message != EOF-ish
function test(n) {
  const code = header + '\n' + L.slice(0, n);
  try { new vm.Script(code); return null; }
  catch (e) { return e.message; }
}
// scan in chunks, report first position where message becomes "Unexpected string"
let firstBad = -1;
for (let n = 0; n <= L.length; n += 500) {
  const msg = test(n);
  if (msg && msg === 'Unexpected string') { firstBad = n; break; }
}
console.log('chunk firstBad', firstBad);
if (firstBad >= 0) {
  let lo = Math.max(0, firstBad - 500), hi = firstBad;
  while (lo < hi) {
    const mid = (lo + hi) >> 1;
    if (test(mid) === 'Unexpected string') hi = mid; else lo = mid + 1;
  }
  console.log('exact pos', lo);
  console.log('CTX', JSON.stringify(L.slice(Math.max(0, lo - 120), lo + 40)));
}
