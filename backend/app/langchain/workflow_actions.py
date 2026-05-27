"""
工作流动作处理器

这些动作被 WorkflowEngine 调用，实现前端编辑器创建的工作流节点功能：
1. workflow.start / workflow.end - 工作流控制
2. workflow.call_llm - LLM 调用
3. workflow.generate_form - 表单生成（支持等待用户输入）
4. workflow.validate_form - 表单校验
5. workflow.merge_results - 结果合并
6. workflow.ask_user / workflow.call_tool - 用户交互和工具调用
7. workflow.set_variable / workflow.http_request - 变量设置和 HTTP 请求
"""
from typing import Dict, Any, List, Optional
from app.core.logger import get_logger

logger = get_logger(__name__)
import json
import re



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
    """生成表单数据
    
    表单节点特殊处理：
    - 返回 action=ask_user 表示需要等待用户输入
    - WorkflowEngine 检测到此标记后会暂停执行
    - 用户提交表单后，通过 resume() 恢复执行
    
    参数：
    - ontologyCode: 本体编码
    - waitForSubmit: 是否等待用户提交（默认 True）
    - autoSubmit: 是否自动提交（默认 False，设为 True 则不等待）
    """
    ontology_code = kwargs.get("ontologyCode", "")
    default_values = kwargs.get("defaultValues", {})
    field_mappings = kwargs.get("fieldMappings", {})
    validation_rules = kwargs.get("validationRules", {})
    input_variable = kwargs.get("inputVariable", "")
    tool_name = kwargs.get("toolName", "") or kwargs.get("toolType", "")
    enable_recommendation = kwargs.get("enableRecommendation", True)
    auto_submit = kwargs.get("autoSubmit", False)
    
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
        
        form_schema = _build_form_schema(ontology)
        
        extracted_fields = context.inputs
        
        tool_result = None
        if tool_name:
            tool_result = context.step_results.get("call_tool", {})
            if not tool_result:
                tool_result = context.outputs.get("tool_result", {})
        
        user_input = context.inputs.get("user_input", "")
        user_id = context.inputs.get("user_id", "default")
        
        form_data = _initialize_form_data(
            ontology=ontology,
            context=context,
            default_values=default_values,
            extracted_fields=extracted_fields,
            tool_result=tool_result,
            field_mappings=field_mappings,
            ontology_code=ontology_code,
            user_input=user_input,
            user_id=user_id,
            enable_recommendation=enable_recommendation
        )
        
        result = {
            "success": True,
            "action": "generate_form",
            "formCode": ontology_code,
            "form_schema": form_schema,
            "extractedFields": form_data,
            "ontology_code": ontology_code,
            "validation_rules": validation_rules,
            "field_mappings": field_mappings,
            "message": tool_result.get("success") if tool_result else "使用默认表单"
        }
        
        if auto_submit:
            result["action"] = "generate_form"
            result["auto_submitted"] = True
            return result
        
        result["action"] = "ask_user"
        result["waiting_for_input"] = True
        result["message"] = "请填写并提交表单"
        
        return result
        
    except Exception as e:
        logger.exception(f"[action_generate_form] 表单生成失败: {e}")
        return {
            "success": False,
            "error": str(e)
        }


def _build_form_schema(ontology: Dict[str, Any]) -> Dict[str, Any]:
    """构建表单结构"""
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
    
    return form_schema


