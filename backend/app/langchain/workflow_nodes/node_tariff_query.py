"""
资费备案 - 查询套餐节点

输入参数：无（从上下文获取 tariff_code）

输出结果：
- success: 是否成功
- 工具返回的其他字段
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class TariffQueryNode(WorkflowNode):
    """资费备案查询套餐节点"""
    
    name = "tariff.query_tariff"
    description = "查询套餐信息"
    _legacy = True
    inputs = {}
    outputs = {
        "success": {"type": "bool", "description": "是否成功"},
        "tariff_code": {"type": "str", "description": "套餐编码"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        tariff_code = context.outputs.get("tariff_code")
        
        # 记录输入信息
        self._log_input(tariff_code=tariff_code)
        
        # 记录处理逻辑
        processing = f"查询套餐信息，套餐编码: {tariff_code}"
        self._log_processing(processing)
        
        if not tariff_code:
            result = {
                "success": False,
                "error": "未提供套餐编码",
                "input": {},
                "processing": processing,
                "output": {"error": "未提供套餐编码"}
            }
            
            self._log_output(success=False, error="未提供套餐编码")
            return result
        
        try:
            from app.mcp_tools.tariff_tools import query_tariff_by_code
            
            result = query_tariff_by_code(tariff_code)
            
            # 添加标准字段
            result["input"] = {"tariff_code": tariff_code}
            result["processing"] = processing
            
            if result.get("success"):
                result["output"] = {"tariff_code": tariff_code, "has_data": True}
                self._log_output(success=True, tariff_code=tariff_code)
            else:
                result["output"] = {"tariff_code": tariff_code, "has_data": False}
                self._log_output(success=False, error=result.get("error"))
            
            return result
            
        except Exception as e:
            error_msg = str(e)
            result = {
                "success": False,
                "error": error_msg,
                "input": {"tariff_code": tariff_code},
                "processing": processing,
                "output": {"error": error_msg}
            }
            
            self._log_output(success=False, error=error_msg)
            return result