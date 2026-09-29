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

// GBK encode a string; 0x3F placeholder marks a lost delimiter.
// Reconstruct: if bytes form incomplete UTF-8 seq followed by 0x3F, the 0x3F
// stands for (missing continuation bytes)(delimiter). We restore continuation
// bytes so the char decodes, and keep a single 0x3F marker for the delimiter.
function repair(B) {
  const out = [];
  let i = 0;
  while (i < B.length) {
    const b = B[i];
    if (b < 0x80) { out.push(b); i++; continue; }
    // determine utf8 length
    let len;
    if (b >= 0xf0) len = 4;
    else if (b >= 0xe0) len = 3;
    else if (b >= 0xc0) len = 2;
    else { out.push(0x3f); i++; continue; } // stray continuation
    // gather continuation bytes
    const seq = [b];
    let j = i + 1;
    while (seq.length < len && j < B.length && (B[j] & 0xc0) === 0x80) { seq.push(B[j]); j++; }
    if (seq.length === len) { out.push(...seq); i = j; continue; }
    // incomplete: seq has k bytes, next byte (at j) should be 0x3F placeholder
    // missing cont bytes = len - seq.length
    if (j < B.length && B[j] === 0x3f) {
      // fill continuation bytes for the char (use 0x80 as placeholder to decode)
      const full = seq.slice();
      while (full.length < len) full.push(0x80);
      out.push(...full);
      out.push(0x3f); // delimiter marker
      i = j + 1;
    } else {
      // no placeholder: just push what we have, treat remaining len as missing
      out.push(...seq);
      i = j;
    }
  }
  return Buffer.from(out).toString('utf8');
}

const f = process.argv[2];
const bak = fs.readFileSync(f, 'utf8');
const B = gbkEncode(bak);
const text = repair(B);
fs.writeFileSync(f + '.recon', text, 'utf8');
console.log('written', f + '.recon');
// show sample around a known spot
const lines = text.split('\n');
for (let k = 0; k < lines.length; k++) {
  if (lines[k].includes("0.29")) { console.log(k + 1, JSON.stringify(lines[k])); break; }
}
