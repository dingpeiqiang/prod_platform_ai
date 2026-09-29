const fs = require('fs');
const path = require('path');

const files = process.argv.slice(2);
for (const f of files) {
  const buf = fs.readFileSync(f);
  const t = buf.toString('utf8');
  const lines = t.split('\n');
  let bad = 0;
  lines.forEach((line, i) => {
    for (const ch of line) {
      const c = ch.codePointAt(0);
      // suspicious: U+FFFD, or Latin-1 supplement letters often from mojibake
      if (c === 0xFFFD) {
        console.log(`${path.basename(f)}:${i + 1}: FFFD -> ${JSON.stringify(line)}`);
        bad++;
        break;
      }
    }
  });
  console.log(`${path.basename(f)}: ${bad} lines with FFFD, ${lines.length} lines`);
}
