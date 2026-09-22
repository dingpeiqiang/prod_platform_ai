const fs = require('fs');
const h = fs.readFileSync('frontend/public/ops-web/config-workbench.html', 'utf8');
const i = h.indexOf('onMounted');
// find the import statement containing onMounted
const j = h.lastIndexOf('import', i);
console.log(h.slice(j, j + 260));
console.log('=== emits full ===');
const k = h.indexOf("emits: [");
console.log(h.slice(k, k + 300));
