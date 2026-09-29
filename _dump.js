const fs = require('fs');
const f = process.argv[2];
const buf = fs.readFileSync(f);
const t = buf.toString('utf8');
const lines = t.split('\n');
const n = parseInt(process.argv[3] || '18', 10) - 1;
const line = lines[n];
console.log('LINE:', JSON.stringify(line));
const b = Buffer.from(line, 'utf8');
console.log('BYTES:', b.toString('hex').match(/../g).join(' '));
for (const ch of line) {
  const c = ch.codePointAt(0);
  if (c > 0x7F) process.stdout.write(`[${ch}=U+${c.toString(16).toUpperCase()}]`);
  else process.stdout.write(ch);
}
console.log();
