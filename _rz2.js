const fs = require('fs');
for (const suf of ['.bak.recon', '.bak.recon.resolved']) {
  const s = fs.readFileSync('frontend/public/ops-web/js/vendor-echarts.js' + suf, 'utf8');
  for (const nd of ['var oe=', 'mouse|pointer|contextmenu']) {
    let i = s.indexOf(nd);
    while (i >= 0) { console.log(suf, nd, i, JSON.stringify(s.slice(i, i + 45))); i = s.indexOf(nd, i + 1); }
  }
}
