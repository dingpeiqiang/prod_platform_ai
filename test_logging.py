"""
测试日志系统是否正常工作
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'backend'))

from app.harness.observability.logger import get_agent_logger
from app.harness.engine import HarnessEngine
import logging

def test_logging():
    print("开始测试日志系统...")
    
    # 测试 AgentLogger
    agent_logger = get_agent_logger()
    agent_logger.info("这是来自 AgentLogger 的测试信息")
    agent_logger.debug("这是来自 AgentLogger 的调试信息")
    agent_logger.warning("这是来自 AgentLogger 的警告信息")
    agent_logger.error("这是来自 AgentLogger 的错误信息")
    
    # 测试 Harness Engine 日志
    engine = HarnessEngine()
    engine.logger.info("这是来自 HarnessEngine 的测试信息")
    
    # 测试标准 logging
    logger = logging.getLogger("harness.engine")
    logger.info("这是来自标准 logging 的测试信息")
    
    print("日志测试完成，请检查控制台输出和日志文件")

if __name__ == "__main__":
    test_logging()