const { execSync } = require('child_process');
const fs = require('fs');
for (const pat of ['86万', '5,046', '合约用户', '在网']) {
  try {
    const out = execSync('git grep -l "' + pat + '" -- "*.json"', { encoding: 'utf8' });
    fs.appendFileSync('_grep2.txt', '== ' + pat + '\n' + out + '\n', 'utf8');
  } catch (e) { fs.appendFileSync('_grep2.txt', '== ' + pat + ' : none\n', 'utf8'); }
}
