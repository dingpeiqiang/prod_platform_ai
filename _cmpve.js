const fs = require('fs');
const bak = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak');
// find the start of the UMD code in bak (after the license comment)
const bakTxt = bak.toString('utf8');
const marker = '!function';
let bi = bakTxt.indexOf(marker);
console.log('bak code start (char idx):', bi, JSON.stringify(bakTxt.slice(bi, bi + 120)));

const ve = fs.readFileSync('_ve543.js', 'utf8');
let vi = ve.indexOf(marker);
console.log('ve  code start (char idx):', vi, JSON.stringify(ve.slice(vi, vi + 120)));

// compare first 300 chars of code
const a = bakTxt.slice(bi, bi + 300);
const b = ve.slice(vi, vi + 300);
let k = 0;
while (k < a.length && k < b.length && a[k] === b[k]) k++;
console.log('common prefix len:', k);
console.log('bak diverges at:', JSON.stringify(a.slice(k - 30, k + 40)));
console.log('ve  diverges at:', JSON.stringify(b.slice(k - 30, k + 40)));
