# SSE 工具函数与共享类型
# 从 chat.py 提取的通用 SSE 帧格式化、思考步骤、推理等工具函数

import json
import logging
import datetime
from dataclasses import dataclass
from typing import Any, Dict, Optional, AsyncGenerator

from app.services.llm_service import llm_service, StreamStats
from app.core.config_loader import config_loader

logger = logging.getLogger("intent_utils")


# ── SSE 帧格式化 ──────────────────────────────────────

def sse(data: dict) -> str:
    """格式化 SSE 帧"""
    return f"data: {json.dumps(data, ensure_ascii=False)}\n\n"


def thinking(content: str, result: Any = None) -> str:
    """
    系统步骤日志（type=thinking）。

    Args:
        content: 步骤描述
        result: 步骤结果，可选。结构化数据会在前端显示为可展开的详情
    """
    data = {"type": "thinking", "content": content}
    if result is not None:
        data["result"] = result
    return sse(data)


def ask_user(content: str) -> str:
    """直接回复用户的消息（type=text），会显示在聊天区而非推理折叠面板"""
    return sse({"type": "text", "content": content})


def reasoning(content: str) -> str:
    """大模型推理过程（type=reasoning），与系统步骤区分"""
    return sse({"type": "reasoning", "content": content})


# ── 聊天流式回复 ──────────────────────────────────────

async def stream_chat_reply(
    chat_prompt: str,
    ontologies_info: str,
    messages_text: str
) -> AsyncGenerator[tuple, None]:
    """
    用 LLM 流式输出聊天回复。
    若 LLM 不可用则降级为 fallback 文本。

    Yields:
        (SSE帧, 统计信息) - 统计信息仅在结束时提供
    """
    import asyncio

    chat_prompt_template = config_loader.get_prompt('smart_chat_response')
    if not chat_prompt_template:
        yield sse({"type": "text_start"}), None
        yield sse({"type": "text", "content": "好的，请问有什么可以帮助你？"}), None
        yield sse({"type": "text_end"}), None
        return

    current_date = datetime.datetime.now().strftime('%Y-%m-%d')
    current_weekday = datetime.datetime.now().strftime('%A')
    prompt = chat_prompt_template.format(
        current_date=current_date,
        current_weekday=current_weekday,
        ontologies_info=ontologies_info,
        messages_text=messages_text
    )

    yield thinking("🤖 正在生成回复..."), None
    logger.info("[stream_chat] 开始流式调用，prompt长度=%d", len(prompt))

    # text_start 延迟到首个正文出现时再发
    final_stats: Optional[StreamStats] = None
    _thinking_buf: str = ""
    _in_thinking: bool = False
    _text_started: bool = False

    async for text, stats in llm_service.call_llm_stream_with_stats(prompt):
        if stats is not None:
            final_stats = stats
            continue

        if not text:
            continue

        remaining = text
        while remaining:
            if _in_thinking:
                end_idx = remaining.find("[/THINKING]")
                if end_idx == -1:
                    _thinking_buf += remaining
                    remaining = ""
                else:
                    _thinking_buf += remaining[:end_idx]
                    logger.debug("[stream_chat] 丢弃 thinking 块 (%d 字符)", len(_thinking_buf))
                    _thinking_buf = ""
                    _in_thinking = False
                    remaining = remaining[end_idx + len("[/THINKING]"):]
            else:
                start_idx = remaining.find("[THINKING]")
                if start_idx == -1:
                    if remaining.strip():
                        if not _text_started:
                            _text_started = True
                            yield sse({"type": "text_start"}), None
                        yield sse({"type": "text", "content": remaining}), None
                        await asyncio.sleep(0)
                    remaining = ""
                else:
                    before = remaining[:start_idx]
                    if before.strip():
                        if not _text_started:
                            _text_started = True
                            yield sse({"type": "text_start"}), None
                        yield sse({"type": "text", "content": before}), None
                        await asyncio.sleep(0)
                    _in_thinking = True
                    remaining = remaining[start_idx + len("[THINKING]"):]

    if _in_thinking and _thinking_buf.strip():
        logger.debug("[stream_chat] 丢弃未闭合的 thinking 块 (%d 字符)", len(_thinking_buf))

    if not _text_started:
        yield sse({"type": "text_start"}), None

    if final_stats:
        logger.info("[stream_chat] 流式完成: tokens=%d chars=%d elapsed=%.2fs tps=%.1f",
                    final_stats.token_count, final_stats.char_count,
                    final_stats.elapsed, final_stats.tokens_per_second)

    yield sse({"type": "text_end"}), final_stats


# ── 统一意图事件 ──────────────────────────────────────

