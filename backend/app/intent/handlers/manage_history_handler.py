# ManageHistoryHandler - 历史数据维护意图处理器
# 对应 chat.py 第975-1094行

import asyncio
import time
import logging
from typing import AsyncGenerator

from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse, intent_event, done_event
from ...services.history_ai_service import (
    analyze_history,
    apply_generated_data,
    list_available_data,
    get_history_summary,
    query_history_records,
    export_history_data
)

logger = logging.getLogger("intent.manage_history_handler")


class ManageHistoryHandler(BaseIntentHandler):
    """历史数据维护意图 —— analyze/generate/import/status 四种子操作"""

    intent_type = "manage_history"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        target_code = ctx.intent_data.get("formCode", "") or ctx.intent_data.get("detectedFormCode", "")
        action = ctx.intent_data.get("action", "analyze")  # analyze / generate / import / status
        target_name = ctx.intent_data.get("formName", "")
        description = ctx.intent_data.get("description", "")
        count = ctx.intent_data.get("count", 10)

        if not target_name and target_code and target_code in ctx.ontologies:
            target_name = ctx.ontologies[target_code].get("formName", target_code)

        yield thinking(f"📊 识别到历史数据维护请求: {target_name or target_code}")
        yield thinking(f"   操作: {action}")

        loop = asyncio.get_event_loop()

        if action == "analyze":
            yield thinking("🔍 正在分析历史数据质量...")
            result = await loop.run_in_executor(
                None, lambda: analyze_history(target_code, db=ctx.db)
            )

            if result.get("success"):
                yield thinking(f"✅ 分析完成，数据质量评分: {result.get('qualityScore', '-')}")
                if result.get("recommendations"):
                    for rec in result["recommendations"][:3]:
                        yield thinking(f"   💡 {rec}")

                ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
                yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
                yield intent_event("manage_history", "analyze", result, is_form=False)
                yield done_event("manage_history", is_form=False, intent_data=ctx.intent_data)
            else:
                error_msg = result.get("message", "分析失败")
                yield thinking(f"❌ {error_msg}")
                ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
                ctx.stream_stats.error = error_msg
                yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
                yield sse({"type": "error", "content": error_msg})

        elif action == "import":
            # 新的导入流程：提供上传入口
            yield thinking("📥 准备数据导入入口...")

            # 返回导入入口信息，前端会显示上传界面
            import_entry = {
                "type": "import_entry",
                "formCode": target_code,
                "formName": target_name,
                "message": f"请上传「{target_name or target_code}」的历史数据文件（JSONL格式）",
                "template_url": f"/api/v1/config/import/template/{target_code}",
                "upload_url": f"/api/v1/config/import/upload"
            }

            ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
            yield intent_event("manage_history", "import", import_entry, is_form=False)
            yield done_event("manage_history", is_form=False, intent_data=ctx.intent_data)

        elif action == "query":
            # 查询历史数据记录
            start_date = ctx.intent_data.get("start_date")
            end_date = ctx.intent_data.get("end_date")
            user_id = ctx.intent_data.get("user_id")
            page = ctx.intent_data.get("page", 1)
            page_size = ctx.intent_data.get("page_size", 20)

            yield thinking(f"🔍 查询历史数据...")
            if start_date or end_date:
                yield thinking(f"   时间范围: {start_date or '开始'} ~ {end_date or '至今'}")

            result = await loop.run_in_executor(
                None,
                lambda: query_history_records(
                    target_code,
                    start_date=start_date,
                    end_date=end_date,
                    user_id=user_id,
                    page=page,
                    page_size=page_size,
                    db=ctx.db
                )
            )

            if result.get("success"):
                yield thinking(f"✅ 查询完成，共 {result.get('total', 0)} 条记录")
                yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
                yield intent_event("manage_history", "query", result, is_form=False)
                yield done_event("manage_history", is_form=False, intent_data=ctx.intent_data)
            else:
                error_msg = result.get("message", "查询失败")
                yield thinking(f"❌ {error_msg}")
                yield sse({"type": "error", "content": error_msg})

        elif action == "export":
            export_format = ctx.intent_data.get("format", "jsonl")
            start_date = ctx.intent_data.get("start_date")
            end_date = ctx.intent_data.get("end_date")
            user_id = ctx.intent_data.get("user_id")

            yield thinking(f"📤 导出历史数据为 {export_format.upper()} 格式...")

            result = await loop.run_in_executor(
                None,
                lambda: export_history_data(
                    target_code,
                    format=export_format,
                    start_date=start_date,
                    end_date=end_date,
                    user_id=user_id,
                    db=ctx.db
                )
            )

            if result.get("success"):
                yield thinking(f"✅ 导出完成，共 {result.get('recordCount', 0)} 条记录")

                # 只返回下载链接，不带文件内容（大文件不适合走 SSE）
                filename = result.get("filename", "")
                export_event_data = {
                    "action": "export",
                    "formCode": target_code,
                    "formName": target_name,
                    "filename": filename,
                    "recordCount": result.get("recordCount", 0),
                    "downloadUrl": f"/api/v1/config/export/{target_code}?format={export_format}",
                    "message": f"文件已准备好，点击下载：{filename}"
                }
                ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
                yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
                yield intent_event("manage_history", "export", export_event_data, is_form=False)
                yield done_event("manage_history", is_form=False, intent_data=ctx.intent_data)
            else:
                error_msg = result.get("message", "导出失败")
                yield thinking(f"❌ {error_msg}")
                yield sse({"type": "error", "content": error_msg})

        else:
            # 默认：status 查询
            yield thinking("📋 正在查询历史数据状态...")
            result = await loop.run_in_executor(
                None, lambda: get_history_summary(target_code, db=ctx.db)
            )

            ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
            yield intent_event("manage_history", "status", result, is_form=False)
            yield done_event("manage_history", is_form=False, intent_data=ctx.intent_data)
