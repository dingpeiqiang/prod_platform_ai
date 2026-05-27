"""
解析输出节点

输入参数：
- content: 要解析的内容（必填）
- pattern: 正则表达式（可选）

输出结果：
- parsed: 解析结果
- groups: 捕获组
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node
import re


@register_node
class ParseOutputNode(WorkflowNode):
    """解析输出节点"""
    
    name = "workflow.parse_output"
    description = "使用正则表达式解析内容"
    inputs = {
        "content": {"type": "str", "required": True, "description": "要解析的内容"},
        "pattern": {"type": "str", "required": False, "description": "正则表达式"}
    }
    outputs = {
        "parsed": {"type": "str", "description": "解析结果"},
        "groups": {"type": "tuple", "description": "捕获组"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        content = kwargs.get("content", "")
        pattern = kwargs.get("pattern", "")
        
        # 记录输入信息
        self._log_input(content=content[:100] + "..." if len(content) > 100 else content, pattern=pattern)
        
        # 记录处理逻辑
        processing = f"使用正则表达式 '{pattern}' 解析内容"
        self._log_processing(processing)
        
        if pattern and content:
            try:
                match = re.search(pattern, content)
                if match:
                    output = {
                        "success": True,
                        "parsed": match.group(0),
                        "groups": match.groups(),
                        "input": {"content": content, "pattern": pattern},
                        "processing": processing,
                        "output": {"parsed": match.group(0), "groups": match.groups()}
                    }
                    
                    # 记录输出信息
                    self._log_output(success=True, parsed=match.group(0), groups=match.groups())
                    
                    return output
                else:
                    self._log_output(success=False, error="Pattern not found")
                    
                    return {
                        "success": False,
                        "error": "Pattern not found",
                        "input": {"content": content, "pattern": pattern},
                        "processing": processing,
                        "output": {"error": "Pattern not found"}
                    }
            except Exception as e:
                error_msg = str(e)
                self._log_output(success=False, error=error_msg)
                
                return {
                    "success": False,
                    "error": error_msg,
                    "input": {"content": content, "pattern": pattern},
                    "processing": processing,
                    "output": {"error": error_msg}
                }
        
        output = {
            "success": True,
            "parsed": content,
            "input": {"content": content, "pattern": pattern},
            "processing": processing,
            "output": {"parsed": content}
        }
        
        # 记录输出信息
        self._log_output(success=True, parsed=content[:100] + "..." if len(content) > 100 else content)
        
        return output