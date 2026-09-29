const fs = require('fs');

const gbkDecoder = new TextDecoder('gbk');
let reverse = null;
function buildTable() {
  reverse = new Map();
  for (let lead = 0x81; lead <= 0xfe; lead++) {
    for (let trail = 0x40; trail <= 0xfe; trail++) {
      if (trail === 0x7f) continue;
      const ch = gbkDecoder.decode(Buffer.from([lead, trail]));
      if (ch === '\uFFFD' || ch.length !== 1) continue;
      if (!reverse.has(ch)) reverse.set(ch, Buffer.from([lead, trail]));
    }
  }
}
function encodeGBK(str) {
  if (!reverse) buildTable();
  const out = [];
  for (const ch of str) {
    const c = ch.codePointAt(0);
    if (c < 0x80) out.push(Buffer.from([c]));
    else {
      const b = reverse.get(ch);
      if (!b) throw new Error('no gbk map for U+' + c.toString(16));
      out.push(b);
    }
  }
  return Buffer.concat(out);
}

function roundtrip(line) {
  const bytes = encodeGBK(line);
  return bytes; // still contains 0x3F where byte lost
}

module.exports = { encodeGBK };

if (require.main === module) {
  const t = fs.readFileSync(process.argv[2], 'utf8');
  const lines = t.split('\n');
  const target = parseInt(process.argv[3], 10);
  const l = lines[target - 1];
  console.log('orig moji:', JSON.stringify(l));
  const bytes = roundtrip(l);
  console.log('gbk bytes:', bytes.toString('hex').match(/../g).join(' '));
  console.log('as utf8 :', JSON.stringify(bytes.toString('utf8')));
}
