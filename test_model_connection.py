"""
测试模型连接错误日志功能
"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'backend'))

# 配置基础日志
import logging
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s.%(msecs)03d [%(levelname)-8s] %(name)-25s: %(message)s',
    datefmt='%H:%M:%S'
)

from app.services.llm.factory import ProviderFactory

logger = logging.getLogger("test_model_connection")

def test_invalid_api_key():
    """测试无效的 API Key"""
    print("\n" + "="*60)
    print("测试 1: 无效的 API Key")
    print("="*60)
    
    config = {
        "provider": "openai",
        "apiKey": "invalid-key-12345",
        "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "model": "qwen-vl-plus-2025-05-07"
    }
    
    try:
        provider = ProviderFactory.create("openai", config)
        response = provider.call_sync("Hello")
        print(f"响应: {response}")
    except Exception as e:
        print(f"捕获到异常: {e}")

def test_invalid_url():
    """测试无效的 URL"""
    print("\n" + "="*60)
    print("测试 2: 无效的 URL")
    print("="*60)
    
    config = {
        "provider": "openai",
        "apiKey": "test-key",
        "baseUrl": "http://invalid-url-that-does-not-exist.com/v1",
        "model": "test-model"
    }
    
    try:
        provider = ProviderFactory.create("openai", config)
        response = provider.call_sync("Hello")
        print(f"响应: {response}")
    except Exception as e:
        print(f"捕获到异常: {e}")

def test_missing_config():
    """测试缺失配置"""
    print("\n" + "="*60)
    print("测试 3: 缺失配置")
    print("="*60)
    
    config = {
        "provider": "openai",
        "apiKey": "",
        "baseUrl": "",
        "model": ""
    }
    
    try:
        provider = ProviderFactory.create("openai", config)
        response = provider.call_sync("Hello")
        print(f"响应: {response}")
    except Exception as e:
        print(f"捕获到异常: {e}")

if __name__ == "__main__":
    print("\n开始测试模型连接错误日志功能...")
    
    # 运行测试
    test_missing_config()
    test_invalid_url()
    test_invalid_api_key()
    
    print("\n" + "="*60)
    print("测试完成，请检查控制台输出和日志文件 backend/logs/app.log")
    print("="*60 + "\n")
