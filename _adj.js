const fs = require('fs');
const lines = fs.readFileSync('_chk_vendor-echarts.js', 'utf8').split('\n');
const L = lines[35] || '';
console.log('line36 len', L.length);
const re = /(['"])((?:\\.|(?!\1).)*)\1/g;
let m; const spans = [];
while ((m = re.exec(L))) spans.push([m.index, m.index + m[0].length, m[0].slice(0, 30)]);
const suspicious = [];
for (let k = 0; k + 1 < spans.length; k++) {
  const end = spans[k][1];
  const nxt = spans[k + 1][0];
  if (nxt === end) suspicious.push([end, L.slice(end - 30, end + 30)]);
}
console.log('suspicious adjacent literals:', suspicious.length);
suspicious.slice(0, 25).forEach(([p, c]) => console.log(p, JSON.stringify(c)));
