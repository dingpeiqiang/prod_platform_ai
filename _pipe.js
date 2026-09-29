const fs = require('fs');
const vm = require('vm');
const path = require('path');
const { recover } = require('./_recover.js');
const { resolve } = require('./_resolve.js');

const JSDIR = 'frontend/public/ops-web/js';
const files = [
  'data-presets.js',
  'wb-context.js',
  'ops-view.js',
  'app-config-workbench.js',
  'vendor-echarts.js',
];

const report = [];
for (const f of files) {
  const bak = path.join(JSDIR, f + '.bak');
  if (!fs.existsSync(bak)) { report.push(f + ' : NO BAK'); continue; }
  const recon = recover(bak);
  fs.writeFileSync(bak + '.recon', recon, 'utf8');
  const res = resolve(recon);
  fs.writeFileSync(bak + '.recon.resolved', res, 'utf8');
  fs.writeFileSync('./_chk_' + f, res, 'utf8');
  let status;
  let errStack = '';
  try { new vm.Script(res); status = 'SYNTAX OK'; }
  catch (e) { status = 'ERROR: ' + e.message; errStack = e.stack || ''; }
  const qq = (res.match(/'/g) || []).length;
  const lines = res.split('\n');
  let odd = 0;
  lines.forEach((l) => { if (((l.match(/'/g) || []).length % 2)) odd++; });
  report.push(f + ' : ' + status + ' | lines=' + lines.length + ' oddQuoteLines=' + odd + ' totalQ=' + qq);
  if (status.startsWith('ERROR')) {
    report.push('   STACK: ' + errStack.split('\n').slice(0, 5).join(' ~ '));
    const m = /evalmachine\.<anonymous>:(\d+):(\d+)/.exec(errStack);
    if (m) {
      const ln = parseInt(m[1], 10);
      const col = parseInt(m[2], 10);
      report.push('   near line ' + ln + ' col ' + col);
      const l = lines[ln - 1] || '';
      report.push('   CTX ' + JSON.stringify(l.slice(Math.max(0, col - 70), col + 40)));
    }
  }
}
fs.writeFileSync('_pipe_out.txt', report.join('\n'), 'utf8');
console.log('done');
