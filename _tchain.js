const { gbkEncode, reconstruct } = require('./_recover.js');
const utf8dec = new TextDecoder('utf8');
function chain(str) {
  const B = gbkEncode(str);
  const { bytes } = reconstruct(B);
  return utf8dec.decode(Buffer.from(bytes));
}
for (const ch of ['↑', '↓', '▲', '▼', '→', '⇀', '⑀', '▀', '◀', '✀', '阀', '户', '万', '①', '②']) {
  console.log(ch, JSON.stringify(chain(ch)));
}
