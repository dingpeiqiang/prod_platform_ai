"""
演示场景三 API - 智聊・对话式配置(零基础极简配置)
前缀: /api/v1/demo/dialog
"""
import json
import uuid
import asyncio
import copy
from typing import Dict, Any, List, Optional
from pathlib import Path
from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from app.core.logger import get_logger
from app.services.demo_data_service import demo_data_service
from app.services.llm_service import llm_service
from app.services.llm.base import extract_json

logger = get_logger(__name__)
router = APIRouter(prefix="/api/v1/demo/dialog", tags=["演示-智聊对话配置"])

PROMPT_PATH = Path(__file__).parent.parent / "prompts" / "demo_dialog_config.txt"

_dialog_sessions: Dict[str, Dict] = {}


class StartRequest(BaseModel):
    user_id: Optional[str] = Field(default="demo_user")


class ChatRequest(BaseModel):
    session_id: str = Field(..., description="会话ID")
    message: str = Field(..., description="用户消息")


class UpdateNodeRequest(BaseModel):
    session_id: str = Field(..., description="会话ID")
    node: Dict[str, Any] = Field(..., description="节点数据")


class ValidateRequest(BaseModel):
    session_id: str = Field(..., description="会话ID")


@router.post("/start", summary="开始对话配置会话")
async def start_session(request: StartRequest) -> Dict[str, Any]:
    """创建新的对话配置会话,初始化空画布"""
    session_id = f"demo_dialog_{uuid.uuid4().hex[:12]}"
    canvas = demo_data_service.get_initial_canvas()

    _dialog_sessions[session_id] = {
        "session_id": session_id,
        "user_id": request.user_id,
        "canvas": canvas,
        "messages": [],
        "created_at": asyncio.get_event_loop().time(),
        "validation_status": "pending"
    }

    logger.info(f"[DemoDialog] 创建会话: {session_id}")

    return {
        "success": True,
        "session_id": session_id,
        "canvas": canvas,
        "welcome": "您好!我是产品智能配置助手。请描述您的配置需求,例如:\n\n• 办理100M家庭宽带,首月半价\n• 200M宽带+2张副卡,合约24个月\n• 500M全屋光WiFi,仅限本市小区用户\n\n您可以直接用自然语言描述,我会帮您生成配置方案。"
    }


@router.post("/chat", summary="多轮对话(SSE流式)")
async def chat(request: ChatRequest):
    """多轮对话配置,SSE流式返回思考过程、回复文本和画布更新"""
    logger.info(f"[DemoDialog] 对话: session={request.session_id}, msg={request.message[:50]}")

    session = _dialog_sessions.get(request.session_id)
    if not session:
        raise HTTPException(status_code=404, detail="会话不存在")

    session["messages"].append({"role": "user", "content": request.message})

    async def event_generator():
        try:
            yield f"data: {json.dumps({'type': 'thinking', 'content': '🔍 正在分析您的需求...'}, ensure_ascii=False)}\n\n"
            await asyncio.sleep(0.3)

            ai_result = await _process_dialog(request.session_id, request.message)

            if ai_result.get("reasoning"):
                yield f"data: {json.dumps({'type': 'reasoning', 'content': ai_result['reasoning']}, ensure_ascii=False)}\n\n"
                await asyncio.sleep(0.2)

            yield f"data: {json.dumps({'type': 'thinking', 'content': '🛠️ 正在更新配置画布...'}, ensure_ascii=False)}\n\n"
            await asyncio.sleep(0.3)

            reply = ai_result.get("reply", "我已理解您的需求。")
            yield f"data: {json.dumps({'type': 'text_start'}, ensure_ascii=False)}\n\n"

            for i in range(0, len(reply), 3):
                chunk = reply[i:i+3]
                yield f"data: {json.dumps({'type': 'text', 'content': chunk}, ensure_ascii=False)}\n\n"
                await asyncio.sleep(0.02)

            yield f"data: {json.dumps({'type': 'text_end'}, ensure_ascii=False)}\n\n"

            if ai_result.get("canvas_update"):
                yield f"data: {json.dumps({'type': 'canvas_update', 'canvas': session['canvas']}, ensure_ascii=False)}\n\n"

            missing_fields = ai_result.get("missing_fields", [])
            if missing_fields:
                yield f"data: {json.dumps({'type': 'missing_fields', 'fields': missing_fields}, ensure_ascii=False)}\n\n"

            yield f"data: {json.dumps({'type': 'done', 'intent': ai_result.get('intent', 'requirement')}, ensure_ascii=False)}\n\n"

        except Exception as e:
            logger.error(f"[DemoDialog] 对话异常: {e}", exc_info=True)
            yield f"data: {json.dumps({'type': 'error', 'content': str(e)}, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "Connection": "keep-alive"}
    )


