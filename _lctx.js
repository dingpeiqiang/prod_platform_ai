const fs = require('fs');
const f = process.argv[2];
const src = fs.readFileSync(f, 'utf8');
const lines = src.split('\n');
const ln = parseInt(process.argv[3], 10);
const col = parseInt(process.argv[4] || '-1', 10);
const l = lines[ln - 1];
if (col >= 0) {
  for (let c = Math.max(0, col - 80); c < Math.min(l.length, col + 80); c += 1) {}
  console.log('LEN', l.length, 'COL', col);
  console.log(JSON.stringify(l.slice(Math.max(0, col - 100), col + 60)));
} else {
  console.log('LEN', l.length);
  console.log(JSON.stringify(l.slice(0, 300)));
}
