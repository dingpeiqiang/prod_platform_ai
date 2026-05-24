"""
工作流动作处理器

这些动作被 WorkflowEngine 调用，实现前端编辑器创建的工作流节点功能：
1. workflow.start / workflow.end - 工作流控制
2. workflow.call_llm - LLM 调用
3. workflow.generate_form - 表单生成
4. workflow.ask_user / workflow.call_tool - 用户交互和工具调用
5. workflow.set_variable / workflow.http_request - 变量设置和 HTTP 请求
"""
from typing import Dict, Any
import logging
import json
import re

logger = logging.getLogger("workflow_actions")


async def action_workflow_start(context: Any, **kwargs) -> Dict[str, Any]:
    """开始工作流"""
    return {
        "status": "started",
        "message": "工作流已开始"
    }


async def action_workflow_end(context: Any, **kwargs) -> Dict[str, Any]:
    """结束工作流"""
    return {
        "status": "completed",
        "message": "工作流已完成"
    }


async def action_call_llm(context: Any, **kwargs) -> Dict[str, Any]:
    """调用 LLM"""
    prompt = kwargs.get("prompt", "")
    model = kwargs.get("model", "qwen-plus")
    temperature = kwargs.get("temperature", 0.7)
    
    try:
        from app.engine.llm_factory import get_langchain_llm
        llm = get_langchain_llm().llm
        
        from langchain_core.prompts import ChatPromptTemplate
        from langchain_core.output_parsers import StrOutputParser
        
        prompt_template = ChatPromptTemplate.from_messages([
            ("user", "{prompt}")
        ])
        
        chain = prompt_template | llm | StrOutputParser()
        response = await chain.ainvoke({"prompt": prompt})
        
        return {
            "success": True,
            "response": response,
            "model": model
        }
    except Exception as e:
        logger.error(f"[action_call_llm] LLM 调用失败: {e}")
        return {
            "success": False,
            "error": str(e)
        }


async def action_ask_user(context: Any, **kwargs) -> Dict[str, Any]:
    """询问用户"""
    message = kwargs.get("message", "请提供信息")
    required_fields = kwargs.get("required_fields", [])
    
    return {
        "action": "ask_user",
        "message": message,
        "required_fields": required_fields,
        "waiting_for_input": True
    }


async def action_call_tool(context: Any, **kwargs) -> Dict[str, Any]:
    """调用工具"""
    tool_name = kwargs.get("tool_name", "")
    tool_type = kwargs.get("tool_type", "")
    params = kwargs.get("params", {})
    
    try:
        from app.mcp_tools.tool_hub import get_tool_hub
        tool_hub = get_tool_hub()
        
        result = tool_hub.execute_sync(tool_name, params)
        
        return {
            "success": True,
            "result": result,
            "tool_name": tool_name
        }
    except Exception as e:
        logger.error(f"[action_call_tool] 工具调用失败: {e}")
        return {
            "success": False,
            "error": str(e)
        }


async def action_set_variable(context: Any, **kwargs) -> Dict[str, Any]:
    """设置变量"""
    variable_name = kwargs.get("variable_name", "")
    variable_value = kwargs.get("variable_value", "")
    
    if variable_name:
        context.outputs[variable_name] = variable_value
    
    return {
        "success": True,
        "variable_name": variable_name,
        "variable_value": variable_value
    }


async def action_http_request(context: Any, **kwargs) -> Dict[str, Any]:
    """HTTP 请求"""
    import httpx
    
    url = kwargs.get("url", "")
    method = kwargs.get("method", "GET").upper()
    headers = kwargs.get("headers", {})
    body = kwargs.get("body", {})
    
    if not url:
        return {
            "success": False,
            "error": "URL is required"
        }
    
    try:
        async with httpx.AsyncClient() as client:
            if method == "GET":
                response = await client.get(url, headers=headers, params=body)
            elif method == "POST":
                response = await client.post(url, headers=headers, json=body)
            elif method == "PUT":
                response = await client.put(url, headers=headers, json=body)
            elif method == "DELETE":
                response = await client.delete(url, headers=headers)
            else:
                return {
                    "success": False,
                    "error": f"Unsupported method: {method}"
                }
            
            return {
                "success": True,
                "status_code": response.status_code,
                "response": response.text,
                "headers": dict(response.headers)
            }
    except Exception as e:
        logger.error(f"[action_http_request] HTTP 请求失败: {e}")
        return {
            "success": False,
            "error": str(e)
        }


async def action_execute_code(context: Any, **kwargs) -> Dict[str, Any]:
    """执行代码"""
    code = kwargs.get("code", "")
    
    if not code:
        return {
            "success": False,
            "error": "Code is required"
        }
    
    try:
        exec_locals = {}
        exec(code, {}, exec_locals)
        
        result = exec_locals.get("result", None)
        
        return {
            "success": True,
            "result": result
        }
    except Exception as e:
        logger.error(f"[action_execute_code] 代码执行失败: {e}")
        return {
            "success": False,
            "error": str(e)
        }


