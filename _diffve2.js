const fs = require('fs');
const recon = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak.recon', 'utf8');
const ve = fs.readFileSync('_ve543.js', 'utf8');
const m = '!function';
const a = recon.slice(recon.indexOf(m));
const b = ve.slice(ve.indexOf(m));
console.log('len a', a.length, 'len b', b.length);

// Walk; when mismatch, find so that a[i+da]==b[j] and a[i]==b[j+db] minimized
let i = 0, j = 0;
const blocks = [];
while (i < a.length && j < b.length) {
  if (a[i] === b[j]) { i++; j++; continue; }
  // find resync: smallest k such that a[i+k]===b[j+k] over next few, or a[i+k]==b[j] handled
  // search for the offset (ka,kb) with ka,kb in 0..8 (not both 0) minimizing ka+kb and matching a window
  let best = null;
  for (let ka = 0; ka <= 8; ka++) {
    for (let kb = 0; kb <= 8; kb++) {
      if (ka === 0 && kb === 0) continue;
      if (i + ka >= a.length || j + kb >= b.length) continue;
      // require next 6 chars equal
      let ok = true;
      for (let w = 0; w < 6; w++) {
        if (a[i + ka + w] !== b[j + kb + w]) { ok = false; break; }
      }
      if (ok) { const cost = ka + kb; if (!best || cost < best.cost) best = { ka, kb, cost }; }
    }
  }
  if (!best) best = { ka: 1, kb: 1, cost: 2 };
  blocks.push({ ai: i, A: a.slice(i, i + best.ka), B: b.slice(j, j + best.kb) });
  i += best.ka; j += best.kb;
}
console.log('blocks:', blocks.length);
// aggregate distinct A->B
const agg = new Map();
for (const bl of blocks) {
  const key = bl.A + '\u0000' + bl.B;
  agg.set(key, (agg.get(key) || 0) + 1);
}
const arr = [...agg.entries()].sort((x, y) => y[1] - x[1]);
console.log('distinct', arr.length);
console.log(arr.slice(0, 60).map(([k, c]) => c + '  ' + JSON.stringify(k.split('\u0000')[0] + ' -> ' + k.split('\u0000')[1])).join('\n'));
fs.writeFileSync('_ve_blocks.txt', blocks.map((bl, n) => n + ' @a' + bl.ai + ' A=' + JSON.stringify(bl.A) + ' B=' + JSON.stringify(bl.B)).join('\n'), 'utf8');
fs.writeFileSync('_ve_agg.txt', arr.map(([k, c]) => c + '\t' + JSON.stringify(k.split('\u0000')[0]) + '\t' + JSON.stringify(k.split('\u0000')[1])).join('\n'), 'utf8');
