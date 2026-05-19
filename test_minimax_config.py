"""
测试用户提供的 MiniMax 配置
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

def test_user_config():
    """测试用户提供的配置"""
    print("\n" + "="*60)
    print("测试用户提供的 MiniMax 配置")
    print("="*60)
    
    # 原始配置（有问题的 Base URL）
    config_original = {
        "provider": "custom",
        "model": "minimax-m2.7",
        "apiKey": "cb3a5cb469de1d0820d25a1e6349306dc4482f90",
        "baseUrl": "https://aicp.teamshub.com/openai/api/v1/openai/v1",
        "temperature": 0.3,
        "maxTokens": 2048,
        "thinking": False
    }
    
    # 修正后的配置
    config_fixed = {
        "provider": "custom",
        "model": "minimax-m2.7",
        "apiKey": "cb3a5cb469de1d0820d25a1e6349306dc4482f90",
        "baseUrl": "https://aicp.teamshub.com/openai/api/v1",
        "temperature": 0.3,
        "maxTokens": 2048,
        "thinking": False
    }
    
    print("\n【测试 1】原始配置（Base URL 有重复路径）")
    print("-" * 60)
    print(f"Base URL: {config_original['baseUrl']}")
    print(f"Model: {config_original['model']}")
    
    try:
        provider = ProviderFactory.create("custom", config_original)
        print(f"✓ Provider 创建成功")
        
        response = provider.call_sync("Hello, this is a test.")
        if response:
            print(f"✓ 调用成功！")
            print(f"响应长度: {len(response)}")
            print(f"响应预览: {response[:100]}...")
        else:
            print("✗ 调用失败，返回 None")
    except Exception as e:
        print(f"✗ 错误: {e}")
    
    print("\n【测试 2】修正后的配置（Base URL 正常）")
    print("-" * 60)
    print(f"Base URL: {config_fixed['baseUrl']}")
    print(f"Model: {config_fixed['model']}")
    
    try:
        provider = ProviderFactory.create("custom", config_fixed)
        print(f"✓ Provider 创建成功")
        
        response = provider.call_sync("Hello, this is a test.")
        if response:
            print(f"✓ 调用成功！")
            print(f"响应长度: {len(response)}")
            print(f"响应预览: {response[:100]}...")
        else:
            print("✗ 调用失败，返回 None")
    except Exception as e:
        error_msg = str(e)
        print(f"✗ 错误: {error_msg}")
        
        # 提供建议
        if "404" in error_msg:
            print("\n💡 建议:")
            print("   - 模型名称 'minimax-m2.7' 可能不存在")
            print("   - 请查阅 API 文档确认可用的模型列表")
            print("   - 尝试其他模型名称，如: abab6.5-chat, abab6-chat")
        elif "401" in error_msg or "Unauthorized" in error_msg:
            print("\n💡 建议:")
            print("   - API Key 可能无效或已过期")
            print("   - 请检查 API Key 是否正确")

if __name__ == "__main__":
    print("\n开始测试...")
    test_user_config()
    print("\n" + "="*60)
    print("测试完成")
    print("="*60 + "\n")
