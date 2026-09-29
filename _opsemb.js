const fs = require('fs');
const vm = require('vm');
const src = fs.readFileSync('_chk_ops-view.js', 'utf8');
const lines = src.split('\n');
const line = lines[2];
const eq = line.indexOf('= "');
const end = line.lastIndexOf('";');
const raw = line.slice(eq + 3, end);
const inner = JSON.parse('"' + raw + '"');
fs.writeFileSync('_ops_inner.js', inner, 'utf8');
console.log('inner len', inner.length);
try { new vm.Script(inner); console.log('inner SYNTAX OK'); } catch (e) { console.log('inner ERR', e.message); }
try {
  const acorn = require('./frontend/node_modules/acorn');
  acorn.parse(inner, { ecmaVersion: 2022 });
  console.log('inner acorn OK');
} catch (e) {
  console.log('inner acorn ERR', e.message);
  if (e.loc) {
    const ls = inner.split('\n');
    const l = ls[e.loc.line - 1] || '';
    console.log('LOC line', e.loc.line, 'col', e.loc.column);
    console.log('CTX', JSON.stringify(l.slice(Math.max(0, e.loc.column - 60), e.loc.column + 40)));
  } else { console.log('no loc', e.stack && e.stack.split('\n').slice(0, 3).join(' ~ ')); }
}
console.log('inner ? count', (inner.match(/\?/g) || []).length);
const idx = inner.indexOf('opsOpenRules');
console.log(JSON.stringify(inner.slice(0, 200)));
