"""
知识库查询节点

输入参数：
- query_text: 查询词（必填）
- knowledge_base: 知识库编码（必填）
- query_mode: 查询模式（可选，默认 similarity）

输出结果：
- results: 查询结果列表
- query: 查询词
- count: 结果数量
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node


@register_node
class QueryKnowledgeNode(WorkflowNode):
    """知识库查询节点"""
    
    name = "workflow.query_knowledge"
    description = "查询知识库获取相关知识"
    inputs = {
        "query_text": {"type": "str", "required": True, "description": "查询词"},
        "knowledge_base": {"type": "str", "required": True, "description": "知识库编码"},
        "query_mode": {"type": "str", "required": False, "description": "查询模式", "default": "similarity"}
    }
    outputs = {
        "results": {"type": "list", "description": "查询结果列表"},
        "query": {"type": "str", "description": "查询词"},
        "count": {"type": "int", "description": "结果数量"}
    }
    
    async def execute(self, context: Any, **kwargs) -> Dict[str, Any]:
        query = kwargs.get("query_text", "")
        knowledge_base = kwargs.get("knowledge_base", "")
        query_mode = kwargs.get("query_mode", "similarity")
        
        # 记录输入信息
        self._log_input(query=query, knowledge_base=knowledge_base, query_mode=query_mode)
        
        # 构建处理逻辑描述
        processing = f"使用模式 '{query_mode}' 查询知识库 '{knowledge_base}'，查询词 '{query}'"
        self._log_processing(processing)
        
        try:
            from app.services.knowledge_base_service import KnowledgeBaseService
            
            service = KnowledgeBaseService()
            result = await service.search(
                query=query,
                kb_code=knowledge_base,
                mode=query_mode
            )
            
            results = result.get("results", [])
            
            output = {
                "success": True,
                "results": results,
                "query": query,
                "input": {"query": query, "knowledge_base": knowledge_base, "query_mode": query_mode},
                "processing": processing,
                "output": {"results": results, "count": len(results), "query": query}
            }
            
            # 记录输出信息
            self._log_output(success=True, query=query, result_count=len(results))
            
            return output
            
        except Exception as e:
            error_msg = str(e)
            self._log_output(success=False, error=error_msg)
            
            return {
                "success": False,
                "error": error_msg,
                "input": {"query": query, "knowledge_base": knowledge_base, "query_mode": query_mode},
                "processing": processing,
                "output": {"error": error_msg}
            }