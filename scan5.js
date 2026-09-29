const L = require('fs').readFileSync('frontend/public/ops-web/js/app-config-workbench.js', 'utf8').split('\n')[1166];
let mode = 'code';
let found = 0;
for (let i = 0; i < L.length; i++) {
  const c = L[i];
  if (mode === 'str2') {
    if (c === '\\') { i++; continue; }
    if (c === '"') { mode = 'code'; }
    continue;
  }
  if (c === '"') { mode = 'str2'; continue; }
  // code mode: non-ascii?
  if (c.charCodeAt(0) > 126) {
    console.log('NON-ASCII IN CODE at ' + i + ' ctx ' + JSON.stringify(L.slice(i - 30, i + 10)));
    if (++found >= 10) break;
  }
}
console.log('done, found=' + found + ' endmode=' + mode);
