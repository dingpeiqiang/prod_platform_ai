const fs = require('fs');
const code = fs.readFileSync('_badrender.js', 'utf8');
const body = 'function __r(){\n' + code + '\n}\n';
const vm = require('vm');
try {
  new vm.Script(body, { filename: 'w.js' });
  console.log('OK');
} catch (e) {
  console.log(e.stack.split('\n').slice(0, 3).join('\n'));
  const m = /w\.js:(\d+):(\d+)/.exec(e.stack);
  if (!m) { console.log('no pos'); process.exit(0); }
  const ln = +m[1], col = +m[2];
  const lines = body.split('\n');
  const line = lines[ln - 1];
  console.log('line', ln, 'col', col);
  console.log('context:', JSON.stringify(line.slice(Math.max(0, col - 40), col + 10)));
  console.log('charAtCol:', JSON.stringify(line[col - 1]), line.charCodeAt(col - 1).toString(16));
}
