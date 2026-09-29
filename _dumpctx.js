const fs = require('fs');
const f = process.argv[2];
const t = fs.readFileSync(f, 'utf8');
const lines = t.split('\n');
const out = [];
for (let i = 0; i < lines.length; i++) {
  const line = lines[i];
  let idx = -1;
  while ((idx = line.indexOf('?', idx + 1)) !== -1) {
    const prev = line.slice(Math.max(0, idx - 3), idx);
    const next = line.slice(idx + 1, idx + 4);
    // only sentinel: prev char is non-ascii CJK-ish
    const pc = prev.slice(-1);
    const cp = pc.codePointAt(0);
    if (cp < 0x80) continue;
    out.push(`L${i + 1} [${prev}]?[${next}]`);
  }
}
fs.writeFileSync(f + '.ctx', out.join('\n'), 'utf8');
console.log('ctx lines', out.length);
