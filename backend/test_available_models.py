"""
测试不同的模型名称，找出用户 API Key 有权访问的模型
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import requests

def test_models():
    """测试不同的模型名称"""
    print("=" * 60)
    print("测试不同的模型名称")
    print("=" * 60)
    
    base_url = "https://aicp.teamshub.com/openai/api/v1/openai/v1/chat/completions"
    api_key = "cb3a5cb469de1d0820d25a1e6349306dc4482f90"
    
    headers = {
        "Content-Type": "application/json",
        "token": api_key
    }
    
    # 尝试不同的模型名称
    models_to_test = [
        "minimax-m2.7",
        "minimax-m2.2",
        "abab6.5-chat",
        "m2.7",
        "m2.2",
        "qwen-plus",
        "gpt-4",
        "gpt-3.5-turbo"
    ]
    
    for model_name in models_to_test:
        print(f"\n测试模型: {model_name}")
        print("-" * 40)
        
        payload = {
            "stream": False,
            "messages": [
                {"role": "user", "content": "Hello"}
            ],
            "model": model_name,
            "temperature": 0.1,
            "max_tokens": 50
        }
        
        try:
            response = requests.post(base_url, json=payload, headers=headers, timeout=30)
            
            if response.status_code == 200:
                result = response.json()
                
                if result.get('code') or result.get('flag') is False:
                    error_msg = result.get('message', 'Unknown error')
                    print(f"❌ 权限错误: {error_msg}")
                else:
                    content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
                    if content:
                        print(f"✅ 成功！响应: {content[:50]}...")
                    else:
                        print(f"⚠️  响应键: {list(result.keys())}")
            else:
                print(f"❌ HTTP {response.status_code}")
                
        except Exception as e:
            print(f"❌ 请求异常: {e}")

if __name__ == "__main__":
    test_models()
    print("\n" + "=" * 60)
