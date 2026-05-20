"""
资费备案业务处理器 - 完全配置驱动
所有业务规则从配置文件动态读取，无硬编码
"""
from typing import Optional, Dict, Any, List
import logging
import json
import re

from app.config.tariff_rules_loader import (
    get_default_value,
    get_enum_values,
    get_validation_rule,
    get_field_mapping,
    get_extraction_rule,
    get_tariff_code_patterns,
    get_all_fields,
    load_tariff_rules
)
from app.mcp_tools.tariff_tools import query_tariff_by_code
from app.core.config_loader import config_loader

logger = logging.getLogger("tariff_processor")


class TariffProcessor:
    """
    资费备案业务处理器
    
    特点：
    1. 完全配置驱动，无硬编码业务规则
    2. 支持动态加载配置文件
    3. 提供统一的业务流程接口
    """
    
    FORM_CODE = "tariff_filing_publicity"
    
    def __init__(self):
        self.session_state = {}
        self._load_rules()
    
    def _load_rules(self):
        """加载业务规则（可动态刷新）"""
        self.rules = load_tariff_rules()
    
    def refresh_rules(self):
        """刷新业务规则（重新读取配置文件）"""
        global _rules_cache
        _rules_cache = None
        self._load_rules()
        logger.info("[TariffProcessor] 业务规则已刷新")
    
    def _extract_tariff_code(self, user_input: str) -> Optional[str]:
        """从用户输入中提取套餐编码（基于配置的模式）"""
        patterns = get_tariff_code_patterns()
        
        for pattern in patterns:
            match = re.search(pattern, user_input)
            if match:
                code = match.group(0).strip()
                if len(code) >= 3:
                    return code
        
        return None
    
    def _generate_default_fields(self) -> Dict[str, Any]:
        """生成默认字段值（从配置读取）"""
        # 获取所有已知字段
        all_fields = get_all_fields()
        
        # 构建默认字段字典
        fields = {}
        for field in all_fields:
            default_value = get_default_value(field)
            if default_value is not None:
                fields[field] = default_value
            else:
                fields[field] = ""
        
        # 添加一些基础字段（即使配置中没有）
        base_fields = ["bossid", "name", "fees", "online_day", "offline_day"]
        for field in base_fields:
            if field not in fields:
                fields[field] = ""
        
        return fields
    
    def _extract_from_input(self, fields: Dict[str, Any], user_input: str) -> Dict[str, Any]:
        """根据配置的提取规则从用户输入中提取字段"""
        extraction_rules = self.rules.get("extraction_rules", {})
        
        for field_code, rule in extraction_rules.items():
            # 关键词匹配（用于 action_type 等枚举字段）
            if "keywords" in rule:
                for keyword_item in rule["keywords"]:
                    keyword = keyword_item.get("keyword")
                    value = keyword_item.get("value")
                    if keyword and keyword in user_input:
                        fields[field_code] = value
                        break
            
            # 正则表达式提取
            if "pattern" in rule:
                pattern = rule["pattern"]
                match = re.search(pattern, user_input)
                if match:
                    # 处理日期格式
                    if field_code == "online_day" and "format" in rule:
                        try:
                            year, month, day = match.groups()
                            fields[field_code] = rule["format"].format(int(year), int(month), int(day))
                        except (ValueError, IndexError):
                            pass
                    else:
                        fields[field_code] = match.group(1)
        
        return fields
    
    def _validate_fields(self, fields: Dict[str, Any]) -> List[Dict[str, Any]]:
        """根据配置的校验规则验证字段（完全配置驱动）"""
        results = []
        
        # 获取所有校验规则
        validation_rules = self.rules.get("validation_rules", {})
        
        for field_code, rule in validation_rules.items():
            value = fields.get(field_code, "")
            field_name = self._get_field_display_name(field_code)
            
            # 执行校验
            result, reason, suggestion = self._validate_field(field_code, value, rule)
            
            results.append({
                "field": field_code,
                "fieldName": field_name,
                "value": value,
                "result": result,
                "reason": reason,
                "suggestion": suggestion
            })
        
        return results
    
    def _get_field_display_name(self, field_code: str) -> str:
        """获取字段的显示名称"""
        # 优先从本体定义获取
        ontology = config_loader.get_ontology(self.FORM_CODE)
        if ontology:
            for entity in ontology.get("entities", []):
                for field in entity.get("fields", []):
                    if field.get("fieldCode") == field_code:
                        return field.get("fieldName", field_code)
        
        # 字段名映射
        field_name_mapping = {
            "bossid": "省内套餐编码",
            "seq_no": "序列号",
            "reporter": "备案主体",
            "action_type": "操作类型",
            "type1": "一级分类",
            "type2": "二级分类",
            "name": "资费名称",
            "tariff_attr": "资费属性",
            "applicable_people": "适用范围",
            "applicable_area": "适用地区",
            "valid_period": "有效期限",
            "channel": "销售渠道",
            "duration": "在网要求",
            "unsubscribe": "退订方式",
            "responsibility": "违约责任",
            "online_day": "上线日期",
            "offline_day": "下线日期",
            "fees": "资费标准",
            "fees_unit": "资费单位"
        }
        
        return field_name_mapping.get(field_code, field_code)
    
    def _validate_field(self, field_code: str, value: Any, rule: Dict[str, Any]) -> tuple:
        """根据单个规则校验字段"""
        required = rule.get("required", False)
        
        # 检查必填
        if required and not value:
            return ("error", rule.get("empty_error", "此字段不能为空"), rule.get("empty_suggestion", ""))
        
        # 非必填字段为空时直接通过
        if not value and not required:
            # 检查是否有警告配置
            if rule.get("empty_warning"):
                return ("warning", rule["empty_warning"], "")
            return ("pass", "未填写", "")
        
        # 正则表达式校验
        if "pattern" in rule:
            pattern = rule["pattern"]
            if not re.match(pattern, str(value)):
                return ("error", rule.get("pattern_error", "格式不正确"), "")
        
        # 枚举值校验
        if "enum" in rule:
            enum_name = rule["enum"]
            enum_values = get_enum_values(enum_name)
            valid_values = [item.get("value") for item in enum_values]
            if str(value) not in valid_values:
                labels = [f"{item.get('value')}({item.get('label')})" for item in enum_values]
                return ("error", rule.get("enum_error", "值无效"), f"有效值: {', '.join(labels)}")
        
        # 长度校验
        if "max_length" in rule:
            max_len = rule["max_length"]
            if len(str(value)) > max_len:
                return ("error", rule.get("length_error", f"长度超过限制"), f"最大长度{max_len}字符")
        
        # 类型校验
        if "type" in rule:
            field_type = rule["type"]
            if field_type == "number":
                try:
                    float(value)
                except (ValueError, TypeError):
                    return ("error", rule.get("type_error", "类型不正确"), "")
        
        return ("pass", "校验通过", "")
    
    def _map_tool_result(self, tool_result: Dict[str, Any]) -> Dict[str, Any]:
        """根据配置的字段映射关系处理工具返回结果"""
        fields = self._generate_default_fields()
        
        # 使用字段映射配置
        field_mapping = self.rules.get("field_mapping", {})
        
        for target_field, source_fields in field_mapping.items():
            for source_field in source_fields:
                if source_field in tool_result and tool_result[source_field]:
                    fields[target_field] = tool_result[source_field]
                    break
        
        return fields
    
    def _generate_validation_message(self, results: List[Dict[str, Any]]) -> str:
        """生成校验结果消息"""
        passed = sum(1 for r in results if r["result"] == "pass")
        warnings = sum(1 for r in results if r["result"] == "warning")
        errors = sum(1 for r in results if r["result"] == "error")
        
        if errors == 0 and warnings == 0:
            return f"表单校验完成，全部{passed}个字段通过！"
        elif errors > 0:
            return f"表单校验完成，{passed}个字段通过，{warnings}个警告，{errors}个错误，请修正后提交。"
        else:
            return f"表单校验完成，{passed}个字段通过，{warnings}个警告，请确认后提交。"
    
    async def process(self, user_input: str) -> Dict[str, Any]:
        """处理用户输入，执行完整业务流程"""
        logger.info(f"[TariffProcessor] 处理用户输入: {user_input[:100]}")
        
        # 步骤1: 提取套餐编码
        tariff_code = self._extract_tariff_code(user_input)
        logger.info(f"[TariffProcessor] 提取到套餐编码: {tariff_code}")
        
        # 步骤2: 如果没有套餐编码，询问用户
        if not tariff_code:
            if any(keyword in user_input for keyword in ["套餐", "资费", "备案"]):
                return self._ask_for_tariff_code()
            else:
                return self._handle_general_query(user_input)
        
        # 步骤3: 查询套餐信息
        try:
            tariff_result = await self._query_tariff_info(tariff_code)
            
            if not tariff_result.get("success"):
                # 查询失败，生成空表单让用户手动填写
                return self._generate_empty_form(tariff_code, user_input)
            
            # 步骤4: 构建表单数据
            return self._build_form_data(tariff_result)
        
        except Exception as e:
            logger.error(f"[TariffProcessor] 处理失败: {e}")
            return {
                "action": "error",
                "message": f"处理失败，请稍后重试: {e}"
            }
    
    async def _query_tariff_info(self, tariff_code: str) -> Dict[str, Any]:
        """调用工具查询套餐信息"""
        logger.info(f"[TariffProcessor] 调用工具查询套餐: {tariff_code}")
        try:
            result = query_tariff_by_code(tariff_code)
            return result
        except Exception as e:
            logger.error(f"[TariffProcessor] 查询失败: {e}")
            return {"success": False, "error": str(e)}
    
    def _ask_for_tariff_code(self) -> Dict[str, Any]:
        """询问用户提供套餐编码"""
        return {
            "action": "ask_user",
            "missing_fields": [
                {
                    "field": "bossid",
                    "reason": "请提供要备案的套餐编码（字母开头+数字组合）"
                }
            ],
            "message": "您好，欢迎进行资费备案申请！为了帮您查询套餐信息，请提供您要备案的套餐编码（如 P000111、P123456789）"
        }
    
    def _handle_general_query(self, user_input: str) -> Dict[str, Any]:
        """处理通用查询"""
        greetings = ["你好", "您好", "嗨", "hello", "hi"]
        if any(greeting in user_input.lower() for greeting in greetings):
            return {
                "action": "chat",
                "message": "您好！我是资费备案助手，可以帮您完成资费套餐的备案申请。请告诉我您要备案的套餐编码，或者描述您的需求。"
            }
        
        return {
            "action": "chat",
            "message": "请问有什么可以帮助您的？\n\n您可以：\n1. 提供套餐编码进行备案（如：备案套餐 P000111）\n2. 描述您的备案需求"
        }
    
    def _generate_empty_form(self, tariff_code: str, user_input: str) -> Dict[str, Any]:
        """生成空表单（查询失败时）"""
        fields = self._generate_default_fields()
        fields["bossid"] = tariff_code
        
        fields = self._extract_from_input(fields, user_input)
        
        return {
            "action": "generate_form",
            "formCode": self.FORM_CODE,
            "extractedFields": fields,
            "message": f"套餐编码 {tariff_code} 查询失败，请手动填写表单信息。已为您预填部分默认值。"
        }
    
    def _build_form_data(self, tariff_result: Dict[str, Any]) -> Dict[str, Any]:
        """构建表单数据（完全基于配置）"""
        # 使用配置的字段映射处理工具结果
        fields = self._map_tool_result(tariff_result)
        
        # 生成校验结果
        validation_results = self._validate_fields(fields)
        
        return {
            "action": "validate_form",
            "formCode": self.FORM_CODE,
            "extractedFields": fields,
            "validationResults": validation_results,
            "message": self._generate_validation_message(validation_results)
        }
    
    def validate_form_data(self, form_data: Dict[str, Any]) -> Dict[str, Any]:
        """验证表单数据（直接调用）"""
        validation_results = self._validate_fields(form_data)
        
        passed = sum(1 for r in validation_results if r["result"] == "pass")
        warnings = sum(1 for r in validation_results if r["result"] == "warning")
        errors = sum(1 for r in validation_results if r["result"] == "error")
        
        return {
            "success": True,
            "validationResults": validation_results,
            "summary": {
                "total": len(validation_results),
                "passed": passed,
                "warnings": warnings,
                "errors": errors
            },
            "message": self._generate_validation_message(validation_results)
        }
    
    def get_form_schema(self) -> Dict[str, Any]:
        """获取表单结构定义"""
        ontology = config_loader.get_ontology(self.FORM_CODE)
        if not ontology:
            return {"success": False, "error": "未找到表单本体定义"}
        
        return {
            "success": True,
            "formCode": ontology.get("formCode"),
            "formName": ontology.get("formName"),
            "description": ontology.get("description"),
            "defaultValues": self._generate_default_fields(),
            "enums": self.rules.get("enums", {}),
            "validationRules": self.rules.get("validation_rules", {})
        }
