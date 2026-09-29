const fs = require('fs');
const vm = require('vm');
const acorn = require('./frontend/node_modules/acorn');
const files = ['data-presets', 'wb-context', 'ops-view', 'app-config-workbench', 'vendor-echarts'];
let bad = 0;
for (const f of files) {
  const p = 'frontend/public/ops-web/js/' + f + '.js';
  const s = fs.readFileSync(p, 'utf8');
  let a = 'OK', b = 'OK';
  try { new vm.Script(s); } catch (e) { a = 'VM ' + e.message; bad++; }
  try { acorn.parse(s, { ecmaVersion: 2022 }); } catch (e) { b = 'ACORN ' + e.message; bad++; }
  console.log(f, '| vm:', a, '| acorn:', b);
}
console.log(bad ? 'FAIL' : 'ALL OK');
