const fs = require('fs');
const path = require('path');
const roots = process.argv.slice(2);
const hits = [];
function walk(dir, depth) {
  if (depth > 4) return;
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const e of entries) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      walk(p, depth + 1);
    } else if (e.name === 'understand-diagnosis.log') {
      const st = fs.statSync(p);
      hits.push(p + '  [mtime: ' + st.mtime.toISOString() + ', size: ' + st.size + ']');
    }
  }
}
roots.forEach((r) => walk(r, 0));
console.log(hits.length ? hits.join('\n') : 'no new diagnosis log found');
