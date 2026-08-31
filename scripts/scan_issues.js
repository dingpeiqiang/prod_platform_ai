const fs = require('fs');
const s = fs.readFileSync('backend-app/src/main/java/com/sitech/prodai/service/ProductOntologyService.java', 'utf8').split(/\r?\n/);
const idx = s.findIndex((l) => l.includes('private Map<String, Object> toWorkOrderMap'));
if (idx < 0) {
  console.log('toWorkOrderMap not found; searching for workOrder map build');
  const alt = s.findIndex((l) => l.includes('toWorkOrderMap'));
  console.log('first mention at L' + (alt + 1) + ': ' + s[alt].trim());
} else {
  console.log(s.slice(idx, idx + 45).map((l, i) => (i + idx + 1) + ': ' + l).join('\n'));
}
