const fs = require('fs');
const dec = new TextDecoder('gbk', { fatal: false });
const map = new Map();
for (let hi = 0x81; hi <= 0xfe; hi++) {
  for (let lo = 0x40; lo <= 0xfe; lo++) {
    if (lo === 0x7f) continue;
    const ch = dec.decode(Buffer.from([hi, lo]));
    if (ch.length === 1 && ch !== '\uFFFD' && !map.has(ch)) map.set(ch, [hi, lo]);
  }
}
function gbkEncode(str) {
  const out = [];
  for (const ch of str) {
    const cp = ch.codePointAt(0);
    if (cp < 0x80) { out.push(cp); continue; }
    const b = map.get(ch);
    if (b) out.push(b[0], b[1]); else out.push(0x3f);
  }
  return Buffer.from(out);
}
const s = fs.readFileSync('frontend/public/ops-web/js/wb-context.js', 'utf8');
const rev = gbkEncode(s).toString('utf8');
fs.writeFileSync('rev_wb.txt', rev, 'utf8');
