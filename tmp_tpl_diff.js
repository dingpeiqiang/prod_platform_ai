const { execSync } = require('child_process');
const fs = require('fs');
function tpl(s) {
  const i = s.indexOf('ConfigWorkbench.template = ');
  return s.slice(i);
}
function unesc(s) {
  return s.replace(/\\r\\n/g, '\n').replace(/\\n/g, '\n').replace(/\\"/g, '"');
}
const oldRaw = execSync('git show HEAD:frontend/public/ops-web/js/app-config-workbench.js', { maxBuffer: 1e8 }).toString('utf8');
const neuRaw = fs.readFileSync('frontend/public/ops-web/js/app-config-workbench.js', 'utf8');
const old = unesc(tpl(oldRaw));
const neu = unesc(tpl(neuRaw));
console.log('old len', old.length, 'new len', neu.length);
const ol = old.split('\n');
const nl = neu.split('\n');
const max = Math.max(ol.length, nl.length);
let diffs = 0;
for (let i = 0; i < max; i++) {
  if (ol[i] !== nl[i]) {
    console.log('DIFF line', i + 1);
    console.log('  OLD:', (ol[i] || '').slice(0, 240));
    console.log('  NEW:', (nl[i] || '').slice(0, 240));
    if (++diffs > 20) break;
  }
}
