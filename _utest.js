const r = require('./_resolve.js');
const cases = [
  '  window.__OPS_JS = "abc元?+def";',
  '  window.__OPS_JS = "abc?def";',
];
for (const c of cases) console.log(JSON.stringify(c), '=>', JSON.stringify(r.resolve(c)));
