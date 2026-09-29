const cp = require('child_process');
const b = cp.execSync('git cat-file blob 8687324abdc9d7db6b4c887441384a86566e6513', { maxBuffer: 1e9 });
const fs = require('fs');
fs.writeFileSync('_trueblob.js', b);
const MOJI = /[\uE000-\uE1FF]|锛|鐢|瀵|妯|寮|鏂|鑻|鍔|缃|€|鏋|锟/;
const lines = b.toString('utf8').split('\n');
let m = 0, c = 0;
for (const l of lines) {
  if (!/[\u4e00-\u9fff]/.test(l)) continue;
  if (MOJI.test(l)) { m++; if (m <= 3) console.log('MOJI', JSON.stringify(l.slice(0, 70))); }
  else c++;
}
console.log('moji=', m, 'clean=', c, 'lines=', lines.length);
