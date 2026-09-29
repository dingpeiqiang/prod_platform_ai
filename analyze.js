const fs = require('fs');
const files = ['app-config-workbench.js', 'wb-context.js', 'data-presets.js'];
for (const f of files) {
  const s = fs.readFileSync('frontend/public/ops-web/js/' + f, 'utf8');
  for (let i = 0; i < s.length; i++) {
    if (s[i] === '?') {
      const after = s[i + 1] || '';
      const before = s[i - 1] || '';
      if (after !== "'") {
        console.log(f + ':' + (s.slice(0, i).split('\n').length) + ' after=' + JSON.stringify(after) + ' before=' + JSON.stringify(before));
      }
    }
  }
}
