const fs = require('fs');

const dec = new TextDecoder('gbk');
const enc = new Map();
for (let lead = 0x81; lead <= 0xfe; lead++) {
  for (let trail = 0x40; trail <= 0xfe; trail++) {
    if (trail === 0x7f) continue;
    const ch = dec.decode(Buffer.from([lead, trail]));
    if (ch.length === 1 && ch !== '\ufffd' && !enc.has(ch)) enc.set(ch, [lead, trail]);
  }
}

function gbkEncode(str) {
  const out = [];
  for (const ch of str) {
    const cp = ch.codePointAt(0);
    if (cp < 0x80) { out.push(cp); continue; }
    const e = enc.get(ch);
    if (e) out.push(e[0], e[1]);
    else out.push(0x3f);
  }
  return Buffer.from(out);
}

const f = process.argv[2];
const bak = fs.readFileSync(f, 'utf8');
const bytes = gbkEncode(bak);
const s = bytes.toString('latin1');
const needle = process.argv[3] || '0.29';
const idx = s.indexOf(needle);
console.log('idx', idx);
console.log([...bytes.slice(idx - 12, idx + 8)].map(b => b.toString(16)));
console.log(JSON.stringify(s.slice(idx - 12, idx + 8)));
