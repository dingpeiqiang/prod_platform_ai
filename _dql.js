const fs = require('fs');
const f = process.argv[2] || '_chk_ops-view.js';
const lineNo = parseInt(process.argv[3] || '3', 10);
const src = fs.readFileSync(f, 'utf8').split('\n');
const line = src[lineNo - 1];

// Parse as a JS double-quoted string starting after `= "`
const start = line.indexOf('= "') + 3;
let i = start;
const out = [];
let bad = -1;
while (i < line.length) {
  const c = line[i];
  if (c === '\\') { i += 2; continue; }
  if (c === '"') { break; }
  i++;
}
const firstClose = i;
console.log('firstClose index=', firstClose, 'of', line.length);
const tail = line.slice(firstClose + 1);
console.log('TAIL:', JSON.stringify(tail.slice(0, 120)));

// count quotes/escapes in the whole line
let esc = 0, bs = 0;
for (const ch of line) { if (ch === '\\') bs++; }
console.log('backslashes=', bs, 'dq=', (line.match(/"/g)||[]).length, 'sq=', (line.match(/'/g)||[]).length);
