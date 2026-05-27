"""
资费备案 - 解析输入节点

输入参数：无（从上下文获取 user_input）

输出结果：
- action: 动作类型（ask_user, chat, continue）
- tariff_code: 套餐编码
- extracted_fields: 提取的字段
- missing_fields: 缺失字段
- message: 消息
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node
import re


@register_node
class TariffParseInputNode(WorkflowNode):
    """资费备案解析输入节点"""
    
    name = "tariff.parse_input"
    description = "解析用户输入，提取套餐编码"
    inputs = {}
    outputs = {
        "action": {"type": "str", "description": "动作类型"},
        "tariff_code": {"type": "str", "description": "套餐编码"},
        "extracted_fields": {"type": "dict", "description": "提取的字段"},
        "missing_fields": {"type": "list", "description": "缺失字段"},
        "message": {"type": "str", "description": "消息"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        user_input = context.inputs.get("user_input", "")
        
        # 记录输入信息
        self._log_input(user_input=user_input[:100] + "..." if len(user_input) > 100 else user_input)
        
        # 记录处理逻辑
        processing = "解析用户输入，提取套餐编码和其他字段"
        self._log_processing(processing)
        
        # 提取套餐编码
        tariff_code = self._extract_tariff_code(user_input)
        
        if not tariff_code:
            if any(keyword in user_input for keyword in ["套餐", "资费", "备案"]):
                result = {
                    "success": True,
                    "action": "ask_user",
                    "tariff_code": None,
                    "missing_fields": [{"field": "bossid", "reason": "请提供套餐编码"}],
                    "message": "请提供要备案的套餐编码",
                    "input": {"user_input": user_input},
                    "processing": processing,
                    "output": {"action": "ask_user", "missing_field_count": 1}
                }
                
                self._log_output(success=True, action="ask_user", message="请提供套餐编码")
                return result
            else:
                result = {
                    "success": True,
                    "action": "chat",
                    "tariff_code": None,
                    "message": self._get_chat_response(user_input),
                    "input": {"user_input": user_input},
                    "processing": processing,
                    "output": {"action": "chat"}
                }
                
                self._log_output(success=True, action="chat", message="普通聊天")
                return result
        
        # 从用户输入提取其他字段
        fields = self._generate_default_fields()
        fields = self._extract_from_input(fields, user_input)
        fields["bossid"] = tariff_code
        
        result = {
            "success": True,
            "action": "continue",
            "tariff_code": tariff_code,
            "extracted_fields": fields,
            "message": f"提取到套餐编码: {tariff_code}",
            "input": {"user_input": user_input},
            "processing": processing,
            "output": {"action": "continue", "tariff_code": tariff_code, "field_count": len(fields)}
        }
        
        self._log_output(success=True, action="continue", tariff_code=tariff_code)
        
        return result
    
    def _extract_tariff_code(self, user_input: str) -> str:
        """提取套餐编码"""
        from app.config.tariff_rules_loader import get_tariff_code_patterns
        
        patterns = get_tariff_code_patterns()
        for pattern in patterns:
            match = re.search(pattern, user_input)
            if match:
                code = match.group(0).strip()
                if len(code) >= 3:
                    return code
        return ""
    
    def _get_chat_response(self, user_input: str) -> str:
        """处理普通聊天"""
        greetings = ["你好", "您好", "嗨", "hello", "hi"]
        if any(greeting in user_input.lower() for greeting in greetings):
            return "您好！我是资费备案助手，可以帮您完成资费套餐的备案申请。"
        return "请问有什么可以帮助您的？您可以提供套餐编码进行备案。"
    
    def _generate_default_fields(self) -> Dict[str, Any]:
        """生成默认字段值"""
        from app.config.tariff_rules_loader import load_tariff_rules
        
        rules = load_tariff_rules()
        default_values = rules.get("default_values", {})
        
        fields = {}
        for field_code, default_value in default_values.items():
            fields[field_code] = default_value
        
        # 添加基础字段
        base_fields = ["bossid", "name", "fees", "online_day", "offline_day"]
        for field in base_fields:
            if field not in fields:
                fields[field] = ""
        
        return fields
    
    def _extract_from_input(self, fields: Dict[str, Any], user_input: str) -> Dict[str, Any]:
        """从用户输入提取字段"""
        from app.config.tariff_rules_loader import load_tariff_rules
        
        extraction_rules = load_tariff_rules().get("extraction_rules", {})
        
        for field_code, rule in extraction_rules.items():
            if "keywords" in rule:
                for keyword_item in rule["keywords"]:
                    keyword = keyword_item.get("keyword")
                    value = keyword_item.get("value")
                    if keyword and keyword in user_input:
                        fields[field_code] = value
                        break
            
            if "pattern" in rule:
                pattern = rule["pattern"]
                match = re.search(pattern, user_input)
                if match:
                    if field_code == "online_day" and "format" in rule:
                        try:
                            year, month, day = match.groups()
                            fields[field_code] = rule["format"].format(int(year), int(month), int(day))
                        except (ValueError, IndexError):
                            pass
                    else:
                        fields[field_code] = match.group(1)
        
        return fields