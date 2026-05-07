"""
AI 历史数据智能维护服务

核心能力：
  1. 分析：对指定表单的历史数据做统计分析，输出字段分布、异常检测、质量评分
  2. 生成：根据自然语言描述 + 本体字段，AI 生成历史实例数据（JSONL）
  3. 导入：将生成的数据写入 FormInstance + FormHistory

使用场景：
  - 用户说"帮我生成一些请假测试数据" → AI 生成符合本体的实例
  - 用户说"分析一下销售订单的数据质量" → 输出数据摘要报告
  - 用户说"导入资费备案的历史数据" → 调用 import_history 导入
"""

import json
import uuid
import logging
from datetime import datetime
from typing import Dict, Any, List, Optional
from pathlib import Path

from app.core.config_loader import config_loader
from app.services.llm_service import llm_service

logger = logging.getLogger("history_ai_service")

PROJECT_ROOT = Path(__file__).parent.parent.parent
DATA_DIR = PROJECT_ROOT / "config" / "import_data"


# ── Prompt 模板 ──────────────────────────────────────────────

_ANALYZE_SYSTEM_PROMPT = """你是一个数据质量分析专家。你会收到一个表单的历史数据统计摘要，需要：

1. 分析字段值的分布合理性
2. 检测可能的异常数据（如极端值、格式不一致、拼写错误）
3. 评估数据完整度（哪些字段经常缺失）
4. 给出改进建议

## 输出格式（JSON）

```json
{
  "qualityScore": 85,
  "totalRecords": 120,
  "completeness": {
    "score": 90,
    "missingFields": ["reason", "end_date"],
    "detail": "reason 字段缺失率 30%，建议..."
  },
  "distribution": [
    {
      "fieldCode": "leave_type",
      "fieldName": "请假类型",
      "analysis": "年假占比过高(70%)，病假仅5%，可能存在分类偏好偏差",
      "suggestion": "建议补充更多病假和事假样本以平衡分布"
    }
  ],
  "anomalies": [
    {
      "fieldCode": "days",
      "fieldName": "天数",
      "type": "extreme_value",
      "detail": "发现3条记录天数>30，疑似异常",
      "suggestion": "核实这些记录是否正确"
    }
  ],
  "summary": "整体数据质量良好，但请假类型分布不均匀，建议补充病假和事假样本",
  "recommendations": [
    "补充 reason 字段填写，目前缺失率30%",
    "增加病假和事假类型的数据样本",
    "核实天数>30的异常记录"
  ]
}
```"""




# ── 分析功能 ──────────────────────────────────────────────────

def analyze_history(form_code: str, db=None) -> Dict[str, Any]:
    """
    分析指定表单的历史数据质量。

    流程：
      1. 从 FormHistory + FormInstance 统计字段值分布
      2. 将摘要发给 LLM 分析
      3. 返回数据质量报告
    """
    from app.models.form import FormInstance, FormTemplate, FormHistory

    # 获取本体
    ontology = config_loader.get_ontology(form_code)
    if not ontology:
        return {"success": False, "message": f"表单 {form_code} 不存在"}

    # 收集统计信息
    stats = _collect_field_stats(form_code, db)

    if stats.get("totalRecords", 0) == 0:
        # 没有历史数据 → 返回空报告
        return {
            "success": True,
            "hasData": False,
            "formCode": form_code,
            "formName": ontology.get("formName", form_code),
            "message": "暂无历史数据，请导入历史数据以启用智能推荐",
            "qualityScore": 0,
            "totalRecords": 0,
            "recommendations": ["建议先导入历史数据以启用智能推荐"]
        }

    # 构建 LLM 分析 prompt
    ontology_summary = _build_ontology_summary(ontology)
    stats_summary = json.dumps(stats, ensure_ascii=False, indent=2)

    user_prompt = f"""请分析以下表单的历史数据质量：

## 表单本体
{ontology_summary}

## 历史数据统计
{stats_summary}

请输出 JSON 格式的数据质量分析报告。"""

    # 调用 LLM
    result_text = _call_llm(user_prompt, _ANALYZE_SYSTEM_PROMPT)
    if not result_text:
        # LLM 失败 → 返回基础统计
        return {
            "success": True,
            "hasData": True,
            "formCode": form_code,
            "formName": ontology.get("formName", form_code),
            "totalRecords": stats.get("totalRecords", 0),
            "fieldStats": stats.get("fieldStats", {}),
            "qualityScore": 50,
            "summary": "基础统计（AI 分析不可用）",
            "recommendations": []
        }

    # 解析 LLM 结果
    parsed = _safe_parse_json(result_text)
    if not parsed:
        return {
            "success": True,
            "hasData": True,
            "formCode": form_code,
            "formName": ontology.get("formName", form_code),
            "totalRecords": stats.get("totalRecords", 0),
            "fieldStats": stats.get("fieldStats", {}),
            "qualityScore": 50,
            "summary": "AI 分析结果解析失败",
            "recommendations": []
        }

    return {
        "success": True,
        "hasData": True,
        "formCode": form_code,
        "formName": ontology.get("formName", form_code),
        **parsed
    }

