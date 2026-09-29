const fs = require('fs');
const needle = process.argv[2];
const base = 'frontend/public/ops-web/js/data-presets.js.bak.recon';
const recon = fs.readFileSync(base, 'utf8');
const res = fs.readFileSync(base + '.resolved', 'utf8');
for (const [name, s] of [['RECON', recon], ['RESOLVED', res]]) {
  let i = s.indexOf(needle);
  while (i >= 0) {
    console.log(name, i, JSON.stringify(s.slice(i - 60, i + 60)));
    i = s.indexOf(needle, i + 1);
  }
}
