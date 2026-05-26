"""
批量 AI 推断服务 - 一次调用处理多个字段的智能推断
增强版：深度结合本体信息，支持字段描述、约束规则、实体级约束和业务规则
"""
import json
import logging
import re
from typing import Dict, List, Any, Optional

logger = logging.getLogger("batch_ai_inference")


def _diagnose_empty_response(prompt: str, model_config: dict = None):
    """
    诊断 LLM 返回空响应的原因
    
    可能原因分析：
    1. 提示词问题：格式错误、缺少输出格式指令、内容过长
    2. 配置问题：API Key 无效、URL 错误、模型名称错误
    3. 网络问题：连接超时、服务不可用
    4. 服务端问题：限流、配额耗尽、模型加载失败
    
    Args:
        prompt: 发送给 LLM 的提示词
        model_config: 模型配置
    """
    logger.warning("=" * 70)
    logger.warning("[BatchAIInference] 空响应深度诊断")
    logger.warning("=" * 70)
    
    # 1. 配置信息诊断
    logger.warning("\n【1】配置信息检查")
    if model_config:
        provider = model_config.get('provider', 'unknown')
        model = model_config.get('model', 'unknown')
        base_url = model_config.get('baseUrl', 'unknown')
        api_key_set = bool(model_config.get('apiKey'))
        
        logger.warning(f"   Provider: {provider}")
        logger.warning(f"   Model: {model}")
        logger.warning(f"   Base URL: {base_url}")
        logger.warning(f"   API Key: {'已设置' if api_key_set else '❌ 未设置'}")
        logger.warning(f"   Temperature: {model_config.get('temperature', 0.3)}")
        logger.warning(f"   Max Tokens: {model_config.get('maxTokens', 2048)}")
        
        # 配置问题检测
        if not api_key_set:
            logger.warning("   ⚠️ 警告: API Key 未设置，这可能导致认证失败")
        if not base_url or base_url == 'unknown':
            logger.warning("   ⚠️ 警告: Base URL 未配置")
        if not model or model == 'unknown':
            logger.warning("   ⚠️ 警告: 模型名称未配置")
    else:
        logger.warning("   ⚠️ 警告: 未获取到模型配置")
    
    # 2. 提示词分析
    logger.warning("\n【2】提示词分析")
    prompt_length = len(prompt)
    logger.warning(f"   提示词长度: {prompt_length} 字符")
    
    # 提示词长度检测
    if prompt_length == 0:
        logger.warning("   ❌ 严重: 提示词为空！")
    elif prompt_length < 100:
        logger.warning("   ⚠️ 警告: 提示词过短，可能缺少必要信息")
    elif prompt_length > 10000:
        logger.warning("   ⚠️ 警告: 提示词过长，可能被截断或触发限流")
    elif prompt_length > 5000:
        logger.warning("   ⚠️ 提示词较长，建议优化")
    
    # 输出格式指令检测
    has_json_format = bool(re.search(r'"fields":\s*\{', prompt)) or \
                      bool(re.search(r'JSON\s*格式', prompt, re.IGNORECASE)) or \
                      bool(re.search(r'请输出.*JSON', prompt, re.IGNORECASE))
    
    has_output_example = bool(re.search(r'\{\s*".*":.*\}', prompt))
    
    logger.warning(f"   包含 JSON 格式指令: {'✅ 是' if has_json_format else '❌ 否'}")
    logger.warning(f"   包含输出示例: {'✅ 是' if has_output_example else '❌ 否'}")
    
    if not has_json_format:
        logger.warning("   ⚠️ 警告: 提示词中未明确要求 JSON 格式输出")
    if not has_output_example:
        logger.warning("   ⚠️ 警告: 提示词中未包含输出格式示例")
    
    # 提示词结构分析
    required_sections = [
        ('任务说明', ['任务', '说明', '目标', '要求']),
        ('字段定义', ['字段', '表单', '需要提取', '需要推断']),
        ('输出格式', ['输出', '格式', '返回', 'JSON']),
        ('规则说明', ['规则', '注意', '必须', '不要'])
    ]
    
    logger.warning("\n【3】提示词结构检查")
    for section_name, keywords in required_sections:
        found = any(keyword in prompt for keyword in keywords)
        status = '✅' if found else '❌'
        logger.warning(f"   {status} {section_name}: {'包含' if found else '缺失'}")
    
    # 3. 常见问题排查建议
    logger.warning("\n【4】常见问题排查建议")
    logger.warning("   ┌─────────────────────────────────────────────────────────────┐")
    logger.warning("   │ 可能原因                     │ 排查方法                      │")
    logger.warning("   ├─────────────────────────────────────────────────────────────┤")
    
    issues = []
    
    if not model_config or not model_config.get('apiKey'):
        issues.append(("API Key 问题", "检查环境变量 LLM_API_KEY 是否正确设置"))
    
    if not model_config or not model_config.get('baseUrl'):
        issues.append(("URL 配置错误", "检查环境变量 LLM_BASE_URL 是否正确"))
    
    if prompt_length == 0:
        issues.append(("提示词为空", "检查调用参数，确保传入了有效提示词"))
    
    if prompt_length > 10000:
        issues.append(("提示词过长", "优化提示词，减少冗余内容"))
    
    if not has_json_format:
        issues.append(("缺少格式指令", "在提示词中添加明确的 JSON 输出格式要求"))
    
    if not has_output_example:
        issues.append(("缺少输出示例", "添加 JSON 输出示例帮助模型理解格式"))
    
    if not issues:
        issues.append(("未知原因", "检查网络连接、服务状态、API 配额"))
    
    for issue, suggestion in issues:
        logger.warning(f"   │ {issue:<26} │ {suggestion:<36} │")
    
    logger.warning("   └─────────────────────────────────────────────────────────────┘")
    
    # 4. 提示词预览
    logger.warning("\n【5】提示词预览（前500字符）")
    logger.warning("-" * 70)
    preview = prompt[:500] if prompt else "(空)"
    logger.warning(preview)
    if len(prompt) > 500:
        logger.warning("...（剩余部分已截断）")
    logger.warning("-" * 70)
    
    logger.warning("\n【6】建议操作")
    logger.warning("   1. 检查 API Key 是否有效且未过期")
    logger.warning("   2. 确认 Base URL 正确，可通过浏览器访问测试")
    logger.warning("   3. 验证提示词中包含明确的 JSON 输出格式要求")
    logger.warning("   4. 添加输出示例帮助模型理解预期格式")
    logger.warning("   5. 如果提示词过长，考虑分批次调用或优化内容")
    logger.warning("   6. 检查模型服务是否正常运行")
    logger.warning("=" * 70)


