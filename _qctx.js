const fs = require('fs');
const t = fs.readFileSync(process.argv[2], 'utf8');
const chars = [...t];
let total = 0;
const after = new Map();
for (let i = 0; i < chars.length; i++) {
  if (chars[i] !== '?') continue;
  total++;
  const nxt = chars[i + 1] === undefined ? '<EOF>' : chars[i + 1];
  const key = JSON.stringify(nxt);
  after.set(key, (after.get(key) || 0) + 1);
}
console.log('total ?', total);
const arr = [...after.entries()].sort((a, b) => b[1] - a[1]).slice(0, 25);
for (const [k, v] of arr) console.log(v, k);
