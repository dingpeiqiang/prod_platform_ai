const fs = require('fs');
const f = process.argv[2];
const t = fs.readFileSync(f, 'utf8');
const chars = [...t];
const sig = new Map();
for (let i = 0; i < chars.length; i++) {
  if (chars[i] !== '?') continue;
  const pc = chars[i - 1] || '';
  const cp = pc.codePointAt(0);
  if (cp < 0x80 && pc !== '?' ) continue; // sentinel must follow non-ascii
  const next = chars[i + 1] || '<EOL>';
  const prev = chars[i - 1] || '';
  const k = prev + ' ▸ ' + next;
  sig.set(k, (sig.get(k) || 0) + 1);
}
const arr = [...sig.entries()].sort((a, b) => b[1] - a[1]);
fs.writeFileSync(f + '.sig', arr.map(([k, v]) => v + '\t' + JSON.stringify(k)).join('\n'), 'utf8');
console.log('signatures', arr.length);
