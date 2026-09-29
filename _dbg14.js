const fs = require('fs');
let src = fs.readFileSync('_resolve.js', 'utf8');
src = src.replace('    const cc = contentFor(prev, next);\n      if (cc !== null) { out.push(dqAsSingle && cc === \'"\' ? "\'" : cc); continue; }',
  '    const cc = contentFor(prev, next);\n      if (prev === "\\u6708") console.log("INSTR prev=月 next=", JSON.stringify(next), "nextNS=", JSON.stringify(nextNS), "cc=", cc, "q=", dqAsSingle);\n      if (cc !== null) { out.push(dqAsSingle && cc === \'"\' ? "\'" : cc); continue; }');
src = src.replace('module.exports = { resolve };', 'module.exports = { resolve, resolveLine };');
fs.writeFileSync('_resolve_dbg3.js', src, 'utf8');
const R = require('./_resolve_dbg3.js');
const txt = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const line = txt.split('\n')[2];
R.resolveLine(line, true);
