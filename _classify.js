const fs = require('fs');
const f = process.argv[2];
const t = fs.readFileSync(f, 'utf8');
const chars = [...t];
const m = new Map();
for (let i = 0; i < chars.length; i++) {
  if (chars[i] !== '?') continue;
  const prev2 = chars.slice(Math.max(0, i - 2), i).join('');
  const next4 = chars.slice(i + 1, i + 5).join('');
  const key = JSON.stringify(next4[0] || '');
  if (!m.has(key)) m.set(key, []);
  if (m.get(key).length < 4) m.get(key).push(prev2 + '?' + next4);
}
for (const [k, v] of [...m.entries()].sort()) {
  console.log(k, '::', v.join(' | '));
}
