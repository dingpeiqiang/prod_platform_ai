const fs = require('fs');
let src = fs.readFileSync('_resolve.js', 'utf8');
// trace every SENT handling
src = src.replace("if (c !== SENT) { out.push(c); continue; }",
  "if (c !== SENT) { out.push(c); continue; }\n    if (global.TR) console.log('SENT i='+i+' prev='+JSON.stringify(out[out.length-1])+' next='+JSON.stringify(chars[i+1])+' inStr='+inStr+' strCh='+JSON.stringify(strCh)+' dq='+dqAsSingle);");
src = src.replace('    const cc = contentFor(prev, next);\n      if (cc !== null) { out.push(dqAsSingle && cc === \'"\' ? "\'" : cc); continue; }',
  '    const cc = contentFor(prev, next);\n      if (global.TR) console.log("  ->inStr contentFor prev="+JSON.stringify(prev)+" next="+JSON.stringify(next)+" cc="+cc);\n      if (cc !== null) { out.push(dqAsSingle && cc === \'"\' ? "\'" : cc); continue; }');
src = src.replace('const cc = contentFor(prev, next);\n    if (cc !== null && (isCJK(prev) || isCJK(next))) {',
  'const cc = contentFor(prev, next);\n    if (global.TR) console.log("  ->OUTSTR contentFor prev="+JSON.stringify(prev)+" next="+JSON.stringify(next)+" cc="+cc);\n    if (cc !== null && (isCJK(prev) || isCJK(next))) {');
src = src.replace('module.exports = { resolve };', 'module.exports = { resolve, resolveLine };');
src = src.replace("const isStruct = (c) => c === undefined || AFTER_CLOSE.has(c);",
  "const isStruct = (c) => c === undefined || AFTER_CLOSE.has(c);");
fs.writeFileSync('_resolve_dbg4.js', src, 'utf8');
const R = require('./_resolve_dbg4.js');
global.TR = false;
const txt = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const line = txt.split('\n')[2];
const chars = Array.from(line);
const i = chars.findIndex((c, k) => chars.slice(k, k + 6).join('') === 'months');
for (let k = i + 10; k < i + 20; k++) {
  const l2 = chars.map((c, idx) => (idx >= i + 10 && idx < i + 20 ? c : ' ')).join('');
}
// run once, capture only SENT near months by counting
global.TR = true;
// We'll filter by prev=月
const origLog = console.log;
R.resolveLine(line, true);
