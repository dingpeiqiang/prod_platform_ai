const fs = require('fs');
const bak = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak', 'utf8');
const needle = '月收元';
let i = bak.indexOf(needle);
while (i >= 0) {
  console.log('bak idx', i, JSON.stringify(bak.slice(i - 20, i + 40)));
  i = bak.indexOf(needle, i + 1);
}
