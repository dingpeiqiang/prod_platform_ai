const fs = require('fs');
const recon = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak.recon', 'utf8');
const ve = fs.readFileSync('_ve543.js', 'utf8');
const m = '!function';
console.log('recon tail:', JSON.stringify(recon.slice(-200)));
console.log('ve tail   :', JSON.stringify(ve.slice(-200)));
console.log('recon version mentions:', (recon.match(/5\.\d\.\d/g) || []).slice(0, 10));
// header compare
console.log('recon head:', JSON.stringify(recon.slice(0, 120)));
console.log('ve head   :', JSON.stringify(ve.slice(0, 120)));
