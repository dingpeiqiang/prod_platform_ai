import json
import logging
from typing import Dict, Any, List, AsyncGenerator
from app.services.llm_service import llm_service
from app.skills.tool_registry import ToolRegistry
from app.core.config_loader import config_loader
from app.mcp_tools import get_toolhub

logger = logging.getLogger("agent_executor")


def _truncate(text: str, max_len: int = 200) -> str:
    """截断长文本用于日志展示"""
    if len(text) <= max_len:
        return text
    return text[:max_len] + f"...[共{len(text)}字符]"

class AgentExecutor:
    """
    LLM 驱动的执行器：
    1. 接收用户输入和可用工具列表
    2. 让 LLM 决定调用哪个工具
    3. 执行工具并返回结果
    """

    @classmethod
    async def execute_stream(cls, user_input: str):
        """
        流式执行 Agent 任务，实时返回思考和执行过程
        """
        import asyncio
        import time

        start_time = time.time()
        logger.info("[AgentExecutor] 开始流式执行, user_input=%s", _truncate(user_input, 100))

        yield {"type": "start", "content": "正在启动智能体..."}
        await asyncio.sleep(0.1)

        yield {"type": "thinking", "content": "🧠 正在分析用户意图并选择工具..."}
        tools_meta = ToolRegistry.get_all_tools()
        prompt = cls._build_react_prompt(user_input, tools_meta)
        logger.info("[AgentExecutor] Prompt 构建完成, length=%d", len(prompt))

        yield {"type": "thinking", "content": "🤖 正在调用 LLM 进行推理..."}
        logger.info("[AgentExecutor] 开始调用 LLM...")
        llm_start = time.time()
        response = llm_service._call_llm_sync(prompt)
        llm_elapsed = time.time() - llm_start
        logger.info("[AgentExecutor] LLM 调用完成, elapsed=%.2fs, response_length=%d",
                    llm_elapsed, len(response) if response else 0)

        if not response:
            logger.error("[AgentExecutor] LLM 返回为空 (耗时 %.2fs), fallback_to_rules=%s",
                        llm_elapsed, llm_service.fallback_to_rules)
            yield {
                "type": "error",
                "content": f"LLM 返回为空（耗时 {llm_elapsed:.1f}s），且未启用降级处理"
            }
            yield {
                "type": "stats",
                "content": {
                    "totalElapsed": round(time.time() - start_time, 3),
                    "intentElapsed": round(llm_elapsed, 3),
                    "llmElapsed": round(llm_elapsed, 3),
                    "llmTokens": 0,
                    "llmChars": 0,
                    "llmTps": 0.0,
                    "isForm": False,
                    "error": "LLM 返回为空，且未启用降级处理"
                }
            }
            return

        try:
            decision = cls._parse_llm_decision(response)
            tool_name = decision.get("tool_name")

            yield {"type": "decision", "content": f"✅ 决定调用工具: {tool_name}"}
            yield {"type": "reasoning", "content": f"💭 推理: {decision.get('reasoning')}"}
            await asyncio.sleep(0.1)

            yield {"type": "executing", "content": f"⚙️ 正在执行 {tool_name}..."}

            # 优先使用 MCP ToolHub 执行
            hub = get_toolhub()
            if hub.has_tool(tool_name):
                result = hub.execute_sync(tool_name, decision.get("arguments", {}))
            else:
                # 降级到旧的 ToolRegistry
                result = ToolRegistry.execute(tool_name, **decision.get("arguments", {}))

            yield {
                "type": "result",
                "data": {
                    "success": True,
                    "tool_used": tool_name,
                    "result": result,
                    "reasoning": decision.get("reasoning")
                }
            }

            yield {"type": "thinking", "content": "✨ 任务执行完成，正在生成回复..."}
            await asyncio.sleep(0.1)

            result_prompt = cls._build_result_prompt(user_input, tool_name, result, decision.get("reasoning"))
            yield {"type": "text_start"}

            token_count = 0
            async for token in llm_service.call_llm_stream(result_prompt):
                token_count += 1
                yield {"type": "text", "content": token}

            yield {"type": "text_end"}
            logger.info("[AgentExecutor] 流式回复完成, tokens=%d", token_count)

        except Exception as e:
            logger.exception("[AgentExecutor] 执行异常: %s", e)
            yield {"type": "error", "content": f"❌ 执行出错: {str(e)}"}

    @classmethod
    def execute(cls, user_input: str, available_tools: List[str] = None) -> Dict[str, Any]:
        logger.info("\n" + "="*60)
        logger.info("[AGENT START] 收到用户输入: %s", user_input)
        logger.info("="*60)

        # 1. 获取所有已注册的工具定义
        tools_meta = ToolRegistry.get_all_tools()
        
        # 2. 构建 ReAct Prompt
        prompt = cls._build_react_prompt(user_input, tools_meta)
        
        # 3. 调用 LLM 进行决策
        logger.info("[AGENT THINKING] 正在调用 LLM 进行决策...")
        response = llm_service._call_llm_sync(prompt)
        
        if not response:
            logger.error("[AGENT ERROR] LLM 未返回任何内容")
            return {"success": False, "error": "LLM 调用失败"}
        
        logger.info("[AGENT DECISION] LLM 原始决策: %s", response[:200])
        
        # 4. 解析 LLM 的决策
        try:
            decision = cls._parse_llm_decision(response)
            tool_name = decision.get("tool_name")
            tool_args = decision.get("arguments", {})
            
            logger.info("[AGENT ACTION] 决定调用工具: [%s], 参数: %s", tool_name, tool_args)
            
            # 5. 执行工具（优先 MCP ToolHub）
            hub = get_toolhub()
            if hub.has_tool(tool_name):
                exec_result = hub.execute_sync(tool_name, tool_args)
                result = exec_result.get("result") if exec_result.get("success") else None
            else:
                # 降级到旧的 ToolRegistry
                result = ToolRegistry.execute(tool_name, **tool_args)
            
            logger.info("[AGENT RESULT] 工具执行成功, 结果摘要: %s", str(result)[:200])
            logger.info("="*60 + "\n")
            
            return {
                "success": True,
                "tool_used": tool_name,
                "result": result,
                "reasoning": decision.get("reasoning")
            }
            
        except Exception as e:
            logger.exception("[AGENT ERROR] 执行过程中发生异常: %s", e)
            logger.info("="*60 + "\n")
            return {"success": False, "error": str(e)}

    @classmethod
    def _build_react_prompt(cls, user_input: str, tools_meta: List[Dict] = None) -> str:
        # 优先使用 MCP ToolHub
        hub = get_toolhub()
        if hub.get_tool_count() > 0:
            tools_desc = hub.get_tool_schemas_for_llm()
        elif tools_meta:
            tools_desc = "\n".join([f"- {t['name']}: {t['description']}" for t in tools_meta])
        else:
            tools_desc = "暂无可用工具"
        
        return f"""你是一个智能助手，可以通过调用工具来完成任务。

## 可用工具
{tools_desc}

## 任务
分析用户输入，选择一个最合适的工具并提取参数。

## 输出格式
请严格以 JSON 格式返回：
{{
  "reasoning": "你的思考过程",
  "tool_name": "工具名称 (必须是上述列表中的一个)",
  "arguments": {{
    "param_name": "参数值"
  }}
}}

## 用户输入
{user_input}
"""

    @classmethod
    def _parse_llm_decision(cls, response: str) -> Dict:
        cleaned = response.strip()
        if "```json" in cleaned:
            cleaned = cleaned.split("```json")[1].split("```")[0].strip()
        elif "```" in cleaned:
            cleaned = cleaned.split("```")[1].split("```")[0].strip()

        return json.loads(cleaned)

    @classmethod
    def _build_result_prompt(cls, user_input: str, tool_name: str, result: Any, reasoning: str) -> str:
        result_json = json.dumps(result, ensure_ascii=False, indent=2)
        return f"""你是一个智能助手，用户刚才请求了帮助。

## 用户原始输入
{user_input}

## 你调用的工具
{tool_name}

## 工具执行结果
{result_json}

## 你的推理过程
{reasoning}

## 任务
请根据以上信息，用自然、友好的语言向用户解释：
1. 你理解了用户的什么需求
2. 你做了什么
3. 结果是什么
4. 接下来用户可以怎么做

请直接回复，不需要再调用任何工具。"""