def apply_generated_data(form_code: str, db=None) -> Dict[str, Any]:
    """
    将已生成的 JSONL 数据导入到数据库。
    对应前端"确认导入"按钮。
    """
    from app.scripts.import_history import import_form_data

    try:
        result = import_form_data(form_code, dry_run=False)
        return {
            "success": True,
            "formCode": form_code,
            "importedCount": result.get("totalImported", 0),
            "errors": result.get("totalErrors", 0),
            "fieldStats": result.get("fieldStats", {}),
            "message": f"成功导入 {result.get('totalImported', 0)} 条历史数据"
        }
    except Exception as e:
        logger.exception("[apply_generated] 导入失败: %s", e)
        return {"success": False, "message": f"导入失败: {str(e)}"}


def list_available_data() -> List[Dict[str, Any]]:
    """列出 import_data/ 目录下可导入的数据文件"""
    from app.scripts.import_history import discover_forms
    return discover_forms()


def get_history_summary(form_code: str, db=None) -> Dict[str, Any]:
    """获取表单历史数据简要统计（不调用 LLM，纯数据库查询）"""
    stats = _collect_field_stats(form_code, db)
    ontology = config_loader.get_ontology(form_code)

    return {
        "formCode": form_code,
        "formName": ontology.get("formName", form_code) if ontology else form_code,
        "hasOntology": ontology is not None,
        **stats
    }


# ── 查询功能 ──────────────────────────────────────────────────

def query_history_records(
    form_code: str,
    start_date: Optional[str] = None,
    end_date: Optional[str] = None,
    user_id: Optional[str] = None,
    page: int = 1,
    page_size: int = 20,
    db=None
) -> Dict[str, Any]:
    """
    分页查询历史数据记录。

    Args:
        form_code: 表单编码
        start_date: 开始日期 (YYYY-MM-DD)
        end_date: 结束日期 (YYYY-MM-DD)
        user_id: 用户ID筛选
        page: 页码（从1开始）
        page_size: 每页条数

    Returns:
        {
            "success": bool,
            "records": List[Dict],
            "total": int,
            "page": int,
            "page_size": int,
            "total_pages": int
        }
    """
    from app.models.form import FormInstance, FormTemplate

    if db is None:
        from app.core.database import get_db
        db = next(get_db())

    try:
        # 查询 FormTemplate
        template = db.query(FormTemplate).filter(
            FormTemplate.form_code == form_code,
            FormTemplate.is_active == True
        ).first()

        if not template:
            return {
                "success": False,
                "message": f"表单 {form_code} 不存在",
                "records": [],
                "total": 0
            }

        # 构建基础查询
        query = db.query(FormInstance).filter(
            FormInstance.template_id == template.id,
            FormInstance.status == 'submitted'
        )

        # 应用时间范围筛选
        if start_date:
            try:
                start_dt = datetime.strptime(start_date, "%Y-%m-%d")
                query = query.filter(FormInstance.submitted_at >= start_dt)
            except ValueError:
                logger.warning("[query_history] 无效的 start_date: %s", start_date)

        if end_date:
            try:
                end_dt = datetime.strptime(end_date, "%Y-%m-%d").replace(
                    hour=23, minute=59, second=59
                )
                query = query.filter(FormInstance.submitted_at <= end_dt)
            except ValueError:
                logger.warning("[query_history] 无效的 end_date: %s", end_date)

        # 应用用户筛选
        if user_id:
            query = query.filter(FormInstance.user_id == user_id)

        # 获取总数
        total = query.count()

        # 计算分页
        total_pages = (total + page_size - 1) // page_size if total > 0 else 0
        offset = (page - 1) * page_size

        # 分页查询
        instances = query.order_by(
            FormInstance.submitted_at.desc()
        ).offset(offset).limit(page_size).all()

        # 格式化记录
        records = []
        for inst in instances:
            records.append({
                "id": inst.id,
                "form_id": inst.form_id,
                "user_id": inst.user_id,
                "submitted_at": inst.submitted_at.strftime("%Y-%m-%d %H:%M:%S") if inst.submitted_at else None,
                "data": inst.data or {}
            })

        return {
            "success": True,
            "records": records,
            "total": total,
            "page": page,
            "page_size": page_size,
            "total_pages": total_pages
        }

    finally:
        try:
            db.close()
        except Exception:
            pass


# ── 导出功能 ──────────────────────────────────────────────────

