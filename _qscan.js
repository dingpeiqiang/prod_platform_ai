const fs = require('fs');
const t = fs.readFileSync('_trueblob.js', 'utf8');
const lines = t.split('\n');
const MOJI = /[\uE000-\uE1FF]|锛|鐢|瀵|妯|寮|鏂|鑻|鍔|缃|€|鏋|锟/;
let qCount = 0, mojiLines = 0;
lines.forEach((l, i) => {
  if (!MOJI.test(l)) return;
  mojiLines++;
  // find ? surrounded by mojibake chars
  const re = /[\u3000-\u9fff\ue000-\ue1ff€]{0,1}\?[\u3000-\u9fff\ue000-\ue1ff€]{0,1}/g;
  let m;
  while ((m = re.exec(l))) {
    qCount++;
    if (qCount <= 25) console.log(`L${i + 1}: ...${JSON.stringify(l.slice(Math.max(0, m.index - 12), m.index + 12))}`);
  }
});
console.log('total ? near moji:', qCount, 'mojiLines:', mojiLines);
