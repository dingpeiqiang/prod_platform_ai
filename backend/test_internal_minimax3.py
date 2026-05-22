"""
测试公司内部部署的 MiniMax API - 尝试不同配置
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
    
    api_key = "cb3a5cb469de1d0820d25a1e6349306dc4482f90"
    
    # 尝试不同的基础 URL
    base_urls_to_try = [
        "https://aicp.teamshub.com",
        "https://aicp.teamshub.com/openai",
        "https://aicp.teamshub.com/openai/api/v1",
        "https://aicp.teamshub.com/api"
    ]
    
    paths_to_try = [
        "/chat/completions",
        "/v1/chat/completions",
        "/minimax-m2.7"
    ]
    
    headers = {
        "Content-Type": "application/json",
        "token": api_key
    }
    
    payload = {
        "stream": False,
        "messages": [
            {"role": "user", "content": "你好"}
        ],
        "model": "minimax-m2.7"
    }
    
    for base_url in base_urls_to_try:
        for path in paths_to_try:
            url = f"{base_url}{path}"
            print(f"\n测试: {url}")
            print("-" * 40)
            
            try:
                response = requests.post(url, json=payload, headers=headers, timeout=30)
                
                if response.status_code == 200:
                    try:
                        result = response.json()
                        content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
                        if content:
                            print(f"✅ 成功！响应: {content[:100]}...")
                        else:
                            keys = list(result.keys())[:10]
                            print(f"⚠️  响应键: {keys}")
                            print(f"响应预览: {json.dumps(result, ensure_ascii=False)[:200]}...")
                    except:
                        print(f"响应内容: {response.text[:200]}")
                else:
                    print(f"❌ HTTP {response.status_code}")
                    
            except Exception as e:
                print(f"❌ 请求异常: {e}")
    
    # 尝试 GET 请求检查服务器状态
    print("\n" + "=" * 40)
    print("尝试 GET 请求")
    print("-" * 40)
    test_urls = [
        "https://aicp.teamshub.com/minimax-m2.7",
        "https://aicp.teamshub.com/openai/api/v1/models"
    ]
    
    for url in test_urls:
        print(f"\nGET {url}")
        try:
            response = requests.get(url, headers={"token": api_key}, timeout=30)
            print(f"状态码: {response.status_code}")
            if response.status_code == 200:
                print(f"响应预览: {response.text[:200]}")
        except Exception as e:
            print(f"❌ {e}")

if __name__ == "__main__":
    import json
    test_internal_minimax()
    print("\n" + "=" * 60)
