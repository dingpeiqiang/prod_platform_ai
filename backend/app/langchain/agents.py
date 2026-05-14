from typing import Optional, Dict, Any, List
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import JsonOutputParser
from langchain_core.runnables import RunnableSequence
import logging
from .llm_wrapper import get_langchain_llm
from .chains import FormRecognitionChain, FieldExtractionChain, FormValidationChain, IntentRecognitionChain
from app.core.config_loader import config_loader

logger = logging.getLogger("langchain.agents")


class FormAgent:
    """表单处理Agent"""
    
    def __init__(self):
        self.llm = get_langchain_llm().llm
        self.intent_chain = IntentRecognitionChain()
        self.form_recognition_chain = FormRecognitionChain()
    
    async def process(self, user_input: str) -> Dict[str, Any]:
        """处理用户输入"""
        logger.info(f"FormAgent processing: {user_input[:100]}")
        
        # 1. 意图识别
        intent_result = await self.intent_chain.run(user_input)
        intent_type = intent_result.get("intentType", "chat")
        logger.info(f"Intent recognized: {intent_type}")
        
        if intent_type == "form":
            # 2. 表单识别
            form_result = await self.form_recognition_chain.run(user_input)
            form_code = form_result.get("formCode", "general")
            
            # 3. 字段提取
            field_chain = FieldExtractionChain(form_code)
            extract_result = await field_chain.run(user_input)
            
            return {
                "intentType": "form",
                "formCode": form_code,
                "formName": form_result.get("formName"),
                "extractedFields": extract_result.get("extractedFields", {}),
                "reasoning": extract_result.get("reasoning", ""),
                "confidence": form_result.get("confidence", 0.5)
            }
        
        elif intent_type == "validation":
            # 表单验证
            return {
                "intentType": "validation",
                "message": "请提供需要验证的表单数据"
            }
        
        elif intent_type == "history":
            # 历史记录
            return {
                "intentType": "history",
                "message": "查询历史记录"
            }
        
        elif intent_type == "config":
            # 配置管理
            return {
                "intentType": "config",
                "message": "配置管理"
            }
        
        else:
            # 普通聊天
            return {
                "intentType": "chat",
                "message": user_input
            }
    
    async def validate_form(self, form_code: str, form_data: Dict[str, Any]) -> Dict[str, Any]:
        """验证表单"""
        validation_chain = FormValidationChain(form_code)
        return await validation_chain.run(form_data)


class TaskAgent:
    """任务处理Agent"""
    
    TASK_TYPES = [
        {"type": "extract", "description": "从文本中提取信息"},
        {"type": "summarize", "description": "总结文本内容"},
        {"type": "translate", "description": "翻译文本"},
        {"type": "analyze", "description": "分析数据"},
        {"type": "generate", "description": "生成内容"}
    ]
    
    def __init__(self):
        self.llm = get_langchain_llm().llm
    
    async def analyze_task(self, user_input: str) -> Dict[str, Any]:
        """分析任务类型"""
        system_prompt = f"""你是一个任务分析专家。请分析用户输入，确定任务类型。

可用的任务类型：
{chr(10).join([f"- {t['type']}: {t['description']}" for t in self.TASK_TYPES])}

请以JSON格式输出：
{{
    "taskType": "任务类型",
    "confidence": 置信度,
    "parameters": {{...}}
}}"""
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("user", user_input)
        ])
        
        parser = JsonOutputParser()
        chain = prompt | self.llm | parser
        
        try:
            return await chain.ainvoke({})
        except Exception as e:
            logger.error(f"Task analysis failed: {e}")
            return {"taskType": "generate", "confidence": 0.5, "parameters": {}}
    
    async def execute_task(self, task_type: str, input_data: str, **kwargs) -> str:
        """执行任务"""
        task_templates = {
            "extract": "请从以下文本中提取关键信息：\n{input}\n\n请以JSON格式输出提取结果。",
            "summarize": "请总结以下文本内容：\n{input}\n\n总结要求：简明扼要，突出重点。",
            "translate": "请将以下文本翻译成中文：\n{input}",
            "analyze": "请分析以下数据：\n{input}\n\n请提供详细的分析报告。",
            "generate": "{input}"
        }
        
        template = task_templates.get(task_type, task_templates["generate"])
        
        prompt = ChatPromptTemplate.from_template(template)
        chain = prompt | self.llm
        
        try:
            response = await chain.ainvoke({"input": input_data})
            return response.content
        except Exception as e:
            logger.error(f"Task execution failed: {e}")
            return f"任务执行失败: {e}"


class ChatAgent:
    """聊天Agent"""
    
    def __init__(self):
        self.llm = get_langchain_llm().llm
    
    async def respond(self, user_input: str, context: Optional[List[Dict]] = None) -> str:
        """生成响应"""
        system_prompt = """你是一个友好的AI助手，擅长帮助用户填写表单和回答问题。
        
可用功能：
1. 表单填写：销售订单、请假申请、费用报销等
2. 信息查询：帮助查询各种信息
3. 日常聊天：回答问题、闲聊

请用自然、友好的语言回复用户。"""
        
        messages = []
        
        if context:
            for msg in context:
                role = msg.get("role", "user")
                content = msg.get("content", "")
                if role == "system":
                    messages.append(("system", content))
                elif role == "assistant":
                    messages.append(("assistant", content))
                else:
                    messages.append(("user", content))
        
        messages.append(("system", system_prompt))
        messages.append(("user", user_input))
        
        prompt = ChatPromptTemplate.from_messages(messages)
        chain = prompt | self.llm
        
        try:
            response = await chain.ainvoke({})
            return response.content
        except Exception as e:
            logger.error(f"Chat response failed: {e}")
            return f"抱歉，我遇到了一些问题，请稍后重试。"
