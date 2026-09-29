const fs = require('fs');
const cp = require('child_process');
const { gbkEncode } = require('./_recover.js');

// The CSS source is a GBK-mojibake UTF-8 blob. Decoding its GBK bytes as UTF-8
// yields essentially-correct Chinese, except for a handful of lossy spots that
// land inside comments (a 3-byte char whose 3rd byte was clobbered to 0x3F,
// which surfaces as U+FFFD). HEAD's blob preserves a 2-byte prefix at most of
// those spots, so it is the best recovery base; the remaining lossy spots are
// disambiguated from the surrounding comment phrase.
const B = gbkEncode(cp.execSync('git show HEAD:frontend/public/ops-web/css/workbench.css',
  { maxBuffer: 1e8 }).toString('utf8'));
let t = Buffer.from(B).toString('utf8');

const F = '\uFFFD';
const FIX = [
  ['页面级布局（独立页面壳' + F + '?', '页面级布局（独立页面壳） '],
  ['新增页签）样' + F + '?', '新增页签）样式 '],
  ['运营视图：运营视' + F + '? 内联', '运营视图：运营视图 内联'],
  ['样式已作用域化' + F + '?', '样式已作用域化） '],
  ['/* ' + F + '?zoom 缩放', '/* 用 zoom 缩放'],
  ['避免裁' + F + '?空白', '避免裁剪 空白'],
  ['/* 内联' + F + '?100vh', '/* 内联后 100vh'],
  ['单产品视图移' + F + '?view-board', '单产品视图移入 view-board'],
  ['#ops-view-root' + F + '?', '#ops-view-root） '],
  ['SFC 抽取的样' + F + '?', 'SFC 抽取的样式 '],
  ['前项限' + F + '?后项限制' + F + '?', '前项限制 后项限制） '],
  ['备案公示预览' + F + '?', '备案公示预览） '],
  ['APP手机壳模' + F + '?', 'APP手机壳模板 '],
  ['销售渠' + F + '?Tab', '销售渠道 Tab'],
  // generic: a trailing "place holder + ?" right before a comment close is a
  // lost char that the phrase does not need, so it collapses to one space.
  [F + '?*/', ' */'],
  [F + '?', ' '],
];
for (const [a, b] of FIX) t = t.split(a).join(b);

fs.writeFileSync('_css.final', t, 'utf8');
process.stdout.write('written _css.final  FFFD=' + (t.match(new RegExp(F, 'g')) || []).length +
  ' CJK=' + [...t].filter((c) => c.codePointAt(0) >= 0x4e00 && c.codePointAt(0) <= 0x9fff).length + '\n');
