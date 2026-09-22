const fs = require('fs');
const h = fs.readFileSync('frontend/public/ops-web/config-workbench.html', 'utf8');
console.log('--- imports (around onMounted import) ---');
const i = h.indexOf("'vue'");
console.log(h.slice(i - 200, i + 200));
console.log('--- props/emits ---');
console.log(h.slice(1340600, 1340820));
console.log('--- __returned__ init ---');
console.log(h.slice(1381120, 1381420));
