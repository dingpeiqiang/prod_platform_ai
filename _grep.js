const { execSync } = require('child_process');
const fs = require('fs');
for (const pat of ['046万', '合约用户', '在网86', '1.2亿', '青春版']) {
  try {
    const out = execSync('git grep -l "' + pat + '"', { encoding: 'utf8' });
    fs.appendFileSync('_grep.txt', '== ' + pat + '\n' + out + '\n', 'utf8');
  } catch (e) { fs.appendFileSync('_grep.txt', '== ' + pat + ' : none\n', 'utf8'); }
}
