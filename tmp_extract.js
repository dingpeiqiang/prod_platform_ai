const fs = require('fs');
const s = fs.readFileSync('frontend/public/ops-web/js/app-config-workbench.js', 'utf8');
const t = s.replace(/\\r\\n/g, '\n').replace(/\\n/g, '\n').replace(/\\"/g, '"');
const lines = t.split('\n');
const start = Number(process.argv[2] || 1);
const end = Number(process.argv[3] || 60);
for (let i = start - 1; i < end && i < lines.length; i++) {
  console.log((i + 1) + ': ' + lines[i]);
}
