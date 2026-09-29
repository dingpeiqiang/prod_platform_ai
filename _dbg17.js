const fs = require('fs');
let src = fs.readFileSync('_resolve.js', 'utf8');
src = src.replace("  for (let i = 0; i < chars.length; i++) {\n    const c = chars[i];",
  "  for (let i = 0; i < chars.length; i++) {\n    const c = chars[i];\n    if (global.TR && i >= 8433 && i <= 8442) console.log('ITER i='+i+' c='+JSON.stringify(c)+' cp='+c.codePointAt(0).toString(16)+' inStr='+inStr+' strCh='+JSON.stringify(strCh));");
src = src.replace('module.exports = { resolve };', 'module.exports = { resolve, resolveLine };');
fs.writeFileSync('_resolve_dbg5.js', src, 'utf8');
global.TR = true;
const R = require('./_resolve_dbg5.js');
const txt = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const line = txt.split('\n')[2];
R.resolveLine(line, true);
