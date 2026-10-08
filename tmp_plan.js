const fs = require('fs');
const dir = '场景设计/ah_cti_poc/工作流配置/wf_runner/logs/chain_full';
const f = fs.readdirSync(dir).find((x) => x.startsWith('wf_sub_02'));
const s = fs.readFileSync(dir + '/' + f, 'utf8');
const lines = s.split(/\r?\n/);
// find the line containing plan_json with baseInfo
const idx = lines.findIndex((l) => l.includes('plan_json') && l.includes('baseInfo'));
console.log('line', idx + 1);
let line = lines[idx];
// unescape \"
line = line.replace(/\\"/g, '"').replace(/\\\\/g, '\\');
// Attempt to find the plan_json object
const key = '"plan_json": "';
const start = line.indexOf(key);
console.log('has plan_json key:', start >= 0);
if (start >= 0) {
  let rest = line.slice(start + key.length);
  // plan_json value is a JSON string embedded; find the terminating quote before , "status" or end
  const planStr = rest.replace(/", "status".*$/, '');
  console.log('--- plan_json raw (first 2000) ---');
  console.log(planStr.slice(0, 2000));
  try {
    const obj = JSON.parse(planStr.replace(/\\"/g, '"'));
    console.log('--- parsed keys ---');
    console.log('baseInfo keys:', Object.keys(obj.baseInfo || {}));
    console.log('optionalInfo keys:', Object.keys(obj.optionalInfo || {}));
    console.log('acctMonth:', JSON.stringify(obj.optionalInfo && obj.optionalInfo.acctMonth));
    console.log('printContent:', JSON.stringify(obj.optionalInfo && obj.optionalInfo.printContent));
    console.log('baseInfo.fixFee:', obj.baseInfo && obj.baseInfo.fixFee);
  } catch (e) {
    console.log('parse err', e.message);
  }
}
