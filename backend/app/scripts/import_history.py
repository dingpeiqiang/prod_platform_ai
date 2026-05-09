"""
历史数据导入器 v3（JSONL 原生，支持 entities 层级结构）

目录结构：
  config/import_data/
    ├── {formCode}.schema.json     ← 元数据声明（可选，用于值转换）
    └── {formCode}.data.jsonl     ← 实例数据（每行一个完整 JSON 对象）

data.jsonl 格式（核心）：
  - 每行一个 JSON 对象 = 一条表单实例
  - 天然支持嵌套、数组、任意层级
  - 字段编码必须与本体 fieldCode 一致
  - 以 # 开头的行为注释

  简单表单示例（扁平）：
    {"applicant_name":"张三","department":"技术部","leave_type":"年假","start_date":"2026-03-15",...}

  复杂表单示例（多实体/嵌套）：
    {"seq_no":"BJ22024070100001","reporter":"JT1-中国电信集团","action_type":"A-新增","name":"5G畅享套餐129",...}

  带子表的复杂实例（订单+明细）：
    {"order_no":"SO20260315001","customer_name":"腾讯科技",...,"items":[{"product":"云服务器","qty":1,"price":128000},...]}

schema.json 格式（可选）：
  {
    "formCode": "tariff_filing_publicity",
    "formName": "资费备案公示",
    "description": "电信业务资费备案数据",
    "valueTransform": { "action_type": { "A": "A-新增" } },
    "userField": "reporter"
  }

使用方式：
  # 列出所有可导入的表单
  python -m app.scripts.import_history --list

  # 预览不写入
  python -m app.scripts.import_history tariff_filing_publicity --dry-run

  # 导入单个表单
  python -m app.scripts.import_history leave

  # 导入全部
  python -m app.scripts.import_history --all

  # 限制条数
  python -m app.scripts.import_history tariff_filing_publicity --limit 50
"""

import json
import sys
import uuid
import logging
import argparse
from pathlib import Path
from datetime import datetime
from typing import Dict, Any, List, Optional, Iterator
from collections import defaultdict

from app.utils import parse_date, format_date

PROJECT_ROOT = Path(__file__).parent.parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

DATA_DIR = PROJECT_ROOT / "config" / "import_data"

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)-8s] %(message)s',
    datefmt='%H:%M:%S'
)
logger = logging.getLogger("import_history")


def load_schema(form_code: str) -> Optional[Dict[str, Any]]:
    """加载 schema.json 声明文件（可选）"""
    schema_path = DATA_DIR / f"{form_code}.schema.json"
    if not schema_path.exists():
        return None

    with open(schema_path, 'r', encoding='utf-8') as f:
        schema = json.load(f)

    logger.info("加载 schema: form_code=%s", schema.get('formCode', form_code))
    return schema


def discover_forms() -> List[Dict[str, str]]:
    """自动发现 import_data/ 目录下所有可导入的表单"""
    forms = []
    if DATA_DIR.exists():
        for jsonl_file in sorted(DATA_DIR.glob("*.data.jsonl")):
            form_code = jsonl_file.stem.replace('.data', '')
            schema = load_schema(form_code)
            forms.append({
                'formCode': form_code,
                'formName': schema.get('formName', form_code) if schema else form_code,
                'dataType': 'jsonl',
                'dataFile': str(jsonl_file.relative_to(PROJECT_ROOT)),
                'hasSchema': schema is not None,
                'description': schema.get('description', '') if schema else '',
            })
        for csv_file in sorted(DATA_DIR.glob("*.data.csv")):
            form_code = csv_file.stem.replace('.data', '')
            if any(f['formCode'] == form_code for f in forms):
                continue
            schema = load_schema(form_code)
            forms.append({
                'formCode': form_code,
                'formName': schema.get('formName', form_code) if schema else form_code,
                'dataType': 'csv',
                'dataFile': str(csv_file.relative_to(PROJECT_ROOT)),
                'hasSchema': schema is not None,
                'description': schema.get('description', '') if schema else '',
            })
    return forms


def read_jsonl_records(file_path: Path) -> Iterator[Dict[str, Any]]:
    """从 JSONL 文件逐行读取记录"""
    with open(file_path, 'r', encoding='utf-8') as f:
        for line_no, line in enumerate(f, 1):
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            try:
                obj = json.loads(line)
                if isinstance(obj, dict) and obj:
                    yield obj
            except json.JSONDecodeError as e:
                logger.warning("JSONL 第 %d 行解析失败: %s (内容前50字符: %s)",
                               line_no, e, line[:50])


