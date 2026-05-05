"""
数据修复脚本：修复现有历史数据的 template_id 链接
============================================
问题：
  - form_templates 表为空（0条）
  - form_instances 的 template_id 全部硬编码为 0（10条）
  - form_history 有53条历史记录，但推荐引擎查不到

修复策略：
  1. 根据每个 form_instance.data 字段特征反推 form_code
  2. 为每种 form_code 创建 FormTemplate 记录
  3. 回填 form_instance.template_id 为正确的值

用法：python scripts/fix_template_id.py
"""
import sys
import os
import json
import logging

# 确保可以导入 app 模块
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.core.database import SessionLocal, engine, Base
from app.models.form import FormTemplate, FormInstance, FormHistory
from app.core.config_loader import config_loader

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("fix_template_id")

# ── 字段 → form_code 映射表 ──────────────────────────────────────────
FIELD_SIGNATURES = {
    "sales_order": {"customer_name", "customer_phone", "order_amount", "order_date", "remark"},
    "expense":    {"expense_type", "amount", "expense_date", "description"},
    "leave":      {"applicant_name", "department", "leave_type", "start_date", "end_date", "reason"},
}


def detect_form_code(data_keys: set) -> str | None:
    """根据字段集合反推 form_code（用 Jaccard 相似度匹配）"""
    best_match = None
    best_score = 0.5  # 至少50%重叠才认

    for code, signature in FIELD_SIGNATURES.items():
        intersection = data_keys & signature
        union = data_keys | signature
        score = len(intersection) / len(union) if union else 0
        logger.debug("  %s: overlap=%d/%d score=%.2f", code, len(intersection), len(union), score)
        if score > best_score:
            best_score = score
            best_match = code

    return best_match


def main():
    db = SessionLocal()

    # ── 1. 统计现状 ────────────────────────────────────────────────
    print("\n=== 当前状态 ===")
    ft_count = db.query(FormTemplate).count()
    fi_count = db.query(FormInstance).count()
    fh_count = db.query(FormHistory).count()
    print(f"FormTemplate: {ft_count} 条")
    print(f"FormInstance: {fi_count} 条")
    print(f"FormHistory: {fh_count} 条")

    # ── 2. 扫描所有 instance，反推 form_code 并分组 ───────────────────
    print("\n=== 分析 FormInstance ===")
    instances = db.query(FormInstance).order_by(FormInstance.id).all()
    code_to_instances = {}  # form_code -> [instance]

    for inst in instances:
        data_keys = set((inst.data or {}).keys())
        detected = detect_form_code(data_keys)
        if not detected:
            logger.warning("  inst#%d 无法识别字段集: %s", inst.id, sorted(data_keys))
            continue

        if detected not in code_to_instances:
            code_to_instances[detected] = []
        code_to_instances[detected].append(inst)
        logger.info("  inst#%d -> %s (keys=%s)", inst.id, detected, sorted(data_keys))

    print(f"\n识别到 {len(code_to_instances)} 种表单类型:")
    for code, group in code_to_instances.items():
        print(f"  {code}: {len(group)} 个实例")

    # ── 3. 创建 FormTemplate 记录 ────────────────────────────────────
    print("\n=== 创建 FormTemplate 记录 ===")
    template_map = {}  # form_code -> template_id

    for form_code, group in code_to_instances.items():
        # 从本体配置中取 schema 信息
        ontology = config_loader.get_ontology(form_code)

        # 用第一个实例的数据字段构建 schema
        sample_data = (group[0].data or {})
        fields = []
        for field_key in sorted(sample_data.keys()):
            value = sample_data[field_key]
            field_def = {
                "fieldCode": field_key,
                "fieldName": field_key,
                "fieldType": _infer_type(value),
                "required": True,
                "disabled": False,
                "hidden": False,
                "ruleDescription": "",
                "recommend": [],
                "defaultValue": None,
                "options": [],
            }
            fields.append(field_def)

        schema = {
            "formCode": form_code,
            "formName": ontology.get("formName", form_code) if ontology else form_code,
            "fields": fields,
        }

        # upsert
        existing = db.query(FormTemplate).filter(FormTemplate.form_code == form_code).first()
        if existing:
            existing.schema = schema
            existing.version += 1
            existing.updated_at = func.now()
            tid = existing.id
            logger.info("  更新 %s (id=%d)", form_code, tid)
        else:
            ft = FormTemplate(
                form_code=form_code,
                form_name=ontology.get("formName", form_code) if ontology else form_code,
                schema=schema,
                version=1,
                is_active=True,
            )
            db.add(ft)
            db.flush()  # 获取 id
            tid = ft.id
            logger.info("  创建 %s (id=%d)", form_code, tid)

        template_map[form_code] = tid

    db.commit()

    # ── 4. 回填 FormInstance.template_id ─────────────────────────────
    print("\n=== 回填 template_id ===")
    fixed_count = 0
    for form_code, group in code_to_instances.items():
        tid = template_map[form_code]
        for inst in group:
            old_tid = inst.template_id
            inst.template_id = tid
            logger.info("  inst#%d: %d -> %d (%s)", inst.id, old_tid, tid, form_code)
            fixed_count += 1

    db.commit()
    print(f"\n共修复 {fixed_count} 条 FormInstance 记录")

    # ── 5. 验证结果 ────────────────────────────────────────────────
    print("\n=== 修复后验证 ===")
    new_ft_count = db.query(FormTemplate).count()
    print(f"FormTemplate: {ft_count} -> {new_ft_count} 条")

    for ft in db.query(FormTemplate).all():
        cnt = db.query(FormInstance).filter(FormInstance.template_id == ft.id).count()
        print(f"  {ft.form_code} (id={ft.id}): {cnt} 个实例")

    db.close()
    print("\n数据修复完成! 现有历史数据已可被推荐引擎正常查询。")


def _infer_type(value):
    """根据值推断字段类型"""
    if isinstance(value, int) or (isinstance(value, str) and value.isdigit()):
        return "number"
    if isinstance(value, str) and "-" in value and len(value) == 10:
        return "date"
    if isinstance(value, str) and "@" in value:
        return "email"
    return "input"


from sqlalchemy import func


if __name__ == "__main__":
    main()
