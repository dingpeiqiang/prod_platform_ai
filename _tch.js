const fs = require('fs');
const t = fs.readFileSync('frontend/public/ops-web/js/ops-view.js.bak.recon', 'utf8');
const i = t.indexOf('月同比\u0002/span>');
for (let k = i; k < i + 12; k++) console.log(k, t.charCodeAt(k).toString(16), JSON.stringify(t[k]));
console.log('regex test', /(\u4e00-\u9fff)\u0002\/span>/.test(t.slice(i, i + 12)));
console.log('regex test2', /比\u0002\//.test('比\u0002/'));
console.log('replaced', JSON.stringify(t.slice(i, i + 12).replace(/(\u4e00-\u9fff)\u0002\/span>/, '$1</span>')));
