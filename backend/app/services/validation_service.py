from typing import Dict, Any, List, Optional, Callable
import re
import logging
from datetime import datetime
from app.core.config_loader import config_loader

logger = logging.getLogger("validation_service")


class ValidationRule:
    def __init__(self, rule_type: str, rule_value: Any, message: str):
        self.rule_type = rule_type
        self.rule_value = rule_value
        self.message = message


class ValidationResult:
    def __init__(self, valid: bool, errors: List[str] = None):
        self.valid = valid
        self.errors = errors or []


class ValidationEngine:
    _custom_rules: Dict[str, Callable] = {}
    
    @classmethod
    def register_custom_rule(cls, rule_type: str, validator: Callable):
        cls._custom_rules[rule_type] = validator
    
    @classmethod
    def validate_field(cls, field_value: Any, rules: List[Dict]) -> ValidationResult:
        errors = []
        
        for rule in rules:
            rule_type = rule.get("rule_type")
            rule_value = rule.get("rule_value")
            message = rule.get("message")
            
            if not rule_type:
                continue
            
            is_valid = cls._validate_single_rule(field_value, rule_type, rule_value)
            if not is_valid:
                errors.append(message)
        
        return ValidationResult(valid=len(errors) == 0, errors=errors)
    
    @classmethod
    def validate_form(cls, form_data: Dict[str, Any], fields: List[Dict]) -> ValidationResult:
        all_errors = []
        
        for field in fields:
            field_code = field.get("fieldCode")
            field_name = field.get("fieldName", field_code)
            required = field.get("required", False)
            rules = field.get("rules", [])
            
            field_value = form_data.get(field_code)
            
            if required and (field_value is None or field_value == ""):
                all_errors.append(f"{field_name} 不能为空")
                continue
            
            if field_value is not None and field_value != "":
                field_result = cls.validate_field(field_value, rules)
                if not field_result.valid:
                    all_errors.extend(field_result.errors)
        
        return ValidationResult(valid=len(all_errors) == 0, errors=all_errors)
    
    @classmethod
    def _validate_single_rule(cls, field_value: Any, rule_type: str, rule_value: Any) -> bool:
        if rule_type in cls._custom_rules:
            return cls._custom_rules[rule_type](field_value, rule_value)
        
        validators = {
            "minLength": cls._validate_min_length,
            "maxLength": cls._validate_max_length,
            "min": cls._validate_min,
            "max": cls._validate_max,
            "minimum": cls._validate_min,
            "maximum": cls._validate_max,
            "pattern": cls._validate_pattern,
            "email": cls._validate_email,
            "phone": cls._validate_phone,
            "idCard": cls._validate_id_card,
            "url": cls._validate_url,
            "enum": cls._validate_enum,
            "dateMin": cls._validate_date_min,
            "dateMax": cls._validate_date_max,
            "custom": cls._validate_custom_script,
        }
        
        validator = validators.get(rule_type)
        if validator:
            return validator(field_value, rule_value)
        
        return True
    
    @staticmethod
    def _validate_min_length(value: Any, min_len: int) -> bool:
        if value is None:
            return True
        return len(str(value)) >= min_len
    
    @staticmethod
    def _validate_max_length(value: Any, max_len: int) -> bool:
        if value is None:
            return True
        return len(str(value)) <= max_len
    
    @staticmethod
    def _validate_min(value: Any, min_val: float) -> bool:
        if value is None or value == "":
            return True
        try:
            return float(value) >= min_val
        except (ValueError, TypeError):
            return False
    
    @staticmethod
    def _validate_max(value: Any, max_val: float) -> bool:
        if value is None or value == "":
            return True
        try:
            return float(value) <= max_val
        except (ValueError, TypeError):
            return False
    
    @staticmethod
    def _validate_pattern(value: Any, pattern: str) -> bool:
        if value is None or value == "":
            return True
        try:
            return bool(re.match(pattern, str(value)))
        except re.error:
            return False
    
    @staticmethod
    def _validate_email(value: Any, _: Any = None) -> bool:
        if value is None or value == "":
            return True
        email_pattern = r'^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
        return bool(re.match(email_pattern, str(value)))
    
    @staticmethod
    def _validate_phone(value: Any, _: Any = None) -> bool:
        if value is None or value == "":
            return True
        phone_pattern = r'^1[3-9]\d{9}$'
        return bool(re.match(phone_pattern, str(value)))
    
    @staticmethod
    def _validate_id_card(value: Any, _: Any = None) -> bool:
        if value is None or value == "":
            return True
        id_card_pattern = r'^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]$'
        return bool(re.match(id_card_pattern, str(value)))
    
    @staticmethod
    def _validate_url(value: Any, _: Any = None) -> bool:
        if value is None or value == "":
            return True
        url_pattern = r'^https?://[^\s/$.?#].[^\s]*$'
        return bool(re.match(url_pattern, str(value)))
    
    @staticmethod
    def _validate_enum(value: Any, enum_values: List) -> bool:
        if value is None or value == "":
            return True
        return str(value) in [str(v) for v in enum_values]
    
    @staticmethod
    def _validate_date_min(value: Any, min_date: str) -> bool:
        if value is None or value == "":
            return True
        try:
            date_val = datetime.strptime(str(value), "%Y-%m-%d")
            min_dt = datetime.strptime(min_date, "%Y-%m-%d")
            return date_val >= min_dt
        except ValueError:
            return False
    
    @staticmethod
    def _validate_date_max(value: Any, max_date: str) -> bool:
        if value is None or value == "":
            return True
        try:
            date_val = datetime.strptime(str(value), "%Y-%m-%d")
            max_dt = datetime.strptime(max_date, "%Y-%m-%d")
            return date_val <= max_dt
        except ValueError:
            return False
    
    @staticmethod
    def _validate_custom_script(value: Any, script_config: Dict) -> bool:
        if value is None or value == "":
            return True
        
        script_type = script_config.get("type", "simple")
        
        if script_type == "simple":
            condition = script_config.get("condition", "")
            try:
                return eval(condition, {}, {"value": value})
            except Exception as e:
                logger.warning("[ValidationService] custom script eval 失败 condition=%s error=%s",
                               condition, e)
                return False
        
        elif script_type == "lambda":
            code = script_config.get("code", "")
            try:
                validator = eval(code)
                return validator(value)
            except Exception as e:
                logger.warning("[ValidationService] custom script lambda 失败 code=%s error=%s",
                               code, e)
                return False
        
        return True


validation_engine = ValidationEngine()
