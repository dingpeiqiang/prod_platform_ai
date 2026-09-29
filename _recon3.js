const fs = require('fs');
const path = require('path');

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

const utf8dec = new TextDecoder('utf8');

// Walk the gbk-encoded bytes. Reconstruct original utf8 bytes.
// Where an incomplete utf8 sequence is followed by 0x3F (the collapsed
// pair), we know one continuation byte + one delimiter byte were lost.
// Emit the missing continuation byte (chosen by candidate freqs) followed
// by a sentinel 0x3F marker that the caller resolves to a delimiter.
function reconstruct(B, freq) {
  const out = [];
  const plan = []; // {index, candidates}
  let i = 0;
  while (i < B.length) {
    const b = B[i];
    if (b < 0x80) { out.push(b); i++; continue; }
    if (b < 0xc0) { out.push(b); i++; continue; } // stray continuation (shouldn't happen)
    let len;
    if (b >= 0xf0) len = 4;
    else if (b >= 0xe0) len = 3;
    else len = 2;
    const seq = [b];
    let j = i + 1;
    while (seq.length < len && j < B.length && (B[j] & 0xc0) === 0x80) { seq.push(B[j]); j++; }
    if (seq.length === len) { out.push(...seq); i = j; continue; }
    // incomplete seq followed by 0x3f?
    if (j < B.length && B[j] === 0x3f) {
      const need = len - seq.length; // number of missing continuation bytes
      // The LEAD bytes we have decode to a 2-byte-mojibake; original lost the
      // tail. For simplicity handle need===1 (most common: 3-byte char lost last).
      const pos = out.length;
      out.push(...seq);
      for (let k = 0; k < need; k++) out.push(0x80); // placeholder
      plan.push({ pos, len, head: seq.slice(), need });
      out.push(0x3f); // delimiter marker
      i = j + 1;
    } else {
      out.push(...seq);
      i = j;
    }
  }
  return { bytes: out, plan };
}

function resolveFreq(headArr, need, freq) {
  // build candidate byte suffixes for the missing continuation bytes
  // brute force over 64^need (need is 1..2)
  const base = headArr.slice();
  const cands = [];
  const total = need === 1 ? 64 : 64 * 64;
  for (let n = 0; n < total; n++) {
    const bytes = base.slice();
    let v = n;
    for (let k = 0; k < need; k++) { bytes.push(0x80 + (v & 0x3f)); v >>= 6; }
    const ch = utf8dec.decode(Buffer.from(bytes));
    if (ch.includes('\ufffd')) continue;
    const score = freq[ch] || 0;
    cands.push({ ch, bytes, score });
  }
  cands.sort((a, b) => b.score - a.score);
  return cands[0];
}

function main(bakPath) {
  const bak = fs.readFileSync(bakPath, 'utf8');
  const B = gbkEncode(bak);
  const { bytes, plan } = reconstruct(B, {});
  if (process.env.DBG) console.error('plan count', plan.length, 'first', plan[0]);

  // build freq from fully-decoded chars
  const freq = {};
  const solid =[];
  // first pass: decode bytes replacing plan placeholders; collect chars not from plan
  const planPos = new Set(plan.map(p => p.pos));
  for (let idx = 0; idx < bytes.length; idx++) {
    if (bytes[idx] < 0x80) continue;
  }
  // Simple: decode whole with placeholders, count CJK chars
  const tmp = utf8dec.decode(Buffer.from(bytes));
  for (const ch of tmp) {
    const cp = ch.codePointAt(0);
    if (cp >= 0x4e00 && cp <= 0x9fff) freq[ch] = (freq[ch] || 0) + 1;
  }
  // resolve each plan
  for (const p of plan) {
    const best = resolveFreq(p.head, p.need, freq);
    if (process.env.DBG && p.pos === 106) console.error('DBG pos106 best', JSON.stringify(best), 'cp', best.ch.codePointAt(0).toString(16));
    for (let k = 0; k < p.need; k++) bytes[p.pos + p.head.length + k] = best.bytes[p.head.length + k];
  }
  const text = utf8dec.decode(Buffer.from(bytes));
  return text;
}

const f = process.argv[2];
const text = main(f);
fs.writeFileSync(f + '.recon', text, 'utf8');
const lines = text.split('\n');
const qi = lines.findIndex(l => l.includes('0.29'));
if (qi >= 0) console.log(qi + 1, JSON.stringify(lines[qi]));
const qi2 = lines.findIndex(l => l.includes('套外流量'));
if (qi2 >= 0) console.log(qi2 + 1, JSON.stringify(lines[qi2]));
