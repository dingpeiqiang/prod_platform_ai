"""
测试公司内部部署的 MiniMax API - 尝试不同的路径
"""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import requests

def test_internal_minimax():
    """测试公司内部 MiniMax API"""
    print("=" * 60)
    print("测试公司内部部署的 MiniMax API")
    print("=" * 60)
    
    # 用户提供的配置
    base_url = "https://aicp.teamshub.com/minimax-m2.7"
    api_key = "cb3a5cb469de1d0820d25a1e6349306dc4482f90"
    
    # 尝试不同的路径
    paths_to_try = [
        "",  # 直接使用基础 URL
        "/chat/completions",
        "/v1/chat/completions",
        "/api/chat/completions"
    ]
    
    headers = {
        "Content-Type": "application/json",
        "token": api_key
    }
    
    payload = {
        "stream": False,
        "messages": [
            {"role": "system", "content": "you are a helpful assistant!"},
            {"role": "user", "content": "你好"}
        ],
        "model": "minimax-m2.7"
    }
    
    for path in paths_to_try:
        url = f"{base_url}{path}"
        print(f"\n测试路径: {url}")
        print("-" * 40)
        
        try:
            response = requests.post(url, json=payload, headers=headers, timeout=30)
            
            print(f"状态码: {response.status_code}")
            if response.status_code == 200:
                try:
                    result = response.json()
                    content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
                    if content:
                        print(f"✅ 成功！响应: {content[:100]}...")
                    else:
                        print(f"⚠️  响应内容: {json.dumps(result, indent=2, ensure_ascii=False)[:200]}...")
                except:
                    print(f"响应内容: {response.text[:200]}")
            else:
                print(f"❌ 失败: HTTP {response.status_code}")
                try:
                    error_data = response.json()
                    print(f"错误信息: {error_data}")
                except:
                    print(f"响应内容: {response.text[:100]}")
                    
        except Exception as e:
            print(f"❌ 请求异常: {e}")

if __name__ == "__main__":
    import json
    test_internal_minimax()
    print("\n" + "=" * 60)
