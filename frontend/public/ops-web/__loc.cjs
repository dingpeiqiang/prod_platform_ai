const fs = require('fs');
const h = fs.readFileSync('frontend/public/ops-web/config-workbench.html', 'utf8');
const re = /config-header-text/g;
let m;
while ((m = re.exec(h))) {
  console.log(m.index, JSON.stringify(h.slice(m.index - 60, m.index + 60)));
}
const re2 = /product-picker/g;
let c = 0;
while ((m = re2.exec(h))) { c++; console.log('PP', m.index, JSON.stringify(h.slice(m.index - 30, m.index + 40))); if (c > 25) break; }
