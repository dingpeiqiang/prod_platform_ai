// Minimal GBK (cp936) encoder using Node's ICU TextDecoder to build a reverse table.
const gbkDecoder = new TextDecoder('gbk');

let reverse = null;
function buildTable() {
  reverse = new Map();
  for (let lead = 0x81; lead <= 0xfe; lead++) {
    for (let trail = 0x40; trail <= 0xfe; trail++) {
      if (trail === 0x7f) continue;
      const bytes = Buffer.from([lead, trail]);
      const ch = gbkDecoder.decode(bytes);
      // skip replacement char / multi-char results
      if (ch === '\uFFFD' || ch.length !== 1) continue;
      if (!reverse.has(ch)) reverse.set(ch, bytes);
    }
  }
}

function encodeGBK(str) {
  if (!reverse) buildTable();
  const out = [];
  for (const ch of str) {
    const c = ch.codePointAt(0);
    if (c < 0x80) {
      out.push(Buffer.from([c]));
    } else {
      const b = reverse.get(ch);
      if (b) out.push(b);
      else throw new Error(`no gbk mapping for ${ch} U+${c.toString(16)}`);
    }
  }
  return Buffer.concat(out);
}

module.exports = { encodeGBK, buildTable };

if (require.main === module) {
  const s = process.argv[2];
  const buf = encodeGBK(s);
  console.log('gbk hex:', buf.toString('hex'));
  console.log('as utf8:', buf.toString('utf8'));
}
