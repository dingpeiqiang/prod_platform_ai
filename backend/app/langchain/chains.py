from typing import Optional, Dict, Any, List
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import JsonOutputParser, StrOutputParser
from langchain_core.runnables import RunnableSequence
import logging
from .llm_wrapper import get_langchain_llm
from app.core.config_loader import config_loader

logger = logging.getLogger("langchain.chains")


class FormRecognitionChain:
    """表单识别链"""
    
    SYSTEM_PROMPT = """你是一个专业的表单识别助手。
你需要根据用户的输入，识别出对应的表单类型。

可用的表单类型：
{form_types}

请分析用户输入，确定最匹配的表单类型。
如果没有匹配的表单类型，返回通用表单类型。"""
    
    OUTPUT_FORMAT = """请以JSON格式输出：
{{
    "formCode": "表单编码",
    "formName": "表单名称",
    "confidence": 匹配置信度(0-1),
    "reason": "识别理由"
}}"""
    
    def __init__(self):
        self.llm = get_langchain_llm().llm
        self._chain = None
        self._init_chain()
    
    def _init_chain(self):
        """初始化链"""
        ontologies = config_loader.get_all_ontologies()
        form_types = "\n".join([f"- {code}: {info.get('formName', code)}" for code, info in ontologies.items()])
        
        system_prompt = self.SYSTEM_PROMPT.format(form_types=form_types)
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("user", "{input}\n\n" + self.OUTPUT_FORMAT)
        ])
        
        parser = JsonOutputParser()
        self._chain = prompt | self.llm | parser
    
    async def run(self, user_input: str) -> Dict[str, Any]:
        """运行表单识别"""
        if not self._chain:
            return {"formCode": "general", "formName": "通用表单", "confidence": 0.5, "reason": "未初始化"}
        
        try:
            result = await self._chain.ainvoke({"input": user_input})
            return result
        except Exception as e:
            logger.error(f"FormRecognitionChain failed: {e}")
            return {"formCode": "general", "formName": "通用表单", "confidence": 0.3, "reason": f"识别失败: {e}"}


class FieldExtractionChain:
    """字段提取链"""
    
    SYSTEM_PROMPT = """你是一个专业的表单字段提取助手。
请从用户输入中提取表单字段值。

表单定义：
{form_schema}

请仔细分析用户输入，提取对应的字段值。
如果字段无法从输入中提取，请留空。

字段提取规则：
1. 编码类字段（fieldCode包含code/id/no/number）：提取符合格式的编码
2. 枚举类字段（fieldType为select/enum/radio）：匹配枚举值
3. 数值类字段：提取数字，注意单位转换（如"5万"→50000）
4. 日期字段：转换为YYYY-MM-DD格式
5. 文本字段：直接提取文本内容"""
    
    OUTPUT_FORMAT = """请以JSON格式输出提取的字段：
{{
    "extractedFields": {{
        "字段编码": "字段值",
        ...
    }},
    "reasoning": "提取过程说明"
}}"""
    
    def __init__(self, form_code: str):
        self.form_code = form_code
        self.llm = get_langchain_llm().llm
        self._chain = None
        self._init_chain()
    
    def _init_chain(self):
        """初始化链"""
        ontology = config_loader.get_ontology(self.form_code)
        if not ontology:
            logger.warning(f"Ontology not found for {self.form_code}")
            return
        
        form_schema = self._format_ontology(ontology)
        
        system_prompt = self.SYSTEM_PROMPT.format(form_schema=form_schema)
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("user", "{input}\n\n" + self.OUTPUT_FORMAT)
        ])
        
        parser = JsonOutputParser()
        self._chain = prompt | self.llm | parser
    
    def _format_ontology(self, ontology: Dict) -> str:
        """格式化本体定义"""
        fields = []
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                fields.append({
                    "fieldCode": field.get("fieldCode"),
                    "fieldName": field.get("fieldName"),
                    "fieldType": field.get("fieldType", "string"),
                    "required": field.get("required", False),
                    "options": field.get("options", [])
                })
        return str(fields)
    
    async def run(self, user_input: str) -> Dict[str, Any]:
        """运行字段提取"""
        if not self._chain:
            return {"extractedFields": {}, "reasoning": "链未初始化"}
        
        try:
            result = await self._chain.ainvoke({"input": user_input})
            return result
        except Exception as e:
            logger.error(f"FieldExtractionChain failed: {e}")
            return {"extractedFields": {}, "reasoning": f"提取失败: {e}"}


