const fs = require('fs');
const vm = require('vm');
const code = fs.readFileSync('frontend/public/ops-web/js/data-presets.js', 'utf8');
const sandbox = {
  console,
  window: {},
  JSON, Object, Array, String, Number, Boolean, Date, Math, RegExp, Error, parseInt, parseFloat, isNaN,
};
sandbox.window.Vue = { reactive: (x) => x };
vm.createContext(sandbox);
try {
  vm.runInContext(code, sandbox, { filename: 'data-presets.js' });
} catch (e) {
  console.log('RUN ERROR:', e.message);
}
const cfg = sandbox.window.__CFG || {};
const lpc = cfg.launchProductConfig;
console.log('launchProductConfig exists:', !!lpc);
if (lpc) {
  console.log('marketingSections length:', (lpc.marketingSections || []).length);
  (lpc.marketingSections || []).forEach((s) => {
    console.log(' section:', s.key, s.title, 'fields:', (s.fields || []).map((f) => f.key + '=' + f.value).join(', '));
  });
}
