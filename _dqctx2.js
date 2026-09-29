const fs = require('fs');
const f = process.argv[2] || '_chk_ops-view.js';
const lineNo = parseInt(process.argv[3] || '3', 10);
const line = fs.readFileSync(f, 'utf8').split('\n')[lineNo - 1];
const idx = parseInt(process.argv[4] || '45364', 10);
console.log(JSON.stringify(line.slice(idx - 60, idx + 60)));
