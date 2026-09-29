const L = require('fs').readFileSync('frontend/public/ops-web/js/app-config-workbench.js', 'utf8').split('\n')[1166];
let mode = 'code';
let transitions = 0;
for (let i = 0; i < L.length; i++) {
  const c = L[i];
  if (mode === 'str2') {
    if (c === '\\') { i++; continue; }
    if (c === '"') { mode = 'code'; console.log('STR->CODE at ' + i + ' ctx ' + JSON.stringify(L.slice(i - 12, i + 12))); }
    continue;
  }
  if (c === '"') { mode = 'str2'; console.log('CODE->STR at ' + i + ' ctx ' + JSON.stringify(L.slice(i - 12, i + 12))); }
}
console.log('endmode=' + mode);
