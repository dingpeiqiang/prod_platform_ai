const fs = require('fs');
const recon = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak.recon', 'utf8');
const ve = fs.readFileSync('_ve543.js', 'utf8');
const m = '!function';
const a = recon.slice(recon.indexOf(m));
const b = ve.slice(ve.indexOf(m));
console.log('len recon', a.length, 'len ve', b.length, 'diff', a.length - b.length);
// find all divergence points
let i = 0, j = 0;
const diffs = [];
while (i < a.length && j < b.length) {
  if (a[i] === b[j]) { i++; j++; continue; }
  // record context
  diffs.push({ ai: i, ctxA: a.slice(i - 20, i + 30), ctxB: b.slice(j - 20, j + 30) });
  // try to resync: find b[j] in a within 6
  let found = -1;
  for (let d = 1; d <= 6 && i + d < a.length; d++) { if (a[i + d] === b[j]) { found = d; break; } }
  if (found > 0) { i += found; }
  else {
    let foundB = -1;
    for (let d = 1; d <= 6 && j + d < b.length; d++) { if (b[j + d] === a[i]) { foundB = d; break; } }
    if (foundB > 0) { j += foundB; } else { i++; j++; }
  }
}
console.log('total diff points:', diffs.length);
fs.writeFileSync('_ve_diffs.txt', diffs.map((d, n) => n + ' @a' + d.ai + '\n  A: ' + JSON.stringify(d.ctxA) + '\n  B: ' + JSON.stringify(d.ctxB)).join('\n'), 'utf8');
console.log(diffs.slice(0, 40).map((d, n) => n + ' A:' + JSON.stringify(d.ctxA) + ' B:' + JSON.stringify(d.ctxB)).join('\n'));