class BatchAIInferenceService:
    """批量 AI 推断服务"""
    
    @staticmethod
    async def infer_fields(
        form_code: str,
        field_codes: List[str],
        user_input: str,
        ontology: dict,
        existing_values: Dict[str, Any],
        model: str = "qwen-plus",
        temperature: float = 0.3
    ) -> Dict[str, List[str]]:
        """
        批量推断多个字段的值
        
        Args:
            form_code: 表单编码
            field_codes: 需要推断的字段列表
            user_input: 用户输入文本（可选）
            ontology: 本体定义
            existing_values: 已确定的字段值（用于上下文）
            model: LLM 模型
            temperature: 温度参数
        
        Returns:
            {field_code: [候选值列表]}
        """
        if not field_codes:
            logger.debug("[BatchAIInference] 没有需要推断的字段")
            return {}
        
        # 检查本体是否有业务规则描述
        has_business_rules = False
        business_rules = BatchAIInferenceService._get_business_rules(ontology)
        if business_rules:
            has_business_rules = True
        
        # 如果有业务规则描述，或者有用户输入，调用 LLM
        # 用户输入可以为空，但本体信息必须作为提示词的一部分
        logger.info(f"[BatchAIInference] 开始批量推断，表单: {form_code}, 字段数: {len(field_codes)}, 有业务规则: {has_business_rules}")
        
        # 构建批量推断提示词（增强版）
        prompt = BatchAIInferenceService._build_enhanced_batch_prompt(
            form_code, field_codes, user_input, ontology, existing_values
        )
        
        # 调用 LLM
        try:
            response = await BatchAIInferenceService._call_llm(prompt)
            
            if response:
                result = response.get("fields", {})
                logger.info(f"[BatchAIInference] 批量推断完成，推断出 {len(result)} 个字段")
                return result
            else:
                logger.warning("[BatchAIInference] LLM 返回空响应")
                return {}
        except Exception as e:
            logger.error(f"[BatchAIInference] 批量推断失败: {e}")
            return {}
    
    @staticmethod
    async def extract_definite_values(
        form_code: str,
        ontology: dict,
        user_input: str
    ) -> Dict[str, Any]:
        """
        从用户输入中提取确定性值
        
        Args:
            form_code: 表单编码
            ontology: 本体定义
            user_input: 用户输入文本
        
        Returns:
            {field_code: value}
        """
        if not user_input or not user_input.strip():
            logger.debug("[BatchAIInference] 用户输入为空，跳过确定性值提取")
            return {}
        
        field_definitions = BatchAIInferenceService._get_enhanced_field_definitions(ontology)
        
        if not field_definitions:
            logger.debug("[BatchAIInference] 没有字段定义，跳过确定性值提取")
            return {}
        
        prompt = f"""
你是一个智能表单字段提取器。请从用户输入中提取表单字段的值。

## 表单信息
表单编码: {form_code}

## 需要提取的字段
{json.dumps(field_definitions, ensure_ascii=False, indent=2)}

## 用户输入
{user_input}

## 输出格式
请输出 JSON 格式：
{{
  "field_code_1": "提取的值",
  "field_code_2": "",
  ...
}}

## 规则
1. 如果用户明确提到某个字段的值，直接提取
2. 如果无法确定，返回空字符串 ""
3. 严格按照 JSON 格式输出，不要包含其他内容
4. 提取的值必须符合字段约束（类型、格式、枚举选项等）
"""
        
        try:
            response = await BatchAIInferenceService._call_llm(prompt.strip())
            
            if response:
                logger.info(f"[BatchAIInference] 确定性值提取完成，提取到 {len(response)} 个字段")
                return response
            else:
                logger.warning("[BatchAIInference] 确定性值提取返回空响应")
                return {}
        except Exception as e:
            logger.error(f"[BatchAIInference] 确定性值提取失败: {e}")
            return {}
    
    @staticmethod
    async def _call_llm(prompt: str) -> Optional[Dict[str, Any]]:
        """
        调用 LLM 并返回 JSON 结果
        
        Args:
            prompt: 提示词
        
        Returns:
            JSON 解析后的字典，或 None
        """
        try:
            from app.services.llm_service import llm_service
            from app.services.llm.factory import ProviderFactory
            from app.core.database import SessionLocal
            from app.models.llm_user_config import LLMUserConfig
            
            # 记录提示词详细信息
            logger.info("=" * 70)
            logger.info("[BatchAIInference] LLM 调用开始")
            logger.info("=" * 70)
            logger.info(f"[BatchAIInference] 提示词长度: {len(prompt)} 字符")
            
            # 记录完整提示词内容
            logger.debug("[BatchAIInference] 提示词内容:")
            logger.debug("-" * 70)
            logger.debug(prompt if prompt else "(空)")
            logger.debug("-" * 70)
            
            # 从数据库获取模型配置
            db = SessionLocal()
            model_db_config = None
            custom_provider = None
            model_config = None
            
            try:
                # 获取第一个激活的配置
                model_db_config = db.query(LLMUserConfig).filter(
                    LLMUserConfig.is_active == True
                ).first()
                
                if model_db_config:
                    api_key = (model_db_config.api_key or '').strip().strip('`')
                    provider_type = model_db_config.provider or 'openai'
                    base_url = model_db_config.base_url or ''
                    
                    logger.info(f"[BatchAIInference] 从数据库找到模型配置: {model_db_config.model}, provider: {provider_type}")
                    
                    model_config = {
                        'provider': provider_type,
                        'model': model_db_config.model,
                        'apiKey': api_key,
                        'baseUrl': base_url,
                        'authType': model_db_config.auth_type if hasattr(model_db_config, 'auth_type') else 'bearer',
                        'authHeader': getattr(model_db_config, 'auth_header', None),
                        'apiFormat': getattr(model_db_config, 'api_format', 'openai'),
                        'isFullUrl': getattr(model_db_config, 'is_full_url', False),
                        'temperature': getattr(model_db_config, 'temperature', 0.3),
                        'maxTokens': getattr(model_db_config, 'max_tokens', 2048)
                    }
                    
                    custom_provider = ProviderFactory.create(model_config['provider'], model_config)
                    logger.info(f"[BatchAIInference] 使用数据库配置创建 Provider")
                else:
                    logger.warning("[BatchAIInference] 未找到数据库配置，使用全局配置")
            finally:
                db.close()
            
            # 使用流式调用获取响应
            response_chunks = []
            
            if custom_provider:
                # 使用从数据库获取的配置
                async for chunk, _ in custom_provider.call_stream(prompt):
                    if chunk:
                        response_chunks.append(chunk)
            else:
                # 回退到全局配置
                async for chunk in llm_service.call_llm_stream(prompt):
                    if chunk:
                        response_chunks.append(chunk)
            
            full_response = "".join(response_chunks)
            
            # 记录响应详细信息
            logger.info(f"[BatchAIInference] LLM 响应长度: {len(full_response)} 字符")
            
            # 记录完整响应内容
            logger.debug("[BatchAIInference] LLM 响应内容:")
            logger.debug("-" * 70)
            logger.debug(full_response if full_response else "(空)")
            logger.debug("-" * 70)
            
            if not full_response:
                logger.warning("[BatchAIInference] LLM 返回空响应")
                _diagnose_empty_response(prompt, model_config)
                logger.info("=" * 70)
                logger.info("[BatchAIInference] LLM 调用结束（空响应）")
                logger.info("=" * 70)
                return None
            
            # 尝试解析 JSON
            try:
                result = json.loads(full_response)
                logger.info(f"[BatchAIInference] JSON 解析成功")
                logger.info("=" * 70)
                logger.info("[BatchAIInference] LLM 调用结束（成功）")
                logger.info("=" * 70)
                return result
            except json.JSONDecodeError as e:
                # 如果不是有效的 JSON，尝试提取其中的 JSON 部分
                logger.warning(f"[BatchAIInference] 响应不是有效的 JSON: {str(e)}")
                logger.warning(f"[BatchAIInference] 原始响应预览: {full_response[:500]}")
                
                # 尝试提取 JSON（支持响应前后有其他文本的情况）
                start = full_response.find("{")
                end = full_response.rfind("}")
                
                if start != -1 and end != -1 and start < end:
                    json_str = full_response[start:end+1]
                    logger.debug(f"[BatchAIInference] 尝试提取 JSON 片段: {json_str[:200]}...")
                    try:
                        result = json.loads(json_str)
                        logger.info(f"[BatchAIInference] 提取的 JSON 片段解析成功")
                        logger.info("=" * 70)
                        logger.info("[BatchAIInference] LLM 调用结束（修复成功）")
                        logger.info("=" * 70)
                        return result
                    except json.JSONDecodeError as e2:
                        logger.error(f"[BatchAIInference] 无法解析提取的 JSON 片段: {str(e2)}")
                        
                        # 进一步尝试：查找最外层的完整 JSON 对象
                        # 使用正则表达式匹配完整的 JSON 对象
                        import re
                        json_pattern = r'\{[\s\S]*\}'
                        matches = re.findall(json_pattern, full_response)
                        for match in matches:
                            try:
                                result = json.loads(match)
                                logger.info(f"[BatchAIInference] 通过正则提取的 JSON 解析成功")
                                logger.info("=" * 70)
                                logger.info("[BatchAIInference] LLM 调用结束（正则修复成功）")
                                logger.info("=" * 70)
                                return result
                            except json.JSONDecodeError:
                                continue
                
                logger.info("=" * 70)
                logger.info("[BatchAIInference] LLM 调用结束（解析失败）")
                logger.info("=" * 70)
                return None
                
        except Exception as e:
            logger.error(f"[BatchAIInference] 调用 LLM 失败: {e}", exc_info=True)
            logger.info("=" * 70)
            logger.info("[BatchAIInference] LLM 调用结束（异常）")
            logger.info("=" * 70)
            return None
    
    @staticmethod
    def _build_enhanced_batch_prompt(
        form_code: str,
        field_codes: List[str],
        user_input: str,
        ontology: dict,
        existing_values: Dict[str, Any]
    ) -> str:
        """构建增强版批量推断提示词"""
        # 获取字段定义（包含约束信息）
        field_defs = BatchAIInferenceService._get_enhanced_field_definitions_for_codes(
            ontology, field_codes
        )
        
        # 获取实体级约束
        entity_constraints = BatchAIInferenceService._get_entity_constraints(ontology)
        
        # 获取业务规则
        business_rules = BatchAIInferenceService._get_business_rules(ontology)
        
        # 获取字段联动关系
        field_dependencies = BatchAIInferenceService._get_field_dependencies(ontology)
        
        # 判断用户输入是否为空
        has_user_input = bool(user_input and user_input.strip())
        
        # 构建提示词
        prompt_parts = [
            "你是一个严格的JSON格式输出助手。你的任务是根据表单本体定义和业务规则，输出字段推断结果。\n",
            "⚠️ 警告：你必须严格按照指定的JSON格式输出，不允许输出任何解释性文字、说明或额外内容！\n\n",
            
            "## 任务说明\n",
            f"- 表单编码: {form_code}\n"
        ]
        
        if has_user_input:
            prompt_parts.append(f"- 用户输入: {user_input}\n\n")
        else:
            prompt_parts.append("- 用户输入: （暂无用户输入，仅基于本体信息进行推荐）\n\n")
        
        prompt_parts.append("## 已确定的字段值\n")
        prompt_parts.append(f"{json.dumps(existing_values, ensure_ascii=False, indent=2)}\n\n")
        
        # 添加字段定义
        prompt_parts.append("## 需要推断的字段\n")
        prompt_parts.append(f"{json.dumps(field_defs, ensure_ascii=False, indent=2)}\n\n")
        
        # 添加字段联动关系
        if field_dependencies:
            prompt_parts.append("## 字段联动关系\n")
            prompt_parts.append(f"{json.dumps(field_dependencies, ensure_ascii=False, indent=2)}\n\n")
        
        # 添加实体级约束
        if entity_constraints:
            prompt_parts.append("## 实体级约束\n")
            prompt_parts.append(f"{json.dumps(entity_constraints, ensure_ascii=False, indent=2)}\n\n")
        
        # 添加业务规则
        if business_rules:
            prompt_parts.append("## 业务规则\n")
            for rule in business_rules:
                prompt_parts.append(f"- {rule}\n")
            prompt_parts.append("\n")
        
        # 添加输出格式和规则
        prompt_parts.extend([
            "## 输出格式\n",
            "===========\n",
            "### 必须输出纯JSON，不包含任何其他内容！\n\n",
            "你的响应必须是一个完整的、有效的JSON对象，**不允许输出任何解释、说明、注释或其他文本**！\n\n",
            "输出格式示例：\n",
            "{\n",
            '  "fields": {\n',
            '    "bossid": ["ABC123", "DEF456", "GHI789"],\n',
            '    "seq_no": ["001", "002"],\n',
            '    "reporter": [],\n',
            '    "action_type": ["新增", "变更"]\n',
            "  },\n",
            '  "reasoning": "根据表单本体信息推断字段候选值"\n',
            "}\n\n",
            
            "## 严格规则（必须全部遵守）\n",
            "============================\n",
            "⛔ 【强制】必须以 `{` 开头，以 `}` 结尾，中间只包含JSON内容\n",
            "⛔ 【强制】不允许输出任何自然语言文本、解释、分析或说明\n",
            "⛔ 【强制】不允许包含 markdown 格式、代码块标记或其他格式符号\n",
            "⛔ 【强制】所有字符串必须使用双引号，键名必须用双引号包裹\n",
            "⛔ 【强制】数组和对象必须正确闭合，逗号使用正确\n",
            "✅ 每个字段的候选值数量不超过5个\n",
            "✅ 无法推断的字段返回空数组 []\n",
            "✅ 候选值必须符合字段类型和约束要求\n",
            "✅ 遵守字段联动关系和业务规则\n"
        ])
        
        return "".join(prompt_parts)
    
    @staticmethod
    def _get_enhanced_field_definitions(ontology: dict) -> List[Dict[str, Any]]:
        """获取增强版字段定义列表（包含约束信息）"""
        definitions = []
        
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_info = BatchAIInferenceService._extract_field_info(field)
                definitions.append(field_info)
        
        return definitions
    
    @staticmethod
    def _get_enhanced_field_definitions_for_codes(
        ontology: dict,
        field_codes: List[str]
    ) -> List[Dict[str, Any]]:
        """获取指定字段编码的增强版字段定义"""
        definitions = []
        
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_code = field.get("fieldCode")
                if field_code in field_codes:
                    field_info = BatchAIInferenceService._extract_field_info(field)
                    definitions.append(field_info)
        
        return definitions
    
    @staticmethod
    def _extract_field_info(field: dict) -> Dict[str, Any]:
        """提取字段的完整信息（包含所有约束）"""
        field_info = {
            "fieldCode": field.get("fieldCode"),
            "fieldName": field.get("fieldName"),
            "fieldType": field.get("fieldType"),
            "required": field.get("required", False)
        }
        
        # 添加字段描述
        description = field.get("description", "")
        if description:
            field_info["description"] = description
        
        # 添加枚举选项
        enum_config = field.get("enumConfig", {})
        if isinstance(enum_config, dict):
            options = enum_config.get("options", [])
            if options:
                field_info["enumOptions"] = [
                    {"value": opt.get("value"), "label": opt.get("label")}
                    for opt in options
                ]
        
        # 添加字段约束
        constraints = BatchAIInferenceService._extract_field_constraints(field)
        if constraints:
            field_info["constraints"] = constraints
        
        # 添加字段长度限制
        length_limit = field.get("lengthLimit")
        if length_limit:
            field_info["lengthLimit"] = length_limit
        
        # 添加正则表达式约束
        pattern = field.get("pattern")
        if pattern:
            field_info["pattern"] = pattern
        
        # 添加数据范围约束
        data_range = field.get("dataRange")
        if data_range:
            field_info["dataRange"] = data_range
        
        # 添加默认值
        default_value = field.get("default")
        if default_value:
            field_info["default"] = default_value
        
        return field_info
    
    @staticmethod
    def _extract_field_constraints(field: dict) -> Dict[str, Any]:
        """提取字段约束"""
        constraints = {}
        
        # 必填约束
        if field.get("required", False):
            constraints["required"] = True
        
        # 最小长度
        min_length = field.get("minLength")
        if min_length:
            constraints["minLength"] = min_length
        
        # 最大长度
        max_length = field.get("maxLength")
        if max_length:
            constraints["maxLength"] = max_length
        
        # 最小值（数值类型）
        minimum = field.get("minimum")
        if minimum is not None:
            constraints["minimum"] = minimum
        
        # 最大值（数值类型）
        maximum = field.get("maximum")
        if maximum is not None:
            constraints["maximum"] = maximum
        
        # 精度（数值类型）
        precision = field.get("precision")
        if precision:
            constraints["precision"] = precision
        
        # 是否允许为空
        allow_empty = field.get("allowEmpty")
        if allow_empty is not None:
            constraints["allowEmpty"] = allow_empty
        
        return constraints if constraints else None
    
    @staticmethod
    def _get_entity_constraints(ontology: dict) -> List[Dict[str, Any]]:
        """获取实体级约束"""
        constraints = []
        
        for entity in ontology.get("entities", []):
            # 获取实体级约束配置
            entity_constraints = entity.get("constraints", [])
            if entity_constraints:
                constraints.extend(entity_constraints)
            
            # 获取实体描述
            entity_desc = entity.get("description")
            if entity_desc:
                constraints.append({
                    "entityCode": entity.get("entityCode"),
                    "entityName": entity.get("entityName"),
                    "description": entity_desc
                })
        
        return constraints
    
    @staticmethod
    def _get_field_dependencies(ontology: dict) -> List[Dict[str, Any]]:
        """获取字段联动关系"""
        dependencies = []
        
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                # 获取字段依赖配置
                depends_on = field.get("dependsOn")
                if depends_on:
                    dependency_info = {
                        "fieldCode": field.get("fieldCode"),
                        "fieldName": field.get("fieldName"),
                        "dependsOn": depends_on,
                        "mapping": field.get("mapping")
                    }
                    
                    # 添加值映射关系
                    value_mapping = field.get("valueMapping")
                    if value_mapping:
                        dependency_info["valueMapping"] = value_mapping
                    
                    dependencies.append(dependency_info)
        
        return dependencies
    
    @staticmethod
    def _get_business_rules(ontology: dict) -> List[str]:
        """获取业务规则"""
        rules = []
        
        # 从本体级别获取业务规则
        ontology_rules = ontology.get("businessRules", [])
        rules.extend(ontology_rules)
        
        # 从实体级别获取业务规则
        for entity in ontology.get("entities", []):
            entity_rules = entity.get("businessRules", [])
            rules.extend(entity_rules)
        
        # 从字段级别获取业务规则
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_rules = field.get("businessRules", [])
                rules.extend(field_rules)
        
        return rules
    
    @staticmethod
    def _get_field_definitions(ontology: dict) -> List[Dict[str, Any]]:
        """获取字段定义列表（兼容旧版）"""
        definitions = []
        
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_info = {
                    "fieldCode": field.get("fieldCode"),
                    "fieldName": field.get("fieldName"),
                    "fieldType": field.get("fieldType"),
                    "required": field.get("required", False)
                }
                definitions.append(field_info)
        
        return definitions
    
    @staticmethod
    def _infer_from_ontology(
        field_codes: List[str],
        ontology: dict,
        existing_values: Dict[str, Any]
    ) -> Dict[str, List[str]]:
        """
        基于本体信息进行智能推荐（不依赖用户输入）
        
        Args:
            field_codes: 需要推断的字段列表
            ontology: 本体定义
            existing_values: 已确定的字段值（用于约束检查）
        
        Returns:
            {field_code: [候选值列表]}
        """
        result = {}
        
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_code = field.get("fieldCode")
                if field_code not in field_codes:
                    continue
                
                recommendations = []
                
                # 1. 优先使用默认值
                default_value = field.get("default")
                if default_value:
                    recommendations.append(str(default_value))
                
                # 2. 获取枚举选项
                enum_config = field.get("enumConfig", {})
                if isinstance(enum_config, dict):
                    options = enum_config.get("options", [])
                    if options:
                        for opt in options[:5]:  # 最多5个候选值
                            value = opt.get("value")
                            if value and value not in recommendations:
                                recommendations.append(str(value))
                
                # 3. 处理数据范围约束（数值类型）
                data_range = field.get("dataRange")
                if data_range and isinstance(data_range, dict):
                    min_val = data_range.get("min")
                    max_val = data_range.get("max")
                    if min_val is not None or max_val is not None:
                        # 推荐范围内的典型值
                        if min_val is not None:
                            recommendations.append(str(min_val))
                        if max_val is not None and max_val != min_val:
                            recommendations.append(str(max_val))
                        # 如果有范围，推荐中间值
                        if min_val is not None and max_val is not None:
                            try:
                                mid_val = (float(min_val) + float(max_val)) / 2
                                recommendations.append(str(int(mid_val) if mid_val == int(mid_val) else mid_val))
                            except:
                                pass
                
                # 4. 处理字段依赖（联动关系）
                depends_on = field.get("dependsOn")
                if depends_on and depends_on in existing_values:
                    mapping = field.get("mapping")
                    if mapping and isinstance(mapping, dict):
                        key = str(existing_values[depends_on])
                        mapped_value = mapping.get(key)
                        if mapped_value and mapped_value not in recommendations:
                            recommendations.append(str(mapped_value))
                
                # 5. 值映射关系
                value_mapping = field.get("valueMapping")
                if value_mapping and isinstance(value_mapping, dict):
                    # 获取所有映射值
                    for mapped_val in list(value_mapping.values())[:3]:
                        if mapped_val and str(mapped_val) not in recommendations:
                            recommendations.append(str(mapped_val))
                
                # 去重并限制数量
                unique_recommendations = list(dict.fromkeys(recommendations))[:5]
                
                if unique_recommendations:
                    result[field_code] = unique_recommendations
        
        logger.info(f"[BatchAIInference] 基于本体推荐完成，生成 {len(result)} 个字段的推荐值")
        return result