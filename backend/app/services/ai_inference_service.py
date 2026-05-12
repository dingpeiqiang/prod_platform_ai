"""
AI Field Inference Service - 基于本体的智能字段推断服务

职责：
- 接收表单编码和用户输入
- 加载本体定义
- 调用 LLM 为所有字段生成推断值
- 返回完整的 extractedFields（不允许空值）
"""

import logging
from typing import Dict, Any, Optional
from app.services.llm_service import llm_service
from app.core.config_loader import config_loader

logger = logging.getLogger("ai_inference")


class AIInferenceService:
    """AI 字段推断服务"""
    
    def __init__(self):
        # llm_service 是全局单例，直接使用
        pass
    
    def infer_fields(
        self,
        form_code: str,
        user_input: str,
        context: Optional[Dict[str, Any]] = None
    ) -> Dict[str, Any]:
        """
        基于本体定义为所有字段生成推断值
        
        Args:
            form_code: 表单编码
            user_input: 用户输入
            context: 上下文信息（可选）
            
        Returns:
            dict: {
                "extractedFields": {...},  # 字段推断值
                "reasoning": "..."          # LLM 推理过程（可选）
            }
        """
        try:
            # 1. 加载本体定义
            ontology = config_loader.get_ontology(form_code)
            if not ontology:
                logger.error(f"[AIInference] 未找到本体定义: {form_code}")
                return {}
            
            # 2. 构建推断提示词
            prompt = self._build_inference_prompt(ontology, user_input, context)
            
            # 3. 调用 LLM 进行推断（带推理过程）
            logger.info(f"[AIInference] 开始推断 form_code={form_code}, 字段数={self._count_fields(ontology)}")
            
            # 使用 llm_service 的同步调用方法（带推理）
            content, reasoning = llm_service._call_llm_sync_with_reasoning(
                prompt=prompt,
                system_prompt="你是专业的表单字段推断助手，基于本体定义为用户输入生成合理的字段值。"
            )
            
            if not content:
                logger.error(f"[AIInference] LLM 返回为空")
                return {"extractedFields": {}, "reasoning": ""}
            
            # 4. 解析推断结果
            extracted_fields = self._parse_inference_result(content, ontology)
            
            # 5. 验证结果（确保没有空值）
            self._validate_no_empty_values(extracted_fields, ontology, form_code)
            
            logger.info(f"[AIInference] 推断完成 form_code={form_code}, 字段数={len(extracted_fields)}")
            return {
                "extractedFields": extracted_fields,
                "reasoning": reasoning or ""
            }
            
        except Exception as e:
            logger.exception(f"[AIInference] 推断失败: {e}")
            return {}
    
    def _build_inference_prompt(
        self,
        ontology: Dict[str, Any],
        user_input: str,
        context: Optional[Dict[str, Any]] = None
    ) -> str:
        """构建 AI 推断提示词"""
        
        form_code = ontology.get("formCode", "")
        form_name = ontology.get("formName", "")
        
        # 提取所有字段定义（完整本体信息）
        fields_info = []
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_info = {
                    "fieldCode": field.get("fieldCode"),
                    "fieldName": field.get("fieldName"),
                    "fieldType": field.get("fieldType"),
                    "required": field.get("required", False),
                    "ruleDescription": field.get("ruleDescription", ""),
                }
                
                # 添加枚举选项（包含 value 和 label）
                if field.get("enumConfig"):
                    enum_type = field["enumConfig"].get("type")
                    if enum_type == "static":
                        options = field["enumConfig"].get("options", [])
                        field_info["options"] = [f"{opt.get('label')}[{opt.get('value')}]" for opt in options]
                
                fields_info.append(field_info)
        
        # 构建提示词
        prompt = f"""你是专业的表单字段推断助手。

## 任务
基于以下本体定义和用户输入，为所有字段生成**真实合理的业务值**，严禁返回"请输入XXX"等占位符！

## 本体信息
- 表单编码：{form_code}
- 表单名称：{form_name}

## 字段定义（共 {len(fields_info)} 个字段）
{self._format_fields(fields_info)}

## 用户输入
{user_input}

## 推断原则（极其重要）

⚠️ **严禁返回空值**：必须为每个字段生成非空的合理值！
⚠️ **严禁返回占位符**：禁止使用"请输入XXX"、"请填写XXX"等占位符文字！

1. **优先使用用户明确提供的值**
2. **枚举字段处理**（select/enum/radio类型）：
   - 必须返回选项中的 **value 值**（即方括号内的内容，如 JT1、A、1）
   - 不要返回标签名称（如"中国电信集团"、"新增"）
   - 如果没有明确指示，选择第一个选项或最常用的选项
3. **基于业务常识推断合理的真实值**：
   - 文本字段（input/textarea）：根据字段名称推断合理的业务内容
     - name/名称字段：生成合理的中文名称（如"畅享5G套餐"、"企业专线服务"）
     - desc/描述字段：生成简短的描述内容
     - other_content/服务内容：生成合理的服务描述
     - iptv/bandwidth/rights：根据业务常识生成合理值
   - 数值字段：设置为 0 或合理的默认值
   - 日期字段：设置为当前日期或合理的默认日期
4. **基于上下文推断**（如果提供了 context）
5. **使用行业通用默认值**：
   - 编码字段：生成符合格式的示例编码（如 P000001）
   - valid_period/有效期限："长期有效"
   - applicable_area/适用地区："全国"
   - channel/销售渠道："线上渠道"
   - duration/在网要求："无"
   - unsubscribe/退订方式："发送短信退订"
   - responsibility/违约责任：根据业务常识填写

## 输出格式

严格以 JSON 格式返回，不要有其他文本：

```json
{{
  "extractedFields": {{
    "fieldCode1": "推断值1",
    "fieldCode2": "推断值2",
    ...
  }}
}}
```

## 枚举字段输出示例

假设字段定义包含：reporter (备案主体) - 类型: select - 选项: 中国电信集团[JT1], 中国移动集团[YD1]

正确输出（使用 value）：
```json
{{
  "extractedFields": {{
    "reporter": "JT1",
    "action_type": "A",
    "type1": "1"
  }}
}}
```

错误输出（不要这样）：
```json
{{
  "extractedFields": {{
    "reporter": "中国电信集团[JT1]",  // ❌ 错误：包含了标签名称
    "action_type": "新增",           // ❌ 错误：返回了标签而非value
    "type1": "公众[1]",              // ❌ 错误：包含了标签名称
    "name": "请输入名称"             // ❌ 错误：使用了占位符
  }}
}}
```

## 完整示例

用户输入："资费备案申请"

输出：
```json
{{
  "extractedFields": {{
    "bossid": "P000001",
    "action_type": "A",
    "reporter": "JT1",
    "name": "畅享5G套餐",
    "fees": 58,
    "fees_unit": "元/月",
    "valid_period": "长期有效",
    "applicable_area": "全国",
    "channel": "线上渠道",
    "duration": "无",
    "unsubscribe": "发送短信退订",
    "responsibility": "用户违约需支付违约金",
    "iptv": "包含",
    "bandwidth": "100M",
    "rights": "包含来电显示、彩铃",
    "other_content": "本套餐包含语音、流量、短信等服务"
  }}
}}
```

现在请为表单 {form_code} 生成所有字段的推断值：
"""
        return prompt
    
    def _format_fields(self, fields_info: list) -> str:
        """格式化字段信息（包含完整本体定义）"""
        lines = []
        for i, field in enumerate(fields_info, 1):
            line = f"{i}. {field['fieldCode']} ({field['fieldName']})"
            line += f" - 类型: {field['fieldType']}"
            if field.get('required'):
                line += " - **必填**"
            if field.get('options'):
                line += f" - 选项: {', '.join(field['options'])}"
            if field.get('ruleDescription'):
                line += f" - 规则: {field['ruleDescription']}"
            lines.append(line)
        return "\n".join(lines)
    
    def _count_fields(self, ontology: Dict[str, Any]) -> int:
        """统计字段数量"""
        count = 0
        for entity in ontology.get("entities", []):
            count += len(entity.get("fields", []))
        return count
    
    def _parse_inference_result(self, response: str, ontology: Dict[str, Any]) -> Dict[str, Any]:
        """解析 LLM 返回的推断结果"""
        import json
        import re
        
        # 清理 Markdown 代码块标记
        cleaned = re.sub(r'^```json\s*', '', response.strip())
        cleaned = re.sub(r'^```\s*', '', cleaned)
        cleaned = re.sub(r'\s*```$', '', cleaned)
        cleaned = cleaned.strip()
        
        # 解析 JSON
        result = json.loads(cleaned)
        extracted_fields = result.get("extractedFields", {})
        
        # 处理枚举字段：提取方括号中的实际值（如 "中国电信集团[JT1]" -> "JT1"）
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                field_code = field.get("fieldCode")
                field_type = field.get("fieldType")
                
                # 只处理枚举类型字段
                if field_type in ["select", "enum", "radio"] and field_code in extracted_fields:
                    value = extracted_fields[field_code]
                    if isinstance(value, str):
                        # 尝试提取方括号中的值
                        match = re.search(r'\[([^\]]+)\]', value)
                        if match:
                            extracted_fields[field_code] = match.group(1)
        
        # 确保所有字段都存在
        all_field_codes = set()
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                all_field_codes.add(field.get("fieldCode"))
        
        # 补充缺失的字段
        for field_code in all_field_codes:
            if field_code not in extracted_fields:
                logger.warning(f"[AIInference] LLM 遗漏字段 {field_code}，补充默认值")
                extracted_fields[field_code] = self._get_default_value(field_code, ontology)
        
        return extracted_fields
    
    def _get_default_value(self, field_code: str, ontology: Dict[str, Any]) -> str:
        """获取字段的默认值"""
        # 查找字段定义
        for entity in ontology.get("entities", []):
            for field in entity.get("fields", []):
                if field.get("fieldCode") == field_code:
                    field_type = field.get("fieldType", "string")
                    
                    # 根据类型返回默认值
                    if field_type in ["select", "enum", "radio"]:
                        # 枚举字段：返回第一个选项
                        if field.get("enumConfig"):
                            options = field["enumConfig"].get("options", [])
                            if options:
                                return options[0].get("value", "")
                    elif field_type in ["number", "integer", "float"]:
                        return "0"
                    elif field_type == "boolean":
                        return "false"
                    elif field_type == "date":
                        from datetime import datetime
                        return datetime.now().strftime("%Y-%m-%d")
                    else:
                        return f"请输入{field.get('fieldName', '')}"
        
        return ""
    
    def _validate_no_empty_values(
        self,
        extracted_fields: Dict[str, Any],
        ontology: Dict[str, Any],
        form_code: str
    ):
        """验证没有空值"""
        empty_fields = []
        for field_code, value in extracted_fields.items():
            if not value or (isinstance(value, str) and not value.strip()):
                empty_fields.append(field_code)
        
        if empty_fields:
            logger.error(
                f"[AIInference] ⚠️ 发现空值字段 form_code={form_code}: {empty_fields[:5]}"
            )
            # 补充默认值
            for field_code in empty_fields:
                extracted_fields[field_code] = self._get_default_value(field_code, ontology)


# 全局实例
_ai_inference_service = None


def get_ai_inference_service() -> AIInferenceService:
    """获取 AI 推断服务单例"""
    global _ai_inference_service
    if _ai_inference_service is None:
        _ai_inference_service = AIInferenceService()
    return _ai_inference_service
