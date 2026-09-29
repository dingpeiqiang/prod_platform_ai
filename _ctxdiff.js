const fs = require('fs');
const recon = fs.readFileSync('_chk_vendor-echarts.js', 'utf8');
const truth = fs.readFileSync('_ve543.js', 'utf8');
const m = '!function';
const codeA = recon.slice(recon.indexOf(m));
const codeB = truth.slice(truth.indexOf(m));
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
for (const bl of blocks) {
  const ctx = codeA.slice(Math.max(0, bl.ai - 34), bl.ai) + '[' + bl.A + ']' + codeA.slice(bl.ai + bl.A.length, bl.ai + bl.A.length + 24);
  console.log('A=' + JSON.stringify(bl.A) + ' B=' + JSON.stringify(bl.B) + '  CTX ' + JSON.stringify(ctx));
}
