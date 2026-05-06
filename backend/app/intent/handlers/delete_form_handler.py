# DeleteFormHandler - 删除表单意图处理器
# 对应 chat.py 第918-972行

import asyncio
import time
from typing import AsyncGenerator

from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse, intent_event, done_event
from ...services.admin_service import AdminService


class DeleteFormHandler(BaseIntentHandler):
    """删除表单意图 —— 自动备份 + 删除"""

    intent_type = "delete_form"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        target_code = ctx.intent_data.get("formCode", "") or ctx.intent_data.get("detectedFormCode", "")
        target_name = ctx.intent_data.get("formName", "")
        if not target_name and target_code and target_code in ctx.ontologies:
            target_name = ctx.ontologies[target_code].get("formName", target_code)

        # ── Phase 1：确认删除目标 ────────────────────────────────────
        yield thinking(f"🗑️ 确认删除表单「{target_name or target_code}」（自动备份）", result={
            "formCode": target_code,
            "formName": target_name,
            "autoBackup": True
        })

        # 执行删除
        loop = asyncio.get_event_loop()
        delete_result = await loop.run_in_executor(
            None,
            lambda: AdminService.delete_ontology(target_code, auto_backup=True)
        )

        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time

        if delete_result.get("success"):
            backup = delete_result.get("backup", {})
            backup_id = backup.get("id", "") if backup else ""

            # ── Phase 2：删除结果 ────────────────────────────────────
            yield thinking(f"✅ 已删除表单「{target_name or target_code}」", result={
                "success": True,
                "backupVersionId": backup_id,
                "message": delete_result.get("message", "")
            })

            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})

            yield intent_event("delete_form", "delete", {
                "formCode": target_code,
                "formName": target_name,
                "backupVersionId": backup_id,
                "message": delete_result.get("message", "")
            }, is_form=False)

            confirm_text = f"已删除表单「{target_name or target_code}」。如需恢复，可以在版本历史中回退。"
            yield sse({"type": "text_start"})
            for i in range(0, len(confirm_text), 3):
                yield sse({"type": "text", "content": confirm_text[i:i+3]})
                await asyncio.sleep(0.02)
            yield sse({"type": "text_end"})

            yield done_event("delete_form", is_form=False, intent_data=ctx.intent_data)
        else:
            error_msg = delete_result.get("message", "删除失败")

            # ── Phase 2：错误结果（合并到同一结构） ───────────────────
            yield thinking(f"❌ 删除失败: {error_msg}", result={
                "success": False,
                "error": error_msg
            })

            ctx.stream_stats.error = error_msg
            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
            yield sse({"type": "error", "content": error_msg})
            yield done_event("delete_form", is_form=False, intent_data=ctx.intent_data)