def export_history_data(
    form_code: str,
    format: str = "jsonl",
    start_date: Optional[str] = None,
    end_date: Optional[str] = None,
    user_id: Optional[str] = None,
    db=None
) -> Dict[str, Any]:
    """
    导出历史数据。

    Args:
        form_code: 表单编码
        format: 导出格式 (csv/jsonl)
        start_date: 开始日期
        end_date: 结束日期
        user_id: 用户ID筛选

    Returns:
        {
            "success": bool,
            "content": str,      # 导出内容
            "filename": str,
            "content_type": str
        }
    """
    import csv
    from io import StringIO
    from app.models.form import FormInstance, FormTemplate

    if db is None:
        from app.core.database import get_db
        db = next(get_db())

    try:
        # 查询 FormTemplate
        template = db.query(FormTemplate).filter(
            FormTemplate.form_code == form_code,
            FormTemplate.is_active == True
        ).first()

        if not template:
            return {"success": False, "message": f"表单 {form_code} 不存在"}

        # 构建查询
        query = db.query(FormInstance).filter(
            FormInstance.template_id == template.id,
            FormInstance.status == 'submitted'
        )

        if start_date:
            try:
                start_dt = datetime.strptime(start_date, "%Y-%m-%d")
                query = query.filter(FormInstance.submitted_at >= start_dt)
            except ValueError:
                pass

        if end_date:
            try:
                end_dt = datetime.strptime(end_date, "%Y-%m-%d").replace(
                    hour=23, minute=59, second=59
                )
                query = query.filter(FormInstance.submitted_at <= end_dt)
            except ValueError:
                pass

        if user_id:
            query = query.filter(FormInstance.user_id == user_id)

        instances = query.order_by(FormInstance.submitted_at.desc()).all()

        if not instances:
            return {"success": False, "message": "没有找到符合条件的记录"}

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

        if format == "csv":
            return _export_as_csv(instances, form_code, timestamp)
        else:
            return _export_as_jsonl(instances, form_code, timestamp)

    finally:
        try:
            db.close()
        except Exception:
            pass


def _export_as_csv(instances: list, form_code: str, timestamp: str) -> Dict[str, Any]:
    """导出为 CSV 格式"""
    import csv
    from io import StringIO

    # 收集所有字段
    all_fields = set()
    for inst in instances:
        if inst.data:
            all_fields.update(inst.data.keys())
    all_fields = sorted(all_fields)

    meta_fields = ["id", "form_id", "user_id", "submitted_at"]
    display_fields = meta_fields + all_fields

    output = StringIO()
    writer = csv.writer(output)
    writer.writerow(display_fields)

    for inst in instances:
        row = []
        data = inst.data or {}
        for field in display_fields:
            if field == "id":
                row.append(inst.id)
            elif field == "form_id":
                row.append(inst.form_id)
            elif field == "user_id":
                row.append(inst.user_id or "")
            elif field == "submitted_at":
                row.append(inst.submitted_at.strftime("%Y-%m-%d %H:%M:%S") if inst.submitted_at else "")
            else:
                value = data.get(field, "")
                if isinstance(value, list):
                    value = "; ".join(str(v) for v in value)
                row.append(value)
        writer.writerow(row)

    return {
        "success": True,
        "content": output.getvalue(),
        "recordCount": len(instances),
        "filename": f"{form_code}_history_{timestamp}.csv",
        "content_type": "text/csv"
    }


def _export_as_jsonl(instances: list, form_code: str, timestamp: str) -> Dict[str, Any]:
    """导出为 JSONL 格式"""
    from io import StringIO

    output = StringIO()
    for inst in instances:
        record = {
            "id": inst.id,
            "form_id": inst.form_id,
            "user_id": inst.user_id,
            "submitted_at": inst.submitted_at.strftime("%Y-%m-%d %H:%M:%S") if inst.submitted_at else None
        }
        if inst.data:
            record.update(inst.data)
        output.write(json.dumps(record, ensure_ascii=False) + "\n")

    return {
        "success": True,
        "content": output.getvalue(),
        "recordCount": len(instances),
        "filename": f"{form_code}_history_{timestamp}.jsonl",
        "content_type": "application/jsonl"
    }


# ── 内部辅助 ──────────────────────────────────────────────────

