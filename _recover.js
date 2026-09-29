const fs = require('fs');
const path = require('path');

// ---- GBK reverse map ----
const dec = new TextDecoder('gbk');
const gbkMap = new Map();
for (let lead = 0x81; lead <= 0xfe; lead++) {
  for (let trail = 0x40; trail <= 0xfe; trail++) {
    if (trail === 0x7f) continue;
    const ch = dec.decode(Buffer.from([lead, trail]));
    if (ch.length === 1 && ch !== '\ufffd' && !gbkMap.has(ch)) gbkMap.set(ch, [lead, trail]);
  }
}
function gbkEncode(str) {
  const out = [];
  for (const ch of str) {
    const cp = ch.codePointAt(0);
    if (cp < 0x80) { out.push(cp); continue; }
    if (ch === '\u20ac') { out.push(0x80); continue; }
    const e = gbkMap.get(ch);
    if (e) out.push(e[0], e[1]); else out.push(0x3f);
  }
  return Buffer.from(out);
}

const utf8dec = new TextDecoder('utf8');

// Marker byte emitted in place of a lost multibyte continuation byte. 0x02 never
// appears in the source text, so it lets the resolver tell a "lost byte" sentinel
// apart from a genuine literal '?' (0x3f) that survived the corruption.
const SENT_BYTE = 0x02;

// ---- Reconstruct: recover lost continuation bytes; keep 0x3F sentinel for lost ascii ----
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
      out.push(SENT_BYTE);
      i = j + 1;
    } else {
      out.push(...seq);
      i = j;
    }
  }
  return { bytes: out, plan };
}

// Score a candidate by unigram weight plus left/right bigram evidence from the
// surrounding text, so context (e.g. 在一二月 / end:"。") breaks ties that raw
// frequency alone cannot.
function scoreCand(ch, freq, prevCh, nextCh) {
  let s = (freq.uni[ch] || 0) + 1;
  if (prevCh) s += (freq.bi[prevCh + ch] || 0) * 6;
  if (nextCh) s += (freq.bi[ch + nextCh] || 0) * 2;
  return s;
}

function resolveChar(headArr, need, freq, prevCh, nextCh) {
  const cands = [];
  const total = need === 1 ? 64 : 64 * 64;
  for (let n = 0; n < total; n++) {
    const bytes = headArr.slice();
    let v = n;
    for (let k = 0; k < need; k++) { bytes.push(0x80 + (v & 0x3f)); v >>= 6; }
    const ch = utf8dec.decode(Buffer.from(bytes));
    if (ch.includes('\ufffd') || ch.length !== 1) continue;
    cands.push({ ch, bytes, score: scoreCand(ch, freq, prevCh, nextCh) });
  }
  if (!cands.length) return null;
  cands.sort((a, b) => b.score - a.score);
  return cands[0];
}

const COMMON = '的一是了我不人在他有这个上们来到时大地为子中你说生国年着就那和要她出也得里后自以会家可下而过天去能对小多然于心学么之都好看起发当没成只如事把还用第样道想作种开美总从无情己面最女但现前些所同日手又行意动方期它头经长儿回位分爱老因很给名法间斯知世什两次使身者被高已亲其进此话常与活正感';
const PUNCT = '：，。（）、——；！？、“”‘’《》·％－　';

function recover(bakPath) {
  const bak = fs.readFileSync(bakPath, 'utf8');
  const B = gbkEncode(bak);
  const { bytes, plan } = reconstruct(B);
  const freq = { uni: {}, bi: {} };
  for (const ch of COMMON) freq.uni[ch] = (freq.uni[ch] || 0) + 10;
  for (const ch of PUNCT) freq.uni[ch] = (freq.uni[ch] || 0) + 30;
  const tmp = utf8dec.decode(Buffer.from(bytes));
  for (const ch of tmp) { const cp = ch.codePointAt(0); if (cp >= 0x2000) freq.uni[ch] = (freq.uni[ch] || 0) + 1; }
  freq.uni['元'] = (freq.uni['元'] || 0) + 50;
  // merge an externally learned corpus (unigram + bigram) if available
  const extPath = path.join(__dirname, '_freq.json');
  if (fs.existsSync(extPath)) {
    const ext = JSON.parse(fs.readFileSync(extPath, 'utf8'));
    for (const [c, n] of Object.entries(ext.uni || {})) freq.uni[c] = (freq.uni[c] || 0) + n;
    Object.assign(freq.bi, ext.bi || {});
  }
  const plans = plan.slice().sort((a, b) => a.pos - b.pos);
  for (const p of plans) {
    const prevCh = [...utf8dec.decode(Buffer.from(bytes.slice(0, p.pos)))].pop() || '';
    const afterPos = p.pos + p.head.length + 1;
    const nextCh = utf8dec.decode(Buffer.from(bytes.slice(afterPos, afterPos + 4)))[0] || '';
    const best = resolveChar(p.head, p.need, freq, prevCh, nextCh);
    if (!best) continue;
    for (let k = 0; k < p.need; k++) bytes[p.pos + p.head.length + k] = best.bytes[p.head.length + k];
  }
  return utf8dec.decode(Buffer.from(bytes));
}

if (require.main === module) {
  const f = process.argv[2];
  const text = recover(f);
  fs.writeFileSync(f + '.recon', text, 'utf8');
  console.log('written', f + '.recon');
}
module.exports = { recover, gbkEncode, reconstruct };
