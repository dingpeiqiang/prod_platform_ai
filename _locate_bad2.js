const fs = require('fs');
const code = fs.readFileSync('_badrender.js', 'utf8');
const wrapped = 'const _Vue = window.Vue;\n' + code; // top-level return still illegal; wrap it
const body = 'function __r(){\n' + code + '\n}\n';
const vm = require('vm');
try {
  new vm.Script(body, { filename: 'w.js' });
  console.log('wrapped OK');
} catch (e) {
  console.log('wrapped FAIL:', e.message);
  const m = /w\.js:(\d+)/.exec(e.stack);
  if (m) {
    const ln = +m[1];
    const lines = body.split('\n');
    for (let k = Math.max(0, ln - 3); k < Math.min(lines.length, ln + 2); k++) {
      console.log((k + 1) + ': ' + lines[k].slice(0, 300));
    }
  }
}
