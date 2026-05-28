"""
知识库查询节点
"""
from typing import Dict, Any
from app.langchain.workflow_nodes import WorkflowNode, register_node, DelegateExecution, ParamSchema


@register_node
class QueryKnowledgeNode(WorkflowNode):
    """知识库查询节点"""

    name = "workflow.query_knowledge"
    display_name = "知识库查询"
    description = "查询知识库获取相关知识"
    config_fields = {
        "query_text": ParamSchema(type="str", required=True, description="查询词"),
        "knowledge_base": ParamSchema(type="str", required=True, description="知识库编码"),
        "query_mode": ParamSchema(type="str", required=False, description="查询模式", default="similarity"),
    }
    output_fields = {
        "results": ParamSchema(type="list", description="查询结果列表"),
        "query": ParamSchema(type="str", description="查询词"),
        "count": ParamSchema(type="int", description="结果数量"),
    }

    async def execute(self, execution: DelegateExecution) -> None:
        query = execution.get("query_text", "")
        knowledge_base = execution.get("knowledge_base", "")
        query_mode = execution.get("query_mode", "similarity")

        self._log_input(query=query, knowledge_base=knowledge_base, query_mode=query_mode)
        processing = f"使用模式 '{query_mode}' 查询知识库 '{knowledge_base}'，查询词 '{query}'"
        self._log_processing(processing)

        try:
            from app.services.knowledge_base_service import KnowledgeBaseService
            service = KnowledgeBaseService()
            result = await service.search(query=query, kb_code=knowledge_base, mode=query_mode)

            results = result.get("results", [])
            execution.set("results", results)
            execution.set("query", query)
            execution.set("count", len(results))

            self._log_output(success=True, query=query, result_count=len(results))

        except Exception as e:
            error_msg = str(e)
            execution.set("results", [])
            execution.set("query", query)
            execution.set("count", 0)
            execution.set("error", error_msg)
            self._log_output(success=False, error=error_msg)