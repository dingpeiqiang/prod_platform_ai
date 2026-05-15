"""
基于大模型提示词的工作流调度器

通过自然语言描述，让大模型智能选择和调度工作流执行。
支持：
1. 意图识别 → 工作流匹配
2. 动态参数提取 → 工作流执行
3. 多工作流协作 → 复杂任务分解
"""

from typing import Dict, Any, List, Optional, Tuple
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import JsonOutputParser
import logging
import json

from app.langchain.llm_wrapper import get_langchain_llm
from app.engine.workflow_executor import WorkflowExecutor

logger = logging.getLogger("llm_workflow_scheduler")


class WorkflowScheduler:
    """基于LLM的工作流调度器"""
    
    def __init__(self):
        self.llm = get_langchain_llm().llm
        self.workflow_registry = {}
    
    def register_workflow(self, workflow_id: str, workflow_def: Dict[str, Any], description: str):
        """注册工作流到调度器"""
        self.workflow_registry[workflow_id] = {
            "definition": workflow_def,
            "description": description
        }
    
    def unregister_workflow(self, workflow_id: str):
        """从调度器中移除工作流"""
        if workflow_id in self.workflow_registry:
            del self.workflow_registry[workflow_id]
    
    def list_workflows(self) -> List[Dict[str, str]]:
        """列出所有已注册的工作流"""
        return [
            {"id": wf_id, "description": info["description"]}
            for wf_id, info in self.workflow_registry.items()
        ]
    
    async def schedule_by_prompt(self, user_prompt: str) -> Dict[str, Any]:
        """通过提示词调度工作流"""
        # Step 1: 分析用户意图，选择合适的工作流
        workflow_id, params, reasoning = await self._analyze_and_select(user_prompt)
        
        if not workflow_id:
            return {
                "success": False,
                "reasoning": reasoning,
                "message": "未找到合适的工作流"
            }
        
        # Step 2: 执行选中的工作流
        result = await self._execute_workflow(workflow_id, params)
        
        return {
            "success": True,
            "workflow_id": workflow_id,
            "reasoning": reasoning,
            "params": params,
            "result": result
        }
    
    async def schedule_with_choice(self, user_prompt: str) -> Dict[str, Any]:
        """分析用户意图并返回可选工作流（供用户选择）"""
        # 分析用户意图
        analysis = await self._analyze_intent(user_prompt)
        
        # 获取匹配的工作流
        matched_workflows = self._match_workflows(analysis)
        
        return {
            "intent": analysis,
            "matched_workflows": matched_workflows,
            "count": len(matched_workflows)
        }
    
    async def _analyze_and_select(self, user_prompt: str) -> Tuple[Optional[str], Dict[str, Any], str]:
        """分析用户意图并选择工作流"""
        if not self.workflow_registry:
            return None, {}, "没有已注册的工作流"
        
        # 构建工作流列表描述
        workflow_descriptions = "\n".join([
            f"- {wf_id}: {info['description']}"
            for wf_id, info in self.workflow_registry.items()
        ])
        
        system_prompt = f"""你是一个智能工作流调度助手。

可用的工作流：
{workflow_descriptions}

请分析用户的请求，完成以下任务：
1. 理解用户意图
2. 选择最合适的工作流
3. 提取工作流所需的参数

输出格式（JSON）：
{{
    "workflow_id": "选中的工作流ID，无法匹配则为空字符串",
    "parameters": {{
        "参数名": "参数值"
    }},
    "reasoning": "选择理由"
}}"""
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("user", user_prompt)
        ])
        
        parser = JsonOutputParser()
        chain = prompt | self.llm | parser
        
        try:
            result = await chain.ainvoke({})
            return result.get("workflow_id"), result.get("parameters", {}), result.get("reasoning", "")
        except Exception as e:
            logger.error(f"LLM分析失败: {e}")
            return None, {}, f"分析失败: {str(e)}"
    
    async def _analyze_intent(self, user_prompt: str) -> Dict[str, Any]:
        """分析用户意图"""
        system_prompt = """你是一个意图分析专家。请分析用户的请求并提取关键信息。

输出格式（JSON）：
{
    "intent": "用户意图描述",
    "entities": {
        "实体类型": "实体值"
    },
    "requirements": ["需求列表"]
}"""
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("user", user_prompt)
        ])
        
        parser = JsonOutputParser()
        chain = prompt | self.llm | parser
        
        try:
            return await chain.ainvoke({})
        except Exception as e:
            logger.error(f"意图分析失败: {e}")
            return {"intent": user_prompt, "entities": {}, "requirements": []}
    
    def _match_workflows(self, analysis: Dict[str, Any]) -> List[Dict[str, Any]]:
        """根据意图匹配工作流"""
        matched = []
        intent = analysis.get("intent", "")
        
        for wf_id, info in self.workflow_registry.items():
            # 简单的关键词匹配
            description = info["description"].lower()
            intent_lower = intent.lower()
            
            # 检查是否有匹配的关键词
            if any(keyword in intent_lower for keyword in description.split()):
                matched.append({
                    "workflow_id": wf_id,
                    "description": info["description"],
                    "match_score": self._calculate_match_score(intent, info["description"])
                })
        
        # 按匹配分数排序
        matched.sort(key=lambda x: x["match_score"], reverse=True)
        return matched
    
    def _calculate_match_score(self, intent: str, description: str) -> float:
        """计算匹配分数"""
        intent_words = set(intent.lower().split())
        desc_words = set(description.lower().split())
        
        if not intent_words:
            return 0.0
        
        intersection = intent_words & desc_words
        return len(intersection) / len(intent_words)
    
    async def _execute_workflow(self, workflow_id: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """执行工作流"""
        if workflow_id not in self.workflow_registry:
            return {"error": f"工作流 {workflow_id} 未找到"}
        
        workflow_def = self.workflow_registry[workflow_id]["definition"]
        
        try:
            executor = WorkflowExecutor(workflow_def)
            context = await executor.execute(params)
            
            return {
                "status": context.status.value,
                "outputs": context.outputs,
                "error": context.error
            }
        except Exception as e:
            logger.error(f"工作流执行失败: {e}")
            return {"error": str(e)}
    
    async def execute_sequential_workflows(self, workflow_ids: List[str], params_list: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """顺序执行多个工作流"""
        results = []
        
        for i, workflow_id in enumerate(workflow_ids):
            params = params_list[i] if i < len(params_list) else {}
            
            if workflow_id not in self.workflow_registry:
                results.append({"workflow_id": workflow_id, "error": "工作流未找到"})
                continue
            
            result = await self._execute_workflow(workflow_id, params)
            result["workflow_id"] = workflow_id
            results.append(result)
        
        return results
    
    async def execute_parallel_workflows(self, workflow_ids: List[str], params_list: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """并行执行多个工作流"""
        import asyncio
        
        tasks = []
        for i, workflow_id in enumerate(workflow_ids):
            params = params_list[i] if i < len(params_list) else {}
            tasks.append(self._execute_workflow(workflow_id, params))
        
        results = await asyncio.gather(*tasks)
        
        for i, result in enumerate(results):
            result["workflow_id"] = workflow_ids[i]
        
        return results


class DynamicWorkflowGenerator:
    """动态工作流生成器"""
    
    def __init__(self):
        self.llm = get_langchain_llm().llm
    
    async def generate_workflow(self, requirements: str) -> Dict[str, Any]:
        """根据自然语言需求生成工作流定义"""
        system_prompt = """你是一个工作流生成专家。请根据用户的业务需求生成工作流定义。

工作流格式规范：
{
    "name": "工作流名称",
    "description": "工作流描述",
    "nodes": [
        {
            "id": "节点ID",
            "type": "节点类型",
            "data": {
                "label": "节点标签",
                // 其他节点特定参数
            }
        }
    ],
    "edges": [
        {
            "source": "源节点ID",
            "target": "目标节点ID",
            "sourceHandle": "输出端口（条件节点使用true/false）"
        }
    ]
}

支持的节点类型：
- start: 开始节点
- end: 结束节点
- prompt: 提示词节点（data.prompt）
- llm: LLM调用节点（data.model, data.temperature）
- condition: 条件分支节点（data.leftValue, data.operator, data.rightValue）
- loop: 循环节点（data.loopType, data.loopCount）
- variable: 变量赋值节点（data.variableName, data.variableValue）
- http: HTTP请求节点（data.method, data.url）
- code: 代码执行节点（data.code）
- parser: 输出解析节点
- tool: 工具调用节点（data.toolType, data.params）

请确保生成有效的JSON格式！"""
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("user", f"请根据以下需求生成工作流：\n{requirements}")
        ])
        
        parser = JsonOutputParser()
        chain = prompt | self.llm | parser
        
        try:
            return await chain.ainvoke({})
        except Exception as e:
            logger.error(f"工作流生成失败: {e}")
            return {"error": str(e)}
    
    async def optimize_workflow(self, workflow_def: Dict[str, Any]) -> Dict[str, Any]:
        """优化工作流定义"""
        system_prompt = """你是一个工作流优化专家。请分析并优化给定的工作流定义。

优化方向：
1. 删除冗余节点
2. 优化节点顺序
3. 简化条件判断
4. 提高执行效率

请返回优化后的工作流定义（JSON格式）。"""
        
        prompt = ChatPromptTemplate.from_messages([
            ("system", system_prompt),
            ("user", f"请优化以下工作流：\n{json.dumps(workflow_def, indent=2)}")
        ])
        
        parser = JsonOutputParser()
        chain = prompt | self.llm | parser
        
        try:
            return await chain.ainvoke({})
        except Exception as e:
            logger.error(f"工作流优化失败: {e}")
            return workflow_def


# 全局调度器实例
_global_scheduler = None

def get_workflow_scheduler() -> WorkflowScheduler:
    """获取全局工作流调度器实例"""
    global _global_scheduler
    if _global_scheduler is None:
        _global_scheduler = WorkflowScheduler()
    return _global_scheduler


# 使用示例
if __name__ == "__main__":
    import asyncio
    
    # 创建调度器
    scheduler = WorkflowScheduler()
    
    # 注册示例工作流
    sample_workflow = {
        "nodes": [
            {"id": "start-1", "type": "start", "data": {"label": "开始"}},
            {"id": "prompt-1", "type": "prompt", "data": {"label": "提示词", "prompt": "请回答：{{question}}"}},
            {"id": "llm-1", "type": "llm", "data": {"label": "LLM", "model": "qwen-vl-plus"}},
            {"id": "end-1", "type": "end", "data": {"label": "结束"}}
        ],
        "edges": [
            {"source": "start-1", "target": "prompt-1"},
            {"source": "prompt-1", "target": "llm-1"},
            {"source": "llm-1", "target": "end-1"}
        ]
    }
    
    scheduler.register_workflow("qa_workflow", sample_workflow, "问答工作流：回答用户问题")
    
    # 通过自然语言调度
    result = asyncio.run(scheduler.schedule_by_prompt("请解释什么是人工智能"))
    print("调度结果:", result)