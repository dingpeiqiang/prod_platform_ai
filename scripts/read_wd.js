const fs = require('fs');
const s = fs.readFileSync(process.argv[2], 'utf8');
// Find any path-like strings mentioning the project
const re = /[A-Za-z]:[\/\\][^\s"'<>]*prod_platform_ai[^\s"'<>]*/g;
const found = [...new Set(s.match(re) || [])];
found.slice(0, 20).forEach((m) => console.log(m));
if (!found.length) console.log('no absolute project paths in workspace.xml');
