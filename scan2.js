const fs = require('fs');
const src = fs.readFileSync(process.argv[2], 'utf8');

let mode = 'code';
const stack = [];
let line = 1;
let inRegex = false;

function lineOf(pos) { return src.slice(0, pos).split('\n').length; }

for (let i = 0; i < src.length; i++) {
  const c = src[i];
  if (c === '\n') { line++; continue; }

  if (mode === 'str1' || mode === 'str2') {
    const q = mode === 'str1' ? "'" : '"';
    if (c === '\\') { i++; continue; }
    if (c === q) mode = 'code';
    continue;
  }
  if (mode === 'tpl') {
    if (c === '\\') { i++; continue; }
    if (c === '`') { mode = 'code'; continue; }
    if (c === '$' && src[i + 1] === '{') { stack.push('tpl'); mode = 'code'; i++; continue; }
    continue;
  }
  // code
  if (c === "'") { mode = 'str1'; continue; }
  if (c === '"') { mode = 'str2'; continue; }
  if (c === '`') { mode = 'tpl'; continue; }
  if (c === '}' && stack.length) { stack.pop(); mode = 'tpl'; continue; }
  if (c === '/' && src[i + 1] === '/') { while (i < src.length && src[i] !== '\n') i++; i--; continue; }
  if (c === '/' && src[i + 1] === '*') { i += 2; while (i < src.length && !(src[i] === '*' && src[i + 1] === '/')) { if (src[i] === '\n') line++; i++; } i++; continue; }
  if (c === '?') {
    const nx = src[i + 1] || '';
    const nx2 = src[i + 2] || '';
    // valid: ?. ?? ?: ? ... but bare ? followed by letter/number is bad
    if (/[A-Za-z0-9_$]/.test(nx)) {
      console.log('BARE ? at line ' + line + ' ctx: ' + JSON.stringify(src.slice(i - 30, i + 20)));
    }
  }
}
console.log('final mode=' + mode);
