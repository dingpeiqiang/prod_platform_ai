const { gbkEncode, reconstruct } = require('./_recover.js');
const fs = require('fs');
// find the month-array region in bak bytes
const bak = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak');
const B = gbkEncode(bak.toString('utf8'));
const { bytes, plan } = reconstruct(B);
const utf8dec = new TextDecoder('utf8');
// locate plans whose head decodes near '月' context: look for plan around byte pos of "一月"
// find index of the substring in recon
const recon = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak.recon', 'utf8');
const idx = recon.indexOf('一最?"二月"');
console.log('recon idx', idx, JSON.stringify(recon.slice(idx - 10, idx + 40)));
// plans near that position: pos is in bytes array
// bytes position ~ idx (ascii mostly)
for (const p of plan) {
  const headStr = utf8dec.decode(Buffer.from(p.head));
  if (p.head[0] === 0xe6 && p.head[1] === 0x9c) {
    // candidate for 月/最
    const cands = [];
    const total = p.need === 1 ? 64 : 64 * 64;
    for (let n = 0; n < total; n++) {
      const bs = p.head.slice(); let v = n;
      for (let k = 0; k < p.need; k++) { bs.push(0x80 + (v & 0x3f)); v >>= 6; }
      const ch = utf8dec.decode(Buffer.from(bs));
      if (!ch.includes('\ufffd') && ch.length === 1) cands.push(ch);
    }
    console.log('plan head', p.head.map(x => x.toString(16)), 'pos', p.pos, 'need', p.need, 'cands:', cands.join(''));
    break;
  }
}
