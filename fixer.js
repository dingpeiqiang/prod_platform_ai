const fs = require('fs');
const dir = 'frontend/public/ops-web/js/';
const files = ['app-config-workbench.js', 'wb-context.js', 'data-presets.js'];

const ENDERS = new Set(['', '\r', ',', '}', ']', ')', ';', ':', '+']);

// Insert a closing quote after a lost-char '?' inside a single-quoted string.
// Handles nested strings inside template literal ${...} expressions.
function fixLine(ln) {
  let out = '';
  let i = 0;
  const stack = ['code']; // modes: code, str1, str2, tpl
  const top = () => stack[stack.length - 1];

  while (i < ln.length) {
    const c = ln[i];
    const m = top();

    if (m === 'str1' || m === 'str2') {
      const q = m === 'str1' ? "'" : '"';
      if (c === '\\') { out += c + (ln[i + 1] || ''); i += 2; continue; }
      if (c === q) { stack.pop(); out += c; i++; continue; }
      if (m === 'str1' && c === '?') {
        let j = i + 1;
        while (j < ln.length && (ln[j] === ' ' || ln[j] === '\t')) j++;
        const nx = ln[j] || '';
        if (ENDERS.has(nx)) { out += "?'"; stack.pop(); i++; continue; }
      }
      out += c; i++; continue;
    }

    if (m === 'tpl') {
      if (c === '\\') { out += c + (ln[i + 1] || ''); i += 2; continue; }
      if (c === '`') { stack.pop(); out += c; i++; continue; }
      if (c === '$' && ln[i + 1] === '{') { stack.push('code'); out += '${'; i += 2; continue; }
      if (c === '?' && ln[i + 1] === '{') { stack.push('code'); out += '${'; i += 2; continue; }
      out += c; i++; continue;
    }

    // code
    if (c === "'") { stack.push('str1'); out += c; i++; continue; }
    if (c === '"') { stack.push('str2'); out += c; i++; continue; }
    if (c === '`') { stack.push('tpl'); out += c; i++; continue; }
    if (c === '}' && stack.length > 1) { stack.pop(); out += c; i++; continue; }
    out += c; i++; continue;
  }
  return out;
}

function countUnescaped(ln, ch) {
  let n = 0;
  for (let i = 0; i < ln.length; i++) {
    if (ln[i] === '\\') { i++; continue; }
    if (ln[i] === ch) n++;
  }
  return n;
}

// repair an unterminated regex literal: /...<missing slash>.method(
function fixRegex(ln) {
  const at = ln.indexOf('= /');
  if (at < 0) return ln;
  const start = at + 2;
  for (let i = start + 1; i < ln.length; i++) {
    const c = ln[i];
    if (c === '\\') { i++; continue; }
    if (c === '/') return ln;
    if (c === '.' && /^\.\w+\(/.test(ln.slice(i))) {
      return ln.slice(0, i) + '/' + ln.slice(i);
    }
  }
  return ln;
}

// insert a missing closing backtick for an unterminated template literal
function closeTemplate(ln) {
  const start = ln.indexOf('`');
  if (start < 0) return ln;
  let depth = 0;
  for (let i = start + 1; i < ln.length; i++) {
    const c = ln[i];
    if (c === '\\') { i++; continue; }
    if ((c === '$' || c === '?') && ln[i + 1] === '{') { depth++; i++; continue; }
    if (depth > 0 && c === '}') { depth--; continue; }
    if (depth === 0) {
      if (c === '`') return ln;
      if (c === ',' || c === ')' || c === ']' || c === '}' || c === ';') {
        return ln.slice(0, i) + '`' + ln.slice(i);
      }
    }
  }
  return ln.replace(/\r?$/, '`$&');
}

for (const f of files) {
  const src = fs.readFileSync(dir + f, 'utf8');
  const lines = src.split('\n');
  let changed = 0;
  const out = lines.map((ln) => {
    let res = fixRegex(fixLine(ln));
    if (countUnescaped(res, '`') % 2 === 1) res = closeTemplate(res);
    if (res !== ln) changed++;
    return res;
  });
  fs.writeFileSync('fixed_' + f, out.join('\n'), 'utf8');
  console.log(f, 'changed=' + changed);
}
