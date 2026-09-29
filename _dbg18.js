const fs = require('fs');
let src = fs.readFileSync('_resolve.js', 'utf8');
src = src.replace("if (!inStr && c === '/' && isRegexStart(out)) {",
  "if (!inStr && c === '/' && isRegexStart(out)) {\n      if (global.TR) console.log('REGEX enter i='+i+' prevout='+JSON.stringify(out.slice(-6).join('')));");
src = src.replace("        if (rc === SENT) {",
  "        if (rc === SENT) {\n          if (global.TR) console.log('  REGEX sent i='+i+' prev='+JSON.stringify(chars[i-1])+' nx='+JSON.stringify(chars[i+1]));");
src = src.replace("        else if (rc === '/' && !inClass) break;",
  "        else if (rc === '/' && !inClass) { if (global.TR) console.log('  REGEX close i='+i); break; }");
src = src.replace('module.exports = { resolve };', 'module.exports = { resolve, resolveLine };');
fs.writeFileSync('_resolve_dbg6.js', src, 'utf8');
global.TR = true;
const R = require('./_resolve_dbg6.js');
const txt = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const line = txt.split('\n')[2];
const out = R.resolveLine(line, true);
