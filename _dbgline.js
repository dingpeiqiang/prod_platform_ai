const fs = require('fs');
const s = fs.readFileSync('frontend/public/ops-web/js/data-presets.js.bak.recon', 'utf8').split('\n');
const line = s[50];
console.log('RAW: ' + line);
[...line].forEach((c, i) => {
  if (c === '?' || c === "'" || c === '"') {
    console.log(i + ' ' + JSON.stringify(c) + ' prev=' + JSON.stringify(line[i - 1]) + ' next=' + JSON.stringify(line[i + 1]));
  }
});
