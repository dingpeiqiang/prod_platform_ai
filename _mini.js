const c = require('./frontend/node_modules/@vue/compiler-dom');
const tpl = `<span :data-reason="parsedFilled[field.key] || '根据用户需求解析得出?'">x</span>`;
const out = c.compile(tpl, { mode: 'function' }).code;
console.log(JSON.stringify(out));
try { new Function('Vue', out); console.log('OK'); } catch (e) { console.log('FAIL', e.message); }
