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

const utf8dec = new TextDecoder('utf8', { fatal: false });

function reconstruct(B) {
  const out = [];
  const plan = [];
  let i = 0;
  while (i < B.length) {
    const b = B[i];
    if (b < 0xc0) { out.push(b); i++; continue; }
    let len;
    if (b >= 0xf0) len = 4;
    else if (b >= 0xe0) len = 3;
    else len = 2;
    const seq = [b];
    let j = i + 1;
    while (seq.length < len && j < B.length && (B[j] & 0xc0) === 0x80) { seq.push(B[j]); j++; }
    if (seq.length === len) { out.push(...seq); i = j; continue; }
    if (j < B.length && B[j] === 0x3f) {
      const need = len - seq.length;
      const pos = out.length;
      out.push(...seq);
      for (let k = 0; k < need; k++) out.push(0x80);
      plan.push({ pos, head: seq.slice(), need });
      out.push(0x3f); // delimiter sentinel
      i = j + 1;
    } else {
      out.push(...seq);
      i = j;
    }
  }
  return { bytes: out, plan };
}

function resolve(headArr, need, freq) {
  const cands = [];
  const total = need === 1 ? 64 : 64 * 64;
  for (let n = 0; n < total; n++) {
    const bytes = headArr.slice();
    let v = n;
    for (let k = 0; k < need; k++) { bytes.push(0x80 + (v & 0x3f)); v >>= 6; }
    const ch = utf8dec.decode(Buffer.from(bytes));
    if (ch.includes('\ufffd') || ch.length !== 1) continue;
    cands.push({ ch, bytes, score: freq[ch] || 0 });
  }
  if (!cands.length) return null;
  cands.sort((a, b) => b.score - a.score);
  return cands[0];
}

function build(bakPath, freqTable) {
  const bak = fs.readFileSync(bakPath, 'utf8');
  const B = gbkEncode(bak);
  const { bytes, plan } = reconstruct(B);
  const tmp = utf8dec.decode(Buffer.from(bytes));
  const freq = Object.assign({}, freqTable);
  for (const ch of tmp) { const cp = ch.codePointAt(0); if (cp >= 0x4e00 && cp <= 0x9fff) freq[ch] = (freq[ch] || 0) + 5; }
  for (const p of plan) {
    const best = resolve(p.head, p.need, freq);
    if (!best) continue;
    for (let k = 0; k < p.need; k++) bytes[p.pos + p.head.length + k] = best.bytes[p.head.length + k];
  }
  return utf8dec.decode(Buffer.from(bytes));
}

if (require.main === module) {
  const f = process.argv[2];
  const text = build(f, {});
  fs.writeFileSync(f + '.recon', text, 'utf8');
  const lines = text.split('\n');
  for (const i of [21, 31, 50, 99, 230, 633]) console.log((i + 1) + ' ' + lines[i]);
}
module.exports = { build, gbkEncode, reconstruct };
