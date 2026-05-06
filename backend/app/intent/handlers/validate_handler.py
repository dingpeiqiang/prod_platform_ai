# ValidateHandler - 表单校验意图处理器
# 触发方式：用户说"帮我校验一下这张请假单"、"检查一下这个销售订单"
# 流式输出 AI reasoning，结束后返回结构化校验结果

import logging
import time
from typing import AsyncGenerator

from ..base import BaseIntentHandler, IntentContext
from ..utils import thinking, sse, intent_event, done_event
from ...skills.validation_skill import ValidationSkill
from ...services.llm_service import llm_service

logger = logging.getLogger("intent.validate_handler")


class ValidateHandler(BaseIntentHandler):
    """表单校验意图 —— 对已有表单数据进行 AI 业务规则校验"""

    intent_type = "validate"

    async def handle(self, ctx: IntentContext) -> AsyncGenerator[str, None]:
        # 从 intent_data 中提取校验目标
        form_code = ctx.intent_data.get("formCode", "") or ctx.intent_data.get("detectedFormCode", "")
        form_data = ctx.intent_data.get("formData", {})  # 格式: {fieldCode: value}
        form_schema = ctx.intent_data.get("formSchema", None)  # 可选，完整 schema

        # 如果没有 formData，尝试从本地表单管理器获取（如果是表单更新场景）
        if not form_data and hasattr(ctx, 'request') and ctx.request:
            # 从请求中获取表单数据（如果前端传了的话）
            pass

        # 获取本体信息（用于显示）
        if not form_schema:
            from app.core.config_loader import config_loader
            form_schema = config_loader.get_ontology(form_code)

        form_name = ""
        if form_schema:
            form_name = form_schema.get("formName", form_code)
        elif form_code in ctx.ontologies:
            form_name = ctx.ontologies[form_code].get("formName", form_code)

        target_label = form_name or form_code

        yield thinking(f"🔍 开始校验表单「{target_label}」...")
        ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
        yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})

        # 流式处理 ValidationSkill 的输出
        passed = True
        errors = []
        warnings = []
        reasonings = []

        try:
            async for event in ValidationSkill.validate(
                form_code=form_code,
                form_data=form_data or {},
                form_schema=form_schema,
                yield_reasoning=True
            ):
                evt_type = event.get("type")

                if evt_type == "reasoning":
                    content = event.get("content", "")
                    if content:
                        reasonings.append(content)
                        # 按片段 yield thinking，每 20 个字符或遇到换行切分
                        yield thinking(content)

                elif evt_type == "done":
                    passed = event.get("passed", True)
                    errors = event.get("errors", [])
                    warnings = event.get("warnings", [])

                elif evt_type == "error":
                    yield thinking(f"⚠️ {event.get('message', '校验出错')}")

            # 根据结果输出最终总结
            ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})

            if not errors and not warnings:
                yield thinking(f"✅ 「{target_label}」校验通过，所有字段符合业务规则")
            elif errors:
                yield thinking(f"❌ 「{target_label}」发现 {len(errors)} 个问题，{len(warnings)} 个提示")
            elif warnings:
                yield thinking(f"⚠️ 「{target_label}」存在 {len(warnings)} 个提示信息")

            # 构造 intent_data 供前端面板使用
            result_data = {
                "formCode": form_code,
                "formName": target_label,
                "passed": passed,
                "errors": errors,
                "warnings": warnings,
                "reasonings": reasonings,
                "count": {
                    "error": len(errors),
                    "warning": len(warnings)
                }
            }

            yield intent_event("validate", "result", result_data, is_form=False)
            yield done_event("validate", is_form=False, intent_data=ctx.intent_data)

        except Exception as e:
            logger.exception("[ValidateHandler] 异常: %s", e)
            yield thinking(f"❌ 校验过程出错：{str(e)}")
            ctx.stream_stats.total_elapsed = time.time() - ctx.start_time
            ctx.stream_stats.error = str(e)
            yield sse({"type": "stats", "content": ctx.stream_stats.to_dict()})
            yield sse({"type": "error", "content": f"校验异常: {str(e)}"})