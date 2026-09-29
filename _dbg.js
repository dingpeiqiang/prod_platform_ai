const fs = require('fs');
const { gbkEncode, reconstruct } = require('./_recover.js');
const f = process.argv[2];
const needle = process.argv[3];
const bak = fs.readFileSync(f, 'utf8');
const B = gbkEncode(bak);
const s = B.toString('latin1');
const idx = s.indexOf(needle);
console.log('idx', idx);
console.log('bytes', [...B.slice(idx, idx + 30)].map(b => b.toString(16)));
const { bytes, plan } = reconstruct(B);
// find plan entries near idx
for (const p of plan) {
  if (p.pos >= idx - 5 && p.pos <= idx + 30) {
    console.log('plan pos', p.pos, 'head', p.head.map(b => b.toString(16)), 'need', p.need);
  }
}
