"""
调试 MiniMax 模型响应格式
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import requests

def debug_response():
    """调试模型响应"""
    print("=" * 60)
    print("调试 MiniMax 模型响应")
    print("=" * 60)
    
    base_url = "https://aicp.teamshub.com/openai/api/v1"
    api_key = "cb3a5cb469de1d0820d25a1e6349306dc4482f90"
    
    url = f"{base_url}/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    payload = {
        "model": "minimax-m2.7",
        "messages": [
            {"role": "user", "content": "Hello"}
        ],
        "temperature": 0.1,
        "max_tokens": 100
    }
    
    print(f"请求 URL: {url}")
    print(f"请求体: {payload}")
    print()
    
    try:
        response = requests.post(url, json=payload, headers=headers, timeout=60)
        
        print(f"状态码: {response.status_code}")
        print(f"响应头: {dict(response.headers)}")
        print(f"响应内容长度: {len(response.text)}")
        print()
        print("响应内容:")
        print("-" * 40)
        print(response.text)
        print("-" * 40)
        
        # 尝试解析 JSON
        try:
            data = response.json()
            print("\n解析后的 JSON:")
            print(f"  flag: {data.get('flag')}")
            print(f"  resultCode: {data.get('resultCode')}")
            print(f"  message: {data.get('message')}")
            print(f"  data: {data.get('data')}")
            print(f"  choices: {data.get('choices')}")
            print(f"  keys: {list(data.keys())}")
            
            # 尝试获取内容
            if 'choices' in data:
                content = data.get('choices', [{}])[0].get('message', {}).get('content', '')
                print(f"  content: '{content}'")
            
        except Exception as e:
            print(f"\nJSON 解析失败: {e}")
            
    except Exception as e:
        print(f"请求异常: {e}")

if __name__ == "__main__":
    debug_response()
    print("\n" + "=" * 60)
