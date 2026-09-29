const fs = require('fs');
const l = fs.readFileSync('_chk_ops-view.js', 'utf8').split('\n')[2];
const hits = [];
for (let i = 0; i < l.length; i++) {
  if (l[i] === '"' && l[i - 1] !== '\\') hits.push(i);
}
console.log('bare dq at', hits.join(','));
for (const h of hits) console.log(h, JSON.stringify(l.slice(h - 50, h + 30)));
