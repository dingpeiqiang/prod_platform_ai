const fs = require('fs');
const SENT = '\u0001';
const line = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak.recon', 'utf8').split('\n')[35];
const chars = Array.from(line);
let inStr = false, strCh = '';
const marks = [2184, 3989, 16233, 16277, 16291, 55536, 67019, 69871];
let mi = 0;
for (let i = 0; i < chars.length; i++) {
  const c = chars[i];
  if (mi < marks.length && i === marks[mi]) {
    console.log('@', i, 'inStr=', inStr, 'strCh=', JSON.stringify(strCh), JSON.stringify(line.slice(i - 20, i + 15)));
    mi++;
  }
  if (c === '\\') { i++; continue; }
  if (c === "'" || c === '"') {
    if (inStr && c === strCh) { inStr = false; strCh = ''; }
    else if (!inStr) { inStr = true; strCh = c; }
  }
}
console.log('final inStr', inStr, 'strCh', JSON.stringify(strCh));
