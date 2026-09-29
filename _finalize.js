const fs = require('fs');
const vm = require('vm');
const acorn = require('./frontend/node_modules/acorn');

const recon = fs.readFileSync('_chk_vendor-echarts.js', 'utf8');
const truth = fs.readFileSync('_ve543.js', 'utf8');

const licA = recon.indexOf('/*\r\n* Licensed to the Apache');
const licB = truth.indexOf('/*\r\n* Licensed to the Apache');

let header = recon.slice(0, licA);
header = header.replace('依赖的?ECharts', '依赖的ECharts');
header = header.replace('问题，?----', '问题）----');

const finalVendor = header + truth.slice(licB);
fs.writeFileSync('_ve_final.js', finalVendor, 'utf8');

try { new vm.Script(finalVendor); console.log('vm SYNTAX OK'); }
catch (e) { console.log('vm ERR', e.message); }
try { acorn.parse(finalVendor, { ecmaVersion: 2022 }); console.log('acorn SYNTAX OK'); }
catch (e) { console.log('acorn ERR', e.message); }

// how far was the pure pipeline output from truth (informational)
const m = '!function';
const A = recon.slice(recon.indexOf(m));
const B = truth.slice(truth.indexOf(m));
let i = 0, j = 0, blocks = 0;
while (i < A.length && j < B.length) {
  if (A[i] === B[j]) { i++; j++; continue; }
  let best = null;
  for (let ka = 0; ka <= 6; ka++) for (let kb = 0; kb <= 6; kb++) {
    if (ka === 0 && kb === 0) continue;
    if (i + ka >= A.length || j + kb >= B.length) continue;
    let ok = true;
    for (let w = 0; w < 6; w++) if (A[i + ka + w] !== B[j + kb + w]) { ok = false; break; }
    if (ok) { const c = ka + kb; if (!best || c < best.cost) best = { ka, kb } ; }
  }
  if (!best) best = { ka: 1, kb: 1 };
  blocks++; i += best.ka; j += best.kb;
}
console.log('pipeline-vs-truth body diff blocks:', blocks, '(all char near-misses)');
console.log('final length:', finalVendor.length);
