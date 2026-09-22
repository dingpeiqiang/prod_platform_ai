const fs = require('fs');
const h = fs.readFileSync('frontend/public/ops-web/config-workbench.html', 'utf8');
// The setup uses onUnmounted, ref, computed - find where defined
for (const name of ['onUnmounted', 'reactive', 'watch']) {
  const re = new RegExp(name + '[^;\\n]{0,6}=\\s*[^;\\n]{0,40}', 'g');
  let m, n = 0;
  while ((m = re.exec(h)) && n < 4) { console.log(name, '=>', JSON.stringify(m[0])); n++; }
}
// find the IIFE/setup wrapper for ConfigWorkbench
const k = h.indexOf("setup(__props, { expose: __expose, emit: __emit })");
console.log('setup start ctx:', JSON.stringify(h.slice(k - 300, k + 60)));
