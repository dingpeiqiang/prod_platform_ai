const fs = require('fs');
const src = fs.readFileSync(process.argv[2], 'utf8');
const lines = src.split('\n');

// Character scanner over whole file to track state; report where it gets stuck.
let mode = 'code';
const stack = []; // for tpl ${}
let line = 1, col = 0;

function report(msg) {
  console.log('line ' + line + ' col ' + col + ': ' + msg);
  console.log('content: ' + JSON.stringify(lines[line - 1].slice(0, 200)));
}

for (let i = 0; i < src.length; i++) {
  const c = src[i];
  if (c === '\n') { line++; col = 0; continue; }
  col++;

  if (mode === 'str1' || mode === 'str2') {
    const q = mode === 'str1' ? "'" : '"';
    if (c === '\\') { i++; col++; continue; }
    if (c === q) { mode = 'code'; }
    continue;
  }
  if (mode === 'tpl') {
    if (c === '\\') { i++; col++; continue; }
    if (c === '`') { mode = 'code'; continue; }
    if ((c === '$') && src[i + 1] === '{') { stack.push('tpl'); mode = 'code'; i++; col++; continue; }
    continue;
  }
  // code
  if (c === "'") { mode = 'str1'; continue; }
  if (c === '"') { mode = 'str2'; continue; }
  if (c === '`') { mode = 'tpl'; continue; }
  if (c === '}' && stack.length) { stack.pop(); mode = 'tpl'; continue; }
  if (c === '/' && src[i + 1] === '/') {
    while (i < src.length && src[i] !== '\n') { i++; }
    i--; continue;
  }
  if (c === '/' && src[i + 1] === '*') {
    i += 2;
    while (i < src.length && !(src[i] === '*' && src[i + 1] === '/')) { if (src[i] === '\n') { line++; } i++; }
    i++; continue;
  }
}
console.log('final mode=' + mode + ' stackDepth=' + stack.length + ' at line ' + line);
