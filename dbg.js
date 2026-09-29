const fs = require('fs');
const src = fs.readFileSync(process.argv[2], 'utf8');
const lines = src.split('\n');

function tryParse(text) {
  try { new Function(text + '\n'); return null; } catch (e) { return e.message; }
}

// Wrap prefix body (drop wrapper line 1 and last 2 lines), close with })(window)
let lo = 2, hi = lines.length - 2;
// find first prefix that errors

for (let n = 2; n <= lines.length - 2; n++) {
  const body = lines.slice(1, n).join('\n');
  const txt = '(function(global){\n' + body + '\n})(window);';
  const err = tryParse(txt);
  if (err) {
    console.log('first failing n=' + n + ' err=' + err);
    for (let i = Math.max(0, n - 3); i < n; i++) {
      console.log((i + 1) + ': len=' + lines[i].length + ' | ' + lines[i].slice(0, 180));
    }
    break;
  }
  if (n === lines.length - 2) console.log('ok all');
}
