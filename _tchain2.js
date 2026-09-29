const { recover, gbkEncode, reconstruct } = require('./_recover.js');
const fs = require('fs');
const utf8dec = new TextDecoder('utf8');
const gbkdec = new TextDecoder('gbk');
// simulate corruption: original utf8 text -> read as GBK (produces mojibake text)
// then the .bak holds that mojibake; recover() reverses it.
function makeBak(str) {
  const b = Buffer.from(str, 'utf8');
  const dec = new TextDecoder('gbk');
  return dec.decode(b);
}
function roundtrip(str) {
  const bak = makeBak(str);
  const B = gbkEncode(bak);
  const { bytes } = reconstruct(B);
  return utf8dec.decode(Buffer.from(bytes));
}
const tmpl = "'月同比%' ";
for (const ch of ['↑', '↓', '▲', '▼', '→', '⇀', '⑀', '▀', '◀', '✀', '①', '②', '阀', '户', '万']) {
  console.log(ch, JSON.stringify(roundtrip('x' + ch + 'y')));
}
