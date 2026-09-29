const fs = require('fs');
const vm = require('vm');
const f = process.argv[2];
const src = fs.readFileSync(f, 'utf8');
const out = [];
try { new vm.Script(src); out.push('SYNTAX OK'); }
catch (e) { out.push('ERROR: ' + e.message); out.push((e.stack || '').split('\n').slice(0, 6).join('\n')); }
const lines = src.split('\n');
lines.forEach((l, i) => {
  const q = (l.match(/'/g) || []).length;
  const d = (l.match(/"/g) || []).length;
  if (q % 2 || d % 2) out.push('L' + (i + 1) + ' q=' + q + ' d=' + d + ' | ' + l.trim());
});
fs.writeFileSync('_check_out.txt', out.join('\n'), 'utf8');
console.log(out.slice(0, 3).join(' | '));
