const fs = require('fs');

// A bare '?' on a JS comment line is a leftover sentinel for one lost byte.
// Resolved by short surrounding phrases (unambiguous in these docs); anything
// not matched falls back to dropping the sentinel. Only comment lines touched.
const FILES = [
  'frontend/public/ops-web/js/data-presets.js',
  'frontend/public/ops-web/js/app-config-workbench.js',
];

const PHRASE = [
  ['第?轮', '第1轮'],
  ['第?转?', '第一步 '],
  ['值?}', '值 }'],
  ['内容?→', '内容 →'],
  ['标?已解析?', '标记"已解析"'],
  ['标?已', '标记"已'],
  ['一?已解析?', '"已解析"'],
  ['标?AI', '标"AI'],
  ['标?A', '标"AI'],
  ['标?复', '标"复'],
  ['一?AI', '一 "AI'],
  ['一?下一个', '一/下一个'],
  ['用?AI', '用 AI'],
  ['用?label', '用 label'],
  ['用?emit', '用 emit'],
  ['用?window', '用 window'],
  ['检查?bizName', '检查 bizName'],
  ['后项限制?ruleType', '后项限制 ruleType'],
  ['统一付?formData', '统一从 formData'],
  ['用于?value', '用于 value'],
  ['身份?资源值', '身份/资源值'],
  ['rows 分?formData', 'rows 分到 formData'],
  ['需求?+ 业务模板', '需求 + 业务模板'],
  ['商家签?launch', '商品签 launch'],
  ['"签?launch', '"签 launch'],
  ['参考?*。', '参考**。'],
  ['文本案?文本域', '文本案/文本域'],
  ['工作流环节?->', '工作流环节 ->'],
  ['数据?——', '数据 ——'],
  ['页签?——', '页签 ——'],
  ['表述?——', '表述 ——'],
  ['只有在?', '只有在'],
  ['单?·', '单 ·'],
  ['同?/ ', '同/'],
  ['，?---', '， ---'],
  ['归属?草稿', '归属'],
  ['草稿?→待审核', '草稿 →待审核'],
  ['草稿?态', '草稿态'],
  ['草稿?批量', '草稿/批量'],
  ['草稿?流程', '草稿/流程'],
  ['流程权?审批', '流程/权责/审批'],
  ['审批?流程', '审批/流程'],
  ['命名?改名', '命名/改名'],
  ['复制?编辑', '复制/编辑'],
  ['批量配置?模式', '批量配置"模式'],
  ['预览?免填单', '预览/免填单'],
  ['免填单?短信', '免填单/短信'],
  ['数组?表格', '数组/表格'],
  ['草稿?等', '草稿 等'],
  ['流程?stepper', '流程/stepper'],
  ['事件?—', '事件 ——'],
  ['-- - ', '---'],
];

const isCommentLine = (L) => {
  const t = L.trim();
  return t.startsWith('//') || t.startsWith('*') || t.startsWith('/*');
};

function fixLine(L) {
  let s = L;
  for (const [a, b] of PHRASE) if (s.includes(a)) s = s.split(a).join(b);
  if (!s.includes('?')) return s;
  // fallback: drop each remaining sentinel with neighbour-aware spacing
  let out = '';
  for (let i = 0; i < s.length; i++) {
    if (s[i] !== '?') { out += s[i]; continue; }
    const pre = out[out.length - 1] || '';
    const nx = s[i + 1];
    if (pre === '，' || pre === '、') continue;                 // already punctuated
    if (pre === '→') { out += ' '; continue; }
    if (nx === '→') continue;
    if (pre === '=' && nx === '=') continue;
    if (pre === '-' && nx === '-') continue;
    if (pre === '*') continue;
    if (nx === '\r' || nx === '\n' || nx === undefined) { out += '。'; continue; }
    if (nx === '/' || nx === ',' || nx === '）' || nx === ')') continue;
    if (nx === ' ') { continue; }
    // before an ASCII identifier -> separating space
    if (/[A-Za-z0-9_$]/.test(nx)) { out += ' '; continue; }
    continue;
  }
  return out;
}

for (const f of FILES) {
  const lines = fs.readFileSync(f, 'utf8').split('\n');
  const out = lines.map((L) => (L.includes('?') && isCommentLine(L) ? fixLine(L) : L));
  fs.writeFileSync(f, out.join('\n'), 'utf8');
  const rem = out.filter((L) => L.includes('?') && isCommentLine(L));
  console.log(f.split('/').pop() + ' remainingCommentQ=' + rem.length);
  for (const L of rem) console.log('   ' + L.trim().slice(0, 130));
}
