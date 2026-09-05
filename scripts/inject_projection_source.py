#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""R4-Step3 前置注入：把 config_message_projection.json 的映射与默认值完整迁入模板，
使模板成为投影配置的单一事实源（SSOT）。

动作：
1. familyBasePrc：修正 message_path 漂移（phoneMbrInfo/broadBandMbrInfo 为顶层节点，
   去掉 optionalInfo. 前缀——依据 xlsx 家庭基础套餐报文样例 31/40/48 行层级）。
2. categoryMappings 逐条落模板：按 message_path 定位字段注入 business_key/draft_aliases；
   定位不到则补最小字段项（expireSms 短信场景）。
3. defaultsByCategory → 各品类模板 message_projection.defaults（报文侧默认值归模板单源）。
4. template.schema.json：message_projection 增加 defaults 属性。
5. product-config.ttl：品类个体块加 GENERATED 标记（供生成器替换）。

运行一次即完成迁移；迁移后 config_message_projection.json 可由 ProjectionConfigGenerator 再生。
"""
import json
import os
import glob
import re

BASE = os.path.join(os.path.dirname(__file__), '..', 'backend-app', 'src', 'main', 'resources')
TPL_DIR = os.path.join(BASE, 'ontologies', 'templates')
CFG_PATH = os.path.join(BASE, 'ontology', 'config_message_projection.json')
TTL_PATH = os.path.join(BASE, 'ontology', 'product-config.ttl')

cfg = json.load(open(CFG_PATH, encoding='utf-8'))
templates = {}
for f in glob.glob(os.path.join(TPL_DIR, '*.json')):
    t = json.load(open(f, encoding='utf-8'))
    templates[t['template_id']] = (f, t)

FIELD_LABELS = {
    'expireSms': '到期提醒短信',
    'sysNoteExpire': '到期提醒短信',
}

changed = []


def save(tid):
    path, t = templates[tid]
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(t, fh, ensure_ascii=False, indent=2)
        fh.write('\n')
    changed.append(tid)


# 1. familyBasePrc message_path 漂移修正
_, fam = templates['familyBasePrc']
for f in fam.get('fields', []):
    mp = f.get('message_path') or ''
    if mp.startswith('optionalInfo.phoneMbrInfo.'):
        f['message_path'] = mp[len('optionalInfo.'):]
    elif mp.startswith('optionalInfo.broadBandMbrInfo.'):
        f['message_path'] = mp[len('optionalInfo.'):]
save('familyBasePrc')

# 2. categoryMappings 逐条落模板
for cat, mappings in cfg['categoryMappings'].items():
    if cat not in templates:
        print('!! 跳过（模板不存在）:', cat)
        continue
    _, tpl = templates[cat]
    fields = tpl.get('fields', [])
    by_mp = {}
    for f in fields:
        if f.get('message_path'):
            by_mp.setdefault(f['message_path'], f)
    for m in mappings:
        biz, path = m['business'], m['path']
        aliases = m.get('aliases') or []
        field = by_mp.get(path)
        if field is None:
            # 尝试 field_code/business_key 命中
            field = next((f for f in fields
                          if f.get('field_code') == biz or f.get('business_key') == biz), None)
            if field is not None and not field.get('message_path'):
                field['message_path'] = path
        if field is None:
            # 补最小字段项（短信场景；报文侧系统字段，非表单元素）
            field = {
                'field_code': biz,
                'label': FIELD_LABELS.get(biz, biz),
                'type': 'input',
                'required': False,
                'section': path.split('.')[0],
                'field_class': 'projection',
                'business_key': biz,
                'message_path': path,
            }
            if aliases:
                field['draft_aliases'] = aliases
            fields.append(field)
            by_mp[path] = field
            print('[+] %s 新增字段 %s -> %s' % (cat, biz, path))
        else:
            if not field.get('business_key'):
                field['business_key'] = biz
            if aliases and not field.get('draft_aliases'):
                field['draft_aliases'] = aliases
    save(cat)

# 3. defaultsByCategory → message_projection.defaults
for cat, dd in cfg['defaultsByCategory'].items():
    if cat not in templates:
        continue
    _, tpl = templates[cat]
    mp_obj = tpl.setdefault('message_projection', {})
    if 'defaults' not in mp_obj:
        mp_obj['defaults'] = dd
        save(cat)

# 4. schema：message_projection.defaults
schema_path = os.path.join(TPL_DIR, 'schema', 'template.schema.json')
schema = json.load(open(schema_path, encoding='utf-8'))
mp_schema = schema['properties']['message_projection']
mp_schema['properties']['defaults'] = {
    'type': 'object',
    'description': '品类报文投影默认值（业务键→默认值；生成 config_message_projection.json#defaultsByCategory）。'
                   '语义与 ConfigMessageProjector.applyCategoryDefaults 对齐：仅当草稿缺省时回填。',
    'additionalProperties': {'type': ['string', 'number', 'boolean']}
}
with open(schema_path, 'w', encoding='utf-8') as fh:
    json.dump(schema, fh, ensure_ascii=False, indent=2)
    fh.write('\n')
changed.append('schema/template.schema.json')

# 5. TTL 品类个体块 GENERATED 标记
ttl = open(TTL_PATH, encoding='utf-8').read()
begin = '# Individuals - categories'
if 'GENERATED: category-individuals' not in ttl:
    ttl = ttl.replace(
        begin,
        '# BEGIN GENERATED: category-individuals (FROM templates/*.json DO NOT EDIT)\n'
        '# 由 TemplateTtlGenerator 生成；品类元数据单源 = category_meta。\n' + begin)
    end_marker = ':PROD_SCHEME_PMP_001'
    ttl = ttl.replace(end_marker,
                      '# END GENERATED: category-individuals\n\n' + end_marker, 1)
    with open(TTL_PATH, 'w', encoding='utf-8') as fh:
        fh.write(ttl)
    changed.append('ontology/product-config.ttl')

print('变更文件数:', len(changed))
for c in sorted(set(changed)):
    print(' -', c)
