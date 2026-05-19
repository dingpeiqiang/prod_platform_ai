"""
测试自定义模型配置功能
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

def test_custom_provider():
    """测试自定义 Provider 配置"""
    print("\n" + "="*60)
    print("测试 1: 自定义 Provider (阿里云 DashScope)")
    print("="*60)
    
    config = {
        "provider": "custom",
        "model": "qwen-plus",
        "apiKey": "sk-16f6fcbc5d1e47f4b976ed8c757ddb95",
        "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "temperature": 0.5,
        "maxTokens": 2048,
        "thinking": True
    }
    
    try:
        provider = ProviderFactory.create("custom", config)
        print(f"✓ Provider 创建成功: {type(provider).__name__}")
        
        response = provider.call_sync("你好，请简单介绍一下自己")
        if response:
            print(f"✓ 调用成功，响应长度: {len(response)}")
            print(f"响应预览: {response[:100]}...")
        else:
            print("✗ 调用失败，返回 None")
    except Exception as e:
        print(f"✗ 错误: {e}")

def test_with_advanced_config():
    """测试带高级配置的调用"""
    print("\n" + "="*60)
    print("测试 2: 带高级配置的调用")
    print("="*60)
    
    config = {
        "provider": "custom",
        "model": "qwen-plus",
        "apiKey": "sk-16f6fcbc5d1e47f4b976ed8c757ddb95",
        "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "temperature": 0.8,  # 更高的随机性
        "maxTokens": 512,    # 限制输出长度
        "thinking": False
    }
    
    try:
        provider = ProviderFactory.create("custom", config)
        print(f"✓ Provider 创建成功")
        print(f"  - Temperature: {config['temperature']}")
        print(f"  - Max Tokens: {config['maxTokens']}")
        print(f"  - Thinking: {config['thinking']}")
        
        response = provider.call_sync("用一句话描述人工智能")
        if response:
            print(f"✓ 调用成功，响应长度: {len(response)}")
            print(f"响应: {response}")
        else:
            print("✗ 调用失败")
    except Exception as e:
        print(f"✗ 错误: {e}")

def test_missing_fields():
    """测试缺少必要字段的情况"""
    print("\n" + "="*60)
    print("测试 3: 缺少必要字段")
    print("="*60)
    
    config = {
        "provider": "custom",
        "model": "",  # 空模型名称
        "apiKey": "",
        "baseUrl": ""
    }
    
    try:
        provider = ProviderFactory.create("custom", config)
        print(f"✓ Provider 创建成功（但配置不完整）")
        
        response = provider.call_sync("测试")
        if response is None:
            print("✓ 正确返回 None（配置不完整）")
        else:
            print(f"✗ 意外返回: {response}")
    except Exception as e:
        print(f"捕获到异常: {e}")

if __name__ == "__main__":
    print("\n开始测试自定义模型配置功能...")
    
    # 运行测试
    test_missing_fields()
    test_custom_provider()
    test_with_advanced_config()
    
    print("\n" + "="*60)
    print("测试完成")
    print("="*60 + "\n")
