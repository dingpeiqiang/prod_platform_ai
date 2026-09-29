const fs = require('fs');

const AFTER_CLOSE = new Set([
  ',', ')', ']', '}', ';', ':', '\r', '\n', ' ', '\t',
  '+', '-', '<', '>', '=', '!', '&', '|', '^', '~',
]);
const isStruct = (c) => c === undefined || AFTER_CLOSE.has(c);
const isCJK = (c) => c && c.codePointAt(0) >= 0x2e80;
const isDigit = (c) => c >= '0' && c <= '9';
const isAlpha = (c) => /[A-Za-z]/.test(c || '');

// prev-char => forced content byte when followed by a content char
const UNIT_PREV = new Set(['元', '名', '置', '类', '费', '率', '价', '量', '权', '条', '单', '张']);

// explicit (prev + next) => byte
const PAIR = {
  '包\u0000': null,
};

// CJK chars that follow a unit separator "元/"
const UNIT_NEXT = new Set(['分', '月', '年', '日', '权', '条', '张', '钟', '秒', '户']);

// contextual content resolution; returns byte string or null
function contentFor(prev, next) {
  const unitish = isDigit(next) || isAlpha(next) || UNIT_NEXT.has(next);
  if (prev === '元') return unitish ? '/' : '"';
  if (UNIT_PREV.has(prev) && unitish) return '/';
  if (isDigit(next)) {
    if (prev === '享') return '5';
    if (prev === '餐' || prev === '套餐') return '1';
    if (prev === '发') return '2';
    if (prev === '，') return '3';
    if (prev === 'ↀ') return '4';
    return '0';
  }
  if (next === '基') return '"';            // 智家包"基本信息"
  if (next === '畅') return '"';            // "畅享
  if (prev === '包') return '"';
  if (prev === '月' && next === '日') return '1';   // 9月1日
  if (prev === '月' && next === '年') return '/';   // 周/月/年
  if (prev === '单' && next === '变') return '"';   // 立即"变更次月
  if (prev === '订' && next === '次') return '1';   // 限订1次
  if (next === '.' && isCJK(prev)) return '/';      // 正则字面量收尾 /....test
  return null;
}

function isRegexStart(out) {
  let k = out.length - 1;
  while (k >= 0 && out[k] === ' ') k--;
  if (k < 0) return true;
  const p = out[k];
  return '=(,:[!&|?{};<>+-*%^~'.includes(p) || p === '';
}

