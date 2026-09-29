const fs = require('fs');
const recon = fs.readFileSync('_chk_vendor-echarts.js', 'utf8');
const ve = fs.readFileSync('_ve543.js', 'utf8');
const m = '!function';
const a0 = recon.indexOf(m), b0 = ve.indexOf(m);
const codeA = recon.slice(a0);
const codeB = ve.slice(b0);
console.log('header:', JSON.stringify(recon.slice(0, a0)));
let i = 0, j = 0;
const blocks = [];
while (i < codeA.length && j < codeB.length) {
  if (codeA[i] === codeB[j]) { i++; j++; continue; }
  let best = null;
  for (let ka = 0; ka <= 10; ka++) for (let kb = 0; kb <= 10; kb++) {
    if (ka === 0 && kb === 0) continue;
    if (i + ka >= codeA.length || j + kb >= codeB.length) continue;
    let ok = true;
    for (let w = 0; w < 8; w++) if (codeA[i + ka + w] !== codeB[j + kb + w]) { ok = false; break; }
    if (ok) { const c = ka + kb; if (!best || c < best.cost) best = { ka, kb, cost: c }; }
  }
  if (!best) best = { ka: 1, kb: 1, cost: 2 };
  blocks.push({ ai: i, A: codeA.slice(i, i + best.ka), B: codeB.slice(j, j + best.kb) });
  i += best.ka; j += best.kb;
}
console.log('remaining diff blocks:', blocks.length);
const agg = new Map();
for (const bl of blocks) { const k = JSON.stringify(bl.A) + ' -> ' + JSON.stringify(bl.B); agg.set(k, (agg.get(k) || 0) + 1); }
console.log([...agg.entries()].sort((x, y) => y[1] - x[1]).map(([k, c]) => c + '  ' + k).join('\n'));