def intent_event(
    intent_type: str,
    action: str = "",
    data: Any = None,
    is_form: bool = False,
    intent_data: Dict = None
) -> str:
    """
    统一的意图事件格式（版本 2.0）。

    旧格式 vs 新格式对比：
      旧: yield sse({"type": "result", "content": json_string})
      旧: yield sse({"type": "config", "content": {...}})
      旧: yield sse({"type": "delete_form", "content": {...}})
      旧: yield sse({"type": "manage_history", "content": {...}})
      新: yield intent_event("form", "generate", {...})

    统一字段：
      type: "intent"        # 固定
      version: "2.0"        # 协议版本
      intentType: str       # 意图类型
      action: str           # 子操作（generate/update/delete/import/export等）
      data: Any             # 意图数据（统一用 data，不在嵌套 content）
      isForm: bool          # 是否表单意图

    适用场景：
      - form/generate: 表单生成
      - form/update:   表单字段更新
      - configure:     新业务配置
      - delete_form:   删除表单
      - manage_history/analyze: 历史数据分析
      - manage_history/import:  历史数据导入
      - manage_history/query:   历史数据查询
      - manage_history/export:  历史数据导出
      - chat:          纯聊天
    """
    event = {
        "type": "intent",
        "version": "2.0",
        "intentType": intent_type,
        "action": action,
        "data": data if data is not None else {},
        "isForm": is_form
    }
    return sse(event)


def done_event(
    intent_type: str = "",
    is_form: bool = False,
    intent_data: Dict = None
) -> str:
    """统一的 done 事件格式"""
    event = {"type": "done", "isForm": is_form}
    if intent_type:
        event["intentType"] = intent_type
    if intent_data is not None:
        event["intentData"] = intent_data
    return sse(event)


# ── 推荐合并工具 ──────────────────────────────────────

def merge_field_recommendations(
    llm_recs: Dict[str, Any],
    engine_recs: Dict[str, Any]
) -> Dict[str, Any]:
    """
    合并两路 fieldRecommendations：
    - llm_recs: LLM 意图识别输出
    - engine_recs: 推荐引擎输出

    合并策略：LLM 推荐优先保留（source=llm_rule），引擎推荐作为补充追加
    """
    merged = {}
    max_per_field = config_loader.get_system_config().get("smartRecommend", {}).get("maxRecommendationsPerField", 5)

    # 先注入 LLM 推荐
    for field_code, rec_data in llm_recs.items():
        if isinstance(rec_data, list):
            merged[field_code] = {
                "items": [
                    {**item, "source": item.get("source", "llm_rule")}
                    for item in rec_data
                    if isinstance(item, dict) and item.get("value")
                ],
                "strategyUsed": ["llm_rule_inference"],
                "_has_llm": True
            }
        elif isinstance(rec_data, dict) and "items" in rec_data:
            items = [
                {**item, "source": item.get("source", "llm_rule")}
                for item in (rec_data.get("items") or [])
                if isinstance(item, dict)
            ]
            merged[field_code] = {
                "items": items,
                "strategyUsed": rec_data.get("strategyUsed", ["llm_rule_inference"]),
                "_has_llm": True
            }

    # 追加引擎推荐（补充 LLM 未覆盖的字段 + 同字段去重追加）
    for field_code, engine_data in engine_recs.items():
        engine_items = []
        if isinstance(engine_data, dict):
            engine_items = engine_data.get("items", [])
        elif isinstance(engine_data, list):
            engine_items = engine_data

        if not engine_items:
            continue

        if field_code in merged:
            # 去重追加
            existing_values = {
                item.get("value")
                for item in merged[field_code].get("items", [])
            }
            for item in engine_items:
                if isinstance(item, dict) and item.get("value") not in existing_values:
                    merged[field_code]["items"].append(item)
                    existing_values.add(item.get("value"))
            # 合并策略标记
            engine_strategy = engine_data.get("strategyUsed", []) if isinstance(engine_data, dict) else []
            if engine_strategy:
                current = merged[field_code].get("strategyUsed", [])
                merged[field_code]["strategyUsed"] = list(set(current + engine_strategy))
        else:
            merged[field_code] = {
                "items": [
                    item for item in engine_items
                    if isinstance(item, dict)
                ],
                "strategyUsed": (
                    engine_data.get("strategyUsed", [])
                    if isinstance(engine_data, dict) else []
                )
            }

    # 按 max_per_field 截断
    for field_code in merged:
        items = merged[field_code].get("items", [])
        if len(items) > max_per_field:
            # 优先保留 llm_rule，再按 confidence 降序
            llm_items = [i for i in items if i.get("source") == "llm_rule"]
            other_items = [i for i in items if i.get("source") != "llm_rule"]
            other_items.sort(key=lambda x: x.get("confidence", 0), reverse=True)
            merged[field_code]["items"] = (llm_items + other_items)[:max_per_field]

    return merged
