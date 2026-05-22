"""
调试后端 API 调用
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from app.services.llm.openai_provider import OpenAIProvider

def debug_provider():
    """调试 OpenAIProvider"""
    print("=" * 60)
    print("调试 OpenAIProvider")
    print("=" * 60)
    
    # 模拟数据库配置
    config = {
        'provider': 'custom',
        'model': 'custom-minimax-m2.7',  # 前端传递的名称
        'apiKey': 'cb3a5cb469de1d0820d25a1e6349306dc4482f90',
        'baseUrl': 'https://aicp.teamshub.com/openai/api/v1/openai/v1',
        'temperature': 0.1,
        'maxTokens': 1024
    }
    
    print(f"配置模型: {config['model']}")
    print(f"基础 URL: {config['baseUrl']}")
    print()
    
    # 创建 Provider
    provider = OpenAIProvider(config)
    
    print(f"Provider model: {provider.model}")
    print(f"Provider base_url: {provider.base_url}")
    print(f"Provider api_key: {'***' if provider.api_key else '(空)'}")
    print()
    
    # 构建请求体
    payload = provider._build_payload("你是谁", "", 50)
    print(f"请求体: {payload}")
    print(f"发送的模型名称: {payload['model']}")
    print()
    
    # 获取请求头
    headers = provider._get_headers()
    print(f"请求头: {headers}")
    print()
    
    # 测试调用
    print("执行实际调用...")
    print("-" * 40)
    try:
        result = provider.call_sync("你是谁", "", 100)
        if result:
            print(f"✅ 成功！响应: {result[:100]}...")
        else:
            print("❌ 失败：返回空")
    except Exception as e:
        print(f"❌ 异常: {e}")

if __name__ == "__main__":
    debug_provider()
    print("\n" + "=" * 60)
