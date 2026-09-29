const fs = require('fs');
const { gbkEncode, reconstruct } = require('./_recover.js');
function dump(file, needle) {
  const bak = fs.readFileSync(file, 'utf8');
  const B = gbkEncode(bak);
  const { bytes, plan } = reconstruct(B);
  // find needle in bak text; map to approximate position by searching decoded
  const tmp = new TextDecoder('utf8').decode(Buffer.from(bytes));
  let idx = tmp.indexOf(needle);
  if (idx < 0) { console.log(file, needle, 'not found'); return; }
  console.log('---', file, needle, 'at', idx);
  console.log('recon:', JSON.stringify(tmp.slice(idx - 15, idx + 30)));
  // plan entries near idx (positions are byte offsets ~ char offsets for ascii+CJK)
  for (const p of plan) {
    if (Math.abs(p.pos - idx) < 40) console.log('plan', JSON.stringify(p));
  }
}
dump('frontend/public/ops-web/js/vendor-echarts.js.bak', '_showIndicator(l,l');
dump('frontend/public/ops-web/js/app-config-workbench.js.bak', '.test(reason)');
