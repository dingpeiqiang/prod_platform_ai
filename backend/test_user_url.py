"""
测试用户提供的正确 URL
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import requests
import json

def test_user_url():
    """测试用户提供的 URL"""
    print("=" * 60)
    print("测试用户提供的 URL")
    print("=" * 60)
    
    # 用户提供的配置
    base_url = "https://aicp.teamshub.com/openai/api/v1/openai/v1"
    api_key = "cb3a5cb469de1d0820d25a1e6349306dc4482f90"
    
    print(f"Base URL: {base_url}")
    print(f"API Key: {'***' if api_key else '(空)'}")
    print()
    
    # 测试不同的路径
    paths_to_try = [
        "",
        "/chat/completions"
    ]
    
    headers = {
        "Content-Type": "application/json",
        "token": api_key
    }
    
    payload = {
        "stream": False,
        "messages": [
            {"role": "system", "content": "you are a helpful assistant!"},
            {"role": "user", "content": "你好，你是谁"}
        ],
        "model": "minimax-m2.7"
    }
    
    for path in paths_to_try:
        url = f"{base_url}{path}"
        print(f"\n测试: {url}")
        print("-" * 40)
        
        try:
            response = requests.post(url, json=payload, headers=headers, timeout=60)
            
            print(f"状态码: {response.status_code}")
            
            if response.status_code == 200:
                result = response.json()
                
                # 检查错误
                if result.get('code') or result.get('flag') is False:
                    print(f"❌ 错误: {result.get('message', 'Unknown error')}")
                else:
                    content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
                    if content:
                        print("✅ 成功！")
                        print("-" * 40)
                        print(content)
                        print("-" * 40)
                    else:
                        print(f"响应键: {list(result.keys())}")
                        print(f"响应预览: {json.dumps(result, ensure_ascii=False)[:500]}...")
            else:
                print(f"❌ HTTP {response.status_code}")
                try:
                    error_data = response.json()
                    print(f"错误信息: {error_data}")
                except:
                    print(f"响应内容: {response.text[:200]}")
                    
        except Exception as e:
            print(f"❌ 请求异常: {e}")

if __name__ == "__main__":
    test_user_url()
    print("\n" + "=" * 60)