def read_csv_records(form_code: str, schema: Optional[Dict]) -> Iterator[Dict[str, str]]:
    """从 CSV 文件读取记录（兼容旧格式）"""
    import csv
    csv_path = DATA_DIR / f"{form_code}.data.csv"
    if not csv_path.exists():
        return

    with open(csv_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            clean = {k: v.strip() for k, v in row.items() if v and v.strip()}
            if clean:
                yield clean


def load_all_records(form_code: str, schema: Optional[Dict]) -> List[Dict[str, Any]]:
    """加载所有实例数据（优先 JSONL，回退 CSV）"""
    jsonl_path = DATA_DIR / f"{form_code}.data.jsonl"
    if jsonl_path.exists():
        records = list(read_jsonl_records(jsonl_path))
        logger.info("读取 JSONL: %s → %d 条记录", jsonl_path.name, len(records))
        return records

    records = list(read_csv_records(form_code, schema))
    if records:
        logger.info("读取 CSV: %s → %d 条记录 (兼容模式)", form_code + '.data.csv', len(records))
    return records


def flatten_record(record: Dict[str, Any], parent_key: str = '', sep: str = '.') -> Dict[str, Any]:
    """将嵌套的 JSON 记录展平为一层键值对"""
    items = {}
    for k, v in record.items():
        new_key = f"{parent_key}{sep}{k}" if parent_key else k
        if isinstance(v, dict):
            items.update(flatten_record(v, new_key, sep))
        elif isinstance(v, list):
            for i, item in enumerate(v):
                if isinstance(item, dict):
                    items.update(flatten_record(item, f"{new_key}.{i}", sep))
                else:
                    existing = items.get(new_key, [])
                    if isinstance(existing, list):
                        existing.append(str(item))
                        items[new_key] = existing
                    else:
                        items[new_key] = [str(item)]
        else:
            items[new_key] = v
    for k, v in items.items():
        if isinstance(v, list):
            items[k] = ', '.join(v)
    return items


def transform_value(value: Any, field_code: str,
                    transforms: Dict[str, Dict[str, str]]) -> str:
    """按 valueTransform 配置转换字段值"""
    if field_code in transforms:
        value_str = str(value)
        transform_map = transforms[field_code]
        return transform_map.get(value_str, value_str)
    return str(value)


def apply_transforms(flat_data: Dict[str, Any],
                     transforms: Dict[str, Dict[str, str]]) -> Dict[str, str]:
    """对所有字段应用值转换"""
    return {
        fc: transform_value(fv, fc, transforms)
        for fc, fv in flat_data.items() if fv is not None and str(fv).strip()
    }


def reverse_transform_value(value: Any, field_code: str,
                            ontology: Optional[Dict]) -> str:
    """反向转换 - 将中文标签转换为编码"""
    if not ontology:
        return str(value)

    value_str = str(value).strip()
    entities = ontology.get('entities', [])
    for entity in entities:
        fields = entity.get('fields', [])
        for field_def in fields:
            if field_def.get('fieldCode') == field_code:
                options = field_def.get('options', [])
                for opt in options:
                    if isinstance(opt, dict):
                        opt_label = opt.get('label', '')
                        opt_value = opt.get('value', '')
                        if opt_label and value_str == opt_label:
                            return opt_value
                        if opt_value and value_str == opt_value:
                            return opt_value
                    elif isinstance(opt, str):
                        if value_str == opt:
                            return opt
    return value_str


def apply_reverse_transforms(flat_data: Dict[str, Any],
                             ontology: Optional[Dict]) -> Dict[str, str]:
    """对所有字段应用反向转换（中文 → 编码）"""
    if not ontology:
        return {fc: str(fv) for fc, fv in flat_data.items() if fv is not None and str(fv).strip()}

    result = {}
    for fc, fv in flat_data.items():
        if fv is not None and str(fv).strip():
            result[fc] = reverse_transform_value(fv, fc, ontology)
    return result


def _extract_timestamp(record: Dict[str, Any]) -> datetime:
    """从记录中提取时间戳"""
    time_candidates = [
        'submitted_at', 'created_at', 'submit_time',
        'online_day', 'order_date', 'expense_date',
        'start_date', 'created_time'
    ]
    for key in time_candidates:
        val = record.get(key)
        if val is None:
            continue
        if isinstance(val, datetime):
            return val
        if isinstance(val, str):
            parsed = parse_date(val)
            if parsed:
                return parsed
    return datetime.now()


def _ensure_template(db, form_code: str, form_name: str,
                     schema: Optional[Dict] = None,
                     sample_record: Optional[Dict] = None) -> int:
    """确保 FormTemplate 存在，不存在则创建，存在则更新"""
    from app.models.form import FormTemplate
    from app.services.ontology_service import OntologyService

    template = db.query(FormTemplate).filter(
        FormTemplate.form_code == form_code
    ).first()

    enriched_schema = schema.copy() if schema else {}
    try:
        ontology_result = OntologyService.get_form_constraint(form_code)
        if ontology_result.get('success'):
            ontology = ontology_result.get('constraints', {})
            if 'entities' in ontology and ontology['entities']:
                enriched_schema['entities'] = ontology['entities']
                logger.info("从本体加载字段配置: form_code=%s", form_code)
    except Exception as e:
        logger.warning("从本体加载字段配置失败: %s", e)

    if sample_record:
        enriched_schema['sample'] = sample_record

    if template:
        if enriched_schema:
            updated = False
            if form_name and template.form_name != form_name:
                template.form_name = form_name
                updated = True
            current_schema = template.schema or {}
            for key, value in enriched_schema.items():
                if key not in current_schema or current_schema[key] != value:
                    current_schema[key] = value
                    updated = True
            if updated:
                template.schema = current_schema
                template.updated_at = datetime.now()
                db.commit()
                logger.info("更新 FormTemplate: form_code=%s", form_code)
        return template.id

    template_schema = enriched_schema or {"formCode": form_code, "formName": form_name}
    template = FormTemplate(
        form_code=form_code,
        form_name=form_name,
        schema=template_schema,
        version=1,
        is_active=True,
        created_at=datetime.now(),
        updated_at=datetime.now()
    )
    db.add(template)
    db.commit()
    db.refresh(template)
    logger.info("创建 FormTemplate: form_code=%s id=%d", form_code, template.id)
    return template.id


def _flush_batch(db, batch: List[tuple]) -> int:
    """批量写入 FormInstance + FormHistory"""
    from app.models.form import FormHistory

    count = 0
    for instance, flat_data, user_id in batch:
        db.add(instance)
        db.commit()
        db.refresh(instance)

        for fc, fv in flat_data.items():
            history = FormHistory(
                form_instance_id=instance.id,
                field_code=fc,
                field_value=str(fv),
                user_id=user_id
            )
            db.add(history)
        count += 1

    db.commit()
    logger.info("批量写入 %d 条实例 (%d 条字段历史)",
               count, sum(len(fd) for _, fd, _ in batch))
    return count


def _preview_report(form_code: str, records: List[Dict],
                    schema: Optional[Dict], nested_fields: List[str]):
    """dry-run 预览报告"""
    value_transforms = schema.get('valueTransform', {}) if schema else {}
    user_field = schema.get('userField', '') if schema else ''

    print(f"\n{'=' * 64}")
    print(f"  [DRY RUN] 预览: {form_code}")
    print(f"{'=' * 64}")
    print(f"  总记录数:   {len(records)}")
    print(f"  数据格式:   {'JSONL (支持嵌套)' if nested_fields or any(isinstance(v, (dict, list)) for v in records[0].values()) else '扁平'}")

    if nested_fields:
        print(f"  嵌套字段:   {', '.join(nested_fields)}")
        for nf in nested_fields[:3]:
            sample_val = records[0].get(nf)
            if isinstance(sample_val, list) and sample_val:
                print(f"    [{nf}] 数组, {len(sample_val)} 个元素")
                if isinstance(sample_val[0], dict):
                    sub_keys = list(sample_val[0].keys())[:5]
                    print(f"           子字段: {', '.join(sub_keys)}")
            elif isinstance(sample_val, dict):
                sub_keys = list(sample_val.keys())[:8]
                print(f"    [{nf}] 对象, 子字段: {', '.join(sub_keys)}")

    if user_field:
        users = set()
        for r in records:
            flat = flatten_record(r)
            val = flat.get(user_field) or r.get(user_field)
            if val:
                users.add(str(val)[:20])
        print(f"  用户字段:   {user_field} ({len(users)} 个不同用户)")

    field_stats = defaultdict(lambda: defaultdict(int))
    for record in records:
        flat = flatten_record(record)
        for fc, fv in flat.items():
            if fv and str(fv).strip():
                fv_t = transform_value(fv, fc, value_transforms)
                field_stats[fc][fv_t] += 1

    top_fields = [fc for fc in field_stats.keys() if '.' not in fc]
    nested_sub_fields = [fc for fc in field_stats.keys() if '.' in fc]

    if top_fields:
        print(f"\n  顶层字段值分布 (Top 5):")
        print(f"  {'-' * 56}")
        for fc in sorted(top_fields):
            values = field_stats[fc]
            top5 = sorted(values.items(), key=lambda x: x[1], reverse=True)[:5]
            print(f"  {fc}:")
            for val, cnt in top5:
                display_val = val[:40] + '...' if len(val) > 40 else val
                print(f"    {display_val}: {cnt}次")

    if nested_sub_fields:
        print(f"\n  嵌套子字段值分布 (Top 3):")
        print(f"  {'-' * 56}")
        for fc in sorted(nested_sub_fields)[:10]:
            values = field_stats[fc]
            top3 = sorted(values.items(), key=lambda x: x[1], reverse=True)[:3]
            display_fc = fc[-50:] if len(fc) > 50 else fc
            top_str = ', '.join(f"{v[0][:30]}:{v[1]}" for v in top3)
            print(f"  {display_fc}: {top_str}")

    print(f"\n  首条记录样例:")
    print(f"  {'-' * 56}")
    sample = json.dumps(records[0], ensure_ascii=False, indent=2)
    if len(sample) > 500:
        print(f"  {sample[:500]}\n  ... (截断, 完整长度 {len(sample)})")
    else:
        for line in sample.split('\n'):
            print(f"  {line}")

    print(f"{'=' * 64}\n")


def _print_report(stats: Dict[str, Any]):
    """导入完成统计报告"""
    print(f"\n{'=' * 64}")
    print(f"  导入报告: {stats['formCode']}")
    print(f"{'=' * 64}")
    print(f"  源记录数:     {stats['totalSource']}")
    print(f"  实际导入:     {stats['totalImported']}")
    print(f"  跳过(空数据): {stats['totalSkipped']}")
    print(f"  错误:         {stats['totalErrors']}")

    if stats.get('nestedFields'):
        print(f"  嵌套字段:     {', '.join(stats['nestedFields'])}")

    if stats.get('fieldStats'):
        top_fields = {fc: vals for fc, vals in stats['fieldStats'].items() if '.' not in fc}
        nested_fields = {fc: vals for fc, vals in stats['fieldStats'].items() if '.' in fc}

        if top_fields:
            print(f"\n  顶层字段分布 (Top 5):")
            print(f"  {'-' * 56}")
            for fc in sorted(top_fields.keys()):
                values = top_fields[fc]
                top5 = sorted(values.items(), key=lambda x: x[1], reverse=True)[:5]
                print(f"  {fc}:")
                for val, cnt in top5:
                    display_val = val[:40] + '...' if len(val) > 40 else val
                    print(f"    {display_val}: {cnt}次")

        if nested_fields:
            unique_nested = set(nested_fields.keys())
            print(f"\n  嵌套子字段: {len(unique_nested)} 个")

    print(f"{'=' * 64}\n")


def import_form_data(
    form_code: str,
    dry_run: bool = False,
    limit: Optional[int] = None
) -> Dict[str, Any]:
    """导入单个表单类型的历史数据"""
    from app.core.database import get_db
    from app.models.form import FormInstance, FormTemplate, FormHistory
    from app.services.ontology_service import OntologyService

    stats = {
        'formCode': form_code,
        'totalSource': 0,
        'totalImported': 0,
        'totalSkipped': 0,
        'totalErrors': 0,
        'fieldStats': {},
        'nestedFields': [],
        'dryRun': dry_run
    }

    schema = load_schema(form_code)
    form_name = schema.get('formName', form_code) if schema else form_code
    value_transforms = schema.get('valueTransform', {}) if schema else {}
    user_field = schema.get('userField', '') if schema else ''

    ontology = None
    try:
        ontology_result = OntologyService.get_form_constraint(form_code)
        if ontology_result.get('success'):
            ontology = ontology_result.get('constraints', {})
    except Exception as e:
        logger.warning("从本体加载字段配置失败: %s", e)

    records = load_all_records(form_code, schema)
    if limit:
        records = records[:limit]

    stats['totalSource'] = len(records)
    if not records:
        logger.warning("[%s] 没有实例数据，跳过导入", form_code)
        return stats

    sample = records[0]
    nested_fields = [k for k, v in sample.items() if isinstance(v, (dict, list))]
    stats['nestedFields'] = nested_fields

    if dry_run:
        _preview_report(form_code, records, schema, nested_fields)
        return stats

    db_gen = get_db()
    db = next(db_gen)

    try:
        sample_record = None
        if records:
            first_record = records[0]
            flat_sample = flatten_record(first_record)
            sample_with_codes = apply_reverse_transforms(flat_sample, ontology)
            sample_record = sample_with_codes

        template_id = _ensure_template(db, form_code, form_name, schema, sample_record)

        batch_size = 100
        batch_buffer: List[tuple] = []
        imported = 0

        for i, record in enumerate(records):
            try:
                flat = flatten_record(record)
                flat_for_storage = apply_reverse_transforms(flat, ontology)

                if not flat_for_storage:
                    stats['totalSkipped'] += 1
                    continue

                flat_for_report = apply_transforms(flat, value_transforms)
                for fc, fv in flat_for_report.items():
                    if fc not in stats['fieldStats']:
                        stats['fieldStats'][fc] = defaultdict(int)
                    stats['fieldStats'][fc][fv] += 1

                user_id = None
                if user_field and user_field in flat_for_storage:
                    user_id = flat_for_storage[user_field]
                elif user_field and user_field in record:
                    user_id = str(record[user_field])

                record_with_codes = record.copy()
                for fc, fv in flat_for_storage.items():
                    if '.' not in fc:
                        record_with_codes[fc] = fv

                instance = FormInstance(
                    form_id=f"imp_{form_code}_{uuid.uuid4().hex[:12]}",
                    template_id=template_id,
                    data=record_with_codes,
                    version=1,
                    status='submitted',
                    user_id=user_id,
                    submitted_at=_extract_timestamp(record)
                )
                batch_buffer.append((instance, flat_for_storage, user_id))

                if len(batch_buffer) >= batch_size:
                    imported += _flush_batch(db, batch_buffer)
                    batch_buffer = []

            except Exception as e:
                stats['totalErrors'] += 1
                logger.warning("[%s] 第 %d 条处理失败: %s", form_code, i + 1, e)

        if batch_buffer:
            imported += _flush_batch(db, batch_buffer)

        stats['totalImported'] = imported
        _print_report(stats)

    except Exception as e:
        logger.exception("[%s] 导入异常: %s", form_code, e)
        stats['error'] = str(e)
    finally:
        try:
            db.close()
        except Exception:
            pass

    return stats


def main():
    parser = argparse.ArgumentParser(
        description='历史数据导入器 v3',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python -m app.scripts.import_history --list              # 列出所有可导入表单
  python -m app.scripts.import_history leave               # 导入请假数据
  python -m app.scripts.import_history sales_order --dry-run  # 预览销售订单
  python -m app.scripts.import_history tariff_filing_publicity --limit 50
  python -m app.scripts.import_history --all                # 导入全部

文件格式:
  config/import_data/
    ├── {formCode}.schema.json    可选：值转换规则
    └── {formCode}.data.jsonl     必填：每行一个JSON对象
        """)
    parser.add_argument('form_code', nargs='?', help='表单编码')
    parser.add_argument('--all', action='store_true', help='导入全部表单')
    parser.add_argument('--dry-run', action='store_true', help='仅预览')
    parser.add_argument('--limit', type=int, default=None, help='限制条数')
    parser.add_argument('--list', action='store_true', help='列出可导入表单')
    args = parser.parse_args()

    if args.list:
        forms = discover_forms()
        if not forms:
            print("config/import_data/ 目录下没有可导入的数据")
            print("\n请创建文件:")
            print("  config/import_data/{formCode}.data.jsonl")
            print("  config/import_data/{formCode}.schema.json (可选)")
        else:
            print(f"\n  {'编码':20s} {'名称':12s} {'类型':6s} {'描述'}")
            print(f"  {'-'*20} {'-'*12} {'-'*6} {'-'*30}")
            for f in forms:
                schema_mark = "[schema]" if f['hasSchema'] else ""
                print(f"  {f['formCode']:20s} {f['formName']:12s} [{f['dataType']:>4s}] {schema_mark:8s} {f['description']}")
            print(f"\n共 {len(forms)} 个表单类型可导入")
        return

    if args.all:
        all_forms = discover_forms()
        form_codes = [f['formCode'] for f in all_forms]
    elif args.form_code:
        form_codes = [args.form_code]
    else:
        print("请指定表单编码或 --all")
        forms = discover_forms()
        if forms:
            print("\n可用表单:", ", ".join(f['formCode'] for f in forms))
        return

    total_imported = 0
    for fc in form_codes:
        logger.info("=" * 50)
        logger.info("开始导入: %s", fc)
        result = import_form_data(fc, dry_run=args.dry_run, limit=args.limit)
        total_imported += result.get('totalImported', 0)
        if result.get('error'):
            logger.error("导入失败: %s → %s", fc, result['error'])

    logger.info("=" * 50)
    logger.info("全部完成, 共导入 %d 条记录", total_imported)


if __name__ == '__main__':
    main()