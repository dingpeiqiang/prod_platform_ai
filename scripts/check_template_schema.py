# -*- coding: utf-8 -*-
"""离线完整校验模板 vs template.schema.json（与 JsonSchemaLiteValidator 同约束子集）。"""
import json, glob, re, os, sys

BASE = os.path.join(os.path.dirname(__file__), '..', 'backend-app', 'src', 'main', 'resources')
SCHEMA = json.load(open(os.path.join(BASE, 'ontologies', 'templates', 'schema', 'template.schema.json'), encoding='utf-8'))
DEFS = SCHEMA.get('definitions', {})

def type_ok(v, t):
    if isinstance(t, list):
        return any(type_ok(v, x) for x in t)
    checks = {
        'object': lambda x: isinstance(x, dict),
        'array': lambda x: isinstance(x, list),
        'string': lambda x: isinstance(x, str),
        'integer': lambda x: isinstance(x, int) and not isinstance(x, bool),
        'number': lambda x: isinstance(x, (int, float)) and not isinstance(x, bool),
        'boolean': lambda x: isinstance(x, bool),
        'null': lambda x: x is None,
    }
    return checks.get(t, lambda x: False)(v)

def validate(v, s, path, errs):
    if '$ref' in s:
        cur = SCHEMA
        for p in s['$ref'][2:].split('/'):
            if p == '#':
                continue
            cur = cur.get(p, {})
        return validate(v, cur, path, errs)
    t = s.get('type')
    if t is not None and not type_ok(v, t):
        errs.append('%s: type %s got %s' % (path, t, type(v).__name__))
        return errs
    if 'enum' in s and v not in s['enum']:
        errs.append('%s: enum violation %r' % (path, v))
    if isinstance(v, str):
        if 'pattern' in s and not re.search(s['pattern'], v):
            errs.append('%s: pattern %s mismatch %r' % (path, s['pattern'], v))
        if 'minLength' in s and len(v) < s['minLength']:
            errs.append('%s: minLength' % path)
        if 'maxLength' in s and len(v) > s['maxLength']:
            errs.append('%s: maxLength' % path)
    if isinstance(v, dict):
        for r in s.get('required', []):
            if r not in v:
                errs.append('%s: missing required %s' % (path, r))
        props = s.get('properties', {})
        if s.get('additionalProperties') is False:
            for k in v:
                if k not in props:
                    errs.append('%s.%s: additionalProperty' % (path, k))
        for k, val in v.items():
            if k in props:
                validate(val, props[k], '%s.%s' % (path, k), errs)
    if isinstance(v, list):
        if 'items' in s:
            for i, item in enumerate(v):
                validate(item, s['items'], '%s[%d]' % (path, i), errs)
        if 'minItems' in s and len(v) < s['minItems']:
            errs.append('%s: minItems' % path)
    return errs

fail = False
for f in sorted(glob.glob(os.path.join(BASE, 'ontologies', 'templates', '*.json'))):
    d = json.load(open(f, encoding='utf-8'))
    errs = validate(d, SCHEMA, '', [])
    name = os.path.basename(f)
    print('===', name, 'errors:', len(errs))
    for e in errs[:20]:
        print('  ', e)
    if errs:
        fail = True
sys.exit(1 if fail else 0)