def _initialize_form_data(
    ontology: Dict[str, Any],
    context: Any,
    default_values: Dict[str, Any],
    extracted_fields: Dict[str, Any],
    tool_result: Optional[Dict[str, Any]],
    field_mappings: Dict[str, Any],
    ontology_code: str = "",
    user_input: str = "",
    user_id: str = "default",
    enable_recommendation: bool = True
) -> Dict[str, Any]:
    """初始化表单数据
    
    优先级：
    1. 工具查询结果（通过字段映射）
    2. 提取的字段
    3. 上下文变量
    4. 智能推荐引擎
    5. 默认值
    6. 本体定义的默认值
    """
    form_data = {}
    rec_engine = None
    
    if enable_recommendation:
        try:
            from app.services.recommendation_engine import get_recommendation_engine
            rec_engine = get_recommendation_engine()
        except Exception as e:
            logger.warning(f"[_initialize_form_data] 无法获取推荐引擎: {e}")
    
    for entity in ontology.get("entities", []):
        for field in entity.get("fields", []):
            field_code = field.get("fieldCode")
            if not field_code:
                continue
            
            value = None
            
            if tool_result and tool_result.get("success"):
                result_data = tool_result.get("result", tool_result)
                if field_mappings and field_code in field_mappings:
                    source_fields = field_mappings[field_code]
                    if isinstance(source_fields, str):
                        source_fields = [source_fields]
                    for source_field in source_fields:
                        if source_field in result_data and result_data[source_field]:
                            value = result_data[source_field]
                            break
                if not value and field_code in result_data:
                    value = result_data[field_code]
            
            if not value and extracted_fields:
                value = extracted_fields.get(field_code)
            
            if not value:
                value = context.inputs.get(field_code)
            
            if not value:
                value = context.outputs.get(field_code)
            
            if not value and rec_engine and ontology_code:
                try:
                    rec_result = rec_engine.recommend(
                        form_code=ontology_code,
                        field_code=field_code,
                        user_input=user_input,
                        user_id=user_id
                    )
                    if rec_result and rec_result.recommendations and len(rec_result.recommendations) > 0:
                        value = rec_result.recommendations[0].value
                        logger.debug(f"[_initialize_form_data] 字段 {field_code} 获取到推荐值: {value}")
                except Exception as e:
                    logger.warning(f"[_initialize_form_data] 字段 {field_code} 推荐失败: {e}")
            
            if not value:
                value = default_values.get(field_code)
            
            if not value:
                value = field.get("default", "")
            
            form_data[field_code] = value
    
    return form_data


async def action_validate_form(context: Any, **kwargs) -> Dict[str, Any]:
    """验证表单数据
    
    参考 tariff_actions.action_validate_form 实现：
    1. 从上下文获取表单数据
    2. 根据本体定义的校验规则进行校验
    3. 返回校验结果摘要
    """
    form_result = context.step_results.get("generate_form", {})
    if not form_result:
        form_result = context.outputs.get("form_result", {})
    
    form_data = form_result.get("extractedFields", {})
    ontology_code = form_result.get("ontologyCode") or form_result.get("formCode", "")
    
    if not form_data:
        return {
            "success": False,
            "error": "No form data to validate"
        }
    
    try:
        from app.config.config_loader import config_loader
        
        ontology = config_loader.get_ontology(ontology_code) if ontology_code else None
        
        validation_results = _validate_fields(form_data, ontology)
        
        passed = sum(1 for r in validation_results if r["result"] == "pass")
        warnings = sum(1 for r in validation_results if r["result"] == "warning")
        errors = sum(1 for r in validation_results if r["result"] == "error")
        
        return {
            "success": errors == 0,
            "validationResults": validation_results,
            "summary": {
                "total": len(validation_results),
                "passed": passed,
                "warnings": warnings,
                "errors": errors
            },
            "message": _generate_validation_message(passed, warnings, errors)
        }
    except Exception as e:
        logger.exception(f"[action_validate_form] 表单校验失败: {e}")
        return {
            "success": False,
            "error": str(e)
        }


