const fs = require('fs');
let src = fs.readFileSync('_resolve.js', 'utf8');
src = src.replace('function resolveLine(line, dqAsSingle) {',
  "function resolveLine(line, dqAsSingle) {\n  global.DBG = dqAsSingle;");
src = src.replace('const cc = contentFor(prev, next);\n    if (cc !== null && (isCJK(prev) || isCJK(next))) {',
  "const cc = contentFor(prev, next);\n    if (global.DBG && next === ',') console.log('OOS prev=', JSON.stringify(prev), 'next=', JSON.stringify(next), 'cc=', cc, 'inStr=', inStr);\n    if (cc !== null && (isCJK(prev) || isCJK(next))) {");
src = src.replace('module.exports = { resolve };', 'module.exports = { resolve, resolveLine };');
fs.writeFileSync('_resolve_dbg2.js', src, 'utf8');
const R = require('./_resolve_dbg2.js');
const txt = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const line = txt.split('\n')[2];
R.resolveLine(line, true);
