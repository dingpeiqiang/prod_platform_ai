"""
测试公司内部部署的 MiniMax API
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
    
    # 用户提供的正确配置
    base_url = "https://aicp.teamshub.com/minimax-m2.7"
    api_key = "cb3a5cb469de1d0820d25a1e6349306dc4482f90"
    
    print(f"API URL: {base_url}")
    print(f"Token: {'***' if api_key else '(空)'}")
    print()
    
    # 构建请求（按照用户提供的格式）
    headers = {
        "Content-Type": "application/json",
        "token": api_key  # 使用 token 请求头
    }
    
    payload = {
        "stream": False,
        "messages": [
            {"role": "system", "content": "you are a helpful assistant!"},
            {"role": "user", "content": "你好，你是谁"}
        ],
        "model": "minimax-m2.7"
    }
    
    print("发送请求...")
    print(f"请求头: {headers}")
    print(f"请求体: {payload}")
    print()
    
    try:
        response = requests.post(base_url, json=payload, headers=headers, timeout=60)
        
        print(f"状态码: {response.status_code}")
        print(f"响应内容长度: {len(response.text)}")
        print()
        
        if response.status_code == 200:
            try:
                result = response.json()
                print("响应内容:")
                print("-" * 40)
                print(json.dumps(result, indent=2, ensure_ascii=False))
                print("-" * 40)
                
                # 检查内容
                content = result.get('choices', [{}])[0].get('message', {}).get('content', '')
                if content:
                    print(f"\n✅ 成功！响应内容: {content[:100]}...")
                else:
                    print("\n⚠️  响应内容为空")
                    
            except Exception as e:
                print(f"响应内容: {response.text}")
                print(f"JSON 解析失败: {e}")
        else:
            print(f"❌ HTTP 错误: {response.status_code}")
            try:
                error_data = response.json()
                print(f"错误信息: {error_data}")
            except:
                print(f"响应内容: {response.text}")
                
    except Exception as e:
        print(f"❌ 请求异常: {e}")

if __name__ == "__main__":
    import json
    test_internal_minimax()
    print("\n" + "=" * 60)
