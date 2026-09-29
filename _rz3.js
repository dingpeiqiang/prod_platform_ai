const fs = require('fs');
for (const suf of ['.bak.recon', '.bak.recon.resolved']) {
  const s = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js' + suf, 'utf8');
  const i = s.indexOf('d{4})(?:[');
  console.log(suf, i, JSON.stringify(s.slice(i - 8, i + 150)));
}
