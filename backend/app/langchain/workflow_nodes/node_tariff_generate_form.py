"""
资费备案 - 表单生成节点

输入参数：无（从上下文获取数据）

输出结果：
- formCode: 表单编码
- extractedFields: 提取的字段
- message: 消息
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class TariffGenerateFormNode(WorkflowNode):
    """资费备案表单生成节点"""
    
    name = "tariff.generate_form"
    description = "生成资费备案表单数据"
    _legacy = True
    inputs = {}
    outputs = {
        "formCode": {"type": "str", "description": "表单编码"},
        "extractedFields": {"type": "dict", "description": "提取的字段"},
        "message": {"type": "str", "description": "消息"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        # 记录处理逻辑
        processing = "生成资费备案表单数据"
        self._log_processing(processing)
        
        # 从上下文获取数据
        query_result = context.step_results.get("query_tariff", {})
        extracted_fields = context.outputs.get("extracted_fields", {})
        
        if query_result.get("success"):
            # 使用工具返回的数据填充表单
            fields = self._map_tool_result(query_result)
            # 合并提取的字段
            fields.update(extracted_fields)
        else:
            # 使用默认值
            fields = self._generate_default_fields()
            fields.update(extracted_fields)
        
        result = {
            "success": True,
            "formCode": "tariff_filing_publicity",
            "extractedFields": fields,
            "message": query_result.get("success") and "套餐信息查询成功" or "使用默认表单",
            "input": {},
            "processing": processing,
            "output": {
                "formCode": "tariff_filing_publicity",
                "field_count": len(fields),
                "has_tool_data": query_result.get("success", False)
            }
        }
        
        self._log_output(
            success=True,
            formCode="tariff_filing_publicity",
            field_count=len(fields),
            has_tool_data=query_result.get("success", False)
        )
        
        return result
    
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
    
    def _map_tool_result(self, tool_result: Dict[str, Any]) -> Dict[str, Any]:
        """映射工具返回结果"""
        from app.config.tariff_rules_loader import load_tariff_rules
        
        fields = self._generate_default_fields()
        field_mapping = load_tariff_rules().get("field_mapping", {})
        
        for target_field, source_fields in field_mapping.items():
            for source_field in source_fields:
                if source_field in tool_result and tool_result[source_field]:
                    fields[target_field] = tool_result[source_field]
                    break
        
        return fields