const fs = require('fs');
const f = 'frontend/public/ops-web/js/app-config-workbench.js';
let s = fs.readFileSync(f, 'utf8');
const bad = '解析得出?\\">';   // 解析得出?\"  (backslash + quote + >)
const good = '解析得出\'\\">'; // 解析得出'\" 
console.log('bad len', bad.length, 'codes', [...bad].map(c => c.codePointAt(0).toString(16)).join(','));
const n = s.split(bad).length - 1;
console.log('bad occurrences:', n);
if (n > 0) { s = s.split(bad).join(good); fs.writeFileSync(f, s, 'utf8'); console.log('replaced'); }
