const fs = require('fs');
const h = fs.readFileSync('frontend/public/ops-web/config-workbench.html', 'utf8');
// find real import of vue components in ConfigWorkbench setup
const m = h.match(/const\s*\{[^}]*onMounted[^}]*\}\s*=\s*Vue/);
console.log('Vue destructure match:', m ? m[0] : 'none');
const m2 = h.match(/onMounted[^;]{0,10}=[^;]{0,60}Vue/);
console.log('m2:', m2 ? m2[0] : 'none');
// list all "= Vue" occurrences
let idx = 0, n = 0;
while ((idx = h.indexOf('= Vue', idx)) !== -1 && n < 10) { console.log('=Vue at', idx, JSON.stringify(h.slice(idx - 180, idx + 10))); idx++; n++; }
