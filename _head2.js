const guess = '/* ---- 运营视图2 \u4f9d\u8d56\u7684 ECharts';
const b = Buffer.from(guess, 'utf8');
console.log('utf8 bytes:', b.toString('latin1'));
console.log('cp936-decoded:', new TextDecoder('gbk').decode(b));
console.log('bak head     :', JSON.stringify(require('fs').readFileSync('frontend/public/ops-web/js/vendor-echarts.js.bak').slice(0, 60).toString('latin1')));
