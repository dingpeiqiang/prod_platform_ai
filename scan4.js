const L = require('fs').readFileSync('frontend/public/ops-web/js/app-config-workbench.js', 'utf8').split('\n')[1166];
const counts = {};
for (const ch of L) {
  const code = ch.charCodeAt(0);
  if (code > 126) {
    counts[code] = (counts[code] || 0) + 1;
  }
}
console.log('non-ascii code points:', Object.keys(counts).map(Number).sort((a,b)=>a-b).join(','));
// print all distinct fancy chars
const set = new Set();
for (const ch of L) { const c = ch.charCodeAt(0); if (c > 126) set.add(ch); }
console.log('distinct:', JSON.stringify([...set]));
