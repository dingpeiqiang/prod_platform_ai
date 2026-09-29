const fs = require('fs');
const vm = require('vm');
const src = fs.readFileSync('_chk_vendor-echarts.js', 'utf8');
const o = [];
try { new vm.Script(src); o.push('OK'); }
catch (e) {
  o.push(e.message);
  const m = /<anonymous>:(\d+):(\d+)/.exec(e.stack || '');
  if (m) {
    const ln = parseInt(m[1], 10), col = parseInt(m[2], 10);
    const lines = src.split('\n');
    o.push('LINE ' + ln + ' COL ' + col + ' LINELEN ' + (lines[ln - 1] || '').length);
    o.push('CTX ' + JSON.stringify((lines[ln - 1] || '').slice(Math.max(0, col - 120), col + 60)));
  } else o.push('no location');
}
fs.writeFileSync('_ve_out.txt', o.join('\n'), 'utf8');

