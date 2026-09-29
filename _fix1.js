const fs = require('fs');
const p = '_resolve.js';
const lines = fs.readFileSync(p, 'utf8').split('\n');
const idx = lines.findIndex((l) => l.includes('if (rc === SENT) {'));
console.log('found at line', idx + 1);
const repl = [
  "        if (rc === SENT) {",
  "          // a lost byte inside a regex is almost always the closing slash,",
  "          // unless this '?' is a genuine regex quantifier/group modifier",
  "          const pc = chars[i - 1] || '';",
  "          const nx = chars[i + 1];",
  "          const isQuant = '+*?{}'.includes(pc);",
  "          const beforeCloser = nx === undefined || '.,;)]}'.includes(nx);",
  "          if (isQuant || !beforeCloser) { out.push('?'); continue; }",
  "          out.push('/');",
  "          break;",
  "        }",
];
lines.splice(idx, 8, ...repl);
fs.writeFileSync(p, lines.join('\n'), 'utf8');
console.log('done');
