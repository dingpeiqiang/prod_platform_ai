const fs = require('fs');
const l = fs.readFileSync('_chk_ops-view.js', 'utf8').split('\n')[2];
const escDq = (l.match(/\\"/g) || []).length;
const allBs = (l.match(/\\/g) || []).length;
const allDq = (l.match(/"/g) || []).length;
console.log('escaped \\" =', escDq, '| all backslash =', allBs, '| all dq =', allDq, '| bare dq =', allDq - escDq);
// sample of escaped contexts
const re = /\\"/g; let m; const samples = [];
while ((m = re.exec(l)) && samples.length < 5) samples.push(l.slice(m.index - 15, m.index + 15));
console.log(samples.join('\n'));
