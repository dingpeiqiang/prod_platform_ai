const fs = require('fs');
const vm = require('vm');
const acorn = require('./frontend/node_modules/acorn');

function variant(removeFold) {
  let s = fs.readFileSync('_resolve.js', 'utf8');
  if (removeFold) s = s.replace("res = res.replace(/ ?-\\s*- ?/g, ' - ');", '');
  fs.writeFileSync('_resolve_tmp.js', s);
  delete require.cache[require.resolve('./_resolve_tmp.js')];
  const { resolve } = require('./_resolve_tmp.js');
  const files = ['data-presets', 'wb-context', 'ops-view', 'app-config-workbench', 'vendor-echarts'];
  console.log('### removeFold =', removeFold);
  for (const f of files) {
    const p = 'frontend/public/ops-web/js/' + f + '.js.bak.recon';
    const r = resolve(fs.readFileSync(p, 'utf8'));
    let st = 'OK';
    try { new vm.Script(r); } catch (e) { st = 'VMERR ' + e.message; }
    let ac = 'OK';
    try { acorn.parse(r, { ecmaVersion: 2022 }); } catch (e) { ac = 'ACORNERR ' + e.message + ' @' + e.pos; }
    console.log('  ' + f + ' | ' + st + ' | ' + ac);
  }
}
variant(false);
variant(true);