async def action_parse_output(context: Any, **kwargs) -> Dict[str, Any]:
    """解析输出"""
    content = kwargs.get("content", "")
    pattern = kwargs.get("pattern", "")
    
    if pattern and content:
        try:
            match = re.search(pattern, content)
            if match:
                return {
                    "success": True,
                    "parsed": match.group(0),
                    "groups": match.groups()
                }
            else:
                return {
                    "success": False,
                    "error": "Pattern not found"
                }
        except Exception as e:
            return {
                "success": False,
                "error": str(e)
            }
    
    return {
        "success": True,
        "parsed": content
    }


async def action_query_knowledge(context: Any, **kwargs) -> Dict[str, Any]:
    """查询知识库"""
    query = kwargs.get("query_text", "")
    knowledge_base = kwargs.get("knowledge_base", "")
    query_mode = kwargs.get("query_mode", "similarity")
    
    try:
        from app.services.knowledge_base_service import KnowledgeBaseService
        
        service = KnowledgeBaseService()
        result = await service.search(
            query=query,
            kb_code=knowledge_base,
            mode=query_mode
        )
        
        return {
            "success": True,
            "results": result.get("results", []),
            "query": query
        }
    except Exception as e:
        logger.error(f"[action_query_knowledge] 知识库查询失败: {e}")
        return {
            "success": False,
            "error": str(e)
        }


async def action_generate_form(context: Any, **kwargs) -> Dict[str, Any]:
    """生成表单
    
    这是工作流编辑器中表单节点的核心动作处理器。
    支持从本体生成表单结构，并可选地通过推荐引擎初始化表单数据。
    """
    ontology_code = kwargs.get("ontologyCode", "")
    default_values = kwargs.get("defaultValues", {})
    field_mappings = kwargs.get("fieldMappings", {})
    validation_rules = kwargs.get("validationRules", {})
    input_variable = kwargs.get("inputVariable", "")
    
    if not ontology_code:
        return {
            "success": False,
            "error": "ontologyCode is required for form generation"
        }
    
    try:
        from app.config.config_loader import config_loader
        
        ontology = config_loader.get_ontology(ontology_code)
        if not ontology:
            return {
                "success": False,
                "error": f"Ontology not found: {ontology_code}"
            }
        
        form_schema = {
            "ontologyCode": ontology.get("ontologyCode", ""),
            "ontologyName": ontology.get("ontologyName", ""),
            "description": ontology.get("description", ""),
            "entities": [],
            "fields": []
        }
        
        for entity in ontology.get("entities", []):
            entity_info = {
                "entityCode": entity.get("entityCode", ""),
                "entityName": entity.get("entityName", ""),
                "fields": []
            }
            
            for field in entity.get("fields", []):
                field_def = {
                    "fieldCode": field.get("fieldCode"),
                    "fieldName": field.get("fieldName"),
                    "fieldType": field.get("fieldType", "string"),
                    "required": field.get("required", False),
                    "default": field.get("default", ""),
                    "description": field.get("description", ""),
                    "options": field.get("options", []),
                    "validation": field.get("validation", {}),
                    "placeholder": field.get("placeholder", "")
                }
                
                entity_info["fields"].append(field_def)
                form_schema["fields"].append(field_def)
            
            form_schema["entities"].append(entity_info)
        
        form_data = _initialize_form_data(
            ontology, 
            context, 
            default_values, 
            input_variable
        )
        
        return {
            "success": True,
            "form_schema": form_schema,
            "form_data": form_data,
            "ontology_code": ontology_code,
            "validation_rules": validation_rules,
            "field_mappings": field_mappings
        }
    except Exception as e:
        logger.exception(f"[action_generate_form] 表单生成失败: {e}")
        return {
            "success": False,
            "error": str(e)
        }


def _initialize_form_data(
    ontology: Dict[str, Any], 
    context: Any, 
    default_values: Dict[str, Any],
    input_variable: str
) -> Dict[str, Any]:
    """初始化表单数据"""
    form_data = {}
    
    for entity in ontology.get("entities", []):
        for field in entity.get("fields", []):
            field_code = field.get("fieldCode")
            if not field_code:
                continue
            
            value = None
            
            if not value:
                value = context.inputs.get(field_code)
            
            if not value:
                value = context.outputs.get(field_code)
            
            if not value:
                value = default_values.get(field_code)
            
            if not value:
                value = field.get("default", "")
            
            form_data[field_code] = value
    
    return form_data


async def action_set_prompt(context: Any, **kwargs) -> Dict[str, Any]:
    """设置提示词"""
    prompt = kwargs.get("prompt", "")
    output_var = kwargs.get("output_var", "prompt")
    
    if prompt:
        context.outputs[output_var] = prompt
    
    return {
        "success": True,
        "prompt": prompt
    }