def _collect_field_stats(form_code: str, db=None) -> Dict[str, Any]:
    """从数据库收集字段值统计"""
    from app.models.form import FormInstance, FormTemplate, FormHistory
    from collections import defaultdict

    if db is None:
        from app.core.database import get_db
        db = next(get_db())

    try:
        # 查 FormTemplate
        template = db.query(FormTemplate).filter(
            FormTemplate.form_code == form_code
        ).first()

        total_records = 0
        field_stats = {}

        if template:
            # 通过 template_id 查 FormInstance
            instances = db.query(FormInstance).filter(
                FormInstance.template_id == template.id,
                FormInstance.status == 'submitted'
            ).all()
            total_records = len(instances)

            if instances:
                instance_ids = [inst.id for inst in instances]
                histories = db.query(FormHistory).filter(
                    FormHistory.form_instance_id.in_(instance_ids)
                ).all()

                # 统计每个字段的值分布
                value_counts = defaultdict(lambda: defaultdict(int))
                for h in histories:
                    if h.field_value and h.field_value.strip():
                        value_counts[h.field_code][h.field_value] += 1

                # 取每个字段的 Top 10
                for fc, vals in value_counts.items():
                    top = sorted(vals.items(), key=lambda x: x[1], reverse=True)[:10]
                    field_stats[fc] = {
                        "distinctValues": len(vals),
                        "topValues": [{"value": v, "count": c} for v, c in top]
                    }
        else:
            # 无 template → 兜底：直接查 form_history
            # 尝试通过 import_ 前缀的 form_id 匹配
            instances = db.query(FormInstance).filter(
                FormInstance.form_id.like(f"imp_{form_code}%"),
                FormInstance.status == 'submitted'
            ).all()
            total_records = len(instances)

            if instances:
                instance_ids = [inst.id for inst in instances]
                histories = db.query(FormHistory).filter(
                    FormHistory.form_instance_id.in_(instance_ids)
                ).all()

                value_counts = defaultdict(lambda: defaultdict(int))
                for h in histories:
                    if h.field_value and h.field_value.strip():
                        value_counts[h.field_code][h.field_value] += 1

                for fc, vals in value_counts.items():
                    top = sorted(vals.items(), key=lambda x: x[1], reverse=True)[:10]
                    field_stats[fc] = {
                        "distinctValues": len(vals),
                        "topValues": [{"value": v, "count": c} for v, c in top]
                    }

        return {
            "totalRecords": total_records,
            "fieldStats": field_stats
        }

    finally:
        try:
            db.close()
        except Exception:
            pass


def _build_ontology_summary(ontology: Dict) -> str:
    """构建本体简要摘要（用于 LLM 分析）"""
    lines = []
    form_name = ontology.get("formName", "")
    lines.append(f"表单: {form_name}")

    entities = ontology.get("entities", [])
    for entity in entities:
        entity_name = entity.get("entityName", "")
        fields = entity.get("fields", [])
        if entity_name:
            lines.append(f"\n{entity_name}:")
        for f in fields:
            fc = f.get("fieldCode", "")
            fn = f.get("fieldName", "")
            ft = f.get("fieldType", "")
            req = "必填" if f.get("required") else "可选"
            enum_config = f.get("enumConfig", {})
            opts = enum_config.get("options", []) if isinstance(enum_config, dict) else []
            opt_str = f", 枚举: {opts}" if opts else ""
            lines.append(f"  {fn}({fc}): {ft} [{req}{opt_str}]")

    return "\n".join(lines)



def _safe_parse_json(text: str) -> Optional[Any]:
    """安全解析 JSON，支持各种格式问题"""
    if not text:
        return None

    # 去掉代码块标记
    text = text.strip()
    if text.startswith('```'):
        # 找到第一个换行
        first_nl = text.find('\n')
        if first_nl != -1:
            text = text[first_nl + 1:]
        if text.endswith('```'):
            text = text[:-3]
        text = text.strip()

    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # 尝试提取 JSON 对象
    start = text.find('{')
    end = text.rfind('}')
    if start != -1 and end != -1 and end > start:
        try:
            return json.loads(text[start:end + 1])
        except json.JSONDecodeError:
            pass

    # 尝试提取 JSON 数组
    start = text.find('[')
    end = text.rfind(']')
    if start != -1 and end != -1 and end > start:
        try:
            return json.loads(text[start:end + 1])
        except json.JSONDecodeError:
            pass

    return None


def _call_llm(user_prompt: str, system_prompt: str, max_tokens: int = 4096) -> Optional[str]:
    """调用 LLM，返回结果文本"""
    if not llm_service.enabled:
        logger.warning("[history_ai] LLM 不可用")
        return None

    try:
        result = llm_service._call_llm_sync(
            user_prompt,
            system_prompt=system_prompt,
            max_tokens=max_tokens
        )
        if result:
            # 尝试从 reasoning 中提取（兼容 MiniMax M2.7）
            return result

        # 如果 content 为空，尝试 reasoning fallback
        _, reasoning = llm_service._call_llm_sync_with_reasoning(
            user_prompt,
            system_prompt=system_prompt,
            max_tokens=max_tokens
        )
        if reasoning:
            return reasoning

        return None

    except Exception as e:
        logger.exception("[history_ai] LLM 调用异常: %s", e)
        return None
