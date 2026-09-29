const fs = require('fs');
for (const f of ['ops-view.js', 'app-config-workbench.js']) {
  const l = fs.readFileSync('frontend/public/ops-web/js/' + f + '.bak.recon', 'utf8').split('\n');
  console.log('=== ' + f);
  l.forEach((x, i) => {
    if (/=\s*"/.test(x) && x.length > 200) console.log((i + 1) + ': len=' + x.length + ' ' + x.slice(0, 70));
  });
}
