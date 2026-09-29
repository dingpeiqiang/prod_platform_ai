const fs = require('fs');
const txt = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const line = txt.split('\n')[2];
const chars = Array.from(line);
const i = chars.findIndex((c, k) => c === 'm' && chars.slice(k, k + 6).join('') === 'months');
console.log('idx', i);
for (let k = i + 8; k < i + 40; k++) {
  const c = chars[k];
  console.log(k, JSON.stringify(c), c.codePointAt(0).toString(16));
}
