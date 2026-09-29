const fs = require('fs');
const path = require('path');

// Classic double-encode detector: a byte sequence that is valid UTF-8 but,
// when re-decoded, gives common "mojibake" CJK chars.
const MOJI = /[\uE000-\uE1FF]|锛|鐢|瀵|妯|寮|鏂|鑻|鍔|缃|€|鏋|锟/;

const files = process.argv.slice(2);
for (const f of files) {
  const t = fs.readFileSync(f, 'utf8');
  const lines = t.split(/\r?\n/);
  let mojLines = 0, cjkLines = 0;
  lines.forEach((line) => {
    const hasCJK = /[\u4e00-\u9fff]/.test(line);
    if (!hasCJK) return;
    if (MOJI.test(line)) {
      mojLines++;
      if (mojLines <= 3) console.log(`  ${path.basename(f)}: ${JSON.stringify(line.slice(0, 80))}`);
    } else {
      cjkLines++;
    }
  });
  console.log(`${path.basename(f)}: mojibakeLines=${mojLines}, cleanCJKLines=${cjkLines}, total=${lines.length}`);
}