async def _process_dialog(session_id: str, user_message: str) -> Dict[str, Any]:
    """处理对话,调用LLM分析意图并更新画布"""
    session = _dialog_sessions[session_id]
    canvas = session["canvas"]

    current_config = _canvas_to_config_summary(canvas)

    ai_result = None

    if llm_service.enabled:
        try:
            prompt_template = PROMPT_PATH.read_text(encoding="utf-8")
            prompt = prompt_template.replace("{current_config}", json.dumps(current_config, ensure_ascii=False, indent=2))
            prompt = prompt.replace("{user_input}", user_message)

            response = llm_service._call_llm_sync(prompt)

            if response:
                ai_result = extract_json(response)
                if ai_result and isinstance(ai_result, dict):
                    logger.info(f"[DemoDialog] AI分析成功: intent={ai_result.get('intent')}")
        except Exception as e:
            logger.warning(f"[DemoDialog] AI分析失败,使用规则: {e}")

    if not ai_result:
        ai_result = _rule_based_dialog(user_message, current_config)
        logger.info(f"[DemoDialog] 规则分析: intent={ai_result.get('intent')}")

    canvas_update = ai_result.get("canvas_update", {})
    if canvas_update:
        _apply_canvas_update(canvas, canvas_update)
        session["canvas"] = canvas

    session["messages"].append({"role": "assistant", "content": ai_result.get("reply", "")})

    return ai_result


def _canvas_to_config_summary(canvas: Dict) -> Dict:
    """将画布转换为配置摘要(供LLM理解当前状态)"""
    summary = {}
    for node in canvas.get("nodes", []):
        node_type = node.get("node_type")
        fields = node.get("fields", {})
        summary[node_type] = {k: v for k, v in fields.items() if v}
    return summary


def _apply_canvas_update(canvas: Dict, update: Dict):
    """将画布更新指令应用到画布"""
    nodes_to_update = update.get("nodes_to_update", [])
    for update_node in nodes_to_update:
        node_type = update_node.get("node_type")
        new_fields = update_node.get("fields", {})

        for node in canvas.get("nodes", []):
            if node.get("node_type") == node_type:
                node["fields"].update(new_fields)
                break


