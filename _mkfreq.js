const fs = require('fs');
const path = require('path');
const uni = {}; const bi = {};
function bump(m, k) { m[k] = (m[k] || 0) + 1; }
function feed(s, w) {
  let prev = '';
  for (const ch of s) {
    const cp = ch.codePointAt(0);
    if (cp >= 0x2e80) { uni[ch] = (uni[ch] || 0) + w; if (prev) bi[prev + ch] = (bi[prev + ch] || 0) + w; prev = ch; }
    else if (cp === 0x22 || cp === 0x27 || cp === 0x3a || cp === 0x2c) prev = ch;
    else prev = '';
  }
}
// primary target corpus
feed(fs.readFileSync('_ve543.js', 'utf8'), 3);
// broad echarts corpus available in node_modules (contains locale strings)
const dist = 'frontend/node_modules/echarts/dist';
if (fs.existsSync(dist)) {
  for (const f of fs.readdirSync(dist)) {
    if (f.endsWith('.js')) feed(fs.readFileSync(path.join(dist, f), 'utf8'), 1);
  }
}
// recovered source files (general ops-web vocabulary)
for (const f of ['data-presets', 'wb-context', 'ops-view', 'app-config-workbench']) {
  const p = '_chk_' + f + '.js';
  if (fs.existsSync(p)) feed(fs.readFileSync(p, 'utf8'), 1);
}
// corrupted raw recon (second opinion, low weight)
for (const f of ['data-presets', 'wb-context', 'ops-view', 'app-config-workbench']) {
  const p = 'frontend/public/ops-web/js/' + f + '.js.bak.recon';
  if (fs.existsSync(p)) feed(fs.readFileSync(p, 'utf8'), 1);
}

// curated corpus: domain phrases whose exact characters are known good. Feeds
// both unigrams and bigrams so same-head/different-tail picks (率 vs 玀) resolve.
const CURATED = [
  '利润率', '活跃用户率', '付费活跃率', '营销费用占比', '同比增长', '环比增长',
  '利润率(%)', '活跃用户率(%)', '付费活跃率(%)',
].join(' ');
feed(CURATED, 20);
fs.writeFileSync('_freq.json', JSON.stringify({ uni, bi }));
console.log('uni', Object.keys(uni).length, 'bi', Object.keys(bi).length);
for (const c of ['月', '最', '。', '、', '国', '图', '二', '于', '三', '一', '四', '五', '六', '片', '叠', '铺', '▶', '≈', '≤', '≥'])
  console.log(c, 'uni', uni[c] || 0);
