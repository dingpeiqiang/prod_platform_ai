const fs = require('fs');
const L = fs.readFileSync('_chk_vendor-echarts.js', 'utf8').split('\n')[35];
// find lone single quotes and their contexts
const hits = [];
for (let i = 0; i < L.length; i++) {
  if (L[i] === "'" && L[i - 1] !== '\\') hits.push(i);
}
console.log('single quotes count', hits.length);
hits.slice(0, 60).forEach((h) => console.log(h, JSON.stringify(L.slice(h - 40, h + 25))));
