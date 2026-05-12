from fastapi import APIRouter
from pydantic import BaseModel
from typing import List, Dict, Any, Optional
import json
import logging
from app.services.llm_service import llm_service
from app.core.config_loader import config_loader
from app.skills import ToolRegistry
from app.skills.scene_recognition import SceneRecognitionSkill
from app.skills.field_extraction import FieldExtractionSkill
from app.services.scene_service import SceneService
from app.core.database import get_db

logger = logging.getLogger("chat_with_tools_api")

router = APIRouter(prefix="/api/v1", tags=["chat_with_tools"])


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatRequest(BaseModel):
    messages: List[ChatMessage]
    userId: Optional[str] = None


class ChatResponse(BaseModel):
    success: bool
    reply: Optional[str] = None
    intentType: Optional[str] = None
    formCode: Optional[str] = None
    extractedFields: Optional[Dict[str, Any]] = None
    toolCalls: Optional[List[Dict[str, Any]]] = None
    message: Optional[str] = None


FALLBACK_RESPONSES = {
    '你好': '你好！我是AI智能助手。我可以帮你填写各种表单（销售订单、请假申请、费用报销等），也可以和你聊天。有什么我可以帮你的吗？',
    '你能做什么': '我可以帮你：\n1. 生成和填写表单（销售订单、请假申请、费用报销等）\n2. 回答你的问题\n3. 和你聊天\n\n你可以直接告诉我需要什么帮助，比如："帮我填一个请假申请"',
    '帮助': '使用指南：\n1. 告诉我需要什么表单，如"帮我填一个销售订单"\n2. 我会自动调用工具识别和提取\n3. 填写后点击提交\n\n快捷操作可以点击下方按钮！',
    '默认': '我是AI智能助手！我可以帮你填写各种表单。你可以告诉我需要填写什么，比如：\n- "帮我填一个销售订单"\n- "帮我填一个请假申请"\n- "帮我填一个费用报销"'
}


def _call_tools_manually(user_message: str, ontologies: Dict) -> Dict:
    tool_calls = []
    
    scene_result = SceneRecognitionSkill.recognize(user_message)
    scene_code = scene_result["sceneCode"]
    logger.info("[chat_with_tools] 场景识别结果 scene_code=%s", scene_code)
    tool_calls.append({
        "tool": "recognize_scene",
        "input": user_message,
        "output": scene_code
    })
    
    prompt_content = None
    form_code = None
    
    if scene_code:
        db_gen = get_db()
        db = next(db_gen)
        try:
            prompt_result = SceneService.get_scene_prompt(scene_code, db)
            if prompt_result["success"]:
                prompt_content = prompt_result.get("prompt_content")
                form_code = prompt_result.get("scene", {}).get("formCode")
                
                logger.info("[chat_with_tools] 获取提示词成功 prompt_code=%s", prompt_result.get("prompt_code"))
                tool_calls.append({
                    "tool": "get_scene_prompt",
                    "input": {"scene_code": scene_code},
                    "output": {"prompt_code": prompt_result.get("prompt_code"), "has_prompt": prompt_content is not None}
                })
            else:
                logger.warning("[chat_with_tools] 获取提示词失败: %s", prompt_result.get("message"))
        finally:
            db.close()
    
    llm_response = None
    if prompt_content:
        try:
            llm_response = llm_service.call_llm_sync(user_message, system_prompt=prompt_content)
            logger.info("[chat_with_tools] 大模型调用成功 response_len=%d", len(llm_response) if llm_response else 0)
            tool_calls.append({
                "tool": "call_llm",
                "input": {"user_message": user_message, "prompt_code": prompt_result.get("prompt_code")},
                "output": llm_response[:200] + "..." if llm_response and len(llm_response) > 200 else llm_response
            })
        except Exception as e:
            logger.exception("[chat_with_tools] 大模型调用失败: %s", e)
    
    extracted_fields = {}
    if form_code and form_code in ontologies:
        temp_schema = {
            "formCode": form_code,
            "formName": ontologies[form_code].get("formName", ""),
            "fields": []
        }
        extraction_result = FieldExtractionSkill.extract(user_message, form_code, temp_schema)
        
        if extraction_result["success"]:
            for field in extraction_result["fields"]:
                extracted_fields[field.get("fieldCode")] = field.get("defaultValue")
        logger.debug("[chat_with_tools] 字段提取结果 fields=%s", list(extracted_fields.keys()))
        
        tool_calls.append({
            "tool": "extract_fields",
            "input": {"user_input": user_message, "form_code": form_code},
            "output": extracted_fields
        })
    
    return {
        "scene_code": scene_code,
        "form_code": form_code,
        "prompt_content": prompt_content,
        "llm_response": llm_response,
        "extracted_fields": extracted_fields,
        "tool_calls": tool_calls
    }


@router.post("/chat_with_tools", response_model=ChatResponse)
async def chat_with_tools(request: ChatRequest):
    try:
        ontologies = config_loader.get_all_ontologies()
        tools_info = ToolRegistry.get_tool_definitions_json()
        
        last_user_message = ""
        for msg in reversed(request.messages):
            if msg.role == "user":
                last_user_message = msg.content
                break
        
        logger.info("[chat_with_tools] 收到请求 user=%s msg=%s",
                    request.userId, last_user_message[:100])
        
        form_keywords = ['订单', '请假', '报销', '合同', '项目', '表单', '填写', '生成', '校验', 'API', '演示']
        needs_form = any(keyword in last_user_message for keyword in form_keywords)
        
        if needs_form:
            tool_result = _call_tools_manually(last_user_message, ontologies)
            scene_code = tool_result["scene_code"]
            form_code = tool_result["form_code"]
            llm_response = tool_result.get("llm_response")
            
            if scene_code:
                logger.info("[chat_with_tools] 识别到场景 scene_code=%s form_code=%s has_prompt=%s",
                            scene_code, form_code, tool_result.get("prompt_content") is not None)
                
                response_data = {
                    "success": True,
                    "intentType": "form",
                    "formCode": form_code,
                    "extractedFields": tool_result["extracted_fields"],
                    "toolCalls": tool_result["tool_calls"]
                }
                
                if llm_response:
                    response_data["reply"] = llm_response
                
                return ChatResponse(**response_data)
        
        reply = FALLBACK_RESPONSES['默认']
        for key, value in FALLBACK_RESPONSES.items():
            if key != '默认' and key in last_user_message:
                reply = value
                break
        
        logger.info("[chat_with_tools] 返回聊天回复 intent=chat")
        return ChatResponse(
            success=True,
            intentType="chat",
            reply=reply,
            toolCalls=[]
        )
        
    except Exception as e:
        logger.exception("[chat_with_tools] 处理异常: %s", e)
        
        reply = FALLBACK_RESPONSES['默认']
        return ChatResponse(
            success=True,
            intentType="chat",
            reply=reply,
            toolCalls=[]
        )
