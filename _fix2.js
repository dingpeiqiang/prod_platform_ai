const fs = require('fs');
const p = '_resolve.js';
let s = fs.readFileSync(p, 'utf8');
const before = s;
s = s.replace(
  "const beforeCloser = nx === undefined || '.,;)]}'.includes(nx);",
  "const beforeCloser = nx === undefined || nx === '.' || nx === ',' || nx === ';';"
);
fs.writeFileSync(p, s, 'utf8');
console.log('changed', before !== s);
