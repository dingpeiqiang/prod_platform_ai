"""
完整测试日志系统
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'backend'))

# 先配置日志
from app.main import _apply_logging
_apply_logging()

from app.harness.observability.logger import get_agent_logger
from app.harness.engine import HarnessEngine, AgentRequest, RequestType
import logging
import asyncio

async def test_complete_logging():
    print("开始完整日志系统测试...")
    
    # 测试1: 标准 logging
    logger = logging.getLogger("test.module")
    logger.debug("这是 DEBUG 级别日志")
    logger.info("这是 INFO 级别日志")
    logger.warning("这是 WARNING 级别日志")
    logger.error("这是 ERROR 级别日志")
    
    # 测试2: AgentLogger
    agent_logger = get_agent_logger()
    agent_logger.set_context(session_id="test-session-001", user_id="test-user")
    agent_logger.debug("AgentLogger DEBUG 测试")
    agent_logger.info("AgentLogger INFO 测试")
    agent_logger.warning("AgentLogger WARNING 测试")
    agent_logger.error("AgentLogger ERROR 测试")
    
    # 测试3: Harness Engine
    engine = HarnessEngine()
    
    # 测试4: 处理一个请求
    request = AgentRequest(
        request_type=RequestType.GENERAL_CHAT,
        user_input="测试日志功能",
        session_id="test-session-002"
    )
    
    response = await engine.process(request)
    print(f"请求处理结果: {response.success}")
    
    # 测试5: 检查追踪日志
    traces = engine.get_traces()
    print(f"追踪记录数: {len(traces)}")
    
    print("\n完整日志测试完成！")
    print("请检查:")
    print("1. 控制台输出是否有彩色日志")
    print("2. backend/app/logs/app.log 文件是否有新记录")

if __name__ == "__main__":
    asyncio.run(test_complete_logging())