def _validate_fields(form_data: Dict[str, Any], ontology: Optional[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """验证字段"""
    results = []
    
    if not ontology:
        for field_code, value in form_data.items():
            results.append({
                "field": field_code,
                "fieldName": field_code,
                "value": value,
                "result": "pass",
                "reason": "无校验规则",
                "suggestion": ""
            })
        return results
    
    for entity in ontology.get("entities", []):
        for field in entity.get("fields", []):
            field_code = field.get("fieldCode")
            field_name = field.get("fieldName", field_code)
            value = form_data.get(field_code, "")
            
            result, reason, suggestion = _validate_single_field(field_code, value, field)
            
            results.append({
                "field": field_code,
                "fieldName": field_name,
                "value": value,
                "result": result,
                "reason": reason,
                "suggestion": suggestion
            })
    
    return results


def _validate_single_field(field_code: str, value: Any, field_def: Dict[str, Any]) -> tuple:
    """验证单个字段"""
    required = field_def.get("required", False)
    
    if required and (value is None or value == "" or (isinstance(value, str) and value.strip() == "")):
        return ("error", "此字段不能为空", "")
    
    if value is None or value == "" or (isinstance(value, str) and value.strip() == ""):
        return ("pass", "未填写", "")
    
    validation = field_def.get("validation", {})
    
    if validation.get("pattern"):
        pattern = validation["pattern"]
        if not re.match(pattern, str(value)):
            return ("error", validation.get("pattern_error", "格式不正确"), "")
    
    options = field_def.get("options", [])
    if options:
        valid_values = []
        for opt in options:
            if isinstance(opt, str):
                valid_values.append(opt)
            elif isinstance(opt, dict):
                valid_values.append(opt.get("value", ""))
        
        if str(value) not in valid_values:
            return ("error", "值不在有效选项中", f"有效值: {', '.join(valid_values[:5])}...")
    
    if validation.get("max_length"):
        max_len = validation["max_length"]
        if len(str(value)) > max_len:
            return ("error", f"长度超过限制", f"最大长度{max_len}字符")
    
    if validation.get("type") == "number":
        try:
            float(value)
        except (ValueError, TypeError):
            return ("error", "类型不正确，应为数字", "")
    
    return ("pass", "校验通过", "")


def _generate_validation_message(passed: int, warnings: int, errors: int) -> str:
    """生成校验消息"""
    if errors == 0 and warnings == 0:
        return f"表单校验完成，全部{passed}个字段通过！"
    elif errors > 0:
        return f"表单校验完成，{passed}个字段通过，{warnings}个警告，{errors}个错误，请修正后提交。"
    else:
        return f"表单校验完成，{passed}个字段通过，{warnings}个警告，请确认后提交。"


async def action_merge_results(context: Any, **kwargs) -> Dict[str, Any]:
    """合并最终结果"""
    form_result = context.step_results.get("generate_form", {})
    if not form_result:
        form_result = context.outputs.get("form_result", {})
    
    validate_result = context.step_results.get("validate_form", {})
    if not validate_result:
        validate_result = context.outputs.get("validation_result", {})
    
    return {
        "success": True,
        "action": form_result.get("action", "generate_form"),
        "formCode": form_result.get("formCode") or form_result.get("ontology_code"),
        "form_schema": form_result.get("form_schema"),
        "extractedFields": form_result.get("extractedFields", {}),
        "validationResults": validate_result.get("validationResults", []),
        "summary": validate_result.get("summary"),
        "message": validate_result.get("message", form_result.get("message"))
    }


async def action_handle_missing_fields(context: Any, **kwargs) -> Dict[str, Any]:
    """处理缺失字段"""
    validate_result = context.step_results.get("validate_form", {})
    if not validate_result:
        validate_result = context.outputs.get("validation_result", {})
    
    validation_results = validate_result.get("validationResults", [])
    
    missing_fields = []
    for result in validation_results:
        if result.get("result") == "error":
            missing_fields.append({
                "field": result.get("field"),
                "fieldName": result.get("fieldName"),
                "reason": result.get("reason"),
                "suggestion": result.get("suggestion")
            })
    
    if missing_fields:
        return {
            "action": "ask_user",
            "missing_fields": missing_fields,
            "message": f"请补充以下 {len(missing_fields)} 个必填字段"
        }
    
    return {
        "action": "continue",
        "message": "所有字段校验通过"
    }


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
