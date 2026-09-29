const L = require('fs').readFileSync('frontend/public/ops-web/js/app-config-workbench.js', 'utf8').split('\n')[1166];
let mode = 'code';
for (let i = 0; i < L.length; i++) {
  const c = L[i];
  if (mode === 'str2') {
    if (c === '\\') { i++; continue; }
    if (c === '"') { mode = 'code'; }
    continue;
  }
  if (c === '"') { mode = 'str2'; continue; }
  if (c === '?') {
    console.log('CODE ? at ' + i + ' ctx ' + JSON.stringify(L.slice(i - 25, i + 25)));
  }
}
console.log('end mode ' + mode);
