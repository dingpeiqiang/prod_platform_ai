# -*- coding: utf-8 -*-
import io, os, re
p = os.path.join('src', 'main', 'java', 'com', 'sitech', 'prodai', 'controller', 'AppStoreV16Controller.java')
txt = io.open(p, encoding='utf-8', errors='ignore').read()
print('=== Class-level annotations ===')
for m in re.finditer(r'@(?:RequestMapping|RestController|GetMapping)\(([^)]*)\)', txt):
    print('  ', m.group(0)[:120])
print('len chars:', len(txt), 'KB:', len(txt) // 1024)
print('=== @XxxMapping("...") ===')
for m in re.finditer(r'@(Get|Post|Put|Delete|Request)Mapping\([^)]*"([^"]*)"[^)]*\)', txt):
    print('  @%s %s' % (m.group(1), m.group(2)))
print('=== substrings of interest ===')
for t in ['appstore', 'product-ontology', 'api/v1']:
    print('  count', t, '=', txt.count(t))