class FormValidationChain:
    """表单验证链"""
    
    SYSTEM_PROMPT = """你是一个专业的表单验证助手。
请验证表单数据是否符合要求。

表单定义：
{form_schema}

验证规则：
1. 必填字段必须有值
2. 字段类型必须正确（数字、日期等）
3. 枚举字段必须在选项范围内
4. 数值字段必须在允许范围内
5. 格式验证（邮箱、手机号等）"""
    
    OUTPUT_FORMAT = """请以JSON格式输出验证结果：
{{
    "valid": true/false,
    "errors": [
        {{
            "field": "字段编码",
            "fieldName": "字段名称",
            "error": "错误描述",
            "type": "错误类型"
        }}
    ],
    "warnings": [
        {{
            "field": "字段编码",
            "message": "警告信息"
        }}
    ],
    "validatedFields": ["已验证的字段列表"]
}}"""
    
    def __init__(self, form_code: str):
        self.form_code = form_code
        self.llm = get_langchain_llm().llm
        self._chain = None
        self._init_chain()
    
    def _init_chain(self):
        """初始化链"""
        ontology = config_loader.get_ontology(self.form_code)
        if not ontology:
            logger.warning(f"Ontology not found for {self.form_code}")
            return
        
        form_schema = self._format_ontology(ontology)
        
        system_prompt = self.SYSTEM_PROMPT.format(form_schema=form_schema)
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("user", "表单数据：{form_data}\n\n" + self.OUTPUT_FORMAT)
        ])
        
        parser = JsonOutputParser()
        self._chain = prompt | self.llm | parser
    
    def _format_ontology(self, ontology: Dict) -> str:
        """格式化本体定义"""
        fields = []
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                fields.append({
                    "fieldCode": field.get("fieldCode"),
                    "fieldName": field.get("fieldName"),
                    "fieldType": field.get("fieldType", "string"),
                    "required": field.get("required", False),
                    "options": field.get("options", []),
                    "ruleDescription": field.get("ruleDescription", "")
                })
        return str(fields)
    
    async def run(self, form_data: Dict[str, Any]) -> Dict[str, Any]:
        """运行表单验证"""
        if not self._chain:
            return {"valid": True, "errors": [], "warnings": [], "validatedFields": []}
        
        try:
            result = await self._chain.ainvoke({"form_data": str(form_data)})
            return result
        except Exception as e:
            logger.error(f"FormValidationChain failed: {e}")
            return {"valid": False, "errors": [{"field": "", "fieldName": "系统", "error": f"验证失败: {e}", "type": "system"}], "warnings": [], "validatedFields": []}


class IntentRecognitionChain:
    """意图识别链"""
    
    INTENT_TYPES = [
        {"type": "form", "description": "表单相关操作：创建、填写、修改表单"},
        {"type": "chat", "description": "普通聊天：问答、闲聊"},
        {"type": "validation", "description": "表单验证：校验表单数据"},
        {"type": "history", "description": "历史记录：查看、管理历史数据"},
        {"type": "config", "description": "配置管理：系统配置相关"}
    ]
    
    SYSTEM_PROMPT = """你是一个意图识别专家。
请分析用户输入，识别其意图类型。

可用的意图类型：
{intent_types}

请根据用户输入选择最合适的意图类型。"""
    
    OUTPUT_FORMAT = """请以JSON格式输出：
{{
    "intentType": "意图类型",
    "confidence": 置信度(0-1),
    "reason": "识别理由",
    "parameters": {{
        "可选参数": "值"
    }}
}}"""
    
    def __init__(self):
        self.llm = get_langchain_llm().llm
        self._chain = None
        self._init_chain()
    
    def _init_chain(self):
        """初始化链"""
        intent_types = "\n".join([f"- {item['type']}: {item['description']}" for item in self.INTENT_TYPES])
        
        system_prompt = self.SYSTEM_PROMPT.format(intent_types=intent_types)
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("user", "{input}\n\n" + self.OUTPUT_FORMAT)
        ])
        
        parser = JsonOutputParser()
        self._chain = prompt | self.llm | parser
    
    async def run(self, user_input: str) -> Dict[str, Any]:
        """运行意图识别"""
        if not self._chain:
            return {"intentType": "chat", "confidence": 0.5, "reason": "未初始化", "parameters": {}}
        
        try:
            result = await self._chain.ainvoke({"input": user_input})
            return result
        except Exception as e:
            logger.error(f"IntentRecognitionChain failed: {e}")
            return {"intentType": "chat", "confidence": 0.3, "reason": f"识别失败: {e}", "parameters": {}}