def _rule_based_dialog(user_message: str, current_config: Dict) -> Dict:
    """基于规则的对话处理(LLM不可用时的降级方案)"""
    msg_lower = user_message.lower()

    extracted = {}
    missing = []

    bandwidth_map = {"50m": "50M", "100m": "100M", "200m": "200M", "300m": "300M", "500m": "500M", "1g": "1G"}
    for kw, val in bandwidth_map.items():
        if kw in msg_lower:
            extracted["bandwidth"] = val
            break

    if "半价" in user_message:
        extracted["first_month_discount"] = "半价"
    elif "免费" in user_message:
        extracted["first_month_discount"] = "免费"

    if "副卡" in user_message:
        import re
        count_match = re.search(r'(\d+)\s*张?\s*副卡', user_message)
        if count_match:
            extracted["sub_card_count"] = int(count_match.group(1))

    if "本市" in user_message or "小区" in user_message:
        extracted["region_limit"] = "本市小区用户"

    if "家庭" in user_message:
        extracted["product_type"] = "宽带+手机"

    contract_match = re.search(r'(\d+)\s*月|(\d+)\s*个月', user_message)
    if contract_match:
        num = int(contract_match.group(1) or contract_match.group(2))
        extracted["contract_period"] = f"{num}个月"

    fee_match = re.search(r'(\d+)\s*元', user_message)
    if fee_match:
        extracted["sub_card_monthly_fee"] = int(fee_match.group(1))

    if not current_config.get("product", {}).get("bandwidth") and "bandwidth" not in extracted:
        missing.append({"field": "bandwidth", "question": "您需要办理多大带宽的宽带?"})

    if "first_month_discount" in extracted and "contract_period" not in extracted:
        if not current_config.get("tariff", {}).get("contract_period"):
            missing.append({"field": "contract_period", "question": "合约需要绑定多久?"})

    if extracted.get("sub_card_count", 0) > 0 and "sub_card_monthly_fee" not in extracted:
        if not current_config.get("sub_card", {}).get("sub_card_monthly_fee"):
            missing.append({"field": "sub_card_monthly_fee", "question": "副卡是否收取月功能费?"})

    is_initial = not any(current_config.values())

    if is_initial:
        reply = "我已根据您的需求生成初始配置。"
    else:
        reply = "我已更新配置。"

    if missing:
        reply += "请补充以下信息:\n"
        for m in missing:
            reply += f"• {m['question']}\n"

    nodes_to_update = []
    if extracted:
        product_fields = {}
        tariff_fields = {}
        sub_card_fields = {}
        constraint_fields = {}

        for k, v in extracted.items():
            if k in ["bandwidth", "product_type", "product_name"]:
                product_fields[k] = v
            elif k in ["first_month_discount", "contract_period", "monthly_fee"]:
                tariff_fields[k] = v
            elif k in ["sub_card_count", "sub_card_monthly_fee"]:
                sub_card_fields[k] = v
            elif k in ["region_limit", "user_limit", "install_limit"]:
                constraint_fields[k] = v

        if product_fields:
            nodes_to_update.append({"node_type": "product", "fields": product_fields})
        if tariff_fields:
            nodes_to_update.append({"node_type": "tariff", "fields": tariff_fields})
        if sub_card_fields:
            nodes_to_update.append({"node_type": "sub_card", "fields": sub_card_fields})
        if constraint_fields:
            nodes_to_update.append({"node_type": "constraint", "fields": constraint_fields})

    return {
        "intent": "requirement" if is_initial else "supplement",
        "extracted_fields": extracted,
        "missing_fields": missing,
        "reply": reply,
        "canvas_update": {
            "nodes_to_update": nodes_to_update,
            "is_initial": is_initial
        }
    }


@router.get("/canvas/{session_id}", summary="获取画布配置数据")
async def get_canvas(session_id: str) -> Dict[str, Any]:
    """获取指定会话的画布配置数据"""
    session = _dialog_sessions.get(session_id)
    if not session:
        raise HTTPException(status_code=404, detail="会话不存在")

    return {
        "success": True,
        "session_id": session_id,
        "canvas": session["canvas"],
        "validation_status": session.get("validation_status", "pending")
    }


@router.post("/update-node", summary="更新画布节点")
async def update_node(request: UpdateNodeRequest) -> Dict[str, Any]:
    """前端拖拽或编辑节点后,更新画布数据"""
    session = _dialog_sessions.get(request.session_id)
    if not session:
        raise HTTPException(status_code=404, detail="会话不存在")

    node_data = request.node
    node_id = node_data.get("node_id")

    canvas = session["canvas"]
    for node in canvas.get("nodes", []):
        if node.get("node_id") == node_id:
            if "fields" in node_data:
                node["fields"].update(node_data["fields"])
            if "position" in node_data:
                node["position"] = node_data["position"]
            if "label" in node_data:
                node["label"] = node_data["label"]
            break

    session["validation_status"] = "pending"

    return {
        "success": True,
        "node": node,
        "message": "节点已更新"
    }


@router.post("/validate", summary="实时本体规则校验")
async def validate_config(request: ValidateRequest) -> Dict[str, Any]:
    """对当前画布配置执行本体规则校验"""
    session = _dialog_sessions.get(request.session_id)
    if not session:
        raise HTTPException(status_code=404, detail="会话不存在")

    canvas = session["canvas"]
    validation = demo_data_service.validate_config(canvas, ontology_code="home_broadband")

    session["validation_status"] = "passed" if validation["valid"] else "failed"

    return {
        "success": True,
        "valid": validation["valid"],
        "errors": validation["errors"],
        "warnings": validation["warnings"],
        "validation_status": session["validation_status"],
        "message": "校验通过" if validation["valid"] else f"发现 {len(validation['errors'])} 个错误, {len(validation['warnings'])} 个警告"
    }
