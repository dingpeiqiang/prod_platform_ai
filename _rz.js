const fs = require('fs');
function show(file, needle) {
  const s = fs.readFileSync(file, 'utf8');
  let i = s.indexOf(needle);
  while (i >= 0) {
    console.log(file, i, JSON.stringify(s.slice(i, i + 45)));
    i = s.indexOf(needle, i + 1);
  }
}
show('frontend/public/ops-web/js/app-config-workbench.js.bak', '原套餐');
show('frontend/public/ops-web/js/vendor-echarts.js.bak', '.+?rv');
show('frontend/public/ops-web/js/vendor-echarts.js.bak', 'Trident');
