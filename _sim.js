const clean = '  // 对话式配置：由 AI 从用户需求解析出的字段值（{ 字段key: 值}）。传入了即以"对话式配置"模式渲染。';
const gbk = new TextDecoder('gbk');
const utf8bytes = Buffer.from(clean, 'utf8');
// emulate .NET gbk decode with ? replacement (byte-level greedy)
const utf8dec = new TextDecoder('utf-8');
let outBytes = [];
let i = 0;
while (i < utf8bytes.length) {
  const b = utf8bytes[i];
  if (b < 0x80) { outBytes.push([b]); i++; continue; }
  if (b >= 0x81 && b <= 0xfe && i + 1 < utf8bytes.length && ((utf8bytes[i + 1] >= 0x40 && utf8bytes[i + 1] <= 0x7e) || (utf8bytes[i + 1] >= 0xa1 && utf8bytes[i + 1] <= 0xfe))) {
    outBytes.push([b, utf8bytes[i + 1]]); i += 2; continue;
  }
  outBytes.push([b]); i++; // decode single byte via gbk (0x80 -> euro, else ?)
}
const moji = gbk.decode(Buffer.concat(outBytes.map(a => Buffer.from(a))));
console.log('expected moji:', JSON.stringify(moji));
const actual = require('fs').readFileSync('_trueblob.js', 'utf8').split('\n')[17];
console.log('actual   moji:', JSON.stringify(actual));
console.log('MATCH:', moji === actual.replace(/\r$/, ''));
