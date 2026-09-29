const fs = require('fs');
const s = fs.readFileSync('frontend/public/ops-web/js/data-presets.js.bak.recon', 'utf8');
['\u2700', '\u2180', '\u2240'].forEach((c) => {
  const re = new RegExp('.{0,10}' + c + '.{0,10}', 'g');
  let m;
  const o = [];
  while ((m = re.exec(s))) o.push(m[0].replace(/\r/g, ''));
  fs.appendFileSync('_markers.txt', '== ' + c + ' (' + o.length + ')\n' + o.slice(0, 30).join('\n') + '\n', 'utf8');
});
fs.writeFileSync('_markers.txt', fs.readFileSync('_markers.txt', 'utf8'), 'utf8');
