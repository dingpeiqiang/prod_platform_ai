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
  if (next === '%') return '(';             // 利润率(%) / 活跃用户率(%)
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
  if (p === '*') return false;   // comment terminator */
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
      if (next === '-' && isDigit(chars[i + 2])) continue;   // 月同比-4.5%
      if (isStruct(nextNS)) { out.push(dqAsSingle ? "'" : (strCh || '"')); inStr = false; strCh = ''; continue; }
      if (next === '-' && isDigit(chars[i + 2])) continue;   // 月同比-4.5%
      if (isDigit(next)) {
        if (prev === '享') { out.push('5'); continue; }
        if (prev === '餐') { out.push('1'); continue; }
        if (prev === '发') { out.push('2'); continue; }
        if (prev === '置') { out.push('8'); continue; }   // 在网86万
        if (prev === '费') { out.push('5'); continue; }   // 月费59元
        continue;                                          // continued label+value in one string
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

// Targeted repairs on the recon text before byte-level resolution. These cover
// lossy spots where a whole multibyte char plus a following ASCII byte vanished;
// the exact surrounding template/chart context makes the intent unambiguous.
function fixRecon(t) {
  t = t.replace(/\u0002?到达用户\+月净增\u0002 \+ 同\u0002环比/g, '到达用户+月净增 + 同环比');
  t = t.replace(/到达用户<b>([\d.]+) 一\u0002\/b>/g, '到达用户<b>$1 万</b>');
  t = t.replace(/到达用户\(一\u0002<\/span>/g, '到达用户(户)</span>');
  t = t.replace(/月净增\u0002一\u0002<\/span>/g, '月净增(户)</span>');
  t = t.replace(/月净增用户\u0002\/td>/g, '月净增用户</td>');
  t = t.replace(/到达用户\(一\u0002',type:'bar'/g, "到达用户(户)',type:'bar'");
  t = t.replace(/月净增\u0002一\u0002',type:'bar'/g, "月净增(户)',type:'bar'");
  t = t.replace(/月净增\u0002\/span>/g, '月净增</span>');
  // '一' recovered where the lost char was a unit ('户' in '(户)', '万' in '12.5 万')
  t = t.replace(/\(一\u0002/g, '(户)');
  t = t.replace(/([\d.] )一\u0002\/(b>)/g, '$1万</$2');
  t = t.replace(/一\u0002\/(span>)/g, '户</$1');
  // a lost byte right before a closing HTML tag is always the tag's '<'
  t = t.replace(/\u0002\/([a-z][a-zA-Z0-9]*>)/g, '</$1');
  t = t.replace(/当年\u0002(?=\d)/g, '当年');
  t = t.replace(/年\u0002(?=\d? ?月)/g, '年1');
  return t;
}

function resolve(text) {
  const t = fixRecon(text)
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
  // 3-byte glyphs whose final byte was lost; the reconstructed head is a
  // look-alike (U+2440/U+2580/U+2180/U+21C0), resolved here by context.
  res = res.replace(/\u2440['?]?/g, '①');                 // 章节标题 ---
  res = res.replace(/(closeHealth\(\)\\?">)\u2180['?]?/g, '$1←');   // 返回按钮
  res = res.replace(/(closeRules\(\)\\?">)\u2180['?]?/g, '$1←');
  res = res.replace(/(yoy-down[^\r\n]*?)\u2580/g, '$1▼');
  res = res.replace(/(yoy-up[^\r\n]*?)\u2580/g, '$1▲');
  res = res.replace(/(trigger\\?">)\u2580/g, '$1▼');
  res = res.replace(/(q-trend\\*">)\u2580[\\/'?]?(?=[.\d])/g, '$1▲');
  res = res.replace(/\u2580[\\'\/]?(?=[.\d])/g, '▲');
  res = res.replace(/\u2580/g, '▼');
  res = res.replace(/(\\?">)\u21c0['?]?/g, '$1⇩');          // 导出按钮
  // marker chars produced by lossy recovery
  res = res.replace(/\u2700/g, '，');
  res = res.replace(/\u2180/g, '→');
  res = res.replace(/\u2240/g, '≥');
  // yoy arrow + polarity: the caret glyph was lost; polarity is signalled by '-'
  res = res.replace(
    /raw\.indexOf\('-'\) >= 0 \|\| raw\.indexOf\('→'\) >= 0/,
    "raw.indexOf('-') >= 0",
  );
  res = res.replace(/\? '▀' : '▀'/g, "? '▼' : '▲'");
  // template-literal interpolation `${...}` where the '$' was lost; the preceding
  // '、'/'，' were really the quote marks '「'/'」' around the interpolated value
  res = res.replace(/、\?\{([^}]*)\}」/g, '「${$1}」');
  res = res.replace(/工单号，\?\{/g, '工单号：${');
  res = res.replace(/、\?\{/g, '「${');
  res = res.replace(/，\?\{/g, '，${');
  // A lost '$' before '{' only forms a template when it follows a CJK char;
  // in minified vendor code a bare `?{` is a ternary between ASCII operands.
  res = res.replace(/([^\x00-\x7f])\?\{/g, '$1${');
  // '、?' / '——?' at sentence end are a mis-recovered '。' plus a lost byte
  res = res.replace(/、\?\*\//g, ' */');
  res = res.replace(/、\?/g, '。');
  res = res.replace(/——\?/g, '—— ');
  res = res.replace(/—— ?Tab/g, '—— Tab');
  // mis-recovered CJK words (context-unambiguous in these sources)
  const WORDS = [
    ['玀', '率'], ['生户', '生成'], ['一自', '一致'], ['表过', '表述'],
    ['汇态', '汇总'], ['双查', '双轴'], ['草用', '草稿'], ['否分', '否则'],
    ['身付', '身份'], ['优元', '优先'], ['数维', '数组'], ['命同', '命名'],
    ['进元/', '进入 '], ['默认回营', '默认回退'], ['致用00086', '致电10000'],
    ['tz诉', '投诉'], ['维着', '维系'],
  ];
  for (const [a, b] of WORDS) res = res.split(a).join(b);
  // formulas: '∀' lost minus sign, 'À' lost divide sign
  res = res.replace(/'∀ ?'|\?∀\?|'∀'|∀'|'∀/g, ' - ');
  res = res.replace(/À\?|À'/g, '÷ ');
  // NOTE: no generic `- -` -> ` - ` fold; it corrupts minified `--` (a-- / .row--warn)
  // '_cap' placeholder '￀' replaced a lost final byte of a 2-char context
  res = res.split('模￀').join('模板');
  res = res.split('传￀?').join('传入 ');
  res = res.split('配￀?').join('配置。');
  res = res.split('报￀?').join('报告/');
  res = res.split('提￀"').join('提交"');
  res = res.split('key:￀?}').join('key: 值 }');
  res = res.split("拖拽调宽￀'").join('拖拽调宽 ');
  res = res.replace(/￀'-->/g, ' -->');
  res = res.split('单位，').join('单位：');
  res = res.replace(/横坐标'=/g, '横坐标 =');
  res = res.replace(/\?\uFFFD\uFFFD/g, '/');
  return res;
}

if (require.main === module) {
  const f = process.argv[2];
  const res = resolve(fs.readFileSync(f, 'utf8'));
  fs.writeFileSync(f + '.resolved', res, 'utf8');
  console.log('written', f + '.resolved', 'quoteBal=', (res.match(/'/g) || []).length % 2);
}
module.exports = { resolve };