function resolveLine(line, dqAsSingle) {
  const SENT = '\u0002';
  const chars = Array.from(line);
  const out = [];
  let inStr = false;
  let strCh = '';           // quote char that opened the current string
  for (let i = 0; i < chars.length; i++) {
    const c = chars[i];
    if (global.TR && i >= 8433 && i <= 8442) console.log('ITER i='+i+' c='+JSON.stringify(c)+' cp='+c.codePointAt(0).toString(16)+' inStr='+inStr+' strCh='+JSON.stringify(strCh));
    if (c === '\\') { out.push(c, chars[i + 1] || ''); i++; continue; }
    if (!inStr && c === '/' && isRegexStart(out)) {
      // consume regex literal as-is (quotes inside are not delimiters)
      out.push(c);
      i++;
      let inClass = false;
      for (; i < chars.length; i++) {
        const rc = chars[i];
        if (rc === SENT) {
          // a lost byte inside a regex is almost always the closing slash,
          // unless this '?' is a genuine regex quantifier/group modifier
          const pc = chars[i - 1] || '';
          const nx = chars[i + 1];
          const isQuant = '+*?{}'.includes(pc);
          const beforeCloser = nx === undefined || nx === '.' || nx === ',' || nx === ';';
          if (isQuant || !beforeCloser) { out.push('?'); continue; }
          out.push('/');
          break;
        }
        out.push(rc);
        if (rc === '\\') { out.push(chars[i + 1] || ''); i++; continue; }
        if (rc === '[') inClass = true;
        else if (rc === ']') inClass = false;
        else if (rc === '/' && !inClass) break;
      }
      continue;
    }
    if (c === "'" || c === '"') {
      if (inStr && c === strCh) { inStr = false; strCh = ''; }
      else if (!inStr) { inStr = true; strCh = c; }
      out.push(c);
      continue;
    }
    if (c !== SENT) { out.push(c); continue; }

    const prev = out[out.length - 1] || '';
    const next = chars[i + 1];
    let j = i + 1, nextNS = next;
    while (nextNS === ' ') nextNS = chars[++j];

    // effective quote char to emit for this context
    const openQ = dqAsSingle ? "'" : '"';
    if (inStr) {
      if (nextNS === '(') { out.push(' '); continue; }
      if (nextNS === '|') { out.push(' '); continue; }
      if (nextNS === strCh) { continue; }   // lost continuation absorbed by next quote
      if (isStruct(nextNS)) { out.push(dqAsSingle ? "'" : (strCh || '"')); inStr = false; strCh = ''; continue; }
      if (isDigit(next)) {
        if (prev === '享') { out.push('5'); continue; }
        if (prev === '餐') { out.push('1'); continue; }
        if (prev === '发') { out.push('2'); continue; }
        if (prev === '置') { out.push('8'); continue; }   // 在网86万
        if (prev === '费') { out.push('5'); continue; }   // 月费59元
        out.push(dqAsSingle ? "'" : '"');                  // quoted metric
        continue;
      }
      const cc = contentFor(prev, next);
      if (cc !== null) { out.push(dqAsSingle && cc === '"' ? "'" : cc); continue; }
      out.push(openQ);   // inner emphasis quote
      continue;
    }
    // outside string: content only in CJK/unit contexts, else ternary
    const cc = contentFor(prev, next);
    if (cc !== null && (isCJK(prev) || isCJK(next))) {
      out.push(dqAsSingle && cc === '"' ? "'" : cc);
      continue;
    }
    out.push('\u0001');   // unresolved sentinel; resolved later by context (never a real "?")
  }
  return out.join('');
}

function resolve(text) {
  const t = text
    .replace(/\u2700/g, '，')
    .replace(/\u2180/g, '→')
    .replace(/\u2240/g, '≥');
  return applyFixes(t.split('\n').map((l) => {
    const embedded = /^\s*(window\.__OPS_JS|ConfigWorkbench\.template)\s*=\s*"/.test(l);
    const r = resolveLine(l, embedded);
    if (embedded) return r.replace(/\u0001/g, "'");
    return fixUnclosed(r);
  }).join('\n'));
}

// A line with an odd apostrophe count has an unclosed string. Repair the last
// quote-ish marker so the line is syntactically balanced. Any leftover sentinel
// (\u0001) is turned into "'" when the line needs one more open quote, else "?".
function fixUnclosed(line) {
  let s = line;
  const q0 = (s.match(/'/g) || []).length;
  if (q0 % 2 === 1) {
    const lastSent = s.lastIndexOf('\u0001');
    const lastDq = s.lastIndexOf('"');
    if (lastSent >= 0 && lastSent > lastDq) s = s.slice(0, lastSent) + "'" + s.slice(lastSent + 1);
    else if (lastDq >= 0) s = s.slice(0, lastDq) + "'" + s.slice(lastDq + 1);
  }
  return s.replace(/\u0001/g, '?');
}

function applyFixes(res) {
  res = res.replace(/'(名|否)'/g, "'否'");
  // marker chars produced by lossy recovery
  res = res.replace(/\u2700/g, '，');
  res = res.replace(/\u2180/g, '→');
  res = res.replace(/\u2240/g, '≥');
  return res;
}

if (require.main === module) {
  const f = process.argv[2];
  const res = resolve(fs.readFileSync(f, 'utf8'));
  fs.writeFileSync(f + '.resolved', res, 'utf8');
  console.log('written', f + '.resolved', 'quoteBal=', (res.match(/'/g) || []).length % 2);
}
module.exports = { resolve, resolveLine };
