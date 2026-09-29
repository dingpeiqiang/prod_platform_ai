const fs = require('fs');
const acorn = require('./frontend/node_modules/acorn');
const f = process.argv[2];
const src = fs.readFileSync(f, 'utf8');
try {
  acorn.parse(src, { ecmaVersion: 2022 });
  fs.writeFileSync('_acorn_out.txt', 'OK');
} catch (e) {
  const o = [e.message, 'pos=' + e.pos, 'loc=' + JSON.stringify(e.loc)];
  if (e.loc) {
    const lines = src.split('\n');
    const l = lines[e.loc.line - 1] || '';
    o.push('CTX ' + JSON.stringify(l.slice(Math.max(0, e.loc.column - 120), e.loc.column + 60)));
  }
  fs.writeFileSync('_acorn_out.txt', o.join('\n'), 'utf8');
}
