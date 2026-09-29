const fs = require('fs');
let src = fs.readFileSync('_resolve.js', 'utf8');
src = src.replace("const cc = contentFor(prev, next);\n    if (cc !== null && (isCJK(prev) || isCJK(next))) {",
  "const cc = contentFor(prev, next);\n    if (prev === '\\u6708' && next === ',') console.log('HIT out-of-str', JSON.stringify({prev,next,cc,inStr,dqAsSingle}));\n    if (cc !== null && (isCJK(prev) || isCJK(next))) {");
src += "\nmodule.exports = module.exports;\n";
eval(src.replace('module.exports = { resolve };', 'global.RESOLVE = resolve;'));
const txt = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const line = txt.split('\n')[2];
console.log('dq check:', /^\s*(window\.__OPS_JS|ConfigWorkbench\.template)\s*=\s*"/.test(line));
RESOLVE(line);
