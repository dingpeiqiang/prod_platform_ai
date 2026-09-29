const fs = require('fs');
const bak = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak');
const end = bak.indexOf(Buffer.from('Licensed to the Apache'));
const head = bak.slice(0, end);
console.log('head bytes len', head.length);
// gbk-encode head then utf8-decode (reverse), like recover
const dec = new TextDecoder('gbk');
const map = new Map();
for (let lead = 0x81; lead <= 0xfe; lead++) for (let trail = 0x40; trail <= 0xfe; trail++) {
  if (trail === 0x7f) continue;
  const ch = dec.decode(Buffer.from([lead, trail]));
  if (ch.length === 1 && ch !== '\ufffd' && !map.has(ch)) map.set(ch, [lead, trail]);
}
const txt = bak.toString('utf8');
const out = [];
for (const ch of txt.slice(0, end)) {
  const cp = ch.codePointAt(0);
  if (cp < 0x80) out.push(cp);
  else if (ch === '\u20ac') out.push(0x80);
  else { const e = map.get(ch); if (e) out.push(e[0], e[1]); else out.push(0x3f); }
}
const B = Buffer.from(out);
console.log('reverse utf8:', JSON.stringify(new TextDecoder('utf8').decode(B)));
console.log('raw hex:', B.slice(0, 120).toString('hex'));
